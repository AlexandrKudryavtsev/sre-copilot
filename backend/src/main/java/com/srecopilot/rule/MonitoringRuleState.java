package com.srecopilot.rule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "monitoring_rule_states")
public class MonitoringRuleState {

    @Id
    private Long ruleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RuleState state = RuleState.HEALTHY;

    @Column(nullable = false)
    private int consecutiveViolations;

    @Column(nullable = false)
    private int consecutiveRecoveries;

    private Long activeIncidentId;
    private Instant lastCheckedAt;
    private Instant lastViolationAt;

    protected MonitoringRuleState() {
    }

    public MonitoringRuleState(long ruleId) {
        this.ruleId = ruleId;
    }

    public boolean isDue(Instant timestamp, int intervalSeconds) {
        return lastCheckedAt == null || !timestamp.isBefore(lastCheckedAt.plusSeconds(intervalSeconds));
    }

    public void checkedAt(Instant timestamp) {
        lastCheckedAt = timestamp;
    }

    public void violated(Instant timestamp) {
        lastViolationAt = timestamp;
        consecutiveRecoveries = 0;
        if (activeIncidentId == null) {
            consecutiveViolations++;
            state = RuleState.VIOLATING;
        }
    }

    public void healthy() {
        consecutiveViolations = 0;
        if (activeIncidentId != null) {
            consecutiveRecoveries++;
        } else {
            resetProgress();
        }
    }

    public void opened(long incidentId) {
        activeIncidentId = incidentId;
        state = RuleState.INCIDENT_OPEN;
    }

    public void resolved() {
        activeIncidentId = null;
        resetProgress();
    }

    public void resetProgress() {
        consecutiveViolations = 0;
        consecutiveRecoveries = 0;
        state = activeIncidentId == null ? RuleState.HEALTHY : RuleState.INCIDENT_OPEN;
        // Preserve lastCheckedAt: resetting counters must not make an old snapshot eligible again.
    }

    public RuleDtos.RuleStateResponse toResponse() {
        return new RuleDtos.RuleStateResponse(state, consecutiveViolations, consecutiveRecoveries,
                activeIncidentId, lastCheckedAt, lastViolationAt);
    }

    public Long getRuleId() { return ruleId; }
    public int getConsecutiveViolations() { return consecutiveViolations; }
    public int getConsecutiveRecoveries() { return consecutiveRecoveries; }
    public Long getActiveIncidentId() { return activeIncidentId; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
}
