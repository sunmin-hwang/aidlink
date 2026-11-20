package io.aidlink.user.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenJpaRepository
        extends JpaRepository<EmailVerificationTokenEntity, Long> {

    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    List<EmailVerificationTokenEntity> findAllByUserIdAndUsedAtIsNullAndRevokedAtIsNull(Long userId);
}
