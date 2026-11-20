package io.aidlink.collector.domain;

import io.aidlink.policy.domain.PolicyAnalysisStatus;

import java.time.Instant;
import java.time.LocalDate;

public record NormalizedPolicy(
        String externalId,
        PolicySource source,
        String sourceExternalId,
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
}
