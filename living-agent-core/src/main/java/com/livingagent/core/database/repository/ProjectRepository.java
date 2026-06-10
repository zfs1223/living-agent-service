package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {

    Optional<ProjectEntity> findByProjectId(String projectId);

    List<ProjectEntity> findByStatus(String status);

    List<ProjectEntity> findByOwnerDepartment(String ownerDepartment);

    List<ProjectEntity> findByManagerId(String managerId);

    List<ProjectEntity> findByStatusAndOwnerDepartment(String status, String ownerDepartment);

    List<ProjectEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<ProjectEntity> findByProjectKey(String projectKey);

    long countByStatus(String status);

    long countByOwnerDepartment(String ownerDepartment);
}
