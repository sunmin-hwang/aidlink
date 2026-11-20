package io.aidlink.ai.domain;

public record RuleValidationIssue(
        String code,
        RuleValidationSeverity severity,
        String nodePath,
        String message
) {
}
