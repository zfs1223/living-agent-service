package com.livingagent.core.ops.scheduler;

import com.livingagent.core.database.entity.TaskEntity;
import com.livingagent.core.database.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TaskCheckout 与 TaskRepository 的同步服务
 * 确保内存任务系统和数据库任务系统的数据互通
 */
@Service
public class TaskCheckoutSyncService {

    private static final Logger log = LoggerFactory.getLogger(TaskCheckoutSyncService.class);

    private final TaskRepository taskRepository;

    public TaskCheckoutSyncService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 从数据库恢复任务到 TaskCheckout
     * 用于启动时重建内存状态
     */
    public TaskCheckout.Task restoreFromEntity(TaskEntity entity) {
        if (entity == null) return null;

        TaskCheckout.TaskStatus status;
        try {
            status = TaskCheckout.TaskStatus.valueOf(entity.getStatus());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown task status '{}' in DB, defaulting to PENDING", entity.getStatus());
            status = TaskCheckout.TaskStatus.PENDING;
        }

        return new TaskCheckout.Task(
            entity.getTaskId(),
            entity.getTaskType(),
            entity.getDescription(),
            entity.getPriority(),
            entity.getRequiredCapability(),
            Map.of(),
            status,
            entity.getCreatedAt(),
            entity.getCheckedOutAt(),
            entity.getAssignedTo(),
            entity.getCompletedAt(),
            entity.getProjectId()
        );
    }

    /**
     * 将 TaskCheckout.Task 同步到数据库
     */
    public void syncToDatabase(TaskCheckout.Task task) {
        if (task == null) return;
        try {
            Optional<TaskEntity> existing = taskRepository.findByTaskId(task.taskId());
            TaskEntity entity = existing.orElseGet(TaskEntity::new);
            entity.setTaskId(task.taskId());
            entity.setTaskType(task.taskType());
            entity.setDescription(task.description());
            entity.setPriority(task.priority());
            entity.setRequiredCapability(task.requiredCapability());
            entity.setStatus(task.status().name());
            entity.setCreatedAt(task.createdAt());
            entity.setCheckedOutAt(task.checkedOutAt());
            entity.setAssignedTo(task.assignedTo());
            entity.setCompletedAt(task.completedAt());
            entity.setUpdatedAt(Instant.now());
            if (task.projectId() != null) {
                entity.setProjectId(task.projectId());
            }
            taskRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to sync task {} to database: {}", task.taskId(), e.getMessage());
        }
    }

    /**
     * 从数据库加载所有 PENDING 状态的任务
     */
    public List<TaskCheckout.Task> loadPendingTasks() {
        try {
            return taskRepository.findByStatusOrderByCreatedAtDesc("PENDING").stream()
                .map(this::restoreFromEntity)
                .toList();
        } catch (Exception e) {
            log.warn("Failed to load pending tasks from database: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从数据库加载指定员工的活跃任务
     */
    public List<TaskCheckout.Task> loadActiveTasksByEmployee(String employeeId) {
        try {
            List<String> activeStatuses = List.of("CHECKED_OUT", "IN_PROGRESS", "SUBMITTED", "PENDING_REVIEW");
            return taskRepository.findByAssignedToAndStatusIn(employeeId, activeStatuses).stream()
                .map(this::restoreFromEntity)
                .toList();
        } catch (Exception e) {
            log.warn("Failed to load active tasks for employee {}: {}", employeeId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 根据 projectId 查询任务
     */
    public List<TaskCheckout.Task> loadTasksByProjectId(String projectId) {
        try {
            return taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(this::restoreFromEntity)
                .toList();
        } catch (Exception e) {
            log.warn("Failed to load tasks for project {}: {}", projectId, e.getMessage());
            return List.of();
        }
    }
}
