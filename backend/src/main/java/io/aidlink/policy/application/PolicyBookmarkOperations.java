package io.aidlink.policy.application;

public interface PolicyBookmarkOperations {

    PageResult<Long> findPolicyIds(String email, int page, int size);

    boolean contains(String email, long policyId);

    void add(String email, long policyId);

    void remove(String email, long policyId);
}
