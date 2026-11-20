package io.aidlink.user.application;

import io.aidlink.user.infrastructure.jpa.AppUserEntity;
import io.aidlink.user.infrastructure.jpa.AppUserJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@Profile("postgres")
public class UserAccountService implements UserAccountOperations {

    private final AppUserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(
            AppUserJpaRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        userRepository.save(new AppUserEntity(
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                "ROLE_USER",
                Instant.now()
        ));
    }

    @Override
    @Transactional
    public void ensureAdmin(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        String encodedPassword = passwordEncoder.encode(rawPassword);
        AppUserEntity admin = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseGet(() -> new AppUserEntity(
                        normalizedEmail,
                        encodedPassword,
                        "ROLE_ADMIN",
                        now
                ));
        admin.updateAdminCredentials(encodedPassword, now);
        userRepository.save(admin);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUserEntity user = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(user.getRole())
                .build();
    }
}
