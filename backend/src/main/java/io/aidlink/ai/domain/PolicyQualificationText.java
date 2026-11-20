package io.aidlink.ai.domain;

public record PolicyQualificationText(
        long policyId,
        String policyName,
        String targetDescription,
        String selectionCriteria,
        String exclusionConditions
) {
}
