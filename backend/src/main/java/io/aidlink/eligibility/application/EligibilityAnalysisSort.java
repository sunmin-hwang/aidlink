package io.aidlink.eligibility.application;

import java.util.Locale;

public enum EligibilityAnalysisSort {
    RECOMMENDED,
    MISSING_COUNT,
    DEADLINE,
    NAME;

    public static EligibilityAnalysisSort resolve(String value) {
        if (value == null || value.isBlank()) {
            return RECOMMENDED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RECOMMENDED;
        }
    }

    public String queryValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
