package io.aidlink.collector.domain;

import java.util.List;

public record CollectedPolicyPage(
        int page,
        int perPage,
        long totalCount,
        List<RawPolicyDocument> policies
) {
    public CollectedPolicyPage {
        if (page < 1 || perPage < 1 || totalCount < 0) {
            throw new IllegalArgumentException("Invalid collection page metadata");
        }
        policies = List.copyOf(policies);
    }
}
