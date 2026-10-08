package com.srecopilot.rule;

public class RuleHasOpenIncidentException extends RuntimeException {
    public RuleHasOpenIncidentException(long id) {
        super("Monitoring rule '%d' has an open incident and cannot be deleted before recovery.".formatted(id));
    }
}
