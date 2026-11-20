package io.aidlink.interfaces.api;

import io.aidlink.user.domain.ProfileChanges;
import io.aidlink.user.domain.UserProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SimulationRequest(
        @NotNull @Valid UserProfile currentProfile,
        @NotNull @Valid ProfileChanges changes,
        LocalDate evaluationDate
) {
    public LocalDate effectiveDate() {
        return evaluationDate == null ? LocalDate.now() : evaluationDate;
    }
}

