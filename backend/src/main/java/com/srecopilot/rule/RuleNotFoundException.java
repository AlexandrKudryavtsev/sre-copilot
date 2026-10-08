package com.srecopilot.rule;

public class RuleNotFoundException extends RuntimeException {
    public RuleNotFoundException(long id) {
        super("Monitoring rule '%d' was not found.".formatted(id));
    }
}
