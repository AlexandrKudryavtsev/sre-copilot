package com.srecopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.srecopilot.incident.IncidentRepository;
import com.srecopilot.incident.IncidentStatus;
import com.srecopilot.incident.Severity;
import com.srecopilot.metric.MetricSnapshot;
import com.srecopilot.metric.MetricSnapshotRepository;
import com.srecopilot.metric.MetricType;
import com.srecopilot.monitoring.GoldenSignals;
import com.srecopilot.monitoring.MetricCollectionStore;
import com.srecopilot.project.EndpointType;
import com.srecopilot.project.Project;
import com.srecopilot.project.ProjectRepository;
import com.srecopilot.project.ProjectService;
import com.srecopilot.rule.RuleDtos.RuleRequest;
import com.srecopilot.rule.RuleOperator;
import com.srecopilot.rule.RuleService;
import com.srecopilot.user.CurrentUserProvider;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {"monitoring.scheduler-enabled=false", "ai.scheduler-enabled=false", "ai.token=test-secret",
        "ai.max-history-points=3", "ai.request-timeout=2s"})
@AutoConfigureMockMvc
@Testcontainers
class AiAnalysisWorkflowIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static final AtomicReference<String> RESULT = new AtomicReference<>();
    static final AtomicReference<String> REQUEST = new AtomicReference<>();
    static final AtomicInteger HTTP_STATUS = new AtomicInteger(200);
    static final AtomicInteger CALLS = new AtomicInteger();
    static final HttpServer SERVER = startServer();
    static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/responses", exchange -> {
                CALLS.incrementAndGet();
                REQUEST.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                var json = JsonMapper.builder().build();
                byte[] body = json.writeValueAsBytes(Map.of("status", "completed", "output", List.of(Map.of("type", "message",
                        "content", List.of(Map.of("type", "output_text", "text", RESULT.get()))))));
                exchange.sendResponseHeaders(HTTP_STATUS.get(), body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            server.start(); return server;
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("ai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/v1");
    }
    @AfterAll static void stop() { SERVER.stop(0); }
    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired ProjectService projectService;
    @Autowired CurrentUserProvider currentUser;
    @Autowired RuleService ruleService;
    @Autowired IncidentRepository incidents;
    @Autowired MetricSnapshotRepository snapshots;
    @Autowired MetricCollectionStore store;
    @Autowired AiAnalysisRepository analyses;
    @Autowired AiAnalysisService service;
    @Autowired AiAnalysisWorker worker;
    @Autowired AiContextService contexts;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private Instant detected;

    @BeforeEach void reset() {
        projects.deleteAll(); CALLS.set(0); HTTP_STATUS.set(200);
        RESULT.set("""
                {"status":"COMPLETED","probableCause":"Вероятно, рост нагрузки.",
                 "evidence":["Latency 950 ms"],"recommendations":["Проверить CPU"]}
                """);
        detected = Instant.now().plusSeconds(10);
    }
    private long incident(long userId) {
        var project = projects.save(new Project(userId, "Checkout", null, "http://localhost/metrics", EndpointType.PROMETHEUS));
        projectService.setMonitoringEnabled(project.getId(), true);
        // Rule creation is an owner-scoped API operation; foreign incident is inserted by copying SQL below.
        var rule = ruleService.create(project.getId(), new RuleRequest("High latency", MetricType.LATENCY,
                RuleOperator.GREATER_OR_EQUAL, BigDecimal.valueOf(800), Severity.CRITICAL, 1, 1, true));
        for (int i = 0; i < 5; i++) {
            snapshots.save(new MetricSnapshot(project.getId(), detected.minusSeconds(1200 - i * 60), signals(200 + i)));
        }
        snapshots.save(new MetricSnapshot(project.getId(), detected.minusSeconds(1900), signals(100)));
        long revision = projects.findById(project.getId()).orElseThrow().getMonitoringRevision();
        store.recordSuccess(project.getId(), revision, detected, Optional.of(signals(950)));
        snapshots.save(new MetricSnapshot(project.getId(), detected.plusSeconds(60), signals(9999)));
        return incidents.findByRuleIdAndStatus(rule.id(), IncidentStatus.OPEN).orElseThrow().getId();
    }
    private long incident() { return incident(currentUser.userId()); }
    private GoldenSignals signals(int latency) {
        return new GoldenSignals(BigDecimal.valueOf(latency), BigDecimal.valueOf(20), BigDecimal.valueOf(2), BigDecimal.valueOf(70));
    }

    @Test void createsPendingThenSavesValidatedResultAndRealHistoricalContext() throws Exception {
        long id = incident();
        mvc.perform(get("/api/incidents/{id}/ai-analysis", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.completedAt").isEmpty());
        assertThat(CALLS.get()).isZero();
        worker.processNext();
        mvc.perform(get("/api/incidents/{id}/ai-analysis", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.evidence[0]").value("Latency 950 ms"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        var context = json.readTree(json.readTree(REQUEST.get()).path("input").asString());
        assertThat(context.path("triggeredRule").path("threshold").asInt()).isEqualTo(800);
        assertThat(context.path("triggeredRule").path("operator").asString()).isEqualTo(">=");
        assertThat(context.path("metricsAtDetection").path("latencyMs").asInt()).isEqualTo(950);
        assertThat(context.path("historyPointsAvailable").asInt()).isEqualTo(6);
        assertThat(context.path("historySampled").asBoolean()).isTrue();
        assertThat(context.path("metricHistory").size()).isEqualTo(3);
        assertThat(context.path("metricHistory").get(0).path("metrics").path("latencyMs").asInt()).isEqualTo(200);
        assertThat(context.path("metricHistory").get(2).path("metrics").path("latencyMs").asInt()).isEqualTo(950);
        assertThat(REQUEST.get()).doesNotContain("9999", "test-secret");
        assertThat(incidents.findById(id).orElseThrow().toResponse().status()).isEqualTo(IncidentStatus.OPEN);
        worker.processNext(); assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test void failureDoesNotChangeIncidentAndRetryTransitionsThroughPending() throws Exception {
        long id = incident(); HTTP_STATUS.set(429); worker.processNext();
        mvc.perform(get("/api/incidents/{id}/ai-analysis", id)).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("OpenAI returned HTTP 429."));
        assertThat(incidents.findById(id).orElseThrow().toResponse().status()).isEqualTo(IncidentStatus.OPEN);
        HTTP_STATUS.set(200);
        mvc.perform(post("/api/incidents/{id}/ai-analysis/retry", id)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.errorMessage").isEmpty())
                .andExpect(jsonPath("$.completedAt").isEmpty());
        service.retry(id); worker.processNext(); assertThat(CALLS.get()).isEqualTo(2);
        assertThat(service.get(id).status()).isEqualTo(AiDtos.Status.COMPLETED);
        // Monitoring still resolves incidents when AI failed.
        HTTP_STATUS.set(500); service.retry(id); worker.processNext();
        var detail = incidents.findById(id).orElseThrow().toResponse();
        var project = projects.findById(detail.projectId()).orElseThrow();
        for (int i = 1; i <= 3; i++) {
            store.recordSuccess(project.getId(), project.getMonitoringRevision(), detected.plusSeconds(i * 10), Optional.of(signals(100)));
        }
        assertThat(incidents.findById(id).orElseThrow().toResponse().status()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test void rejectsMalformedOutputAndPersistsInsufficientData() {
        long id = incident(); RESULT.set("not JSON test-secret"); worker.processNext();
        assertThat(service.get(id).status()).isEqualTo(AiDtos.Status.FAILED);
        assertThat(service.get(id).errorMessage()).doesNotContain("test-secret");
        RESULT.set("""
                {"status":"INSUFFICIENT_DATA","probableCause":null,"evidence":["Нет baseline"],"recommendations":["Собрать историю"]}
                """);
        service.retry(id); worker.processNext();
        assertThat(service.get(id).status()).isEqualTo(AiDtos.Status.INSUFFICIENT_DATA);
    }

    @Test void expiredClaimsAreRecoveredAndStaleResponsesCannotOverwriteNewAttempt() {
        long id = incident(); Instant now = Instant.now();
        var first = analyses.claim(now, now.plusSeconds(10)).orElseThrow();
        assertThat(analyses.claim(now, now.plusSeconds(10))).isEmpty();
        var second = analyses.claim(now.plusSeconds(11), now.plusSeconds(30)).orElseThrow();
        analyses.fail(first, "Stale result", now.plusSeconds(12));
        assertThat(service.get(id).status()).isEqualTo(AiDtos.Status.PENDING);
        analyses.fail(second, "Recovered attempt", now.plusSeconds(13));
        assertThat(service.get(id).errorMessage()).isEqualTo("Recovered attempt");
    }

    @Test void rollbackDoesNotLeaveQueuedAnalysisAndProjectDeletionCascades() {
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.execute(status -> { incident(); throw new IllegalStateException("rollback"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_analyses", Long.class)).isZero();
        assertThat(incidents.count()).isZero();
        long id = incident();
        var job = analyses.claim(Instant.now(), Instant.now().plusSeconds(60)).orElseThrow();
        projectService.delete(incidents.findById(id).orElseThrow().toResponse().projectId());
        analyses.fail(job, "Deleted", Instant.now());
        assertThat(analyses.find(id)).isEmpty();
    }

    @Test void simultaneousRetryAndClaimEachCoalesce() throws Exception {
        long id = incident(); worker.processNext();
        var go = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { go.await(); return service.retry(id); });
            var b = executor.submit(() -> { go.await(); return service.retry(id); });
            go.countDown();
            assertThat(a.get(5, TimeUnit.SECONDS).status()).isEqualTo(AiDtos.Status.PENDING);
            assertThat(b.get(5, TimeUnit.SECONDS).status()).isEqualTo(AiDtos.Status.PENDING);
            var first = executor.submit(() -> analyses.claim(Instant.now(), Instant.now().plusSeconds(60)));
            var second = executor.submit(() -> analyses.claim(Instant.now(), Instant.now().plusSeconds(60)));
            assertThat((first.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS).isPresent() ? 1 : 0)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_analyses WHERE incident_id=?", Long.class, id)).isEqualTo(1);
    }

    @Test void ownershipChecksProtectBothEndpointsAndLegacyIncidentsCanBeAnalyzed() throws Exception {
        long id = incident();
        long foreignUser = jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES ('ai-other@test','!disabled') RETURNING id", Long.class);
        long projectId = incidents.findById(id).orElseThrow().toResponse().projectId();
        jdbc.update("UPDATE projects SET user_id=? WHERE id=?", foreignUser, projectId);
        for (long denied : new long[]{id, Long.MAX_VALUE}) {
            mvc.perform(get("/api/incidents/{id}/ai-analysis", denied)).andExpect(status().isNotFound());
            mvc.perform(post("/api/incidents/{id}/ai-analysis/retry", denied)).andExpect(status().isNotFound());
        }
        jdbc.update("UPDATE projects SET user_id=? WHERE id=?", currentUser.userId(), projectId);
        jdbc.update("DELETE FROM ai_analyses WHERE incident_id=?", id);
        mvc.perform(get("/api/incidents/{id}/ai-analysis", id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/incidents/{id}/ai-analysis/retry", id)).andExpect(status().isAccepted());
        worker.processNext(); assertThat(service.get(id).status()).isEqualTo(AiDtos.Status.COMPLETED);
    }
}
