package io.aidlink.briefing.application;

import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.period.PublicApplicationPeriod;
import java.time.LocalDate;
import java.util.List;

public record BriefingContent(LocalDate evaluationDate, String regionCode,
                              int evaluatedCount, boolean candidateLimitReached,
                              String guidance, List<Item> items) {
    public BriefingContent { items = List.copyOf(items); }

    public record Item(long policyId, String name, String provider, String category,
                       EligibilityStatus status, PolicyAnalysisStatus analysisStatus,
                       String reason, List<String> checkConditions,
                       LocalDate applicationStart, LocalDate applicationEnd,
                       PublicApplicationPeriod applicationPeriod,
                       String sourceUrl, String applicationUrl) { }
}
