package io.aidlink.subscription.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(
        name = "subscription_schedule",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_subscription_schedule_slot", columnNames = {"subscription_id", "slot_number"}),
                @UniqueConstraint(name = "uq_subscription_schedule_time", columnNames = {"subscription_id", "day_of_week", "delivery_time"})
        }
)
public class SubscriptionScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private PolicySubscriptionEntity subscription;

    @Column(name = "slot_number", nullable = false)
    private short slotNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(name = "delivery_time", nullable = false)
    private LocalTime deliveryTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubscriptionScheduleEntity() {
    }

    public SubscriptionScheduleEntity(
            PolicySubscriptionEntity subscription,
            int slotNumber,
            DayOfWeek dayOfWeek,
            LocalTime deliveryTime,
            Instant now
    ) {
        this.subscription = Objects.requireNonNull(subscription, "subscription");
        this.slotNumber = requireSlotNumber(slotNumber);
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        this.deliveryTime = normalizeTime(deliveryTime);
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public PolicySubscriptionEntity getSubscription() {
        return subscription;
    }

    public short getSlotNumber() {
        return slotNumber;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getDeliveryTime() {
        return deliveryTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(DayOfWeek dayOfWeek, LocalTime deliveryTime, Instant now) {
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        this.deliveryTime = normalizeTime(deliveryTime);
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    private static short requireSlotNumber(int slotNumber) {
        if (slotNumber < 1 || slotNumber > 2) {
            throw new IllegalArgumentException("slotNumber must be 1 or 2");
        }
        return (short) slotNumber;
    }

    private static LocalTime normalizeTime(LocalTime deliveryTime) {
        return Objects.requireNonNull(deliveryTime, "deliveryTime").withSecond(0).withNano(0);
    }
}
