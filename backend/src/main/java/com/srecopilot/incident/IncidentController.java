package com.srecopilot.incident;

import com.srecopilot.incident.IncidentDtos.IncidentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Incidents")
@ApiResponse(responseCode = "501", description = "Feature not implemented",
        content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @GetMapping("/api/projects/{projectId}/incidents")
    @Operation(summary = "List project incidents")
    public List<IncidentResponse> list(@PathVariable @Positive long projectId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Severity severity) {
        return service.list(projectId, status, severity);
    }

    @GetMapping("/api/incidents/{incidentId}")
    @Operation(summary = "Get incident")
    public IncidentResponse get(@PathVariable @Positive long incidentId) {
        return service.get(incidentId);
    }
}
