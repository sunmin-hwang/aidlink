package io.aidlink.collector.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.collector.domain.CollectedPolicyPage;
import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.aidlink.observability.AidLinkMetrics;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCollectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FakeStore store = new FakeStore();
    private final MutableClient client = new MutableClient();
    private final CanonicalJsonHasher hasher = new CanonicalJsonHasher(objectMapper);
    private final PolicyCollector collector = new PolicyCollector(
            List.of(client),
            store,
            new RawPolicyChangeDetector(store, hasher),
            new AidLinkMetrics(new SimpleMeterRegistry())
    );

    @Test
    void storesOnlyNewOrChangedRawVersions() throws Exception {
        client.payload = objectMapper.readTree("""
                {"서비스ID":"SVC-1","서비스명":"청년 지원","지원내용":"100만원"}
                """);

        var first = collector.collectPage(PolicySource.GOV24, 1, 100);
        var second = collector.collectPage(PolicySource.GOV24, 1, 100);
        client.payload = objectMapper.readTree("""
                {"서비스명":"청년 지원","지원내용":"120만원","서비스ID":"SVC-1"}
                """);
        var third = collector.collectPage(PolicySource.GOV24, 1, 100);

        assertThat(first.results().getFirst().changeType()).isEqualTo(CollectionChangeType.CREATED);
        assertThat(second.results().getFirst().changeType()).isEqualTo(CollectionChangeType.UNCHANGED);
        assertThat(third.results().getFirst().changeType()).isEqualTo(CollectionChangeType.UPDATED);
        assertThat(store.saveCount).isEqualTo(2);
    }

    private static class MutableClient implements PolicySourceClient {
        private JsonNode payload;

        @Override
        public PolicySource source() {
            return PolicySource.GOV24;
        }

        @Override
        public CollectedPolicyPage fetchPage(int page, int perPage) {
            return new CollectedPolicyPage(
                    page,
                    perPage,
                    1,
                    List.of(new RawPolicyDocument(PolicySource.GOV24, "SVC-1", payload))
            );
        }
    }

    private static class FakeStore implements RawPolicyStore {
        private final Map<String, String> latestHashes = new HashMap<>();
        private int saveCount;

        @Override
        public Optional<String> findLatestContentHash(PolicySource source, String externalId) {
            return Optional.ofNullable(latestHashes.get(source + ":" + externalId));
        }

        @Override
        public void save(RawPolicyDocument document, String contentHash, Instant collectedAt) {
            latestHashes.put(document.source() + ":" + document.externalId(), contentHash);
            saveCount++;
        }
    }
}
