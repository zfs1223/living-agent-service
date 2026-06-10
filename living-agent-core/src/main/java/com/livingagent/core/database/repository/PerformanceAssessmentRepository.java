package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.PerformanceAssessmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerformanceAssessmentRepository extends JpaRepository<PerformanceAssessmentEntity, UUID> {

    Optional<PerformanceAssessmentEntity> findByAssessmentId(String assessmentId);

    List<PerformanceAssessmentEntity> findByEmployeeIdOrderByAssessedAtDesc(String employeeId);

    List<PerformanceAssessmentEntity> findByEmployeeIdAndPeriodTypeOrderByAssessedAtDesc(String employeeId, String periodType);

    List<PerformanceAssessmentEntity> findByPeriodTypeOrderByAssessedAtDesc(String periodType);

    List<PerformanceAssessmentEntity> findByAssessedAtBetweenOrderByAssessedAtDesc(Instant start, Instant end);
}
