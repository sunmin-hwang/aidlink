package io.aidlink.admin.application;

import io.aidlink.policy.domain.PolicyLifecycleStatus;

import java.util.List;

public record AdminPolicyLifecyclePage(
        List<AdminPolicyLifecycleView> content,
        long totalElements,
        int totalPages,
        int page,
        int size,
        PolicyLifecycleStatus status,
        String query,
        boolean first,
        boolean last
) {
    public AdminPolicyLifecyclePage {
        content = List.copyOf(content);
    }
}
