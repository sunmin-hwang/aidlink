package io.aidlink.collector.domain;

public record PolicyCollectionResult(
        String externalId,
        String contentHash,
        CollectionChangeType changeType
) {
}
