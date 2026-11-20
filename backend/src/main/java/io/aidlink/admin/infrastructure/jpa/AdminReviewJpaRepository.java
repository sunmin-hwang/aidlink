package io.aidlink.admin.infrastructure.jpa;

import io.aidlink.admin.domain.AdminReviewReason;
import io.aidlink.admin.domain.AdminReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AdminReviewJpaRepository extends JpaRepository<AdminReviewEntity, Long> {

    Optional<AdminReviewEntity> findFirstByPolicyIdAndReasonAndStatusIn(
            long policyId,
            AdminReviewReason reason,
            Collection<AdminReviewStatus> statuses
    );

    Page<AdminReviewEntity> findByStatusIn(
            Collection<AdminReviewStatus> statuses,
            Pageable pageable
    );

    long countByStatusIn(Collection<AdminReviewStatus> statuses);

    List<AdminReviewEntity> findByPolicyIdAndStatusIn(
            long policyId,
            Collection<AdminReviewStatus> statuses
    );
}
