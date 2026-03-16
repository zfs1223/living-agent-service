package com.livingagent.core.proactive.cron.impl;

import com.livingagent.core.proactive.cron.CronJob;
import com.livingagent.core.proactive.cron.CronService;
import com.livingagent.core.proactive.event.EventHookManager;
import com.livingagent.core.proactive.event.HookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class CronServiceImpl implements CronService {

    private static final Logger log = LoggerFactory.getLogger(CronServiceImpl.class);

    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executorService;
    private final EventHookManager eventHookManager;
    private volatile boolean running = false;

    public CronServiceImpl(EventHookManager eventHookManager) {
        this.eventHookManager = eventHookManager;
        this.executorService = Executors.newScheduledThreadPool(4);
    }

    @Override
    public CronJob scheduleJob(CronJob job) {
        if (job == null || job.jobId() == null) {
            throw new IllegalArgumentException("Job and jobId must not be null");
        }

        unscheduleJob(job.jobId());

        jobs.put(job.jobId(), job);

        if (job.enabled()) {
            scheduleInternal(job);
        }

        log.info("Scheduled cron job: {} [{}]", job.name(), job.cronExpression());
        return job;
    }

    private void scheduleInternal(CronJob job) {
        try {
            long initialDelay = calculateInitialDelay(job);
            long period = calculatePeriod(job);

            if (period <= 0) {
                log.warn("Invalid cron expression for job {}: {}", job.jobId(), job.cronExpression());
                return;
            }

            ScheduledFuture<?> future = executorService.scheduleAtFixedRate(
                    () -> executeJobInternal(job.jobId()),
                    initialDelay,
                    period,
                    TimeUnit.MILLISECONDS
            );

            scheduledTasks.put(job.jobId(), future);

            Instant nextRun = Instant.now().plusMillis(initialDelay);
            jobs.put(job.jobId(), job.withNextRunAt(nextRun));

            log.debug("Job {} scheduled to run at {}", job.name(), nextRun);

        } catch (Exception e) {
            log.error("Failed to schedule job {}: {}", job.jobId(), e.getMessage());
        }
    }

    private long calculateInitialDelay(CronJob job) {
        return 60000;
    }

    private long calculatePeriod(CronJob job) {
        String expr = job.cronExpression();
        if (expr == null || expr.isEmpty()) {
            return -1;
        }

        String[] parts = expr.split(" ");
        if (parts.length >= 1) {
            String minutePart = parts[0];
            if (minutePart.equals("*")) {
                return 60000;
            } else if (minutePart.startsWith("*/")) {
                try {
                    int interval = Integer.parseInt(minutePart.substring(2));
                    return interval * 60000L;
                } catch (NumberFormatException e) {
                    return 60000;
                }
            }
        }

        if (parts.length >= 2) {
            String hourPart = parts[1];
            if (hourPart.startsWith("*/")) {
                try {
                    int interval = Integer.parseInt(hourPart.substring(2));
                    return interval * 3600000L;
                } catch (NumberFormatException e) {
                    return 3600000;
                }
            }
        }

        return 86400000L;
    }

    @Override
    public boolean unscheduleJob(String jobId) {
        if (jobId == null) {
            return false;
        }

        ScheduledFuture<?> future = scheduledTasks.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }

        CronJob removed = jobs.remove(jobId);
        if (removed != null) {
            log.info("Unscheduled cron job: {}", removed.name());
            return true;
        }

        return false;
    }

    @Override
    public Optional<CronJob> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public List<CronJob> getAllJobs() {
        return new ArrayList<>(jobs.values());
    }

    @Override
    public List<CronJob> getEnabledJobs() {
        return jobs.values().stream()
                .filter(CronJob::enabled)
                .toList();
    }

    @Override
    public boolean enableJob(String jobId) {
        CronJob job = jobs.get(jobId);
        if (job == null) {
            return false;
        }

        if (!job.enabled()) {
            CronJob enabledJob = job.withEnabled(true);
            jobs.put(jobId, enabledJob);
            scheduleInternal(enabledJob);
            log.info("Enabled cron job: {}", job.name());
        }

        return true;
    }

    @Override
    public boolean disableJob(String jobId) {
        CronJob job = jobs.get(jobId);
        if (job == null) {
            return false;
        }

        if (job.enabled()) {
            ScheduledFuture<?> future = scheduledTasks.remove(jobId);
            if (future != null) {
                future.cancel(false);
            }

            jobs.put(jobId, job.withEnabled(false));
            log.info("Disabled cron job: {}", job.name());
        }

        return true;
    }

    @Override
    public CronJob updateJob(CronJob job) {
        if (job == null || job.jobId() == null) {
            throw new IllegalArgumentException("Job and jobId must not be null");
        }

        return scheduleJob(job);
    }

    @Override
    public void executeJob(String jobId) {
        executeJobInternal(jobId);
    }

    private void executeJobInternal(String jobId) {
        CronJob job = jobs.get(jobId);
        if (job == null || !job.enabled()) {
            return;
        }

        log.info("Executing cron job: {}", job.name());

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("jobId", job.jobId());
            payload.put("jobName", job.name());
            payload.put("taskType", job.taskType());
            payload.put("taskParams", job.taskParams());
            payload.put("runCount", job.runCount() + 1);

            HookEvent event = HookEvent.of("cron.execute", "CronService", payload);
            eventHookManager.publishEvent(event);

            CronJob updatedJob = job.recordRun(true, null);
            jobs.put(jobId, updatedJob);

            log.debug("Cron job {} executed successfully", job.name());

        } catch (Exception e) {
            log.error("Cron job {} execution failed: {}", job.name(), e.getMessage());

            CronJob updatedJob = job.recordRun(false, e.getMessage());
            jobs.put(jobId, updatedJob);
        }
    }

    @Override
    public void start() {
        running = true;
        log.info("CronService started");
    }

    @Override
    public void stop() {
        running = false;

        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(false);
        }
        scheduledTasks.clear();

        log.info("CronService stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public long getNextExecutionTime(String jobId) {
        CronJob job = jobs.get(jobId);
        if (job == null || job.nextRunAt() == null) {
            return -1;
        }
        return job.nextRunAt().toEpochMilli() - System.currentTimeMillis();
    }

    public void shutdown() {
        stop();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        log.info("CronService shutdown complete");
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalJobs", jobs.size());
        stats.put("enabledJobs", getEnabledJobs().size());
        stats.put("running", running);
        
        Map<String, Object> jobStats = new HashMap<>();
        for (CronJob job : jobs.values()) {
            Map<String, Object> j = new HashMap<>();
            j.put("name", job.name());
            j.put("enabled", job.enabled());
            j.put("runCount", job.runCount());
            j.put("failureCount", job.failureCount());
            j.put("lastRunAt", job.lastRunAt());
            j.put("nextRunAt", job.nextRunAt());
            jobStats.put(job.jobId(), j);
        }
        stats.put("jobs", jobStats);
        
        return stats;
    }
}
