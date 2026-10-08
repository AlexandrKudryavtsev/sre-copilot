package com.srecopilot.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srecopilot.metric.MetricSnapshotRepository;
import com.srecopilot.project.ConnectionStatus;
import com.srecopilot.project.EndpointType;
import com.srecopilot.project.Project;
import com.srecopilot.project.ProjectRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "monitoring.scheduler-enabled=false",
        "monitoring.request-timeout=2s",
        "spring.datasource.hikari.maximum-pool-size=1",
        "debug=false"
})
@AutoConfigureMockMvc
@Import(MonitoringWorkflowIT.TimeConfig.class)
@Testcontainers
class MonitoringWorkflowIT {

    private static final Instant START = Instant.parse("2026-10-06T12:00:00Z");
    private static final AtomicReference<String> BODY = new AtomicReference<>();
    private static final AtomicReference<Runnable> HOOK = new AtomicReference<>(() -> { });
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final AtomicInteger HTTP_STATUS = new AtomicInteger(200);
    private static HttpServer endpoint;
    private static ExecutorService executor;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired MetricSnapshotRepository snapshots;
    @Autowired MetricCollector collector;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(START);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(time.get(), zone); }
        @Override public Instant instant() { return time.get(); }
        void advance() { time.updateAndGet(value -> value.plusSeconds(10)); }
    }

    @BeforeAll
    static void startEndpoint() throws IOException {
        endpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        endpoint.setExecutor(executor);
        endpoint.createContext("/metrics", exchange -> {
            REQUESTS.incrementAndGet();
            HOOK.get().run();
            respond(exchange, HTTP_STATUS.get(), BODY.get());
        });
        endpoint.createContext("/unavailable", exchange -> respond(exchange, 503, "unavailable\n"));
        endpoint.createContext("/other-metrics", exchange -> respond(exchange, 200, BODY.get()));
        endpoint.start();
    }

    @AfterAll
    static void stopEndpoint() {
        endpoint.stop(0);
        executor.shutdownNow();
    }

    @BeforeEach
    void reset() {
        projects.deleteAll();
        clock.time.set(START);
        REQUESTS.set(0);
        HTTP_STATUS.set(200);
        HOOK.set(() -> { });
        BODY.set(metrics(100, 10, 10, 2, 0.25));
    }

    @ParameterizedTest
    @EnumSource(EndpointType.class)
    void collectsGoldenSignalsAndServesCurrentAndInclusiveHistory(EndpointType type) throws Exception {
        long id = create("/metrics", type);
        collector.collect();
        assertThat(REQUESTS.get()).isZero();
        mvc.perform(get("/api/projects/{id}/metrics/current", id)).andExpect(status().isNoContent());
        enable(id, true);
        collector.collect();
        assertThat(snapshots.count()).isZero();
        assertThat(projects.findById(id).orElseThrow().getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        BODY.set(metrics(190, 28, 20, 5, 0.25));
        clock.advance();
        collector.collect();
        mvc.perform(get("/api/projects/{id}/metrics/current", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(id))
                .andExpect(jsonPath("$.timestamp").value(START.plusSeconds(10).toString()))
                .andExpect(jsonPath("$.latency").value(210))
                .andExpect(jsonPath("$.traffic").value(10))
                .andExpect(jsonPath("$.errors").value(10))
                .andExpect(jsonPath("$.saturation").value(25));
        BODY.set(metrics(230, 36, 20, 5, 0.7));
        clock.advance();
        collector.collect();
        mvc.perform(get("/api/projects/{id}/metrics/history", id)
                        .param("from", START.plusSeconds(10).toString()).param("to", START.plusSeconds(20).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].traffic").value(10))
                .andExpect(jsonPath("$[1].traffic").value(4));
        mvc.perform(get("/api/projects/{id}/metrics/current", id))
                .andExpect(jsonPath("$.saturation").value(70));
        mvc.perform(get("/api/projects/{id}/metrics/history", id)
                        .param("from", START.toString()).param("to", START.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/projects/{id}/metrics/history", id)
                        .param("from", START.plusSeconds(1).toString()).param("to", START.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void failsOneProjectWithoutStoppingOthersAndRePrimesAfterRecovery() throws Exception {
        long bad = create("/unavailable", EndpointType.PROMETHEUS);
        long good = create("/metrics", EndpointType.PROMETHEUS);
        enable(bad, true);
        enable(good, true);
        collector.collect();
        BODY.set(metrics(200, 20, 20, 4, 0.25));
        clock.advance();
        collector.collect();
        assertThat(projects.findById(bad).orElseThrow().getConnectionStatus()).isEqualTo(ConnectionStatus.UNAVAILABLE);
        assertThat(snapshots.findFirstByProjectIdOrderByTimestampDescIdDesc(bad)).isEmpty();
        assertThat(snapshots.count()).isEqualTo(1);
        HTTP_STATUS.set(503);
        clock.advance();
        collector.collect();
        assertThat(projects.findById(good).orElseThrow().getConnectionStatus()).isEqualTo(ConnectionStatus.UNAVAILABLE);
        assertThat(snapshots.count()).isEqualTo(1);
        mvc.perform(get("/api/projects/{id}/metrics/current", good)).andExpect(status().isOk());
        HTTP_STATUS.set(200);
        clock.advance();
        collector.collect();
        assertThat(projects.findById(good).orElseThrow().getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(snapshots.count()).isEqualTo(1);
        BODY.set(metrics(300, 30, 30, 6, 0.25));
        clock.advance();
        collector.collect();
        assertThat(snapshots.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"process_cpu_usage 0.2\n", "http_server_requests_seconds_count{status=\"200\"} NaN\nprocess_cpu_usage 0.2\n"})
    void rejectsIncompleteOrInvalidMetricsWithoutWritingSnapshots(String text) throws Exception {
        long id = create("/metrics", EndpointType.PROMETHEUS);
        enable(id, true);
        BODY.set(text);
        collector.collect();
        assertThat(snapshots.count()).isZero();
        assertThat(projects.findById(id).orElseThrow().getConnectionStatus()).isEqualTo(ConnectionStatus.UNAVAILABLE);
    }

    @Test
    void disableStopsRequestsAndReEnableRequiresAFreshBaselineEvenBetweenCycles() throws Exception {
        long id = create("/metrics", EndpointType.PROMETHEUS);
        enable(id, true);
        collector.collect();
        enable(id, false);
        clock.advance();
        collector.collect();
        assertThat(REQUESTS.get()).isEqualTo(1);
        enable(id, true);
        collector.collect();
        clock.advance();
        BODY.set(metrics(200, 20, 20, 4, 0.25));
        collector.collect();
        assertThat(snapshots.count()).isEqualTo(1);
        long revision = projects.findById(id).orElseThrow().getMonitoringRevision();
        enable(id, true);
        assertThat(projects.findById(id).orElseThrow().getMonitoringRevision()).isEqualTo(revision);
        enable(id, false);
        enable(id, true);
        clock.advance();
        BODY.set(metrics(300, 30, 30, 6, 0.25));
        collector.collect();
        assertThat(snapshots.count()).isEqualTo(1);
    }

    @Test
    void doesNotHoldADatabaseConnectionDuringHttpAndDiscardsResultsAfterRestartingMonitoring() throws Exception {
        long id = create("/metrics", EndpointType.PROMETHEUS);
        enable(id, true);
        collector.collect();
        clock.advance();
        BODY.set(metrics(200, 20, 20, 4, 0.25));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HOOK.set(() -> {
            assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
            entered.countDown();
            try {
                if (!release.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Collection was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = worker.submit(collector::collect);
            try {
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                enable(id, false);
                enable(id, true);
            } finally {
                release.countDown();
            }
            future.get(3, TimeUnit.SECONDS);
        }
        assertThat(snapshots.count()).isZero();
        HOOK.set(() -> { });
        clock.advance();
        collector.collect();
        assertThat(snapshots.count()).isZero();
        clock.advance();
        BODY.set(metrics(300, 30, 30, 6, 0.25));
        collector.collect();
        assertThat(snapshots.count()).isEqualTo(1);
    }

    @Test
    void metricReadsAndMonitoringControlsRespectProjectOwnership() throws Exception {
        long user = jdbc.queryForObject("INSERT INTO users(email, password_hash) VALUES ('other-monitor@test', '!disabled') RETURNING id", Long.class);
        long other = projects.save(new Project(user, "Other", null, url("/metrics"), EndpointType.PROMETHEUS)).getId();
        for (long id : new long[]{other, Long.MAX_VALUE}) {
            mvc.perform(get("/api/projects/{id}/metrics/current", id)).andExpect(status().isNotFound());
            mvc.perform(get("/api/projects/{id}/metrics/history", id)
                    .param("from", START.toString()).param("to", START.plusSeconds(10).toString()))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/projects/{id}/monitoring/enable", id)).andExpect(status().isNotFound());
            mvc.perform(post("/api/projects/{id}/monitoring/disable", id)).andExpect(status().isNotFound());
        }
    }

    @Test
    void counterResetDoesNotCreateANegativeOrArtificialSpike() throws Exception {
        long id = create("/metrics", EndpointType.PROMETHEUS);
        enable(id, true);
        collector.collect();
        clock.advance();
        BODY.set(metrics(1, 0.1, 0, 0, 0.25));
        collector.collect();
        assertThat(snapshots.count()).isZero();
        clock.advance();
        BODY.set(metrics(101, 10.1, 0, 0, 0.25));
        collector.collect();
        mvc.perform(get("/api/projects/{id}/metrics/current", id))
                .andExpect(jsonPath("$.traffic").value(10))
                .andExpect(jsonPath("$.latency").value(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"url", "type"})
    void endpointChangesRePrimeCountersAndResetRuleProgressWhileKeepingHistory(String field) throws Exception {
        long id = create("/metrics", EndpointType.PROMETHEUS);
        mvc.perform(post("/api/projects/{id}/rules", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Pending","metric":"SATURATION","operator":"GREATER_OR_EQUAL","threshold":80,
                 "severity":"WARNING","checkIntervalSeconds":10,"consecutiveViolations":3,"enabled":true}
                """)).andExpect(status().isCreated());
        mvc.perform(post("/api/projects/{id}/rules", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Open","metric":"SATURATION","operator":"GREATER_OR_EQUAL","threshold":80,
                 "severity":"CRITICAL","checkIntervalSeconds":10,"consecutiveViolations":1,"enabled":true}
                """)).andExpect(status().isCreated());
        enable(id, true);
        collector.collect();
        clock.advance();
        BODY.set(metrics(200, 20, 20, 4, 0.9));
        collector.collect();
        mvc.perform(get("/api/projects/{id}/rules", id))
                .andExpect(jsonPath("$[0].runtimeState.consecutiveViolations").value(1))
                .andExpect(jsonPath("$[1].runtimeState.state").value("INCIDENT_OPEN"));
        assertThat(snapshots.count()).isEqualTo(1);
        mvc.perform(put("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Updated","endpointUrl":"%s","endpointType":"%s"}
                """.formatted(url(field.equals("url") ? "/other-metrics" : "/metrics"),
                        field.equals("type") ? "ACTUATOR" : "PROMETHEUS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.connectionStatus").value("UNKNOWN"));
        mvc.perform(get("/api/projects/{id}/rules", id))
                .andExpect(jsonPath("$[0].runtimeState.consecutiveViolations").value(0))
                .andExpect(jsonPath("$[0].runtimeState.state").value("HEALTHY"))
                .andExpect(jsonPath("$[1].runtimeState.state").value("INCIDENT_OPEN"));
        mvc.perform(get("/api/projects/{id}/incidents", id))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("OPEN"));
        assertThat(snapshots.count()).isEqualTo(1);
        clock.advance();
        BODY.set(metrics(300, 30, 30, 6, 0.9));
        collector.collect();
        assertThat(snapshots.count()).isEqualTo(1);
        clock.advance();
        BODY.set(metrics(400, 40, 30, 6, 0.9));
        collector.collect();
        assertThat(snapshots.count()).isEqualTo(2);
        mvc.perform(get("/api/projects/{id}/metrics/current", id)).andExpect(jsonPath("$.traffic").value(10));
        mvc.perform(get("/api/projects/{id}/rules", id))
                .andExpect(jsonPath("$[0].runtimeState.consecutiveViolations").value(1));
        mvc.perform(get("/api/projects/{id}/incidents", id)).andExpect(jsonPath("$.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit", "delete"})
    void discardsScrapesAfterEditingOrDeletingTheProjectDuringHttp(String action) throws Exception {
        long id = create("/metrics", EndpointType.PROMETHEUS);
        enable(id, true);
        collector.collect();
        clock.advance();
        BODY.set(metrics(200, 20, 20, 4, 0.25));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HOOK.set(() -> {
            entered.countDown();
            try {
                if (!release.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Collection was not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = worker.submit(collector::collect);
            try {
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                if (action.equals("delete")) {
                    mvc.perform(delete("/api/projects/{id}", id)).andExpect(status().isNoContent());
                } else {
                    mvc.perform(put("/api/projects/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                            {"name":"Changed","endpointUrl":"%s","endpointType":"PROMETHEUS"}
                            """.formatted(url("/other-metrics"))))
                            .andExpect(status().isOk());
                }
            } finally {
                release.countDown();
            }
            future.get(3, TimeUnit.SECONDS);
        }
        assertThat(snapshots.count()).isZero();
        HOOK.set(() -> { });
        if (action.equals("delete")) {
            assertThat(projects.findById(id)).isEmpty();
            collector.collect();
            assertThat(REQUESTS.get()).isEqualTo(2);
        } else {
            assertThat(projects.findById(id).orElseThrow().getConnectionStatus()).isEqualTo(ConnectionStatus.UNKNOWN);
            clock.advance();
            collector.collect();
            assertThat(snapshots.count()).isZero();
            clock.advance();
            BODY.set(metrics(300, 30, 30, 6, 0.25));
            collector.collect();
            assertThat(snapshots.count()).isEqualTo(1);
        }
    }

    @Test
    void deletingAProjectCascadesAllMonitoringDataWithoutAffectingOtherProjects() throws Exception {
        long deleted = create("/metrics", EndpointType.PROMETHEUS);
        long retained = create("/metrics", EndpointType.PROMETHEUS);
        for (long id : new long[]{deleted, retained}) {
            mvc.perform(post("/api/projects/{id}/rules", id).contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"CPU","metric":"SATURATION","operator":"GREATER_OR_EQUAL","threshold":0,
                     "severity":"WARNING","checkIntervalSeconds":10,"consecutiveViolations":1,"enabled":true}
                    """)).andExpect(status().isCreated());
            enable(id, true);
        }
        collector.collect();
        clock.advance();
        BODY.set(metrics(200, 20, 20, 4, 0.25));
        collector.collect();
        long rule = jdbc.queryForObject("SELECT id FROM monitoring_rules WHERE project_id = ?", Long.class, deleted);
        long incident = jdbc.queryForObject("SELECT id FROM incidents WHERE project_id = ?", Long.class, deleted);
        assertThat(jdbc.queryForObject("SELECT active_incident_id FROM monitoring_rule_states WHERE rule_id = ?",
                Long.class, rule)).isEqualTo(incident);
        mvc.perform(delete("/api/projects/{id}", deleted)).andExpect(status().isNoContent());
        assertThat(projects.findById(deleted)).isEmpty();
        assertThat(projects.findById(retained)).isPresent();
        for (String table : new String[]{"metric_snapshots", "monitoring_rules", "incidents"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE project_id = ?", Long.class, deleted))
                    .as(table + " for deleted project").isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE project_id = ?", Long.class, retained))
                    .as(table + " for retained project").isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_rule_states WHERE rule_id = ?", Long.class, rule))
                .isZero();
        mvc.perform(get("/api/incidents/{id}", incident)).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/{id}", deleted)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/projects/{id}", deleted)).andExpect(status().isNotFound());
        clock.advance();
        BODY.set(metrics(300, 30, 30, 6, 0.25));
        collector.collect();
        assertThat(snapshots.count()).isEqualTo(2);
        assertThat(REQUESTS.get()).isEqualTo(5);
    }

    @Test
    void realHttpCollectionOpensAndResolvesAnIncident() throws Exception {
        long id = create("/metrics", EndpointType.PROMETHEUS);
        mvc.perform(post("/api/projects/{id}/rules", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Latency","metric":"LATENCY","operator":"GREATER_OR_EQUAL","threshold":800,
                 "severity":"CRITICAL","checkIntervalSeconds":10,"consecutiveViolations":2,"enabled":true}
                """)).andExpect(status().isCreated());
        enable(id, true);
        collector.collect();
        for (int i = 1; i <= 2; i++) {
            BODY.set(metrics(100 + 20 * i, 10 + 20 * i, 10, 2, 0.25));
            clock.advance();
            collector.collect();
        }
        mvc.perform(get("/api/projects/{id}/incidents", id).param("status", "OPEN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].latencyAtDetection").value(1000));
        for (int i = 1; i <= 3; i++) {
            BODY.set(metrics(140 + 20 * i, 50 + 1.6 * i, 10, 2, 0.25));
            clock.advance();
            collector.collect();
        }
        mvc.perform(get("/api/projects/{id}/incidents", id))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("RESOLVED"));
    }

    private long create(String path, EndpointType type) throws Exception {
        String location = mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Monitored\",\"endpointUrl\":\"%s\",\"endpointType\":\"%s\"}".formatted(url(path), type)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private void enable(long id, boolean enabled) throws Exception {
        mvc.perform(post("/api/projects/{id}/monitoring/{action}", id, enabled ? "enable" : "disable"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monitoringEnabled").value(enabled));
    }

    private static String metrics(double count, double duration, double errors, double errorDuration, double cpu) {
        return """
                http_server_requests_seconds_count{status="200",uri="/test"} %s
                http_server_requests_seconds_sum{status="200",uri="/test"} %s
                http_server_requests_seconds_count{status="500",uri="/test"} %s
                http_server_requests_seconds_sum{status="500",uri="/test"} %s
                process_cpu_usage %s
                """.formatted(count, duration, errors, errorDuration, cpu);
    }

    private static String url(String path) {
        return "http://127.0.0.1:" + endpoint.getAddress().getPort() + path;
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        }
    }
}
