package com.livingagent.core.tenant.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class TenantHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(TenantHealthMonitor.class);
    private static final double QUOTA_USAGE_WARNING = 0.80;
    private static final double ACTIVITY_LOW_THRESHOLD = 0.10;

    private final CrossLoopEventBus eventBus;
    private final Map<String, TenantHealth> tenantHealthMap = new ConcurrentHashMap<>();

    public TenantHealthMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordQuotaUsage(String tenantId, double usageRatio) {
        TenantHealth health = tenantHealthMap.computeIfAbsent(tenantId, k -> new TenantHealth());
        health.quotaUsageRatio = usageRatio;
        health.activeMembers.increment();

        if (usageRatio > QUOTA_USAGE_WARNING) {
            log.warn("[闭环50] 租户配额使用率超限: tenant={}, usage={:.0%}", tenantId, usageRatio);
            eventBus.publish(50, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("Tenant %s quota usage %.0f%% exceeds %.0f%% threshold", tenantId, usageRatio * 100, QUOTA_USAGE_WARNING * 100)));
        }
    }

    public void recordActivity(String tenantId, String action) {
        TenantHealth health = tenantHealthMap.computeIfAbsent(tenantId, k -> new TenantHealth());
        health.totalActions.increment();
    }

    public TenantHealthReport getReport(String tenantId) {
        TenantHealth health = tenantHealthMap.get(tenantId);
        if (health == null) return new TenantHealthReport(tenantId, 0, 0, 0, "UNKNOWN");
        long members = health.activeMembers.sum();
        long actions = health.totalActions.sum();
        double activityRate = members > 0 ? (double) actions / members : 0;
        String status = health.quotaUsageRatio > QUOTA_USAGE_WARNING ? "OVER_QUOTA"
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
