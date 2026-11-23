package io.aidlink.policy.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.policy.domain.Policy;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.infrastructure.jpa.RuleJsonMapper;

import java.time.LocalDate;
import java.time.Instant;

public record PublicPolicyView(
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
        JsonNode rule,
        io.aidlink.policy.period.PublicApplicationPeriod applicationPeriod,
        io.aidlink.policy.domain.PolicyLifecycleStatus lifecycleStatus
) {
    public static PublicPolicyView from(Policy policy, RuleJsonMapper mapper) {
        return new PublicPolicyView(
                policy.id(),
                policy.externalId(),
                policy.name(),
                policy.provider(),
                policy.category(),
                policy.regionCode(),
                policy.description(),
                policy.targetDescription(),
                policy.supportContent(),
                policy.applicationStart(),
                policy.applicationEnd(),
                policy.applicationMethod(),
                policy.requiredDocuments(),
                PublicPolicyLink.usableUrl(policy.applicationUrl()),
                PublicPolicyLink.usableUrl(policy.sourceUrl()),
                policy.sourceUpdatedAt(),
                policy.analysisStatus(),
                policy.rule() == null ? null : mapper.toJson(policy.rule()),
                policy.applicationPeriod(),
                policy.lifecycleStatus()
        );
    }
}
