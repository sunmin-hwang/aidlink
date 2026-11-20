package io.aidlink.ai.infrastructure.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aidlink.ai.application.PolicyRuleValidationStore;
import io.aidlink.ai.domain.PolicyRuleValidationResult;
import io.aidlink.ai.domain.RuleValidationStatus;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
import io.aidlink.policy.infrastructure.jpa.PolicyJpaRepository;
import io.aidlink.policy.infrastructure.jpa.PolicyRuleEntity;
import io.aidlink.policy.infrastructure.jpa.PolicyRuleJpaRepository;
import io.aidlink.policy.application.PublicPolicyCache;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import com.fasterxml.jackson.core.type.TypeReference;
import io.aidlink.ai.domain.RuleValidationBreakdown;
import io.aidlink.ai.domain.RuleValidationIssue;

@Repository
@Profile("postgres")
public class JpaPolicyRuleValidationStore implements PolicyRuleValidationStore {

    private final PolicyRuleCandidateJpaRepository candidateRepository;
    private final PolicyRuleValidationJpaRepository validationRepository;
    private final PolicyJpaRepository policyRepository;
    private final PolicyRuleJpaRepository ruleRepository;
    private final PolicyRuleVersionJpaRepository versionRepository;
    private final ObjectMapper objectMapper;
    private final PublicPolicyCache publicPolicyCache;

    public JpaPolicyRuleValidationStore(
            PolicyRuleCandidateJpaRepository candidateRepository,
            PolicyRuleValidationJpaRepository validationRepository,
            PolicyJpaRepository policyRepository,
            PolicyRuleJpaRepository ruleRepository,
            PolicyRuleVersionJpaRepository versionRepository,
            ObjectMapper objectMapper,
            PublicPolicyCache publicPolicyCache
    ) {
        this.candidateRepository = candidateRepository;
        this.validationRepository = validationRepository;
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
        this.publicPolicyCache = publicPolicyCache;
    }

    @Override
    @Transactional
    public void save(PolicyRuleValidationResult result) {
        if (validationRepository.existsByCandidateId(result.candidateId())) {
            return;
        }
        PolicyRuleCandidateEntity candidate = candidateRepository.findById(result.candidateId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rule candidate not found: " + result.candidateId()));
        PolicyEntity policy = policyRepository.findById(result.policyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Policy not found: " + result.policyId()));

        ObjectNode report = objectMapper.createObjectNode();
        report.set("breakdown", objectMapper.valueToTree(result.breakdown()));
        report.set("issues", objectMapper.valueToTree(result.issues()));
        validationRepository.save(new PolicyRuleValidationEntity(
                candidate,
                BigDecimal.valueOf(result.score()),
                result.status(),
                report,
                Instant.now()
        ));

        if (result.status() == RuleValidationStatus.AUTO_VALIDATED
                && policy.getAnalysisStatus() == PolicyAnalysisStatus.AI_PARSED) {
            promoteRule(policy, result);
            policy.markAutoValidated();
        } else {
            policy.markReviewRequired();
        }
        publicPolicyCache.evictAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PolicyRuleValidationResult> findLatest(long policyId) {
        return validationRepository.findFirstByCandidatePolicyIdOrderByCreatedAtDesc(policyId)
                .map(this::toDomain);
    }

    private PolicyRuleValidationResult toDomain(PolicyRuleValidationEntity entity) {
        try {
            var report = entity.getReportJson();
            var candidate = entity.getCandidate();
            return new PolicyRuleValidationResult(
                    candidate.getId(),
                    candidate.getPolicy().getId(),
                    entity.getStatus(),
                    objectMapper.treeToValue(
                            report.path("breakdown"), RuleValidationBreakdown.class),
                    objectMapper.convertValue(
                            report.path("issues"), new TypeReference<java.util.List<RuleValidationIssue>>() { }),
                    candidate.getCandidateJson().path("rule")
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Stored validation report is invalid", exception);
        }
    }

    private void promoteRule(PolicyEntity policy, PolicyRuleValidationResult result) {
        Instant now = Instant.now();
        BigDecimal score = BigDecimal.valueOf(result.score());
        PolicyRuleEntity activeRule = ruleRepository.findByPolicyId(policy.getId())
                .orElseGet(() -> new PolicyRuleEntity(
                        policy,
                        result.rule(),
                        score,
                        PolicyAnalysisStatus.AUTO_VALIDATED,
                        now
                ));
        activeRule.update(
                result.rule(), score, PolicyAnalysisStatus.AUTO_VALIDATED, now);
        ruleRepository.save(activeRule);

        versionRepository.findFirstByPolicyIdAndEffectiveToIsNull(policy.getId())
                .ifPresent(version -> version.closeAt(now));
        int nextVersion = versionRepository.findFirstByPolicyIdOrderByVersionDesc(policy.getId())
                .map(version -> version.getVersion() + 1)
                .orElse(1);
        versionRepository.save(new PolicyRuleVersionEntity(
                policy,
                nextVersion,
                result.rule(),
                now,
                null
        ));
    }
}
