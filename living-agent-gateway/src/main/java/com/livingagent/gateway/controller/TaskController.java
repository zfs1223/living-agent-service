package com.livingagent.gateway.controller;

import com.livingagent.core.database.entity.EmployeeExecutionReceiptEntity;
import com.livingagent.core.database.repository.EmployeeExecutionReceiptRepository;
import com.livingagent.core.ops.scheduler.TaskCheckout;
import com.livingagent.core.ops.scheduler.TaskCheckout.Task;
import com.livingagent.core.ops.scheduler.TaskCheckout.TaskResult;
import com.livingagent.core.ops.scheduler.TaskCheckout.TaskStatistics;
import com.livingagent.core.runtime.RuntimeEventStore;
import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.AuthContext;
import com.livingagent.core.security.auth.UnifiedAuthService;
import com.livingagent.core.security.auth.UnifiedAuthService.AuthSession;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.gateway.security.WorkItemPermissionService;
import com.livingagent.gateway.service.PublicTaskEventPublisher;
import com.livingagent.gateway.service.TaskEventBridgeService;
import com.livingagent.gateway.service.TaskPerformanceBridgeService;
import com.livingagent.gateway.service.TaskWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskCheckout taskCheckout;
    private final AccessGateService accessGateService;
    private final TaskWorkflowService taskWorkflowService;
    private final TaskEventBridgeService taskEventBridgeService;
    private final TaskPerformanceBridgeService taskPerformanceBridgeService;
    private final WorkItemPermissionService workItemPermissionService;
    private final RuntimeEventStore runtimeEventStore;
    private final UnifiedAuthService unifiedAuthService;
    private final EmployeeExecutionReceiptRepository receiptRepository;
    private final PublicTaskEventPublisher publicTaskEventPublisher;

    public TaskController(TaskCheckout taskCheckout,
                          AccessGateService accessGateService,
                          TaskWorkflowService taskWorkflowService,
                          TaskEventBridgeService taskEventBridgeService,
                          TaskPerformanceBridgeService taskPerformanceBridgeService,
                          WorkItemPermissionService workItemPermissionService,
                          RuntimeEventStore runtimeEventStore,
                          UnifiedAuthService unifiedAuthService,
                          EmployeeExecutionReceiptRepository receiptRepository,
                          PublicTaskEventPublisher publicTaskEventPublisher) {
        this.taskCheckout = taskCheckout;
        this.accessGateService = accessGateService;
        this.taskWorkflowService = taskWorkflowService;
        this.taskEventBridgeService = taskEventBridgeService;
        this.taskPerformanceBridgeService = taskPerformanceBridgeService;
        this.workItemPermissionService = workItemPermissionService;
        this.runtimeEventStore = runtimeEventStore;
        this.unifiedAuthService = unifiedAuthService;
        this.receiptRepository = receiptRepository;
        this.publicTaskEventPublisher = publicTaskEventPublisher;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskSummary>>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignee,
            @RequestParam(required = false) String capability,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        List<Task> tasks;
        if ("pending".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getPendingTasks();
        } else if ("checked_out".equalsIgnoreCase(status)) {
            tasks = assignee != null ? taskCheckout.getCheckedOutTasks(assignee) : taskCheckout.getAllCheckedOutTasks();
        } else if ("completed".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getCompletedTasks(limit);
        } else {
            tasks = taskCheckout.getPendingTasks();
        }

        if (capability != null) {
            tasks = taskCheckout.getPendingTasksByCapability(capability);
        }

        List<TaskSummary> summaries = tasks.stream().skip(offset).limit(limit).map(this::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Task>> createTask(
            @RequestBody CreateTaskRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        Task task = taskCheckout.createTask(
                request.taskId() != null ? request.taskId() : "task_" + System.currentTimeMillis(),
                request.taskType(),
                request.description(),
                request.priority() != null ? request.priority() : 5,
                request.requiredCapability(),
                request.context()
        );

        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskDetail>> getTask(
            @PathVariable String taskId,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx != null && !workItemPermissionService.canViewTask(taskId, ctx)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "No permission to view this task"));
        }

        Optional<Task> taskOpt = taskCheckout.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Task not found: " + taskId));
        }

        return ResponseEntity.ok(ApiResponse.ok(toDetail(taskOpt.get())));
    }

    @PostMapping("/{taskId}/checkout")
    public ResponseEntity<ApiResponse<Task>> checkoutTask(
            @PathVariable String taskId,
            @RequestBody CheckoutRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        Optional<Task> taskOpt = taskCheckout.checkoutSpecificTask(taskId, request.employeeId());
        if (taskOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.err("checkout_failed", "Task cannot be checked out"));
        }

        return ResponseEntity.ok(ApiResponse.ok(taskOpt.get()));
    }

    @PostMapping("/{taskId}/complete")
    public ResponseEntity<ApiResponse<Task>> completeTask(
            @PathVariable String taskId,
            @RequestBody CompleteTaskRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        try {
            TaskResult result = request.success() ? TaskResult.success(taskId, request.output(), request.metrics()) : TaskResult.failure(taskId, request.error());
            Task task = taskCheckout.completeTask(taskId, request.employeeId(), result);
            return ResponseEntity.ok(ApiResponse.ok(task));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("complete_failed", e.getMessage()));
        }
    }

    @PostMapping("/{taskId}/release")
    public ResponseEntity<ApiResponse<Task>> releaseTask(
            @PathVariable String taskId,
            @RequestBody ReleaseRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        try {
            Task task = taskCheckout.releaseTask(taskId, request.employeeId(), request.reason());
            return ResponseEntity.ok(ApiResponse.ok(task));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("release_failed", e.getMessage()));
        }
    }

    @PostMapping("/{taskId}/reassign")
    public ResponseEntity<ApiResponse<Task>> reassignTask(
            @PathVariable String taskId,
            @RequestBody ReassignRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        try {
            taskCheckout.reassignTask(taskId, request.fromEmployeeId(), request.toEmployeeId());
            Optional<Task> taskOpt = taskCheckout.getTask(taskId);
            return ResponseEntity.ok(ApiResponse.ok(taskOpt.orElse(null)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.err("reassign_failed", e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<TaskStatistics>> getStatistics() {
        return ResponseEntity.ok(ApiResponse.ok(taskCheckout.getStatistics()));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<TaskSummary>>> getPendingTasks(
            @RequestParam(required = false) String capability,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        List<Task> tasks = capability != null ? taskCheckout.getPendingTasksByCapability(capability) : taskCheckout.getPendingTasks();
        List<TaskSummary> summaries = tasks.stream().limit(limit).map(this::toSummary).toList();
        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<TaskSummary>>> getMyTasks(
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        String employeeId;
        if (ctx != null) {
            employeeId = ctx.getEmployeeId();
        } else if (headerEmployeeId != null && !headerEmployeeId.isBlank()) {
            employeeId = headerEmployeeId;
        } else {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Authentication required"));
        }

        List<Task> tasks;
        if (status == null || status.isBlank() || "active".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getCheckedOutTasks(employeeId);
        } else if ("all".equalsIgnoreCase(status)) {
            List<Task> all = new ArrayList<>();
            all.addAll(taskCheckout.getCheckedOutTasks(employeeId));
            all.addAll(taskCheckout.getCompletedTasks(100).stream()
                .filter(t -> employeeId.equals(t.assignedTo()))
                .toList());
            all.addAll(taskCheckout.getPendingTasks().stream()
                .filter(t -> employeeId.equals(t.assignedTo()))
                .toList());
            tasks = all;
        } else if ("completed".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getCompletedTasks(100).stream()
                .filter(t -> employeeId.equals(t.assignedTo()))
                .toList();
        } else if ("submitted".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getCheckedOutTasks(employeeId).stream()
                .filter(t -> t.status() == TaskCheckout.TaskStatus.SUBMITTED || t.status() == TaskCheckout.TaskStatus.PENDING_REVIEW)
                .toList();
        } else {
            tasks = taskCheckout.getCheckedOutTasks(employeeId);
        }
        return ResponseEntity.ok(ApiResponse.ok(tasks.stream().map(this::toSummary).toList()));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<TaskSummary>>> getEmployeeTasks(
            @PathVariable String employeeId,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId
    ) {
        if (headerEmployeeId != null && !headerEmployeeId.isBlank() && !accessGateService.canRoute(headerEmployeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        List<Task> tasks;
        if (status == null || status.isBlank() || "active".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getCheckedOutTasks(employeeId);
        } else if ("all".equalsIgnoreCase(status)) {
            List<Task> all = new ArrayList<>();
            all.addAll(taskCheckout.getCheckedOutTasks(employeeId));
            all.addAll(taskCheckout.getCompletedTasks(100).stream()
                .filter(t -> employeeId.equals(t.assignedTo()))
                .toList());
            all.addAll(taskCheckout.getPendingTasks().stream()
                .filter(t -> employeeId.equals(t.assignedTo()))
                .toList());
            tasks = all;
        } else if ("completed".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getCompletedTasks(100).stream()
                .filter(t -> employeeId.equals(t.assignedTo()))
                .toList();
        } else if ("submitted".equalsIgnoreCase(status)) {
            tasks = taskCheckout.getCheckedOutTasks(employeeId).stream()
                .filter(t -> t.status() == TaskCheckout.TaskStatus.SUBMITTED || t.status() == TaskCheckout.TaskStatus.PENDING_REVIEW)
                .toList();
        } else {
            tasks = taskCheckout.getCheckedOutTasks(employeeId);
        }
        return ResponseEntity.ok(ApiResponse.ok(tasks.stream().map(this::toSummary).toList()));
    }

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<List<PublicTaskSummary>>> getPublicTasks(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "50") int limit,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        List<PublicTaskSummary> publicTasks = taskCheckout.getPendingTasks().stream()
                .filter(task -> department == null || department.equalsIgnoreCase(task.context() != null ? String.valueOf(task.context().get("department")) : null))
                .filter(task -> difficulty == null || difficulty.equalsIgnoreCase(task.context() != null ? String.valueOf(task.context().get("difficulty")) : null))
                .limit(limit)
                .map(this::toPublicSummary)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(publicTasks));
    }

    @PostMapping("/{taskId}/claim")
    public ResponseEntity<ApiResponse<TaskClaimResult>> claimTask(
            @PathVariable String taskId,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        String employeeId;
        if (ctx != null) {
            employeeId = ctx.getEmployeeId();
        } else if (headerEmployeeId != null && !headerEmployeeId.isBlank()) {
            employeeId = headerEmployeeId;
        } else {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Authentication required"));
        }

        if (!accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        Optional<Task> taskOpt = taskCheckout.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Task not found: " + taskId));
        }

        if (taskOpt.get().status() != TaskCheckout.TaskStatus.PENDING) {
            return ResponseEntity.badRequest().body(ApiResponse.err("not_available", "Task is not available for claiming"));
        }

        Optional<Task> claimedOpt = taskCheckout.checkoutSpecificTask(taskId, employeeId);
        if (claimedOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.err("claim_failed", "Failed to claim task"));
        }

        Task claimed = claimedOpt.get();
        TaskClaimResult result = new TaskClaimResult(claimed.taskId(), claimed.taskType(), claimed.description(), employeeId, claimed.checkedOutAt().toString(), "Task claimed successfully");

        Map<String, Object> eventData = new java.util.LinkedHashMap<>();
        eventData.put("taskId", taskId);
        eventData.put("employeeId", employeeId);
        runtimeEventStore.appendTaskEvent("_system", taskId, "task_claimed", eventData);

        // 桌面端事件广播（参考 HERMES_COMPARISON_AND_BORROWING_PLAN.md §6.19）
        String taskDepartment = claimed.context() != null && claimed.context().get("department") != null
            ? String.valueOf(claimed.context().get("department"))
            : "ALL";
        publicTaskEventPublisher.publishTaskClaimed(taskDepartment, taskId, employeeId);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/{taskId}/submit")
    public ResponseEntity<ApiResponse<TaskSubmitResult>> submitTask(
            @PathVariable String taskId,
            @RequestBody SubmitTaskRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String headerEmployeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        String employeeId;
        if (ctx != null) {
            employeeId = ctx.getEmployeeId();
        } else if (headerEmployeeId != null && !headerEmployeeId.isBlank()) {
            employeeId = headerEmployeeId;
        } else {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Authentication required"));
        }

        if (!accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        if (ctx != null && !workItemPermissionService.canEditTask(taskId, ctx)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "No permission to submit this task"));
        }

        Optional<Task> taskOpt = taskCheckout.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Task not found: " + taskId));
        }

        Task task = taskOpt.get();
        if (!employeeId.equals(task.assignedTo())) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "You can only submit your own tasks"));
        }

        Task submitted = taskCheckout.submitTask(taskId, employeeId, request.result());

        Map<String, Object> eventData = new java.util.LinkedHashMap<>();
        eventData.put("taskId", taskId);
        eventData.put("employeeId", employeeId);
        eventData.put("status", "SUBMITTED");
        runtimeEventStore.appendTaskEvent("_system", taskId, "task_submitted", eventData);

        TaskSubmitResult submitResult = new TaskSubmitResult(submitted.taskId(), submitted.taskType(), employeeId, Instant.now().toString(), "SUBMITTED", "Task submitted, awaiting review");
        return ResponseEntity.ok(ApiResponse.ok(submitResult));
    }

    @PostMapping("/{taskId}/review")
    public ResponseEntity<ApiResponse<TaskReviewResult>> reviewTask(
            @PathVariable String taskId,
            @RequestBody ReviewTaskRequest request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "OpsBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }

        AuthContext ctx = resolveAuthContext(authorization);
        if (ctx != null && !workItemPermissionService.canReviewTask(taskId, ctx)) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "No permission to review this task"));
        }

        log.info("Reviewer {} reviewing task {}: approved={}", request.reviewerId(), taskId, request.approved());

        Optional<Task> taskOpt = taskCheckout.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.err("not_found", "Task not found: " + taskId));
        }

        Task task = taskOpt.get();
        if (task.status() != TaskCheckout.TaskStatus.SUBMITTED && task.status() != TaskCheckout.TaskStatus.CHECKED_OUT) {
            return ResponseEntity.badRequest().body(ApiResponse.err("invalid_status", "Task is not in a reviewable state: " + task.status()));
        }

        taskCheckout.reviewTask(taskId, request.reviewerId(), request.approved(), request.comment());

        int baseReward = calculateBaseReward(task);
        double qualityMultiplier = calculateQualityMultiplier(request.qualityScore());
        double timelinessMultiplier = calculateTimelinessMultiplier(request.timelinessScore());
        int totalReward = (int) (baseReward * qualityMultiplier * timelinessMultiplier);

        TaskReviewResult result = new TaskReviewResult(taskId, task.assignedTo(), request.reviewerId(), request.approved(), totalReward, request.qualityScore(), request.comment(), Instant.now().toString());

        taskWorkflowService.summarizeReview(result);
        taskEventBridgeService.onTaskReviewed(
                task.context() != null && task.context().get("department") != null ? String.valueOf(task.context().get("department")) : "ops",
                taskId,
                task.assignedTo(),
                request.approved(),
                totalReward,
                request.qualityScore());
        taskPerformanceBridgeService.onTaskReview(
                task.context() != null && task.context().get("department") != null ? String.valueOf(task.context().get("department")) : "ops",
                task.assignedTo(),
                request.approved(),
                totalReward,
                request.qualityScore(),
                taskId);

        Map<String, Object> reviewEventData = new java.util.LinkedHashMap<>();
        reviewEventData.put("taskId", taskId);
        reviewEventData.put("reviewerId", request.reviewerId());
        reviewEventData.put("approved", request.approved());
        reviewEventData.put("qualityScore", request.qualityScore());
        reviewEventData.put("rewardGranted", totalReward);
        runtimeEventStore.appendTaskEvent("_system", taskId, "task_reviewed", reviewEventData);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private TaskSummary toSummary(Task task) {
        return new TaskSummary(task.taskId(), task.taskType(), task.description(), task.priority(), task.requiredCapability(), task.status().name(), task.assignedTo(), task.createdAt(), task.checkedOutAt(), task.completedAt());
    }

    private TaskDetail toDetail(Task task) {
        return new TaskDetail(task.taskId(), task.taskType(), task.description(), task.priority(), task.requiredCapability(), task.context(), task.status().name(), task.createdAt(), task.checkedOutAt(), task.assignedTo(), task.completedAt());
    }

    private PublicTaskSummary toPublicSummary(Task task) {
        return new PublicTaskSummary(task.taskId(), task.taskType(), task.description(), task.priority(), task.requiredCapability(), task.context() != null ? String.valueOf(task.context().getOrDefault("difficulty", "INTERMEDIATE")) : "INTERMEDIATE", task.context() != null ? Integer.parseInt(String.valueOf(task.context().getOrDefault("estimatedHours", 2))) : 2, task.context() != null ? Integer.parseInt(String.valueOf(task.context().getOrDefault("reward", 100))) : 100, task.createdAt().toString());
    }

    private int calculateBaseReward(Task task) {
        int base = task.priority() * 50;
        String difficulty = task.context() != null ? String.valueOf(task.context().getOrDefault("difficulty", "INTERMEDIATE")) : "INTERMEDIATE";
        int difficultyMultiplier = switch (difficulty.toUpperCase()) {
            case "BEGINNER" -> 1;
            case "INTERMEDIATE" -> 2;
            case "ADVANCED" -> 3;
            case "EXPERT" -> 4;
            case "MASTER" -> 5;
            default -> 2;
        };
        return base + difficultyMultiplier * 100;
    }

    private double calculateQualityMultiplier(double qualityScore) {
        if (qualityScore >= 0.98) return 1.5;
        if (qualityScore >= 0.95) return 1.3;
        if (qualityScore >= 0.90) return 1.2;
        if (qualityScore >= 0.80) return 1.0;
        return 0.8;
    }

    private double calculateTimelinessMultiplier(double timelinessScore) {
        if (timelinessScore >= 0.95) return 1.3;
        if (timelinessScore >= 0.80) return 1.1;
        if (timelinessScore >= 0.60) return 1.0;
        return 0.8;
    }

    public record TaskSummary(String taskId, String taskType, String description, int priority, String requiredCapability, String status, String assignedTo, Instant createdAt, Instant checkedOutAt, Instant completedAt) {}
    public record TaskDetail(String taskId, String taskType, String description, int priority, String requiredCapability, Map<String, Object> context, String status, Instant createdAt, Instant checkedOutAt, String assignedTo, Instant completedAt) {}
    public record CreateTaskRequest(String taskId, String taskType, String description, Integer priority, String requiredCapability, Map<String, Object> context) {}
    public record CheckoutRequest(String employeeId, List<String> capabilities) {}
    public record CompleteTaskRequest(String employeeId, boolean success, String output, String error, Map<String, Object> metrics) {}
    public record ReleaseRequest(String employeeId, String reason) {}
    public record ReassignRequest(String fromEmployeeId, String toEmployeeId) {}
    public record PublicTaskSummary(String taskId, String taskType, String description, int priority, String requiredCapability, String difficulty, int estimatedHours, int reward, String createdAt) {}
    public record ClaimTaskRequest(String employeeId, String message) {}
    public record TaskClaimResult(String taskId, String taskType, String description, String claimedBy, String claimedAt, String message) {}
    public record SubmitTaskRequest(String employeeId, String result, String notes, Map<String, Object> metrics) {
        public SubmitTaskRequest {
            if (metrics == null) metrics = Map.of();
        }
    }
    public record TaskSubmitResult(String taskId, String taskType, String submittedBy, String submittedAt, String status, String message) {}
    public record ReviewTaskRequest(String reviewerId, boolean approved, double qualityScore, double timelinessScore, String comment) {}
    public record TaskReviewResult(String taskId, String employeeId, String reviewerId, boolean approved, int rewardGranted, double qualityScore, String comment, String reviewedAt) {}

    private AuthContext resolveAuthContext(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7);
        if (token.isBlank()) {
            return null;
        }
        return unifiedAuthService.validateSession(token)
            .map(AuthSession::authContext)
            .orElse(null);
    }

    // ==================== 员工执行历史查询 ====================

    /**
     * 查询员工的执行历史（从 receipt 中查询）
     * 数字员工可以查看自己的任务执行历史
     */
    @GetMapping("/executions/my")
    public ResponseEntity<ApiResponse<List<ExecutionReceiptSummary>>> getMyExecutions(
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Employee-Code", required = false) String headerEmployeeCode
    ) {
        AuthContext ctx = resolveAuthContext(authorization);
        String employeeCode;
        if (ctx != null) {
            employeeCode = ctx.getEmployeeId();
        } else if (headerEmployeeCode != null && !headerEmployeeCode.isBlank()) {
            employeeCode = headerEmployeeCode;
        } else {
            return ResponseEntity.status(401).body(ApiResponse.err("unauthorized", "Authentication required"));
        }

        List<EmployeeExecutionReceiptEntity> receipts = receiptRepository.findByEmployeeCode(employeeCode);
        List<ExecutionReceiptSummary> summaries = receipts.stream()
            .limit(limit)
            .map(this::toReceiptSummary)
            .toList();

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    /**
     * 查询指定执行ID的所有员工回执（部门任务进展）
     * 用于查看一个任务的整体进展情况
     */
    @GetMapping("/executions/{executionId}/progress")
    public ResponseEntity<ApiResponse<ExecutionProgress>> getExecutionProgress(
            @PathVariable String executionId
    ) {
        List<EmployeeExecutionReceiptEntity> receipts = receiptRepository.findByExecutionId(executionId);
        if (receipts.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(new ExecutionProgress(executionId, 0, 0, 0, List.of())));
        }

        int total = receipts.size();
        int completed = (int) receipts.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
        int failed = (int) receipts.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        List<ExecutionReceiptSummary> summaries = receipts.stream().map(this::toReceiptSummary).toList();

        return ResponseEntity.ok(ApiResponse.ok(new ExecutionProgress(executionId, total, completed, failed, summaries)));
    }

    /**
     * 查询部门的任务执行历史
     * 用于部门大脑了解本部门的任务执行情况
     */
    @GetMapping("/executions/department/{department}")
    public ResponseEntity<ApiResponse<List<ExecutionReceiptSummary>>> getDepartmentExecutions(
            @PathVariable String department,
            @RequestParam(defaultValue = "50") int limit
    ) {
        // 从 artifacts 目录推断执行ID，然后查询 receipt
        // 简化实现：查询所有 receipt，按部门过滤
        List<EmployeeExecutionReceiptEntity> allReceipts = receiptRepository.findAll();
        List<ExecutionReceiptSummary> summaries = allReceipts.stream()
            .filter(r -> r.getSummary() != null && r.getSummary().contains(department))
            .limit(limit)
            .map(this::toReceiptSummary)
            .toList();

        return ResponseEntity.ok(ApiResponse.ok(summaries));
    }

    private ExecutionReceiptSummary toReceiptSummary(EmployeeExecutionReceiptEntity entity) {
        return new ExecutionReceiptSummary(
            entity.getReceiptId(),
            entity.getExecutionId(),
            entity.getEmployeeCode(),
            entity.getEmployeeNeuronId(),
            entity.getStatus(),
            entity.getSummary() != null && entity.getSummary().length() > 200
                ? entity.getSummary().substring(0, 200) + "..."
                : entity.getSummary(),
            entity.getReceivedAt(),
            extractMetadataField(entity.getMetadataJson(), "modelName"),
            extractMetadataField(entity.getMetadataJson(), "modelProvider")
        );
    }

    private String extractMetadataField(String metadataJson, String field) {
        if (metadataJson == null || metadataJson.isBlank()) return null;
        try {
            // 简单 JSON 字段提取，避免引入 ObjectMapper
            String search = "\"" + field + "\"";
            int idx = metadataJson.indexOf(search);
            if (idx < 0) return null;
            int colonIdx = metadataJson.indexOf(':', idx);
            if (colonIdx < 0) return null;
            int quoteStart = metadataJson.indexOf('"', colonIdx + 1);
            if (quoteStart < 0) return null;
            int quoteEnd = metadataJson.indexOf('"', quoteStart + 1);
            if (quoteEnd < 0) return null;
            return metadataJson.substring(quoteStart + 1, quoteEnd);
        } catch (Exception e) {
            return null;
        }
    }

    public record ExecutionReceiptSummary(
        String receiptId,
        String executionId,
        String employeeCode,
        String employeeNeuronId,
        String status,
        String summary,
        Instant completedAt,
        String modelName,
        String modelProvider
    ) {}

    public record ExecutionProgress(
        String executionId,
        int totalCount,
        int completedCount,
        int failedCount,
        List<ExecutionReceiptSummary> receipts
    ) {}
}
