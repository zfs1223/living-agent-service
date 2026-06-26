package com.livingagent.core.database.repository;

import com.livingagent.core.database.entity.DagTaskEntity;
import com.livingagent.core.database.entity.DagTaskEntity.DagTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DagTaskRepository extends JpaRepository<DagTaskEntity, String> {

    Optional<DagTaskEntity> findByTaskId(String taskId);

    List<DagTaskEntity> findByStatusOrderByCreatedAtAsc(DagTaskStatus status);

    List<DagTaskEntity> findByAssigneeIsNullAndStatusOrderByCreatedAtAsc(DagTaskStatus status);

    @Query("SELECT t FROM DagTaskEntity t WHERE t.assignee IS NULL OR t.assignee = '' ORDER BY t.createdAt ASC")
    List<DagTaskEntity> findUnclaimedTasks();

    @Query("SELECT t FROM DagTaskEntity t WHERE (t.assignee IS NULL OR t.assignee = '') AND (t.role IS NULL OR t.role = :role) ORDER BY t.createdAt ASC")
    List<DagTaskEntity> findUnclaimedTasksByRole(@Param("role") String role);

    @Query("SELECT t.taskId FROM DagTaskEntity t WHERE t.status = :status AND (t.blockedByJson IS NULL OR t.blockedByJson = '[]')")
    List<String> findReadyTaskIds(@Param("status") DagTaskStatus status);

    @Query("SELECT COUNT(t) FROM DagTaskEntity t WHERE t.status = :status")
    long countByStatus(@Param("status") DagTaskStatus status);

    @Query("SELECT MAX(CAST(t.taskId AS integer)) FROM DagTaskEntity t")
    Integer findMaxTaskId();

    void deleteByStatus(DagTaskStatus status);
}