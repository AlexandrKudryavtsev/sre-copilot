package com.srecopilot.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.srecopilot.project.ConnectionStatus;
import com.srecopilot.project.EndpointType;
import com.srecopilot.project.Project;
import com.srecopilot.project.ProjectRepository;
import com.srecopilot.project.ProjectService;
import com.srecopilot.rule.RuleDtos.RuleRequest;
import com.srecopilot.user.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"monitoring.scheduler-enabled=false", "debug=false"})
@AutoConfigureMockMvc
@Import(RuleWorkflowIT.TimeConfig.class)
@Testcontainers
class RuleWorkflowIT {

    private static final Instant START = Instant.parse("2026-10-06T12:00:00Z");

    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired ProjectService projectService;
    @Autowired CurrentUserProvider currentUser;
    @Autowired MonitoringRuleRepository rules;
    @Autowired MonitoringRuleStateRepository states;
    @Autowired RuleService ruleService;
    @Autowired RuleEngine engine;
    @Autowired IncidentRepository incidents;
    @Autowired MetricSnapshotRepository snapshots;
    @Autowired MetricCollectionStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfig {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(START);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(time.get(), zone); }
        @Override public Instant instant() { return time.get(); }
        void advance(int seconds) { time.updateAndGet(value -> value.plusSeconds(seconds)); }
    }

    @BeforeEach
    void reset() {
        projects.deleteAll();
        clock.time.set(START);
    }

    @Test
    void opensOnceAfterConsecutiveViolationsResolvesAfterConsecutiveRecoveryAndCanReopen() throws Exception {
        long project = project();
        long rule = create(project, 3);
        state(project, rule, "HEALTHY", 0, 0);
        sample(project, 850);
        state(project, rule, "VIOLATING", 1, 0);
        sample(project, 700);
        state(project, rule, "HEALTHY", 0, 0);
        sample(project, 900);
        sample(project, 910);
        assertThat(incidents.count()).isZero();
        sample(project, 950);
        state(project, rule, "INCIDENT_OPEN", 3, 0);
        long first = states.findById(rule).orElseThrow().getActiveIncidentId();
        mvc.perform(get("/api/incidents/{id}", first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("High latency"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.ruleThreshold").value(800))
                .andExpect(jsonPath("$.requiredConsecutiveViolations").value(3))
                .andExpect(jsonPath("$.triggerValue").value(950))
                .andExpect(jsonPath("$.latencyAtDetection").value(950))
                .andExpect(jsonPath("$.trafficAtDetection").value(20))
                .andExpect(jsonPath("$.errorsAtDetection").value(2))
                .andExpect(jsonPath("$.saturationAtDetection").value(70));
        sample(project, 1000);
        assertThat(incidents.count()).isEqualTo(1);
        sample(project, 700);
        sample(project, 600);
        state(project, rule, "INCIDENT_OPEN", 0, 2);
        sample(project, 950);
        state(project, rule, "INCIDENT_OPEN", 0, 0);
        sample(project, 700);
        sample(project, 600);
        sample(project, 500);
        state(project, rule, "HEALTHY", 0, 0);
        mvc.perform(get("/api/incidents/{id}", first))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").value(clock.instant().toString()));
        sample(project, 900);
        sample(project, 900);
        sample(project, 900);
        assertThat(incidents.count()).isEqualTo(2);
        mvc.perform(get("/api/projects/{id}/incidents", project).param("status", "OPEN").param("severity", "CRITICAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/projects/{id}/incidents", project).param("status", "RESOLVED"))
                .andExpect(jsonPath("$[0].id").value(first));
        mvc.perform(get("/api/projects/{id}/incidents", project).param("severity", "WARNING"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/projects/{id}/incidents", project))
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].id").value(first));
    }

    @Test
    void honorsCheckIntervalAndNeverCountsTheSameOrOlderSnapshotTwice() throws Exception {
        long project = project();
        long rule = create(project, 2);
        sample(project, 900);
        MetricSnapshot first = snapshots.findFirstByProjectIdOrderByTimestampDescIdDesc(project).orElseThrow();
        engine.evaluate(first);
        sample(project, 700, 20, 2, 70, 1);
        state(project, rule, "VIOLATING", 1, 0);
        sample(project, 900, 20, 2, 70, 9);
        assertThat(incidents.count()).isEqualTo(1);
        engine.evaluate(first);
        state(project, rule, "INCIDENT_OPEN", 2, 0);
        sample(project, 700, 20, 2, 70, 1);
        state(project, rule, "INCIDENT_OPEN", 2, 0);
        sample(project, 700, 20, 2, 70, 9);
        state(project, rule, "INCIDENT_OPEN", 0, 1);
    }

    @Test
    void editingAnOpenRuleKeepsHistoricalConditionAndUsesItForRecovery() throws Exception {
        long project = project();
        long rule = create(project, 1);
        sample(project, 900);
        long original = states.findById(rule).orElseThrow().getActiveIncidentId();
        String edited = request(4).replace("\"LATENCY\"", "\"ERRORS\"")
                .replace("\"threshold\":800", "\"threshold\":5").replace("\"CRITICAL\"", "\"WARNING\"");
        mvc.perform(put("/api/projects/{p}/rules/{r}", project, rule).contentType(MediaType.APPLICATION_JSON).content(edited))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metric").value("ERRORS"));
        mvc.perform(get("/api/incidents/{id}", original)).andExpect(jsonPath("$.ruleMetric").value("LATENCY"))
                .andExpect(jsonPath("$.ruleThreshold").value(800)).andExpect(jsonPath("$.severity").value("CRITICAL"));
        sample(project, 900, 20, 0, 70, 10);
        assertThat(incidents.findById(original).orElseThrow().toResponse().status()).isEqualTo(IncidentStatus.OPEN);
        for (int i = 0; i < 3; i++) { sample(project, 700, 20, 25, 70, 10); }
        assertThat(incidents.findById(original).orElseThrow().toResponse().status()).isEqualTo(IncidentStatus.RESOLVED);
        for (int i = 0; i < 4; i++) { sample(project, 100, 20, 20, 70, 10); }
        var next = incidents.findByRuleIdAndStatus(rule, IncidentStatus.OPEN).orElseThrow().toResponse();
        assertThat(next.ruleMetric()).isEqualTo(MetricType.ERRORS);
        assertThat(next.severity()).isEqualTo(Severity.WARNING);
        assertThat(next.requiredConsecutiveViolations()).isEqualTo(4);
        assertThat(next.ruleThreshold()).isEqualByComparingTo("5");
    }

    @Test
    void disabledRulesPauseOpenIncidentsAndResetProgressWhenEnabledAgain() throws Exception {
        long project = project();
        long rule = create(project, 2);
        sample(project, 900);
        toggle(project, rule, false);
        sample(project, 900);
        state(project, rule, "HEALTHY", 0, 0);
        toggle(project, rule, true);
        sample(project, 900);
        assertThat(incidents.count()).isZero();
        sample(project, 900);
        long id = states.findById(rule).orElseThrow().getActiveIncidentId();
        toggle(project, rule, false);
        for (int i = 0; i < 3; i++) { sample(project, 700); }
        assertThat(incidents.findById(id).orElseThrow().toResponse().status()).isEqualTo(IncidentStatus.OPEN);
        toggle(project, rule, true);
        for (int i = 0; i < 3; i++) { sample(project, 700); }
        assertThat(incidents.findById(id).orElseThrow().toResponse().status()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void collectionFailuresBaselineResetsAndMonitoringPausesBreakConsecutiveChecks() throws Exception {
        long project = project();
        long rule = create(project, 2);
        sample(project, 900);
        var monitored = projects.findById(project).orElseThrow();
        clock.advance(10);
        assertThat(store.recordFailure(project, monitored.getMonitoringRevision(), clock.instant())).isTrue();
        state(project, rule, "HEALTHY", 0, 0);
        sample(project, 900);
        clock.advance(10);
        store.recordSuccess(project, monitored.getMonitoringRevision(), clock.instant(), Optional.empty());
        state(project, rule, "HEALTHY", 0, 0);
        sample(project, 900);
        projectService.setMonitoringEnabled(project, false);
        state(project, rule, "HEALTHY", 0, 0);
        assertThat(store.recordSuccess(project, monitored.getMonitoringRevision(), clock.instant(), Optional.of(signals(900, 20, 2, 70)))).isFalse();
        projectService.setMonitoringEnabled(project, true);
        sample(project, 900);
        assertThat(incidents.count()).isZero();
        sample(project, 900);
        sample(project, 700);
        state(project, rule, "INCIDENT_OPEN", 0, 1);
        monitored = projects.findById(project).orElseThrow();
        store.recordFailure(project, monitored.getMonitoringRevision(), clock.instant());
        state(project, rule, "INCIDENT_OPEN", 0, 0);
        assertThat(incidents.findByRuleIdAndStatus(rule, IncidentStatus.OPEN)).isPresent();
    }

    @Test
    void idleTrafficDoesNotRecoverLatencyIncidentsButCanTriggerTrafficRules() throws Exception {
        long project = project();
        long latencyRule = create(project, 1);
        sample(project, 900);
        long incident = states.findById(latencyRule).orElseThrow().getActiveIncidentId();
        for (int i = 0; i < 3; i++) { sample(project, 0, 0, 0, 20, 10); }
        assertThat(incidents.findById(incident).orElseThrow().toResponse().status()).isEqualTo(IncidentStatus.OPEN);
        String traffic = request(1).replace("\"LATENCY\"", "\"TRAFFIC\"")
                .replace("\"GREATER_OR_EQUAL\"", "\"LESS_THAN\"").replace("\"threshold\":800", "\"threshold\":1");
        long trafficRule = create(project, traffic);
        sample(project, 0, 0, 0, 20, 10);
        assertThat(incidents.findByRuleIdAndStatus(trafficRule, IncidentStatus.OPEN)).isPresent();
    }

    @Test
    void deletingAnOpenRuleConflictsAndDeletingARecoveredRulePreservesIncidentHistory() throws Exception {
        long project = project();
        long rule = create(project, 1);
        sample(project, 900);
        long incident = states.findById(rule).orElseThrow().getActiveIncidentId();
        mvc.perform(delete("/api/projects/{p}/rules/{r}", project, rule)).andExpect(status().isConflict());
        for (int i = 0; i < 3; i++) { sample(project, 700); }
        mvc.perform(delete("/api/projects/{p}/rules/{r}", project, rule)).andExpect(status().isNoContent());
        assertThat(states.findById(rule)).isEmpty();
        mvc.perform(get("/api/projects/{p}/rules/{r}", project, rule)).andExpect(status().isNotFound());
        mvc.perform(get("/api/incidents/{id}", incident)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").isEmpty()).andExpect(jsonPath("$.ruleThreshold").value(800));
    }

    @Test
    void concurrentEvaluationCreatesOnlyOneIncidentAndCountsASnapshotOnlyOnce() throws Exception {
        long project = project();
        long rule = create(project, 1);
        clock.advance(10);
        MetricSnapshot snapshot = snapshots.save(new MetricSnapshot(project, clock.instant(), signals(900, 20, 2, 70)));
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> { go.await(); engine.evaluate(snapshot); return null; });
            var second = executor.submit(() -> { go.await(); engine.evaluate(snapshot); return null; });
            go.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        assertThat(incidents.count()).isEqualTo(1);
        state(project, rule, "INCIDENT_OPEN", 1, 0);
    }

    @Test
    void incidentInsertFailureRollsBackSnapshotStatusAndRuleStateTogether() throws Exception {
        long project = project();
        long rule = create(project, 1);
        jdbc.execute("ALTER TABLE incidents ADD CONSTRAINT reject_test_incident CHECK (false) NOT VALID");
        clock.advance(10);
        long revision = projects.findById(project).orElseThrow().getMonitoringRevision();
        try {
            assertThatThrownBy(() -> store.recordSuccess(project, revision, clock.instant(), Optional.of(signals(900, 20, 2, 70))))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(snapshots.count()).isZero();
            assertThat(incidents.count()).isZero();
            assertThat(projects.findById(project).orElseThrow().getConnectionStatus()).isEqualTo(ConnectionStatus.UNKNOWN);
            state(project, rule, "HEALTHY", 0, 0);
            assertThat(states.findById(rule).orElseThrow().getLastCheckedAt()).isNull();
        } finally {
            jdbc.execute("ALTER TABLE incidents DROP CONSTRAINT reject_test_incident");
        }
    }

    @Test
    void readingRuntimeStateWhileDeletingARuleNeverReturnsAServerError() throws Exception {
        long project = project();
        long rule = create(project, 1);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reader = executor.submit(() -> {
                go.await();
                for (int i = 0; i < 20; i++) {
                    mvc.perform(get("/api/projects/{p}/rules", project)).andExpect(status().isOk());
                    int code = mvc.perform(get("/api/projects/{p}/rules/{r}", project, rule))
                            .andReturn().getResponse().getStatus();
                    assertThat(code).isIn(200, 404);
                }
                return null;
            });
            var writer = executor.submit(() -> {
                go.await();
                ruleService.delete(project, rule);
                return null;
            });
            go.countDown();
            reader.get(10, TimeUnit.SECONDS);
            writer.get(10, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @EnumSource(MetricType.class)
    void usesTheSelectedMetricAndPreservesAllFourDetectionValues(MetricType metric) throws Exception {
        long project = project();
        BigDecimal threshold = switch (metric) {
            case LATENCY -> new BigDecimal("900"); case TRAFFIC -> new BigDecimal("20");
            case ERRORS -> new BigDecimal("2"); case SATURATION -> new BigDecimal("70");
        };
        var rule = ruleService.create(project, new RuleRequest("Selected", metric, RuleOperator.GREATER_OR_EQUAL,
                threshold, Severity.WARNING, 10, 1, true));
        sample(project, 900);
        var incident = incidents.findByRuleIdAndStatus(rule.id(), IncidentStatus.OPEN).orElseThrow().toResponse();
        assertThat(incident.triggerMetric()).isEqualTo(metric);
        assertThat(incident.triggerValue()).isEqualByComparingTo(threshold);
        assertThat(incident.saturationAtDetection()).isEqualByComparingTo("70");
    }

    @Test
    void ownershipAndProjectRuleScopingProtectEveryEndpoint() throws Exception {
        long own = project();
        long rule = create(own, 1);
        long another = project();
        long user = jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES ('other-rule@test','!disabled') RETURNING id", Long.class);
        long foreign = projects.save(new Project(user, "Foreign", null, "http://localhost/metrics", EndpointType.PROMETHEUS)).getId();
        for (long project : new long[]{foreign, Long.MAX_VALUE}) {
            mvc.perform(get("/api/projects/{p}/rules", project)).andExpect(status().isNotFound());
            mvc.perform(post("/api/projects/{p}/rules", project).contentType(MediaType.APPLICATION_JSON).content(request(1)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/projects/{p}/rules/{r}", project, rule)).andExpect(status().isNotFound());
            mvc.perform(put("/api/projects/{p}/rules/{r}", project, rule).contentType(MediaType.APPLICATION_JSON).content(request(1)))
                    .andExpect(status().isNotFound());
            mvc.perform(delete("/api/projects/{p}/rules/{r}", project, rule)).andExpect(status().isNotFound());
            mvc.perform(post("/api/projects/{p}/rules/{r}/enable", project, rule)).andExpect(status().isNotFound());
            mvc.perform(post("/api/projects/{p}/rules/{r}/disable", project, rule)).andExpect(status().isNotFound());
            mvc.perform(get("/api/projects/{p}/incidents", project)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/projects/{p}/rules/{r}", another, rule)).andExpect(status().isNotFound());
        sample(own, 900);
        long ownIncident = states.findById(rule).orElseThrow().getActiveIncidentId();
        var foreignRule = rules.save(new MonitoringRule(foreign,
                new RuleRequest("Foreign", MetricType.LATENCY, RuleOperator.GREATER_OR_EQUAL, new BigDecimal("800"), Severity.CRITICAL, 1, 1, true), clock.instant()));
        states.save(new MonitoringRuleState(foreignRule.getId()));
        jdbc.update("UPDATE projects SET monitoring_enabled=true WHERE id=?", foreign);
        sample(foreign, 900);
        long foreignIncident = states.findById(foreignRule.getId()).orElseThrow().getActiveIncidentId();
        mvc.perform(get("/api/incidents/{id}", foreignIncident)).andExpect(status().isNotFound());
        mvc.perform(get("/api/incidents/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/api/incidents/{id}", ownIncident)).andExpect(status().isOk());
        mvc.perform(get("/api/projects/{p}/incidents", own).param("status", "BAD")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"threshold,-1", "checkIntervalSeconds,0", "consecutiveViolations,0", "enabled,null", "metric,null", "operator,\"UNKNOWN\""})
    void rejectsInvalidRulesWithoutLeavingPartialRuleOrState(String field, String value) throws Exception {
        long project = project();
        String invalid = request(3).replaceAll("\"" + field + "\":(?:\"[^\"]*\"|[0-9]+|true)", "\"" + field + "\":" + value);
        mvc.perform(post("/api/projects/{p}/rules", project).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        assertThat(rules.count()).isZero();
        assertThat(states.count()).isZero();
    }

    private long project() {
        long id = projects.save(new Project(currentUser.userId(), "Monitored", null, "http://localhost/metrics", EndpointType.PROMETHEUS)).getId();
        projectService.setMonitoringEnabled(id, true);
        return id;
    }

    private long create(long project, int consecutive) throws Exception { return create(project, request(consecutive)); }

    private long create(long project, String body) throws Exception {
        String location = mvc.perform(post("/api/projects/{p}/rules", project).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("High latency"))
                .andReturn().getResponse().getHeader("Location");
        long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
        mvc.perform(get("/api/projects/{p}/rules", project)).andExpect(status().isOk());
        return id;
    }

    private void toggle(long project, long rule, boolean enabled) throws Exception {
        mvc.perform(post("/api/projects/{p}/rules/{r}/{action}", project, rule, enabled ? "enable" : "disable"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(enabled));
    }

    private void sample(long project, double latency) { sample(project, latency, 20, 2, 70, 10); }

    private void sample(long project, double latency, double traffic, double errors, double saturation, int seconds) {
        clock.advance(seconds);
        var monitored = projects.findById(project).orElseThrow();
        assertThat(store.recordSuccess(project, monitored.getMonitoringRevision(), clock.instant(), Optional.of(signals(latency, traffic, errors, saturation)))).isTrue();
    }

    private static GoldenSignals signals(double latency, double traffic, double errors, double saturation) {
        return new GoldenSignals(BigDecimal.valueOf(latency), BigDecimal.valueOf(traffic), BigDecimal.valueOf(errors), BigDecimal.valueOf(saturation));
    }

    private void state(long project, long rule, String expected, int violations, int recoveries) throws Exception {
        mvc.perform(get("/api/projects/{p}/rules/{r}", project, rule)).andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeState.state").value(expected))
                .andExpect(jsonPath("$.runtimeState.consecutiveViolations").value(violations))
                .andExpect(jsonPath("$.runtimeState.consecutiveRecoveries").value(recoveries));
    }

    private static String request(int consecutive) {
        return """
                {"name":"  High latency  ","metric":"LATENCY","operator":"GREATER_OR_EQUAL","threshold":800,
                 "severity":"CRITICAL","checkIntervalSeconds":10,"consecutiveViolations":%d,"enabled":true}
                """.formatted(consecutive);
    }
}
