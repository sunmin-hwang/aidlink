package io.aidlink.admin.infrastructure.memory;

import io.aidlink.admin.application.AdminReviewQueue;
import io.aidlink.admin.application.AdminReviewPage;
import io.aidlink.admin.domain.AdminReview;
import io.aidlink.admin.domain.AdminReviewReason;
import io.aidlink.admin.domain.AdminReviewStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("!postgres")
public class InMemoryAdminReviewQueue implements AdminReviewQueue {

    private final AtomicLong sequence = new AtomicLong();
    private final Map<ReviewKey, AdminReview> reviews = new ConcurrentHashMap<>();

    @Override
    public AdminReview enqueue(long policyId, AdminReviewReason reason, BigDecimal priorityScore) {
        ReviewKey key = new ReviewKey(policyId, reason);
        return reviews.compute(key, (ignored, existing) -> {
            if (existing != null && existing.status() != AdminReviewStatus.RESOLVED) {
                return existing.priorityScore().compareTo(priorityScore) >= 0
                        ? existing
                        : new AdminReview(
                                existing.id(), policyId, existing.policyName(), reason,
                                priorityScore, existing.status(), existing.createdAt(), null);
            }
            return new AdminReview(
                    sequence.incrementAndGet(), policyId, "정책 " + policyId, reason,
                    priorityScore, AdminReviewStatus.OPEN, Instant.now(), null);
        });
    }

    @Override
    public AdminReviewPage findOpenReviews(int page, int size) {
        int normalizedSize = Math.max(1, Math.min(size, 100));
        List<AdminReview> openReviews = reviews.values().stream()
                .filter(review -> review.status() != AdminReviewStatus.RESOLVED)
                .sorted(Comparator.comparing(AdminReview::priorityScore).reversed()
                        .thenComparing(AdminReview::createdAt))
                .toList();
        int totalPages = openReviews.isEmpty()
                ? 0
                : (openReviews.size() + normalizedSize - 1) / normalizedSize;
        int normalizedPage = totalPages == 0
                ? 0
                : Math.min(Math.max(page, 0), totalPages - 1);
        int from = Math.min(normalizedPage * normalizedSize, openReviews.size());
        int to = Math.min(from + normalizedSize, openReviews.size());
        return AdminReviewPage.of(
                openReviews.subList(from, to),
                openReviews.size(),
                normalizedPage,
                normalizedSize
        );
    }

    @Override
    public long countOpenReviews() {
        return reviews.values().stream()
                .filter(review -> review.status() != AdminReviewStatus.RESOLVED)
                .count();
    }

    @Override
    public void resolveForPolicy(long policyId) {
        reviews.replaceAll((key, review) -> review.policyId() == policyId
                ? new AdminReview(
                        review.id(), review.policyId(), review.policyName(), review.reason(),
                        review.priorityScore(), AdminReviewStatus.RESOLVED,
                        review.createdAt(), Instant.now())
                : review);
    }

    private record ReviewKey(long policyId, AdminReviewReason reason) {
    }
}
