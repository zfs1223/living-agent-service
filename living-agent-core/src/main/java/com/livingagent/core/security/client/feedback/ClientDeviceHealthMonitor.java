package com.livingagent.core.security.client.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ClientDeviceHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(ClientDeviceHealthMonitor.class);
    private static final int MAX_ANOMALOUS_OPERATIONS = 5;

    private final CrossLoopEventBus eventBus;
    private final Map<String, DeviceHealth> deviceHealthMap = new ConcurrentHashMap<>();
    private final LongAdder totalDevices = new LongAdder();
    private final LongAdder autoUnbound = new LongAdder();

    public ClientDeviceHealthMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void registerDevice(String deviceId) {
        deviceHealthMap.computeIfAbsent(deviceId, k -> new DeviceHealth());
        totalDevices.increment();
    }

    public void recordOperation(String deviceId, boolean normal) {
        DeviceHealth health = deviceHealthMap.computeIfAbsent(deviceId, k -> new DeviceHealth());
        if (normal) {
            health.normalOps.increment();
        } else {
            health.anomalousOps.increment();
            if (health.anomalousOps.sum() >= MAX_ANOMALOUS_OPERATIONS) {
                log.warn("[闭环61] 设备异常操作过多，建议自动解绑: device={}", deviceId);
                eventBus.publish(61, "auth_error", CrossLoopEvent.EventPriority.SECURITY,
                    Map.of("content", String.format("Device %s has %d anomalous operations, suggest auto-unbind", deviceId, health.anomalousOps.sum()),
                        "source", "client-device"));
                autoUnbound.increment();
            }
        }
    }

    public DeviceHealthReport getReport(String deviceId) {
        DeviceHealth health = deviceHealthMap.get(deviceId);
        if (health == null) return new DeviceHealthReport(deviceId, 0, 0, "UNKNOWN");
        long normal = health.normalOps.sum();
        long anomalous = health.anomalousOps.sum();
        String status = anomalous >= MAX_ANOMALOUS_OPERATIONS ? "COMPROMISED"
            : anomalous > 0 ? "WARNING" : "HEALTHY";
        return new DeviceHealthReport(deviceId, normal, anomalous, status);
    }

    public static class DeviceHealth {
        LongAdder normalOps = new LongAdder();
        LongAdder anomalousOps = new LongAdder();
    }

    public record DeviceHealthReport(String deviceId, long normalOps, long anomalousOps,
                                      String status) {}
}
