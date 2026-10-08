package com.srecopilot.rule;

import java.math.BigDecimal;

public enum RuleOperator {
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL;

    public boolean matches(BigDecimal value, BigDecimal threshold) {
        int comparison = value.compareTo(threshold);
        return switch (this) {
            case GREATER_THAN -> comparison > 0;
            case GREATER_OR_EQUAL -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_OR_EQUAL -> comparison <= 0;
        };
    }
}
