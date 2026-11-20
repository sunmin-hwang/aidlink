package io.aidlink.ai.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRuleValidationJpaRepository
        extends JpaRepository<PolicyRuleValidationEntity, Long> {

    boolean existsByCandidateId(long candidateId);

    java.util.Optional<PolicyRuleValidationEntity>
            findFirstByCandidatePolicyIdOrderByCreatedAtDesc(long policyId);
}
