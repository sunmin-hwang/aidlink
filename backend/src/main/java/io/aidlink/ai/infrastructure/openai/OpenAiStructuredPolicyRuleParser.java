package io.aidlink.ai.infrastructure.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aidlink.ai.application.PolicyRuleParser;
import io.aidlink.ai.domain.PolicyQualificationText;
import io.aidlink.ai.domain.PolicyRuleCandidate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "aidlink.ai.openai",
        name = "enabled",
        havingValue = "true"
)
public class OpenAiStructuredPolicyRuleParser implements PolicyRuleParser {

    private static final String INSTRUCTIONS = """
            You extract eligibility conditions from official Korean public-policy text.
            Return only the requested structured output. Never decide whether a person is eligible.
            Preserve AND/OR relationships and numeric units. Do not invent a condition absent from the source.
            If a condition requires agency discretion or cannot be represented, emit MANUAL_REVIEW with REQUIRED,
            an empty expectedValues array, and explain it in both description and ambiguities.
            Copy the exact supporting phrase into sourceText for every CONDITION node.
            """;

    private final RestClient restClient;
    private final OpenAiRuleParserProperties properties;
    private final ObjectMapper objectMapper;
    private final JsonNode schema;

    public OpenAiStructuredPolicyRuleParser(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAiRuleParserProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.schema = loadSchema(objectMapper);
    }

    @Override
    public PolicyRuleCandidate parse(PolicyQualificationText sourceText) {
        JsonNode response = restClient.post()
                .uri("/responses")
                .body(requestBody(sourceText))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !"completed".equals(response.path("status").asText())) {
            throw new IllegalStateException("OpenAI rule parsing response did not complete");
        }

        String outputText = extractOutputText(response);
        try {
            JsonNode parsed = objectMapper.readTree(outputText);
            JsonNode rule = parsed.get("rule");
            JsonNode ambiguitiesNode = parsed.get("ambiguities");
            if (rule == null || !rule.isObject() || ambiguitiesNode == null || !ambiguitiesNode.isArray()) {
                throw new IllegalStateException("Structured rule output is missing rule or ambiguities");
            }
            List<String> ambiguities = new ArrayList<>();
            ambiguitiesNode.forEach(item -> ambiguities.add(item.asText()));
            return new PolicyRuleCandidate(
                    rule,
                    ambiguities,
                    response.path("model").asText(properties.getModel()),
                    response.path("id").asText(),
                    response.path("usage").path("input_tokens").asLong(),
                    response.path("usage").path("output_tokens").asLong()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not parse OpenAI structured rule output", exception);
        }
    }

    private ObjectNode requestBody(PolicyQualificationText sourceText) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("store", false);
        root.put("instructions", INSTRUCTIONS);
        root.put("input", objectMapper.valueToTree(sourceText).toString());

        ObjectNode format = objectMapper.createObjectNode();
        format.put("type", "json_schema");
        format.put("name", "aidlink_policy_rule_candidate");
        format.put("strict", true);
        format.set("schema", schema);
        ObjectNode text = root.putObject("text");
        text.set("format", format);
        return root;
    }

    private String extractOutputText(JsonNode response) {
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("refusal".equals(content.path("type").asText())) {
                    throw new IllegalStateException(
                            "OpenAI refused the rule parsing request: " + content.path("refusal").asText()
                    );
                }
                if ("output_text".equals(content.path("type").asText())
                        && !content.path("text").asText().isBlank()) {
                    return content.path("text").asText();
                }
            }
        }
        throw new IllegalStateException("OpenAI response did not contain structured output text");
    }

    private JsonNode loadSchema(ObjectMapper mapper) {
        try (var input = new ClassPathResource("ai/policy-rule-candidate-schema.json").getInputStream()) {
            return mapper.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the policy rule JSON Schema", exception);
        }
    }
}
