package io.aidlink.security;

import io.aidlink.user.application.UserAccountOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "aidlink.security.bootstrap-admin",
        name = "enabled",
        havingValue = "true"
)
public class AdminBootstrapConfiguration {

    @Bean
    ApplicationRunner bootstrapAdmin(
            UserAccountOperations accounts,
            @Value("${aidlink.security.bootstrap-admin.email}") String email,
            @Value("${aidlink.security.bootstrap-admin.password}") String password
    ) {
        return ignored -> {
            if (email.isBlank() || password.length() < 12) {
                throw new IllegalStateException(
                        "Bootstrap admin requires an email and a password of at least 12 characters");
            }
            accounts.ensureAdmin(email, password);
        };
    }
}
