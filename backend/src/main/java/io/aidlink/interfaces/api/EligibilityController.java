package io.aidlink.interfaces.api;

import io.aidlink.eligibility.application.EligibilityService;
import io.aidlink.eligibility.application.EligibilityAnalysisCriteria;
import io.aidlink.eligibility.application.EligibilityAnalysisPage;
import io.aidlink.eligibility.application.EligibilityAnalysisQueryService;
import io.aidlink.eligibility.application.EligibilityAnalysisSort;
import io.aidlink.eligibility.domain.EligibilityAnalysis;
import io.aidlink.policy.application.PolicyCatalog;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/eligibility")
public class EligibilityController {

    private final PolicyCatalog policyCatalog;
    private final EligibilityService eligibilityService;
    private final EligibilityAnalysisQueryService eligibilityAnalysisQueryService;

    public EligibilityController(
            PolicyCatalog policyCatalog,
            EligibilityService eligibilityService,
            EligibilityAnalysisQueryService eligibilityAnalysisQueryService
    ) {
        this.policyCatalog = policyCatalog;
        this.eligibilityService = eligibilityService;
        this.eligibilityAnalysisQueryService = eligibilityAnalysisQueryService;
    }

    @PostMapping("/analyze")
    public EligibilityAnalysisPage analyzeAll(
            @Valid @RequestBody EligibilityAnalyzeRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String sort
    ) {
        return eligibilityAnalysisQueryService.analyze(
                request.profile(),
                request.effectiveDate(),
                new EligibilityAnalysisCriteria(
                        page,
                        size,
                        EligibilityAnalysisCriteria.resolveStatus(status),
                        category,
                        region,
                        EligibilityAnalysisSort.resolve(sort)
                )
        );
    }

    @PostMapping("/policies/{policyId}")
    public EligibilityAnalysis analyzeOne(
            @PathVariable long policyId,
            @Valid @RequestBody EligibilityAnalyzeRequest request
    ) {
        return policyCatalog.findById(policyId)
                .map(policy -> eligibilityService.analyze(policy, request.profile(), request.effectiveDate()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));
    }
}

