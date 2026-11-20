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
@Table(name = "policy_rule_candidate")
public class PolicyRuleCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private PolicyEntity policy;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_text_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String sourceTextHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode candidateJson;

    @Column(name = "parser_model", nullable = false, length = 120)
    private String parserModel;

    @Column(name = "response_id", length = 160)
    private String responseId;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PolicyRuleCandidateEntity() {
    }

    public PolicyRuleCandidateEntity(
            PolicyEntity policy,
            String sourceTextHash,
            JsonNode candidateJson,
            String parserModel,
            String responseId,
            long inputTokens,
            long outputTokens,
            Instant createdAt
    ) {
        this.policy = policy;
        this.sourceTextHash = sourceTextHash;
        this.candidateJson = candidateJson.deepCopy();
        this.parserModel = parserModel;
        this.responseId = responseId;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public PolicyEntity getPolicy() {
        return policy;
    }

    public String getSourceTextHash() {
        return sourceTextHash;
    }

    public JsonNode getCandidateJson() {
        return candidateJson;
    }

    public String getParserModel() {
        return parserModel;
    }

    public String getResponseId() {
        return responseId;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
