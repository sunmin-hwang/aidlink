package io.aidlink.eligibility.domain;

import java.util.List;

public record ConditionEvaluation(
        ConditionField field,
        Operator operator,
        List<String> expectedValues,
        String actualValue,
        EvaluationStatus status,
        String description,
        String message
) {
    public ConditionEvaluation {
        expectedValues = List.copyOf(expectedValues);
    }
}

