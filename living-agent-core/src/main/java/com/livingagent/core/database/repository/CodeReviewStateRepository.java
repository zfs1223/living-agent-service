package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.CodeReviewStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeReviewStateRepository extends JpaRepository<CodeReviewStateEntity, Long> {

    Optional<CodeReviewStateEntity> findByTaskId(String taskId);

    Optional<CodeReviewStateEntity> findByExecutionId(String executionId);

    List<CodeReviewStateEntity> findByStage(String stage);

    List<CodeReviewStateEntity> findByDeveloperEmployeeCode(String developerEmployeeCode);

    List<CodeReviewStateEntity> findByReviewerEmployeeCode(String reviewerEmployeeCode);

    boolean existsByTaskId(String taskId);
}
