package io.aidlink.policy.application;

import io.aidlink.policy.domain.Policy;

import java.util.List;
import java.util.Optional;

public interface PolicyCatalog {
    List<Policy> findAll();

    long count();

    PageResult<Policy> search(PolicySearchCriteria criteria);

    Optional<Policy> findById(long policyId);
}
