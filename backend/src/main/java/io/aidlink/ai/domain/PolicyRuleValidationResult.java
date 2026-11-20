package io.aidlink.ai.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record PolicyRuleValidationResult(
        long candidateId,
        long policyId,
        RuleValidationStatus status,
        RuleValidationBreakdown breakdown,
        List<RuleValidationIssue> issues,
        JsonNode rule
) {
    public PolicyRuleValidationResult {
        issues = List.copyOf(issues);
        rule = rule.deepCopy();
    }

    public int score() {
        return breakdown.total();
    }
}
