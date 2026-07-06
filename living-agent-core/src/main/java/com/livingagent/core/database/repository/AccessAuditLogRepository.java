package com.livingagent.core.database.repository;

import com.livingagent.core.security.AccessAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AccessAuditLogRepository extends JpaRepository<AccessAuditLog, String> {

    List<AccessAuditLog> findByEmployeeIdOrderByTimestampDesc(String employeeId, Pageable pageable);

    @Query("SELECT a FROM AccessAuditLog a WHERE a.employeeId = :employeeId ORDER BY a.timestamp DESC")
    List<AccessAuditLog> findRecentByEmployeeId(@Param("employeeId") String employeeId, Pageable pageable);

    long countByEmployeeId(String employeeId);

    void deleteByTimestampBefore(long cutoffTime);

    @Query("SELECT a FROM AccessAuditLog a ORDER BY a.timestamp DESC")
    List<AccessAuditLog> findRecentAll(Pageable pageable);

    @Query("SELECT a FROM AccessAuditLog a WHERE a.employeeId = :employeeId AND a.timestamp >= :fromMillis AND a.timestamp <= :toMillis ORDER BY a.timestamp DESC")
    List<AccessAuditLog> findByEmployeeIdAndDetectedAtBetween(
        @Param("employeeId") String employeeId,
        @Param("fromMillis") long fromMillis,
        @Param("toMillis") long toMillis);
}