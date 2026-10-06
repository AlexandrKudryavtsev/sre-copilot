package com.srecopilot.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoldenSignalsCalculatorTest {

    private final GoldenSignalsCalculator calculator = new GoldenSignalsCalculator();
    private final PrometheusTextValidator parser = new PrometheusTextValidator();
    private final Instant start = Instant.parse("2026-10-06T12:00:00Z");

    @Test
    void computesWeightedIntervalSignalsInsteadOfLifetimeAverages() {
        var previous = read(start, series("200", 1000, 500) + series("400", 30, 15) + series("500", 50, 25));
        var current = read(start.plusSeconds(10), series("200", 1080, 512) + series("400", 40, 16) + series("500", 60, 27));
        var signals = calculator.calculate(previous, current).orElseThrow();
        assertThat(signals.latency()).isEqualByComparingTo("150");
        assertThat(signals.traffic()).isEqualByComparingTo("10");
        assertThat(signals.errors()).isEqualByComparingTo("10");
        assertThat(signals.saturation()).isEqualByComparingTo("25");
    }

    @Test
    void waitsForTwoSamplesAndRePrimesAfterCounterResetInAnySeries() {
        var previous = read(start, series("200", 100, 10) + series("500", 10, 1));
        var reset = read(start.plusSeconds(10), series("200", 1000, 100) + series("500", 1, 0.1));
        assertThat(calculator.calculate(null, previous)).isEmpty();
        assertThat(calculator.calculate(previous, reset)).isEmpty();
        var next = read(start.plusSeconds(20), series("200", 1100, 110) + series("500", 1, 0.1));
        assertThat(calculator.calculate(reset, next).orElseThrow().traffic()).isEqualByComparingTo("10");
    }

    @Test
    void rePrimesWhenTimeSeriesChangeOrTheClockDoesNotAdvance() {
        var previous = read(start, series("200", 100, 10));
        var sameTime = read(start, series("200", 110, 11));
        var newSeries = read(start.plusSeconds(10), series("200", 110, 11) + series("500", 1, 0.1));
        assertThat(calculator.calculate(previous, sameTime)).isEmpty();
        assertThat(calculator.calculate(previous, newSeries)).isEmpty();
        assertThat(calculator.calculate(sameTime, read(start.minusSeconds(1), series("200", 120, 12)))).isEmpty();
    }

    @Test
    void emitsZeroTrafficLatencyAndErrorsWhenNoRequestsArrive() {
        var previous = read(start, series("200", 100, 10));
        var signals = calculator.calculate(previous, read(start.plusSeconds(10), series("200", 100, 10))).orElseThrow();
        assertThat(signals.latency()).isZero();
        assertThat(signals.traffic()).isZero();
        assertThat(signals.errors()).isZero();
        assertThat(signals.saturation()).isEqualByComparingTo("25");
    }

    @Test
    void usesActualSubsecondElapsedTimeAndMaximumCpuSeries() {
        var previous = read(start, series("200", 100, 10));
        var current = calculator.read(new PrometheusScrape(start.plusMillis(1500), parser.parse(
                series("200", 103, 10.3) + "process_cpu_usage{cpu=\"a\"} 0.2\nprocess_cpu_usage{cpu=\"b\"} 0.7\n")));
        var signals = calculator.calculate(previous, current).orElseThrow();
        assertThat(signals.traffic()).isEqualByComparingTo("2");
        assertThat(signals.latency()).isEqualByComparingTo("100");
        assertThat(signals.saturation()).isEqualByComparingTo("70");
    }

    @Test
    void doesNotTreatUnrelatedNanGaugesAsInvalidSourceMetrics() {
        assertThat(read(start, series("200", 10, 1) + "unrelated_metric NaN\n").requests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "process_cpu_usage 0.2\n",
            "http_server_requests_seconds_count{status=\"200\"} 10\nprocess_cpu_usage 0.2\n",
            "http_server_requests_seconds_count 10\nhttp_server_requests_seconds_sum 1\nprocess_cpu_usage 0.2\n",
            "http_server_requests_seconds_count{status=\"200\"} NaN\nhttp_server_requests_seconds_sum{status=\"200\"} 1\nprocess_cpu_usage 0.2\n",
            "http_server_requests_seconds_count{status=\"200\"} 10\nhttp_server_requests_seconds_sum{status=\"200\"} +Inf\nprocess_cpu_usage 0.2\n",
            "http_server_requests_seconds_count{status=\"200\"} -1\nhttp_server_requests_seconds_sum{status=\"200\"} 1\nprocess_cpu_usage 0.2\n",
            "http_server_requests_seconds_count{status=\"200\"} 0\nhttp_server_requests_seconds_sum{status=\"200\"} 1\nprocess_cpu_usage 0.2\n",
            "http_server_requests_seconds_count{status=\"200\"} 10\nhttp_server_requests_seconds_sum{status=\"500\"} 1\nprocess_cpu_usage 0.2\n"
    })
    void rejectsMissingInvalidOrMismatchedSourceMetrics(String text) {
        assertThatThrownBy(() -> calculator.read(new PrometheusScrape(start, parser.parse(text))))
                .isInstanceOf(MetricCollectionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "+Inf", "-0.1", "1.1"})
    void rejectsInvalidCpuUsage(String value) {
        assertThatThrownBy(() -> calculator.read(new PrometheusScrape(start,
                parser.parse(series("200", 10, 1) + "process_cpu_usage " + value + "\n"))))
                .isInstanceOf(MetricCollectionException.class);
    }

    private GoldenSignalsCalculator.Reading read(Instant time, String requests) {
        return calculator.read(new PrometheusScrape(time, parser.parse(requests + "process_cpu_usage 0.25\n")));
    }

    private String series(String status, double count, double seconds) {
        return "http_server_requests_seconds_count{method=\"GET\",status=\"%s\"} %s\n".formatted(status, count)
                + "http_server_requests_seconds_sum{status=\"%s\",method=\"GET\"} %s\n".formatted(status, seconds);
    }
}
