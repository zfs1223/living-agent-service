package com.livingagent.core.proactive.scheduler;

import java.time.Instant;
import java.util.Map;

public record ProactiveTask(
        String taskId,
        String name,
        String description,
        TaskType type,
        TaskPriority priority,
        TaskTrigger trigger,
        String brainDomain,
        Map<String, Object> parameters,
        TaskStatus status,
        Instant scheduledAt,
        Instant executedAt,
        Instant completedAt,
        String result,
        String error
) {
    public static ProactiveTask create(String name, TaskType type, TaskTrigger trigger) {
        return new ProactiveTask(
                "task_" + System.currentTimeMillis(),
                name,
                null,
                type,
                TaskPriority.NORMAL,
                trigger,
                null,
                Map.of(),
                TaskStatus.PENDING,
                Instant.now(),
                null,
                null,
                null,
                null
        );
    }
    
    public ProactiveTask withBrainDomain(String domain) {
        return new ProactiveTask(taskId, name, description, type, priority, trigger, domain, 
                parameters, status, scheduledAt, executedAt, completedAt, result, error);
    }
    
    public ProactiveTask withParameters(Map<String, Object> params) {
        return new ProactiveTask(taskId, name, description, type, priority, trigger, brainDomain, 
                params, status, scheduledAt, executedAt, completedAt, result, error);
    }
    
    public ProactiveTask withPriority(TaskPriority priority) {
        return new ProactiveTask(taskId, name, description, type, priority, trigger, brainDomain, 
                parameters, status, scheduledAt, executedAt, completedAt, result, error);
    }
    
    public ProactiveTask start() {
        return new ProactiveTask(taskId, name, description, type, priority, trigger, brainDomain, 
                parameters, TaskStatus.RUNNING, scheduledAt, Instant.now(), completedAt, result, error);
    }
    
    public ProactiveTask complete(String result) {
        return new ProactiveTask(taskId, name, description, type, priority, trigger, brainDomain, 
                parameters, TaskStatus.COMPLETED, scheduledAt, executedAt, Instant.now(), result, error);
    }
    
    public ProactiveTask fail(String error) {
        return new ProactiveTask(taskId, name, description, type, priority, trigger, brainDomain, 
                parameters, TaskStatus.FAILED, scheduledAt, executedAt, Instant.now(), result, error);
    }

    public enum TaskType {
        REMINDER,
        REPORT,
        NOTIFICATION,
        CHECK,
        CLEANUP,
        SYNC,
        ANALYSIS,
        CUSTOM
    }

    public enum TaskPriority {
        LOW(1),
        NORMAL(5),
        HIGH(10),
        URGENT(20);

        private final int weight;

        TaskPriority(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }
    }

    public enum TaskTrigger {
        SCHEDULED,
        EVENT_DRIVEN,
        CONDITION_BASED,
        USER_REQUEST,
        SYSTEM_INITIATED
    }

    public enum TaskStatus {
        PENDING,
        SCHEDULED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
