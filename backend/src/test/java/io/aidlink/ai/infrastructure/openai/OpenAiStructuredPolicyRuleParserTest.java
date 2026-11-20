package io.aidlink.ai.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.ai.domain.PolicyQualificationText;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiStructuredPolicyRuleParserTest {

    @Test
    void sendsStrictSchemaAndParsesTheStructuredResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl("https://api.openai.com/v1").build();
        OpenAiRuleParserProperties properties = new OpenAiRuleParserProperties();
        properties.setModel("gpt-5.6-terra");
        var parser = new OpenAiStructuredPolicyRuleParser(restClient, properties, objectMapper);

        var structuredOutput = objectMapper.createObjectNode();
        structuredOutput.set("rule", objectMapper.readTree("""
                {
                  "type":"CONDITION",
                  "field":"AGE",
                  "operator":"BETWEEN",
                  "expectedValues":["19","34"],
                  "description":"만 19~34세",
                  "sourceText":"만 19세 이상 34세 이하"
                }
                """));
        structuredOutput.putArray("ambiguities");
        var response = objectMapper.createObjectNode();
        response.put("id", "resp_test_1");
        response.put("status", "completed");
        response.put("model", "gpt-5.6-terra");
        response.putArray("output").addObject()
                .put("type", "message")
                .putArray("content").addObject()
                .put("type", "output_text")
                .put("text", objectMapper.writeValueAsString(structuredOutput));
        response.putObject("usage")
                .put("input_tokens", 120)
                .put("output_tokens", 45);

        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gpt-5.6-terra"))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(response),
                        MediaType.APPLICATION_JSON
                ));

        var candidate = parser.parse(new PolicyQualificationText(
                1L,
                "청년 지원",
                "만 19세 이상 34세 이하",
                "",
                ""
        ));

        assertThat(candidate.rule().path("field").asText()).isEqualTo("AGE");
        assertThat(candidate.ambiguities()).isEmpty();
        assertThat(candidate.responseId()).isEqualTo("resp_test_1");
        assertThat(candidate.inputTokens()).isEqualTo(120);
        assertThat(candidate.outputTokens()).isEqualTo(45);
        server.verify();
    }
}
