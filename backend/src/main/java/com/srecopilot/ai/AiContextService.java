package com.srecopilot.ai;

import com.srecopilot.incident.IncidentRepository;
import com.srecopilot.metric.MetricSnapshot;
import com.srecopilot.metric.MetricSnapshotRepository;
import com.srecopilot.metric.MetricType;
import com.srecopilot.project.ProjectRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiContextService {
    private final IncidentRepository incidents;
    private final ProjectRepository projects;
    private final MetricSnapshotRepository snapshots;
    private final AiProperties properties;
    public AiContextService(IncidentRepository incidents, ProjectRepository projects,
            MetricSnapshotRepository snapshots, AiProperties properties) {
        this.incidents = incidents; this.projects = projects; this.snapshots = snapshots; this.properties = properties;
    }
    public record Signals(BigDecimal latencyMs, BigDecimal trafficRps, BigDecimal errorsPercent, BigDecimal saturationPercent) { }
    public record HistoryPoint(Instant timestamp, Signals metrics) { }
    public record Context(long incidentId, String project, String severity, Instant detectedAt,
            Map<String, Object> triggeredRule, Signals metricsAtDetection, List<HistoryPoint> metricHistory,
            int historyPointsAvailable, boolean historySampled) { }

    @Transactional(readOnly = true)
    public Context build(long incidentId) {
        var incident = incidents.findById(incidentId).orElseThrow().toResponse();
        var project = projects.findById(incident.projectId()).orElseThrow();
        var history = snapshots.findAllByProjectIdAndTimestampBetweenOrderByTimestampAscIdAsc(
                incident.projectId(), incident.startedAt().minus(properties.historyWindow()), incident.startedAt());
        // Even sampling retains both ends of the actual pre-detection window; never synthesize values.
        int count = Math.min(history.size(), properties.maxHistoryPoints());
        var points = new ArrayList<HistoryPoint>(count);
        for (int n = 0; n < count; n++) {
            int index = count == 1 ? history.size() - 1 : (int) ((long) n * (history.size() - 1) / (count - 1));
            MetricSnapshot snapshot = history.get(index);
            points.add(new HistoryPoint(snapshot.getTimestamp(), new Signals(snapshot.value(MetricType.LATENCY),
                    snapshot.value(MetricType.TRAFFIC), snapshot.value(MetricType.ERRORS), snapshot.value(MetricType.SATURATION))));
        }
        String operator = switch (incident.ruleOperator()) {
            case GREATER_THAN -> ">"; case GREATER_OR_EQUAL -> ">="; case LESS_THAN -> "<"; case LESS_OR_EQUAL -> "<=";
        };
        return new Context(incident.id(), project.getName(), incident.severity().name(), incident.startedAt(),
                Map.of("metric", incident.ruleMetric().name(), "operator", operator, "threshold", incident.ruleThreshold(),
                        "consecutiveViolations", incident.requiredConsecutiveViolations()),
                new Signals(incident.latencyAtDetection(), incident.trafficAtDetection(), incident.errorsAtDetection(),
                        incident.saturationAtDetection()), List.copyOf(points), history.size(), history.size() > count);
    }
}
