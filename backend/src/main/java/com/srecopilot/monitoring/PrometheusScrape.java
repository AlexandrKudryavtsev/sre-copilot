package com.srecopilot.monitoring;

import java.time.Instant;
import java.util.List;

public record PrometheusScrape(Instant timestamp, List<PrometheusSample> samples) {

    public PrometheusScrape {
        samples = List.copyOf(samples);
    }
}
