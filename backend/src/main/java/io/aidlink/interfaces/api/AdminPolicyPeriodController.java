package io.aidlink.interfaces.api;

import io.aidlink.policy.period.PolicyPeriodService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@Profile("postgres")
@RequestMapping("/api/v1/admin/policies/periods")
public class AdminPolicyPeriodController {
    private final PolicyPeriodService service;
    public AdminPolicyPeriodController(PolicyPeriodService service) { this.service = service; }
    public record Review(String periodText, String sourceUrl, String evidence, String expectedSourceText) { }
    public record Apply(String fingerprint) { }
    @GetMapping public PolicyPeriodService.SearchPage search(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="0") int page) { return service.search(q, page); }
    @GetMapping("/{id}") public PolicyPeriodService.View get(@PathVariable long id) { return service.get(id); }
    @PutMapping("/{id}/verify") public PolicyPeriodService.View verify(@PathVariable long id, @RequestBody Review review, Principal principal) {
        return service.verify(id, review.periodText(), review.sourceUrl(), review.evidence(), review.expectedSourceText(), principal.getName());
    }
    @GetMapping("/backfill/preview") public PolicyPeriodService.Preview preview() { return service.preview(); }
    @PostMapping("/backfill/apply") public PolicyPeriodService.Preview apply(@RequestBody Apply request) { return service.apply(request.fingerprint()); }
}
