package com.srecopilot.monitoring;

import com.srecopilot.monitoring.GoldenSignalsCalculator.Reading;
import com.srecopilot.project.Project;
import com.srecopilot.project.ProjectRepository;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MetricCollector {

    private static final Logger LOG = LoggerFactory.getLogger(MetricCollector.class);

    private record Baseline(long revision, String endpointUrl, Reading reading) {
    }

    private final ProjectRepository projects;
    private final PrometheusClient client;
    private final GoldenSignalsCalculator calculator;
    private final MetricCollectionStore store;
    private final Clock clock;
    private final Map<Long, Baseline> baselines = new HashMap<>();

    public MetricCollector(ProjectRepository projects, PrometheusClient client,
            GoldenSignalsCalculator calculator, MetricCollectionStore store, Clock clock) {
        this.projects = projects;
        this.client = client;
        this.calculator = calculator;
        this.store = store;
        this.clock = clock;
    }

    // One process, one cycle at a time. No database transaction spans the outbound HTTP call.
    public synchronized void collect() {
        var enabled = projects.findAllByMonitoringEnabledTrueOrderByIdAsc();
        baselines.keySet().retainAll(enabled.stream().map(Project::getId).collect(Collectors.toSet()));
        for (Project project : enabled) {
            try {
                Reading reading = calculator.read(client.fetch(project.getEndpointUrl()));
                Baseline baseline = baselines.get(project.getId());
                Reading previous = baseline != null && baseline.revision() == project.getMonitoringRevision()
                        && baseline.endpointUrl().equals(project.getEndpointUrl()) ? baseline.reading() : null;
                var signals = calculator.calculate(previous, reading);
                if (store.recordSuccess(project.getId(), project.getMonitoringRevision(), reading.timestamp(), signals)) {
                    baselines.put(project.getId(), new Baseline(project.getMonitoringRevision(), project.getEndpointUrl(), reading));
                } else {
                    baselines.remove(project.getId());
                }
            } catch (RuntimeException exception) {
                baselines.remove(project.getId());
                if (exception instanceof MetricCollectionException) {
                    LOG.warn("Metric collection failed for project {}: {}", project.getId(), exception.getMessage());
                } else {
                    LOG.error("Metric collection failed for project {}", project.getId(), exception);
                }
                try {
                    store.recordFailure(project.getId(), project.getMonitoringRevision(), clock.instant());
                } catch (RuntimeException persistenceFailure) {
                    LOG.error("Could not record collection failure for project {}", project.getId(), persistenceFailure);
                }
            }
        }
    }
}
