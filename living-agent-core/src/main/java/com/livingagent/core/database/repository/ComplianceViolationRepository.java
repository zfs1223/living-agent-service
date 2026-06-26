package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ComplianceViolationEntity;
import com.livingagent.core.database.entity.ComplianceViolationEntity.ViolationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComplianceViolationRepository extends JpaRepository<ComplianceViolationEntity, String> {

    List<ComplianceViolationEntity> findByEmployeeIdOrderByDetectedAtDesc(String employeeId);

    List<ComplianceViolationEntity> findByStatusOrderByDetectedAtDesc(ViolationStatus status);

    List<ComplianceViolationEntity> findByRuleIdOrderByDetectedAtDesc(String ruleId);

    @Query("SELECT v FROM ComplianceViolationEntity v WHERE v.status NOT IN :resolvedStatuses ORDER BY v.severity DESC, v.detectedAt DESC")
    List<ComplianceViolationEntity> findOpenViolations(@Param("resolvedStatuses") List<ViolationStatus> resolvedStatuses);

    @Query("SELECT v FROM ComplianceViolationEntity v WHERE v.detectedAt BETWEEN :from AND :to ORDER BY v.detectedAt DESC")
    List<ComplianceViolationEntity> findByDetectedAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(v) FROM ComplianceViolationEntity v WHERE v.detectedAt BETWEEN :from AND :to")
    long countByDetectedAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT v FROM ComplianceViolationEntity v WHERE v.employeeId = :employeeId AND v.detectedAt BETWEEN :from AND :to ORDER BY v.detectedAt DESC")
    List<ComplianceViolationEntity> findByEmployeeIdAndDetectedAtBetween(
        @Param("employeeId") String employeeId,
        @Param("from") Instant from,
        @Param("to") Instant to);

    Optional<ComplianceViolationEntity> findByViolationId(String violationId);

    long countByStatus(ViolationStatus status);

    @Query("SELECT v.severity, COUNT(v) FROM ComplianceViolationEntity v WHERE v.detectedAt BETWEEN :from AND :to GROUP BY v.severity")
    List<Object[]> countBySeverityBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT v.category, COUNT(v) FROM ComplianceViolationEntity v WHERE v.detectedAt BETWEEN :from AND :to GROUP BY v.category")
    List<Object[]> countByCategoryBetween(@Param("from") Instant from, @Param("to") Instant to);
}