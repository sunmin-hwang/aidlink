package io.aidlink.admin.application;

import io.aidlink.policy.application.PageResult;

import java.util.Optional;

public interface AdminPolicyLifecycleStore {

    PageResult<AdminPolicyLifecycleView> findInactive(AdminPolicyLifecycleCriteria criteria);

    Optional<AdminPolicyLifecycleView> findById(long policyId);

    void restore(long policyId);

    Optional<PolicyDeletionReferenceCounts> findReferenceCounts(long policyId);
}
