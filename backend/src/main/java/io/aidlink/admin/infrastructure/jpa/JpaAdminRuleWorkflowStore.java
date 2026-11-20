package io.aidlink.admin.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.admin.application.AdminRuleWorkflowStore;
import io.aidlink.ai.infrastructure.jpa.PolicyRuleVersionEntity;
import io.aidlink.ai.infrastructure.jpa.PolicyRuleVersionJpaRepository;
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

@Repository
@Profile("postgres")
public class JpaAdminRuleWorkflowStore implements AdminRuleWorkflowStore {

    private final PolicyJpaRepository policyRepository;
    private final PolicyRuleJpaRepository ruleRepository;
    private final PolicyRuleVersionJpaRepository versionRepository;
    private final PublicPolicyCache publicPolicyCache;

    public JpaAdminRuleWorkflowStore(
            PolicyJpaRepository policyRepository,
            PolicyRuleJpaRepository ruleRepository,
            PolicyRuleVersionJpaRepository versionRepository,
            PublicPolicyCache publicPolicyCache
    ) {
        this.policyRepository = policyRepository;
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.publicPolicyCache = publicPolicyCache;
    }

    @Override
    @Transactional
    public void saveDraft(long policyId, JsonNode rule) {
        PolicyEntity policy = requiredPolicy(policyId);
        PolicyRuleEntity draft = ruleRepository.findByPolicyId(policyId)
                .orElseGet(() -> new PolicyRuleEntity(
                        policy,
                        rule,
                        null,
                        PolicyAnalysisStatus.REVIEW_REQUIRED,
                        Instant.now()
                ));
        draft.update(rule, null, PolicyAnalysisStatus.REVIEW_REQUIRED, Instant.now());
        ruleRepository.save(draft);
        policy.markReviewRequired();
        publicPolicyCache.evictAll();
    }

    @Override
    @Transactional
    public void verify(long policyId) {
        PolicyEntity policy = requiredPolicy(policyId);
        PolicyRuleEntity rule = ruleRepository.findByPolicyId(policyId)
                .orElseThrow(() -> new IllegalStateException(
                        "No rule draft exists for policy: " + policyId));
        Instant now = Instant.now();
        rule.update(rule.getRuleJson(), BigDecimal.valueOf(100),
                PolicyAnalysisStatus.HUMAN_VERIFIED, now);

        versionRepository.findFirstByPolicyIdAndEffectiveToIsNull(policyId)
                .ifPresent(version -> version.closeAt(now));
        int nextVersion = versionRepository.findFirstByPolicyIdOrderByVersionDesc(policyId)
                .map(version -> version.getVersion() + 1)
                .orElse(1);
        versionRepository.save(new PolicyRuleVersionEntity(
                policy,
                nextVersion,
                rule.getRuleJson(),
                now,
                now
        ));
        policy.markHumanVerified();
        publicPolicyCache.evictAll();
    }

    private PolicyEntity requiredPolicy(long policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
    }
}
