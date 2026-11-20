package io.aidlink.user.infrastructure.jpa;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailVerificationTokenEntityTest {

    private static final String TOKEN_HASH = "a".repeat(64);

    @Test
    void tokenCanBeUsedOnlyOnceBeforeExpiry() {
        Instant createdAt = Instant.parse("2026-11-11T00:00:00Z");
        var user = new AppUserEntity("user@example.com", "hash", "ROLE_USER", createdAt);
        var token = new EmailVerificationTokenEntity(
                user,
                TOKEN_HASH,
                createdAt.plusSeconds(3600),
                createdAt
        );

        assertThat(token.isUsableAt(createdAt.plusSeconds(60))).isTrue();

        token.markUsed(createdAt.plusSeconds(120));
        user.markEmailVerified(createdAt.plusSeconds(120));

        assertThat(token.isUsableAt(createdAt.plusSeconds(180))).isFalse();
        assertThat(user.isEmailVerified()).isTrue();
        assertThatThrownBy(() -> token.markUsed(createdAt.plusSeconds(180)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rawOrMalformedTokenValuesCannotBePersistedAsTokenHashes() {
        Instant now = Instant.parse("2026-11-11T00:00:00Z");
        var user = new AppUserEntity("user@example.com", "hash", "ROLE_USER", now);

        assertThatThrownBy(() -> new EmailVerificationTokenEntity(
                user,
                "raw-token",
                now.plusSeconds(3600),
                now
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
