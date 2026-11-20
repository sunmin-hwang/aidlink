package io.aidlink.collector.application;

import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.collector.domain.RawPolicyDocument;
import org.springframework.stereotype.Component;

@Component
public class RawPolicyChangeDetector {

    private final RawPolicyStore rawPolicyStore;
    private final CanonicalJsonHasher hasher;

    public RawPolicyChangeDetector(RawPolicyStore rawPolicyStore, CanonicalJsonHasher hasher) {
        this.rawPolicyStore = rawPolicyStore;
        this.hasher = hasher;
    }

    public DetectedRawPolicy detect(RawPolicyDocument document) {
        String contentHash = hasher.hash(document.payload());
        CollectionChangeType changeType = rawPolicyStore
                .findLatestContentHash(document.source(), document.externalId())
                .map(previousHash -> previousHash.equals(contentHash)
                        ? CollectionChangeType.UNCHANGED
                        : CollectionChangeType.UPDATED)
                .orElse(CollectionChangeType.CREATED);
        return new DetectedRawPolicy(document, contentHash, changeType);
    }

    public record DetectedRawPolicy(
            RawPolicyDocument document,
            String contentHash,
            CollectionChangeType changeType
    ) {
    }
}
