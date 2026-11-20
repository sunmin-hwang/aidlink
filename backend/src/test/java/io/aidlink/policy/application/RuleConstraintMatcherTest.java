package io.aidlink.policy.application;

import io.aidlink.eligibility.domain.AndRule;
import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.eligibility.domain.ConditionRule;
import io.aidlink.eligibility.domain.Operator;
import io.aidlink.eligibility.domain.OrRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleConstraintMatcherTest {

    @Test
    void appliesAllConstraintsForTheSelectedFieldInsideAnd() {
        var rule = new AndRule(List.of(
                ConditionRule.of(ConditionField.AGE, Operator.GTE, "성인", "19"),
                ConditionRule.of(ConditionField.AGE, Operator.LTE, "청년", "34"),
                ConditionRule.of(ConditionField.REGION, Operator.EQ, "서울", "SEOUL")
        ));

        assertThat(RuleConstraintMatcher.matches(rule, ConditionField.AGE, 29)).isTrue();
        assertThat(RuleConstraintMatcher.matches(rule, ConditionField.AGE, 40)).isFalse();
    }

    @Test
    void acceptsWhenAnOrBranchDoesNotConstrainTheSelectedField() {
        var rule = new OrRule(List.of(
                ConditionRule.of(ConditionField.EMPLOYMENT, Operator.EQ, "미취업", "UNEMPLOYED"),
                ConditionRule.of(ConditionField.WEEKLY_WORK_HOURS, Operator.LTE, "단시간 근로", "30")
        ));

        assertThat(RuleConstraintMatcher.matches(
                rule, ConditionField.EMPLOYMENT, "EMPLOYED"))
                .isTrue();
    }

    @Test
    void requiresOneMatchingBranchWhenEveryOrBranchConstrainsTheField() {
        var rule = new OrRule(List.of(
                ConditionRule.of(ConditionField.EMPLOYMENT, Operator.EQ, "미취업", "UNEMPLOYED"),
                ConditionRule.of(ConditionField.EMPLOYMENT, Operator.EQ, "구직", "JOB_SEEKER")
        ));

        assertThat(RuleConstraintMatcher.matches(
                rule, ConditionField.EMPLOYMENT, "JOB_SEEKER"))
                .isTrue();
        assertThat(RuleConstraintMatcher.matches(
                rule, ConditionField.EMPLOYMENT, "EMPLOYED"))
                .isFalse();
    }
}
