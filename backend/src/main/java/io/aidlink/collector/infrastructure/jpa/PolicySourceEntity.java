package io.aidlink.collector.infrastructure.jpa;

import io.aidlink.collector.domain.PolicySource;
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

import java.time.Instant;

@Entity
@Table(name = "policy_source")
public class PolicySourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private PolicyEntity policy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private PolicySource source;

    @Column(name = "external_id", nullable = false, length = 160)
    private String externalId;

    @Column(name = "source_url", nullable = false, columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "last_collected_at")
    private Instant lastCollectedAt;

    protected PolicySourceEntity() {
    }

    public PolicySourceEntity(
            PolicyEntity policy,
            PolicySource source,
            String externalId,
            String sourceUrl,
            Instant lastCollectedAt
    ) {
        this.policy = policy;
        this.source = source;
        this.externalId = externalId;
        update(sourceUrl, lastCollectedAt);
    }

    public void update(String sourceUrl, Instant lastCollectedAt) {
        this.sourceUrl = sourceUrl;
        this.lastCollectedAt = lastCollectedAt;
    }

    public void markSeen(Instant collectedAt) {
        this.lastCollectedAt = collectedAt;
    }

    public PolicyEntity getPolicy() {
        return policy;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Instant getLastCollectedAt() {
        return lastCollectedAt;
    }
}
