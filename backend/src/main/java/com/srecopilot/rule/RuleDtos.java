package com.srecopilot.rule;

import com.srecopilot.incident.Severity;
import com.srecopilot.metric.MetricType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class RuleDtos {

    private RuleDtos() {
    }

    public record RuleRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull MetricType metric,
            @NotNull RuleOperator operator,
            @NotNull @PositiveOrZero BigDecimal threshold,
            @NotNull Severity severity,
            @NotNull @Min(1) Integer checkIntervalSeconds,
            @NotNull @Min(1) Integer consecutiveViolations,
            @NotNull Boolean enabled) {
    }

    public record RuleResponse(
            Long id, Long projectId, String name, MetricType metric,
            RuleOperator operator, BigDecimal threshold, Severity severity,
            int checkIntervalSeconds, int consecutiveViolations, boolean enabled,
            Instant createdAt, Instant updatedAt, RuleStateResponse runtimeState) {
    }

    public record RuleStateResponse(
            RuleState state, int consecutiveViolations, int consecutiveRecoveries,
            Long activeIncidentId, Instant lastCheckedAt, Instant lastViolationAt) {
    }
}
