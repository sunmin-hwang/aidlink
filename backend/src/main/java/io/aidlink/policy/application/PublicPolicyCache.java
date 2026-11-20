package io.aidlink.policy.application;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface PublicPolicyCache {

    Optional<List<PublicPolicyView>> findAll();

    Optional<PublicPolicyView> findById(long policyId);

    void putAll(List<PublicPolicyView> policies, Duration ttl);

    void put(PublicPolicyView policy, Duration ttl);

    void evictAll();
}
