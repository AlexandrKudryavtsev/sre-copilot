package com.srecopilot.metric;

import com.srecopilot.metric.MetricDtos.MetricSnapshotResponse;
import com.srecopilot.project.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MetricService {

    private final MetricSnapshotRepository repository;
    private final ProjectService projects;

    public MetricService(MetricSnapshotRepository repository, ProjectService projects) {
        this.repository = repository;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public Optional<MetricSnapshotResponse> current(long projectId) {
        projects.get(projectId);
        return repository.findFirstByProjectIdOrderByTimestampDescIdDesc(projectId)
                .map(MetricSnapshot::toResponse);
    }

    @Transactional(readOnly = true)
    public List<MetricSnapshotResponse> history(long projectId, Instant from, Instant to) {
        projects.get(projectId);
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be earlier than or equal to 'to'.");
        }
        return repository.findAllByProjectIdAndTimestampBetweenOrderByTimestampAscIdAsc(projectId, from, to)
                .stream().map(MetricSnapshot::toResponse).toList();
    }
}
