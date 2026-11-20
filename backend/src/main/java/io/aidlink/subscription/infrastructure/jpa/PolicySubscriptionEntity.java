package io.aidlink.subscription.infrastructure.jpa;

import io.aidlink.subscription.domain.SubscriptionStatus;
import io.aidlink.user.infrastructure.jpa.AppUserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

@Entity
@Table(name = "policy_subscription")
public class PolicySubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "timezone_id", nullable = false, length = 64)
    private String timezoneId;

    @Column(name = "urgent_deadline_alerts_enabled", nullable = false)
    private boolean urgentDeadlineAlertsEnabled;

    @Column(name = "urgent_alert_consent_updated_at")
    private Instant urgentAlertConsentUpdatedAt;

    @Column(name = "next_briefing_at")
    private Instant nextBriefingAt;

    @Column(name = "subscribed_at", nullable = false)
    private Instant subscribedAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PolicySubscriptionEntity() {
    }

    public PolicySubscriptionEntity(AppUserEntity user, String timezoneId, Instant now) {
        this.user = Objects.requireNonNull(user, "user");
        this.timezoneId = requireTimezone(timezoneId);
        this.status = SubscriptionStatus.ACTIVE;
        this.subscribedAt = Objects.requireNonNull(now, "now");
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public AppUserEntity getUser() {
        return user;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public String getTimezoneId() {
        return timezoneId;
    }

    public boolean isUrgentDeadlineAlertsEnabled() {
        return urgentDeadlineAlertsEnabled;
    }

    public Instant getUrgentAlertConsentUpdatedAt() {
        return urgentAlertConsentUpdatedAt;
    }

    public Instant getNextBriefingAt() {
        return nextBriefingAt;
    }

    public Instant getSubscribedAt() {
        return subscribedAt;
    }

    public Instant getPausedAt() {
        return pausedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateTimezone(String timezoneId, Instant now) {
        requireNotCancelled();
        this.timezoneId = requireTimezone(timezoneId);
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void updateUrgentDeadlineAlerts(boolean enabled, Instant now) {
        requireNotCancelled();
        this.urgentDeadlineAlertsEnabled = enabled;
        this.urgentAlertConsentUpdatedAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public void scheduleNextBriefing(Instant nextBriefingAt, Instant now) {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("only an active subscription can be scheduled");
        }
        this.nextBriefingAt = Objects.requireNonNull(nextBriefingAt, "nextBriefingAt");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void pause(Instant now) {
        requireNotCancelled();
        this.status = SubscriptionStatus.PAUSED;
        this.pausedAt = Objects.requireNonNull(now, "now");
        this.nextBriefingAt = null;
        this.updatedAt = now;
    }

    public void resume(Instant nextBriefingAt, Instant now) {
        requireNotCancelled();
        this.status = SubscriptionStatus.ACTIVE;
        this.pausedAt = null;
        this.nextBriefingAt = Objects.requireNonNull(nextBriefingAt, "nextBriefingAt");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void cancel(Instant now) {
        if (status == SubscriptionStatus.CANCELLED) {
            return;
        }
        this.status = SubscriptionStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(now, "now");
        this.pausedAt = null;
        this.nextBriefingAt = null;
        this.updatedAt = now;
    }

    private void requireNotCancelled() {
        if (status == SubscriptionStatus.CANCELLED) {
            throw new IllegalStateException("cancelled subscription cannot be changed");
        }
    }

    private static String requireTimezone(String timezoneId) {
        Objects.requireNonNull(timezoneId, "timezoneId");
        return ZoneId.of(timezoneId).getId();
    }
}
