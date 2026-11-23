package io.aidlink.interfaces.api;

import io.aidlink.policy.link.PolicyLinkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@Profile("postgres")
@RequestMapping("/api/v1/admin/policies/links")
public class AdminPolicyLinkController {
    private final PolicyLinkService service;

    public AdminPolicyLinkController(PolicyLinkService service) { this.service = service; }

    public record CheckRequest(@Size(max = 4000) String url) { }
    public record ApproveRequest(@Size(max = 4000) String url, @Size(min = 20, max = 4000) String evidence) { }

    @GetMapping
    public PolicyLinkService.LinkPage list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false, name = "q") String query) {
        return service.list(page, status, query);
    }

    @PostMapping("/{id}/check")
    public PolicyLinkService.LinkView check(@PathVariable long id, @Valid @RequestBody CheckRequest request) {
        return service.check(id, request.url());
    }

    @PutMapping("/{id}/approve")
    public PolicyLinkService.LinkView approve(@PathVariable long id, @Valid @RequestBody ApproveRequest request, Principal principal) {
        return service.approve(id, request.url(), request.evidence(), principal.getName());
    }

    @PostMapping("/check-due")
    public Map<String, Integer> checkDue(@RequestParam(defaultValue = "20") int limit) {
        return service.checkDue(limit);
    }
}
