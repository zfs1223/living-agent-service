package com.livingagent.core.employee.lifecycle;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闭环39-P39-A: Agent生命周期监控
 * 定期检查Agent状态(心跳/响应/错误率)，异常触发SelfHealingOrchestrator
 */
public class AgentLifecycleMonitor {

    private static final Logger log = LoggerFactory.getLogger(AgentLifecycleMonitor.class);

    private static final long HEARTBEAT_TIMEOUT_MS = 60_000;
    private static final double ERROR_RATE_THRESHOLD = 0.30;

    private final Map<String, AgentState> agentStates = new ConcurrentHashMap<>();
    private final CrossLoopEventBus eventBus;

    public AgentLifecycleMonitor(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void registerAgent(String agentId, String agentType) {
        agentStates.put(agentId, new AgentState(agentId, agentType, Instant.now()));
        log.info("[闭环39] Agent已注册生命周期监控: id={}, type={}", agentId, agentType);
    }

    public void unregisterAgent(String agentId) {
        agentStates.remove(agentId);
        log.info("[闭环39] Agent已注销生命周期监控: id={}", agentId);
    }

    public void recordHeartbeat(String agentId) {
        AgentState state = agentStates.get(agentId);
        if (state != null) {
            state.lastHeartbeat = Instant.now();
            state.heartbeatCount++;
        }
    }

    public void recordSuccess(String agentId) {
        AgentState state = agentStates.get(agentId);
        if (state != null) {
            state.totalExecutions++;
            state.successCount++;
            state.lastActivity = Instant.now();
        }
    }

    public void recordFailure(String agentId, String errorType) {
        AgentState state = agentStates.get(agentId);
        if (state != null) {
            state.totalExecutions++;
            state.failureCount++;
            state.lastError = errorType;
            state.lastErrorTime = Instant.now();
            state.lastActivity = Instant.now();

            checkAgentHealth(agentId, state);
        }
    }

    private void checkAgentHealth(String agentId, AgentState state) {
        if (state.totalExecutions < 3) return;

        double errorRate = (double) state.failureCount / state.totalExecutions;

        // 检查错误率
        if (errorRate > ERROR_RATE_THRESHOLD) {
            if (eventBus != null) {
                eventBus.publish(39, "agent_health_error", CrossLoopEvent.EventPriority.SELF_HEALING,
                    Map.of("content", String.format("Agent %s 错误率 %.1f%% 超过阈值 %.0f%%",
                        agentId, errorRate * 100, ERROR_RATE_THRESHOLD * 100),
                        "agentId", agentId, "errorRate", errorRate, "lastError", state.lastError != null ? state.lastError : ""));
            }
            log.warn("[闭环39] Agent健康告警: id={}, errorRate={}/{}", agentId,
                String.format("%.1f%%", errorRate * 100),
                String.format("%.0f%%", ERROR_RATE_THRESHOLD * 100));
        }

        // 检查心跳超时
        Duration sinceLastHeartbeat = Duration.between(state.lastHeartbeat, Instant.now());
        if (sinceLastHeartbeat.toMillis() > HEARTBEAT_TIMEOUT_MS) {
            if (eventBus != null) {
                eventBus.publish(39, "agent_heartbeat_timeout", CrossLoopEvent.EventPriority.SELF_HEALING,
                    Map.of("content", String.format("Agent %s 心跳超时 %ds", agentId, sinceLastHeartbeat.toSeconds()),
                        "agentId", agentId, "heartbeatTimeoutMs", sinceLastHeartbeat.toMillis()));
            }
            log.warn("[闭环39] Agent心跳超时: id={}, timeout={}s", agentId, sinceLastHeartbeat.toSeconds());
        }
    }

    public AgentHealthReport getHealthReport(String agentId) {
        AgentState state = agentStates.get(agentId);
        if (state == null) return null;
        return new AgentHealthReport(
            agentId, state.agentType,
            state.totalExecutions, state.successCount, state.failureCount,
            state.totalExecutions > 0 ? (double) state.failureCount / state.totalExecutions : 0,
            state.lastHeartbeat, state.lastActivity, state.lastError, state.lastErrorTime
        );
    }

    public Map<String, AgentHealthReport> getAllHealthReports() {
        Map<String, AgentHealthReport> reports = new ConcurrentHashMap<>();
        agentStates.forEach((id, state) -> reports.put(id, new AgentHealthReport(
            id, state.agentType,
            state.totalExecutions, state.successCount, state.failureCount,
            state.totalExecutions > 0 ? (double) state.failureCount / state.totalExecutions : 0,
            state.lastHeartbeat, state.lastActivity, state.lastError, state.lastErrorTime
        )));
        return reports;
    }

    public int getRegisteredAgentCount() {
        return agentStates.size();
    }

    public record AgentHealthReport(
        String agentId, String agentType,
        long totalExecutions, long successCount, long failureCount,
        double errorRate,
        Instant lastHeartbeat, Instant lastActivity,
        String lastError, Instant lastErrorTime
    ) {}

    private static class AgentState {
        final String agentId;
        final String agentType;
        final Instant registeredAt;
        volatile Instant lastHeartbeat;
        volatile Instant lastActivity;
        volatile Instant lastErrorTime;
        volatile String lastError;
        volatile long totalExecutions;
        volatile long successCount;
        volatile long failureCount;
        volatile long heartbeatCount;

        AgentState(String agentId, String agentType, Instant registeredAt) {
            this.agentId = agentId;
            this.agentType = agentType;
            this.registeredAt = registeredAt;
            this.lastHeartbeat = registeredAt;
            this.lastActivity = registeredAt;
        }
    }
}
