package com.srecopilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.junit.jupiter.api.Test;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ScaffoldIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void startsWithPostgresAndAppliesFlywayMigration() throws Exception {
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        Set<String> tables = Set.copyOf(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class));
        assertThat(tables).contains("users", "projects", "metric_snapshots",
                "monitoring_rules", "monitoring_rule_states", "incidents");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class))
                .isEqualTo(1);
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void businessApiReturnsAnExplicitProblemResponse() throws Exception {
        mvc.perform(get("/api/projects"))
                .andExpect(status().isNotImplemented())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.title").value("Feature not implemented"))
                .andExpect(jsonPath("$.instance").value("/api/projects"));
        mvc.perform(get("/api/projects/1/metrics/current"))
                .andExpect(status().isNotImplemented());
        mvc.perform(get("/api/projects/1/rules"))
                .andExpect(status().isNotImplemented());
        mvc.perform(get("/api/projects/1/incidents").param("status", "OPEN"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void rejectsInvalidRequestBeforeEnteringTheService() throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"", "endpointUrl":"not-a-url", "endpointType":"PROMETHEUS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors").isArray());
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Checkout API", "endpointUrl":"http://localhost:8081/actuator/prometheus",
                                 "endpointType":"ACTUATOR"}
                                """))
                .andExpect(status().isNotImplemented());
        mvc.perform(get("/api/projects/0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/1/metrics/history").param("from", "invalid").param("to", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesOpenApiAndAllowsFrontendPreflight() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SRE Copilot — Monitoring Core"))
                .andExpect(jsonPath("$.paths['/api/projects']").exists())
                .andExpect(jsonPath("$.paths['/api/projects/{projectId}/rules']").exists());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mvc.perform(options("/api/projects")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
