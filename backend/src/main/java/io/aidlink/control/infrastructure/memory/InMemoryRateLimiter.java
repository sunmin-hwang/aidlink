package io.aidlink.control.infrastructure.memory;

import io.aidlink.control.application.RateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
        prefix = "aidlink.redis",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class InMemoryRateLimiter implements RateLimiter {

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (limit < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Rate limit and window must be positive");
        }
        Instant now = Instant.now();
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || !now.isBefore(existing.expiresAt())) {
                return new WindowCounter(1, now.plus(window));
            }
            return new WindowCounter(existing.count() + 1, existing.expiresAt());
        });
        return counter.count() <= limit;
    }

    private record WindowCounter(int count, Instant expiresAt) {
    }
}
