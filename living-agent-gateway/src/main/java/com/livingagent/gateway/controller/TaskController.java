package com.livingagent.gateway.controller;

import com.livingagent.core.ops.scheduler.TaskCheckout;
import com.livingagent.core.ops.scheduler.TaskCheckout.Task;
import com.livingagent.core.ops.scheduler.TaskCheckout.TaskResult;
import com.livingagent.core.ops.scheduler.TaskCheckout.TaskStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskCheckout taskCheckout;

    public TaskController(TaskCheckout taskCheckout) {
        this.taskCheckout = taskCheckout;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskSummary>>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String capability,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        log.debug("Listing tasks, status: {}, assignee: {}", status, assignee);

        List<Task> tasks;
        if ("pending".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getPendingTasks();
        } else if ("checked_out".equalsIgnoreCase(status)) {
            tasks = assignee != null 
                ? taskCheckout.getCheckedOutTasks(assignee)
                : taskCheckout.getAllCheckedOutTasks();
        } else if ("completed".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getCompletedTasks(limit);
        } else {
            tasks = taskCheckout.getPendingTasks();
        }

        if (capability != null) {
            tasks = taskCheckout.getPendingTasksByCapability(capability);
        }

        List<TaskSummary> summaries = tasks.stream()
                .skip(offset)
                .limit(limit)
                .map(this::toSummary)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Task>> createTask(
            @RequestBody CreateTaskRequest request
    ) {
        log.info("Creating task: {} - {}", request.taskType(), request.description());

        Task task = taskCheckout.createTask(
                request.taskId() != null ? request.taskId() : "task_" + System.currentTimeMillis(),
                request.taskType(),
                request.description(),
                request.priority() != null ? request.priority() : 5,
                request.requiredCapability(),
                request.context()
        );

        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskDetail>> getTask(
            @PathVariable String taskId
    ) {
        log.debug("Getting task: {}", taskId);

        Optional<Task> taskOpt = taskCheckout.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("not_found", "Task not found: " + taskId));
        }

        return ResponseEntity.ok(ApiResponse.success(toDetail(taskOpt.get())));
    }

    @PostMapping("/{taskId}/checkout")
    public ResponseEntity<ApiResponse<Task>> checkoutTask(
            @PathVariable String taskId,
            @RequestBody CheckoutRequest request
    ) {
        log.info("Checking out task {} to employee {}", taskId, request.employeeId());

        Optional<Task> taskOpt = taskCheckout.checkoutSpecificTask(taskId, request.employeeId());
        if (taskOpt.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("checkout_failed", "Task cannot be checked out"));
        }

        return ResponseEntity.ok(ApiResponse.success(taskOpt.get()));
    }

    @PostMapping("/{taskId}/complete")
    public ResponseEntity<ApiResponse<Task>> completeTask(
            @PathVariable String taskId,
            @RequestBody CompleteTaskRequest request
    ) {
        log.info("Completing task {} by employee {}", taskId, request.employeeId());

        try {
            TaskResult result = request.success()
                    ? TaskResult.success(taskId, request.output())
                    : TaskResult.failure(taskId, request.error());

            Task task = taskCheckout.completeTask(taskId, request.employeeId(), result);
            return ResponseEntity.ok(ApiResponse.success(task));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("complete_failed", e.getMessage()));
        }
    }

    @PostMapping("/{taskId}/release")
    public ResponseEntity<ApiResponse<Task>> releaseTask(
            @PathVariable String taskId,
            @RequestBody ReleaseRequest request
    ) {
        log.info("Releasing task {} from employee {}", taskId, request.employeeId());

        try {
            Task task = taskCheckout.releaseTask(taskId, request.employeeId(), request.reason());
            return ResponseEntity.ok(ApiResponse.success(task));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("release_failed", e.getMessage()));
        }
    }

    @PostMapping("/{taskId}/reassign")
    public ResponseEntity<ApiResponse<Task>> reassignTask(
            @PathVariable String taskId,
            @RequestBody ReassignRequest request
    ) {
        log.info("Reassigning task {} from {} to {}", taskId, request.fromEmployeeId(), request.toEmployeeId());

        try {
            taskCheckout.reassignTask(taskId, request.fromEmployeeId(), request.toEmployeeId());
            Optional<Task> taskOpt = taskCheckout.getTask(taskId);
            return ResponseEntity.ok(ApiResponse.success(taskOpt.orElse(null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("reassign_failed", e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<TaskStatistics>> getStatistics() {
        log.debug("Getting task statistics");
        TaskStatistics stats = taskCheckout.getStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<TaskSummary>>> getPendingTasks(
            @RequestParam(required = false) String capability,
            @RequestParam(defaultValue = "100") int limit
    ) {
        log.debug("Getting pending tasks");

        List<Task> tasks = capability != null
                ? taskCheckout.getPendingTasksByCapability(capability)
                : taskCheckout.getPendingTasks();

        List<TaskSummary> summaries = tasks.stream()
                .limit(limit)
                .map(this::toSummary)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<TaskSummary>>> getEmployeeTasks(
            @PathVariable String employeeId
    ) {
        log.debug("Getting tasks for employee: {}", employeeId);

        List<Task> tasks = taskCheckout.getCheckedOutTasks(employeeId);
        List<TaskSummary> summaries = tasks.stream()
                .map(this::toSummary)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    private TaskSummary toSummary(Task task) {
        return new TaskSummary(
                task.taskId(),
                task.taskType(),
                task.description(),
                task.priority(),
                task.requiredCapability(),
                task.status().name(),
                task.assignedTo(),
                task.createdAt(),
                task.checkedOutAt(),
                task.completedAt()
        );
    }

    private TaskDetail toDetail(Task task) {
        return new TaskDetail(
                task.taskId(),
                task.taskType(),
                task.description(),
                task.priority(),
                task.requiredCapability(),
                task.context(),
                task.status().name(),
                task.createdAt(),
                task.checkedOutAt(),
                task.assignedTo(),
                task.completedAt()
        );
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
        }
    }

    public record TaskSummary(
            String taskId,
            String taskType,
            String description,
            int priority,
            String requiredCapability,
            String status,
            String assignedTo,
            Instant createdAt,
            Instant checkedOutAt,
            Instant completedAt
    ) {}

    public record TaskDetail(
            String taskId,
            String taskType,
            String description,
            int priority,
            String requiredCapability,
            Map<String, Object> context,
            String status,
            Instant createdAt,
            Instant checkedOutAt,
            String assignedTo,
            Instant completedAt
    ) {}

    public record CreateTaskRequest(
            String taskId,
            String taskType,
            String description,
            Integer priority,
            String requiredCapability,
            Map<String, Object> context
    ) {}

    public record CheckoutRequest(
            String employeeId,
            List<String> capabilities
    ) {}

    public record CompleteTaskRequest(
            String employeeId,
            boolean success,
            String output,
            String error,
            Map<String, Object> metrics
    ) {}

    public record ReleaseRequest(
            String employeeId,
            String reason
    ) {}

    public record ReassignRequest(
            String fromEmployeeId,
            String toEmployeeId
    ) {}
}
