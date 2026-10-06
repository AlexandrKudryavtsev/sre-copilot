package com.srecopilot.monitoring;

import com.srecopilot.metric.MetricSnapshot;
import com.srecopilot.metric.MetricSnapshotRepository;
import com.srecopilot.project.ConnectionStatus;
import com.srecopilot.project.ProjectRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricCollectionStore {

    private final ProjectRepository projects;
    private final MetricSnapshotRepository snapshots;

    public MetricCollectionStore(ProjectRepository projects, MetricSnapshotRepository snapshots) {
        this.projects = projects;
        this.snapshots = snapshots;
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
        signals.ifPresent(value -> snapshots.save(new MetricSnapshot(projectId, timestamp, value)));
        return true;
    }
}
