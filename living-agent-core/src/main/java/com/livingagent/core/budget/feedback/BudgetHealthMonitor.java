package com.livingagent.core.budget.feedback;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class BudgetHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(BudgetHealthMonitor.class);
    private static final double OVERSPEND_WARNING = 0.90;

    private final CrossLoopEventBus eventBus;
    private final Map<String, BudgetHealth> budgetHealthMap = new ConcurrentHashMap<>();

    public BudgetHealthMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordAllocation(String budgetId, double allocated) {
        BudgetHealth health = budgetHealthMap.computeIfAbsent(budgetId, k -> new BudgetHealth());
        health.allocated = allocated;
    }

    public void recordSpending(String budgetId, double spent) {
        BudgetHealth health = budgetHealthMap.computeIfAbsent(budgetId, k -> new BudgetHealth());
        health.spent = spent;
        health.spendingCount.increment();

        double usageRatio = health.allocated > 0 ? spent / health.allocated : 0;
        if (usageRatio > OVERSPEND_WARNING) {
            log.warn("[闭环52] 预算超支预警: id={}, usage={:.0%}%", budgetId, usageRatio);
            eventBus.publish(52, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                Map.of("content", String.format("Budget %s usage %.0f%% exceeds %.0f%% threshold", budgetId, usageRatio * 100, OVERSPEND_WARNING * 100)));
        }
    }

    public BudgetHealthReport getReport(String budgetId) {
        BudgetHealth health = budgetHealthMap.get(budgetId);
        if (health == null) return new BudgetHealthReport(budgetId, 0, 0, 0, "UNKNOWN");
        double usageRatio = health.allocated > 0 ? health.spent / health.allocated : 0;
        String status = usageRatio > 1.0 ? "OVERSPENT" : usageRatio > OVERSPEND_WARNING ? "WARNING" : "HEALTHY";
        return new BudgetHealthReport(budgetId, health.allocated, health.spent, usageRatio, status);
    }

    public static class BudgetHealth {
        double allocated;
        double spent;
        LongAdder spendingCount = new LongAdder();
    }

    public record BudgetHealthReport(String budgetId, double allocated, double spent,
                                      double usageRatio, String status) {}
}
