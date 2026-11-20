package io.aidlink.policy.infrastructure.jpa;

import io.aidlink.policy.domain.PolicyLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyRuleJpaRepository extends JpaRepository<PolicyRuleEntity, Long> {

    @Query("select rule from PolicyRuleEntity rule where rule.policy.id = :policyId")
    Optional<PolicyRuleEntity> findByPolicyId(@Param("policyId") long policyId);

    @Query("""
            select rule
            from PolicyRuleEntity rule
            join fetch rule.policy policy
            where policy.id in :policyIds and policy.lifecycleStatus = :status
            """)
    List<PolicyRuleEntity> findAllByPolicyIdIn(
            @Param("policyIds") List<Long> policyIds,
            @Param("status") PolicyLifecycleStatus status
    );
}
