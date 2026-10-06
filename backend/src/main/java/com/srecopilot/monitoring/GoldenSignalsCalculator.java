package com.srecopilot.monitoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GoldenSignalsCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    public record Counters(BigDecimal count, BigDecimal durationSeconds) {
    }

    public record Reading(Instant timestamp, Map<Map<String, String>, Counters> requests, BigDecimal saturation) {
        public Reading {
            requests = Map.copyOf(requests);
        }
    }

    public Reading read(PrometheusScrape scrape) {
        Map<Map<String, String>, BigDecimal> counts = new HashMap<>();
        Map<Map<String, String>, BigDecimal> durations = new HashMap<>();
        BigDecimal cpu = null;
        for (PrometheusSample sample : scrape.samples()) {
            switch (sample.name()) {
                case "http_server_requests_seconds_count", "http_server_requests_seconds_sum" -> {
                    String status = sample.labels().get("status");
                    if (status == null || !status.matches("[1-5][0-9]{2}")) {
                        throw new MetricCollectionException("HTTP request metrics must include a numeric status label.");
                    }
                    Map<Map<String, String>, BigDecimal> target = sample.name().endsWith("_count") ? counts : durations;
                    if (target.put(sample.labels(), nonNegativeValue(sample)) != null) {
                        throw new MetricCollectionException("Duplicate HTTP request metric series.");
                    }
                }
                case "process_cpu_usage" -> {
                    BigDecimal value = nonNegativeValue(sample);
                    if (value.compareTo(BigDecimal.ONE) > 0) {
                        throw new MetricCollectionException("process_cpu_usage must be between 0 and 1.");
                    }
                    cpu = cpu == null ? value : cpu.max(value);
                }
                default -> { /* Other metric families are not used by this adapter. */ }
            }
        }
        if (counts.isEmpty() || !counts.keySet().equals(durations.keySet())) {
            throw new MetricCollectionException("Matching http_server_requests_seconds_count and _sum series are required.");
        }
        if (cpu == null) {
            throw new MetricCollectionException("process_cpu_usage is required for saturation.");
        }
        Map<Map<String, String>, Counters> requests = new HashMap<>();
        counts.forEach((labels, count) -> {
            BigDecimal duration = durations.get(labels);
            if (count.signum() == 0 && duration.signum() != 0) {
                throw new MetricCollectionException("Request duration sum must be zero when the count is zero.");
            }
            requests.put(labels, new Counters(count, duration));
        });
        return new Reading(scrape.timestamp(), requests, cpu.multiply(HUNDRED));
    }

    public Optional<GoldenSignals> calculate(Reading previous, Reading current) {
        // Re-prime the baseline on startup, a clock jump or a changed set of time series.
        if (previous == null || !current.timestamp().isAfter(previous.timestamp())
                || !current.requests().keySet().equals(previous.requests().keySet())) {
            return Optional.empty();
        }
        BigDecimal count = BigDecimal.ZERO;
        BigDecimal duration = BigDecimal.ZERO;
        BigDecimal errors = BigDecimal.ZERO;
        for (var entry : current.requests().entrySet()) {
            Counters before = previous.requests().get(entry.getKey());
            BigDecimal countDelta = entry.getValue().count().subtract(before.count());
            BigDecimal durationDelta = entry.getValue().durationSeconds().subtract(before.durationSeconds());
            // Detect resets per series, even if the aggregate counter still grew.
            if (countDelta.signum() < 0 || durationDelta.signum() < 0) {
                return Optional.empty();
            }
            if (countDelta.signum() == 0 && durationDelta.signum() != 0) {
                throw new MetricCollectionException("Request duration grew without any new requests.");
            }
            count = count.add(countDelta);
            duration = duration.add(durationDelta);
            if (entry.getKey().get("status").startsWith("5")) {
                errors = errors.add(countDelta);
            }
        }
        Duration elapsed = Duration.between(previous.timestamp(), current.timestamp());
        BigDecimal seconds = BigDecimal.valueOf(elapsed.getSeconds())
                .add(BigDecimal.valueOf(elapsed.getNano(), 9));
        BigDecimal latency = count.signum() == 0 ? BigDecimal.ZERO : divide(duration.multiply(THOUSAND), count);
        BigDecimal errorPercent = count.signum() == 0 ? BigDecimal.ZERO : divide(errors.multiply(HUNDRED), count);
        return Optional.of(new GoldenSignals(latency, divide(count, seconds), errorPercent,
                current.saturation().setScale(6, RoundingMode.HALF_UP)));
    }

    private BigDecimal nonNegativeValue(PrometheusSample sample) {
        try {
            BigDecimal value = new BigDecimal(sample.value());
            if (value.signum() < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new MetricCollectionException(sample.name() + " must be a finite, non-negative number.");
        }
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }
}
