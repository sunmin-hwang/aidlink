package io.aidlink.collector.infrastructure.memory;

import io.aidlink.collector.application.NormalizedPolicyStore;
import io.aidlink.collector.domain.NormalizedPolicy;
import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.collector.domain.PolicySource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryNormalizedPolicyStore implements NormalizedPolicyStore {

    private final Map<String, NormalizedPolicy> policies = new ConcurrentHashMap<>();

    @Override
    public void upsert(
            NormalizedPolicy policy,
            CollectionChangeType changeType,
            Instant collectedAt
    ) {
        policies.put(policy.externalId(), policy);
    }

    @Override
    public void markSeen(PolicySource source, String sourceExternalId, Instant collectedAt) {
        // The in-memory profile does not persist collection lifecycle metadata.
    }

    @Override
    public void completeSuccessfulCollection(
            PolicySource source,
            Instant collectionStartedAt,
            int sourceRemovedThreshold
    ) {
        // The in-memory profile has no durable source inventory to reconcile.
    }
}
