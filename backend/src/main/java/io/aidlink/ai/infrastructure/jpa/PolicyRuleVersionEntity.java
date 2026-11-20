package io.aidlink.ai.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "policy_rule_version")
public class PolicyRuleVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private PolicyEntity policy;

    @Column(nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode ruleJson;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected PolicyRuleVersionEntity() {
    }

    public PolicyRuleVersionEntity(
            PolicyEntity policy,
            int version,
            JsonNode ruleJson,
            Instant effectiveFrom,
            Instant verifiedAt
    ) {
        this.policy = policy;
        this.version = version;
        this.ruleJson = ruleJson.deepCopy();
        this.effectiveFrom = effectiveFrom;
        this.verifiedAt = verifiedAt;
    }

    public int getVersion() {
        return version;
    }

    public void closeAt(Instant effectiveTo) {
        this.effectiveTo = effectiveTo;
    }
}
