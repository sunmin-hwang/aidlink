package io.aidlink.interfaces.api;

import io.aidlink.policy.application.PublicPolicyQueryService;
import io.aidlink.policy.application.PublicPolicyView;
import io.aidlink.policy.application.PolicySearchCriteria;
import io.aidlink.policy.application.PolicySearchSort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PublicPolicyQueryService policyQueryService;

    public PolicyController(PublicPolicyQueryService policyQueryService) {
        this.policyQueryService = policyQueryService;
    }

    @GetMapping
    public List<PublicPolicyView> findAll() {
        return policyQueryService.findAll();
    }

    @GetMapping("/search")
    public PolicySearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @RequestParam(required = false) Integer age,
            @RequestParam(required = false) String employment,
            @RequestParam(required = false) String householdType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort
    ) {
        PolicySearchCriteria criteria = new PolicySearchCriteria(
                q, category, region, provider, openOnly, age,
                employment, householdType, page, size,
                PolicySearchSort.resolve(sort, q));
        return PolicySearchResponse.from(policyQueryService.search(criteria), criteria.sort());
    }

    @GetMapping("/{policyId}")
    public PublicPolicyView findOne(@PathVariable long policyId) {
        return policyQueryService.findById(policyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found"));
    }
}

