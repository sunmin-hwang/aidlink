package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.EligibilityStatus;

import java.util.Locale;

public record EligibilityAnalysisCriteria(
        int page,
        int size,
        EligibilityStatus status,
        String category,
        String regionCode,
        EligibilityAnalysisSort sort
) {
    public EligibilityAnalysisCriteria {
        page = Math.max(page, 0);
        size = Math.clamp(size, 1, 100);
        category = normalize(category);
        regionCode = normalize(regionCode);
        sort = sort == null ? EligibilityAnalysisSort.RECOMMENDED : sort;
    }

    public static EligibilityStatus resolveStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EligibilityStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
