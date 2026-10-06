package com.srecopilot.project;

import com.srecopilot.common.exception.FeatureNotImplementedException;
import com.srecopilot.monitoring.PrometheusClient;
import com.srecopilot.project.ProjectDtos.ConnectionTestResponse;
import com.srecopilot.project.ProjectDtos.ProjectRequest;
import com.srecopilot.project.ProjectDtos.ProjectResponse;
import com.srecopilot.user.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final CurrentUserProvider currentUser;
    private final PrometheusClient prometheusClient;

    public ProjectService(ProjectRepository repository, CurrentUserProvider currentUser,
            PrometheusClient prometheusClient) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.prometheusClient = prometheusClient;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        return repository.findAllByUserIdOrderByCreatedAtDescIdDesc(currentUser.userId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        Project project = new Project(currentUser.userId(), request.name().strip(), request.description(),
                request.endpointUrl(), request.endpointType());
        return toResponse(repository.save(project));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(long id) {
        return toResponse(requireProject(id, currentUser.userId()));
    }

    public ProjectResponse update(long id, ProjectRequest request) {
        throw new FeatureNotImplementedException("Update project");
    }

    public void delete(long id) {
        throw new FeatureNotImplementedException("Delete project");
    }

    public ConnectionTestResponse testConnection(long id) {
        long userId = currentUser.userId();
        Project project = requireProject(id, userId);
        // Network I/O happens outside a database transaction.
        var probe = prometheusClient.testConnection(project.getEndpointUrl());
        ConnectionStatus status = probe.connected() ? ConnectionStatus.CONNECTED : ConnectionStatus.UNAVAILABLE;
        if (repository.updateConnectionStatus(id, userId, status, Instant.now()) == 0) {
            throw new ProjectNotFoundException(id);
        }
        return new ConnectionTestResponse(status, probe.message());
    }

    @Transactional
    public ProjectResponse setMonitoringEnabled(long id, boolean enabled) {
        Project project = repository.findOwnedByIdForUpdate(id, currentUser.userId())
                .orElseThrow(() -> new ProjectNotFoundException(id));
        project.setMonitoringEnabled(enabled);
        return toResponse(project);
    }

    private Project requireProject(long id, long userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getUserId(), project.getName(),
                project.getDescription(), project.getEndpointUrl(), project.getEndpointType(),
                project.isMonitoringEnabled(), project.getConnectionStatus(),
                project.getCreatedAt(), project.getUpdatedAt());
    }
}
