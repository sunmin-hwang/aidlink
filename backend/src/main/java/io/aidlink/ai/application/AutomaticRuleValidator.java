package io.aidlink.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.aidlink.ai.domain.PolicyQualificationText;
import io.aidlink.ai.domain.PolicyRuleValidationResult;
import io.aidlink.ai.domain.RuleValidationBreakdown;
import io.aidlink.ai.domain.RuleValidationIssue;
import io.aidlink.ai.domain.RuleValidationSeverity;
import io.aidlink.ai.domain.RuleValidationStatus;
import io.aidlink.ai.domain.StoredPolicyRuleCandidate;
import io.aidlink.eligibility.domain.ConditionField;
import io.aidlink.eligibility.domain.Operator;
import io.aidlink.policy.infrastructure.jpa.RuleJsonMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AutomaticRuleValidator {

    private static final Set<String> COMPOSITE_FIELDS = Set.of("type", "children");
    private static final Set<String> CONDITION_FIELDS = Set.of(
            "type", "field", "operator", "expectedValues", "description", "sourceText"
    );
    private static final Set<ConditionField> NUMERIC_FIELDS = Set.of(
            ConditionField.AGE,
            ConditionField.PERSONAL_INCOME,
            ConditionField.HOUSEHOLD_INCOME,
            ConditionField.MEDIAN_INCOME_RATIO,
            ConditionField.WEEKLY_WORK_HOURS,
            ConditionField.HOUSEHOLD_SIZE,
            ConditionField.ASSET,
            ConditionField.CHILDREN_COUNT,
            ConditionField.BUSINESS_AGE,
            ConditionField.BUSINESS_REVENUE
    );
    private static final Set<Operator> ORDER_OPERATORS = Set.of(
            Operator.GT, Operator.GTE, Operator.LT, Operator.LTE, Operator.BETWEEN
    );

    private final RuleJsonMapper ruleJsonMapper;

    public AutomaticRuleValidator(RuleJsonMapper ruleJsonMapper) {
        this.ruleJsonMapper = ruleJsonMapper;
    }

    public PolicyRuleValidationResult validate(
            StoredPolicyRuleCandidate stored,
            PolicyQualificationText sourceText
    ) {
        List<RuleValidationIssue> issues = new ArrayList<>();
        ValidationCounters counters = new ValidationCounters();
        validateNode(stored.candidate().rule(), "$", sourceTextValue(sourceText), issues, counters);

        boolean domainMappingValid = true;
        try {
            ruleJsonMapper.fromJson(stored.candidate().rule());
        } catch (RuntimeException exception) {
            domainMappingValid = false;
            issues.add(error("RULE_MAPPING_FAILED", "$", exception.getMessage()));
        }

        int schemaValidity = issues.stream().anyMatch(issue ->
                issue.severity() == RuleValidationSeverity.ERROR
                        && (issue.code().startsWith("SCHEMA_")
                        || issue.code().equals("RULE_MAPPING_FAILED")))
                ? 0 : 20;
        int supportedFieldRatio = ratioScore(counters.supportedFields, counters.conditions, 20);
        int sourceCoverage = ratioScore(counters.sourceMatches, counters.conditions, 30);
        int operatorValidity = ratioScore(counters.validOperators, counters.conditions, 20);
        int ambiguityScore = Math.max(0, 10 - stored.candidate().ambiguities().size() * 5);
        int unsupportedPenalty = counters.manualReviewConditions * 15;

        stored.candidate().ambiguities().forEach(ambiguity -> issues.add(new RuleValidationIssue(
                "AMBIGUOUS_SOURCE_CONDITION",
                RuleValidationSeverity.WARNING,
                "$",
                ambiguity
        )));
        RuleValidationBreakdown breakdown = new RuleValidationBreakdown(
                schemaValidity,
                supportedFieldRatio,
                sourceCoverage,
                operatorValidity,
                ambiguityScore,
                unsupportedPenalty
        );

        boolean requiresReview = !domainMappingValid
                || counters.conditions == 0
                || counters.supportedFields != counters.conditions
                || counters.sourceMatches != counters.conditions
                || counters.validOperators != counters.conditions
                || counters.manualReviewConditions > 0
                || !stored.candidate().ambiguities().isEmpty()
                || issues.stream().anyMatch(issue -> issue.severity() == RuleValidationSeverity.ERROR)
                || breakdown.total() < 85;

        return new PolicyRuleValidationResult(
                stored.id(),
                stored.policyId(),
                requiresReview
                        ? RuleValidationStatus.REVIEW_REQUIRED
                        : RuleValidationStatus.AUTO_VALIDATED,
                breakdown,
                issues,
                stored.candidate().rule()
        );
    }

    private void validateNode(
            JsonNode node,
            String path,
            String normalizedSource,
            List<RuleValidationIssue> issues,
            ValidationCounters counters
    ) {
        if (node == null || !node.isObject()) {
            issues.add(error("SCHEMA_NODE_NOT_OBJECT", path, "Rule node must be an object"));
            return;
        }
        JsonNode typeNode = node.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            issues.add(error("SCHEMA_TYPE_MISSING", path, "Rule node type is missing"));
            return;
        }
        switch (typeNode.asText()) {
            case "AND", "OR" -> validateComposite(node, path, normalizedSource, issues, counters);
            case "CONDITION" -> validateCondition(node, path, normalizedSource, issues, counters);
            default -> issues.add(error(
                    "SCHEMA_TYPE_UNSUPPORTED", path, "Unsupported rule type: " + typeNode.asText()));
        }
    }

    private void validateComposite(
            JsonNode node,
            String path,
            String normalizedSource,
            List<RuleValidationIssue> issues,
            ValidationCounters counters
    ) {
        checkAdditionalProperties(node, COMPOSITE_FIELDS, path, issues);
        JsonNode children = node.get("children");
        if (children == null || !children.isArray() || children.isEmpty()) {
            issues.add(error("SCHEMA_CHILDREN_INVALID", path, "AND/OR requires non-empty children"));
            return;
        }
        for (int index = 0; index < children.size(); index++) {
            validateNode(children.get(index), path + ".children[" + index + "]",
                    normalizedSource, issues, counters);
        }
    }

    private void validateCondition(
            JsonNode node,
            String path,
            String normalizedSource,
            List<RuleValidationIssue> issues,
            ValidationCounters counters
    ) {
        counters.conditions++;
        checkAdditionalProperties(node, CONDITION_FIELDS, path, issues);

        ConditionField field = enumValue(node, "field", ConditionField.class, path, issues);
        Operator operator = enumValue(node, "operator", Operator.class, path, issues);
        if (field != null) {
            counters.supportedFields++;
            if (field == ConditionField.MANUAL_REVIEW) {
                counters.manualReviewConditions++;
                issues.add(new RuleValidationIssue(
                        "MANUAL_REVIEW_REQUIRED",
                        RuleValidationSeverity.WARNING,
                        path,
                        "기관의 개별 심사 또는 자동 구조화가 불가능한 조건입니다."
                ));
            }
        }

        JsonNode values = node.get("expectedValues");
        boolean valuesValid = values != null && values.isArray()
                && java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .allMatch(JsonNode::isTextual);
        if (!valuesValid) {
            issues.add(error("SCHEMA_VALUES_INVALID", path, "expectedValues must be a string array"));
        }

        if (field != null && operator != null && valuesValid
                && validateOperatorAndValues(field, operator, values, path, issues)) {
            counters.validOperators++;
        }

        JsonNode description = node.get("description");
        JsonNode sourceText = node.get("sourceText");
        if (description == null || !description.isTextual() || description.asText().isBlank()) {
            issues.add(error("SCHEMA_DESCRIPTION_MISSING", path, "Condition description is missing"));
        }
        if (sourceText == null || !sourceText.isTextual() || sourceText.asText().isBlank()) {
            issues.add(error("SOURCE_TEXT_MISSING", path, "Condition sourceText is missing"));
        } else if (normalizedSource.contains(normalizeText(sourceText.asText()))) {
            counters.sourceMatches++;
        } else {
            issues.add(error(
                    "SOURCE_TEXT_NOT_FOUND",
                    path,
                    "Condition evidence was not found in the official source text"
            ));
        }
    }

    private boolean validateOperatorAndValues(
            ConditionField field,
            Operator operator,
            JsonNode values,
            String path,
            List<RuleValidationIssue> issues
    ) {
        int valueCount = values.size();
        int expectedCount = operator == Operator.BETWEEN ? 2 : operator == Operator.REQUIRED ? 0 : -1;
        if ((expectedCount >= 0 && valueCount != expectedCount)
                || (expectedCount == -1 && valueCount < 1)) {
            issues.add(error(
                    "OPERATOR_VALUE_COUNT_INVALID",
                    path,
                    operator + " has an invalid number of expected values"
            ));
            return false;
        }
        if (field == ConditionField.MANUAL_REVIEW) {
            if (operator != Operator.REQUIRED) {
                issues.add(error(
                        "MANUAL_REVIEW_OPERATOR_INVALID", path,
                        "MANUAL_REVIEW must use REQUIRED"));
                return false;
            }
            return true;
        }
        if (operator == Operator.REQUIRED) {
            issues.add(error(
                    "REQUIRED_OPERATOR_INVALID", path,
                    "REQUIRED is reserved for MANUAL_REVIEW"));
            return false;
        }
        if (ORDER_OPERATORS.contains(operator) && !NUMERIC_FIELDS.contains(field)) {
            issues.add(error(
                    "ORDER_OPERATOR_ON_CATEGORICAL_FIELD", path,
                    "Order comparison cannot be used with " + field));
            return false;
        }
        if (NUMERIC_FIELDS.contains(field)) {
            for (JsonNode value : values) {
                try {
                    new BigDecimal(value.asText());
                } catch (NumberFormatException exception) {
                    issues.add(error(
                            "NUMERIC_VALUE_INVALID", path,
                            "Numeric fields require unit-normalized numeric values"));
                    return false;
                }
            }
        }
        return true;
    }

    private <E extends Enum<E>> E enumValue(
            JsonNode node,
            String fieldName,
            Class<E> enumType,
            String path,
            List<RuleValidationIssue> issues
    ) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual()) {
            issues.add(error("SCHEMA_" + fieldName.toUpperCase(Locale.ROOT) + "_MISSING",
                    path, fieldName + " is missing"));
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.asText());
        } catch (IllegalArgumentException exception) {
            issues.add(error("UNSUPPORTED_" + fieldName.toUpperCase(Locale.ROOT),
                    path, "Unsupported " + fieldName + ": " + value.asText()));
            return null;
        }
    }

    private void checkAdditionalProperties(
            JsonNode node,
            Set<String> allowed,
            String path,
            List<RuleValidationIssue> issues
    ) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        actual.removeAll(allowed);
        if (!actual.isEmpty()) {
            issues.add(error(
                    "SCHEMA_ADDITIONAL_PROPERTIES", path,
                    "Unexpected rule properties: " + actual));
        }
    }

    private int ratioScore(int valid, int total, int maximum) {
        return total == 0 ? 0 : (int) Math.round((double) valid / total * maximum);
    }

    private String sourceTextValue(PolicyQualificationText sourceText) {
        return normalizeText(String.join(" ",
                nullToEmpty(sourceText.policyName()),
                nullToEmpty(sourceText.targetDescription()),
                nullToEmpty(sourceText.selectionCriteria()),
                nullToEmpty(sourceText.exclusionConditions())
        ));
    }

    private String normalizeText(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private RuleValidationIssue error(String code, String path, String message) {
        return new RuleValidationIssue(code, RuleValidationSeverity.ERROR, path, message);
    }

    private static class ValidationCounters {
        private int conditions;
        private int supportedFields;
        private int sourceMatches;
        private int validOperators;
        private int manualReviewConditions;
    }
}
