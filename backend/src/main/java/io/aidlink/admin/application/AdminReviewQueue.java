package io.aidlink.admin.application;

import io.aidlink.admin.domain.AdminReview;
import io.aidlink.admin.domain.AdminReviewReason;

import java.math.BigDecimal;

public interface AdminReviewQueue {

    AdminReview enqueue(long policyId, AdminReviewReason reason, BigDecimal priorityScore);

    AdminReviewPage findOpenReviews(int page, int size);

    long countOpenReviews();

    void resolveForPolicy(long policyId);
}
