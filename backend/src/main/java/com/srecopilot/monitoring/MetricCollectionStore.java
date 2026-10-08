package com.srecopilot.monitoring;

import com.srecopilot.metric.MetricSnapshot;
import com.srecopilot.metric.MetricSnapshotRepository;
import com.srecopilot.project.ConnectionStatus;
import com.srecopilot.project.ProjectRepository;
import com.srecopilot.rule.RuleEngine;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricCollectionStore {

    private final ProjectRepository projects;
    private final MetricSnapshotRepository snapshots;
    private final RuleEngine ruleEngine;

    public MetricCollectionStore(ProjectRepository projects, MetricSnapshotRepository snapshots, RuleEngine ruleEngine) {
        this.projects = projects;
        this.snapshots = snapshots;
        this.ruleEngine = ruleEngine;
    }

    @Transactional
    public boolean recordSuccess(long projectId, long revision, Instant timestamp, Optional<GoldenSignals> signals) {
        return record(projectId, revision, timestamp, ConnectionStatus.CONNECTED, signals);
    }

    @Transactional
    public boolean recordFailure(long projectId, long revision, Instant timestamp) {
        return record(projectId, revision, timestamp, ConnectionStatus.UNAVAILABLE, Optional.empty());
    }

    private boolean record(long projectId, long revision, Instant timestamp,
            ConnectionStatus status, Optional<GoldenSignals> signals) {
        var project = projects.findByIdForUpdate(projectId).orElse(null);
        if (project == null || !project.isMonitoringEnabled() || project.getMonitoringRevision() != revision) {
            return false;
        }
        // The lock also serializes disable/re-enable with the short snapshot transaction.
        project.recordConnectionStatus(status, timestamp);
        if (signals.isPresent()) {
            MetricSnapshot snapshot = snapshots.save(new MetricSnapshot(projectId, timestamp, signals.get()));
            ruleEngine.evaluate(snapshot);
        } else {
            ruleEngine.resetProgress(projectId);
        }
        return true;
    }
}
