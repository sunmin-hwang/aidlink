package io.aidlink.admin.infrastructure.memory;

import io.aidlink.admin.application.AdminPolicyLifecycleCriteria;
import io.aidlink.admin.application.AdminPolicyLifecycleStore;
import io.aidlink.admin.application.AdminPolicyLifecycleView;
import io.aidlink.admin.application.PolicyDeletionReferenceCounts;
import io.aidlink.policy.application.PageResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("!postgres")
public class InMemoryAdminPolicyLifecycleStore implements AdminPolicyLifecycleStore {

    @Override
    public PageResult<AdminPolicyLifecycleView> findInactive(
            AdminPolicyLifecycleCriteria criteria
    ) {
        return new PageResult<>(List.of(), 0, 0, criteria.size());
    }

    @Override
    public Optional<AdminPolicyLifecycleView> findById(long policyId) {
        return Optional.empty();
    }

    @Override
    public void restore(long policyId) {
        throw new IllegalArgumentException("Policy not found: " + policyId);
    }

    @Override
    public Optional<PolicyDeletionReferenceCounts> findReferenceCounts(long policyId) {
        return Optional.empty();
    }
}
