package io.aidlink.policy.infrastructure.jpa;

import io.aidlink.policy.application.PolicyCatalog;
import io.aidlink.policy.domain.PolicyLifecycleStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "AIDLINK_RUN_POSTGRES_TEST", matches = "true")
class PostgresPolicyCatalogIntegrationTest {

    @Autowired
    private PolicyCatalog policyCatalog;

    @Autowired
    private PolicyJpaRepository policyRepository;

    @Test
    void sampleFixturesAreArchivedAndExcludedFromPublicCatalog() {
        assertThat(policyRepository.findByExternalId("SAMPLE-SEOUL-HOUSING"))
                .isPresent()
                .get()
                .satisfies(policy -> assertThat(policy.getLifecycleStatus())
                        .isEqualTo(PolicyLifecycleStatus.ARCHIVED));
        assertThat(policyCatalog.findById(1L)).isEmpty();
        assertThat(policyCatalog.findAll())
                .noneMatch(policy -> policy.externalId().startsWith("SAMPLE-"));
    }
}
