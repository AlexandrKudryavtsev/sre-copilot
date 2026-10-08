package com.srecopilot.project;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Transactional(readOnly = true)
    List<Project> findAllByUserIdOrderByCreatedAtDescIdDesc(long userId);

    @Transactional(readOnly = true)
    Optional<Project> findByIdAndUserId(long id, long userId);

    @Transactional(readOnly = true)
    List<Project> findAllByMonitoringEnabledTrueOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.userId = :userId")
    Optional<Project> findOwnedByIdForUpdate(@Param("id") long id, @Param("userId") long userId);

    @Modifying
    @Transactional
    @Query("""
            UPDATE Project p SET p.connectionStatus = :status, p.updatedAt = :checkedAt
            WHERE p.id = :id AND p.userId = :userId AND p.monitoringRevision = :revision
                AND p.endpointUrl = :endpointUrl AND p.endpointType = :endpointType
            """)
    int updateConnectionStatus(@Param("id") long id, @Param("userId") long userId,
            @Param("revision") long revision, @Param("endpointUrl") String endpointUrl,
            @Param("endpointType") EndpointType endpointType,
            @Param("status") ConnectionStatus status, @Param("checkedAt") Instant checkedAt);
}
