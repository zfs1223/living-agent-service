package com.livingagent.core.security.client.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
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
    private final Set<String> compromisedDevices = ConcurrentHashMap.newKeySet();

    public ClientDeviceHealthMonitor(@Autowired(required = false) CrossLoopEventBus eventBus) {
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
                markDeviceCompromised(deviceId);
            }
        }
    }

    public void markDeviceCompromised(String deviceId) {
        if (compromisedDevices.add(deviceId)) {
            autoUnbound.increment();
            log.warn("[闭环61] 设备已标记为受损并自动解绑: device={}", deviceId);
            if (eventBus != null) {
                eventBus.publish(61, "auth_error", CrossLoopEvent.EventPriority.SECURITY,
                    Map.of("content", String.format("Device %s auto-unbound due to %d anomalous operations", deviceId, MAX_ANOMALOUS_OPERATIONS),
                        "source", "client-device", "action", "auto_unbind"));
            }
        }
    }

    public boolean isDeviceCompromised(String deviceId) {
        return compromisedDevices.contains(deviceId);
    }

    public Set<String> getCompromisedDevices() {
        return Set.copyOf(compromisedDevices);
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void scheduledDeviceHealthCheck() {
        for (Map.Entry<String, DeviceHealth> entry : deviceHealthMap.entrySet()) {
            String deviceId = entry.getKey();
            DeviceHealth health = entry.getValue();
            if (health.anomalousOps.sum() >= MAX_ANOMALOUS_OPERATIONS && !compromisedDevices.contains(deviceId)) {
                markDeviceCompromised(deviceId);
            }
        }
    }

    public DeviceHealthReport getReport(String deviceId) {
        DeviceHealth health = deviceHealthMap.get(deviceId);
        if (health == null) return new DeviceHealthReport(deviceId, 0, 0, "UNKNOWN");
        long normal = health.normalOps.sum();
        long anomalous = health.anomalousOps.sum();
        String status = compromisedDevices.contains(deviceId) ? "COMPROMISED"
            : anomalous >= MAX_ANOMALOUS_OPERATIONS ? "COMPROMISED"
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
