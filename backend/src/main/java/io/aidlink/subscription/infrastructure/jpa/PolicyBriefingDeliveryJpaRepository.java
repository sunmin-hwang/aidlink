package io.aidlink.subscription.infrastructure.jpa;

import io.aidlink.subscription.domain.BriefingDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PolicyBriefingDeliveryJpaRepository
        extends JpaRepository<PolicyBriefingDeliveryEntity, Long> {

    Optional<PolicyBriefingDeliveryEntity> findByIdempotencyKey(String idempotencyKey);

    List<PolicyBriefingDeliveryEntity> findAllByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            BriefingDeliveryStatus status,
            Instant dueAt
    );
}
