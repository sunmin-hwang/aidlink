package io.aidlink.eligibility.domain;

import java.util.List;

public record OrRule(List<RuleNode> children) implements RuleNode {
    public OrRule {
        if (children == null || children.isEmpty()) {
            throw new IllegalArgumentException("OR rule requires at least one child");
        }
        children = List.copyOf(children);
    }

    @Override
    public RuleType type() {
        return RuleType.OR;
    }
}

