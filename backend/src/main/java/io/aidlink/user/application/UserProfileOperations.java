package io.aidlink.user.application;

import io.aidlink.user.domain.UserProfile;

public interface UserProfileOperations {

    UserProfile get(String email);

    UserProfile update(String email, UserProfile profile);
}
