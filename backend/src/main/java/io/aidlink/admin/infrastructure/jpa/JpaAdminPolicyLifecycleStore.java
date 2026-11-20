package io.aidlink.admin.infrastructure.jpa;

import io.aidlink.admin.application.AdminPolicyLifecycleCriteria;
import io.aidlink.admin.application.AdminPolicyLifecycleStore;
import io.aidlink.admin.application.AdminPolicyLifecycleView;
import io.aidlink.admin.application.PolicyDeletionReferenceCounts;
import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.infrastructure.jpa.PolicyEntity;
import io.aidlink.policy.infrastructure.jpa.PolicyJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
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
public class JpaAdminPolicyLifecycleStore implements AdminPolicyLifecycleStore {

    private final PolicyJpaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public JpaAdminPolicyLifecycleStore(
            PolicyJpaRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<AdminPolicyLifecycleView> findInactive(
            AdminPolicyLifecycleCriteria criteria
    ) {
        Page<Long> ids = findIds(criteria, criteria.page());
        int effectivePage = effectivePage(
                criteria.page(),
                ids.getTotalElements(),
                criteria.size()
        );
        if (effectivePage != criteria.page()) {
            ids = findIds(criteria, effectivePage);
        }
        return new PageResult<>(
                loadInOrder(ids.getContent()),
                ids.getTotalElements(),
                effectivePage,
                criteria.size()
        );
    }

    @Override
    public Optional<AdminPolicyLifecycleView> findById(long policyId) {
        return repository.findOneForAdmin(policyId).map(this::toView);
    }

    @Override
    @Transactional
    public void restore(long policyId) {
        repository.findOneForAdmin(policyId)
                .orElseThrow()
                .restore();
    }

    @Override
    public Optional<PolicyDeletionReferenceCounts> findReferenceCounts(long policyId) {
        List<PolicyDeletionReferenceCounts> counts = jdbcTemplate.query(
                """
                SELECT
                  (SELECT count(*) FROM policy_source ps WHERE ps.policy_id = p.id) AS policy_sources,
                  (SELECT count(*) FROM policy_rule pr WHERE pr.policy_id = p.id) AS policy_rules,
                  (SELECT count(*) FROM policy_rule_version prv WHERE prv.policy_id = p.id) AS policy_rule_versions,
                  (SELECT count(*) FROM policy_rule_candidate prc WHERE prc.policy_id = p.id) AS policy_rule_candidates,
                  (SELECT count(*)
                     FROM policy_rule_validation validation
                     JOIN policy_rule_candidate candidate ON candidate.id = validation.candidate_id
                    WHERE candidate.policy_id = p.id) AS policy_rule_validations,
                  (SELECT count(*) FROM simulation_result sr WHERE sr.policy_id = p.id) AS simulation_results,
                  (SELECT count(*) FROM policy_bookmark pb WHERE pb.policy_id = p.id) AS bookmarks,
                  (SELECT count(*) FROM admin_review ar WHERE ar.policy_id = p.id) AS admin_reviews,
                  (SELECT count(*) FROM admin_review ar
                    WHERE ar.policy_id = p.id AND ar.status IN ('OPEN', 'IN_PROGRESS')) AS active_admin_reviews,
                  (SELECT count(*) FROM subscription_policy_match spm WHERE spm.policy_id = p.id) AS subscription_matches,
                  (SELECT count(*) FROM raw_policy raw WHERE raw.external_id = p.external_id) AS raw_policy_snapshots
                FROM policy p
                WHERE p.id = ?
                """,
                (resultSet, rowNum) -> new PolicyDeletionReferenceCounts(
                        resultSet.getLong("policy_sources"),
                        resultSet.getLong("policy_rules"),
                        resultSet.getLong("policy_rule_versions"),
                        resultSet.getLong("policy_rule_candidates"),
                        resultSet.getLong("policy_rule_validations"),
                        resultSet.getLong("simulation_results"),
                        resultSet.getLong("bookmarks"),
                        resultSet.getLong("admin_reviews"),
                        resultSet.getLong("active_admin_reviews"),
                        resultSet.getLong("subscription_matches"),
                        resultSet.getLong("raw_policy_snapshots")
                ),
                policyId
        );
        return counts.stream().findFirst();
    }

    private Page<Long> findIds(AdminPolicyLifecycleCriteria criteria, int page) {
        return repository.findInactivePolicyIds(
                criteria.status() == null ? null : criteria.status().name(),
                criteria.query(),
                PageRequest.of(page, criteria.size())
        );
    }

    private List<AdminPolicyLifecycleView> loadInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, PolicyEntity> entitiesById = new HashMap<>();
        repository.findAllForAdminByIdIn(ids)
                .forEach(entity -> entitiesById.put(entity.getId(), entity));
        return ids.stream()
                .map(entitiesById::get)
                .filter(Objects::nonNull)
                .map(this::toView)
                .toList();
    }

    private AdminPolicyLifecycleView toView(PolicyEntity entity) {
        return new AdminPolicyLifecycleView(
                entity.getId(),
                entity.getExternalId(),
                entity.getName(),
                entity.getProvider(),
                entity.getCategory(),
                entity.getLifecycleStatus(),
                entity.getApplicationEnd(),
                entity.getLastSeenAt(),
                entity.getMissedCollectionCount(),
                entity.getArchivedAt()
        );
    }

    private int effectivePage(int requestedPage, long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return Math.toIntExact(Math.min(requestedPage, (totalElements - 1) / size));
    }
}
