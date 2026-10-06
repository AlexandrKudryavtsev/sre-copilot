package com.srecopilot.metric;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {

    Optional<MetricSnapshot> findFirstByProjectIdOrderByTimestampDescIdDesc(long projectId);

    List<MetricSnapshot> findAllByProjectIdAndTimestampBetweenOrderByTimestampAscIdAsc(
            long projectId, Instant from, Instant to);
}
