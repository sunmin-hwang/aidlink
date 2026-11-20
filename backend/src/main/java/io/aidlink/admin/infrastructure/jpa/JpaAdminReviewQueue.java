package io.aidlink.admin.infrastructure.jpa;

import io.aidlink.admin.application.AdminReviewQueue;
import io.aidlink.admin.application.AdminReviewPage;
import io.aidlink.admin.domain.AdminReview;
import io.aidlink.admin.domain.AdminReviewReason;
import io.aidlink.admin.domain.AdminReviewStatus;
import io.aidlink.policy.infrastructure.jpa.PolicyJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
@Profile("postgres")
public class JpaAdminReviewQueue implements AdminReviewQueue {

    private static final int MAX_PAGE_SIZE = 100;

    private static final List<AdminReviewStatus> ACTIVE_STATUSES =
            List.of(AdminReviewStatus.OPEN, AdminReviewStatus.IN_PROGRESS);

    private final AdminReviewJpaRepository reviewRepository;
    private final PolicyJpaRepository policyRepository;

    public JpaAdminReviewQueue(
            AdminReviewJpaRepository reviewRepository,
            PolicyJpaRepository policyRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.policyRepository = policyRepository;
    }

    @Override
    @Transactional
    public AdminReview enqueue(long policyId, AdminReviewReason reason, BigDecimal priorityScore) {
        AdminReviewEntity review = reviewRepository
                .findFirstByPolicyIdAndReasonAndStatusIn(policyId, reason, ACTIVE_STATUSES)
                .orElseGet(() -> new AdminReviewEntity(
                        policyRepository.findById(policyId).orElseThrow(() ->
                                new IllegalArgumentException("Policy not found: " + policyId)),
                        reason,
                        priorityScore,
                        Instant.now()
                ));
        review.raisePriority(priorityScore);
        return toDomain(reviewRepository.save(review));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReviewPage findOpenReviews(int page, int size) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        long totalElements = reviewRepository.countByStatusIn(ACTIVE_STATUSES);
        int totalPages = totalElements == 0
                ? 0
                : Math.toIntExact((totalElements + normalizedSize - 1) / normalizedSize);
        int normalizedPage = totalPages == 0
                ? 0
                : Math.min(Math.max(page, 0), totalPages - 1);
        var pageable = PageRequest.of(
                normalizedPage,
                normalizedSize,
                Sort.by(
                        Sort.Order.desc("priorityScore"),
                        Sort.Order.asc("createdAt")
                )
        );
        List<AdminReview> content = reviewRepository
                .findByStatusIn(ACTIVE_STATUSES, pageable)
                .getContent()
                .stream()
                .map(this::toDomain)
                .toList();
        return AdminReviewPage.of(content, totalElements, normalizedPage, normalizedSize);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOpenReviews() {
        return reviewRepository.countByStatusIn(ACTIVE_STATUSES);
    }

    @Override
    @Transactional
    public void resolveForPolicy(long policyId) {
        Instant resolvedAt = Instant.now();
        reviewRepository.findByPolicyIdAndStatusIn(policyId, ACTIVE_STATUSES)
                .forEach(review -> review.resolve(resolvedAt));
    }

    private AdminReview toDomain(AdminReviewEntity entity) {
        return new AdminReview(
                entity.getId(),
                entity.getPolicy().getId(),
                entity.getPolicy().getName(),
                entity.getReason(),
                entity.getPriorityScore(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getResolvedAt()
        );
    }
}
