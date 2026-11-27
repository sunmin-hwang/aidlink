package io.aidlink.interfaces.api;

import io.aidlink.briefing.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@Profile("postgres")
public class BriefingEmailController {
    private final BriefingEmailVerificationService verification;
    private final BriefingDeliveryService deliveries;
    public BriefingEmailController(BriefingEmailVerificationService verification,BriefingDeliveryService deliveries) {
        this.verification=verification; this.deliveries=deliveries;
    }
    @GetMapping("/api/v1/me/briefing-email")
    public ResponseEntity<BriefingEmailVerificationService.Status> status(Authentication auth) { return privateResponse(verification.status(auth.getName())); }
    @PostMapping("/api/v1/me/briefing-email/verification")
    public ResponseEntity<Void> request(Authentication auth) { verification.request(auth.getName()); return emptyResponse(); }
    public record Confirmation(@NotBlank String token) { }
    @PostMapping("/api/v1/me/briefing-email/confirmation")
    public ResponseEntity<Void> confirm(Authentication auth,@Valid @RequestBody Confirmation request) { verification.confirm(auth.getName(),request.token()); return emptyResponse(); }
    public record Schedule(@NotNull UUID requestId,Instant scheduledAt,boolean consent) { }
    @PostMapping("/api/v1/me/briefings/{id}/deliveries")
    public ResponseEntity<BriefingDeliveryService.Delivery> schedule(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Schedule request) {
        return privateResponse(deliveries.schedule(auth.getName(),id,request.requestId(),request.scheduledAt(),request.consent()));
    }
    @GetMapping("/api/v1/me/briefing-deliveries")
    public ResponseEntity<List<BriefingDeliveryService.Delivery>> list(Authentication auth) { return privateResponse(deliveries.list(auth.getName())); }
    @DeleteMapping("/api/v1/me/briefing-deliveries/{id}")
    public ResponseEntity<Void> cancel(Authentication auth,@PathVariable UUID id) { deliveries.cancel(auth.getName(),id); return emptyResponse(); }
    private ResponseEntity<Void> emptyResponse() { return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build(); }
    private <T> ResponseEntity<T> privateResponse(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
