package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.observability.AidLinkMetrics;
import io.aidlink.policy.infrastructure.InMemoryPolicyCatalog;
import io.aidlink.user.domain.UserProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EligibilityAnalysisQueryServiceTest {

    private final InMemoryPolicyCatalog catalog = new InMemoryPolicyCatalog();
    private final EligibilityAnalysisQueryService queryService =
            new EligibilityAnalysisQueryService(
                    catalog,
                    new EligibilityService(
                            new RuleEngine(new ConditionValueResolver()),
                            new NearMissClassifier(),
                            new AidLinkMetrics(new SimpleMeterRegistry())
                    )
            );

    @Test
    void returnsOnlyTheRequestedPageWithSummaryCounts() {
        EligibilityAnalysisPage page = queryService.analyze(
                emptyProfile(),
                LocalDate.of(2026, 11, 13),
                criteria(0, 2, null, null, EligibilityAnalysisSort.RECOMMENDED)
        );

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.summary()).containsEntry(EligibilityStatus.UNKNOWN, 5L);
        assertThat(page.categories()).contains("주거", "취업", "생활");
    }

    @Test
    void clampsPagesAndAppliesStatusAndCategoryFilters() {
        EligibilityAnalysisPage pastEnd = queryService.analyze(
                emptyProfile(),
                LocalDate.of(2026, 11, 13),
                criteria(99, 2, null, null, EligibilityAnalysisSort.RECOMMENDED)
        );
        EligibilityAnalysisPage eligible = queryService.analyze(
                emptyProfile(),
                LocalDate.of(2026, 11, 13),
                criteria(0, 20, EligibilityStatus.ELIGIBLE, null,
                        EligibilityAnalysisSort.RECOMMENDED)
        );
        EligibilityAnalysisPage jobs = queryService.analyze(
                emptyProfile(),
                LocalDate.of(2026, 11, 13),
                criteria(0, 20, null, "취업", EligibilityAnalysisSort.NAME)
        );

        assertThat(pastEnd.page()).isEqualTo(2);
        assertThat(pastEnd.content()).hasSize(1);
        assertThat(eligible.totalElements()).isZero();
        assertThat(eligible.content()).isEmpty();
        assertThat(jobs.totalElements()).isEqualTo(2);
        assertThat(jobs.content()).extracting("category").containsOnly("취업");
    }

    private EligibilityAnalysisCriteria criteria(
            int page,
            int size,
            EligibilityStatus status,
            String category,
            EligibilityAnalysisSort sort
    ) {
        return new EligibilityAnalysisCriteria(
                page,
                size,
                status,
                category,
                null,
                sort
        );
    }

    private UserProfile emptyProfile() {
        return new UserProfile(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }
}
