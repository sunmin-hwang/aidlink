package io.aidlink.interfaces.api;

import io.aidlink.briefing.application.PersonalBriefingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@Profile("postgres")
public class PersonalBriefingController {
    private final PersonalBriefingService service;
    private final io.aidlink.briefing.application.BriefingPdfRenderer pdf;
    public PersonalBriefingController(PersonalBriefingService service, io.aidlink.briefing.application.BriefingPdfRenderer pdf) { this.service = service; this.pdf = pdf; }

    @GetMapping("/api/v1/me/briefing-access")
    public ResponseEntity<PersonalBriefingService.Access> access(Authentication auth) { return privateResponse(service.access(auth.getName())); }
    @GetMapping("/api/v1/me/briefings")
    public ResponseEntity<List<PersonalBriefingService.Summary>> list(Authentication auth) { return privateResponse(service.list(auth.getName())); }
    @GetMapping("/api/v1/me/briefings/{id}")
    public ResponseEntity<PersonalBriefingService.Report> get(Authentication auth, @PathVariable UUID id) { return privateResponse(service.get(auth.getName(), id)); }
    @GetMapping("/api/v1/me/briefings/{id}/pdf")
    public ResponseEntity<byte[]> pdf(Authentication auth, @PathVariable UUID id) {
        var report = service.get(auth.getName(), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"aidlink-briefing-" + id + ".pdf\"")
                .body(pdf.render(report));
    }
    @PostMapping("/api/v1/me/briefings")
    public ResponseEntity<PersonalBriefingService.Report> create(Authentication auth) { return privateResponse(service.create(auth.getName())); }

    public record GrantRequest(@NotNull Boolean enabled) { }
    @PutMapping("/api/v1/admin/briefing-access/{userId}")
    public ResponseEntity<PersonalBriefingService.Access> grant(Authentication auth, @PathVariable long userId,
                                                              @Valid @RequestBody GrantRequest request) {
        return privateResponse(service.setAccess(userId, request.enabled(), auth.getName()));
    }
    private <T> ResponseEntity<T> privateResponse(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
