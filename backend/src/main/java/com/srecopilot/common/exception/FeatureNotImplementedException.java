package com.srecopilot.common.exception;

public class FeatureNotImplementedException extends RuntimeException {

    public FeatureNotImplementedException(String operation) {
        super("Operation '%s' is not implemented yet.".formatted(operation));
    }
}
