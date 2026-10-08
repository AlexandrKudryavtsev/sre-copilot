package com.srecopilot.rule;

import com.srecopilot.incident.Severity;
import com.srecopilot.metric.MetricType;
import com.srecopilot.rule.RuleDtos.RuleRequest;
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
@Table(name = "monitoring_rules")
public class MonitoringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MetricType metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleOperator operator;

    @Column(nullable = false)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Severity severity;

    @Column(name = "check_interval_seconds", nullable = false)
    private int checkIntervalSeconds;

    @Column(nullable = false)
    private int consecutiveViolations;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected MonitoringRule() {
    }

    public MonitoringRule(long projectId, RuleRequest request, Instant now) {
        this.projectId = projectId;
        this.createdAt = now;
        update(request, now);
    }

    public void update(RuleRequest request, Instant now) {
        name = request.name().strip();
        metric = request.metric();
        operator = request.operator();
        threshold = request.threshold();
        severity = request.severity();
        checkIntervalSeconds = request.checkIntervalSeconds();
        consecutiveViolations = request.consecutiveViolations();
        enabled = request.enabled();
        updatedAt = now;
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public RuleDtos.RuleResponse toResponse(MonitoringRuleState runtime) {
        return new RuleDtos.RuleResponse(id, projectId, name, metric, operator, threshold, severity,
                checkIntervalSeconds, consecutiveViolations, enabled, createdAt, updatedAt, runtime.toResponse());
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }
    public MetricType getMetric() { return metric; }
    public RuleOperator getOperator() { return operator; }
    public BigDecimal getThreshold() { return threshold; }
    public Severity getSeverity() { return severity; }
    public int getCheckIntervalSeconds() { return checkIntervalSeconds; }
    public int getConsecutiveViolations() { return consecutiveViolations; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
