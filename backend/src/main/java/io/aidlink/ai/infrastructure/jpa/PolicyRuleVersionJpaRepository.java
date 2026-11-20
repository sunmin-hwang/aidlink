package io.aidlink.ai.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyRuleVersionJpaRepository
        extends JpaRepository<PolicyRuleVersionEntity, Long> {

    Optional<PolicyRuleVersionEntity> findFirstByPolicyIdOrderByVersionDesc(long policyId);

    Optional<PolicyRuleVersionEntity> findFirstByPolicyIdAndEffectiveToIsNull(long policyId);
}
