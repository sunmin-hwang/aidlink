package io.aidlink.admin.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminReview(
        long id,
        long policyId,
        String policyName,
        AdminReviewReason reason,
        BigDecimal priorityScore,
        AdminReviewStatus status,
        Instant createdAt,
        Instant resolvedAt
) {
}
