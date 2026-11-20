package io.aidlink.ai.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.ai.domain.RuleValidationStatus;
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
@Table(name = "policy_rule_validation")
public class PolicyRuleValidationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private PolicyRuleCandidateEntity candidate;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RuleValidationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode reportJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyRuleValidationEntity() {
    }

    public PolicyRuleValidationEntity(
            PolicyRuleCandidateEntity candidate,
            BigDecimal score,
            RuleValidationStatus status,
            JsonNode reportJson,
            Instant createdAt
    ) {
        this.candidate = candidate;
        this.score = score;
        this.status = status;
        this.reportJson = reportJson.deepCopy();
        this.createdAt = createdAt;
    }

    public PolicyRuleCandidateEntity getCandidate() {
        return candidate;
    }

    public BigDecimal getScore() {
        return score;
    }

    public RuleValidationStatus getStatus() {
        return status;
    }

    public JsonNode getReportJson() {
        return reportJson;
    }
}
