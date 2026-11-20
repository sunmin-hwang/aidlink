package io.aidlink.simulation.application;

import java.util.Locale;

public enum SimulationSort {
    IMPORTANCE,
    NAME;

    public static SimulationSort resolve(String value) {
        if (value == null || value.isBlank()) {
            return IMPORTANCE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return IMPORTANCE;
        }
    }

    public String queryValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
