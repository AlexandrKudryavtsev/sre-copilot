package com.srecopilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.srecopilot.incident.IncidentRepository;
import com.srecopilot.incident.IncidentStatus;
import com.srecopilot.incident.Severity;
import com.srecopilot.metric.MetricSnapshotRepository;
import com.srecopilot.metric.MetricType;
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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"monitoring.collection-interval=1s", "ai.scheduler-enabled=true", "ai.poll-interval=1s", "debug=false"})
@Testcontainers
class AiSchedulerIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired ProjectRepository projects;
    @Autowired ProjectService projectService;
    @Autowired CurrentUserProvider currentUser;
    @Autowired RuleService rules;
    @Autowired IncidentRepository incidents;
    @Autowired MetricSnapshotRepository snapshots;
    @Autowired AiAnalysisService analyses;
    @MockitoBean OpenAiClient openAi;

    @Test void automaticallyAnalyzesCommittedIncidentWhileMonitoringContinuesDuringSlowAnalysis() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(openAi.analyze(any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) { throw new AiAnalysisException("test timeout"); }
            return new AiDtos.Result(AiDtos.Status.COMPLETED, "Возможно, нагрузка CPU", List.of("CPU 50%"), List.of("Проверить CPU"));
        });
        var endpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new AtomicInteger();
        endpoint.createContext("/metrics", exchange -> {
            int count = requests.incrementAndGet() * 10;
            byte[] body = ("http_server_requests_seconds_count{status=\"200\"} " + count + "\n"
                    + "http_server_requests_seconds_sum{status=\"200\"} " + count * 0.1 + "\n"
                    + "process_cpu_usage 0.5\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (exchange) { exchange.getResponseBody().write(body); }
        });
        endpoint.start();
        long project = projects.save(new Project(currentUser.userId(), "Scheduler", null,
                "http://127.0.0.1:" + endpoint.getAddress().getPort() + "/metrics", EndpointType.PROMETHEUS)).getId();
        try {
            var rule = rules.create(project, new RuleRequest("CPU", MetricType.SATURATION, RuleOperator.GREATER_OR_EQUAL,
                    BigDecimal.valueOf(40), Severity.WARNING, 1, 1, true));
            projectService.setMonitoringEnabled(project, true);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            long id = incidents.findByRuleIdAndStatus(rule.id(), IncidentStatus.OPEN).orElseThrow().getId();
            assertThat(analyses.get(id).status()).isEqualTo(AiDtos.Status.PENDING);
            long before = snapshots.count();
            await().atMost(Duration.ofSeconds(5)).until(() -> snapshots.count() > before);
            assertThat(analyses.get(id).status()).isEqualTo(AiDtos.Status.PENDING);
            release.countDown();
            await().atMost(Duration.ofSeconds(5)).until(() -> analyses.get(id).status() == AiDtos.Status.COMPLETED);
            assertThat(incidents.count()).isEqualTo(1);
        } finally {
            release.countDown(); projectService.setMonitoringEnabled(project, false); endpoint.stop(0);
        }
    }
}
