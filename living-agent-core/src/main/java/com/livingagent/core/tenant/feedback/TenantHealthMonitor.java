package com.livingagent.core.tenant.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class TenantHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(TenantHealthMonitor.class);
    private static final double QUOTA_USAGE_WARNING = 0.80;
    private static final double ACTIVITY_LOW_THRESHOLD = 0.10;

    private final CrossLoopEventBus eventBus;
    private final Map<String, TenantHealth> tenantHealthMap = new ConcurrentHashMap<>();
    private volatile double quotaWarningMultiplier = 1.0;

    public TenantHealthMonitor(@Autowired(required = false) CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordQuotaUsage(String tenantId, double usageRatio) {
        TenantHealth health = tenantHealthMap.computeIfAbsent(tenantId, k -> new TenantHealth());
        health.quotaUsageRatio = usageRatio;
        health.activeMembers.increment();

        double effectiveThreshold = QUOTA_USAGE_WARNING * quotaWarningMultiplier;
        if (usageRatio > effectiveThreshold) {
            log.warn("[闭环50] 租户配额使用率超限: tenant={}, usage={}%, threshold={}%",
                tenantId, String.format("%.0f", usageRatio * 100), String.format("%.0f", effectiveThreshold * 100));
            if (eventBus != null) {
                eventBus.publish(50, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("content", String.format("Tenant %s quota usage %.0f%% exceeds %.0f%% threshold", tenantId, usageRatio * 100, effectiveThreshold * 100)));
            }
        }
    }

    public void recordActivity(String tenantId, String action) {
        TenantHealth health = tenantHealthMap.computeIfAbsent(tenantId, k -> new TenantHealth());
        health.totalActions.increment();
    }

    @Scheduled(fixedRate = 30 * 60 * 1000)
    public void checkAndAdjustQuotaWarning() {
        if (tenantHealthMap.isEmpty()) return;
        long overQuotaCount = tenantHealthMap.values().stream()
            .filter(h -> h.quotaUsageRatio > QUOTA_USAGE_WARNING * quotaWarningMultiplier)
            .count();
        double overQuotaRate = (double) overQuotaCount / tenantHealthMap.size();
        if (overQuotaRate > 0.30 && quotaWarningMultiplier > 0.7) {
            double old = quotaWarningMultiplier;
            quotaWarningMultiplier = Math.max(0.7, quotaWarningMultiplier - 0.05);
            log.info("[闭环50] 配额超限率{}%过高，预警乘数从{}降至{}",
                String.format("%.0f", overQuotaRate * 100), old, quotaWarningMultiplier);
            if (eventBus != null) {
                eventBus.publish(50, "quota_warning_adjusted", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("quotaWarningMultiplier", quotaWarningMultiplier, "overQuotaRate", overQuotaRate), 300);
            }
        } else if (overQuotaRate < 0.10 && quotaWarningMultiplier < 1.0) {
            quotaWarningMultiplier = Math.min(1.0, quotaWarningMultiplier + 0.02);
        }
    }

    public double getQuotaWarningMultiplier() {
        return quotaWarningMultiplier;
    }

    public TenantHealthReport getReport(String tenantId) {
        TenantHealth health = tenantHealthMap.get(tenantId);
        if (health == null) return new TenantHealthReport(tenantId, 0, 0, 0, "UNKNOWN");
        long members = health.activeMembers.sum();
        long actions = health.totalActions.sum();
        double activityRate = members > 0 ? (double) actions / members : 0;
        String status = health.quotaUsageRatio > QUOTA_USAGE_WARNING * quotaWarningMultiplier ? "OVER_QUOTA"
            : activityRate < ACTIVITY_LOW_THRESHOLD ? "INACTIVE" : "HEALTHY";
        return new TenantHealthReport(tenantId, health.quotaUsageRatio, activityRate, actions, status);
    }

    public static class TenantHealth {
        double quotaUsageRatio;
        LongAdder activeMembers = new LongAdder();
        LongAdder totalActions = new LongAdder();
    }

    public record TenantHealthReport(String tenantId, double quotaUsage, double activityRate,
                                      long totalActions, String status) {}
}
