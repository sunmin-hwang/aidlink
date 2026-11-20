package io.aidlink.user.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileEntity, Long> {
}
