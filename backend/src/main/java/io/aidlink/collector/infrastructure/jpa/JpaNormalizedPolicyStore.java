package io.aidlink.collector.infrastructure.jpa;

import io.aidlink.collector.application.NormalizedPolicyStore;
import io.aidlink.collector.domain.NormalizedPolicy;
import io.aidlink.collector.domain.CollectionChangeType;
import io.aidlink.collector.domain.PolicySource;
import io.aidlink.admin.application.AdminReviewQueue;
import io.aidlink.admin.domain.AdminReviewReason;
import io.aidlink.policy.domain.PolicyAnalysisStatus;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
import io.aidlink.policy.infrastructure.jpa.PolicyJpaRepository;
import io.aidlink.policy.application.PublicPolicyCache;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;

@Repository
@Profile("postgres")
public class JpaNormalizedPolicyStore implements NormalizedPolicyStore {

    private final PolicyJpaRepository policyRepository;
    private final PolicySourceJpaRepository sourceRepository;
    private final PublicPolicyCache publicPolicyCache;
    private final AdminReviewQueue reviewQueue;

    public JpaNormalizedPolicyStore(
            PolicyJpaRepository policyRepository,
            PolicySourceJpaRepository sourceRepository,
            PublicPolicyCache publicPolicyCache,
            AdminReviewQueue reviewQueue
    ) {
        this.policyRepository = policyRepository;
        this.sourceRepository = sourceRepository;
        this.publicPolicyCache = publicPolicyCache;
        this.reviewQueue = reviewQueue;
    }

    @Override
    @Transactional
    public void upsert(
            NormalizedPolicy normalized,
            CollectionChangeType changeType,
            Instant collectedAt
    ) {
        var existing = policyRepository.findByExternalId(normalized.externalId());
        PolicyEntity policy = existing
                .orElseGet(() -> new PolicyEntity(
                        normalized.externalId(),
                        normalized.name(),
                        normalized.provider(),
                        normalized.category(),
                        normalized.regionCode(),
                        normalized.description(),
                        normalized.targetDescription(),
                        normalized.supportContent(),
                        normalized.applicationStart(),
                        normalized.applicationEnd(),
                        normalized.applicationMethod(),
                        normalized.requiredDocuments(),
                        normalized.applicationUrl(),
                        normalized.sourceUrl(),
                        normalized.analysisStatus(),
                        normalized.sourceUpdatedAt()
                ));

        PolicyAnalysisStatus nextStatus = changeType == CollectionChangeType.UPDATED
                && existing.map(PolicyEntity::getAnalysisStatus)
                .orElse(PolicyAnalysisStatus.RAW) == PolicyAnalysisStatus.HUMAN_VERIFIED
                ? PolicyAnalysisStatus.REVIEW_REQUIRED
                : normalized.analysisStatus();
        policy.update(
                normalized.name(),
                normalized.provider(),
                normalized.category(),
                normalized.regionCode(),
                normalized.description(),
                normalized.targetDescription(),
                normalized.supportContent(),
                normalized.applicationStart(),
                normalized.applicationEnd(),
                normalized.applicationMethod(),
                normalized.requiredDocuments(),
                normalized.applicationUrl(),
                normalized.sourceUrl(),
                nextStatus,
                normalized.sourceUpdatedAt()
        );
        policy.markSeen(collectedAt);
        PolicyEntity savedPolicy = policyRepository.save(policy);

        PolicySourceEntity source = sourceRepository
                .findBySourceAndExternalId(normalized.source(), normalized.sourceExternalId())
                .orElseGet(() -> new PolicySourceEntity(
                        savedPolicy,
                        normalized.source(),
                        normalized.sourceExternalId(),
                        normalized.sourceUrl(),
                        collectedAt
                ));
        source.update(normalized.sourceUrl(), collectedAt);
        sourceRepository.save(source);
        if (changeType == CollectionChangeType.UPDATED) {
            reviewQueue.enqueue(
                    savedPolicy.getId(),
                    AdminReviewReason.POLICY_CHANGED,
                    BigDecimal.valueOf(60)
            );
        }
        publicPolicyCache.evictAll();
    }

    @Override
    @Transactional
    public void markSeen(PolicySource source, String sourceExternalId, Instant collectedAt) {
        PolicySourceEntity policySource = sourceRepository
                .findBySourceAndExternalId(source, sourceExternalId)
                .orElseThrow(() -> new IllegalStateException(
                        "Normalized policy source is missing for " + source + ":" + sourceExternalId
                ));
        policySource.markSeen(collectedAt);
        boolean statusChanged = policySource.getPolicy().markSeen(collectedAt);
        sourceRepository.save(policySource);
        policyRepository.save(policySource.getPolicy());
        if (statusChanged) {
            publicPolicyCache.evictAll();
        }
    }

    @Override
    @Transactional
    public void completeSuccessfulCollection(
            PolicySource source,
            Instant collectionStartedAt,
            int sourceRemovedThreshold
    ) {
        boolean statusChanged = false;
        for (PolicySourceEntity missing : sourceRepository
                .findMissingSince(source, collectionStartedAt)) {
            statusChanged |= missing.getPolicy().markMissed(sourceRemovedThreshold);
            policyRepository.save(missing.getPolicy());
        }
        if (statusChanged) {
            publicPolicyCache.evictAll();
        }
    }
}
