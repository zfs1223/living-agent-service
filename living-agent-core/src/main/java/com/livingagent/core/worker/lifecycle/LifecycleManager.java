package com.livingagent.core.worker.lifecycle;

import com.livingagent.core.worker.DigitalWorker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LifecycleManager {

    void initialize(DigitalWorker worker);
    
    void activate(String workerId);
    
    void deactivate(String workerId);
    
    void suspend(String workerId, String reason);
    
    void resume(String workerId);
    
    void terminate(String workerId, String reason);
    
    Optional<WorkerLifecycleState> getState(String workerId);
    
    List<WorkerLifecycleState> getAllStates();
    
    List<String> getActiveWorkers();
    
    List<String> getIdleWorkers(Duration idleThreshold);
    
    List<String> getWorkersByState(LifecycleState state);
    
    void scheduleHealthCheck(Duration interval);
    
    void performHealthCheck(String workerId);
    
    void performHealthCheckAll();
    
    void setMaxIdleTime(Duration maxIdleTime);
    
    void setAutoTerminate(boolean autoTerminate);
    
    void registerLifecycleListener(LifecycleListener listener);
    
    void unregisterLifecycleListener(LifecycleListener listener);
    
    enum LifecycleState {
        CREATED,
        INITIALIZING,
        ACTIVE,
        IDLE,
        SUSPENDED,
        TERMINATING,
        TERMINATED,
        ERROR
    }
    
    record WorkerLifecycleState(
        String workerId,
        LifecycleState state,
        Instant createdAt,
        Instant lastStateChange,
        Instant lastActiveTime,
        String suspensionReason,
        String terminationReason,
        int healthCheckFailures,
        Map<String, Object> metadata
    ) {
        public Duration getIdleDuration() {
            return lastActiveTime != null 
                ? Duration.between(lastActiveTime, Instant.now())
                : Duration.ZERO;
        }
    }
    
    interface LifecycleListener {
        default void onStateChange(String workerId, LifecycleState from, LifecycleState to) {}
        default void onHealthCheckFailure(String workerId, String reason) {}
        default void onAutoTerminate(String workerId, String reason) {}
        default void onError(String workerId, Throwable error) {}
    }
}
