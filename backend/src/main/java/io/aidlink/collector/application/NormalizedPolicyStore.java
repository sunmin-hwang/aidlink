package io.aidlink.collector.application;

import io.aidlink.collector.domain.NormalizedPolicy;
import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.collector.domain.PolicySource;

import java.time.Instant;

public interface NormalizedPolicyStore {

    void upsert(
            NormalizedPolicy policy,
            CollectionChangeType changeType,
            Instant collectedAt
    );

    void markSeen(PolicySource source, String sourceExternalId, Instant collectedAt);

    void completeSuccessfulCollection(
            PolicySource source,
            Instant collectionStartedAt,
            int sourceRemovedThreshold
    );
}
