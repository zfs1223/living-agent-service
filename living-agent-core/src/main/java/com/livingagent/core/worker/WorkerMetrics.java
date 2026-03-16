package com.livingagent.core.worker;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class WorkerMetrics {

    private final AtomicLong tasksCompleted = new AtomicLong(0);
    private final AtomicLong tasksSucceeded = new AtomicLong(0);
    private final AtomicLong tasksFailed = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);
    private final AtomicLong totalTokensUsed = new AtomicLong(0);
    
    private volatile Instant lastTaskTime;
    private volatile double averageResponseTimeMs = 0.0;
    private volatile double successRate = 0.0;
    
    private final Map<String, AtomicLong> taskTypeCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> taskTypeSuccess = new ConcurrentHashMap<>();
    private final Map<String, Double> capabilityScores = new ConcurrentHashMap<>();

    public void recordTask(boolean success, long durationMs, String taskType, long tokensUsed) {
        tasksCompleted.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
        totalTokensUsed.addAndGet(tokensUsed);
        
        if (success) {
            tasksSucceeded.incrementAndGet();
        } else {
            tasksFailed.incrementAndGet();
        }
        
        if (taskType != null) {
            taskTypeCounts.computeIfAbsent(taskType, k -> new AtomicLong(0)).incrementAndGet();
            if (success) {
                taskTypeSuccess.computeIfAbsent(taskType, k -> new AtomicLong(0)).incrementAndGet();
            }
        }
        
        lastTaskTime = Instant.now();
        updateAverages();
    }

    private void updateAverages() {
        long completed = tasksCompleted.get();
        long succeeded = tasksSucceeded.get();
        
        if (completed > 0) {
            successRate = (double) succeeded / completed;
            averageResponseTimeMs = (double) totalDurationMs.get() / completed;
        }
    }

    public long getTasksCompleted() {
        return tasksCompleted.get();
    }

    public long getTasksSucceeded() {
        return tasksSucceeded.get();
    }

    public long getTasksFailed() {
        return tasksFailed.get();
    }

    public double getSuccessRate() {
        return successRate;
    }

    public double getAverageResponseTimeMs() {
        return averageResponseTimeMs;
    }

    public long getTotalDurationMs() {
        return totalDurationMs.get();
    }

    public long getTotalTokensUsed() {
        return totalTokensUsed.get();
    }

    public Instant getLastTaskTime() {
        return lastTaskTime;
    }

    public Map<String, Long> getTaskTypeCounts() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        taskTypeCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public Map<String, Double> getCapabilityScores() {
        return new ConcurrentHashMap<>(capabilityScores);
    }

    public void updateCapabilityScore(String capability, double score) {
        capabilityScores.put(capability, Math.max(0.0, Math.min(1.0, score)));
    }

    public double getCapabilityScore(String capability) {
        return capabilityScores.getOrDefault(capability, 0.0);
    }

    public void reset() {
        tasksCompleted.set(0);
        tasksSucceeded.set(0);
        tasksFailed.set(0);
        totalDurationMs.set(0);
        totalTokensUsed.set(0);
        averageResponseTimeMs = 0.0;
        successRate = 0.0;
        taskTypeCounts.clear();
        taskTypeSuccess.clear();
        capabilityScores.clear();
    }

    public WorkerMetricsSnapshot snapshot() {
        return new WorkerMetricsSnapshot(
            tasksCompleted.get(),
            tasksSucceeded.get(),
            tasksFailed.get(),
            successRate,
            averageResponseTimeMs,
            totalDurationMs.get(),
            totalTokensUsed.get(),
            lastTaskTime,
            getTaskTypeCounts(),
            getCapabilityScores()
        );
    }

    public record WorkerMetricsSnapshot(
        long tasksCompleted,
        long tasksSucceeded,
        long tasksFailed,
        double successRate,
        double averageResponseTimeMs,
        long totalDurationMs,
        long totalTokensUsed,
        Instant lastTaskTime,
        Map<String, Long> taskTypeCounts,
        Map<String, Double> capabilityScores
    ) {}
}
