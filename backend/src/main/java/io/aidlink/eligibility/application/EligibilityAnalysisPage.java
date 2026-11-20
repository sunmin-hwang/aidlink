package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.EligibilityAnalysis;
import io.aidlink.eligibility.domain.EligibilityStatus;

import java.util.List;
import java.util.Map;

public record EligibilityAnalysisPage(
        List<EligibilityAnalysis> content,
        Map<EligibilityStatus, Long> summary,
        List<String> categories,
        long totalElements,
        int totalPages,
        int page,
        int size,
        String sort,
        EligibilityStatus status,
        boolean first,
        boolean last
) {
    public EligibilityAnalysisPage {
        content = List.copyOf(content);
        summary = Map.copyOf(summary);
        categories = List.copyOf(categories);
    }
}
