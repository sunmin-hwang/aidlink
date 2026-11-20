package io.aidlink.admin.application;

import io.aidlink.ai.domain.PolicyRuleValidationResult;
import io.aidlink.ai.domain.StoredPolicyRuleCandidate;
import io.aidlink.policy.domain.PolicyAnalysisStatus;

public record AdminPolicyAnalysisView(
        long policyId,
        String policyName,
        PolicyAnalysisStatus analysisStatus,
        StoredPolicyRuleCandidate latestCandidate,
        PolicyRuleValidationResult latestValidation
) {
}
