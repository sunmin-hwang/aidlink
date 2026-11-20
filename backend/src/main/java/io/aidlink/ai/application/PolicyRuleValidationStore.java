package io.aidlink.ai.application;

import io.aidlink.ai.domain.PolicyRuleValidationResult;

import java.util.Optional;

public interface PolicyRuleValidationStore {

    void save(PolicyRuleValidationResult result);

    Optional<PolicyRuleValidationResult> findLatest(long policyId);
}
