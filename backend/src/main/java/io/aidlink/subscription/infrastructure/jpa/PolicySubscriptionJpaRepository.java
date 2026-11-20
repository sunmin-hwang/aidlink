package io.aidlink.subscription.infrastructure.jpa;

import io.aidlink.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PolicySubscriptionJpaRepository
        extends JpaRepository<PolicySubscriptionEntity, Long> {

    Optional<PolicySubscriptionEntity> findByUserId(Long userId);

    List<PolicySubscriptionEntity> findAllByStatusAndNextBriefingAtLessThanEqualOrderByNextBriefingAtAsc(
            SubscriptionStatus status,
            Instant dueAt
    );
}
