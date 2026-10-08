package com.srecopilot.ai;

/** Only fixed, sanitized messages: never retain upstream bodies, credentials or request context. */
public class AiAnalysisException extends RuntimeException {
    public AiAnalysisException(String message) { super(message); }
}
