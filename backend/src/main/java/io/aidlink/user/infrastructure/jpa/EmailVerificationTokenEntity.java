package io.aidlink.user.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "email_verification_token")
public class EmailVerificationTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerificationTokenEntity() {
    }

    public EmailVerificationTokenEntity(
            AppUserEntity user,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt
    ) {
        this.user = Objects.requireNonNull(user, "user");
        this.tokenHash = requireSha256Hash(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public Long getId() {
        return id;
    }

    public AppUserEntity getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isUsableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return usedAt == null
                && revokedAt == null
                && !now.isBefore(createdAt)
                && now.isBefore(expiresAt);
    }

    public void markUsed(Instant usedAt) {
        if (!isUsableAt(usedAt)) {
            throw new IllegalStateException("verification token is not usable");
        }
        this.usedAt = usedAt;
    }

    public void revoke(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt");
        if (revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not be before createdAt");
        }
        if (usedAt != null) {
            throw new IllegalStateException("used verification token cannot be revoked");
        }
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }

    private static String requireSha256Hash(String tokenHash) {
        Objects.requireNonNull(tokenHash, "tokenHash");
        if (!tokenHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("tokenHash must be a 64-character SHA-256 hex value");
        }
        return tokenHash.toLowerCase(java.util.Locale.ROOT);
    }
}
