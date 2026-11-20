package io.aidlink.eligibility.domain;

public sealed interface RuleNode permits AndRule, OrRule, ConditionRule {
    RuleType type();
}

