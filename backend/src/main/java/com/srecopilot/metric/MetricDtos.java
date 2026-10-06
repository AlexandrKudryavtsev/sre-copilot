package com.srecopilot.metric;

import java.math.BigDecimal;
import java.time.Instant;

public final class MetricDtos {

    private MetricDtos() {
    }

    public record MetricSnapshotResponse(
            Long id, Long projectId, Instant timestamp,
            BigDecimal latency, BigDecimal traffic,
            BigDecimal errors, BigDecimal saturation) {
    }
}
