package io.aidlink.user.application;

import io.aidlink.user.domain.UserProfile;
import io.aidlink.user.infrastructure.jpa.AppUserJpaRepository;
import io.aidlink.user.infrastructure.jpa.UserProfileEntity;
import io.aidlink.user.infrastructure.jpa.UserProfileJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("postgres")
public class UserProfileService implements UserProfileOperations {

    private final AppUserJpaRepository userRepository;
    private final UserProfileJpaRepository profileRepository;

    public UserProfileService(
            AppUserJpaRepository userRepository,
            UserProfileJpaRepository profileRepository
    ) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public UserProfile get(String email) {
        var user = requiredUser(email);
        return profileRepository.findById(user.getId())
                .map(UserProfileEntity::toDomain)
                .orElseGet(UserProfileService::emptyProfile);
    }

    @Transactional
    public UserProfile update(String email, UserProfile profile) {
        var user = requiredUser(email);
        var entity = profileRepository.findById(user.getId())
                .orElseGet(() -> new UserProfileEntity(user, profile));
        entity.update(profile);
        return profileRepository.save(entity).toDomain();
    }

    private io.aidlink.user.infrastructure.jpa.AppUserEntity requiredUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public static UserProfile emptyProfile() {
        return new UserProfile(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }
}
