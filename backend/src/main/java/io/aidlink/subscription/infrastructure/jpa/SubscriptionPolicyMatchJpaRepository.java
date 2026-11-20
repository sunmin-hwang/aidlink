package io.aidlink.subscription.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubscriptionPolicyMatchJpaRepository
        extends JpaRepository<SubscriptionPolicyMatchEntity, Long> {

    Optional<SubscriptionPolicyMatchEntity> findBySubscriptionIdAndPolicyId(
            Long subscriptionId,
            Long policyId
    );

    List<SubscriptionPolicyMatchEntity> findAllBySubscriptionIdAndLastChangedAtAfterOrderByLastChangedAtAsc(
            Long subscriptionId,
            Instant changedAfter
    );
}
