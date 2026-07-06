package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {

    Optional<TaskEntity> findByTaskId(String taskId);

    List<TaskEntity> findByAssignedToAndStatusIn(String assignedTo, List<String> statuses);

    List<TaskEntity> findByAssignedToOrderByCreatedAtDesc(String assignedTo);

    List<TaskEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<TaskEntity> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    List<TaskEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<TaskEntity> findByDepartmentCodeAndStatusIn(String departmentCode, List<String> statuses);

    List<TaskEntity> findByProjectIdOrderByCreatedAtAsc(String projectId);

    Optional<TaskEntity> findByTaskKey(String taskKey);

    List<TaskEntity> findByExecutionId(String executionId);

    List<TaskEntity> findBySourceTypeOrderByCreatedAtDesc(String sourceType);

    long countByStatus(String status);

    long countByAssignedToAndStatusIn(String assignedTo, List<String> statuses);

    List<TaskEntity> findByReadinessStatus(String readinessStatus);

    List<TaskEntity> findByConversationIdAndStatusIn(String conversationId, List<String> statuses);

    List<TaskEntity> findByDepartmentCodeAndReadinessStatus(String departmentCode, String readinessStatus);
}
