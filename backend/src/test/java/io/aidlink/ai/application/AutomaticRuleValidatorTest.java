package io.aidlink.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.ai.domain.PolicyQualificationText;
import io.aidlink.ai.domain.PolicyRuleCandidate;
import io.aidlink.ai.domain.RuleValidationStatus;
import io.aidlink.ai.domain.StoredPolicyRuleCandidate;
import io.aidlink.policy.infrastructure.jpa.RuleJsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutomaticRuleValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AutomaticRuleValidator validator =
            new AutomaticRuleValidator(new RuleJsonMapper(objectMapper));

    @Test
    void autoValidatesAWellSupportedRuleWithExactSourceEvidence() throws Exception {
        var result = validator.validate(
                stored(rule("""
                        {
                          "type":"CONDITION",
                          "field":"AGE",
                          "operator":"BETWEEN",
                          "expectedValues":["19","34"],
                          "description":"만 19~34세",
                          "sourceText":"만 19세 이상 34세 이하"
                        }
                        """), List.of()),
                source("지원대상은 만 19세 이상 34세 이하입니다.")
        );

        assertThat(result.status()).isEqualTo(RuleValidationStatus.AUTO_VALIDATED);
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void rejectsAConditionWhoseEvidenceWasNotInTheOfficialText() throws Exception {
        var result = validator.validate(
                stored(rule("""
                        {
                          "type":"CONDITION",
                          "field":"PERSONAL_INCOME",
                          "operator":"LTE",
                          "expectedValues":["2500000"],
                          "description":"월소득 250만원 이하",
                          "sourceText":"월소득 250만원 이하"
                        }
                        """), List.of()),
                source("지원대상은 만 19세 이상 34세 이하입니다.")
        );

        assertThat(result.status()).isEqualTo(RuleValidationStatus.REVIEW_REQUIRED);
        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("SOURCE_TEXT_NOT_FOUND");
    }

    @Test
    void manualReviewAlwaysEntersTheReviewQueue() throws Exception {
        var result = validator.validate(
                stored(rule("""
                        {
                          "type":"CONDITION",
                          "field":"MANUAL_REVIEW",
                          "operator":"REQUIRED",
                          "expectedValues":[],
                          "description":"기관 개별 심사 필요",
                          "sourceText":"기관이 지원 필요성을 인정하는 자"
                        }
                        """), List.of("기관 재량 판단")),
                source("기관이 지원 필요성을 인정하는 자")
        );

        assertThat(result.status()).isEqualTo(RuleValidationStatus.REVIEW_REQUIRED);
        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("MANUAL_REVIEW_REQUIRED", "AMBIGUOUS_SOURCE_CONDITION");
    }

    @Test
    void rejectsUnsupportedFieldsEvenIfTheJsonShapeLooksValid() throws Exception {
        var result = validator.validate(
                stored(rule("""
                        {
                          "type":"CONDITION",
                          "field":"FAVORITE_COLOR",
                          "operator":"EQ",
                          "expectedValues":["BLUE"],
                          "description":"좋아하는 색상",
                          "sourceText":"좋아하는 색상은 파랑"
                        }
                        """), List.of()),
                source("좋아하는 색상은 파랑")
        );

        assertThat(result.status()).isEqualTo(RuleValidationStatus.REVIEW_REQUIRED);
        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("UNSUPPORTED_FIELD", "RULE_MAPPING_FAILED");
    }

    private StoredPolicyRuleCandidate stored(JsonNode rule, List<String> ambiguities) {
        return new StoredPolicyRuleCandidate(
                10L,
                1L,
                "a".repeat(64),
                new PolicyRuleCandidate(rule, ambiguities, "test-model", "resp-test", 10, 10),
                Instant.parse("2026-10-08T00:00:00Z")
        );
    }

    private PolicyQualificationText source(String target) {
        return new PolicyQualificationText(1L, "테스트 정책", target, "", "");
    }

    private JsonNode rule(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
