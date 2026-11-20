package io.aidlink.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.policy.infrastructure.jpa.RuleJsonMapper;
import io.aidlink.ai.application.PolicyRuleCandidateStore;
import io.aidlink.ai.application.PolicyRuleValidationStore;
import io.aidlink.policy.application.PolicyCatalog;
import org.springframework.stereotype.Service;

@Service
public class AdminReviewService {

    private final AdminReviewQueue reviewQueue;
    private final AdminRuleWorkflowStore ruleWorkflowStore;
    private final RuleJsonMapper ruleJsonMapper;
    private final PolicyCatalog policyCatalog;
    private final PolicyRuleCandidateStore candidateStore;
    private final PolicyRuleValidationStore validationStore;

    public AdminReviewService(
            AdminReviewQueue reviewQueue,
            AdminRuleWorkflowStore ruleWorkflowStore,
            RuleJsonMapper ruleJsonMapper,
            PolicyCatalog policyCatalog,
            PolicyRuleCandidateStore candidateStore,
            PolicyRuleValidationStore validationStore
    ) {
        this.reviewQueue = reviewQueue;
        this.ruleWorkflowStore = ruleWorkflowStore;
        this.ruleJsonMapper = ruleJsonMapper;
        this.policyCatalog = policyCatalog;
        this.candidateStore = candidateStore;
        this.validationStore = validationStore;
    }

    public AdminReviewPage findOpenReviews(int page, int size) {
        return reviewQueue.findOpenReviews(page, size);
    }

    public void saveRuleDraft(long policyId, JsonNode rule) {
        ruleJsonMapper.fromJson(rule);
        ruleWorkflowStore.saveDraft(policyId, rule);
    }

    public void verifyRule(long policyId) {
        ruleWorkflowStore.verify(policyId);
        reviewQueue.resolveForPolicy(policyId);
    }

    public AdminPolicyAnalysisView analysis(long policyId) {
        var policy = policyCatalog.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        return new AdminPolicyAnalysisView(
                policy.id(),
                policy.name(),
                policy.analysisStatus(),
                candidateStore.findLatest(policyId).orElse(null),
                validationStore.findLatest(policyId).orElse(null)
        );
    }
}
