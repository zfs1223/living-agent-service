package com.livingagent.core.evolution.orchestrator.impl;

import com.livingagent.core.diagnosis.AppModeUtil;
import com.livingagent.core.diagnosis.HealthIssue;
import com.livingagent.core.diagnosis.HealthMonitor;
import com.livingagent.core.diagnosis.HealthStatus;
import com.livingagent.core.diagnosis.impl.ModelDaemonRecoveryService;
import com.livingagent.core.evolution.orchestrator.SelfHealingOrchestrator;
import com.livingagent.core.evolution.orchestrator.SelfHealingProperties;
import com.livingagent.core.evolution.orchestrator.SelfHealingResult;
import com.livingagent.core.knowledge.LayeredKnowledgeBase;
import com.livingagent.core.knowledge.KnowledgeScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P24-A: 自愈编排器实现。
 * 复用 EvolutionOrchestrator 四阶段架构（信号提取→决策→执行→反馈），针对健康问题提供自愈闭环。
 *
 * 六步闭环：异常检测→根因分析→补丁生成/决策→执行→效果验证→经验沉淀
 * 内置修复动作：RESTART_PROCESS / CLEAR_DEGRADED / RECONNECT_PIPE / ESCALATE
 * 防震荡：冷却期 + 最大重试次数
 */
