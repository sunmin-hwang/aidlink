package io.aidlink.ai.infrastructure.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aidlink.ai.application.PolicyRuleCandidateStore;
import io.aidlink.ai.domain.PolicyRuleCandidate;
import io.aidlink.ai.domain.StoredPolicyRuleCandidate;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
import io.aidlink.policy.infrastructure.jpa.PolicyJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
@Profile("postgres")
public class JpaPolicyRuleCandidateStore implements PolicyRuleCandidateStore {

    private final PolicyRuleCandidateJpaRepository candidateRepository;
    private final PolicyJpaRepository policyRepository;
    private final ObjectMapper objectMapper;

    public JpaPolicyRuleCandidateStore(
            PolicyRuleCandidateJpaRepository candidateRepository,
            PolicyJpaRepository policyRepository,
            ObjectMapper objectMapper
    ) {
        this.candidateRepository = candidateRepository;
        this.policyRepository = policyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public StoredPolicyRuleCandidate save(
            long policyId,
            String sourceTextHash,
            PolicyRuleCandidate candidate
    ) {
        PolicyEntity policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        policy.markAiParsed();

        ObjectNode candidateJson = objectMapper.createObjectNode();
        candidateJson.set("rule", candidate.rule());
        candidateJson.set("ambiguities", objectMapper.valueToTree(candidate.ambiguities()));
        PolicyRuleCandidateEntity saved = candidateRepository.save(new PolicyRuleCandidateEntity(
                policy,
                sourceTextHash,
                candidateJson,
                candidate.model(),
                candidate.responseId(),
                candidate.inputTokens(),
                candidate.outputTokens(),
                Instant.now()
        ));
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredPolicyRuleCandidate> findLatest(long policyId) {
        return candidateRepository.findFirstByPolicyIdOrderByCreatedAtDesc(policyId)
                .map(this::toDomain);
    }

    private StoredPolicyRuleCandidate toDomain(PolicyRuleCandidateEntity entity) {
        var json = entity.getCandidateJson();
        var ambiguities = new java.util.ArrayList<String>();
        json.path("ambiguities").forEach(value -> ambiguities.add(value.asText()));
        PolicyRuleCandidate candidate = new PolicyRuleCandidate(
                json.path("rule"),
                ambiguities,
                entity.getParserModel(),
                entity.getResponseId(),
                entity.getInputTokens(),
                entity.getOutputTokens()
        );
        return new StoredPolicyRuleCandidate(
                entity.getId(),
                entity.getPolicy().getId(),
                entity.getSourceTextHash(),
                candidate,
                entity.getCreatedAt()
        );
    }
}
