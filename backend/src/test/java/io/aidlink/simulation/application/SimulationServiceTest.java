package io.aidlink.simulation.application;

import io.aidlink.eligibility.application.ConditionValueResolver;
import io.aidlink.eligibility.application.EligibilityService;
import io.aidlink.eligibility.application.NearMissClassifier;
import io.aidlink.eligibility.application.RuleEngine;
import io.aidlink.policy.infrastructure.InMemoryPolicyCatalog;
import io.aidlink.simulation.domain.SimulationChangeStatus;
import io.aidlink.simulation.domain.SimulationResult;
import io.aidlink.user.domain.EmploymentStatus;
import io.aidlink.user.domain.HomeOwnership;
import io.aidlink.user.domain.ProfileChanges;
import io.aidlink.user.domain.UserProfile;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.aidlink.observability.AidLinkMetrics;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationServiceTest {

    @Test
    void employmentChangeCanMakePolicyNewlyEligible() {
        InMemoryPolicyCatalog catalog = new InMemoryPolicyCatalog();
        AidLinkMetrics metrics = new AidLinkMetrics(new SimpleMeterRegistry());
        EligibilityService eligibilityService = new EligibilityService(
                new RuleEngine(new ConditionValueResolver()),
                new NearMissClassifier(),
                metrics
        );
        SimulationService service = new SimulationService(catalog, eligibilityService, metrics);
        UserProfile current = new UserProfile(
                LocalDate.of(2000, 1, 1),
                "SEOUL",
                EmploymentStatus.UNEMPLOYED,
                null,
                new BigDecimal("800000"),
                null,
                null,
                null,
                null,
                null,
                null,
                HomeOwnership.NONE,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ProfileChanges changes = new ProfileChanges(
                null,
                null,
                EmploymentStatus.EMPLOYED,
                null,
                new BigDecimal("2500000"),
                null,
                null,
                40,
                null,
                null,
                "RENT",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        SimulationResult result = service.simulate(
                current,
                changes,
                LocalDate.of(2026, 10, 7),
                new SimulationCriteria(0, 20, null, SimulationSort.IMPORTANCE)
        );

        assertThat(result.content())
                .filteredOn(policy -> policy.policyId() == 3L)
                .extracting(policy -> policy.changeStatus())
                .containsExactly(SimulationChangeStatus.NEWLY_ELIGIBLE);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void returnsOnlyTheRequestedPageAndSupportsStatusFiltering() {
        InMemoryPolicyCatalog catalog = new InMemoryPolicyCatalog();
        AidLinkMetrics metrics = new AidLinkMetrics(new SimpleMeterRegistry());
        EligibilityService eligibilityService = new EligibilityService(
                new RuleEngine(new ConditionValueResolver()),
                new NearMissClassifier(),
                metrics
        );
        SimulationService service = new SimulationService(catalog, eligibilityService, metrics);
        UserProfile current = new UserProfile(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
        ProfileChanges noChanges = new ProfileChanges(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );

        SimulationResult lastPage = service.simulate(
                current,
                noChanges,
                LocalDate.of(2026, 10, 7),
                new SimulationCriteria(99, 2, null, SimulationSort.NAME)
        );

        assertThat(lastPage.content()).hasSize(1);
        assertThat(lastPage.totalElements()).isEqualTo(5);
        assertThat(lastPage.totalPages()).isEqualTo(3);
        assertThat(lastPage.page()).isEqualTo(2);
        assertThat(lastPage.last()).isTrue();
        assertThat(lastPage.summary().values()).containsOnly(0L, 5L);

        SimulationResult filtered = service.simulate(
                current,
                noChanges,
                LocalDate.of(2026, 10, 7),
                new SimulationCriteria(
                        0,
                        20,
                        SimulationChangeStatus.NEWLY_ELIGIBLE,
                        SimulationSort.IMPORTANCE
                )
        );

        assertThat(filtered.content()).isEmpty();
        assertThat(filtered.totalElements()).isZero();
        assertThat(filtered.totalPages()).isZero();
    }
}
