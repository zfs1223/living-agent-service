package com.livingagent.core.autonomy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ArtifactRecordService {
    ArtifactRecord recordArtifact(ArtifactRecord artifact);
    Optional<ArtifactRecord> getArtifact(String artifactId);
    List<ArtifactRecord> getByExecutionId(String executionId);
    List<ArtifactRecord> getByDepartment(String department);
    List<ArtifactRecord> getByEmployeeCode(String employeeCode);
    List<ArtifactRecord> getByDepartmentAndType(String department, String type);
    Page<ArtifactRecord> getByType(String type, Pageable pageable);
    Page<ArtifactRecord> getAllOrderByCreatedAtDesc(Pageable pageable);
    long countByExecutionId(String executionId);
    long countByDepartment(String department);
    boolean exists(String artifactId);
    List<ArtifactRecord> scanAndIndexDirectory(String baseDir);
    List<ArtifactRecord> getByTaskId(String taskId);
    List<ArtifactRecord> getByProjectId(String projectId);
    ArtifactRecord associateTaskAndProject(String artifactId, String taskId, String projectId);
}
