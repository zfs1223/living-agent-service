package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProactiveAgentTool implements Tool {
    private static final String NAME = "proactive_agent";
    private static final String DESCRIPTION = "主动代理技能，使智能体能主动执行任务，支持定时任务、条件触发任务、持续任务";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "automation";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ProactiveTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, TaskExecution> executionHistory = new ConcurrentHashMap<>();
    private ToolStats stats = ToolStats.empty(NAME);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: schedule(创建任务), cancel(取消任务), list(列出任务), status(任务状态), execute(立即执行)", true)
                .parameter("task_id", "string", "任务ID，用于取消或查询状态", false)
                .parameter("task_type", "string", "任务类型: scheduled(定时), conditional(条件触发), continuous(持续)", false)
                .parameter("trigger", "string", "触发条件: cron表达式或条件表达式", false)
                .parameter("action_spec", "string", "任务执行的动作描述", false)
                .parameter("priority", "string", "优先级: critical, high, medium, low", false)
                .parameter("max_executions", "integer", "最大执行次数，0表示无限", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("scheduled_tasks", "conditional_triggers", "continuous_monitoring", "task_management");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("action parameter is required");
        }

        try {
            ToolResult result;
            switch (action.toLowerCase()) {
                case "schedule":
                    result = scheduleTask(params, context);
                    break;
                case "cancel":
                    result = cancelTask(params);
                    break;
                case "list":
                    result = listTasks(params);
                    break;
                case "status":
                    result = getTaskStatus(params);
                    break;
                case "execute":
                    result = executeTaskNow(params, context);
                    break;
                case "history":
                    result = getExecutionHistory(params);
                    break;
                default:
                    result = ToolResult.failure("Unknown action: " + action);
            }
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Error executing proactive agent: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("action parameter is required");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    private ToolResult scheduleTask(ToolParams params, ToolContext context) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        String taskType = params.getString("task_type");
        if (taskType == null) taskType = "scheduled";
        
        String trigger = params.getString("trigger");
        String actionSpec = params.getString("action_spec");
        String priority = params.getString("priority");
        if (priority == null) priority = "medium";
        
        Integer maxExecutionsInt = params.getInteger("max_executions");
        int maxExecutions = maxExecutionsInt != null ? maxExecutionsInt : 0;

        if (actionSpec == null) {
            return ToolResult.failure("action_spec is required for scheduling a task");
        }

        ProactiveTask task = new ProactiveTask(
                taskId,
                taskType,
                trigger,
                actionSpec,
                TaskPriority.fromString(priority),
                maxExecutions,
                System.currentTimeMillis()
        );

        tasks.put(taskId, task);

        if ("scheduled".equals(taskType) && trigger != null) {
            scheduleCronTask(task, context);
        } else if ("continuous".equals(taskType)) {
            scheduleContinuousTask(task, context);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("task_id", taskId);
        result.put("status", "scheduled");
        result.put("task_type", taskType);
        result.put("trigger", trigger);
        result.put("action", actionSpec);
        result.put("priority", priority);

        return ToolResult.success(result);
    }

    private void scheduleCronTask(ProactiveTask task, ToolContext context) {
        long initialDelay = parseTriggerToDelay(task.getTrigger());
        long period = parseTriggerToPeriod(task.getTrigger());

        scheduler.scheduleAtFixedRate(() -> {
            executeTaskInternal(task, context);
        }, initialDelay, period > 0 ? period : initialDelay, TimeUnit.MILLISECONDS);
    }

    private void scheduleContinuousTask(ProactiveTask task, ToolContext context) {
        scheduler.scheduleWithFixedDelay(() -> {
            executeTaskInternal(task, context);
        }, 0, 60000, TimeUnit.MILLISECONDS);
    }

    private long parseTriggerToDelay(String trigger) {
        if (trigger == null) return 60000L;
        
        if (trigger.startsWith("every:")) {
            String interval = trigger.substring(6);
            return parseInterval(interval);
        }
        
        return 60000L;
    }

    private long parseTriggerToPeriod(String trigger) {
        return parseTriggerToDelay(trigger);
    }

    private long parseInterval(String interval) {
        interval = interval.toLowerCase().trim();
        
        if (interval.endsWith("s")) {
            return Long.parseLong(interval.replace("s", "")) * 1000;
        } else if (interval.endsWith("m")) {
            return Long.parseLong(interval.replace("m", "")) * 60000;
        } else if (interval.endsWith("h")) {
            return Long.parseLong(interval.replace("h", "")) * 3600000;
        } else if (interval.endsWith("d")) {
            return Long.parseLong(interval.replace("d", "")) * 86400000;
        }
        
        return Long.parseLong(interval) * 1000;
    }

    private void executeTaskInternal(ProactiveTask task, ToolContext context) {
        if (task.getMaxExecutions() > 0 && task.getExecutionCount() >= task.getMaxExecutions()) {
            tasks.remove(task.getId());
            return;
        }

        task.incrementExecutionCount();
        
        TaskExecution execution = new TaskExecution(
                UUID.randomUUID().toString().substring(0, 8),
                task.getId(),
                System.currentTimeMillis(),
                "executing",
                task.getActionSpec()
        );
        
        executionHistory.put(execution.getId(), execution);
        
        try {
            logExecution(task, "Task executed: " + task.getActionSpec());
            execution.complete("success", "Task completed successfully");
        } catch (Exception e) {
            execution.complete("failed", e.getMessage());
        }
    }

    private void logExecution(ProactiveTask task, String message) {
        System.out.println("[ProactiveAgent] " + Instant.now() + " - Task " + task.getId() + ": " + message);
    }

    private ToolResult cancelTask(ToolParams params) {
        String taskId = params.getString("task_id");
        if (taskId == null) {
            return ToolResult.failure("task_id is required");
        }

        ProactiveTask task = tasks.remove(taskId);
        if (task == null) {
            return ToolResult.failure("Task not found: " + taskId);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("task_id", taskId);
        result.put("status", "cancelled");

        return ToolResult.success(result);
    }

    private ToolResult listTasks(ToolParams params) {
        String taskType = params.getString("task_type");
        
        List<Map<String, Object>> taskList = new ArrayList<>();
        for (ProactiveTask task : tasks.values()) {
            if (taskType == null || taskType.equals(task.getType())) {
                taskList.add(task.toMap());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", taskList.size());
        result.put("tasks", taskList);

        return ToolResult.success(result);
    }

    private ToolResult getTaskStatus(ToolParams params) {
        String taskId = params.getString("task_id");
        if (taskId == null) {
            return ToolResult.failure("task_id is required");
        }

        ProactiveTask task = tasks.get(taskId);
        if (task == null) {
            return ToolResult.failure("Task not found: " + taskId);
        }

        return ToolResult.success(task.toMap());
    }

    private ToolResult executeTaskNow(ToolParams params, ToolContext context) {
        String taskId = params.getString("task_id");
        if (taskId == null) {
            return ToolResult.failure("task_id is required");
        }

        ProactiveTask task = tasks.get(taskId);
        if (task == null) {
            return ToolResult.failure("Task not found: " + taskId);
        }

        executeTaskInternal(task, context);

        Map<String, Object> result = new HashMap<>();
        result.put("task_id", taskId);
        result.put("status", "executed");
        result.put("execution_count", task.getExecutionCount());

        return ToolResult.success(result);
    }

    private ToolResult getExecutionHistory(ToolParams params) {
        String taskId = params.getString("task_id");
        Integer limitInt = params.getInteger("limit");
        int limit = limitInt != null ? limitInt : 10;

        List<Map<String, Object>> history = new ArrayList<>();
        for (TaskExecution exec : executionHistory.values()) {
            if (taskId == null || taskId.equals(exec.getTaskId())) {
                history.add(exec.toMap());
                if (history.size() >= limit) break;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", history.size());
        result.put("history", history);

        return ToolResult.success(result);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    private enum TaskPriority {
        CRITICAL(4),
        HIGH(3),
        MEDIUM(2),
        LOW(1);

        private final int value;

        TaskPriority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static TaskPriority fromString(String priority) {
            if (priority == null) return MEDIUM;
            try {
                return TaskPriority.valueOf(priority.toUpperCase());
            } catch (IllegalArgumentException e) {
                return MEDIUM;
            }
        }
    }

    private static class ProactiveTask {
        private final String id;
        private final String type;
        private final String trigger;
        private final String actionSpec;
        private final TaskPriority priority;
        private final int maxExecutions;
        private final long createdAt;
        private int executionCount = 0;

        public ProactiveTask(String id, String type, String trigger, String actionSpec,
                             TaskPriority priority, int maxExecutions, long createdAt) {
            this.id = id;
            this.type = type;
            this.trigger = trigger;
            this.actionSpec = actionSpec;
            this.priority = priority;
            this.maxExecutions = maxExecutions;
            this.createdAt = createdAt;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public String getTrigger() { return trigger; }
        public String getActionSpec() { return actionSpec; }
        public TaskPriority getPriority() { return priority; }
        public int getMaxExecutions() { return maxExecutions; }
        public long getCreatedAt() { return createdAt; }
        public int getExecutionCount() { return executionCount; }
        public void incrementExecutionCount() { executionCount++; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("type", type);
            map.put("trigger", trigger);
            map.put("action", actionSpec);
            map.put("priority", priority.name().toLowerCase());
            map.put("max_executions", maxExecutions);
            map.put("execution_count", executionCount);
            map.put("created_at", createdAt);
            map.put("status", maxExecutions > 0 && executionCount >= maxExecutions ? "completed" : "active");
            return map;
        }
    }

    private static class TaskExecution {
        private final String id;
        private final String taskId;
        private final long executedAt;
        private final String status;
        private final String action;
        private String result;
        private String message;
        private long completedAt;

        public TaskExecution(String id, String taskId, long executedAt, String status, String action) {
            this.id = id;
            this.taskId = taskId;
            this.executedAt = executedAt;
            this.status = status;
            this.action = action;
        }

        public String getId() { return id; }
        public String getTaskId() { return taskId; }
        public long getExecutedAt() { return executedAt; }
        public String getStatus() { return status; }
        public String getAction() { return action; }

        public void complete(String result, String message) {
            this.result = result;
            this.message = message;
            this.completedAt = System.currentTimeMillis();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            map.put("task_id", taskId);
            map.put("executed_at", executedAt);
            map.put("status", status);
            map.put("action", action);
            map.put("result", result);
            map.put("message", message);
            map.put("completed_at", completedAt);
            return map;
        }
    }
}
