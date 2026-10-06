package com.srecopilot.monitoring;

import java.util.Map;

public record PrometheusSample(String name, Map<String, String> labels, String value) {

    public PrometheusSample {
        labels = Map.copyOf(labels);
    }
}
