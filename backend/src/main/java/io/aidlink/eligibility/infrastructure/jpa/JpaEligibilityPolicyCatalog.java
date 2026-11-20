package io.aidlink.eligibility.infrastructure.jpa;

import io.aidlink.eligibility.application.EligibilityPolicyCatalog;
import io.aidlink.eligibility.application.EligibilityPolicySort;
import io.aidlink.eligibility.application.EligibilityRulePresence;
import io.aidlink.eligibility.domain.RuleNode;
import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.domain.Policy;
import io.aidlink.policy.domain.PolicyLifecycleStatus;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
import io.aidlink.policy.infrastructure.jpa.PolicyJpaRepository;
import io.aidlink.policy.infrastructure.jpa.RuleJsonMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
@Profile("postgres")
@Transactional(readOnly = true)
public class JpaEligibilityPolicyCatalog implements EligibilityPolicyCatalog {

    private final PolicyJpaRepository repository;
    private final RuleJsonMapper ruleJsonMapper;

    public JpaEligibilityPolicyCatalog(
            PolicyJpaRepository repository,
            RuleJsonMapper ruleJsonMapper
    ) {
        this.repository = repository;
        this.ruleJsonMapper = ruleJsonMapper;
    }

    @Override
    public List<Policy> findWithRules(String category, String regionCode) {
        return repository.findAllWithRuleForEligibility(
                        PolicyLifecycleStatus.ACTIVE,
                        category,
                        regionCode
                ).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countWithoutRules(String category, String regionCode) {
        return repository.countWithoutRuleForEligibility(
                PolicyLifecycleStatus.ACTIVE,
                category,
                regionCode
        );
    }

    @Override
    public PageResult<Policy> findPage(
            String category,
            String regionCode,
            List<Long> excludedPolicyIds,
            EligibilityRulePresence rulePresence,
            int page,
            int size,
            EligibilityPolicySort sort
    ) {
        boolean excludeDisabled = !excludedPolicyIds.isEmpty();
        Page<Long> ids = repository.findEligibilityPageIds(
                category,
                regionCode,
                rulePresence.name(),
                sort.name(),
                excludeDisabled,
                excludeDisabled ? excludedPolicyIds : List.of(-1L),
                PageRequest.of(page, size)
        );
        int effectivePage = effectivePage(page, ids.getTotalElements(), size);
        if (effectivePage != page) {
            ids = repository.findEligibilityPageIds(
                    category,
                    regionCode,
                    rulePresence.name(),
                    sort.name(),
                    excludeDisabled,
                    excludeDisabled ? excludedPolicyIds : List.of(-1L),
                    PageRequest.of(effectivePage, size)
            );
        }
        return new PageResult<>(
                loadPolicies(ids.getContent()),
                ids.getTotalElements(),
                effectivePage,
                size
        );
    }

    @Override
    public List<String> findCategories() {
        return repository.findDistinctCategories(PolicyLifecycleStatus.ACTIVE);
    }

    private List<Policy> loadPolicies(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, PolicyEntity> entitiesById = new HashMap<>();
        repository.findAllWithRuleByIdIn(ids, PolicyLifecycleStatus.ACTIVE)
                .forEach(entity -> entitiesById.put(entity.getId(), entity));
        return ids.stream()
                .map(entitiesById::get)
                .filter(Objects::nonNull)
                .map(this::toDomain)
                .toList();
    }

    private int effectivePage(int requestedPage, long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return Math.toIntExact(Math.min(requestedPage, (totalElements - 1) / size));
    }

    private Policy toDomain(PolicyEntity entity) {
        RuleNode rule = entity.getRule() == null
                ? null
                : ruleJsonMapper.fromJson(entity.getRule().getRuleJson());
        return new Policy(
                entity.getId(),
                entity.getExternalId(),
                entity.getName(),
                entity.getProvider(),
                entity.getCategory(),
                entity.getRegionCode(),
                entity.getDescription(),
                entity.getTargetDescription(),
                entity.getSupportContent(),
                entity.getApplicationStart(),
                entity.getApplicationEnd(),
                entity.getApplicationMethod(),
                entity.getRequiredDocuments(),
                entity.getApplicationUrl(),
                entity.getSourceUrl(),
                entity.getSourceUpdatedAt(),
                entity.getLifecycleStatus(),
                entity.getLastSeenAt(),
                entity.getMissedCollectionCount(),
                entity.getAnalysisStatus(),
                rule
        );
    }
}
