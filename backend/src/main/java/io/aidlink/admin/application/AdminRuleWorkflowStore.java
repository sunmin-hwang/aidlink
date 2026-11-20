package io.aidlink.admin.application;

import com.fasterxml.jackson.databind.JsonNode;

public interface AdminRuleWorkflowStore {

    void saveDraft(long policyId, JsonNode rule);

    void verify(long policyId);
}
