package com.srecopilot.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 2048)
            @Pattern(regexp = "^https?://[^\\s]+$", message = "must be an HTTP or HTTPS URL")
            String endpointUrl,
            @NotNull EndpointType endpointType) {
    }

    public record ProjectResponse(
            Long id, Long userId, String name, String description,
            String endpointUrl, EndpointType endpointType,
            boolean monitoringEnabled, ConnectionStatus connectionStatus,
            Instant createdAt, Instant updatedAt) {
    }

    public record ConnectionTestResponse(ConnectionStatus connectionStatus, String message) {
    }
}
