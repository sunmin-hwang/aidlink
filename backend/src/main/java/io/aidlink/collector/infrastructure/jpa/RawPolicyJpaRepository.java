package io.aidlink.collector.infrastructure.jpa;

import io.aidlink.collector.domain.PolicySource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RawPolicyJpaRepository extends JpaRepository<RawPolicyEntity, Long> {

    Optional<RawPolicyEntity> findFirstBySourceAndExternalIdOrderByCollectedAtDesc(
            PolicySource source,
            String externalId
    );
}
