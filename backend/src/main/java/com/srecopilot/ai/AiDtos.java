package com.srecopilot.ai;

import java.time.Instant;
import java.util.List;

public final class AiDtos {
    private AiDtos() { }
    public enum Status { PENDING, COMPLETED, FAILED, INSUFFICIENT_DATA }
    public record Result(Status status, String probableCause, List<String> evidence, List<String> recommendations) { }
    public record AnalysisResponse(long id, long incidentId, Status status, String probableCause,
            List<String> evidence, List<String> recommendations, String errorMessage,
            Instant createdAt, Instant completedAt) { }
}
