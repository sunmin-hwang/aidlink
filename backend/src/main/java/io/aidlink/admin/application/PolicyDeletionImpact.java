package io.aidlink.admin.application;

import io.aidlink.policy.domain.PolicyLifecycleStatus;

import java.time.Instant;
import java.util.List;

public record PolicyDeletionImpact(
        long policyId,
        String policyName,
        PolicyLifecycleStatus lifecycleStatus,
        boolean dryRun,
        int minimumRetentionDays,
        Instant archivedAt,
        Instant retentionUntil,
        PolicyDeletionReferenceCounts references,
        long cascadingRows,
        long preservedRawPolicyRows,
        boolean safeToDelete,
        List<String> blockers,
        Instant previewedAt
) {
    public PolicyDeletionImpact {
        blockers = List.copyOf(blockers);
    }
}
