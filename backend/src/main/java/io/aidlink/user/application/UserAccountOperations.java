package io.aidlink.user.application;

import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserAccountOperations extends UserDetailsService {

    void register(String email, String rawPassword);

    void ensureAdmin(String email, String rawPassword);
}
