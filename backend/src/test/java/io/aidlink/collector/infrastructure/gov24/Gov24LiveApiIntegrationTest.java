package io.aidlink.collector.infrastructure.gov24;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.observability.AidLinkMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "AIDLINK_RUN_GOV24_TEST", matches = "true")
class Gov24LiveApiIntegrationTest {

    @Test
    void fetchesAndParsesTheLiveOfficialContract() {
        String serviceKey = System.getenv("AIDLINK_GOV24_SERVICE_KEY");
        assertThat(serviceKey)
                .as("AIDLINK_GOV24_SERVICE_KEY must be set for the opt-in live test")
                .isNotBlank();

        Gov24ApiProperties properties = new Gov24ApiProperties();
        properties.setServiceKey(serviceKey);
        var client = new Gov24PolicySourceClient(
                RestClient.builder().baseUrl(properties.getBaseUrl()).build(),
                properties,
                new AidLinkMetrics(new SimpleMeterRegistry())
        );

        var page = client.fetchPage(1, 2);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.perPage()).isEqualTo(2);
        assertThat(page.totalCount()).isPositive();
        assertThat(page.policies()).hasSizeBetween(1, 2).allSatisfy(policy -> {
            assertThat(policy.source()).isEqualTo(PolicySource.GOV24);
            assertThat(policy.externalId()).isNotBlank();
            assertThat(policy.payload().isObject()).isTrue();
            assertThat(policy.payload().has("상세조회URL")).isTrue();
            assertThat(policy.payload().has("온라인신청사이트URL")).isTrue();
        });
    }
}
