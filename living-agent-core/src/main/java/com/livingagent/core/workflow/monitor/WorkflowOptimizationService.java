package com.livingagent.core.workflow.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闭环43-P43-B: 工作流优化服务
 * 基于历史数据优化阶段Handler参数和超时阈值
 */
public class WorkflowOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOptimizationService.class);

    private final WorkflowStageMonitor stageMonitor;
    private final Map<String, Long> optimizedThresholds = new ConcurrentHashMap<>();

    public WorkflowOptimizationService(WorkflowStageMonitor stageMonitor) {
        this.stageMonitor = stageMonitor;
    }

    public void optimizeStageTimeout(String stageName, long currentTimeoutMs, long avgDurationMs) {
        if (avgDurationMs <= 0) return;

        // 如果平均耗时超过当前阈值的80%，建议提高阈值
        if (avgDurationMs > currentTimeoutMs * 0.8) {
            long newThreshold = (long) (avgDurationMs * 1.5);
            optimizedThresholds.put(stageName, newThreshold);
            log.info("[闭环43] 阶段超时优化: stage={}, {}ms → {}ms (avgDuration={}ms)",
                stageName, currentTimeoutMs, newThreshold, avgDurationMs);
        }
    }

    public Long getOptimizedTimeout(String stageName) {
        return optimizedThresholds.get(stageName);
    }

    public Map<String, Long> getAllOptimizedThresholds() {
        return Map.copyOf(optimizedThresholds);
    }
}
