package io.aidlink.policy.infrastructure.jpa;

import io.aidlink.policy.domain.PolicyLifecycleStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

@Profile("postgres")
public interface PolicyJpaRepository extends JpaRepository<PolicyEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PolicyEntity p where p.id = :id")
    Optional<PolicyEntity> findOneForPeriodUpdate(@Param("id") long id);

    @Query("select p from PolicyEntity p left join fetch p.rule where p.id = :id and p.lifecycleStatus in (io.aidlink.policy.domain.PolicyLifecycleStatus.ACTIVE, io.aidlink.policy.domain.PolicyLifecycleStatus.EXPIRED)")
    Optional<PolicyEntity> findOnePublicWithRule(@Param("id") long id);

    Optional<PolicyEntity> findByExternalId(String externalId);

    @Query("select p from PolicyEntity p left join fetch p.rule where p.lifecycleStatus = :status order by p.id")
    List<PolicyEntity> findAllWithRule(@Param("status") PolicyLifecycleStatus status);

    @Query("select p from PolicyEntity p left join fetch p.rule where p.id = :id and p.lifecycleStatus = :status")
    Optional<PolicyEntity> findOneWithRule(
            @Param("id") long id,
            @Param("status") PolicyLifecycleStatus status
    );

    @Query("select p from PolicyEntity p left join fetch p.rule where p.id = :id")
    Optional<PolicyEntity> findOneForAdmin(@Param("id") long id);

    long countByLifecycleStatus(PolicyLifecycleStatus status);

    @Query(value = """
            SELECT p.id
            FROM policy p
            WHERE p.lifecycle_status = 'ACTIVE'
              AND (:query IS NULL OR
                to_tsvector(
                    'simple',
                    coalesce(p.name, '') || ' ' || coalesce(p.description, '') || ' '
                        || coalesce(p.target_description, '') || ' ' || coalesce(p.support_content, '') || ' '
                        || coalesce(p.provider, '')
                ) @@ websearch_to_tsquery('simple', :query)
                OR p.name % :query)
              AND (:category IS NULL OR p.category = :category)
              AND (:regionCode IS NULL OR p.region_code IN (:regionCode, 'NATIONWIDE'))
              AND (:provider IS NULL OR lower(p.provider) LIKE lower(concat('%', :provider, '%')))
              AND (:openOnly = false OR
                NOT p.application_period_review_required AND policy_application_available_now(p.application_period_kind, p.application_start, p.application_end, p.application_start_time, p.application_end_time, p.application_period_raw))
              AND (:excludeDisabled = false OR p.id NOT IN (:excludedPolicyIds))
            ORDER BY
              CASE WHEN :sort = 'RELEVANCE' AND :query IS NOT NULL THEN
                ts_rank(
                    to_tsvector(
                        'simple',
                        coalesce(p.name, '') || ' ' || coalesce(p.description, '') || ' '
                            || coalesce(p.target_description, '') || ' ' || coalesce(p.support_content, '') || ' '
                            || coalesce(p.provider, '')
                    ),
                    websearch_to_tsquery('simple', :query)
                )
              END DESC,
              CASE WHEN :sort = 'RELEVANCE' AND :query IS NOT NULL
                THEN similarity(p.name, :query)
              END DESC,
              CASE WHEN :sort = 'DEADLINE' THEN p.application_end END ASC NULLS LAST,
              CASE WHEN :sort = 'RECENT'
                THEN coalesce(p.source_updated_at, p.updated_at)
              END DESC NULLS LAST,
              CASE WHEN :sort = 'RECENT' THEN p.id END DESC,
              CASE WHEN :sort = 'NAME' THEN p.name END ASC,
              p.id
            """,
            countQuery = """
            SELECT count(*)
            FROM policy p
            WHERE p.lifecycle_status = 'ACTIVE'
              AND (:query IS NULL OR
                to_tsvector(
                    'simple',
                    coalesce(p.name, '') || ' ' || coalesce(p.description, '') || ' '
                        || coalesce(p.target_description, '') || ' ' || coalesce(p.support_content, '') || ' '
                        || coalesce(p.provider, '')
                ) @@ websearch_to_tsquery('simple', :query)
                OR p.name % :query)
              AND (:category IS NULL OR p.category = :category)
              AND (:regionCode IS NULL OR p.region_code IN (:regionCode, 'NATIONWIDE'))
              AND (:provider IS NULL OR lower(p.provider) LIKE lower(concat('%', :provider, '%')))
              AND (:openOnly = false OR
                NOT p.application_period_review_required AND policy_application_available_now(p.application_period_kind, p.application_start, p.application_end, p.application_start_time, p.application_end_time, p.application_period_raw))
              AND (:excludeDisabled = false OR p.id NOT IN (:excludedPolicyIds))
            """,
            nativeQuery = true)
    Page<Long> searchBaseIds(
            @Param("query") String query,
            @Param("category") String category,
            @Param("regionCode") String regionCode,
            @Param("provider") String provider,
            @Param("openOnly") boolean openOnly,
            @Param("sort") String sort,
            @Param("excludeDisabled") boolean excludeDisabled,
            @Param("excludedPolicyIds") List<Long> excludedPolicyIds,
            Pageable pageable
    );

    @Query(value = """
            SELECT p.id
            FROM policy p
            JOIN policy_rule rule ON rule.policy_id = p.id
            WHERE p.lifecycle_status = 'ACTIVE'
              AND (:query IS NULL OR
                to_tsvector(
                    'simple',
                    coalesce(p.name, '') || ' ' || coalesce(p.description, '') || ' '
                        || coalesce(p.target_description, '') || ' ' || coalesce(p.support_content, '') || ' '
                        || coalesce(p.provider, '')
                ) @@ websearch_to_tsquery('simple', :query)
                OR p.name % :query)
              AND (:category IS NULL OR p.category = :category)
              AND (:regionCode IS NULL OR p.region_code IN (:regionCode, 'NATIONWIDE'))
              AND (:provider IS NULL OR lower(p.provider) LIKE lower(concat('%', :provider, '%')))
              AND (:openOnly = false OR
                NOT p.application_period_review_required AND policy_application_available_now(p.application_period_kind, p.application_start, p.application_end, p.application_start_time, p.application_end_time, p.application_period_raw))
            """, nativeQuery = true)
    List<Long> searchRulePolicyIds(
            @Param("query") String query,
            @Param("category") String category,
            @Param("regionCode") String regionCode,
            @Param("provider") String provider,
            @Param("openOnly") boolean openOnly
    );

    @Query("""
            select p
            from PolicyEntity p
            left join fetch p.rule
            where p.id in :ids and p.lifecycleStatus = :status
            """)
    List<PolicyEntity> findAllWithRuleByIdIn(
            @Param("ids") List<Long> ids,
            @Param("status") PolicyLifecycleStatus status
    );

    @Query("select p from PolicyEntity p where p.id in :ids")
    List<PolicyEntity> findAllForAdminByIdIn(@Param("ids") List<Long> ids);

    @Query(value = """
            SELECT p.id
            FROM policy p
            WHERE p.lifecycle_status <> 'ACTIVE'
              AND (coalesce(:lifecycleStatus, '') = ''
                OR p.lifecycle_status = :lifecycleStatus)
              AND (coalesce(:query, '') = ''
                OR lower(p.name) LIKE lower(concat('%', :query, '%'))
                OR lower(p.external_id) LIKE lower(concat('%', :query, '%'))
                OR lower(p.provider) LIKE lower(concat('%', :query, '%')))
            ORDER BY
              CASE p.lifecycle_status
                WHEN 'ARCHIVED' THEN 0
                WHEN 'SOURCE_REMOVED' THEN 1
                WHEN 'EXPIRED' THEN 2
                ELSE 3
              END,
              p.archived_at DESC NULLS LAST,
              p.id DESC
            """,
            countQuery = """
            SELECT count(*)
            FROM policy p
            WHERE p.lifecycle_status <> 'ACTIVE'
              AND (coalesce(:lifecycleStatus, '') = ''
                OR p.lifecycle_status = :lifecycleStatus)
              AND (coalesce(:query, '') = ''
                OR lower(p.name) LIKE lower(concat('%', :query, '%'))
                OR lower(p.external_id) LIKE lower(concat('%', :query, '%'))
                OR lower(p.provider) LIKE lower(concat('%', :query, '%')))
            """,
            nativeQuery = true)
    Page<Long> findInactivePolicyIds(
            @Param("lifecycleStatus") String lifecycleStatus,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            select p
            from PolicyEntity p
            join fetch p.rule
            where p.lifecycleStatus = :status
              and (:category is null or p.category = :category)
              and (:regionCode is null or p.regionCode in (:regionCode, 'NATIONWIDE'))
            order by p.id
            """)
    List<PolicyEntity> findAllWithRuleForEligibility(
            @Param("status") PolicyLifecycleStatus status,
            @Param("category") String category,
            @Param("regionCode") String regionCode
    );

    @Query("""
            select count(p)
            from PolicyEntity p
            where p.lifecycleStatus = :status
              and p.rule is null
              and (:category is null or p.category = :category)
              and (:regionCode is null or p.regionCode in (:regionCode, 'NATIONWIDE'))
            """)
    long countWithoutRuleForEligibility(
            @Param("status") PolicyLifecycleStatus status,
            @Param("category") String category,
            @Param("regionCode") String regionCode
    );

    @Query(value = """
            SELECT p.id
            FROM policy p
            WHERE p.lifecycle_status = 'ACTIVE'
              AND (:category IS NULL OR p.category = :category)
              AND (:regionCode IS NULL OR p.region_code IN (:regionCode, 'NATIONWIDE'))
              AND (
                :rulePresence = 'ANY'
                OR (:rulePresence = 'WITH' AND EXISTS (
                  SELECT 1 FROM policy_rule rule WHERE rule.policy_id = p.id
                ))
                OR (:rulePresence = 'WITHOUT' AND NOT EXISTS (
                  SELECT 1 FROM policy_rule rule WHERE rule.policy_id = p.id
                ))
              )
              AND (:excludeDisabled = false OR p.id NOT IN (:excludedPolicyIds))
            ORDER BY
              CASE WHEN :sort = 'DEADLINE' THEN p.application_end END ASC NULLS LAST,
              CASE WHEN :sort = 'RECENT'
                THEN coalesce(p.source_updated_at, p.updated_at)
              END DESC NULLS LAST,
              CASE WHEN :sort = 'RECENT' THEN p.id END DESC,
              CASE WHEN :sort = 'NAME' THEN p.name END ASC,
              p.id
            """,
            countQuery = """
            SELECT count(*)
            FROM policy p
            WHERE p.lifecycle_status = 'ACTIVE'
              AND (:category IS NULL OR p.category = :category)
              AND (:regionCode IS NULL OR p.region_code IN (:regionCode, 'NATIONWIDE'))
              AND (
                :rulePresence = 'ANY'
                OR (:rulePresence = 'WITH' AND EXISTS (
                  SELECT 1 FROM policy_rule rule WHERE rule.policy_id = p.id
                ))
                OR (:rulePresence = 'WITHOUT' AND NOT EXISTS (
                  SELECT 1 FROM policy_rule rule WHERE rule.policy_id = p.id
                ))
              )
              AND (:excludeDisabled = false OR p.id NOT IN (:excludedPolicyIds))
            """,
            nativeQuery = true)
    Page<Long> findEligibilityPageIds(
            @Param("category") String category,
            @Param("regionCode") String regionCode,
            @Param("rulePresence") String rulePresence,
            @Param("sort") String sort,
            @Param("excludeDisabled") boolean excludeDisabled,
            @Param("excludedPolicyIds") List<Long> excludedPolicyIds,
            Pageable pageable
    );

    @Query("""
            select distinct p.category
            from PolicyEntity p
            where p.lifecycleStatus = :status
            order by p.category
            """)
    List<String> findDistinctCategories(@Param("status") PolicyLifecycleStatus status);
}

