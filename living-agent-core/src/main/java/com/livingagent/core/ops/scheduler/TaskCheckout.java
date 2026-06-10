package com.livingagent.core.ops.scheduler;

import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.channel.ChannelManager;
import com.livingagent.core.channel.ChannelMessage;
import com.livingagent.core.database.entity.TaskEntity;
import com.livingagent.core.database.repository.TaskRepository;
import com.livingagent.core.employee.Employee;
import com.livingagent.core.employee.EmployeeService;
import com.livingagent.core.employee.impl.DigitalEmployee;
import com.livingagent.core.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TaskCheckout {

    private static final Logger log = LoggerFactory.getLogger(TaskCheckout.class);

    private static final int MAX_COMPLETED_TASKS = 500;

    private final Map<String, Task> pendingTasks = new ConcurrentHashMap<>();
    private final Map<String, Task> checkedOutTasks = new ConcurrentHashMap<>();
    private final Map<String, Task> completedTasks = new ConcurrentHashMap<>();
    private final Deque<String> completedTaskOrder = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private final Map<String, CheckoutRecord> checkoutRecords = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> employeeTaskCounts = new ConcurrentHashMap<>();
    
    private final EmployeeService employeeService;
    private final ChannelManager channelManager;
    private final TaskRepository taskRepository;
    private final int maxConcurrentPerEmployee = 3;
    private final long checkoutTimeoutMs = 30 * 60 * 1000;

    public TaskCheckout(EmployeeService employeeService, ChannelManager channelManager, TaskRepository taskRepository) {
        this.employeeService = employeeService;
        this.channelManager = channelManager;
        this.taskRepository = taskRepository;
    }

    public Task createTask(String taskId, String taskType, String description, 
                          int priority, String requiredCapability, Map<String, Object> context) {
        Task task = new Task(
            taskId,
            taskType,
            description,
            priority,
            requiredCapability,
            context,
            TaskStatus.PENDING,
            Instant.now(),
            null,
            null,
            null
        );
        
        pendingTasks.put(taskId, task);
        persistTask(task);
        log.info("Created task: {} type={} priority={}", taskId, taskType, priority);
        return task;
    }

    public Optional<Task> checkoutTask(String employeeId, List<String> capabilities) {
        List<Task> eligibleTasks = pendingTasks.values().stream()
            .filter(t -> t.status() == TaskStatus.PENDING)
            .filter(t -> capabilities.contains(t.requiredCapability()) || t.requiredCapability() == null)
            .filter(t -> !hasConflict(t, employeeId))
            .sorted(Comparator.comparingInt(Task::priority).reversed()
                .thenComparing(Task::createdAt))
            .toList();
        
        if (eligibleTasks.isEmpty()) {
            return Optional.empty();
        }
        
        int currentCount = getEmployeeActiveCount(employeeId);
        if (currentCount >= maxConcurrentPerEmployee) {
            log.debug("Employee {} has max concurrent tasks ({}), skipping checkout", 
                employeeId, currentCount);
            return Optional.empty();
        }
        
        Task selected = eligibleTasks.get(0);
        Task checkedOut = new Task(
            selected.taskId(),
            selected.taskType(),
            selected.description(),
            selected.priority(),
            selected.requiredCapability(),
            selected.context(),
            TaskStatus.CHECKED_OUT,
            selected.createdAt(),
            Instant.now(),
            employeeId,
            null
        );
        
        pendingTasks.remove(selected.taskId());
        checkedOutTasks.put(selected.taskId(), checkedOut);
        
        CheckoutRecord record = new CheckoutRecord(
            UUID.randomUUID().toString(),
            selected.taskId(),
            employeeId,
            Instant.now(),
            null,
            CheckoutStatus.ACTIVE
        );
        checkoutRecords.put(selected.taskId(), record);
        
        employeeTaskCounts.computeIfAbsent(employeeId, k -> new AtomicLong(0)).incrementAndGet();
        
        sendTaskToEmployee(checkedOut, employeeId);
        
        log.info("Checked out task {} to employee {}", selected.taskId(), employeeId);
        return Optional.of(checkedOut);
    }

    private void sendTaskToEmployee(Task task, String employeeId) {
        employeeService.getEmployee(employeeId).ifPresent(emp -> {
            if (!emp.isDigital()) {
                log.debug("Employee {} is not digital, skipping channel dispatch", employeeId);
                return;
            }
            
            DigitalEmployee de = (DigitalEmployee) emp;
            List<String> channels = de.getDigitalConfig().getSubscribeChannels();
            
            if (channels.isEmpty()) {
                log.warn("Employee {} has no subscribed channels", employeeId);
                return;
            }
            
            String targetChannel = channels.get(0);
            
            ChannelMessage message = ChannelMessage.text(
                targetChannel,
                "task-dispatcher",
                targetChannel,
                UUID.randomUUID().toString(),
                formatTaskMessage(task)
            );
            
            message.addMetadata("taskId", task.taskId());
            message.addMetadata(TaskMetadataKeys.TASK_TYPE, task.taskType());
            message.addMetadata("priority", task.priority());
            message.addMetadata("assignedEmployee", employeeId);
            message.addMetadata("dispatchedAt", Instant.now().toString());
            
            try {
                channelManager.publish(targetChannel, message);
                log.info("Dispatched task {} to employee {} via channel {}", 
                    task.taskId(), employeeId, targetChannel);
            } catch (Exception e) {
                log.error("Failed to dispatch task {} to employee {}: {}", 
                    task.taskId(), employeeId, e.getMessage());
            }
        });
    }

    private String formatTaskMessage(Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 任务分配\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("任务ID: ").append(task.taskId()).append("\n");
        sb.append("类型: ").append(task.taskType()).append("\n");
        sb.append("优先级: ").append(task.priority()).append("\n");
        sb.append("描述: ").append(task.description()).append("\n");
        if (task.requiredCapability() != null) {
            sb.append("所需能力: ").append(task.requiredCapability()).append("\n");
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━");
        return sb.toString();
    }

    public Optional<Task> checkoutSpecificTask(String taskId, String employeeId) {
        Task task = pendingTasks.get(taskId);
        if (task == null || task.status() != TaskStatus.PENDING) {
            return Optional.empty();
        }
        
        int currentCount = getEmployeeActiveCount(employeeId);
        if (currentCount >= maxConcurrentPerEmployee) {
            log.warn("Employee {} has max concurrent tasks, cannot checkout {}", employeeId, taskId);
            return Optional.empty();
        }
        
        Task checkedOut = new Task(
            task.taskId(),
            task.taskType(),
            task.description(),
            task.priority(),
            task.requiredCapability(),
            task.context(),
            TaskStatus.CHECKED_OUT,
            task.createdAt(),
            Instant.now(),
            employeeId,
            null
        );
        
        pendingTasks.remove(taskId);
        checkedOutTasks.put(taskId, checkedOut);
        
        CheckoutRecord record = new CheckoutRecord(
            UUID.randomUUID().toString(),
            taskId,
            employeeId,
            Instant.now(),
            null,
            CheckoutStatus.ACTIVE
        );
        checkoutRecords.put(taskId, record);
        
        employeeTaskCounts.computeIfAbsent(employeeId, k -> new AtomicLong(0)).incrementAndGet();
        
        persistTask(checkedOut);
        sendTaskToEmployee(checkedOut, employeeId);
        
        log.info("Checked out specific task {} to employee {}", taskId, employeeId);
        return Optional.of(checkedOut);
    }

    public Task completeTask(String taskId, String employeeId, TaskResult result) {
        Task task = checkedOutTasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found or not checked out: " + taskId);
        }
        
        if (!employeeId.equals(task.assignedTo())) {
            throw new IllegalStateException("Task not assigned to employee: " + employeeId);
        }
        
        Task completed = new Task(
            task.taskId(),
            task.taskType(),
            task.description(),
            task.priority(),
            task.requiredCapability(),
            task.context(),
            result.success() ? TaskStatus.COMPLETED : TaskStatus.FAILED,
            task.createdAt(),
            task.checkedOutAt(),
            task.assignedTo(),
            Instant.now()
        );
        
        checkedOutTasks.remove(taskId);
        completedTasks.put(taskId, completed);
        completedTaskOrder.addLast(taskId);
        evictCompletedIfNeeded();
        
        CheckoutRecord record = checkoutRecords.get(taskId);
        if (record != null) {
            CheckoutRecord completedRecord = new CheckoutRecord(
                record.recordId(),
                record.taskId(),
                record.employeeId(),
                record.checkedOutAt(),
                Instant.now(),
                result.success() ? CheckoutStatus.COMPLETED : CheckoutStatus.FAILED
            );
            checkoutRecords.put(taskId, completedRecord);
        }
        
        employeeTaskCounts.getOrDefault(employeeId, new AtomicLong(0)).decrementAndGet();
        
        persistTask(completed);
        notifyTaskCompletion(completed, result);
        
        log.info("Completed task {} by employee {} - success={}", taskId, employeeId, result.success());
        return completed;
    }

    private void notifyTaskCompletion(Task task, TaskResult result) {
        String resultChannel = "channel://task/results";
        
        ChannelMessage message = ChannelMessage.text(
            resultChannel,
            task.assignedTo(),
            resultChannel,
            UUID.randomUUID().toString(),
            formatResultMessage(task, result)
        );
        
        message.addMetadata("taskId", task.taskId());
        message.addMetadata("success", result.success());
        message.addMetadata("completedAt", Instant.now().toString());
        
        try {
            channelManager.publish(resultChannel, message);
        } catch (Exception e) {
            log.warn("Failed to notify task completion: {}", e.getMessage());
        }
    }

    private String formatResultMessage(Task task, TaskResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.success() ? "✅" : "❌").append(" 任务完成\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("任务ID: ").append(task.taskId()).append("\n");
        sb.append("状态: ").append(result.success() ? "成功" : "失败").append("\n");
        if (result.output() != null) {
            sb.append("输出: ").append(result.output()).append("\n");
        }
        if (result.error() != null) {
            sb.append("错误: ").append(result.error()).append("\n");
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━");
        return sb.toString();
    }

    public Task releaseTask(String taskId, String employeeId, String reason) {
        Task task = checkedOutTasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found or not checked out: " + taskId);
        }
        
        Task released = new Task(
            task.taskId(),
            task.taskType(),
            task.description(),
            task.priority(),
            task.requiredCapability(),
            task.context(),
            TaskStatus.PENDING,
            task.createdAt(),
            null,
            null,
            null
        );
        
        checkedOutTasks.remove(taskId);
        pendingTasks.put(taskId, released);
        
        CheckoutRecord record = checkoutRecords.get(taskId);
        if (record != null) {
            CheckoutRecord releasedRecord = new CheckoutRecord(
                record.recordId(),
                record.taskId(),
                record.employeeId(),
                record.checkedOutAt(),
                Instant.now(),
                CheckoutStatus.RELEASED
            );
            checkoutRecords.put(taskId, releasedRecord);
        }
        
        employeeTaskCounts.getOrDefault(employeeId, new AtomicLong(0)).decrementAndGet();
        
        persistTask(released);
        log.info("Released task {} from employee {} - reason: {}", taskId, employeeId, reason);
        return released;
    }

    public void reassignTask(String taskId, String fromEmployeeId, String toEmployeeId) {
        Task task = checkedOutTasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found or not checked out: " + taskId);
        }
        
        Task reassigned = new Task(
            task.taskId(),
            task.taskType(),
            task.description(),
            task.priority(),
            task.requiredCapability(),
            task.context(),
            TaskStatus.CHECKED_OUT,
            task.createdAt(),
            task.checkedOutAt(),
            toEmployeeId,
            null
        );
        
        checkedOutTasks.put(taskId, reassigned);
        
        employeeTaskCounts.getOrDefault(fromEmployeeId, new AtomicLong(0)).decrementAndGet();
        employeeTaskCounts.computeIfAbsent(toEmployeeId, k -> new AtomicLong(0)).incrementAndGet();
        
        persistTask(reassigned);
        sendTaskToEmployee(reassigned, toEmployeeId);
        
        log.info("Reassigned task {} from {} to {}", taskId, fromEmployeeId, toEmployeeId);
    }

    public List<Task> getPendingTasks() {
        return new ArrayList<>(pendingTasks.values());
    }

    public List<Task> getPendingTasksByCapability(String capability) {
        return pendingTasks.values().stream()
            .filter(t -> capability.equals(t.requiredCapability()))
            .sorted(Comparator.comparingInt(Task::priority).reversed())
            .toList();
    }

    public List<Task> getCheckedOutTasks(String employeeId) {
        return checkedOutTasks.values().stream()
            .filter(t -> employeeId.equals(t.assignedTo()))
            .toList();
    }

    public List<Task> getAllCheckedOutTasks() {
        return new ArrayList<>(checkedOutTasks.values());
    }

    public List<Task> getCompletedTasks(int limit) {
        return completedTasks.values().stream()
            .sorted(Comparator.comparing(Task::completedAt).reversed())
            .limit(limit)
            .toList();
    }

    public Optional<Task> getTask(String taskId) {
        return Optional.ofNullable(pendingTasks.get(taskId))
            .or(() -> Optional.ofNullable(checkedOutTasks.get(taskId)))
            .or(() -> Optional.ofNullable(completedTasks.get(taskId)));
    }

    public TaskStatistics getStatistics() {
        return new TaskStatistics(
            pendingTasks.size(),
            checkedOutTasks.size(),
            completedTasks.size(),
            employeeTaskCounts.entrySet().stream()
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().get()), HashMap::putAll)
        );
    }

    public void checkTimeouts() {
        Instant now = Instant.now();
        List<String> timedOut = new ArrayList<>();
        
        for (Task task : checkedOutTasks.values()) {
            if (task.checkedOutAt() != null) {
                long elapsed = now.toEpochMilli() - task.checkedOutAt().toEpochMilli();
                if (elapsed > checkoutTimeoutMs) {
                    timedOut.add(task.taskId());
                }
            }
        }
        
        for (String taskId : timedOut) {
            Task task = checkedOutTasks.get(taskId);
            if (task != null && task.assignedTo() != null) {
                releaseTask(taskId, task.assignedTo(), "Timeout");
                log.warn("Task {} timed out, released from {}", taskId, task.assignedTo());
            }
        }
    }

    private void persistTask(Task task) {
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
            // 优先使用 Task record 的 projectId 字段
            if (task.projectId() != null) {
                entity.setProjectId(task.projectId());
            }
            if (task.context() != null) {
                Object userId = task.context().get("userId");
                if (userId != null) entity.setUserId(String.valueOf(userId));
                Object department = task.context().get("department");
                if (department != null) entity.setDepartmentCode(String.valueOf(department));
                Object executionId = task.context().get("executionId");
                if (executionId != null) entity.setExecutionId(String.valueOf(executionId));
                Object taskKey = task.context().get("taskKey");
                if (taskKey != null) entity.setTaskKey(String.valueOf(taskKey));
                // 如果 Task record 没有设置 projectId，从 context 中获取
                if (entity.getProjectId() == null) {
                    Object projectId = task.context().get(TaskMetadataKeys.PROJECT_ID);
                    if (projectId != null) entity.setProjectId(String.valueOf(projectId));
                }
                Object sourceType = task.context().get("sourceType");
                if (sourceType != null) entity.setSourceType(String.valueOf(sourceType));
            }
            taskRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist task {}: {}", task.taskId(), e.getMessage());
        }
    }

    public Task submitTask(String taskId, String employeeId, String result) {
        Task task = checkedOutTasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found or not checked out: " + taskId);
        }
        if (!employeeId.equals(task.assignedTo())) {
            throw new IllegalStateException("Task not assigned to employee: " + employeeId);
        }
        Task submitted = new Task(
            task.taskId(),
            task.taskType(),
            task.description(),
            task.priority(),
            task.requiredCapability(),
            task.context(),
            TaskStatus.SUBMITTED,
            task.createdAt(),
            task.checkedOutAt(),
            task.assignedTo(),
            null
        );
        checkedOutTasks.put(taskId, submitted);
        try {
            Optional<TaskEntity> existing = taskRepository.findByTaskId(taskId);
            if (existing.isPresent()) {
                TaskEntity entity = existing.get();
                entity.setStatus(TaskStatus.SUBMITTED.name());
                entity.setSubmissionResult(result);
                entity.setSubmittedAt(Instant.now());
                entity.setUpdatedAt(Instant.now());
                taskRepository.save(entity);
            }
        } catch (Exception e) {
            log.warn("Failed to persist task submission {}: {}", taskId, e.getMessage());
        }
        log.info("Submitted task {} by employee {}", taskId, employeeId);
        return submitted;
    }

    public Task reviewTask(String taskId, String reviewerId, boolean approved, String conclusion) {
        Task task = checkedOutTasks.get(taskId);
        if (task == null) {
            task = completedTasks.get(taskId);
        }
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        TaskStatus newStatus = approved ? TaskStatus.COMPLETED : TaskStatus.NEEDS_REWORK;
        Task reviewed = new Task(
            task.taskId(),
            task.taskType(),
            task.description(),
            task.priority(),
            task.requiredCapability(),
            task.context(),
            newStatus,
            task.createdAt(),
            task.checkedOutAt(),
            task.assignedTo(),
            approved ? Instant.now() : null
        );
        if (approved) {
            checkedOutTasks.remove(taskId);
            completedTasks.put(taskId, reviewed);
            completedTaskOrder.addLast(taskId);
            evictCompletedIfNeeded();
            if (task.assignedTo() != null) {
                employeeTaskCounts.getOrDefault(task.assignedTo(), new AtomicLong(0)).decrementAndGet();
            }
        } else {
            checkedOutTasks.put(taskId, reviewed);
        }
        try {
            Optional<TaskEntity> existing = taskRepository.findByTaskId(taskId);
            if (existing.isPresent()) {
                TaskEntity entity = existing.get();
                entity.setStatus(newStatus.name());
                entity.setReviewerId(reviewerId);
                entity.setReviewConclusion(conclusion);
                entity.setReviewedAt(Instant.now());
                if (approved) {
                    entity.setCompletedAt(Instant.now());
                }
                entity.setUpdatedAt(Instant.now());
                taskRepository.save(entity);
            }
        } catch (Exception e) {
            log.warn("Failed to persist task review {}: {}", taskId, e.getMessage());
        }
        log.info("Reviewed task {} by reviewer {} - approved={}", taskId, reviewerId, approved);
        return reviewed;
    }

    public Task requestClarification(String taskId, List<String> clarificationQuestions, List<String> blockingIssues, String readinessStatus) {
        Task task = checkedOutTasks.get(taskId);
        if (task == null) {
            task = pendingTasks.get(taskId);
        }
        if (task == null) {
            task = completedTasks.get(taskId);
        }
        if (task == null) {
            throw new IllegalArgumentException("Task not found: " + taskId);
        }
        TaskStatus newStatus = blockingIssues != null && !blockingIssues.isEmpty()
            ? TaskStatus.NEEDS_CLARIFICATION
            : TaskStatus.CLARIFICATION_PENDING;
        Task clarified = new Task(
            task.taskId(),
            task.taskType(),
            task.description(),
            task.priority(),
            task.requiredCapability(),
            task.context(),
            newStatus,
            task.createdAt(),
            task.checkedOutAt(),
            task.assignedTo(),
            null
        );
        checkedOutTasks.put(taskId, clarified);
        try {
            Optional<TaskEntity> existing = taskRepository.findByTaskId(taskId);
            TaskEntity entity = existing.orElseGet(TaskEntity::new);
            entity.setTaskId(task.taskId());
            entity.setStatus(newStatus.name());
            entity.setReadinessStatus(readinessStatus);
            entity.setClarificationQuestions(clarificationQuestions != null ? String.join("\n", clarificationQuestions) : null);
            entity.setBlockingIssues(blockingIssues != null ? String.join("\n", blockingIssues) : null);
            entity.setClarificationRequestedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            taskRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist task clarification {}: {}", taskId, e.getMessage());
        }
        log.info("Task {} moved to {} with {} clarification questions", taskId, newStatus,
            clarificationQuestions != null ? clarificationQuestions.size() : 0);
        return clarified;
    }

    public Task resolveClarification(String taskId, String clarificationAnswer) {
        Task task = checkedOutTasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found or not in clarification state: " + taskId);
        }
        if (task.status() != TaskStatus.NEEDS_CLARIFICATION && task.status() != TaskStatus.CLARIFICATION_PENDING) {
            throw new IllegalStateException("Task is not in clarification state: " + taskId + ", current: " + task.status());
        }
        Task resumed = new Task(
            task.taskId(),
            task.taskType(),
            task.description(),
            task.priority(),
            task.requiredCapability(),
            task.context(),
            TaskStatus.PENDING,
            task.createdAt(),
            null,
            null,
            null
        );
        checkedOutTasks.remove(taskId);
        pendingTasks.put(taskId, resumed);
        try {
            Optional<TaskEntity> existing = taskRepository.findByTaskId(taskId);
            if (existing.isPresent()) {
                TaskEntity entity = existing.get();
                entity.setStatus(TaskStatus.PENDING.name());
                entity.setClarificationAnswer(clarificationAnswer);
                entity.setUpdatedAt(Instant.now());
                taskRepository.save(entity);
            }
        } catch (Exception e) {
            log.warn("Failed to persist task clarification resolution {}: {}", taskId, e.getMessage());
        }
        log.info("Task {} clarification resolved, moved back to PENDING", taskId);
        return resumed;
    }

    private void evictCompletedIfNeeded() {
        while (completedTasks.size() > MAX_COMPLETED_TASKS) {
            String oldest = completedTaskOrder.pollFirst();
            if (oldest != null) {
                completedTasks.remove(oldest);
            } else {
                break;
            }
        }
    }

    private boolean hasConflict(Task task, String employeeId) {
        return false;
    }

    private int getEmployeeActiveCount(String employeeId) {
        return (int) checkedOutTasks.values().stream()
            .filter(t -> employeeId.equals(t.assignedTo()))
            .count();
    }

    public record Task(
        String taskId,
        String taskType,
        String description,
        int priority,
        String requiredCapability,
        Map<String, Object> context,
        TaskStatus status,
        Instant createdAt,
        Instant checkedOutAt,
        String assignedTo,
        Instant completedAt,
        String projectId
    ) {
        /** 兼容旧构造：无 projectId */
        public Task(String taskId, String taskType, String description,
                     int priority, String requiredCapability, Map<String, Object> context,
                     TaskStatus status, Instant createdAt, Instant checkedOutAt,
                     String assignedTo, Instant completedAt) {
            this(taskId, taskType, description, priority, requiredCapability, context,
                 status, createdAt, checkedOutAt, assignedTo, completedAt, null);
        }
    }

    public record TaskResult(
        String taskId,
        boolean success,
        String output,
        String error,
        Map<String, Object> metrics
    ) {
        public static TaskResult success(String taskId, String output) {
            return new TaskResult(taskId, true, output, null, Map.of());
        }
        
        public static TaskResult success(String taskId, String output, Map<String, Object> metrics) {
            return new TaskResult(taskId, true, output, null, metrics);
        }
        
        public static TaskResult failure(String taskId, String error) {
            return new TaskResult(taskId, false, null, error, Map.of());
        }
    }

    public record CheckoutRecord(
        String recordId,
        String taskId,
        String employeeId,
        Instant checkedOutAt,
        Instant completedAt,
        CheckoutStatus status
    ) {}

    public record TaskStatistics(
        int pendingCount,
        int checkedOutCount,
        int completedCount,
        Map<String, Long> employeeActiveCounts
    ) {}

    public enum TaskStatus {
        PENDING,
        CHECKED_OUT,
        IN_PROGRESS,
        NEEDS_CLARIFICATION,
        CLARIFICATION_PENDING,
        SUBMITTED,
        PENDING_REVIEW,
        REVIEWED,
        COMPLETED,
        REJECTED,
        NEEDS_REWORK,
        FAILED,
        CANCELLED
    }

    public enum CheckoutStatus {
        ACTIVE,
        COMPLETED,
        FAILED,
        RELEASED,
        TIMEOUT
    }
}
