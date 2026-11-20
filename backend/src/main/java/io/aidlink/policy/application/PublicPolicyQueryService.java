package io.aidlink.policy.application;

import io.aidlink.policy.infrastructure.jpa.RuleJsonMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class PublicPolicyQueryService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final PolicyCatalog policyCatalog;
    private final PublicPolicyCache cache;
    private final RuleJsonMapper ruleJsonMapper;

    public PublicPolicyQueryService(
            PolicyCatalog policyCatalog,
            PublicPolicyCache cache,
            RuleJsonMapper ruleJsonMapper
    ) {
        this.policyCatalog = policyCatalog;
        this.cache = cache;
        this.ruleJsonMapper = ruleJsonMapper;
    }

    public List<PublicPolicyView> findAll() {
        return cache.findAll().orElseGet(() -> {
            List<PublicPolicyView> policies = policyCatalog.findAll().stream()
                    .map(policy -> PublicPolicyView.from(policy, ruleJsonMapper))
                    .toList();
            cache.putAll(policies, CACHE_TTL);
            return policies;
        });
    }

    public Optional<PublicPolicyView> findById(long policyId) {
        return cache.findById(policyId).or(() -> policyCatalog.findById(policyId)
                .map(policy -> PublicPolicyView.from(policy, ruleJsonMapper))
                .map(policy -> {
                    cache.put(policy, CACHE_TTL);
                    return policy;
                }));
    }

    public PageResult<PublicPolicyView> search(PolicySearchCriteria criteria) {
        return policyCatalog.search(criteria)
                .map(policy -> PublicPolicyView.from(policy, ruleJsonMapper));
    }
}
