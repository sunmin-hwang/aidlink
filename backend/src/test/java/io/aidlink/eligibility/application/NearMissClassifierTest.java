package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.AndRule;
import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.eligibility.domain.ConditionRule;
import io.aidlink.eligibility.domain.EligibilityStatus;
import io.aidlink.eligibility.domain.Operator;
import io.aidlink.eligibility.domain.OrRule;
import io.aidlink.eligibility.domain.RuleEvaluation;
import io.aidlink.user.domain.EmploymentStatus;
import io.aidlink.user.domain.HomeOwnership;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.aidlink.eligibility.application.RuleEngineTest.profile;
import static org.assertj.core.api.Assertions.assertThat;

class NearMissClassifierTest {

    @Test
    void classifiesOneBlockingConditionAfterThreePassesAsNearMiss() {
        RuleEngine engine = new RuleEngine(new ConditionValueResolver());
        AndRule rule = new AndRule(List.of(
                ConditionRule.of(ConditionField.AGE, Operator.BETWEEN, "연령", "19", "34"),
                ConditionRule.of(ConditionField.REGION, Operator.EQ, "지역", "SEOUL"),
                ConditionRule.of(ConditionField.EMPLOYMENT, Operator.EQ, "취업상태", "UNEMPLOYED"),
                ConditionRule.of(ConditionField.PERSONAL_INCOME, Operator.LTE, "소득", "500000")
        ));
        RuleEvaluation evaluation = engine.evaluate(
                rule,
                profile(
                        LocalDate.of(2000, 1, 1),
                        "SEOUL",
                        EmploymentStatus.UNEMPLOYED,
                        new BigDecimal("800000"),
                        HomeOwnership.NONE
                ),
                LocalDate.of(2026, 10, 7)
        );

        EligibilityStatus result = new NearMissClassifier().classify(evaluation);

        assertThat(result).isEqualTo(EligibilityStatus.NEAR_MISS);
        assertThat(evaluation.blockingConditions())
                .extracting(condition -> condition.field())
                .containsExactly(ConditionField.PERSONAL_INCOME);
    }

    @Test
    void ignoresUnknownConditionOnAnUnusedPassingOrBranch() {
        RuleEngine engine = new RuleEngine(new ConditionValueResolver());
        AndRule rule = new AndRule(List.of(
                ConditionRule.of(ConditionField.AGE, Operator.BETWEEN, "연령", "19", "34"),
                ConditionRule.of(ConditionField.REGION, Operator.EQ, "지역", "SEOUL"),
                new OrRule(List.of(
                        ConditionRule.of(ConditionField.EMPLOYMENT, Operator.EQ, "미취업", "UNEMPLOYED"),
                        ConditionRule.of(ConditionField.WEEKLY_WORK_HOURS, Operator.LTE, "주 30시간 이하", "30")
                )),
                ConditionRule.of(ConditionField.HOME_OWNERSHIP, Operator.EQ, "무주택", "NONE"),
                ConditionRule.of(ConditionField.PERSONAL_INCOME, Operator.LTE, "소득", "500000")
        ));

        RuleEvaluation evaluation = engine.evaluate(
                rule,
                profile(
                        LocalDate.of(2000, 1, 1),
                        "SEOUL",
                        EmploymentStatus.UNEMPLOYED,
                        new BigDecimal("800000"),
                        HomeOwnership.NONE
                ),
                LocalDate.of(2026, 10, 7)
        );

        assertThat(new NearMissClassifier().classify(evaluation))
                .isEqualTo(EligibilityStatus.NEAR_MISS);
        assertThat(evaluation.conditions())
                .anyMatch(condition -> condition.field() == ConditionField.WEEKLY_WORK_HOURS
                        && condition.status() == io.aidlink.eligibility.domain.EvaluationStatus.UNKNOWN);
        assertThat(evaluation.relevantConditions())
                .noneMatch(condition -> condition.field() == ConditionField.WEEKLY_WORK_HOURS);
    }
}

