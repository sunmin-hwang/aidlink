package io.aidlink.subscription.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionScheduleJpaRepository
        extends JpaRepository<SubscriptionScheduleEntity, Long> {

    List<SubscriptionScheduleEntity> findAllBySubscriptionIdOrderBySlotNumberAsc(Long subscriptionId);

    void deleteAllBySubscriptionId(Long subscriptionId);
}
