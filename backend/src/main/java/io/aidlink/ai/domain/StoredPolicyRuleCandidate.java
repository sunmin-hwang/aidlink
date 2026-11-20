package io.aidlink.ai.domain;

import java.time.Instant;

public record StoredPolicyRuleCandidate(
        long id,
        long policyId,
        String sourceTextHash,
        PolicyRuleCandidate candidate,
        Instant createdAt
) {
}
