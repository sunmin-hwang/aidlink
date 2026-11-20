package io.aidlink.collector.infrastructure.batch;

import io.aidlink.collector.application.Gov24PolicyNormalizer;
import io.aidlink.collector.application.RawPolicyChangeDetector;
import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.collector.domain.RawPolicyDocument;
import io.aidlink.collector.domain.RawPolicyVersionCandidate;
import org.springframework.batch.item.ItemProcessor;

import java.time.Instant;

public class Gov24PolicyItemProcessor
        implements ItemProcessor<RawPolicyDocument, RawPolicyVersionCandidate> {

    private final RawPolicyChangeDetector changeDetector;
    private final Gov24PolicyNormalizer normalizer;

    public Gov24PolicyItemProcessor(
            RawPolicyChangeDetector changeDetector,
            Gov24PolicyNormalizer normalizer
    ) {
        this.changeDetector = changeDetector;
        this.normalizer = normalizer;
    }

    @Override
    public RawPolicyVersionCandidate process(RawPolicyDocument item) {
        var detected = changeDetector.detect(item);
        return new RawPolicyVersionCandidate(
                item,
                detected.contentHash(),
                detected.changeType(),
                detected.changeType() == CollectionChangeType.UNCHANGED
                        ? null
                        : normalizer.normalize(item),
                Instant.now()
        );
    }
}
