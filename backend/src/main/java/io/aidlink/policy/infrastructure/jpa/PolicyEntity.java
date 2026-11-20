package io.aidlink.policy.infrastructure.jpa;

import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.domain.PolicyLifecycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity
@Table(name = "policy")
public class PolicyEntity {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, length = 160)
    private String externalId;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(nullable = false, length = 200)
    private String provider;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(name = "region_code", nullable = false, length = 40)
    private String regionCode;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "target_description", nullable = false, columnDefinition = "text")
    private String targetDescription;

    @Column(name = "support_content", nullable = false, columnDefinition = "text")
    private String supportContent;

    @Column(name = "application_start")
    private LocalDate applicationStart;

    @Column(name = "application_end")
    private LocalDate applicationEnd;

    @Column(name = "application_method", nullable = false, columnDefinition = "text")
    private String applicationMethod;

    @Column(name = "required_documents", nullable = false, columnDefinition = "text")
    private String requiredDocuments;

    @Column(name = "application_url", nullable = false, columnDefinition = "text")
    private String applicationUrl;

    @Column(name = "source_url", nullable = false, columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "verified_source_url")
    private String verifiedSourceUrl;

    @Column(name = "source_link_verified_at")
    private Instant sourceLinkVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 40)
    private PolicyAnalysisStatus analysisStatus;

    @Column(name = "source_updated_at")
    private Instant sourceUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 40)
    private PolicyLifecycleStatus lifecycleStatus = PolicyLifecycleStatus.ACTIVE;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "missed_collection_count", nullable = false)
    private int missedCollectionCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "policy", fetch = FetchType.LAZY)
    private PolicyRuleEntity rule;

    protected PolicyEntity() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public PolicyEntity(
            String externalId,
            String name,
            String provider,
            String category,
            String regionCode,
            String description,
            String targetDescription,
            String supportContent,
            LocalDate applicationStart,
            LocalDate applicationEnd,
            String applicationMethod,
            String requiredDocuments,
            String applicationUrl,
            String sourceUrl,
            PolicyAnalysisStatus analysisStatus,
            Instant sourceUpdatedAt
    ) {
        this.externalId = externalId;
        update(
                name, provider, category, regionCode, description, targetDescription,
                supportContent, applicationStart, applicationEnd, applicationMethod,
                requiredDocuments, applicationUrl, sourceUrl, analysisStatus, sourceUpdatedAt
        );
    }

    public void update(
            String name,
            String provider,
            String category,
            String regionCode,
            String description,
            String targetDescription,
            String supportContent,
            LocalDate applicationStart,
            LocalDate applicationEnd,
            String applicationMethod,
            String requiredDocuments,
            String applicationUrl,
            String sourceUrl,
            PolicyAnalysisStatus analysisStatus,
            Instant sourceUpdatedAt
    ) {
        this.name = name;
        this.provider = provider;
        this.category = category;
        this.regionCode = regionCode;
        this.description = description;
        this.targetDescription = targetDescription;
        this.supportContent = supportContent;
        this.applicationStart = applicationStart;
        this.applicationEnd = applicationEnd;
        this.applicationMethod = applicationMethod;
        this.requiredDocuments = requiredDocuments;
        this.applicationUrl = applicationUrl;
        this.sourceUrl = sourceUrl;
        this.analysisStatus = analysisStatus;
        this.sourceUpdatedAt = sourceUpdatedAt;
    }

    public boolean markSeen(Instant seenAt) {
        PolicyLifecycleStatus previous = lifecycleStatus;
        lastSeenAt = seenAt;
        missedCollectionCount = 0;
        if (lifecycleStatus != PolicyLifecycleStatus.ARCHIVED) {
            lifecycleStatus = statusFromApplicationEnd();
        }
        return previous != lifecycleStatus;
    }

    public boolean markMissed(int sourceRemovedThreshold) {
        if (sourceRemovedThreshold < 2) {
            throw new IllegalArgumentException("sourceRemovedThreshold must be at least 2");
        }
        if (lifecycleStatus == PolicyLifecycleStatus.ARCHIVED) {
            return false;
        }
        PolicyLifecycleStatus previous = lifecycleStatus;
        missedCollectionCount++;
        lifecycleStatus = missedCollectionCount >= sourceRemovedThreshold
                ? PolicyLifecycleStatus.SOURCE_REMOVED
                : statusFromApplicationEnd();
        return previous != lifecycleStatus;
    }

    public void archive() {
        lifecycleStatus = PolicyLifecycleStatus.ARCHIVED;
        archivedAt = Instant.now();
    }

    public void restore() {
        missedCollectionCount = 0;
        archivedAt = null;
        lifecycleStatus = statusFromApplicationEnd();
    }

    private PolicyLifecycleStatus statusFromApplicationEnd() {
        return applicationEnd != null
                && applicationEnd.isBefore(LocalDate.now(SERVICE_ZONE))
                ? PolicyLifecycleStatus.EXPIRED
                : PolicyLifecycleStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public void markAiParsed() {
        this.analysisStatus = analysisStatus == PolicyAnalysisStatus.RAW
                ? PolicyAnalysisStatus.AI_PARSED
                : analysisStatus == PolicyAnalysisStatus.AI_PARSED
                ? PolicyAnalysisStatus.AI_PARSED
                : PolicyAnalysisStatus.REVIEW_REQUIRED;
    }

    public void markAutoValidated() {
        if (analysisStatus == PolicyAnalysisStatus.AI_PARSED) {
            this.analysisStatus = PolicyAnalysisStatus.AUTO_VALIDATED;
        }
    }

    public void markReviewRequired() {
        this.analysisStatus = PolicyAnalysisStatus.REVIEW_REQUIRED;
    }

    public void markHumanVerified() {
        this.analysisStatus = PolicyAnalysisStatus.HUMAN_VERIFIED;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public String getProvider() {
        return provider;
    }

    public String getCategory() {
        return category;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getDescription() {
        return description;
    }

    public String getTargetDescription() {
        return targetDescription;
    }

    public String getSupportContent() {
        return supportContent;
    }

    public LocalDate getApplicationStart() {
        return applicationStart;
    }

    public LocalDate getApplicationEnd() {
        return applicationEnd;
    }

    public String getApplicationMethod() {
        return applicationMethod;
    }

    public String getRequiredDocuments() {
        return requiredDocuments;
    }

    public String getApplicationUrl() {
        return verifiedSourceUrl != null && applicationUrl.equals(sourceUrl)
                ? verifiedSourceUrl : applicationUrl;
    }

    public String getSourceUrl() {
        return verifiedSourceUrl != null ? verifiedSourceUrl : sourceUrl;
    }

    public Instant getSourceUpdatedAt() {
        return sourceUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public PolicyAnalysisStatus getAnalysisStatus() {
        return analysisStatus;
    }

    public PolicyLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public int getMissedCollectionCount() {
        return missedCollectionCount;
    }

    public PolicyRuleEntity getRule() {
        return rule;
    }
}

