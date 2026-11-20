package io.aidlink.collector.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.collector.domain.PolicySource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "raw_policy")
public class RawPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private PolicySource source;

    @Column(name = "external_id", nullable = false, length = 160)
    private String externalId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode rawPayload;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String contentHash;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    protected RawPolicyEntity() {
    }

    public RawPolicyEntity(
            PolicySource source,
            String externalId,
            JsonNode rawPayload,
            String contentHash,
            Instant collectedAt
    ) {
        this.source = source;
        this.externalId = externalId;
        this.rawPayload = rawPayload.deepCopy();
        this.contentHash = contentHash;
        this.collectedAt = collectedAt;
    }

    public String getContentHash() {
        return contentHash;
    }
}
