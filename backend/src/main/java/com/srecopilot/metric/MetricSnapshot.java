package com.srecopilot.metric;

import com.srecopilot.monitoring.GoldenSignals;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "metric_snapshots")
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false)
    private BigDecimal latency;

    @Column(nullable = false, updatable = false)
    private BigDecimal traffic;

    @Column(nullable = false, updatable = false)
    private BigDecimal errors;

    @Column(nullable = false, updatable = false)
    private BigDecimal saturation;

    protected MetricSnapshot() {
    }

    public MetricSnapshot(long projectId, Instant timestamp, GoldenSignals signals) {
        this.projectId = projectId;
        this.timestamp = timestamp;
        this.latency = signals.latency();
        this.traffic = signals.traffic();
        this.errors = signals.errors();
        this.saturation = signals.saturation();
    }

    public MetricDtos.MetricSnapshotResponse toResponse() {
        return new MetricDtos.MetricSnapshotResponse(id, projectId, timestamp, latency, traffic, errors, saturation);
    }
}
