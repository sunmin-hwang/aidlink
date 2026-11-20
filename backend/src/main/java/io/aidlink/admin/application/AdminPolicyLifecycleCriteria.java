package io.aidlink.admin.application;

import io.aidlink.policy.domain.PolicyLifecycleStatus;

import java.util.Locale;

public record AdminPolicyLifecycleCriteria(
        int page,
        int size,
        PolicyLifecycleStatus status,
        String query
) {
    public AdminPolicyLifecycleCriteria {
        page = Math.max(page, 0);
        size = Math.clamp(size, 1, 100);
        query = query == null || query.isBlank() ? null : query.trim();
    }

    public static PolicyLifecycleStatus resolveStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PolicyLifecycleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
