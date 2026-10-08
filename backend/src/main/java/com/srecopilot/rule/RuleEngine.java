package com.srecopilot.rule;

import com.srecopilot.incident.Incident;
import com.srecopilot.incident.IncidentRepository;
import com.srecopilot.incident.IncidentStatus;
import com.srecopilot.metric.MetricSnapshot;
import com.srecopilot.metric.MetricType;
import com.srecopilot.monitoring.MonitoringProperties;
import com.srecopilot.project.ProjectRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleEngine {

    private final MonitoringRuleRepository rules;
    private final MonitoringRuleStateRepository states;
    private final IncidentRepository incidents;
    private final ProjectRepository projects;
    private final MonitoringProperties properties;
    private final Clock clock;

    public RuleEngine(MonitoringRuleRepository rules, MonitoringRuleStateRepository states,
            IncidentRepository incidents, ProjectRepository projects, MonitoringProperties properties, Clock clock) {
        this.rules = rules;
        this.states = states;
        this.incidents = incidents;
        this.projects = projects;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void evaluate(MetricSnapshot snapshot) {
        var project = projects.findByIdForUpdate(snapshot.getProjectId()).orElse(null);
        if (project == null || !project.isMonitoringEnabled()) {
            return;
        }
        // All mutations lock project -> rule state. This also serializes API edits with evaluation.
        for (MonitoringRule rule : rules.findAllByProjectIdAndEnabledTrueOrderByIdAsc(snapshot.getProjectId())) {
            MonitoringRuleState state = states.findForUpdate(rule.getId())
                    .orElseGet(() -> states.saveAndFlush(new MonitoringRuleState(rule.getId())));
            if (snapshot.getTimestamp().isBefore(rule.getUpdatedAt())
                    || !state.isDue(snapshot.getTimestamp(), rule.getCheckIntervalSeconds())) {
                continue;
            }
            Incident active = incidents.findByRuleIdAndStatus(rule.getId(), IncidentStatus.OPEN).orElse(null);
            if (active != null) {
                state.opened(active.getId());
            } else if (state.getActiveIncidentId() != null) {
                state.resolved();
            }
            // Editing a rule does not rewrite the condition of an incident that is already open.
            MetricType metric = active == null ? rule.getMetric() : active.getRuleMetric();
            RuleOperator operator = active == null ? rule.getOperator() : active.getRuleOperator();
            var threshold = active == null ? rule.getThreshold() : active.getRuleThreshold();
            state.checkedAt(snapshot.getTimestamp());
            if (snapshot.getTraffic().signum() == 0 && (metric == MetricType.LATENCY || metric == MetricType.ERRORS)) {
                state.resetProgress();
                continue; // These zero values represent no observations, not a healthy measurement.
            }
            if (operator.matches(snapshot.value(metric), threshold)) {
                state.violated(snapshot.getTimestamp());
                if (active == null && state.getConsecutiveViolations() >= rule.getConsecutiveViolations()) {
                    Incident incident = incidents.save(new Incident(rule, snapshot, clock.instant()));
                    state.opened(incident.getId());
                }
            } else {
                state.healthy();
                if (active != null && state.getConsecutiveRecoveries() >= properties.recoveryChecks()) {
                    active.resolve(snapshot.getTimestamp());
                    state.resolved();
                }
            }
        }
    }

    @Transactional
    public void resetProgress(long projectId) {
        if (projects.findByIdForUpdate(projectId).isPresent()) {
            states.findAllForProjectForUpdate(projectId).forEach(MonitoringRuleState::resetProgress);
        }
    }
}
