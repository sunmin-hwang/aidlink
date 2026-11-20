package io.aidlink.policy.infrastructure.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aidlink.eligibility.domain.AndRule;
import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.eligibility.domain.ConditionRule;
import io.aidlink.eligibility.domain.Operator;
import io.aidlink.eligibility.domain.OrRule;
import io.aidlink.eligibility.domain.RuleNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuleJsonMapper {

    private final ObjectMapper objectMapper;

    public RuleJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuleNode fromJson(JsonNode json) {
        String type = requiredText(json, "type");
        return switch (type) {
            case "AND" -> new AndRule(readChildren(json));
            case "OR" -> new OrRule(readChildren(json));
            case "CONDITION" -> new ConditionRule(
                    ConditionField.valueOf(requiredText(json, "field")),
                    Operator.valueOf(requiredText(json, "operator")),
                    readTextValues(json.path("expectedValues")),
                    requiredText(json, "description")
            );
            default -> throw new IllegalArgumentException("Unsupported rule type: " + type);
        };
    }

    public JsonNode toJson(RuleNode rule) {
        if (rule instanceof AndRule andRule) {
            return compositeJson("AND", andRule.children());
        }
        if (rule instanceof OrRule orRule) {
            return compositeJson("OR", orRule.children());
        }
        if (rule instanceof ConditionRule condition) {
            ObjectNode json = objectMapper.createObjectNode();
            json.put("type", "CONDITION");
            json.put("field", condition.field().name());
            json.put("operator", condition.operator().name());
            json.set("expectedValues", objectMapper.valueToTree(condition.expectedValues()));
            json.put("description", condition.description());
            return json;
        }
        throw new IllegalArgumentException("Unsupported rule node: " + rule.getClass().getName());
    }

    private JsonNode compositeJson(String type, List<RuleNode> children) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("type", type);
        var childrenJson = json.putArray("children");
        children.forEach(child -> childrenJson.add(toJson(child)));
        return json;
    }

    private List<RuleNode> readChildren(JsonNode json) {
        JsonNode children = json.path("children");
        if (!children.isArray() || children.isEmpty()) {
            throw new IllegalArgumentException("Rule group requires children");
        }
        List<RuleNode> result = new ArrayList<>();
        children.forEach(child -> result.add(fromJson(child)));
        return result;
    }

    private List<String> readTextValues(JsonNode values) {
        if (!values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private String requiredText(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing rule field: " + field);
        }
        return value.asText();
    }
}

