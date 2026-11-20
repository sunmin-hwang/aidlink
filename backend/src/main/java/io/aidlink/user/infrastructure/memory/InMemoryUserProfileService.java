package io.aidlink.user.infrastructure.memory;

import io.aidlink.user.application.UserProfileOperations;
import io.aidlink.user.application.UserProfileService;
import io.aidlink.user.domain.UserProfile;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("!postgres")
public class InMemoryUserProfileService implements UserProfileOperations {

    private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public UserProfile get(String email) {
        return profiles.getOrDefault(email, UserProfileService.emptyProfile());
    }

    @Override
    public UserProfile update(String email, UserProfile profile) {
        profiles.put(email, profile);
        return profile;
    }
}
