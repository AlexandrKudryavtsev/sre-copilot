package com.srecopilot.incident;

import com.srecopilot.metric.MetricSnapshot;
import com.srecopilot.metric.MetricType;
import com.srecopilot.rule.MonitoringRule;
import com.srecopilot.rule.RuleOperator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long projectId;

    @Column(updatable = false)
    private Long ruleId;

    @Column(nullable = false, updatable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private Severity severity;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private MetricType triggerMetric;

    @Column(nullable = false, updatable = false)
    private BigDecimal triggerValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private MetricType ruleMetric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private RuleOperator ruleOperator;

    @Column(nullable = false, updatable = false)
    private BigDecimal ruleThreshold;

    @Column(nullable = false, updatable = false)
    private int requiredConsecutiveViolations;

    @Column(nullable = false, updatable = false)
    private BigDecimal latencyAtDetection;

    @Column(nullable = false, updatable = false)
    private BigDecimal trafficAtDetection;

    @Column(nullable = false, updatable = false)
    private BigDecimal errorsAtDetection;

    @Column(nullable = false, updatable = false)
    private BigDecimal saturationAtDetection;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Incident() {
    }

    public Incident(MonitoringRule rule, MetricSnapshot snapshot, Instant now) {
        projectId = rule.getProjectId();
        ruleId = rule.getId();
        title = rule.getName();
        status = IncidentStatus.OPEN;
        severity = rule.getSeverity();
        startedAt = snapshot.getTimestamp();
        triggerMetric = rule.getMetric();
        triggerValue = snapshot.value(triggerMetric);
        ruleMetric = rule.getMetric();
        ruleOperator = rule.getOperator();
        ruleThreshold = rule.getThreshold();
        requiredConsecutiveViolations = rule.getConsecutiveViolations();
        latencyAtDetection = snapshot.value(MetricType.LATENCY);
        trafficAtDetection = snapshot.value(MetricType.TRAFFIC);
        errorsAtDetection = snapshot.value(MetricType.ERRORS);
        saturationAtDetection = snapshot.value(MetricType.SATURATION);
        createdAt = now;
        updatedAt = now;
    }

    public void resolve(Instant timestamp) {
        status = IncidentStatus.RESOLVED;
        resolvedAt = timestamp;
        updatedAt = timestamp;
    }

    public IncidentDtos.IncidentResponse toResponse() {
        return new IncidentDtos.IncidentResponse(id, projectId, ruleId, title, status, severity,
                startedAt, resolvedAt, triggerMetric, triggerValue, ruleMetric, ruleOperator,
                ruleThreshold, requiredConsecutiveViolations, latencyAtDetection, trafficAtDetection,
                errorsAtDetection, saturationAtDetection, createdAt, updatedAt);
    }

    public Long getId() { return id; }
    public MetricType getRuleMetric() { return ruleMetric; }
    public RuleOperator getRuleOperator() { return ruleOperator; }
    public BigDecimal getRuleThreshold() { return ruleThreshold; }
}
