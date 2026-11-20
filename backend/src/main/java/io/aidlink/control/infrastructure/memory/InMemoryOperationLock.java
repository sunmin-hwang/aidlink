package io.aidlink.control.infrastructure.memory;

import io.aidlink.control.application.OperationLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
        prefix = "aidlink.redis",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class InMemoryOperationLock implements OperationLock {

    private final Map<String, LockValue> locks = new ConcurrentHashMap<>();

    @Override
    public Optional<String> tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        LockValue value = locks.compute(key, (ignored, existing) ->
                existing == null || !now.isBefore(existing.expiresAt())
                        ? new LockValue(token, now.plus(ttl))
                        : existing);
        return value.token().equals(token) ? Optional.of(token) : Optional.empty();
    }

    @Override
    public void release(String key, String token) {
        locks.computeIfPresent(key, (ignored, existing) ->
                existing.token().equals(token) ? null : existing);
    }

    private record LockValue(String token, Instant expiresAt) {
    }
}
