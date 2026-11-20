package io.aidlink.policy.domain;

import io.aidlink.eligibility.domain.RuleNode;

import java.time.LocalDate;
import java.time.Instant;

public record Policy(
        long id,
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
        Instant sourceUpdatedAt,
        PolicyLifecycleStatus lifecycleStatus,
        Instant lastSeenAt,
        int missedCollectionCount,
        PolicyAnalysisStatus analysisStatus,
        RuleNode rule
) {

    public Policy(
            long id,
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
            Instant sourceUpdatedAt,
            PolicyAnalysisStatus analysisStatus,
            RuleNode rule
    ) {
        this(
                id, externalId, name, provider, category, regionCode, description,
                targetDescription, supportContent, applicationStart, applicationEnd,
                applicationMethod, requiredDocuments, applicationUrl, sourceUrl,
                sourceUpdatedAt, PolicyLifecycleStatus.ACTIVE, null, 0,
                analysisStatus, rule
        );
    }
}

