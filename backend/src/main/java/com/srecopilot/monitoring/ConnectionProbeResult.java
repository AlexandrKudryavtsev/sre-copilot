package com.srecopilot.monitoring;

public record ConnectionProbeResult(boolean connected, String message) {

    public static ConnectionProbeResult available() {
        return new ConnectionProbeResult(true, "Endpoint is available and exposes Prometheus metrics.");
    }

    public static ConnectionProbeResult unavailable(String message) {
        return new ConnectionProbeResult(false, message);
    }
}
