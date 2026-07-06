package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.DepartmentExecutionResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentExecutionResultRepository extends JpaRepository<DepartmentExecutionResultEntity, UUID> {

    Optional<DepartmentExecutionResultEntity> findByExecutionId(String executionId);

    void deleteByExecutionId(String executionId);
}
