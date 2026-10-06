package com.srecopilot.incident;

import com.srecopilot.metric.MetricType;
import com.srecopilot.rule.RuleOperator;
import java.math.BigDecimal;
import java.time.Instant;

public final class IncidentDtos {

    private IncidentDtos() {
    }

    public record IncidentResponse(
            Long id, Long projectId, Long ruleId, String title,
            IncidentStatus status, Severity severity,
            Instant startedAt, Instant resolvedAt,
            MetricType triggerMetric, BigDecimal triggerValue,
            MetricType ruleMetric, RuleOperator ruleOperator,
            BigDecimal ruleThreshold, int requiredConsecutiveViolations,
            BigDecimal latencyAtDetection, BigDecimal trafficAtDetection,
            BigDecimal errorsAtDetection, BigDecimal saturationAtDetection,
            Instant createdAt, Instant updatedAt) {
    }
}
