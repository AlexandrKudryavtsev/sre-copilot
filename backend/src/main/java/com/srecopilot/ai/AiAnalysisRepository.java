package com.srecopilot.ai;

import com.srecopilot.ai.AiDtos.AnalysisResponse;
import com.srecopilot.ai.AiDtos.Result;
import com.srecopilot.ai.AiDtos.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AiAnalysisRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AiAnalysisRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Optional<AnalysisResponse> find(long incidentId) {
        return jdbc.query("SELECT * FROM ai_analyses WHERE incident_id = ?", this::response, incidentId).stream().findFirst();
    }

    public void retry(long incidentId) {
        // Concurrent retries coalesce; a running attempt cannot be replaced by another request.
        jdbc.update("""
                INSERT INTO ai_analyses(incident_id) VALUES (?)
                ON CONFLICT (incident_id) DO UPDATE SET status='PENDING', probable_cause=NULL,
                    evidence='[]', recommendations='[]', error_message=NULL, completed_at=NULL,
                    attempt_id=NULL, lease_until=NULL
                WHERE ai_analyses.status <> 'PENDING'
                """, incidentId);
    }

    public record Job(long incidentId, UUID attemptId) { }

    @Transactional
    public Optional<Job> claim(Instant now, Instant leaseUntil) {
        List<Long> ids = jdbc.query("""
                SELECT incident_id FROM ai_analyses WHERE status='PENDING'
                    AND (lease_until IS NULL OR lease_until < ?)
                ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getLong(1), Timestamp.from(now));
        if (ids.isEmpty()) { return Optional.empty(); }
        UUID attempt = UUID.randomUUID();
        jdbc.update("UPDATE ai_analyses SET attempt_id=?, lease_until=? WHERE incident_id=?",
                attempt, Timestamp.from(leaseUntil), ids.getFirst());
        return Optional.of(new Job(ids.getFirst(), attempt));
    }

    public void complete(Job job, Result result, Instant now) {
        jdbc.update("""
                UPDATE ai_analyses SET status=?, probable_cause=?, evidence=?::jsonb,
                    recommendations=?::jsonb, error_message=NULL, completed_at=?, lease_until=NULL
                WHERE incident_id=? AND attempt_id=? AND status='PENDING'
                """, result.status().name(), result.probableCause(), json.writeValueAsString(result.evidence()),
                json.writeValueAsString(result.recommendations()), Timestamp.from(now), job.incidentId(), job.attemptId());
    }

    public void fail(Job job, String message, Instant now) {
        jdbc.update("""
                UPDATE ai_analyses SET status='FAILED', error_message=?, completed_at=?, lease_until=NULL
                WHERE incident_id=? AND attempt_id=? AND status='PENDING'
                """, message, Timestamp.from(now), job.incidentId(), job.attemptId());
    }

    private AnalysisResponse response(ResultSet rs, int row) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        return new AnalysisResponse(rs.getLong("id"), rs.getLong("incident_id"), Status.valueOf(rs.getString("status")),
                rs.getString("probable_cause"), json.readValue(rs.getString("evidence"), new TypeReference<List<String>>() { }),
                json.readValue(rs.getString("recommendations"), new TypeReference<List<String>>() { }),
                rs.getString("error_message"), rs.getTimestamp("created_at").toInstant(),
                completed == null ? null : completed.toInstant());
    }
}
