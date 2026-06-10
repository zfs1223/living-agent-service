package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ArtifactRecordEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtifactRecordRepository extends JpaRepository<ArtifactRecordEntity, Long> {

    Optional<ArtifactRecordEntity> findByArtifactId(String artifactId);

    List<ArtifactRecordEntity> findByExecutionId(String executionId);

    List<ArtifactRecordEntity> findByDepartment(String department);

    List<ArtifactRecordEntity> findByOwnerEmployeeCode(String employeeCode);

    @Query("SELECT a FROM ArtifactRecordEntity a WHERE a.department = :department AND a.type = :type")
    List<ArtifactRecordEntity> findByDepartmentAndType(
        @Param("department") String department,
        @Param("type") String type
    );

    @Query("SELECT a FROM ArtifactRecordEntity a WHERE a.type = :type")
    Page<ArtifactRecordEntity> findByType(@Param("type") String type, Pageable pageable);

    @Query("SELECT a FROM ArtifactRecordEntity a WHERE a.ownerEmployeeCode = :employeeCode AND a.type = :type")
    List<ArtifactRecordEntity> findByEmployeeCodeAndType(
        @Param("employeeCode") String employeeCode,
        @Param("type") String type
    );

    @Query("SELECT COUNT(a) FROM ArtifactRecordEntity a WHERE a.executionId = :executionId")
    long countByExecutionId(@Param("executionId") String executionId);

    @Query("SELECT COUNT(a) FROM ArtifactRecordEntity a WHERE a.department = :department")
    long countByDepartment(@Param("department") String department);

    @Query("SELECT a FROM ArtifactRecordEntity a ORDER BY a.createdAt DESC")
    Page<ArtifactRecordEntity> findAllOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByArtifactId(String artifactId);

    List<ArtifactRecordEntity> findByTaskId(String taskId);

    List<ArtifactRecordEntity> findByProjectId(String projectId);

    List<ArtifactRecordEntity> findByExecutionIdAndTaskId(String executionId, String taskId);

    List<ArtifactRecordEntity> findByExecutionIdAndProjectId(String executionId, String projectId);

    /* ============ 权限相关（HERMES_COMPARISON_AND_BORROWING_PLAN.md §6.18）============ */

    List<ArtifactRecordEntity> findByVisibility(String visibility);

    @Query("SELECT a FROM ArtifactRecordEntity a WHERE a.visibility = 'PUBLIC' OR a.department = :department")
    List<ArtifactRecordEntity> findByDepartmentOrPublic(@Param("department") String department);
}
