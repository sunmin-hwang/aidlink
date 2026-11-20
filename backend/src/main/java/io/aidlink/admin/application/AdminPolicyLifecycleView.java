package io.aidlink.admin.application;

import io.aidlink.policy.domain.PolicyLifecycleStatus;

import java.time.Instant;
import java.time.LocalDate;

public record AdminPolicyLifecycleView(
        long policyId,
        String externalId,
        String policyName,
        String provider,
        String category,
        PolicyLifecycleStatus lifecycleStatus,
        LocalDate applicationEnd,
        Instant lastSeenAt,
        int missedCollectionCount,
        Instant archivedAt
) {
}
