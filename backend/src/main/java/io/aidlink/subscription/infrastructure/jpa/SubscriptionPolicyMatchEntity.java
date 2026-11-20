package io.aidlink.subscription.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "subscription_policy_match",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_subscription_policy_match",
                columnNames = {"subscription_id", "policy_id"}
        )
)
public class SubscriptionPolicyMatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private PolicySubscriptionEntity subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private PolicyEntity policy;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 40)
    private EligibilityStatus currentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 40)
    private EligibilityStatus previousStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode resultPayload;

    @Column(name = "policy_source_updated_at")
    private Instant policySourceUpdatedAt;

    @Column(name = "first_observed_at", nullable = false)
    private Instant firstObservedAt;

    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt;

    @Column(name = "last_changed_at", nullable = false)
    private Instant lastChangedAt;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SubscriptionPolicyMatchEntity() {
    }

    public SubscriptionPolicyMatchEntity(
            PolicySubscriptionEntity subscription,
            PolicyEntity policy,
            EligibilityStatus status,
            JsonNode resultPayload,
            Instant policySourceUpdatedAt,
            Instant evaluatedAt
    ) {
        this.subscription = Objects.requireNonNull(subscription, "subscription");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.currentStatus = Objects.requireNonNull(status, "status");
        this.resultPayload = copyPayload(resultPayload);
        this.policySourceUpdatedAt = policySourceUpdatedAt;
        this.firstObservedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        this.lastEvaluatedAt = evaluatedAt;
        this.lastChangedAt = evaluatedAt;
    }

    public Long getId() {
        return id;
    }

    public PolicySubscriptionEntity getSubscription() {
        return subscription;
    }

    public PolicyEntity getPolicy() {
        return policy;
    }

    public EligibilityStatus getCurrentStatus() {
        return currentStatus;
    }

    public EligibilityStatus getPreviousStatus() {
        return previousStatus;
    }

    public JsonNode getResultPayload() {
        return resultPayload.deepCopy();
    }

    public Instant getPolicySourceUpdatedAt() {
        return policySourceUpdatedAt;
    }

    public Instant getFirstObservedAt() {
        return firstObservedAt;
    }

    public Instant getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public Instant getLastChangedAt() {
        return lastChangedAt;
    }

    public Instant getLastNotifiedAt() {
        return lastNotifiedAt;
    }

    public boolean refresh(
            EligibilityStatus status,
            JsonNode resultPayload,
            Instant policySourceUpdatedAt,
            Instant evaluatedAt
    ) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        if (evaluatedAt.isBefore(lastEvaluatedAt)) {
            throw new IllegalArgumentException("evaluatedAt must not be before the previous evaluation");
        }
        boolean changed = currentStatus != status
                || !Objects.equals(this.policySourceUpdatedAt, policySourceUpdatedAt)
                || !this.resultPayload.equals(resultPayload);
        if (changed) {
            this.previousStatus = this.currentStatus;
            this.currentStatus = status;
            this.resultPayload = copyPayload(resultPayload);
            this.policySourceUpdatedAt = policySourceUpdatedAt;
            this.lastChangedAt = evaluatedAt;
        }
        this.lastEvaluatedAt = evaluatedAt;
        return changed;
    }

    public void markNotified(Instant notifiedAt) {
        Objects.requireNonNull(notifiedAt, "notifiedAt");
        if (notifiedAt.isBefore(firstObservedAt)) {
            throw new IllegalArgumentException("notifiedAt must not be before firstObservedAt");
        }
        this.lastNotifiedAt = notifiedAt;
    }

    private static JsonNode copyPayload(JsonNode payload) {
        return Objects.requireNonNull(payload, "resultPayload").deepCopy();
    }
}
