package io.aidlink.admin.infrastructure.memory;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.admin.application.AdminRuleWorkflowStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryAdminRuleWorkflowStore implements AdminRuleWorkflowStore {

    private final Map<Long, JsonNode> drafts = new ConcurrentHashMap<>();

    @Override
    public void saveDraft(long policyId, JsonNode rule) {
        drafts.put(policyId, rule.deepCopy());
    }

    @Override
    public void verify(long policyId) {
        if (!drafts.containsKey(policyId)) {
            throw new IllegalStateException("No rule draft exists for policy: " + policyId);
        }
    }
}
