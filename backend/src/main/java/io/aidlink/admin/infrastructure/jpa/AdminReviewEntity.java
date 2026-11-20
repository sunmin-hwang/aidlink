package io.aidlink.admin.infrastructure.jpa;

import io.aidlink.admin.domain.AdminReviewReason;
import io.aidlink.admin.domain.AdminReviewStatus;
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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "admin_review")
public class AdminReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private PolicyEntity policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private AdminReviewReason reason;

    @Column(name = "priority_score", nullable = false, precision = 10, scale = 2)
    private BigDecimal priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminReviewStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected AdminReviewEntity() {
    }

    public AdminReviewEntity(
            PolicyEntity policy,
            AdminReviewReason reason,
            BigDecimal priorityScore,
            Instant createdAt
    ) {
        this.policy = policy;
        this.reason = reason;
        this.priorityScore = priorityScore;
        this.status = AdminReviewStatus.OPEN;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public PolicyEntity getPolicy() {
        return policy;
    }

    public AdminReviewReason getReason() {
        return reason;
    }

    public BigDecimal getPriorityScore() {
        return priorityScore;
    }

    public AdminReviewStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void raisePriority(BigDecimal requestedPriority) {
        if (requestedPriority.compareTo(priorityScore) > 0) {
            priorityScore = requestedPriority;
        }
    }

    public void resolve(Instant resolvedAt) {
        this.status = AdminReviewStatus.RESOLVED;
        this.resolvedAt = resolvedAt;
    }
}
