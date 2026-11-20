package io.aidlink.eligibility.application;

import io.aidlink.policy.application.PageResult;
import io.aidlink.policy.domain.Policy;

import java.util.List;

public interface EligibilityPolicyCatalog {

    List<Policy> findWithRules(String category, String regionCode);

    long countWithoutRules(String category, String regionCode);

    PageResult<Policy> findPage(
            String category,
            String regionCode,
            List<Long> excludedPolicyIds,
            EligibilityRulePresence rulePresence,
            int page,
            int size,
            EligibilityPolicySort sort
    );

    List<String> findCategories();
}
