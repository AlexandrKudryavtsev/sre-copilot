package com.srecopilot.monitoring;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("monitoring")
public record MonitoringProperties(
        @DurationMin(seconds = 1) Duration collectionInterval,
        @DurationMin(millis = 1) Duration requestTimeout,
        @Min(1) int recoveryChecks) {
}
