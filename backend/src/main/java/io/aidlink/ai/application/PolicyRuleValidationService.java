package io.aidlink.ai.application;

import io.aidlink.ai.domain.PolicyQualificationText;
import io.aidlink.ai.domain.PolicyRuleValidationResult;
import io.aidlink.ai.domain.RuleValidationStatus;
import io.aidlink.admin.application.AdminReviewQueue;
import io.aidlink.admin.domain.AdminReviewReason;
import io.aidlink.policy.application.PolicyCatalog;
import io.aidlink.observability.AidLinkMetrics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PolicyRuleValidationService {

    private final PolicyCatalog policyCatalog;
    private final PolicyRuleCandidateStore candidateStore;
    private final AutomaticRuleValidator validator;
    private final PolicyRuleValidationStore validationStore;
    private final AdminReviewQueue reviewQueue;
    private final AidLinkMetrics metrics;

    public PolicyRuleValidationService(
            PolicyCatalog policyCatalog,
            PolicyRuleCandidateStore candidateStore,
            AutomaticRuleValidator validator,
            PolicyRuleValidationStore validationStore,
            AdminReviewQueue reviewQueue,
            AidLinkMetrics metrics
    ) {
        this.policyCatalog = policyCatalog;
        this.candidateStore = candidateStore;
        this.validator = validator;
        this.validationStore = validationStore;
        this.reviewQueue = reviewQueue;
        this.metrics = metrics;
    }

    public PolicyRuleValidationResult validateLatest(long policyId) {
        var policy = policyCatalog.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));
        var candidate = candidateStore.findLatest(policyId)
                .orElseThrow(() -> new IllegalStateException(
                        "No AI rule candidate exists for policy: " + policyId));
        var sourceText = new PolicyQualificationText(
                policy.id(),
                policy.name(),
                policy.targetDescription(),
                policy.description(),
                ""
        );
        PolicyRuleValidationResult result = validator.validate(candidate, sourceText);
        validationStore.save(result);
        metrics.recordValidation(result.status());
        if (result.status() == RuleValidationStatus.REVIEW_REQUIRED) {
            boolean unsupported = result.issues().stream().anyMatch(issue ->
                    issue.code().equals("MANUAL_REVIEW_REQUIRED")
                            || issue.code().startsWith("UNSUPPORTED_"));
            reviewQueue.enqueue(
                    policyId,
                    unsupported
                            ? AdminReviewReason.UNSUPPORTED_EXPRESSION
                            : AdminReviewReason.VALIDATION_FAILED,
                    BigDecimal.valueOf(100L - result.score()
                            + result.issues().stream()
                            .filter(issue -> issue.severity().name().equals("ERROR"))
                            .count() * 10L)
            );
        }
        return result;
    }
}
