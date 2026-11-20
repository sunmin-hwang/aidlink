package io.aidlink.collector.infrastructure.batch;

import io.aidlink.collector.application.NormalizedPolicyStore;
import io.aidlink.collector.application.RawPolicyStore;
import io.aidlink.collector.domain.RawPolicyVersionCandidate;
import io.aidlink.collector.domain.CollectionChangeType;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import io.aidlink.observability.AidLinkMetrics;

public class Gov24PolicyItemWriter implements ItemWriter<RawPolicyVersionCandidate> {

    private final RawPolicyStore rawPolicyStore;
    private final NormalizedPolicyStore normalizedPolicyStore;
    private final AidLinkMetrics metrics;

    public Gov24PolicyItemWriter(
            RawPolicyStore rawPolicyStore,
            NormalizedPolicyStore normalizedPolicyStore,
            AidLinkMetrics metrics
    ) {
        this.rawPolicyStore = rawPolicyStore;
        this.normalizedPolicyStore = normalizedPolicyStore;
        this.metrics = metrics;
    }

    @Override
    public void write(Chunk<? extends RawPolicyVersionCandidate> chunk) {
        for (RawPolicyVersionCandidate candidate : chunk) {
            if (candidate.changeType() == CollectionChangeType.UNCHANGED) {
                normalizedPolicyStore.markSeen(
                        candidate.document().source(),
                        candidate.document().externalId(),
                        candidate.collectedAt()
                );
            } else {
                rawPolicyStore.save(
                        candidate.document(),
                        candidate.contentHash(),
                        candidate.collectedAt()
                );
                normalizedPolicyStore.upsert(
                        candidate.normalizedPolicy(),
                        candidate.changeType(),
                        candidate.collectedAt()
                );
            }
            metrics.recordPolicyChange(candidate.changeType());
        }
    }
}
