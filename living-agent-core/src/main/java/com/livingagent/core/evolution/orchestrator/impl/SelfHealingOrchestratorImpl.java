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

            // DBS PSP: 置信度低于 70% 时启动 5-Why 根因分析
            if (confidence < 0.70) {
                List<String> fiveWhyChain = performFiveWhyAnalysis(issue);
                if (!fiveWhyChain.isEmpty()) {
                    // 将 5-Why 分析结果注入经验沉淀
                    captureFiveWhyExperience(issue, fiveWhyChain);
                }
            }

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
        // P24-C: 空闲连接的清理已由 ConnectionHealthCheck.check() 直接执行
        // 此处仅记录日志，不再重复处理
        log.info("[P24-C] Connection issue noted (auto-cleanup handled by ConnectionHealthCheck): {} - {}",
            issue.getComponentName(), issue.getDescription());
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

    /**
     * DBS PSP: 5-Why 根因分析。
     * 当快速根因置信度低于 70% 时，执行 5 层 Why 追问，逐层深入根因。
     * 返回 5-Why 分析链（从表面原因到根本原因），空列表表示分析失败。
     */
    private List<String> performFiveWhyAnalysis(HealthIssue issue) {
        List<String> chain = new ArrayList<>();
        try {
            String currentProblem = issue.getDescription() != null ? issue.getDescription() : issue.getComponentName();
            
            // Why 1: 为什么发生这个问题？
            String why1 = inferWhy(currentProblem, issue.getType(), 1);
            chain.add("Why1:" + why1);
            
            // Why 2: 为什么会出现 Why1 的原因？
            String why2 = inferWhy(why1, issue.getType(), 2);
            chain.add("Why2:" + why2);
            
            // Why 3: 为什么存在 Why2 的原因？
            String why3 = inferWhy(why2, issue.getType(), 3);
            chain.add("Why3:" + why3);
            
            // Why 4: 为什么允许 Why3 存在？
            String why4 = inferWhy(why3, issue.getType(), 4);
            chain.add("Why4:" + why4);
            
            // Why 5: 为什么 Why4 未被发现？
            String why5 = inferWhy(why4, issue.getType(), 5);
            chain.add("Why5:" + why5);
            
            log.info("[DBS-PSP] 5-Why analysis for {}: {}", issue.getComponentName(), chain);
        } catch (Exception e) {
            log.warn("[DBS-PSP] 5-Why analysis failed (non-critical): {}", e.getMessage());
        }
        return chain;
    }

    /**
     * 基于 IssueType 和当前问题描述推断 Why 层原因。
     * 规则驱动（LLM 增强留给后续版本），每层追问更深层原因。
     */
    private String inferWhy(String currentDescription, HealthIssue.IssueType issueType, int depth) {
        if (currentDescription == null || currentDescription.isBlank()) {
            return "unknown_cause_at_depth_" + depth;
        }
        
        // 基于 IssueType 的通用推理规则
        if (issueType == HealthIssue.IssueType.CONNECTIVITY) {
            return switch (depth) {
                case 1 -> "连接中断或超时";
                case 2 -> "目标服务不可用或网络异常";
                case 3 -> "服务进程崩溃或资源不足";
                case 4 -> "缺少进程健康监控和自动重启机制";
                default -> "系统级容错和自愈能力不足";
            };
        } else if (issueType == HealthIssue.IssueType.RESOURCE) {
            return switch (depth) {
                case 1 -> "资源耗尽或降级";
                case 2 -> "资源分配不合理或泄漏";
                case 3 -> "缺少资源使用监控和预警";
                case 4 -> "未建立资源基准线和自动扩缩容机制";
                default -> "资源管理体系缺乏持续优化";
            };
        } else {
            return switch (depth) {
                case 1 -> "系统异常: " + abbreviate(currentDescription, 50);
                case 2 -> "异常未及时检测和处理";
                case 3 -> "缺少对该类异常的监控规则";
                case 4 -> "监控体系覆盖不完整";
                default -> "系统性改善机制不足（需DBS持续改善）";
            };
        }
    }

    private String abbreviate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * DBS PSP: 将 5-Why 分析结果写入 KnowledgeBase 作为经验沉淀。
     */
    private void captureFiveWhyExperience(HealthIssue issue, List<String> fiveWhyChain) {
        try {
            String key = "five-why:" + issue.getComponentName() + ":" + issue.getType() + ":" + Instant.now().getEpochSecond();
            
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("componentName", issue.getComponentName());
            analysis.put("issueType", issue.getType() != null ? issue.getType().name() : "UNKNOWN");
            analysis.put("severity", issue.getSeverity() != null ? issue.getSeverity().name() : "UNKNOWN");
            analysis.put("fiveWhyChain", fiveWhyChain);
            analysis.put("rootCause", fiveWhyChain.isEmpty() ? "unknown" : fiveWhyChain.get(fiveWhyChain.size() - 1));
            analysis.put("timestamp", Instant.now().toString());
            analysis.put("experienceType", "DBS_FIVE_WHY_ANALYSIS");
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("source", "SelfHealingOrchestrator.DBS_PSP");
            metadata.put("category", "ROOT_CAUSE_ANALYSIS");
            metadata.put("dbsTool", "dbs-problem-solving");
            
            knowledgeBase.store(key, analysis, KnowledgeScope.L3_SHARED, "system", metadata);
            log.info("[DBS-PSP] Captured 5-Why analysis to KnowledgeBase: key={}", key);
        } catch (Exception e) {
            log.warn("[DBS-PSP] Failed to capture 5-Why analysis (non-critical): {}", e.getMessage());
        }
    }

    /**
     * DBS PSP: 生成 A3 报告。
     * 当自愈无法自动解决时，生成结构化问题分析报告（背景→现状→目标→根因→对策→验证→跟进）。
     * 报告存储到 KnowledgeBase 供闭环 41 人工干预使用。
     */
    public Map<String, Object> generateA3Report(HealthIssue issue, List<String> fiveWhyChain) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "A3_PROBLEM_SOLVING_REPORT");
        report.put("issueId", issue.getIssueId());
        report.put("timestamp", Instant.now().toString());
        
        // 1. 背景
        Map<String, Object> background = new HashMap<>();
        background.put("component", issue.getComponentName());
        background.put("severity", issue.getSeverity() != null ? issue.getSeverity().name() : "UNKNOWN");
        background.put("type", issue.getType() != null ? issue.getType().name() : "UNKNOWN");
        report.put("background", background);
        
        // 2. 现状
        Map<String, Object> currentCondition = new HashMap<>();
        currentCondition.put("description", issue.getDescription());
        currentCondition.put("suggestedAction", issue.getSuggestedAction());
        currentCondition.put("autoHealingAttempted", true);
        currentCondition.put("autoHealingSucceeded", false);
        report.put("currentCondition", currentCondition);
        
        // 3. 目标
        Map<String, Object> targetCondition = new HashMap<>();
        targetCondition.put("goal", "恢复组件健康状态，消除根因，防止复发");
        targetCondition.put("metric", "HealthStatus == HEALTHY for 24h");
        report.put("targetCondition", targetCondition);
        
        // 4. 根因分析
        report.put("rootCauseAnalysis", fiveWhyChain);
        report.put("rootCause", fiveWhyChain.isEmpty() ? "pending_investigation" : fiveWhyChain.get(fiveWhyChain.size() - 1));
        
        // 5. 对策
        Map<String, Object> countermeasures = new HashMap<>();
        countermeasures.put("immediate", "人工审查并修复");
        countermeasures.put("preventive", "更新标准作业书（dbs-standard-work）和监控规则");
        report.put("countermeasures", countermeasures);
        
        // 6. 验证
        Map<String, Object> verification = new HashMap<>();
        verification.put("method", "人工确认组件恢复健康 + 24h 无复发");
        verification.put("responsible", "闭环41 人工干预");
        report.put("verification", verification);
        
        // 7. 跟进
        Map<String, Object> followUp = new HashMap<>();
        followUp.put("action", "更新标准作业 + 5-Why 结果写入知识库");
        followUp.put("dbsTool", "dbs-problem-solving v1.0");
        report.put("followUp", followUp);
        
        // 存储到 KnowledgeBase
        try {
            String key = "a3-report:" + issue.getComponentName() + ":" + Instant.now().getEpochSecond();
            Map<String, String> metadata = new HashMap<>();
            metadata.put("source", "SelfHealingOrchestrator.A3Report");
            metadata.put("category", "A3_PROBLEM_REPORT");
            metadata.put("dbsTool", "dbs-problem-solving");
            knowledgeBase.store(key, report, KnowledgeScope.L3_SHARED, "system", metadata);
            log.info("[DBS-PSP] A3 report generated and stored: key={}", key);
        } catch (Exception e) {
            log.warn("[DBS-PSP] Failed to store A3 report (non-critical): {}", e.getMessage());
        }
        
        return report;
    }
}
