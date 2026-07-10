package com.livingagent.core.employee.lifecycle;

import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 闭环39-P39-C: Agent自动恢复服务
 * Agent崩溃→自动重启→配置降级→经验沉淀
 */
public class AgentAutoRecovery {

    private static final Logger log = LoggerFactory.getLogger(AgentAutoRecovery.class);

    private static final int MAX_RESTART_ATTEMPTS = 3;
    private static final long RESTART_COOLDOWN_MS = 30_000;

    private final AgentLifecycleMonitor lifecycleMonitor;
    private final CrossLoopEventBus eventBus;
    private final Map<String, RestartRecord> restartHistory = new ConcurrentHashMap<>();
    private final Map<String, String> degradedConfigs = new ConcurrentHashMap<>();

    private RecoveryActionHandler recoveryHandler;

    public interface RecoveryActionHandler {
        boolean restartAgent(String agentId, String degradedConfig);
        void onPermanentFailure(String agentId, String reason);
    }

    public AgentAutoRecovery(AgentLifecycleMonitor lifecycleMonitor, CrossLoopEventBus eventBus) {
        this.lifecycleMonitor = lifecycleMonitor;
        this.eventBus = eventBus;
    }

    public void setRecoveryHandler(RecoveryActionHandler handler) {
        this.recoveryHandler = handler;
    }

    /**
     * 检测到Agent异常时调用，执行自动恢复流程
     */
    public RecoveryResult attemptRecovery(String agentId, String failureReason) {
        RestartRecord record = restartHistory.computeIfAbsent(agentId,
            k -> new RestartRecord());

        // 检查重启冷却
        if (record.lastRestartTime > 0) {
            long sinceLast = System.currentTimeMillis() - record.lastRestartTime;
            if (sinceLast < RESTART_COOLDOWN_MS) {
                log.info("[闭环39] Agent {} 重启冷却中，剩余{}ms", agentId,
                    RESTART_COOLDOWN_MS - sinceLast);
                return new RecoveryResult(agentId, "COOLDOWN", false,
                    "重启冷却中", record.attemptCount);
            }
        }

        // 检查重启次数上限
        if (record.attemptCount >= MAX_RESTART_ATTEMPTS) {
            log.error("[闭环39] Agent {} 已达最大重启次数 {}，标记为永久故障",
                agentId, MAX_RESTART_ATTEMPTS);
            if (recoveryHandler != null) {
                recoveryHandler.onPermanentFailure(agentId,
                    String.format("重启%d次仍失败: %s", record.attemptCount, failureReason));
            }
            publishFailureExperience(agentId, failureReason, record.attemptCount);
            return new RecoveryResult(agentId, "PERMANENT_FAILURE", false,
                String.format("已重启%d次仍失败", record.attemptCount), record.attemptCount);
        }

        // 确定降级配置
        String degradedConfig = determineDegradedConfig(agentId, record.attemptCount);

        // 执行重启
        record.attemptCount++;
        record.lastRestartTime = System.currentTimeMillis();

        boolean restarted = false;
        if (recoveryHandler != null) {
            restarted = recoveryHandler.restartAgent(agentId, degradedConfig);
        }

        if (restarted) {
            degradedConfigs.put(agentId, degradedConfig);
            lifecycleMonitor.recordHeartbeat(agentId);
            log.info("[闭环39] Agent {} 自动恢复成功(第{}次)，降级配置: {}",
                agentId, record.attemptCount, degradedConfig);
        } else {
            log.warn("[闭环39] Agent {} 自动恢复失败(第{}次)", agentId, record.attemptCount);
        }

        return new RecoveryResult(agentId, restarted ? "RESTARTED" : "RESTART_FAILED",
            restarted, degradedConfig, record.attemptCount);
    }

    private String determineDegradedConfig(String agentId, int attemptCount) {
        return switch (attemptCount) {
            case 0 -> "NORMAL";
            case 1 -> "REDUCED_CONCURRENCY";
            case 2 -> "MINIMAL_FEATURES";
            default -> "SAFE_MODE";
        };
    }

    private void publishFailureExperience(String agentId, String failureReason, int attempts) {
        if (eventBus != null) {
            eventBus.publish(39, "agent_permanent_failure", CrossLoopEvent.EventPriority.SELF_HEALING,
                Map.of("content", String.format("Agent %s 永久故障: %s (尝试恢复%d次)", agentId, failureReason, attempts),
                    "agentId", agentId, "failureReason", failureReason, "restartAttempts", attempts));
        }
    }

    public Map<String, RestartRecord> getRestartHistory() {
        return Map.copyOf(restartHistory);
    }

    public String getDegradedConfig(String agentId) {
        return degradedConfigs.getOrDefault(agentId, "NORMAL");
    }

    public record RecoveryResult(
        String agentId, String action, boolean success,
        String detail, int attemptCount
    ) {}

    public static class RestartRecord {
        volatile int attemptCount;
        volatile long lastRestartTime;
    }
}
