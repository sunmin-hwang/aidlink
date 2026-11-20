package io.aidlink.control.infrastructure.redis;

import io.aidlink.control.application.RateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "aidlink.redis", name = "enabled", havingValue = "true")
public class RedisRateLimiter implements RateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        try {
            Long current = redisTemplate.execute(
                    SCRIPT,
                    List.of("aidlink:rate:" + key),
                    Long.toString(window.toMillis())
            );
            return current != null && current <= limit;
        } catch (RuntimeException exception) {
            return true;
        }
    }
}
