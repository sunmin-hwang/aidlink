package io.aidlink.simulation.domain;

import io.aidlink.eligibility.domain.EligibilityAnalysis;

public record SimulationPolicyResult(
        long policyId,
        String policyName,
        SimulationChangeStatus changeStatus,
        EligibilityAnalysis current,
        EligibilityAnalysis simulated
) {
}

