package io.aidlink.collector.infrastructure.jpa;

import io.aidlink.collector.domain.PolicySource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PolicySourceJpaRepository extends JpaRepository<PolicySourceEntity, Long> {

    Optional<PolicySourceEntity> findBySourceAndExternalId(PolicySource source, String externalId);

    @Query("""
            select ps
            from PolicySourceEntity ps
            join fetch ps.policy
            where ps.source = :source
              and (ps.lastCollectedAt is null or ps.lastCollectedAt < :collectionStartedAt)
            """)
    List<PolicySourceEntity> findMissingSince(
            @Param("source") PolicySource source,
            @Param("collectionStartedAt") Instant collectionStartedAt
    );
}
