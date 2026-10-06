package com.srecopilot.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.srecopilot.metric.MetricSnapshotRepository;
import com.srecopilot.project.EndpointType;
import com.srecopilot.project.Project;
import com.srecopilot.project.ProjectRepository;
import com.srecopilot.project.ProjectService;
import com.srecopilot.user.CurrentUserProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"monitoring.collection-interval=1s", "debug=false"})
@Testcontainers
class MonitoringSchedulerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired ProjectRepository projects;
    @Autowired ProjectService service;
    @Autowired CurrentUserProvider currentUser;
    @Autowired MetricSnapshotRepository snapshots;

    @Test
    void schedulerAutomaticallyCollectsAnEnabledProject() throws Exception {
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
        long id = projects.save(new Project(currentUser.userId(), "Scheduled", null,
                "http://127.0.0.1:" + endpoint.getAddress().getPort() + "/metrics", EndpointType.PROMETHEUS)).getId();
        try {
            service.setMonitoringEnabled(id, true);
            await().atMost(Duration.ofSeconds(10)).until(() -> snapshots.count() > 0);
            var snapshot = snapshots.findFirstByProjectIdOrderByTimestampDescIdDesc(id).orElseThrow().toResponse();
            assertThat(requests.get()).isGreaterThanOrEqualTo(2);
            assertThat(snapshot.latency()).isEqualByComparingTo("100");
            assertThat(snapshot.traffic()).isPositive();
            assertThat(snapshot.saturation()).isEqualByComparingTo("50");
        } finally {
            service.setMonitoringEnabled(id, false);
            endpoint.stop(0);
        }
    }
}
