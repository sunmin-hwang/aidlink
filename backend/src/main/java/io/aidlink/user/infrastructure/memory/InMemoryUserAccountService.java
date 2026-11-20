package io.aidlink.user.infrastructure.memory;

import io.aidlink.user.application.UserAccountOperations;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Profile("!postgres")
public class InMemoryUserAccountService implements UserAccountOperations {

    private final Map<String, UserDetails> users = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;

    public InMemoryUserAccountService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void register(String email, String rawPassword) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        UserDetails user = User.withUsername(normalized)
                .password(passwordEncoder.encode(rawPassword))
                .authorities("ROLE_USER")
                .build();
        if (users.putIfAbsent(normalized, user) != null) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
    }

    @Override
    public void ensureAdmin(String email, String rawPassword) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        users.put(normalized, User.withUsername(normalized)
                .password(passwordEncoder.encode(rawPassword))
                .authorities("ROLE_ADMIN")
                .build());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDetails user = users.get(username.toLowerCase(Locale.ROOT));
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return new User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                user.isAccountNonExpired(),
                user.isCredentialsNonExpired(),
                user.isAccountNonLocked(),
                user.getAuthorities()
        );
    }
}
