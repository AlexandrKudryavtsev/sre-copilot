package com.srecopilot.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "monitoring.request-timeout=700ms",
        "monitoring.max-response-bytes=1024",
        "spring.datasource.hikari.maximum-pool-size=1",
        "debug=false"
})
@AutoConfigureMockMvc
@Testcontainers
class ProjectWorkflowIT {

    private static final String METRICS = """
            # HELP requests_total Request count
            # TYPE requests_total counter
            requests_total{method="GET",status="200"} 42
            process_cpu_usage 0.25
            """;
    private static final AtomicInteger METRIC_REQUESTS = new AtomicInteger();
    private static HttpServer endpoint;
    private static ExecutorService executor;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProjectRepository repository;

    @BeforeAll
    static void startEndpoint() throws IOException {
        endpoint = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        endpoint.setExecutor(executor);
        endpoint.createContext("/metrics", exchange -> {
            METRIC_REQUESTS.incrementAndGet();
            respond(exchange, 200, "text/plain;version=0.0.4;charset=utf-8", METRICS);
        });
        endpoint.createContext("/unavailable", exchange -> respond(exchange, 503, "text/plain", "unavailable\n"));
        endpoint.createContext("/html", exchange -> respond(exchange, 200, "text/html", "<html>OK</html>\n"));
        endpoint.createContext("/invalid", exchange -> respond(exchange, 200, "text/plain", "requests_total 1\nbroken response\n"));
        endpoint.createContext("/empty", exchange -> respond(exchange, 200, "text/plain", ""));
        endpoint.createContext("/comments", exchange -> respond(exchange, 200, "text/plain", "# HELP requests_total No samples\n"));
        endpoint.createContext("/large", exchange -> respond(exchange, 200, "text/plain", "#" + "x".repeat(2048) + "\nsample 1\n"));
        endpoint.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/metrics");
            respond(exchange, 302, "text/plain", "redirect\n");
        });
        endpoint.createContext("/slow-headers", exchange -> {
            pause();
            respond(exchange, 200, "text/plain", METRICS);
        });
        endpoint.createContext("/slow-body", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, 0);
            try (exchange) {
                exchange.getResponseBody().write("requests_".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                pause();
                exchange.getResponseBody().write("total 1\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // The client is expected to cancel the request when its deadline expires.
            }
        });
        endpoint.start();
    }

    @AfterAll
    static void stopEndpoint() {
        if (endpoint != null) {
            endpoint.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @BeforeEach
    void clearProjects() {
        repository.deleteAll();
    }

    @Test
    void createsAndReadsPersistedProjectWithInitialState() throws Exception {
        int requestsBeforeCreation = METRIC_REQUESTS.get();
        long id = createProject(url("/metrics"), EndpointType.ACTUATOR);
        Project project = repository.findById(id).orElseThrow();
        assertThat(project.getName()).isEqualTo("Checkout API");
        assertThat(project.getUserId()).isEqualTo(jdbc.queryForObject(
                "SELECT id FROM users WHERE email = 'local@srecopilot.test'", Long.class));
        assertThat(project.getConnectionStatus()).isEqualTo(ConnectionStatus.UNKNOWN);
        assertThat(project.isMonitoringEnabled()).isFalse();
        assertThat(project.getCreatedAt()).isNotNull();
        assertThat(project.getUpdatedAt()).isNotNull();
        assertThat(METRIC_REQUESTS.get()).isEqualTo(requestsBeforeCreation);
        mvc.perform(get("/api/projects/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.connectionStatus").value("UNKNOWN"));
        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @ParameterizedTest
    @EnumSource(EndpointType.class)
    void connectsToBothSupportedEndpointTypes(EndpointType type) throws Exception {
        long id = createProject(url("/metrics"), type);
        checkStatus(id, ConnectionStatus.CONNECTED);
        assertThat(repository.findById(id).orElseThrow().getUpdatedAt())
                .isAfterOrEqualTo(repository.findById(id).orElseThrow().getCreatedAt());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM metric_snapshots", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/unavailable", "/html", "/invalid", "/empty", "/comments", "/large", "/redirect"})
    void marksUnusableEndpointsUnavailable(String path) throws Exception {
        int before = METRIC_REQUESTS.get();
        long id = createProject(url(path), EndpointType.PROMETHEUS);
        checkStatus(id, ConnectionStatus.UNAVAILABLE);
        if ("/redirect".equals(path)) {
            assertThat(METRIC_REQUESTS.get()).isEqualTo(before);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/slow-headers", "/slow-body"})
    void timesOutEvenWhenTheResponseBodyStalls(String path) throws Exception {
        long id = createProject(url(path), EndpointType.PROMETHEUS);
        mvc.perform(post("/api/projects/{id}/test-connection", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Endpoint request timed out."));
        assertThat(repository.findById(id).orElseThrow().getConnectionStatus())
                .isEqualTo(ConnectionStatus.UNAVAILABLE);
    }

    @Test
    void handlesConnectionRefusal() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        long id = createProject("http://127.0.0.1:" + port + "/metrics", EndpointType.PROMETHEUS);
        checkStatus(id, ConnectionStatus.UNAVAILABLE);
    }

    @Test
    void persistsRecoveryOnRepeatedChecks() throws Exception {
        long id = createProject(url("/metrics"), EndpointType.ACTUATOR);
        checkStatus(id, ConnectionStatus.CONNECTED);
        jdbc.update("UPDATE projects SET endpoint_url = ? WHERE id = ?", url("/unavailable"), id);
        checkStatus(id, ConnectionStatus.UNAVAILABLE);
        jdbc.update("UPDATE projects SET endpoint_url = ? WHERE id = ?", url("/metrics"), id);
        checkStatus(id, ConnectionStatus.CONNECTED);
    }

    @Test
    void releasesDatabaseConnectionDuringTheNetworkRequest() throws Exception {
        endpoint.createContext("/db-health", exchange -> {
            assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
            respond(exchange, 200, "text/plain", METRICS);
        });
        long id = createProject(url("/db-health"), EndpointType.ACTUATOR);
        checkStatus(id, ConnectionStatus.CONNECTED);
    }

    @Test
    void returnsNotFoundForMissingProject() throws Exception {
        mvc.perform(get("/api/projects/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/projects/{id}/test-connection", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyReadsProjectsOfTheCurrentUser() throws Exception {
        long otherUser = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash) VALUES ('other@srecopilot.test', '!disabled') RETURNING id
                """, Long.class);
        long id = repository.save(new Project(otherUser, "Other project", null, url("/metrics"), EndpointType.PROMETHEUS)).getId();
        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/projects/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/projects/{id}/test-connection", id)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://localhost/metrics", "http:///metrics", "http://localhost:70000/metrics",
            "http://user:password@localhost/metrics", "http://localhost/metrics#fragment", "http://local host/metrics"
    })
    void rejectsInvalidEndpointUrlsWithoutPersistingProject(String url) throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(url, EndpointType.PROMETHEUS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endpointUrl"));
        assertThat(repository.count()).isZero();
    }

    private long createProject(String url, EndpointType type) throws Exception {
        String location = mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(url, type)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Checkout API"))
                .andExpect(jsonPath("$.monitoringEnabled").value(false))
                .andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private void checkStatus(long id, ConnectionStatus expected) throws Exception {
        mvc.perform(post("/api/projects/{id}/test-connection", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value(expected.name()));
        mvc.perform(get("/api/projects/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value(expected.name()));
        assertThat(repository.findById(id).orElseThrow().getConnectionStatus()).isEqualTo(expected);
    }

    private static String request(String url, EndpointType type) {
        return """
                {"name":"  Checkout API  ","description":"Test service","endpointUrl":"%s","endpointType":"%s"}
                """.formatted(url, type);
    }

    private static String url(String path) {
        return "http://127.0.0.1:" + endpoint.getAddress().getPort() + path;
    }

    private static void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (exchange) {
            exchange.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // A timed-out or oversized response is cancelled by the client.
        }
    }

    private static void pause() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
