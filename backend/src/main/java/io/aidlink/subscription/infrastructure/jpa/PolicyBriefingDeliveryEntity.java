package io.aidlink.subscription.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.subscription.domain.BriefingDeliveryStatus;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "policy_briefing_delivery")
public class PolicyBriefingDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private PolicySubscriptionEntity subscription;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String idempotencyKey;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BriefingDeliveryStatus status;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_payload", columnDefinition = "jsonb")
    private JsonNode reportPayload;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PolicyBriefingDeliveryEntity() {
    }

    public PolicyBriefingDeliveryEntity(
            PolicySubscriptionEntity subscription,
            String idempotencyKey,
            String recipientEmail,
            Instant scheduledAt,
            Instant periodStart,
            Instant periodEnd,
            Instant now
    ) {
        this.subscription = Objects.requireNonNull(subscription, "subscription");
        this.idempotencyKey = requireSha256Hash(idempotencyKey);
        this.recipientEmail = requireEmail(recipientEmail);
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("periodEnd must be after periodStart");
        }
        this.status = BriefingDeliveryStatus.SCHEDULED;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public PolicySubscriptionEntity getSubscription() {
        return subscription;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public BriefingDeliveryStatus getStatus() {
        return status;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public JsonNode getReportPayload() {
        return reportPayload == null ? null : reportPayload.deepCopy();
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void startAttempt(JsonNode reportPayload, Instant now) {
        if (status != BriefingDeliveryStatus.SCHEDULED && status != BriefingDeliveryStatus.FAILED) {
            throw new IllegalStateException("delivery cannot be started from status " + status);
        }
        this.reportPayload = Objects.requireNonNull(reportPayload, "reportPayload").deepCopy();
        this.status = BriefingDeliveryStatus.SENDING;
        this.attemptCount++;
        this.failureReason = null;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void markSent(String providerMessageId, Instant sentAt) {
        requireSending();
        this.status = BriefingDeliveryStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
        this.updatedAt = sentAt;
    }

    public void markFailed(String failureReason, Instant now) {
        requireSending();
        this.status = BriefingDeliveryStatus.FAILED;
        this.failureReason = requireNonBlank(failureReason, "failureReason");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void cancel(Instant now) {
        if (status == BriefingDeliveryStatus.SENT) {
            throw new IllegalStateException("sent delivery cannot be cancelled");
        }
        this.status = BriefingDeliveryStatus.CANCELLED;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    private void requireSending() {
        if (status != BriefingDeliveryStatus.SENDING) {
            throw new IllegalStateException("delivery is not being sent");
        }
    }

    private static String requireSha256Hash(String value) {
        Objects.requireNonNull(value, "idempotencyKey");
        if (!value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("idempotencyKey must be a 64-character SHA-256 hex value");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String requireEmail(String email) {
        String normalized = requireNonBlank(email, "recipientEmail").trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !normalized.contains("@")) {
            throw new IllegalArgumentException("recipientEmail is invalid");
        }
        return normalized;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
