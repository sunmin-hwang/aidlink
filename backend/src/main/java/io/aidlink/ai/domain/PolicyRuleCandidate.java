package io.aidlink.ai.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record PolicyRuleCandidate(
        JsonNode rule,
        List<String> ambiguities,
        String model,
        String responseId,
        long inputTokens,
        long outputTokens
) {
    public PolicyRuleCandidate {
        rule = rule.deepCopy();
        ambiguities = List.copyOf(ambiguities);
    }
}
