package io.aidlink.policy.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "policy_rule")
public class PolicyRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, unique = true)
    private PolicyEntity policy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode ruleJson;

    @Column(name = "validation_score", precision = 5, scale = 2)
    private BigDecimal validationScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PolicyAnalysisStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyRuleEntity() {
    }

    public PolicyRuleEntity(
            PolicyEntity policy,
            JsonNode ruleJson,
            BigDecimal validationScore,
            PolicyAnalysisStatus status,
            Instant createdAt
    ) {
        this.policy = policy;
        update(ruleJson, validationScore, status, createdAt);
    }

    public void update(
            JsonNode ruleJson,
            BigDecimal validationScore,
            PolicyAnalysisStatus status,
            Instant createdAt
    ) {
        this.ruleJson = ruleJson.deepCopy();
        this.validationScore = validationScore;
        this.status = status;
        this.createdAt = createdAt;
    }

    public JsonNode getRuleJson() {
        return ruleJson;
    }

    public PolicyEntity getPolicy() {
        return policy;
    }
}

