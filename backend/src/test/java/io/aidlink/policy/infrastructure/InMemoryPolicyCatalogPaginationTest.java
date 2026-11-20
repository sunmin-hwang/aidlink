package io.aidlink.policy.infrastructure;

import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.application.PolicySearchCriteria;
import io.aidlink.policy.application.PolicySearchSort;
import io.aidlink.policy.domain.Policy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPolicyCatalogPaginationTest {

    private final InMemoryPolicyCatalog catalog = new InMemoryPolicyCatalog();

    @Test
    void paginatesSortedResultsAndClampsPagesPastTheEnd() {
        PageResult<Policy> first = catalog.search(criteria(0, 2, PolicySearchSort.NAME, null));
        PageResult<Policy> second = catalog.search(criteria(1, 2, PolicySearchSort.NAME, null));
        PageResult<Policy> pastEnd = catalog.search(criteria(99, 2, PolicySearchSort.NAME, null));

        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.content()).hasSize(2);
        assertThat(second.content()).hasSize(2);
        assertThat(first.content()).doesNotContainAnyElementsOf(second.content());
        assertThat(pastEnd.page()).isEqualTo(2);
        assertThat(pastEnd.content()).hasSize(1);
        assertThat(pastEnd.last()).isTrue();
    }

    @Test
    void appliesRuleConstraintsBeforeCalculatingTotalElements() {
        PageResult<Policy> first = catalog.search(criteria(0, 2, PolicySearchSort.NAME, 35));
        PageResult<Policy> second = catalog.search(criteria(1, 2, PolicySearchSort.NAME, 35));

        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.content()).hasSize(2);
        assertThat(second.content()).hasSize(1);
    }

    @Test
    void resolvesDefaultAndInvalidSortValuesFromTheQuery() {
        assertThat(PolicySearchSort.resolve(null, "월세"))
                .isEqualTo(PolicySearchSort.RELEVANCE);
        assertThat(PolicySearchSort.resolve(null, null))
                .isEqualTo(PolicySearchSort.RECENT);
        assertThat(PolicySearchSort.resolve("relevance", null))
                .isEqualTo(PolicySearchSort.RECENT);
        assertThat(PolicySearchSort.resolve("invalid", "월세"))
                .isEqualTo(PolicySearchSort.RELEVANCE);
    }

    private PolicySearchCriteria criteria(
            int page,
            int size,
            PolicySearchSort sort,
            Integer age
    ) {
        return new PolicySearchCriteria(
                null,
                null,
                null,
                null,
                false,
                age,
                null,
                null,
                page,
                size,
                sort
        );
    }
}
