package com.livingagent.core.worker.lifecycle.impl;

import com.livingagent.core.worker.DigitalWorker;
import com.livingagent.core.worker.factory.DigitalWorkerFactory;
import com.livingagent.core.worker.lifecycle.LifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class LifecycleManagerImpl implements LifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(LifecycleManagerImpl.class);

    private final DigitalWorkerFactory workerFactory;
    private final Map<String, WorkerLifecycleStateImpl> states = new ConcurrentHashMap<>();
    private final List<LifecycleListener> listeners = new CopyOnWriteArrayList<>();

    private Duration maxIdleTime = Duration.ofHours(1);
    private boolean autoTerminate = true;
    private Duration healthCheckInterval = Duration.ofMinutes(5);

    public LifecycleManagerImpl(DigitalWorkerFactory workerFactory) {
        this.workerFactory = workerFactory;
    }

    @Override
    public void initialize(DigitalWorker worker) {
        String workerId = worker.getEmployeeId();
        
        WorkerLifecycleStateImpl state = new WorkerLifecycleStateImpl(workerId);
        state.setState(LifecycleState.INITIALIZING);
        states.put(workerId, state);
        
        try {
            worker.touch();
            state.setState(LifecycleState.ACTIVE);
            state.setLastActiveTime(Instant.now());
            
            notifyStateChange(workerId, LifecycleState.CREATED, LifecycleState.ACTIVE);
            log.info("Initialized worker: {}", workerId);
            
        } catch (Exception e) {
            state.setState(LifecycleState.ERROR);
            notifyError(workerId, e);
            log.error("Failed to initialize worker {}: {}", workerId, e.getMessage());
        }
    }

    @Override
    public void activate(String workerId) {
        WorkerLifecycleStateImpl state = getStateOrThrow(workerId);
        
        if (state.getState() == LifecycleState.SUSPENDED || state.getState() == LifecycleState.IDLE) {
            LifecycleState previous = state.getState();
            
            workerFactory.getWorker(workerId).ifPresent(worker -> {
                worker.touch();
                state.setState(LifecycleState.ACTIVE);
                state.setLastActiveTime(Instant.now());
                state.setSuspensionReason(null);
                
                notifyStateChange(workerId, previous, LifecycleState.ACTIVE);
                log.info("Activated worker: {}", workerId);
            });
        }
    }

    @Override
    public void deactivate(String workerId) {
        WorkerLifecycleStateImpl state = getStateOrThrow(workerId);
        LifecycleState previous = state.getState();
        
        state.setState(LifecycleState.IDLE);
        notifyStateChange(workerId, previous, LifecycleState.IDLE);
        log.info("Deactivated worker: {}", workerId);
    }

    @Override
    public void suspend(String workerId, String reason) {
        WorkerLifecycleStateImpl state = getStateOrThrow(workerId);
        LifecycleState previous = state.getState();
        
        state.setState(LifecycleState.SUSPENDED);
        state.setSuspensionReason(reason);
        
        notifyStateChange(workerId, previous, LifecycleState.SUSPENDED);
        log.info("Suspended worker: {} - Reason: {}", workerId, reason);
    }

    @Override
    public void resume(String workerId) {
        activate(workerId);
    }

    @Override
    public void terminate(String workerId, String reason) {
        WorkerLifecycleStateImpl state = states.get(workerId);
        if (state == null) {
            return;
        }
        
        LifecycleState previous = state.getState();
        state.setState(LifecycleState.TERMINATING);
        state.setTerminationReason(reason);
        
        try {
            workerFactory.destroyWorker(workerId);
            state.setState(LifecycleState.TERMINATED);
            
            notifyStateChange(workerId, previous, LifecycleState.TERMINATED);
            log.info("Terminated worker: {} - Reason: {}", workerId, reason);
            
        } catch (Exception e) {
            state.setState(LifecycleState.ERROR);
            notifyError(workerId, e);
            log.error("Failed to terminate worker {}: {}", workerId, e.getMessage());
        }
    }

    @Override
    public Optional<WorkerLifecycleState> getState(String workerId) {
        WorkerLifecycleStateImpl impl = states.get(workerId);
        return Optional.ofNullable(impl != null ? impl.toRecord() : null);
    }

    @Override
    public List<WorkerLifecycleState> getAllStates() {
        return states.values().stream()
            .map(WorkerLifecycleStateImpl::toRecord)
            .toList();
    }

    @Override
    public List<String> getActiveWorkers() {
        return states.entrySet().stream()
            .filter(e -> e.getValue().state == LifecycleState.ACTIVE)
            .map(Map.Entry::getKey)
            .toList();
    }

    @Override
    public List<String> getIdleWorkers(Duration idleThreshold) {
        return states.entrySet().stream()
            .filter(e -> e.getValue().state == LifecycleState.ACTIVE || 
                        e.getValue().state == LifecycleState.IDLE)
            .filter(e -> {
                Duration idle = e.getValue().lastActiveTime != null 
                    ? Duration.between(e.getValue().lastActiveTime, Instant.now())
                    : Duration.ZERO;
                return idle.compareTo(idleThreshold) > 0;
            })
            .map(Map.Entry::getKey)
            .toList();
    }

    @Override
    public List<String> getWorkersByState(LifecycleState state) {
        return states.entrySet().stream()
            .filter(e -> e.getValue().getState() == state)
            .map(Map.Entry::getKey)
            .toList();
    }

    @Override
    public void scheduleHealthCheck(Duration interval) {
        this.healthCheckInterval = interval;
        log.info("Health check interval set to: {}", interval);
    }

    @Override
    public void performHealthCheck(String workerId) {
        WorkerLifecycleStateImpl state = states.get(workerId);
        if (state == null) {
            return;
        }
        
        Optional<DigitalWorker> workerOpt = workerFactory.getWorker(workerId);
        
        if (workerOpt.isEmpty()) {
            state.incrementHealthCheckFailures();
            notifyHealthCheckFailure(workerId, "Worker not found");
            
            if (state.getHealthCheckFailures() >= 3) {
                terminate(workerId, "Health check failures exceeded threshold");
            }
            return;
        }
        
        DigitalWorker worker = workerOpt.get();
        
        try {
            worker.touch();
            state.resetHealthCheckFailures();
            
            if (worker.getSuccessRate() < 0.5 && worker.getTotalTasksCompleted() > 10) {
                suspend(workerId, "Low success rate: " + worker.getSuccessRate());
            }
            
        } catch (Exception e) {
            state.incrementHealthCheckFailures();
            notifyHealthCheckFailure(workerId, e.getMessage());
        }
    }

    @Scheduled(fixedRateString = "${lifecycle.health-check-interval:300000}")
    public void performHealthCheckAll() {
        log.debug("Performing health check for all workers");
        
        for (String workerId : states.keySet()) {
            performHealthCheck(workerId);
        }
        
        if (autoTerminate) {
            List<String> idleWorkers = getIdleWorkers(maxIdleTime);
            for (String workerId : idleWorkers) {
                log.info("Auto-terminating idle worker: {}", workerId);
                terminate(workerId, "Idle timeout exceeded");
                notifyAutoTerminate(workerId, "Idle timeout exceeded");
            }
        }
    }

    @Override
    public void setMaxIdleTime(Duration maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    @Override
    public void setAutoTerminate(boolean autoTerminate) {
        this.autoTerminate = autoTerminate;
    }

    @Override
    public void registerLifecycleListener(LifecycleListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterLifecycleListener(LifecycleListener listener) {
        listeners.remove(listener);
    }

    private WorkerLifecycleStateImpl getStateOrThrow(String workerId) {
        WorkerLifecycleStateImpl state = states.get(workerId);
        if (state == null) {
            throw new IllegalArgumentException("Worker not found: " + workerId);
        }
        return state;
    }

    private void notifyStateChange(String workerId, LifecycleState from, LifecycleState to) {
        for (LifecycleListener listener : listeners) {
            try {
                listener.onStateChange(workerId, from, to);
            } catch (Exception e) {
                log.warn("Listener error: {}", e.getMessage());
            }
        }
    }

    private void notifyHealthCheckFailure(String workerId, String reason) {
        for (LifecycleListener listener : listeners) {
            try {
                listener.onHealthCheckFailure(workerId, reason);
            } catch (Exception e) {
                log.warn("Listener error: {}", e.getMessage());
            }
        }
    }

    private void notifyAutoTerminate(String workerId, String reason) {
        for (LifecycleListener listener : listeners) {
            try {
                listener.onAutoTerminate(workerId, reason);
            } catch (Exception e) {
                log.warn("Listener error: {}", e.getMessage());
            }
        }
    }

    private void notifyError(String workerId, Throwable error) {
        for (LifecycleListener listener : listeners) {
            try {
                listener.onError(workerId, error);
            } catch (Exception e) {
                log.warn("Listener error: {}", e.getMessage());
            }
        }
    }

    private static class WorkerLifecycleStateImpl {
        private final String workerId;
        private final Instant createdAt;
        private final Map<String, Object> metadata = new ConcurrentHashMap<>();
        
        private volatile LifecycleState state = LifecycleState.CREATED;
        private volatile Instant lastStateChange = Instant.now();
        private volatile Instant lastActiveTime;
        private volatile String suspensionReason;
        private volatile String terminationReason;
        private volatile int healthCheckFailures = 0;

        WorkerLifecycleStateImpl(String workerId) {
            this.workerId = workerId;
            this.createdAt = Instant.now();
        }

        void setState(LifecycleState state) {
            this.state = state;
            this.lastStateChange = Instant.now();
        }

        void setLastActiveTime(Instant time) {
            this.lastActiveTime = time;
        }

        void setSuspensionReason(String reason) {
            this.suspensionReason = reason;
        }

        void setTerminationReason(String reason) {
            this.terminationReason = reason;
        }

        void incrementHealthCheckFailures() {
            this.healthCheckFailures++;
        }

        void resetHealthCheckFailures() {
            this.healthCheckFailures = 0;
        }

        int getHealthCheckFailures() {
            return healthCheckFailures;
        }

        LifecycleState getState() {
            return state;
        }
        
        WorkerLifecycleState toRecord() {
            return new WorkerLifecycleState(
                workerId,
                state,
                createdAt,
                lastStateChange,
                lastActiveTime,
                suspensionReason,
                terminationReason,
                healthCheckFailures,
                new java.util.HashMap<>(metadata)
            );
        }
    }
}
