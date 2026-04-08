package com.livingagent.core.heartbeat;

import java.time.Duration;
import java.time.Instant;

public record ScheduledWakeup(
    String taskId,
    String employeeId,
    Duration interval,
    Instant nextRunAt,
    boolean active
) {
    public static ScheduledWakeup create(String employeeId, Duration interval) {
        return new ScheduledWakeup(
            "periodic_" + employeeId,
            employeeId,
            interval,
            Instant.now().plus(interval),
            true
        );
    }
    
    public ScheduledWakeup withNextRunAt(Instant nextRunAt) {
        return new ScheduledWakeup(taskId, employeeId, interval, nextRunAt, active);
    }
    
    public ScheduledWakeup deactivate() {
        return new ScheduledWakeup(taskId, employeeId, interval, nextRunAt, false);
    }
}
