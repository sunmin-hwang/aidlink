package io.aidlink.collector.infrastructure.jpa;

import io.aidlink.collector.application.RawPolicyStore;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.collector.domain.RawPolicyDocument;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@Profile("postgres")
public class JpaRawPolicyStore implements RawPolicyStore {

    private final RawPolicyJpaRepository repository;

    public JpaRawPolicyStore(RawPolicyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> findLatestContentHash(PolicySource source, String externalId) {
        return repository.findFirstBySourceAndExternalIdOrderByCollectedAtDesc(source, externalId)
                .map(RawPolicyEntity::getContentHash);
    }

    @Override
    public void save(RawPolicyDocument document, String contentHash, Instant collectedAt) {
        repository.save(new RawPolicyEntity(
                document.source(),
                document.externalId(),
                document.payload(),
                contentHash,
                collectedAt
        ));
    }
}
