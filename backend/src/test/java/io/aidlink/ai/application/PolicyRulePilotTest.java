package io.aidlink.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aidlink.ai.domain.*;
import io.aidlink.eligibility.application.ConditionValueResolver;
import io.aidlink.eligibility.application.RuleEngine;
import io.aidlink.eligibility.domain.EvaluationStatus;
import io.aidlink.policy.infrastructure.jpa.RuleJsonMapper;
import io.aidlink.user.domain.UserProfile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRulePilotTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RuleJsonMapper rules = new RuleJsonMapper(mapper);
    private final RuleEngine engine = new RuleEngine(new ConditionValueResolver());
    private final LocalDate date = LocalDate.of(2026, 11, 27);

    @Test
    void allThreeDraftsPreserveEvidenceAndRequireReviewInsteadOfClaimingEligibility() throws Exception {
        var seeds = seeds();
        assertThat(seeds.size()).isEqualTo(3);
        for (var seed : seeds) {
            var source = seed.path("snapshot");
            var candidate = new PolicyRuleCandidate(seed.path("rule"), List.of(),
                    "codex-local-pilot", null, 0, 0);
            var result = new AutomaticRuleValidator(rules).validate(
                    new StoredPolicyRuleCandidate(1, 1, "unused-in-offline-pilot", candidate, Instant.EPOCH),
                    new PolicyQualificationText(1, source.path("name").asText(),
                            source.path("target_description").asText(), source.path("description").asText(),
                            source.path("application_method").asText()));
            assertThat(result.status()).isEqualTo(RuleValidationStatus.REVIEW_REQUIRED);
            assertThat(result.issues()).extracting(RuleValidationIssue::code)
                    .contains("MANUAL_REVIEW_REQUIRED")
                    .doesNotContain("SOURCE_TEXT_NOT_FOUND", "RULE_MAPPING_FAILED");
            assertThat(engine.evaluate(rules.fromJson(seed.path("rule")), profile(date.minusYears(30)), date).status())
                    .isEqualTo(EvaluationStatus.UNKNOWN);
        }
    }

    @Test
    void seosanAgeBoundaryAndMissingBirthDateRemainConservative() throws Exception {
        JsonNode seosan = null;
        for (var seed : seeds()) {
            if (seed.path("externalId").asText().equals("GOV24:453000000196")) seosan = seed;
        }
        assertThat(seosan).isNotNull();
        var rule = rules.fromJson(seosan.path("rule"));
        assertThat(engine.evaluate(rule, profile(date.minusYears(19).plusDays(1)), date).status())
                .isEqualTo(EvaluationStatus.FAIL);
        var onBirthday = engine.evaluate(rule, profile(date.minusYears(19)), date);
        assertThat(onBirthday.conditions().getFirst().status()).isEqualTo(EvaluationStatus.PASS);
        assertThat(onBirthday.status()).isEqualTo(EvaluationStatus.UNKNOWN);
        assertThat(engine.evaluate(rule, profile(null), date).status()).isEqualTo(EvaluationStatus.UNKNOWN);
    }

    private JsonNode seeds() throws Exception {
        try (var resource = getClass().getResourceAsStream(
                "/db/migration/V21__source_backed_policy_rule_pilot.sql")) {
            assertThat(resource).isNotNull();
            String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            return mapper.readTree(sql.split("\\$seeds\\$")[1]);
        }
    }

    private UserProfile profile(LocalDate birthDate) {
        return new UserProfile(birthDate, "CHUNGNAM", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }
}
