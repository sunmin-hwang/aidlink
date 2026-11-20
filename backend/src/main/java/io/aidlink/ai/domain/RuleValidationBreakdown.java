package io.aidlink.ai.domain;

public record RuleValidationBreakdown(
        int schemaValidity,
        int supportedFieldRatio,
        int originalTextCoverage,
        int operatorValueValidity,
        int ambiguityScore,
        int unsupportedExpressionPenalty
) {
    public int total() {
        return Math.max(0, Math.min(100,
                schemaValidity
                        + supportedFieldRatio
                        + originalTextCoverage
                        + operatorValueValidity
                        + ambiguityScore
                        - unsupportedExpressionPenalty
        ));
    }
}
