package com.livingagent.core.proactive.cron;

import java.time.Instant;
import java.util.Map;

public record CronJob(
        String jobId,
        String name,
        String description,
        String cronExpression,
        String timezone,
        boolean enabled,
        String taskType,
        Map<String, Object> taskParams,
        Instant createdAt,
        Instant lastRunAt,
        Instant nextRunAt,
        int runCount,
        int failureCount,
        String lastError
) {
    public static CronJob create(String name, String cronExpression, String taskType) {
        return new CronJob(
                "job_" + System.currentTimeMillis(),
                name,
                null,
                cronExpression,
                "Asia/Shanghai",
                true,
                taskType,
                Map.of(),
                Instant.now(),
                null,
                null,
                0,
                0,
                null
        );
    }
    
    public CronJob withTaskParams(Map<String, Object> params) {
        return new CronJob(jobId, name, description, cronExpression, timezone, enabled, 
                taskType, params, createdAt, lastRunAt, nextRunAt, runCount, failureCount, lastError);
    }
    
    public CronJob withTimezone(String tz) {
        return new CronJob(jobId, name, description, cronExpression, tz, enabled, 
                taskType, taskParams, createdAt, lastRunAt, nextRunAt, runCount, failureCount, lastError);
    }
    
    public CronJob withEnabled(boolean enabled) {
        return new CronJob(jobId, name, description, cronExpression, timezone, enabled, 
                taskType, taskParams, createdAt, lastRunAt, nextRunAt, runCount, failureCount, lastError);
    }
    
    public CronJob withNextRunAt(Instant nextRun) {
        return new CronJob(jobId, name, description, cronExpression, timezone, enabled, 
                taskType, taskParams, createdAt, lastRunAt, nextRun, runCount, failureCount, lastError);
    }
    
    public CronJob recordRun(boolean success, String error) {
        return new CronJob(jobId, name, description, cronExpression, timezone, enabled, 
                taskType, taskParams, createdAt, Instant.now(), nextRunAt, 
                runCount + 1, success ? failureCount : failureCount + 1, 
                success ? null : error);
    }
}
