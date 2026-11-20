package io.aidlink.policy.application;

import java.util.Locale;

public enum PolicySearchSort {
    RELEVANCE,
    DEADLINE,
    RECENT,
    NAME;

    public static PolicySearchSort resolve(String requested, String query) {
        PolicySearchSort fallback = query == null || query.isBlank()
                ? RECENT
                : RELEVANCE;
        if (requested == null || requested.isBlank()) {
            return fallback;
        }
        try {
            PolicySearchSort resolved = valueOf(requested.trim().toUpperCase(Locale.ROOT));
            return resolved == RELEVANCE && (query == null || query.isBlank())
                    ? RECENT
                    : resolved;
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public String queryValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
