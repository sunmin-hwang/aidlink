package io.aidlink.policy.infrastructure.cache;

import io.aidlink.policy.application.PublicPolicyCache;
import io.aidlink.policy.application.PublicPolicyView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(
        prefix = "aidlink.redis",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpPublicPolicyCache implements PublicPolicyCache {

    @Override
    public Optional<List<PublicPolicyView>> findAll() {
        return Optional.empty();
    }

    @Override
    public Optional<PublicPolicyView> findById(long policyId) {
        return Optional.empty();
    }

    @Override
    public void putAll(List<PublicPolicyView> policies, Duration ttl) {
    }

    @Override
    public void put(PublicPolicyView policy, Duration ttl) {
    }

    @Override
    public void evictAll() {
    }
}
