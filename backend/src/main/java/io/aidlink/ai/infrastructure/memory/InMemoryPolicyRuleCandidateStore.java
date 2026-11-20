package io.aidlink.ai.infrastructure.memory;

import io.aidlink.ai.application.PolicyRuleCandidateStore;
import io.aidlink.ai.domain.PolicyRuleCandidate;
import io.aidlink.ai.domain.StoredPolicyRuleCandidate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("!postgres")
public class InMemoryPolicyRuleCandidateStore implements PolicyRuleCandidateStore {

    private final AtomicLong sequence = new AtomicLong();
    private final Map<Long, StoredPolicyRuleCandidate> latestByPolicy = new ConcurrentHashMap<>();

    @Override
    public StoredPolicyRuleCandidate save(
            long policyId,
            String sourceTextHash,
            PolicyRuleCandidate candidate
    ) {
        StoredPolicyRuleCandidate stored = new StoredPolicyRuleCandidate(
                sequence.incrementAndGet(),
                policyId,
                sourceTextHash,
                candidate,
                Instant.now()
        );
        latestByPolicy.put(policyId, stored);
        return stored;
    }

    @Override
    public Optional<StoredPolicyRuleCandidate> findLatest(long policyId) {
        return Optional.ofNullable(latestByPolicy.get(policyId));
    }
}
