package com.srecopilot.rule;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringRuleRepository extends JpaRepository<MonitoringRule, Long> {

    List<MonitoringRule> findAllByProjectIdOrderByIdAsc(long projectId);
    List<MonitoringRule> findAllByProjectIdAndEnabledTrueOrderByIdAsc(long projectId);
    Optional<MonitoringRule> findByIdAndProjectId(long id, long projectId);
}
