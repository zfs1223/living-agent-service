package com.livingagent.gateway.service;

import com.livingagent.core.autonomy.DepartmentExecutionResult;
import com.livingagent.core.autonomy.EmployeeExecutionReceipt;
import com.livingagent.core.autonomy.EmployeeExecutionReceiptService;
import com.livingagent.core.autonomy.ReceiptStatus;
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
        if (receipt == null || executionResult == null) return;

        try {
            String executionId = executionResult.executionId();
            log.debug("Processing receipt for executionId={}, status={}", executionId, receipt.status() != null ? receipt.status().getCode() : "null");

            updateTaskFromReceipt(receipt, executionResult);

            if (receiptService.isExecutionComplete(executionId)) {
                updateProjectFromExecution(executionResult);
            }

            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("executionId", executionId);
            eventData.put("receiptStatus", receipt.status() != null ? receipt.status().getCode() : null);
            eventData.put("employeeCode", receipt.employeeCode());
            eventData.put("dispatchId", receipt.dispatchId());
            runtimeEventStore.appendTaskEvent("_system", executionId, "execution_receipt_recorded", eventData);

        } catch (Exception e) {
            log.warn("Failed to process receipt bridge for executionId={}: {}", 
                executionResult.executionId(), e.getMessage());
        }
    }

    private void updateTaskFromReceipt(EmployeeExecutionReceipt receipt, DepartmentExecutionResult executionResult) {
        String executionId = executionResult.executionId();

        taskRepository.findByExecutionId(executionId).ifPresent(task -> {
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

    private void updateProjectFromExecution(DepartmentExecutionResult executionResult) {
        String executionId = executionResult.executionId();

        taskRepository.findByExecutionId(executionId).ifPresent(task -> {
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

                Map<String, Object> projectEventData = new LinkedHashMap<>();
                projectEventData.put("projectId", projectId);
                projectEventData.put("executionId", executionId);
                projectEventData.put("totalTasks", totalTasks);
                projectEventData.put("completedTasks", completedTasks);
                projectEventData.put("failedTasks", failedTasks);
                projectEventData.put("completionRate", totalTasks > 0 ? (double) completedTasks / totalTasks : 0.0);
                runtimeEventStore.appendProjectEvent("_system", projectId, "project_tasks_updated", projectEventData);

                log.info("Project {} task stats updated: {}/{} completed, {} failed",
                    projectId, completedTasks, totalTasks, failedTasks);
            });
        });
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
