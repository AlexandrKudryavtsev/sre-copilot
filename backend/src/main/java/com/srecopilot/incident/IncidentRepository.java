package com.srecopilot.incident;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByRuleIdAndStatus(long ruleId, IncidentStatus status);

    @Query("""
            SELECT i FROM Incident i WHERE i.projectId = :projectId
            AND (:status IS NULL OR i.status = :status)
            AND (:severity IS NULL OR i.severity = :severity)
            ORDER BY i.startedAt DESC, i.id DESC
            """)
    List<Incident> findForProject(@Param("projectId") long projectId,
            @Param("status") IncidentStatus status, @Param("severity") Severity severity);

    @Query("""
            SELECT i FROM Incident i, Project p
            WHERE i.id = :id AND i.projectId = p.id AND p.userId = :userId
            """)
    Optional<Incident> findOwnedById(@Param("id") long id, @Param("userId") long userId);
}
