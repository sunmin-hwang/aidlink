package io.aidlink.policy.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.policy.application.PublicPolicyCache;
import io.aidlink.policy.application.PublicPolicyView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "aidlink.redis", name = "enabled", havingValue = "true")
public class RedisPublicPolicyCache implements PublicPolicyCache {

    private static final String ALL_KEY = "aidlink:policy:all:v8";
    private static final String ONE_KEY_PREFIX = "aidlink:policy:one:v8:";
    private static final String ONE_KEYS_INDEX = "aidlink:policy:one:v8:keys";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisPublicPolicyCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<List<PublicPolicyView>> findAll() {
        String cached;
        try {
            cached = redisTemplate.opsForValue().get(ALL_KEY);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (cached == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    cached, new TypeReference<List<PublicPolicyView>>() { }));
        } catch (Exception exception) {
            deleteQuietly(ALL_KEY);
            return Optional.empty();
        }
    }

    @Override
    public Optional<PublicPolicyView> findById(long policyId) {
        String key = ONE_KEY_PREFIX + policyId;
        String cached;
        try {
            cached = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (cached == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cached, PublicPolicyView.class));
        } catch (Exception exception) {
            deleteQuietly(key);
            return Optional.empty();
        }
    }

    @Override
    public void putAll(List<PublicPolicyView> policies, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    ALL_KEY, objectMapper.writeValueAsString(policies), ttl);
        } catch (Exception exception) {
            // Cache failures must not fail the source-of-truth database request.
        }
    }

    @Override
    public void put(PublicPolicyView policy, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    ONE_KEY_PREFIX + policy.id(),
                    objectMapper.writeValueAsString(policy),
                    ttl
            );
            redisTemplate.opsForSet().add(ONE_KEYS_INDEX, ONE_KEY_PREFIX + policy.id());
            redisTemplate.expire(ONE_KEYS_INDEX, ttl.plusMinutes(1));
        } catch (Exception exception) {
            // Cache failures must not fail the source-of-truth database request.
        }
    }

    @Override
    public void evictAll() {
        try {
            redisTemplate.delete(ALL_KEY);
            var keys = redisTemplate.opsForSet().members(ONE_KEYS_INDEX);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            redisTemplate.delete(ONE_KEYS_INDEX);
        } catch (RuntimeException exception) {
            // Stale public cache entries expire after the short TTL.
        }
    }

    private void deleteQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            // A corrupt cache entry can expire naturally when Redis is unavailable.
        }
    }
}
