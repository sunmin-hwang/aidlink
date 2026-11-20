package io.aidlink.collector.application;

import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;

import java.time.Instant;
import java.util.Optional;

public interface RawPolicyStore {

    Optional<String> findLatestContentHash(PolicySource source, String externalId);

    void save(RawPolicyDocument document, String contentHash, Instant collectedAt);
}
