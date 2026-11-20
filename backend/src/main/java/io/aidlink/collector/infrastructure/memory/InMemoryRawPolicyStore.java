package io.aidlink.collector.infrastructure.memory;

import io.aidlink.collector.application.RawPolicyStore;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryRawPolicyStore implements RawPolicyStore {

    private final Map<PolicyKey, StoredRawPolicy> latestPolicies = new ConcurrentHashMap<>();

    @Override
    public Optional<String> findLatestContentHash(PolicySource source, String externalId) {
        return Optional.ofNullable(latestPolicies.get(new PolicyKey(source, externalId)))
                .map(StoredRawPolicy::contentHash);
    }

    @Override
    public void save(RawPolicyDocument document, String contentHash, Instant collectedAt) {
        latestPolicies.put(
                new PolicyKey(document.source(), document.externalId()),
                new StoredRawPolicy(contentHash, document.payload().deepCopy(), collectedAt)
        );
    }

    private record PolicyKey(PolicySource source, String externalId) {
    }

    private record StoredRawPolicy(String contentHash, Object payload, Instant collectedAt) {
    }
}
