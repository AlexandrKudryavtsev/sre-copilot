package com.srecopilot.rule;

import com.srecopilot.rule.RuleDtos.RuleRequest;
import com.srecopilot.rule.RuleDtos.RuleResponse;
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
@RequestMapping("/api/projects/{projectId}/rules")
@Tag(name = "Monitoring Rules")
@ApiResponse(responseCode = "501", description = "Feature not implemented",
        content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
public class RuleController {

    private final RuleService service;

    public RuleController(RuleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List monitoring rules")
    public List<RuleResponse> list(@PathVariable @Positive long projectId) {
        return service.list(projectId);
    }

    @PostMapping
    @Operation(summary = "Create monitoring rule")
    public RuleResponse create(@PathVariable @Positive long projectId,
            @Valid @RequestBody RuleRequest request) {
        return service.create(projectId, request);
    }

    @GetMapping("/{ruleId}")
    @Operation(summary = "Get monitoring rule")
    public RuleResponse get(@PathVariable @Positive long projectId,
            @PathVariable @Positive long ruleId) {
        return service.get(projectId, ruleId);
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "Update monitoring rule")
    public RuleResponse update(@PathVariable @Positive long projectId,
            @PathVariable @Positive long ruleId, @Valid @RequestBody RuleRequest request) {
        return service.update(projectId, ruleId, request);
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "Delete monitoring rule")
    public void delete(@PathVariable @Positive long projectId, @PathVariable @Positive long ruleId) {
        service.delete(projectId, ruleId);
    }

    @PostMapping("/{ruleId}/enable")
    @Operation(summary = "Enable monitoring rule")
    public RuleResponse enable(@PathVariable @Positive long projectId,
            @PathVariable @Positive long ruleId) {
        return service.setEnabled(projectId, ruleId, true);
    }

    @PostMapping("/{ruleId}/disable")
    @Operation(summary = "Disable monitoring rule")
    public RuleResponse disable(@PathVariable @Positive long projectId,
            @PathVariable @Positive long ruleId) {
        return service.setEnabled(projectId, ruleId, false);
    }
}
