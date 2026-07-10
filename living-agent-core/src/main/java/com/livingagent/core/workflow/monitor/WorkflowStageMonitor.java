package com.livingagent.core.workflow.monitor;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闭环43-P43-A: 工作流阶段监控
 * 监控各阶段耗时/卡点/超时，异常触发告警
 */
public class WorkflowStageMonitor {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStageMonitor.class);

    private static final long STAGE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final CrossLoopEventBus eventBus;
    private final Map<String, Map<String, StageState>> workflowStages = new ConcurrentHashMap<>();

    public WorkflowStageMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void recordStageStart(String workflowId, String stageName) {
        workflowStages.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>())
            .put(stageName, new StageState(stageName, Instant.now()));
        log.debug("[闭环43] 工作流阶段开始: workflow={}, stage={}", workflowId, stageName);
    }

    public void recordStageComplete(String workflowId, String stageName, boolean success) {
        Map<String, StageState> stages = workflowStages.get(workflowId);
        if (stages == null) return;
        StageState state = stages.get(stageName);
        if (state == null) return;

        state.completed = true;
        state.success = success;
        state.completedAt = Instant.now();
        long durationMs = Duration.between(state.startedAt, state.completedAt).toMillis();

        if (durationMs > STAGE_TIMEOUT_MS) {
            if (eventBus != null) {
                eventBus.publish(43, "performance_issue", CrossLoopEvent.EventPriority.DEGRADATION,
                    Map.of("content", String.format("工作流阶段 %s.%s 耗时 %ds 超过阈值 %ds",
                            workflowId, stageName, durationMs / 1000, STAGE_TIMEOUT_MS / 1000),
                        "workflowId", workflowId, "stageName", stageName, "durationMs", durationMs));
            }
            log.warn("[闭环43] 工作流阶段超时: workflow={}, stage={}, duration={}s",
                workflowId, stageName, durationMs / 1000);
        }
    }

    public WorkflowHealthReport getReport(String workflowId) {
        Map<String, StageState> stages = workflowStages.get(workflowId);
        if (stages == null) return null;
        int total = stages.size();
        int completed = (int) stages.values().stream().filter(s -> s.completed).count();
        int blocked = (int) stages.values().stream().filter(s -> !s.completed && Duration.between(s.startedAt, Instant.now()).toMillis() > STAGE_TIMEOUT_MS).count();
        return new WorkflowHealthReport(workflowId, total, completed, blocked, Instant.now());
    }

    public record WorkflowHealthReport(
        String workflowId, int totalStages, int completedStages,
        int blockedStages, Instant capturedAt
    ) {}

    private static class StageState {
        final String stageName;
        final Instant startedAt;
        volatile boolean completed;
        volatile boolean success;
        volatile Instant completedAt;

        StageState(String stageName, Instant startedAt) {
            this.stageName = stageName;
            this.startedAt = startedAt;
        }
    }
}
