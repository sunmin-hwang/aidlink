package io.aidlink.eligibility.domain;

import java.util.List;

public record RuleEvaluation(
        EvaluationStatus status,
        List<ConditionEvaluation> conditions,
        List<ConditionEvaluation> blockingConditions,
        List<ConditionEvaluation> relevantConditions
) {
    public RuleEvaluation {
        conditions = List.copyOf(conditions);
        blockingConditions = List.copyOf(blockingConditions);
        relevantConditions = List.copyOf(relevantConditions);
    }

    public RuleEvaluation(
            EvaluationStatus status,
            List<ConditionEvaluation> conditions,
            List<ConditionEvaluation> blockingConditions
    ) {
        this(status, conditions, blockingConditions, conditions);
    }
}

