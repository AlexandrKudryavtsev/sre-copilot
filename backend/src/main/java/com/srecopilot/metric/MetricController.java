package com.srecopilot.metric;

import com.srecopilot.metric.MetricDtos.MetricSnapshotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/metrics")
@Tag(name = "Metrics")
@ApiResponse(responseCode = "501", description = "Feature not implemented",
        content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
public class MetricController {

    private final MetricService service;

    public MetricController(MetricService service) {
        this.service = service;
    }

    @GetMapping("/current")
    @Operation(summary = "Get latest metric snapshot")
    public MetricSnapshotResponse current(@PathVariable @Positive long projectId) {
        return service.current(projectId);
    }

    @GetMapping("/history")
    @Operation(summary = "Get metric snapshots for an interval")
    public List<MetricSnapshotResponse> history(@PathVariable @Positive long projectId,
            @RequestParam Instant from, @RequestParam Instant to) {
        return service.history(projectId, from, to);
    }
}
