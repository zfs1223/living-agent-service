package com.livingagent.core.heartbeat.impl;

import com.livingagent.core.heartbeat.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class HeartbeatServiceImpl implements HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatServiceImpl.class);

    private final HeartbeatRunRepository runRepository;
    private final ConcurrentHashMap<String, ScheduledWakeup> scheduledWakeups = new ConcurrentHashMap<>();

    public HeartbeatServiceImpl(HeartbeatRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public void enqueueWakeup(String employeeId, WakeSource source, WakeOptions options) {
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        HeartbeatRun run = new HeartbeatRun();
        run.setRunId(runId);
        run.setEmployeeId(employeeId);
        run.setWakeSource(source.name());
        run.setStatus(RunStatus.PENDING.name());
        run.setPriority(options != null ? options.priority() : "NORMAL");
        run.setContext(options != null ? options.context() : null);
        run.setMaxDurationSeconds(options != null ? (int) options.maxDuration().toSeconds() : null);
        run.setAllowedActions(options != null ? options.allowedActions().toArray(new String[0]) : null);
        run.setRequireSuccess(options != null && options.requireSuccess());
        run.setCreatedAt(Instant.now());
        
        runRepository.save(run);
        
        log.info("Enqueued heartbeat wakeup for employee: {}, source: {}", employeeId, source);
    }

    @Override
    public void executeRun(HeartbeatRun run) {
        if (!run.getStatus().equals(RunStatus.PENDING.name())) {
            log.warn("Run {} is not in PENDING status", run.getRunId());
            return;
        }
        
        run.setStatus(RunStatus.RUNNING.name());
        run.setStartedAt(Instant.now());
        runRepository.save(run);
        
        log.info("Executing heartbeat run: {} for employee: {}", run.getRunId(), run.getEmployeeId());
        
        try {
            int maxSeconds = run.getMaxDurationSeconds() != null ? run.getMaxDurationSeconds() : 300;
            Thread.sleep(Duration.ofSeconds(Math.min(maxSeconds, 10)).toMillis());
            
            completeRun(run.getRunId(), new RunResult(
                    true,
                    "Run completed successfully",
                    List.of("task_completed", "knowledge_updated"),
                    Duration.ofMinutes(1),
                    Instant.now(),
                    null
                )
            );
        } catch (InterruptedException e) {
            log.error("Heartbeat run interrupted", e);
            Thread.currentThread().interrupt();
            completeRun(run.getRunId(), new RunResult(
                false,
                "Run interrupted: " + e.getMessage(),
                null,
                Duration.between(run.getStartedAt(), Instant.now()),
                Instant.now(),
                e.getMessage()
            ));
        }
    }

    @Override
    public void completeRun(String runId, RunResult result) {
        Optional<HeartbeatRun> runOpt = runRepository.findById(runId);
        if (runOpt.isEmpty()) {
            log.warn("Run not found: {}", runId);
            return;
        }
        
        HeartbeatRun run = runOpt.get();
        run.setStatus(result.success() ? RunStatus.COMPLETED.name() : RunStatus.FAILED.name());
        run.setCompletedAt(result.completedAt());
        run.setResultMessage(result.message());
        run.setActionsTaken(result.actionsTaken() != null ? result.actionsTaken().toArray(new String[0]) : null);
        run.setActualDurationSeconds((int) result.actualDuration().toSeconds());
        run.setErrorMessage(result.error());
        
        runRepository.save(run);
        
        log.info("Completed heartbeat run: {} with status: {}", runId, run.getStatus());
    }

    @Override
    public Optional<HeartbeatRun> getRun(String runId) {
        return runRepository.findById(runId);
    }

    @Override
    public List<HeartbeatRun> getPendingRuns() {
        return runRepository.findByStatus(RunStatus.PENDING.name());
    }

    @Override
    public List<HeartbeatRun> getRunsByEmployee(String employeeId) {
        return runRepository.findByEmployeeId(employeeId);
    }

    @Override
    public List<HeartbeatRun> getRunsByStatus(RunStatus status) {
        return runRepository.findByStatus(status.name());
    }

    @Override
    public void cancelRun(String runId) {
        Optional<HeartbeatRun> runOpt = runRepository.findById(runId);
        if (runOpt.isPresent()) {
            HeartbeatRun run = runOpt.get();
            if (run.getStatus().equals(RunStatus.PENDING.name()) || run.getStatus().equals(RunStatus.RUNNING.name())) {
                run.setStatus(RunStatus.CANCELLED.name());
                run.setCompletedAt(Instant.now());
                runRepository.save(run);
                log.info("Cancelled run: {}", runId);
            }
        }
    }

    @Override
    public HeartbeatStatistics getStatistics(String employeeId) {
        List<HeartbeatRun> runs = runRepository.findByEmployeeId(employeeId);
        
        int total = runs.size();
        int successful = (int) runs.stream().filter(r -> r.getStatus().equals(RunStatus.COMPLETED.name())).count();
        int failed = (int) runs.stream().filter(r -> r.getStatus().equals(RunStatus.FAILED.name())).count();
        
        double avgDuration = runs.stream()
                .filter(r -> r.getActualDurationSeconds() != null)
                .mapToInt(HeartbeatRun::getActualDurationSeconds)
                .average()
                .orElse(0.0);
        
        Optional<Instant> lastRun = runs.stream()
                .filter(r -> r.getCompletedAt() != null)
                .map(HeartbeatRun::getCompletedAt)
                .max(Instant::compareTo);
        
        return new HeartbeatStatistics(
            total,
            successful,
            failed,
            Duration.ofSeconds((long) avgDuration),
            lastRun.orElse(null),
            List.of("check_health", "update_knowledge")
        );
    }

    @Override
    public void schedulePeriodicWakeup(String employeeId, Duration interval) {
        String taskId = "periodic_" + employeeId;
        
        if (scheduledWakeups.containsKey(taskId)) {
            log.debug("Periodic wakeup already scheduled for: {}", employeeId);
            return;
        }
        
        ScheduledWakeup wakeup = ScheduledWakeup.create(employeeId, interval);
        scheduledWakeups.put(taskId, wakeup);
        log.info("Scheduled periodic wakeup for employee: {} every {}", employeeId, interval);
    }

    @Scheduled(fixedRate = 60000)
    public void processScheduledWakeups() {
        Instant now = Instant.now();
        
        for (ScheduledWakeup wakeup : scheduledWakeups.values()) {
            if (wakeup.active() && (wakeup.nextRunAt() == null || !wakeup.nextRunAt().isAfter(now))) {
                enqueueWakeup(wakeup.employeeId(), WakeSource.TIMER, new WakeOptions(
                    Duration.ofMinutes(5),
                    null,
                    "LOW",
                    "Periodic heartbeat check",
                    false
                ));
                
                ScheduledWakeup nextWakeup = wakeup.withNextRunAt(now.plus(wakeup.interval()));
                scheduledWakeups.put(wakeup.taskId(), nextWakeup);
            }
        }
    }
    
    public void cancelScheduledWakeup(String employeeId) {
        String taskId = "periodic_" + employeeId;
        ScheduledWakeup wakeup = scheduledWakeups.get(taskId);
        if (wakeup != null) {
            scheduledWakeups.put(taskId, wakeup.deactivate());
            log.info("Cancelled scheduled wakeup for employee: {}", employeeId);
        }
    }
}
