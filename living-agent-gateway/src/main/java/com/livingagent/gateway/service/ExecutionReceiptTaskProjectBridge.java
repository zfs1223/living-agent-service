package com.livingagent.gateway.service;

import com.livingagent.core.autonomy.DepartmentExecutionResult;
import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import com.livingagent.core.autonomy.EmployeeExecutionReceiptService;
import com.livingagent.core.autonomy.ReceiptStatus;
import com.livingagent.core.database.entity.ProjectEntity;
import com.livingagent.core.database.entity.TaskEntity;
import com.livingagent.core.database.repository.TaskRepository;
import com.livingagent.core.database.repository.ProjectRepository;
import com.livingagent.core.runtime.RuntimeEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ExecutionReceiptTaskProjectBridge implements EmployeeExecutionReceiptService.ReceiptListener {

    private static final Logger log = LoggerFactory.getLogger(ExecutionReceiptTaskProjectBridge.class);
    /** B-0-6: 无法解析 tenantId 时的兜底值（保持向后兼容） */
    private static final String DEFAULT_TENANT_ID = "_system";

    private final EmployeeExecutionReceiptService receiptService;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final RuntimeEventStore runtimeEventStore;

    public ExecutionReceiptTaskProjectBridge(
            EmployeeExecutionReceiptService receiptService,
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            RuntimeEventStore runtimeEventStore) {
        this.receiptService = receiptService;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.runtimeEventStore = runtimeEventStore;
    }

    @PostConstruct
    public void init() {
        receiptService.addReceiptListener(this);
        log.info("ExecutionReceiptTaskProjectBridge registered as ReceiptListener");
    }

    @Override
    public void onReceiptRecorded(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult) {
        // B-0-3: executionResult 可能为 null（JpaEmployeeExecutionReceiptService 不再依赖内存缓存传递）
        // 当 executionResult 为 null 时，从 receipt 自身提取 executionId 继续处理
        if (receipt == null) return;

        try {
            String executionId = executionResult != null ? executionResult.executionId() : receipt.executionId();
            if (executionId == null) {
                log.debug("Skipping receipt bridge: no executionId available, receiptId={}", receipt.receiptId());
                return;
            }
            log.debug("Processing receipt for executionId={}, status={}", executionId, receipt.status() != null ? receipt.status().getCode() : "null");

            // B-0-6: 动态解析 tenantId，优先级：task.tenantId > executionResult.metadata.tenantId > receipt.metadata.tenantId > _system
            String tenantId = resolveTenantId(executionResult, receipt);

            updateTaskFromReceipt(receipt, executionResult, executionId);

            if (receiptService.isExecutionComplete(executionId)) {
                updateProjectFromExecution(executionResult, receipt, executionId, tenantId);
            }

            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("executionId", executionId);
            eventData.put("receiptStatus", receipt.status() != null ? receipt.status().getCode() : null);
            eventData.put("employeeCode", receipt.employeeCode());
            eventData.put("dispatchId", receipt.dispatchId());
            eventData.put("tenantId", tenantId);
            // B-0-6: 使用动态 tenantId 替代硬编码 "_system"
            runtimeEventStore.appendTaskEvent(tenantId, executionId, "execution_receipt_recorded", eventData);

        } catch (Exception e) {
            log.warn("Failed to process receipt bridge for executionId={}: {}",
                executionResult != null ? executionResult.executionId() : (receipt != null ? receipt.executionId() : "unknown"),
                e.getMessage());
        }
    }

    /**
     * B-0-6: 动态解析 tenantId。
     * 优先从 task 获取（持久化字段），其次从 executionResult/receipt 的 metadata 获取，
     * 最后回退到 DEFAULT_TENANT_ID。
     */
    private String resolveTenantId(DepartmentExecutionResult executionResult, EmployeeExecutionReceipt receipt) {
        // 1. 从 executionResult.metadata.tenantId 获取
        if (executionResult != null && executionResult.metadata() != null) {
            Object tenantIdObj = executionResult.metadata().get("tenantId");
            if (tenantIdObj != null) {
                String tenantId = String.valueOf(tenantIdObj);
                if (!tenantId.isBlank()) return tenantId;
            }
        }
        // 2. 从 receipt.metadata.tenantId 获取
        if (receipt != null && receipt.metadata() != null) {
            Object tenantIdObj = receipt.metadata().get("tenantId");
            if (tenantIdObj != null) {
                String tenantId = String.valueOf(tenantIdObj);
                if (!tenantId.isBlank()) return tenantId;
            }
        }
        // 3. 从关联的 task.tenantId 获取（持久化字段，最权威）
        String executionId = executionResult != null ? executionResult.executionId() : (receipt != null ? receipt.executionId() : null);
        if (executionId != null) {
            try {
                Optional<TaskEntity> taskOpt = taskRepository.findByExecutionId(executionId).stream().findFirst();
                if (taskOpt.isPresent() && taskOpt.get().getTenantId() != null && !taskOpt.get().getTenantId().isBlank()) {
                    return taskOpt.get().getTenantId();
                }
            } catch (Exception e) {
                log.debug("Failed to lookup task for tenantId resolution, executionId={}: {}", executionId, e.getMessage());
            }
        }
        // 4. 兜底
        return DEFAULT_TENANT_ID;
    }

    private void updateTaskFromReceipt(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult, String executionId) {
        taskRepository.findByExecutionId(executionId).stream().findFirst().ifPresent(task -> {
            String newStatus = mapReceiptStatusToTaskStatus(receipt.status(), task.getStatus());
            if (newStatus != null && !newStatus.equals(task.getStatus())) {
                task.setStatus(newStatus);
                task.setUpdatedAt(Instant.now());
                if ("COMPLETED".equals(newStatus) || "FAILED".equals(newStatus)) {
                    task.setSubmissionResult(receipt.summary());
                }
                taskRepository.save(task);
                log.info("Updated task {} status to {} from receipt", task.getTaskId(), newStatus);
            }
        });

        if (taskRepository.findByExecutionId(executionId).isEmpty()) {
            log.debug("No task found for executionId={}, skipping task update", executionId);
        }
    }

    /**
     * B-0-5: 项目统计落库 + B-0-6: tenantId 动态获取
     * 在原有 appendProjectEvent 基础上，增加 projectRepository.save 更新 progress 字段，确保项目统计落库。
     */
    private void updateProjectFromExecution(DepartmentExecutionResult executionResult,
                                             EmployeeExecutionReceipt receipt,
                                             String executionId,
                                             String tenantId) {
        taskRepository.findByExecutionId(executionId).stream().findFirst().ifPresent(task -> {
            String projectId = task.getProjectId();
            if (projectId == null) return;

            projectRepository.findById(projectId).ifPresent(project -> {
                long totalTasks = taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId).size();
                long completedTasks = taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .count();
                long failedTasks = taskRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                    .filter(t -> "FAILED".equals(t.getStatus()) || "REJECTED".equals(t.getStatus()))
                    .count();
                double completionRate = totalTasks > 0 ? (double) completedTasks / totalTasks : 0.0;

                Map<String, Object> projectEventData = new LinkedHashMap<>();
                projectEventData.put("projectId", projectId);
                projectEventData.put("executionId", executionId);
                projectEventData.put("totalTasks", totalTasks);
                projectEventData.put("completedTasks", completedTasks);
                projectEventData.put("failedTasks", failedTasks);
                projectEventData.put("completionRate", completionRate);
                // B-0-6: 使用动态 tenantId 替代硬编码 "_system"
                runtimeEventStore.appendProjectEvent(tenantId, projectId, "project_tasks_updated", projectEventData);

                // B-0-5: 将项目统计落库（更新 progress 字段，持久化到 projects 表）
                updateProjectStatsInDb(project, totalTasks, completedTasks, completionRate);

                log.info("Project {} task stats updated: {}/{} completed, {} failed",
                    projectId, completedTasks, totalTasks, failedTasks);
            });
        });
    }

    /**
     * B-0-5: 将项目统计落库到 projects 表。
     * 当前 ProjectEntity 只有 progress 字段可承载完成率，没有 completedTaskCount/totalTaskCount 字段，
     * 这里仅更新 progress 与 updatedAt，保留事件推送的同时落库关键统计。
     */
    private void updateProjectStatsInDb(ProjectEntity project, long totalTasks, long completedTasks, double completionRate) {
        try {
            project.setProgress(completionRate);
            project.setUpdatedAt(Instant.now());
            projectRepository.save(project);
            log.debug("Project {} progress persisted to DB: progress={}", project.getProjectId(), completionRate);
        } catch (Exception e) {
            log.warn("Failed to persist project stats for projectId={}: {}", project.getProjectId(), e.getMessage());
        }
    }

    private String mapReceiptStatusToTaskStatus(ReceiptStatus receiptStatus, String currentTaskStatus) {
        if (receiptStatus == null) return null;

        return switch (receiptStatus) {
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case DEGRADED -> "NEEDS_REWORK";
            case NEEDS_RETRY -> "IN_PROGRESS";
            case NEEDS_APPROVAL -> "NEEDS_HUMAN_REVIEW";
            case NEEDS_HUMAN_REVIEW -> "NEEDS_HUMAN_REVIEW";
        };
    }
}
