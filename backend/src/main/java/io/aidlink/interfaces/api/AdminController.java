package io.aidlink.interfaces.api;

import io.aidlink.admin.application.AdminPolicyLifecycleCriteria;
import io.aidlink.admin.application.AdminPolicyLifecyclePage;
import io.aidlink.admin.application.AdminPolicyLifecycleService;
import io.aidlink.admin.application.AdminPolicyLifecycleView;
import io.aidlink.admin.application.AdminReviewService;
import io.aidlink.admin.application.AdminReviewPage;
import io.aidlink.admin.application.AdminPolicyAnalysisView;
import io.aidlink.ai.application.PolicyRuleParsingService;
import io.aidlink.ai.application.PolicyRuleValidationService;
import io.aidlink.ai.domain.PolicyRuleCandidate;
import io.aidlink.ai.domain.PolicyRuleValidationResult;
import io.aidlink.admin.application.PolicyDeletionImpact;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminReviewService adminReviewService;
    private final PolicyRuleParsingService parsingService;
    private final PolicyRuleValidationService validationService;
    private final AdminPolicyLifecycleService lifecycleService;

    public AdminController(
            AdminReviewService adminReviewService,
            PolicyRuleParsingService parsingService,
            PolicyRuleValidationService validationService,
            AdminPolicyLifecycleService lifecycleService
    ) {
        this.adminReviewService = adminReviewService;
        this.parsingService = parsingService;
        this.validationService = validationService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/reviews")
    public AdminReviewPage reviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return adminReviewService.findOpenReviews(page, size);
    }

    @GetMapping("/policies/lifecycle")
    public AdminPolicyLifecyclePage lifecyclePolicies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String query
    ) {
        return lifecycleService.findInactive(new AdminPolicyLifecycleCriteria(
                page,
                size,
                AdminPolicyLifecycleCriteria.resolveStatus(status),
                query
        ));
    }

    @GetMapping("/policies/{policyId}/deletion-impact")
    public PolicyDeletionImpact deletionImpact(@PathVariable long policyId) {
        return lifecycleService.previewDeletion(policyId);
    }

    @PostMapping("/policies/{policyId}/restore")
    public AdminPolicyLifecycleView restore(@PathVariable long policyId) {
        return lifecycleService.restore(policyId);
    }

    @GetMapping("/policies/{policyId}/analysis")
    public AdminPolicyAnalysisView analysis(@PathVariable long policyId) {
        return adminReviewService.analysis(policyId);
    }

    @PostMapping("/policies/{policyId}/parse")
    public PolicyRuleCandidate parse(@PathVariable long policyId) {
        return parsingService.parsePolicy(policyId);
    }

    @PostMapping("/policies/{policyId}/validate")
    public PolicyRuleValidationResult validate(@PathVariable long policyId) {
        return validationService.validateLatest(policyId);
    }

    @PutMapping("/policies/{policyId}/rule")
    public ResponseEntity<Void> updateRule(
            @PathVariable long policyId,
            @Valid @RequestBody AdminRuleUpdateRequest request
    ) {
        adminReviewService.saveRuleDraft(policyId, request.rule());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/policies/{policyId}/verify")
    public ResponseEntity<Void> verify(@PathVariable long policyId) {
        adminReviewService.verifyRule(policyId);
        return ResponseEntity.noContent().build();
    }
}
