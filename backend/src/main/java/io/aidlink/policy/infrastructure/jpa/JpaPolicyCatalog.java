package io.aidlink.policy.infrastructure.jpa;

import io.aidlink.eligibility.domain.RuleNode;
import io.aidlink.policy.application.PolicyCatalog;
import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.application.PolicySearchCriteria;
import io.aidlink.policy.application.RuleConstraintMatcher;
import io.aidlink.policy.domain.Policy;
import io.aidlink.policy.domain.PolicyLifecycleStatus;
import io.aidlink.eligibility.domain.ConditionField;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
@Profile("postgres")
@Transactional(readOnly = true)
public class JpaPolicyCatalog implements PolicyCatalog {

    private final PolicyJpaRepository repository;
    private final PolicyRuleJpaRepository ruleRepository;
    private final RuleJsonMapper ruleJsonMapper;

    public JpaPolicyCatalog(
            PolicyJpaRepository repository,
            PolicyRuleJpaRepository ruleRepository,
            RuleJsonMapper ruleJsonMapper
    ) {
        this.repository = repository;
        this.ruleRepository = ruleRepository;
        this.ruleJsonMapper = ruleJsonMapper;
    }

    @Override
    public List<Policy> findAll() {
        return repository.findAllWithRule(PolicyLifecycleStatus.ACTIVE)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long count() {
        return repository.countByLifecycleStatus(PolicyLifecycleStatus.ACTIVE);
    }

    @Override
    public Optional<Policy> findById(long policyId) {
        return repository.findOnePublicWithRule(policyId)
                .map(this::toDomain);
    }

    @Override
    public PageResult<Policy> search(PolicySearchCriteria criteria) {
        if (!criteria.hasRuleConstraints()) {
            return searchDatabasePage(criteria, List.of());
        }

        List<Long> rulePolicyIds = repository.searchRulePolicyIds(
                criteria.query(),
                criteria.category(),
                criteria.regionCode(),
                criteria.provider(),
                criteria.openOnly()
        );
        Map<Long, RuleNode> rulesByPolicyId = loadRules(rulePolicyIds);
        List<Long> excludedPolicyIds = rulePolicyIds.stream()
                .filter(policyId -> !RuleConstraintMatcher.matches(
                        rulesByPolicyId.get(policyId), ConditionField.AGE, criteria.age())
                        || !RuleConstraintMatcher.matches(
                        rulesByPolicyId.get(policyId), ConditionField.EMPLOYMENT,
                        criteria.employmentStatus())
                        || !RuleConstraintMatcher.matches(
                        rulesByPolicyId.get(policyId), ConditionField.HOUSEHOLD_TYPE,
                        criteria.householdType()))
                .toList();
        return searchDatabasePage(criteria, excludedPolicyIds);
    }

    private PageResult<Policy> searchDatabasePage(
            PolicySearchCriteria criteria,
            List<Long> excludedPolicyIds
    ) {
        Page<Long> ids = searchIds(
                criteria,
                PageRequest.of(criteria.page(), criteria.size()),
                excludedPolicyIds
        );
        int effectivePage = effectivePage(
                criteria.page(), ids.getTotalElements(), criteria.size()
        );
        if (effectivePage != criteria.page()) {
            ids = searchIds(
                    criteria,
                    PageRequest.of(effectivePage, criteria.size()),
                    excludedPolicyIds
            );
        }
        return new PageResult<>(
                loadPolicies(ids.getContent()),
                ids.getTotalElements(),
                effectivePage,
                criteria.size()
        );
    }

    private Page<Long> searchIds(
            PolicySearchCriteria criteria,
            Pageable pageable,
            List<Long> excludedPolicyIds
    ) {
        boolean excludeDisabled = !excludedPolicyIds.isEmpty();
        return repository.searchBaseIds(
                criteria.query(),
                criteria.category(),
                criteria.regionCode(),
                criteria.provider(),
                criteria.openOnly(),
                criteria.sort().name(),
                excludeDisabled,
                excludeDisabled ? excludedPolicyIds : List.of(-1L),
                pageable
        );
    }

    private List<Policy> loadPolicies(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, PolicyEntity> entitiesById = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 500) {
            int end = Math.min(start + 500, ids.size());
            repository.findAllWithRuleByIdIn(
                    ids.subList(start, end),
                    PolicyLifecycleStatus.ACTIVE
            ).forEach(entity -> entitiesById.put(entity.getId(), entity));
        }
        return ids.stream()
                .map(entitiesById::get)
                .filter(Objects::nonNull)
                .map(this::toDomain)
                .toList();
    }

    private Map<Long, RuleNode> loadRules(List<Long> policyIds) {
        Map<Long, RuleNode> rulesByPolicyId = new HashMap<>();
        for (int start = 0; start < policyIds.size(); start += 500) {
            int end = Math.min(start + 500, policyIds.size());
            ruleRepository.findAllByPolicyIdIn(
                    policyIds.subList(start, end),
                    PolicyLifecycleStatus.ACTIVE
            ).forEach(rule -> rulesByPolicyId.put(
                    rule.getPolicy().getId(),
                    ruleJsonMapper.fromJson(rule.getRuleJson())
            ));
        }
        return rulesByPolicyId;
    }

    private int effectivePage(int requestedPage, long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        long lastPage = (totalElements - 1) / size;
        return Math.toIntExact(Math.min(requestedPage, lastPage));
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
                rule,
                entity.getSchedule().publicView()
        );
    }
}
