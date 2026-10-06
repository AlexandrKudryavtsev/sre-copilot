package com.srecopilot.project;

import com.srecopilot.project.ProjectDtos.ConnectionTestResponse;
import com.srecopilot.project.ProjectDtos.ProjectRequest;
import com.srecopilot.project.ProjectDtos.ProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projects")
@ApiResponse(responseCode = "501", description = "Feature not implemented",
        content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List projects")
    public List<ProjectResponse> list() {
        return service.list();
    }

    @PostMapping
    @Operation(summary = "Create project")
    public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project")
    public ProjectResponse get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update project")
    public ProjectResponse update(@PathVariable @Positive long id,
            @Valid @RequestBody ProjectRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete project")
    public void delete(@PathVariable @Positive long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/test-connection")
    @Operation(summary = "Test monitoring endpoint connection")
    public ConnectionTestResponse testConnection(@PathVariable @Positive long id) {
        return service.testConnection(id);
    }

    @PostMapping("/{id}/monitoring/enable")
    @Operation(summary = "Enable monitoring")
    public ProjectResponse enable(@PathVariable @Positive long id) {
        return service.setMonitoringEnabled(id, true);
    }

    @PostMapping("/{id}/monitoring/disable")
    @Operation(summary = "Disable monitoring")
    public ProjectResponse disable(@PathVariable @Positive long id) {
        return service.setMonitoringEnabled(id, false);
    }
}
