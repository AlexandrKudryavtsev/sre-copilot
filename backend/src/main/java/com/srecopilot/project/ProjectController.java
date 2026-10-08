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
import java.net.URI;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List projects")
    @ApiResponse(responseCode = "200", description = "Projects belonging to the current local user")
    public List<ProjectResponse> list() {
        return service.list();
    }

    @PostMapping
    @Operation(summary = "Create project")
    @ApiResponse(responseCode = "201", description = "Project created")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse project = service.create(request);
        return ResponseEntity.created(URI.create("/api/projects/" + project.id())).body(project);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project")
    @ApiResponse(responseCode = "200", description = "Project found")
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ProjectResponse get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update project")
    @ApiResponse(responseCode = "200", description = "Project updated")
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ProjectResponse update(@PathVariable @Positive long id,
            @Valid @RequestBody ProjectRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete project")
    @ApiResponse(responseCode = "204", description = "Project and its monitoring data deleted", content = @Content)
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test-connection")
    @Operation(summary = "Test monitoring endpoint connection")
    @ApiResponse(responseCode = "200", description = "Connection check completed; inspect connectionStatus")
    @ApiResponse(responseCode = "409", description = "Monitoring configuration changed during the check; retry",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ConnectionTestResponse testConnection(@PathVariable @Positive long id) {
        return service.testConnection(id);
    }

    @PostMapping("/{id}/monitoring/enable")
    @Operation(summary = "Enable monitoring")
    @ApiResponse(responseCode = "200", description = "Monitoring enabled; collection starts on the next cycle")
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ProjectResponse enable(@PathVariable @Positive long id) {
        return service.setMonitoringEnabled(id, true);
    }

    @PostMapping("/{id}/monitoring/disable")
    @Operation(summary = "Disable monitoring")
    @ApiResponse(responseCode = "200", description = "Monitoring disabled; previous snapshots are retained")
    @ApiResponse(responseCode = "404", description = "Project not found",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ProjectResponse disable(@PathVariable @Positive long id) {
        return service.setMonitoringEnabled(id, false);
    }
}
