package io.aidlink.control.application;

import java.time.Duration;
import java.util.Optional;

public interface OperationLock {

    Optional<String> tryAcquire(String key, Duration ttl);

    void release(String key, String token);
}