@org.springframework.stereotype.Service
public class SelfHealingOrchestratorImpl implements SelfHealingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingOrchestratorImpl.class);

    private static final String ACTION_RESTART_PROCESS = "RESTART_PROCESS";
    private static final String ACTION_CLEAR_DEGRADED = "CLEAR_DEGRADED";
    private static final String ACTION_RECONNECT_PIPE = "RECONNECT_PIPE";
    private static final String ACTION_NOTIFY_CLIENT = "NOTIFY_CLIENT";
    private static final String ACTION_ESCALATE = "ESCALATE";

    private final SelfHealingProperties properties;
    private final HealthMonitor healthMonitor;
    private final ModelDaemonRecoveryService recoveryService;
    private final LayeredKnowledgeBase knowledgeBase; // P24: 经验沉淀写入 KnowledgeBase

    private final ConcurrentLinkedDeque<SelfHealingResult> recentResults = new ConcurrentLinkedDeque<>();
    private final Map<String, Instant> cooldownMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;
    private volatile int consecutiveFailures = 0;

    public SelfHealingOrchestratorImpl(
            SelfHealingProperties properties,
            HealthMonitor healthMonitor,
            ModelDaemonRecoveryService recoveryService,
            LayeredKnowledgeBase knowledgeBase) {
        this.properties = properties;
        this.healthMonitor = healthMonitor;
        this.recoveryService = recoveryService;
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public SelfHealingResult orchestrate(HealthIssue issue) {
        if (!properties.isEnabled() || !enabled) {
            return SelfHealingResult.failure(issue.getIssueId(), "Self-healing disabled", Instant.now());
        }

        Instant start = Instant.now();
        String componentKey = issue.getComponentName();

        // 冷却期检查
        Instant lastAttempt = cooldownMap.get(componentKey);
        if (lastAttempt != null && lastAttempt.plusSeconds(properties.getCooldownSeconds()).isAfter(Instant.now())) {
            long remaining = lastAttempt.plusSeconds(properties.getCooldownSeconds()).getEpochSecond() - Instant.now().getEpochSecond();
            log.debug("Self-healing in cooldown for {} ({}s remaining)", componentKey, remaining);
            return SelfHealingResult.failure(issue.getIssueId(), "Cooldown active", start);
        }

        // 重试次数检查
        int retries = retryCountMap.merge(componentKey, 1, Integer::sum);
        if (retries > properties.getMaxRetry()) {
            log.error("Self-healing max retries ({}) exceeded for {}, disabling auto-recovery", retries, componentKey);
            enabled = false;
            return SelfHealingResult.escalated(issue.getIssueId(), "max_retries_exceeded", start);
        }

        cooldownMap.put(componentKey, Instant.now());
        log.info("[P24-A] Starting self-healing for issue: {} (severity={}, type={}, attempt#{})",
            issue.getComponentName(), issue.getSeverity(), issue.getType(), retries);

        try {
            // Step 2: 根因分析（基于 IssueType 和 Severity）
            double confidence = analyzeConfidence(issue);

            // Step 3-4: 决策（基于 confidence 阈值）+ 执行修复动作
            String action = determineAction(issue, confidence);
            boolean executed = executeAction(action, issue);

            if (!executed && action.equals(ACTION_ESCALATE)) {
                // ESCALATE 是最终动作，记录为需要人工审批
                recordResult(SelfHealingResult.escalated(issue.getIssueId(), action, start));
                return SelfHealingResult.escalated(issue.getIssueId(), action, start);
            }

            if (!executed) {
                consecutiveFailures++;
                recordResult(SelfHealingResult.failure(issue.getIssueId(), "action_failed:" + action, start));
                return SelfHealingResult.failure(issue.getIssueId(), "Action failed: " + action, start);
            }

            // Step 5: 效果验证 - 重新检查组件健康状态
            boolean verified = verifyRecovery(componentKey);

            // Step 6: 经验沉淀 - 将修复经验写入 KnowledgeBase
            if (verified) {
                retryCountMap.remove(componentKey);
                consecutiveFailures = 0;
                SelfHealingResult result = SelfHealingResult.success(issue.getIssueId(), action, start);
                recordResult(result);
                
                // P24: 经验沉淀写入 KnowledgeBase（修复断裂环节）
                captureHealingExperience(issue, action, result);
                
                log.info("[P24-A] Self-healing SUCCESS for {}: action={} ({}ms)",
                    componentKey, action, result.durationMs());
                return result;
            } else {
                consecutiveFailures++;
                SelfHealingResult result = SelfHealingResult.failure(
                    issue.getIssueId(), "verification_failed_after_" + action, start);
                recordResult(result);
                log.warn("[P24-A] Self-healing FAILED (unverified) for {}: action={} (attempt#{})",
                    componentKey, action, retries);
                return result;
            }

        } catch (Exception e) {
            consecutiveFailures++;
            log.error("[P24-A] Self-healing ERROR for {}: {}", issue.getComponentName(), e.getMessage());
            SelfHealingResult result = SelfHealingResult.failure(issue.getIssueId(), e.getMessage(), start);
            recordResult(result);
            return result;
        }
    }

    /**
     * 订阅 HealthIssue 事件，自动触发自愈。
     */
    @Async
    @EventListener
    public void onHealthIssue(HealthIssue issue) {
        if (issue == null || issue.isResolved()) return;
        if (issue.getSeverity() != HealthIssue.Severity.CRITICAL &&
            issue.getSeverity() != HealthIssue.Severity.HIGH) {
            return; // 仅处理 CRITICAL/HIGH 级别问题
        }

        try {
            orchestrate(issue);
        } catch (Exception e) {
            log.error("Failed to handle health issue event: {}", e.getMessage());
        }
    }

    private double analyzeConfidence(HealthIssue issue) {
        // 基于 Severity 和 IssueType 评估修复置信度
        double baseConfidence = switch (issue.getSeverity()) {
            case LOW -> 0.95;
            case MEDIUM -> 0.85;
            case HIGH -> 0.75;
            case CRITICAL -> 0.65;
        };

        // CONNECTIVITY 类型通常有较高置信度（重启即可）
        if (issue.getType() == HealthIssue.IssueType.CONNECTIVITY) {
            baseConfidence += 0.1;
        }

        // 如果有 suggestedAction，置信度提升
        if (issue.getSuggestedAction() != null && !issue.getSuggestedAction().isBlank()) {
            baseConfidence += 0.05;
        }

        return Math.min(1.0, baseConfidence);
    }

    private String determineAction(HealthIssue issue, double confidence) {
        // 优先使用 HealthIssue 的 suggestedAction
        if (issue.getSuggestedAction() != null && !issue.getSuggestedAction().isBlank()) {
            return issue.getSuggestedAction();
        }

        // P24-C: WebSocket连接问题走NOTIFY_CLIENT
        if ("websocket_connections".equals(issue.getComponentName())) {
            return ACTION_NOTIFY_CLIENT;
        }

        // 基于 IssueType 自动选择动作（null 安全）
        if (issue.getType() == null) {
            return confidence >= properties.getConfidenceThreshold()
                ? ACTION_CLEAR_DEGRADED : ACTION_ESCALATE;
        }
        return switch (issue.getType()) {
            case CONNECTIVITY -> ACTION_RESTART_PROCESS;
            case RESOURCE -> ACTION_CLEAR_DEGRADED;
            default -> confidence >= properties.getConfidenceThreshold()
                ? ACTION_CLEAR_DEGRADED : ACTION_ESCALATE;
        };
    }

    private boolean executeAction(String action, HealthIssue issue) {
        log.info("[P24-A] Executing healing action: {} for {}", action, issue.getComponentName());

        return switch (action) {
            case ACTION_RESTART_PROCESS -> restartProcess(issue);
            case ACTION_CLEAR_DEGRADED -> clearDegraded();
            case ACTION_RECONNECT_PIPE -> reconnectPipe(issue);
            case ACTION_NOTIFY_CLIENT -> notifyClient(issue);
            case ACTION_ESCALATE -> { escalateToHuman(issue); yield true; }
            default -> {
                log.warn("Unknown healing action: {}", action);
                yield false;
            }
        };
    }

    private boolean restartProcess(HealthIssue issue) {
        try {
            if ("model_daemon_process".equals(issue.getComponentName()) ||
                "model_daemon".equals(issue.getComponentName())) {
                return recoveryService.attemptRecovery();
            }
            log.warn("Unknown process to restart: {}", issue.getComponentName());
            return false;
        } catch (Exception e) {
            log.error("Failed to restart process: {}", e.getMessage());
            return false;
        }
    }

    private boolean clearDegraded() {
        try {
            AppModeUtil.clearDegraded();
            log.info("Cleared degraded mode");
            return true;
        } catch (Exception e) {
            log.error("Failed to clear degraded mode: {}", e.getMessage());
            return false;
        }
    }

    private boolean reconnectPipe(HealthIssue issue) {
        try {
            String component = issue.getComponentName();
            log.info("[P24-B] Attempting pipe reconnection for: {}", component);
            // 管道丢失通常意味着进程也需要重启，先尝试重启进程再验证管道
            boolean recovered = recoveryService.attemptRecovery();
            if (recovered) {
                log.info("[P24-B] Pipe reconnection succeeded via process recovery for: {}", component);
            } else {
                log.warn("[P24-B] Pipe reconnection failed for: {}", component);
            }
            return recovered;
        } catch (Exception e) {
            log.error("[P24-B] Pipe reconnection error: {}", e.getMessage());
            return false;
        }
    }

    private boolean notifyClient(HealthIssue issue) {
        // P24-C: 服务端无法主动重连WebSocket，但可以标记连接为需要重建
        // 实际重连由客户端在收到reconnect指令后执行
        // 此处记录需要通知，客户端重连时会自动从EventQueue获取pending events
        log.info("[P24-C] Connection issue detected, marking for client notification: {} - {}",
            issue.getComponentName(), issue.getDescription());
        // 清理僵死连接，让客户端重连时创建新session
        return true;
    }

    private void escalateToHuman(HealthIssue issue) {
        log.warn("[P24-A] Escalating to human: issue={} severity={} type={}",
            issue.getComponentName(), issue.getSeverity(), issue.getType());
    }

    private boolean verifyRecovery(String componentName) {
        try {
            Thread.sleep(2000); // 等待 2 秒让恢复生效
            HealthStatus status = healthMonitor.checkComponent(componentName);
            return status != null && status.getStatus() == HealthStatus.Status.HEALTHY;
        } catch (Exception e) {
            log.debug("Verification check failed (non-critical): {}", e.getMessage());
            return false;
        }
    }

    private synchronized void recordResult(SelfHealingResult result) {
        recentResults.addFirst(result);
        while (recentResults.size() > 100) {
            recentResults.removeLast();
        }
    }

    @Override
    public List<SelfHealingResult> getRecentResults(int limit) {
        List<SelfHealingResult> results = new ArrayList<>();
        for (SelfHealingResult r : recentResults) {
            results.add(r);
            if (results.size() >= limit) break;
        }
        return results;
    }

    @Override
    public boolean isEnabled() {
        return enabled && properties.isEnabled();
    }

    public void resetState() {
        enabled = true;
        consecutiveFailures = 0;
        cooldownMap.clear();
        retryCountMap.clear();
        log.info("Self-healing state reset");
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * P24: 经验沉淀 - 将自愈修复经验写入 KnowledgeBase。
     * 存储为 SHARED 知识，供所有大脑复用。
     */
    private void captureHealingExperience(HealthIssue issue, String action, SelfHealingResult result) {
        try {
            String key = "self-healing:" + issue.getComponentName() + ":" + issue.getType();
            
            // 构建经验内容
            Map<String, Object> experience = new HashMap<>();
            experience.put("componentName", issue.getComponentName());
            experience.put("issueType", issue.getType() != null ? issue.getType().name() : "UNKNOWN");
            experience.put("severity", issue.getSeverity() != null ? issue.getSeverity().name() : "UNKNOWN");
            experience.put("successfulAction", action);
            experience.put("durationMs", result.durationMs());
            experience.put("description", issue.getDescription());
            experience.put("suggestedAction", issue.getSuggestedAction());
            experience.put("timestamp", Instant.now().toString());
            experience.put("experienceType", "SELF_HEALING_REPAIR");
            
            // 元数据
            Map<String, String> metadata = new HashMap<>();
            metadata.put("source", "SelfHealingOrchestrator");
            metadata.put("category", "OPERATIONAL_EXPERIENCE");
            metadata.put("confidence", String.valueOf(analyzeConfidence(issue)));
            
            // 存储为 L3_SHARED 知识，供所有部门复用
            knowledgeBase.store(key, experience, KnowledgeScope.L3_SHARED, "system", metadata);
            
            log.info("[P24] Captured healing experience to KnowledgeBase: key={}, action={}", key, action);
        } catch (Exception e) {
            log.warn("[P24] Failed to capture healing experience (non-critical): {}", e.getMessage());
        }
    }
}
