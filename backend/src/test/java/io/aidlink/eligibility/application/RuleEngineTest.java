package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.AndRule;
import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.eligibility.domain.ConditionRule;
import io.aidlink.eligibility.domain.EvaluationStatus;
import io.aidlink.eligibility.domain.Operator;
import io.aidlink.eligibility.domain.OrRule;
import io.aidlink.eligibility.domain.RuleEvaluation;
import io.aidlink.user.domain.EmploymentStatus;
import io.aidlink.user.domain.HomeOwnership;
import io.aidlink.user.domain.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private static final LocalDate EVALUATION_DATE = LocalDate.of(2026, 10, 7);
    private final RuleEngine ruleEngine = new RuleEngine(new ConditionValueResolver());

    @ParameterizedTest
    @CsvSource({
            "2008-10-08, FAIL",
            "2007-10-07, PASS",
            "1992-10-07, PASS",
            "1991-10-06, FAIL"
    })
    void evaluatesAgeBoundaryInclusively(LocalDate birthDate, EvaluationStatus expected) {
        ConditionRule rule = ConditionRule.of(
                ConditionField.AGE,
                Operator.BETWEEN,
                "만 19~34세",
                "19",
                "34"
        );

        RuleEvaluation result = ruleEngine.evaluate(rule, profile(birthDate, null, null, null, null), EVALUATION_DATE);

        assertThat(result.status()).isEqualTo(expected);
    }

    @Test
    void missingValueIsUnknownInsteadOfFail() {
        ConditionRule rule = ConditionRule.of(
                ConditionField.PERSONAL_INCOME,
                Operator.LTE,
                "월 개인소득 250만원 이하",
                "2500000"
        );

        RuleEvaluation result = ruleEngine.evaluate(rule, profile(null, null, null, null, null), EVALUATION_DATE);

        assertThat(result.status()).isEqualTo(EvaluationStatus.UNKNOWN);
        assertThat(result.blockingConditions()).isEmpty();
    }

    @Test
    void andReturnsFailWhenAnotherChildIsUnknown() {
        AndRule rule = new AndRule(List.of(
                ConditionRule.of(ConditionField.REGION, Operator.EQ, "서울 거주", "SEOUL"),
                ConditionRule.of(ConditionField.PERSONAL_INCOME, Operator.LTE, "소득 기준", "2500000")
        ));

        RuleEvaluation result = ruleEngine.evaluate(
                rule,
                profile(null, "BUSAN", null, null, null),
                EVALUATION_DATE
        );

        assertThat(result.status()).isEqualTo(EvaluationStatus.FAIL);
        assertThat(result.blockingConditions())
                .extracting(condition -> condition.field())
                .containsExactly(ConditionField.REGION);
    }

    @Test
    void orReturnsUnknownWhenNoBranchPassesAndOneBranchIsUnknown() {
        OrRule rule = new OrRule(List.of(
                ConditionRule.of(ConditionField.EMPLOYMENT, Operator.EQ, "실업 상태", "UNEMPLOYED"),
                ConditionRule.of(ConditionField.WEEKLY_WORK_HOURS, Operator.LTE, "주 30시간 이하", "30")
        ));

        RuleEvaluation result = ruleEngine.evaluate(
                rule,
                profile(null, null, EmploymentStatus.EMPLOYED, null, null),
                EVALUATION_DATE
        );

        assertThat(result.status()).isEqualTo(EvaluationStatus.UNKNOWN);
    }

    @Test
    void orSelectsTheBranchWithTheFewestBlockingConditions() {
        OrRule rule = new OrRule(List.of(
                new AndRule(List.of(
                        ConditionRule.of(ConditionField.REGION, Operator.EQ, "서울 거주", "SEOUL"),
                        ConditionRule.of(ConditionField.PERSONAL_INCOME, Operator.LTE, "소득 기준", "1000000")
                )),
                ConditionRule.of(ConditionField.HOME_OWNERSHIP, Operator.EQ, "무주택", "NONE")
        ));

        RuleEvaluation result = ruleEngine.evaluate(
                rule,
                profile(null, "BUSAN", null, new BigDecimal("5000000"), HomeOwnership.OWNED_BY_SELF),
                EVALUATION_DATE
        );

        assertThat(result.status()).isEqualTo(EvaluationStatus.FAIL);
        assertThat(result.blockingConditions())
                .extracting(condition -> condition.field())
                .containsExactly(ConditionField.HOME_OWNERSHIP);
    }

    static UserProfile profile(
            LocalDate birthDate,
            String region,
            EmploymentStatus employment,
            BigDecimal income,
            HomeOwnership homeOwnership
    ) {
        return new UserProfile(
                birthDate,
                region,
                employment,
                null,
                income,
                null,
                null,
                null,
                null,
                null,
                null,
                homeOwnership,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}

