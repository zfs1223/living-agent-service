package com.livingagent.core.nativelib;

import com.livingagent.core.diagnosis.HealthCheck;
import com.livingagent.core.diagnosis.HealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Component
public class NativeLibraryHealthCheck implements HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(NativeLibraryHealthCheck.class);
    private static final double SUCCESS_RATE_THRESHOLD = 0.5;

    private final NativePerformanceMonitor performanceMonitor;

    public NativeLibraryHealthCheck(NativePerformanceMonitor performanceMonitor) {
        this.performanceMonitor = performanceMonitor;
    }

    @Override
    public HealthStatus check() {
        if (!NativeLibrary.isAvailable()) {
            String error = NativeLibrary.getLoadError();
            log.warn("Native library not available: {}", error);
            HealthStatus status = HealthStatus.unhealthy("native_library",
                "Native library not loaded: " + error);
            status.setScore(0.0);
            return status;
        }

        // 测试 native 函数调用是否正常（使用 bookworm 编译后应该安全）
        String version = NativeLibrary.getVersion();
        if ("native-unavailable".equals(version) || "native-error".equals(version)) {
            HealthStatus status = HealthStatus.degraded("native_library",
                "Native library loaded but version query failed: " + version);
            status.setScore(30.0);
            return status;
        }
        log.debug("Native library version: {}", version);

        List<NativeCallMetrics> unhealthyOps = performanceMonitor.getUnhealthyOperations();
        if (!unhealthyOps.isEmpty()) {
            StringBuilder sb = new StringBuilder("Unhealthy native operations: ");
            for (NativeCallMetrics m : unhealthyOps) {
                sb.append(m.getOperationName())
                  .append(String.format("(%.0f%% fail)", (1.0 - m.getSuccessRate()) * 100))
                  .append(", ");
            }
            HealthStatus status = HealthStatus.degraded("native_library", sb.toString());
            status.setScore(50.0);
            return status;
        }

        double overallRate = performanceMonitor.getOverallSuccessRate();
        if (overallRate < SUCCESS_RATE_THRESHOLD) {
            HealthStatus status = HealthStatus.degraded("native_library",
                String.format("Overall success rate low: %.1f%%", overallRate * 100));
            status.setScore(overallRate * 100);
            return status;
        }

        List<NativeCallMetrics> slowOps = performanceMonitor.getSlowOperations();
        if (!slowOps.isEmpty()) {
            HealthStatus status = HealthStatus.degraded("native_library",
                String.format("%d slow operations detected (avg > 500ms)", slowOps.size()));
            status.setScore(75.0);
            return status;
        }

        return HealthStatus.healthy("native_library");
    }
}
