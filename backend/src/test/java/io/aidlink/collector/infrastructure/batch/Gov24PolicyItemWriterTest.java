package io.aidlink.collector.infrastructure.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.collector.application.NormalizedPolicyStore;
import io.aidlink.collector.application.RawPolicyStore;
import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.collector.domain.NormalizedPolicy;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import io.aidlink.collector.domain.RawPolicyVersionCandidate;
import io.aidlink.observability.AidLinkMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Gov24PolicyItemWriterTest {

    @Test
    void unchangedPolicyOnlyRefreshesLastSeenMetadata() throws Exception {
        FakeRawStore rawStore = new FakeRawStore();
        FakeNormalizedStore normalizedStore = new FakeNormalizedStore();
        Gov24PolicyItemWriter writer = new Gov24PolicyItemWriter(
                rawStore,
                normalizedStore,
                new AidLinkMetrics(new SimpleMeterRegistry())
        );
        Instant collectedAt = Instant.parse("2026-11-13T00:00:00Z");
        RawPolicyDocument document = new RawPolicyDocument(
                PolicySource.GOV24,
                "UNCHANGED-1",
                new ObjectMapper().readTree("{\"서비스ID\":\"UNCHANGED-1\"}")
        );

        writer.write(Chunk.of(new RawPolicyVersionCandidate(
                document,
                "hash",
                CollectionChangeType.UNCHANGED,
                null,
                collectedAt
        )));

        assertThat(rawStore.saveCount).isZero();
        assertThat(normalizedStore.upsertCount).isZero();
        assertThat(normalizedStore.markSeenCount).isEqualTo(1);
        assertThat(normalizedStore.lastSeenExternalId).isEqualTo("UNCHANGED-1");
        assertThat(normalizedStore.lastSeenAt).isEqualTo(collectedAt);
    }

    private static class FakeRawStore implements RawPolicyStore {
        private int saveCount;

        @Override
        public Optional<String> findLatestContentHash(PolicySource source, String externalId) {
            return Optional.empty();
        }

        @Override
        public void save(RawPolicyDocument document, String contentHash, Instant collectedAt) {
            saveCount++;
        }
    }

    private static class FakeNormalizedStore implements NormalizedPolicyStore {
        private int upsertCount;
        private int markSeenCount;
        private String lastSeenExternalId;
        private Instant lastSeenAt;

        @Override
        public void upsert(
                NormalizedPolicy policy,
                CollectionChangeType changeType,
                Instant collectedAt
        ) {
            upsertCount++;
        }

        @Override
        public void markSeen(
                PolicySource source,
                String sourceExternalId,
                Instant collectedAt
        ) {
            markSeenCount++;
            lastSeenExternalId = sourceExternalId;
            lastSeenAt = collectedAt;
        }

        @Override
        public void completeSuccessfulCollection(
                PolicySource source,
                Instant collectionStartedAt,
                int sourceRemovedThreshold
        ) {
        }
    }
}
