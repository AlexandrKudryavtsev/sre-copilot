package com.srecopilot.rule;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitoringRuleStateRepository extends JpaRepository<MonitoringRuleState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM MonitoringRuleState s WHERE s.ruleId = :ruleId")
    Optional<MonitoringRuleState> findForUpdate(@Param("ruleId") long ruleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM MonitoringRuleState s, MonitoringRule r
            WHERE s.ruleId = r.id AND r.projectId = :projectId ORDER BY s.ruleId
            """)
    List<MonitoringRuleState> findAllForProjectForUpdate(@Param("projectId") long projectId);
}
