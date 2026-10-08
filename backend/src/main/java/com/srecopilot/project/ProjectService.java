package com.srecopilot.project;

import com.srecopilot.monitoring.PrometheusClient;
import com.srecopilot.project.ProjectDtos.ConnectionTestResponse;
import com.srecopilot.project.ProjectDtos.ProjectRequest;
import com.srecopilot.project.ProjectDtos.ProjectResponse;
import com.srecopilot.user.CurrentUserProvider;
import com.srecopilot.rule.RuleEngine;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectService {

    private final ProjectRepository repository;
    private final CurrentUserProvider currentUser;
    private final PrometheusClient prometheusClient;
    private final RuleEngine ruleEngine;

    public ProjectService(ProjectRepository repository, CurrentUserProvider currentUser,
            PrometheusClient prometheusClient, RuleEngine ruleEngine) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.prometheusClient = prometheusClient;
        this.ruleEngine = ruleEngine;
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

    @Transactional
    public ProjectResponse update(long id, ProjectRequest request) {
        Project project = lockOwnedProject(id);
        if (project.update(request.name().strip(), request.description(), request.endpointUrl(), request.endpointType())) {
            ruleEngine.resetProgress(id);
        }
        return toResponse(project);
    }

    @Transactional
    public void delete(long id) {
        // Foreign keys cascade snapshots, rules, runtime states and incidents in the same transaction.
        repository.delete(lockOwnedProject(id));
    }

    public ConnectionTestResponse testConnection(long id) {
        long userId = currentUser.userId();
        Project project = requireProject(id, userId);
        // Network I/O happens outside a database transaction.
        var probe = prometheusClient.testConnection(project.getEndpointUrl());
        ConnectionStatus status = probe.connected() ? ConnectionStatus.CONNECTED : ConnectionStatus.UNAVAILABLE;
        if (repository.updateConnectionStatus(id, userId, project.getMonitoringRevision(), project.getEndpointUrl(),
                project.getEndpointType(), status, Instant.now()) == 0) {
            requireProject(id, userId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Project monitoring configuration changed during the connection check; retry.");
        }
        return new ConnectionTestResponse(status, probe.message());
    }

    @Transactional
    public ProjectResponse setMonitoringEnabled(long id, boolean enabled) {
        Project project = lockOwnedProject(id);
        if (project.isMonitoringEnabled() != enabled) {
            project.setMonitoringEnabled(enabled);
            ruleEngine.resetProgress(id);
        }
        return toResponse(project);
    }

    private Project requireProject(long id, long userId) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private Project lockOwnedProject(long id) {
        return repository.findOwnedByIdForUpdate(id, currentUser.userId())
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(project.getId(), project.getUserId(), project.getName(),
                project.getDescription(), project.getEndpointUrl(), project.getEndpointType(),
                project.isMonitoringEnabled(), project.getConnectionStatus(),
                project.getCreatedAt(), project.getUpdatedAt());
    }
}
