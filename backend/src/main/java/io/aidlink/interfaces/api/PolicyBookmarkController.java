package io.aidlink.interfaces.api;

import io.aidlink.policy.application.PolicyBookmarkOperations;
import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.application.PublicPolicyQueryService;
import io.aidlink.policy.application.PublicPolicyView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me/bookmarks")
public class PolicyBookmarkController {

    private final PolicyBookmarkOperations bookmarks;
    private final PublicPolicyQueryService policies;

    public PolicyBookmarkController(
            PolicyBookmarkOperations bookmarks,
            PublicPolicyQueryService policies
    ) {
        this.bookmarks = bookmarks;
        this.policies = policies;
    }

    @GetMapping
    public PolicyBookmarkResponse findAll(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        PageResult<Long> policyIds = bookmarks.findPolicyIds(authentication.getName(), page, size);
        List<PublicPolicyView> content = policyIds.content().stream()
                .map(policies::findById)
                .flatMap(java.util.Optional::stream)
                .toList();
        return PolicyBookmarkResponse.from(new PageResult<>(
                content,
                policyIds.totalElements(),
                policyIds.page(),
                policyIds.size()
        ));
    }

    @GetMapping("/{policyId}")
    public Map<String, Boolean> contains(
            Authentication authentication,
            @PathVariable long policyId
    ) {
        return Map.of("bookmarked", bookmarks.contains(authentication.getName(), policyId));
    }

    @PostMapping("/{policyId}")
    public ResponseEntity<Void> add(
            Authentication authentication,
            @PathVariable long policyId
    ) {
        if (policies.findById(policyId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found");
        }
        bookmarks.add(authentication.getName(), policyId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> remove(
            Authentication authentication,
            @PathVariable long policyId
    ) {
        bookmarks.remove(authentication.getName(), policyId);
        return ResponseEntity.noContent().build();
    }
}
