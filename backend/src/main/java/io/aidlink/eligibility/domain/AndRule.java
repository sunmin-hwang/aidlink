package io.aidlink.eligibility.domain;

import java.util.List;

public record AndRule(List<RuleNode> children) implements RuleNode {
    public AndRule {
        if (children == null || children.isEmpty()) {
            throw new IllegalArgumentException("AND rule requires at least one child");
        }
        children = List.copyOf(children);
    }

    @Override
    public RuleType type() {
        return RuleType.AND;
    }
}

