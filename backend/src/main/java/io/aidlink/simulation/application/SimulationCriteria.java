package io.aidlink.simulation.application;

import io.aidlink.simulation.domain.SimulationChangeStatus;

import java.util.Locale;

public record SimulationCriteria(
        int page,
        int size,
        SimulationChangeStatus status,
        SimulationSort sort
) {
    public SimulationCriteria {
        page = Math.max(page, 0);
        size = Math.clamp(size, 1, 100);
        sort = sort == null ? SimulationSort.IMPORTANCE : sort;
    }

    public static SimulationChangeStatus resolveStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SimulationChangeStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
