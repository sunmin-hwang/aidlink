package io.aidlink.interfaces.api;

import io.aidlink.user.application.UserProfileOperations;
import io.aidlink.user.domain.UserProfile;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/profile")
public class UserProfileController {

    private final UserProfileOperations profiles;

    public UserProfileController(UserProfileOperations profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public UserProfile get(Authentication authentication) {
        return profiles.get(authentication.getName());
    }

    @PutMapping
    public UserProfile update(
            Authentication authentication,
            @Valid @RequestBody UserProfile profile
    ) {
        return profiles.update(authentication.getName(), profile);
    }
}
