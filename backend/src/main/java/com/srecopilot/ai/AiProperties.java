package com.srecopilot.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ai")
public record AiProperties(
        String token,
        @NotBlank String model,
        @NotBlank String baseUrl,
        @NotNull @DurationMin(millis = 1) Duration requestTimeout,
        @NotNull @DurationMin(seconds = 1) Duration pollInterval,
        @NotNull @DurationMin(seconds = 1) Duration historyWindow,
        @Min(1) int maxHistoryPoints,
        @Min(1) int maxOutputTokens,
        @Min(1) int maxResponseBytes,
        boolean schedulerEnabled) {

    // Configuration objects must never expose the credential through generated toString().
    @Override public String toString() { return "AiProperties[model=" + model + "]"; }
}
