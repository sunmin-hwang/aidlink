package io.aidlink.policy.application;

import io.aidlink.eligibility.domain.AndRule;
import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.eligibility.domain.ConditionRule;
import io.aidlink.eligibility.domain.Operator;
import io.aidlink.eligibility.domain.OrRule;
import io.aidlink.eligibility.domain.RuleNode;

import java.math.BigDecimal;
import java.util.Locale;

public final class RuleConstraintMatcher {

    private RuleConstraintMatcher() {
    }

    public static boolean matches(RuleNode rule, ConditionField field, Object actual) {
        if (rule == null || actual == null) {
            return true;
        }
        return evaluate(rule, field, actual).matches();
    }

    private static Match evaluate(RuleNode node, ConditionField field, Object actual) {
        if (node instanceof ConditionRule condition) {
            if (condition.field() != field) {
                return Match.unconstrained();
            }
            return new Match(true, matchesCondition(condition, actual));
        }
        if (node instanceof AndRule andRule) {
            boolean constrained = false;
            boolean matches = true;
            for (RuleNode child : andRule.children()) {
                Match result = evaluate(child, field, actual);
                constrained |= result.constrained();
                matches &= result.matches();
            }
            return new Match(constrained, matches);
        }
        if (node instanceof OrRule orRule) {
            boolean anyMatches = false;
            for (RuleNode child : orRule.children()) {
                Match result = evaluate(child, field, actual);
                if (!result.constrained()) {
                    return Match.unconstrained();
                }
                anyMatches |= result.matches();
            }
            return new Match(true, anyMatches);
        }
        throw new IllegalArgumentException("Unsupported rule node: " + node.getClass().getName());
    }

    private static boolean matchesCondition(ConditionRule rule, Object actual) {
        return switch (rule.operator()) {
            case EQ -> compare(actual, rule.expectedValues().getFirst()) == 0;
            case NE -> compare(actual, rule.expectedValues().getFirst()) != 0;
            case GT -> compare(actual, rule.expectedValues().getFirst()) > 0;
            case GTE -> compare(actual, rule.expectedValues().getFirst()) >= 0;
            case LT -> compare(actual, rule.expectedValues().getFirst()) < 0;
            case LTE -> compare(actual, rule.expectedValues().getFirst()) <= 0;
            case BETWEEN -> compare(actual, rule.expectedValues().get(0)) >= 0
                    && compare(actual, rule.expectedValues().get(1)) <= 0;
            case IN -> rule.expectedValues().stream().anyMatch(value -> compare(actual, value) == 0);
            case NOT_IN -> rule.expectedValues().stream().noneMatch(value -> compare(actual, value) == 0);
            case CONTAINS -> normalize(actual).contains(
                    rule.expectedValues().getFirst().toUpperCase(Locale.ROOT));
            case REQUIRED -> true;
        };
    }

    private static int compare(Object actual, String expected) {
        if (actual instanceof Number) {
            return new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected));
        }
        return normalize(actual).compareTo(expected.toUpperCase(Locale.ROOT));
    }

    private static String normalize(Object value) {
        return value instanceof Enum<?> enumValue
                ? enumValue.name()
                : value.toString().toUpperCase(Locale.ROOT);
    }

    private record Match(boolean constrained, boolean matches) {
        private static Match unconstrained() {
            return new Match(false, true);
        }
    }
}
