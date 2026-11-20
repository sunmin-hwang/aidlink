package io.aidlink.simulation.domain;

import java.util.List;
import java.util.Map;

public record SimulationResult(
        List<SimulationPolicyResult> content,
        Map<SimulationChangeStatus, Long> summary,
        long totalElements,
        int totalPages,
        int page,
        int size,
        String sort,
        SimulationChangeStatus status,
        boolean first,
        boolean last
) {
    public SimulationResult {
        content = List.copyOf(content);
        summary = Map.copyOf(summary);
    }
}

