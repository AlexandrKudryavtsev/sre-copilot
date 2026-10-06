package com.srecopilot.metric;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

public final class MetricDtos {

    private MetricDtos() {
    }

    public record MetricSnapshotResponse(
            Long id, Long projectId, Instant timestamp,
            @Schema(description = "Mean request latency in milliseconds over the collection interval") BigDecimal latency,
            @Schema(description = "Requests per second over the collection interval") BigDecimal traffic,
            @Schema(description = "Percentage of HTTP 5xx responses over the collection interval") BigDecimal errors,
            @Schema(description = "CPU usage as a percentage") BigDecimal saturation) {
    }
}
