package io.aidlink.eligibility.application;

import io.aidlink.eligibility.domain.AndRule;
import io.aidlink.eligibility.domain.ConditionEvaluation;
import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.eligibility.domain.ConditionRule;
import io.aidlink.eligibility.domain.EvaluationStatus;
import io.aidlink.eligibility.domain.Operator;
import io.aidlink.eligibility.domain.OrRule;
import io.aidlink.eligibility.domain.RuleEvaluation;
import io.aidlink.eligibility.domain.RuleNode;
import io.aidlink.user.domain.UserProfile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RuleEngine {

    private final ConditionValueResolver valueResolver;

    public RuleEngine(ConditionValueResolver valueResolver) {
        this.valueResolver = valueResolver;
    }

    public RuleEvaluation evaluate(RuleNode rule, UserProfile profile, LocalDate evaluationDate) {
        NodeEvaluation result = evaluateNode(rule, profile, evaluationDate);
        return new RuleEvaluation(
                result.status(),
                result.conditions(),
                result.blockers(),
                result.relevantConditions()
        );
    }

    private NodeEvaluation evaluateNode(RuleNode node, UserProfile profile, LocalDate evaluationDate) {
        if (node instanceof ConditionRule condition) {
            return evaluateCondition(condition, profile, evaluationDate);
        }
        if (node instanceof AndRule andRule) {
            return evaluateAnd(andRule, profile, evaluationDate);
        }
        if (node instanceof OrRule orRule) {
            return evaluateOr(orRule, profile, evaluationDate);
        }
        throw new IllegalArgumentException("Unsupported rule node: " + node.getClass().getName());
    }

    private NodeEvaluation evaluateCondition(
            ConditionRule rule,
            UserProfile profile,
            LocalDate evaluationDate
    ) {
        Object actual = valueResolver.resolve(rule.field(), profile, evaluationDate);
        EvaluationStatus status;

        if (rule.field() == ConditionField.MANUAL_REVIEW || actual == null) {
            status = EvaluationStatus.UNKNOWN;
        } else {
            status = matches(rule, actual) ? EvaluationStatus.PASS : EvaluationStatus.FAIL;
        }

        String actualValue = actual == null ? null : normalize(actual);
        ConditionEvaluation result = new ConditionEvaluation(
                rule.field(),
                rule.operator(),
                rule.expectedValues(),
                actualValue,
                status,
                rule.description(),
                messageFor(status)
        );
        List<ConditionEvaluation> blockers = status == EvaluationStatus.FAIL ? List.of(result) : List.of();
        return new NodeEvaluation(status, List.of(result), blockers, List.of(result));
    }

    private NodeEvaluation evaluateAnd(AndRule rule, UserProfile profile, LocalDate evaluationDate) {
        List<NodeEvaluation> children = rule.children().stream()
                .map(child -> evaluateNode(child, profile, evaluationDate))
                .toList();

        EvaluationStatus status = children.stream().anyMatch(it -> it.status() == EvaluationStatus.FAIL)
                ? EvaluationStatus.FAIL
                : children.stream().anyMatch(it -> it.status() == EvaluationStatus.UNKNOWN)
                ? EvaluationStatus.UNKNOWN
                : EvaluationStatus.PASS;

        List<ConditionEvaluation> conditions = children.stream()
                .flatMap(it -> it.conditions().stream())
                .toList();
        List<ConditionEvaluation> blockers = status == EvaluationStatus.FAIL
                ? children.stream()
                    .filter(it -> it.status() == EvaluationStatus.FAIL)
                    .flatMap(it -> it.blockers().stream())
                    .toList()
                : List.of();
        List<ConditionEvaluation> relevantConditions = children.stream()
                .flatMap(it -> it.relevantConditions().stream())
                .toList();
        return new NodeEvaluation(status, conditions, blockers, relevantConditions);
    }

    private NodeEvaluation evaluateOr(OrRule rule, UserProfile profile, LocalDate evaluationDate) {
        List<NodeEvaluation> children = rule.children().stream()
                .map(child -> evaluateNode(child, profile, evaluationDate))
                .toList();

        EvaluationStatus status = children.stream().anyMatch(it -> it.status() == EvaluationStatus.PASS)
                ? EvaluationStatus.PASS
                : children.stream().anyMatch(it -> it.status() == EvaluationStatus.UNKNOWN)
                ? EvaluationStatus.UNKNOWN
                : EvaluationStatus.FAIL;

        List<ConditionEvaluation> conditions = children.stream()
                .flatMap(it -> it.conditions().stream())
                .toList();
        NodeEvaluation selectedBranch = selectRelevantOrBranch(children, status);
        List<ConditionEvaluation> blockers = status == EvaluationStatus.FAIL
                ? selectedBranch.blockers()
                : List.of();
        return new NodeEvaluation(status, conditions, blockers, selectedBranch.relevantConditions());
    }

    private NodeEvaluation selectRelevantOrBranch(
            List<NodeEvaluation> children,
            EvaluationStatus status
    ) {
        Comparator<NodeEvaluation> comparator = Comparator
                .comparingInt((NodeEvaluation it) -> it.blockers().size())
                .thenComparingLong(this::unknownConditionCount)
                .thenComparingInt(it -> it.relevantConditions().size());

        return children.stream()
                .filter(it -> it.status() == status)
                .min(comparator)
                .orElseThrow(() -> new IllegalStateException("OR rule has no branch matching its result"));
    }

    private long unknownConditionCount(NodeEvaluation evaluation) {
        return evaluation.relevantConditions().stream()
                .filter(it -> it.status() == EvaluationStatus.UNKNOWN)
                .count();
    }

    private boolean matches(ConditionRule rule, Object actual) {
        return switch (rule.operator()) {
            case EQ -> compare(actual, rule.expectedValues().getFirst()) == 0;
            case NE -> compare(actual, rule.expectedValues().getFirst()) != 0;
            case GT -> compare(actual, rule.expectedValues().getFirst()) > 0;
            case GTE -> compare(actual, rule.expectedValues().getFirst()) >= 0;
            case LT -> compare(actual, rule.expectedValues().getFirst()) < 0;
            case LTE -> compare(actual, rule.expectedValues().getFirst()) <= 0;
            case BETWEEN -> compare(actual, rule.expectedValues().get(0)) >= 0
                    && compare(actual, rule.expectedValues().get(1)) <= 0;
            case IN -> rule.expectedValues().stream().anyMatch(expected -> compare(actual, expected) == 0);
            case NOT_IN -> rule.expectedValues().stream().noneMatch(expected -> compare(actual, expected) == 0);
            case CONTAINS -> normalize(actual).toUpperCase(Locale.ROOT)
                    .contains(rule.expectedValues().getFirst().toUpperCase(Locale.ROOT));
            case REQUIRED -> true;
        };
    }

    private int compare(Object actual, String expected) {
        if (actual instanceof Number) {
            return new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected));
        }
        return normalize(actual).compareToIgnoreCase(expected);
    }

    private String normalize(Object value) {
        return value instanceof Enum<?> enumValue ? enumValue.name() : value.toString();
    }

    private String messageFor(EvaluationStatus status) {
        return switch (status) {
            case PASS -> "현재 입력값이 조건을 충족합니다.";
            case FAIL -> "현재 입력값이 조건을 충족하지 않습니다.";
            case UNKNOWN -> "판정에 필요한 정보가 없거나 자동 판정할 수 없습니다.";
        };
    }

    private record NodeEvaluation(
            EvaluationStatus status,
            List<ConditionEvaluation> conditions,
            List<ConditionEvaluation> blockers,
            List<ConditionEvaluation> relevantConditions
    ) {
        private NodeEvaluation {
            conditions = List.copyOf(new ArrayList<>(conditions));
            blockers = List.copyOf(new ArrayList<>(blockers));
            relevantConditions = List.copyOf(new ArrayList<>(relevantConditions));
        }
    }
}

