package io.aidlink.eligibility.domain;

import java.util.Arrays;
import java.util.List;

public record ConditionRule(
        ConditionField field,
        Operator operator,
        List<String> expectedValues,
        String description
) implements RuleNode {

    public ConditionRule {
        if (field == null || operator == null) {
            throw new IllegalArgumentException("Condition field and operator are required");
        }
        expectedValues = expectedValues == null ? List.of() : List.copyOf(expectedValues);
        if (operator == Operator.BETWEEN && expectedValues.size() != 2) {
            throw new IllegalArgumentException("BETWEEN requires exactly two values");
        }
        if (operator != Operator.REQUIRED && expectedValues.isEmpty()) {
            throw new IllegalArgumentException(operator + " requires an expected value");
        }
    }

    public static ConditionRule of(
            ConditionField field,
            Operator operator,
            String description,
            String... expectedValues
    ) {
        return new ConditionRule(field, operator, Arrays.asList(expectedValues), description);
    }

    @Override
    public RuleType type() {
        return RuleType.CONDITION;
    }
}

