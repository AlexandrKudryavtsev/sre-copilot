package com.srecopilot.incident;

public class IncidentNotFoundException extends RuntimeException {
    public IncidentNotFoundException(long id) {
        super("Incident '%d' was not found.".formatted(id));
    }
}
