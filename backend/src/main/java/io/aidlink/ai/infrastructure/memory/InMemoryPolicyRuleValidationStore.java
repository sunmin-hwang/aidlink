package io.aidlink.ai.infrastructure.memory;

import io.aidlink.ai.application.PolicyRuleValidationStore;
import io.aidlink.ai.domain.PolicyRuleValidationResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryPolicyRuleValidationStore implements PolicyRuleValidationStore {

    private final Map<Long, PolicyRuleValidationResult> validations = new ConcurrentHashMap<>();

    @Override
    public void save(PolicyRuleValidationResult result) {
        validations.put(result.policyId(), result);
    }

    @Override
    public Optional<PolicyRuleValidationResult> findLatest(long policyId) {
        return Optional.ofNullable(validations.get(policyId));
    }
}
