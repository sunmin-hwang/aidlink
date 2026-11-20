package io.aidlink.eligibility.domain;

import io.aidlink.policy.domain.PolicyAnalysisStatus;

import java.util.List;

public record EligibilityAnalysis(
        long policyId,
        String policyName,
        String provider,
        String category,
        PolicyAnalysisStatus analysisStatus,
        EligibilityStatus eligibilityStatus,
        EvaluationStatus ruleStatus,
        List<ConditionEvaluation> conditions,
        List<ConditionEvaluation> blockingConditions,
        String guidance
) {
    public EligibilityAnalysis {
        conditions = List.copyOf(conditions);
        blockingConditions = List.copyOf(blockingConditions);
    }
}

