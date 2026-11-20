package io.aidlink.ai.application;

import io.aidlink.ai.domain.PolicyRuleCandidate;
import io.aidlink.ai.domain.StoredPolicyRuleCandidate;

import java.util.Optional;

public interface PolicyRuleCandidateStore {

    StoredPolicyRuleCandidate save(long policyId, String sourceTextHash, PolicyRuleCandidate candidate);

    Optional<StoredPolicyRuleCandidate> findLatest(long policyId);
}
