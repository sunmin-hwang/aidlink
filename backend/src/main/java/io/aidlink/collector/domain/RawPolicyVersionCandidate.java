package io.aidlink.collector.domain;

import java.time.Instant;

public record RawPolicyVersionCandidate(
        RawPolicyDocument document,
        String contentHash,
        CollectionChangeType changeType,
        NormalizedPolicy normalizedPolicy,
        Instant collectedAt
) {
}
