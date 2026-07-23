package com.livingagent.core.evolution.orchestrator.impl;

import com.livingagent.core.diagnosis.AppModeUtil;
import com.livingagent.core.diagnosis.HealthIssue;
import com.livingagent.core.evolution.orchestrator.*;
import com.livingagent.core.evolution.signal.EvolutionSignal;
import com.livingagent.core.database.repository.InterventionDecisionRepository;
import com.livingagent.core.database.entity.InterventionDecisionEntity;
import com.livingagent.core.security.SandboxViolationTracker;
import com.livingagent.core.brain.BrainBoundaryEnforcer;
import com.livingagent.core.autonomy.PerformanceStatsService;
import com.livingagent.core.knowledge.KnowledgeConsumptionFeedback;
import com.livingagent.core.autonomous.bounty.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * P31-A: 跨闭环协同编排器实现。
 * 接收各 L3 闭环事件，按优先级仲裁后分发到对应处理器。
 * 
 * P31: 分发后真正执行 + 审计持久化
 *
 * 闭环映射：
 * - 闭环24（自愈）→ SelfHealingOrchestrator.orchestrate()
 * - 闭环27（降级）→ AppModeUtil.setMode()
 * - 闭环30（安全）→ BrainBoundaryEnforcer (记录+审计)
 * - 闭环28（回执）→ ExecutionReceiptReviewer (记录+审计)
 * - 闭环25（经济）→ LedgerService (记录+审计)
 * - 闭环26（知识）→ KnowledgeCaptureService (记录+审计)
 * - 闭环29（个性）→ PersonalityMutation (记录+审计)
 */
@Service
public class SelfGovernanceOrchestratorImpl implements SelfGovernanceOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SelfGovernanceOrchestratorImpl.class);

    private final SelfGovernanceProperties properties;
    private final LoopPriorityArbiter arbiter;
    private final SelfHealingOrchestrator selfHealingOrchestrator;
    private final InterventionDecisionRepository auditRepository;
    private final SandboxViolationTracker sandboxViolationTracker;
    private final BrainBoundaryEnforcer brainBoundaryEnforcer;
    private final PerformanceStatsService performanceStatsService;
    private final KnowledgeConsumptionFeedback knowledgeConsumptionFeedback;
    private final LedgerService ledgerService;

    private final ConcurrentLinkedDeque<CrossLoopEvent> pendingEvents = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<GovernanceReport> auditTrail = new ConcurrentLinkedDeque<>();
    private volatile int totalProcessedEvents = 0;
    private volatile Instant lastOrchestrationTime = null;
    private final ApplicationEventPublisher eventPublisher;

    public SelfGovernanceOrchestratorImpl(
            SelfGovernanceProperties properties,
            LoopPriorityArbiter arbiter,
            SelfHealingOrchestrator selfHealingOrchestrator,
            InterventionDecisionRepository auditRepository,
            SandboxViolationTracker sandboxViolationTracker,
            BrainBoundaryEnforcer brainBoundaryEnforcer,
            PerformanceStatsService performanceStatsService,
            KnowledgeConsumptionFeedback knowledgeConsumptionFeedback,
            LedgerService ledgerService,
            ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.arbiter = arbiter;
        this.selfHealingOrchestrator = selfHealingOrchestrator;
        this.auditRepository = auditRepository;
        this.sandboxViolationTracker = sandboxViolationTracker;
        this.brainBoundaryEnforcer = brainBoundaryEnforcer;
        this.performanceStatsService = performanceStatsService;
        this.knowledgeConsumptionFeedback = knowledgeConsumptionFeedback;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void submitEvent(CrossLoopEvent event) {
        if (!properties.isEnabled()) {
            log.debug("Governance disabled, ignoring event: {}", event);
            return;
        }

        pendingEvents.add(event);
        log.info("[P31-A] Event submitted: loop={} type={} priority={}",
            event.getSourceLoop(), event.getEventType(), event.getPriority());
    }

    /**
     * 订阅 CrossLoopEvent，自动触发协同编排。
     */
    @Async
    @EventListener
    public void onCrossLoopEvent(CrossLoopEvent event) {
        submitEvent(event);
    }

    /**
     * 订阅 HealthIssue 事件，转发为闭环24（自愈）事件。
     */
    @Async
    @EventListener
    public void onHealthIssue(HealthIssue issue) {
        if (issue == null || issue.isResolved()) return;
        if (issue.getSeverity() != HealthIssue.Severity.CRITICAL &&
            issue.getSeverity() != HealthIssue.Severity.HIGH) return;

        int cooldown = properties.getCooldownSeconds().getOrDefault("self-healing", 300);
        submitEvent(new CrossLoopEvent(this, 24, "health_issue:" + issue.getComponentName(),
            CrossLoopEvent.EventPriority.SELF_HEALING,
            Map.of("issueId", issue.getIssueId(), "component", issue.getComponentName(),
                   "severity", issue.getSeverity().name()),
            cooldown));
    }

    @Async
    @EventListener
    public void onEvolutionSignal(EvolutionSignal signal) {
        if (signal == null) return;

        int loopId = mapSignalToLoop(signal);
        CrossLoopEvent.EventPriority priority = mapSignalToPriority(signal);

        int cooldown = properties.getCooldownSeconds().getOrDefault("evolution-signal", 300);
        submitEvent(new CrossLoopEvent(this, loopId, "evolution_signal:" + signal.getType(),
            priority,
            Map.of("signalId", signal.getSignalId(),
                "signalType", signal.getType().name(),
                "category", signal.getCategory().name(),
                "content", signal.getContent() != null ? signal.getContent() : "",
                "source", signal.getSource() != null ? signal.getSource() : ""),
            cooldown));

        log.info("[闭环17] EvolutionSignal consumed: type={}, category={}, source={}",
            signal.getType(), signal.getCategory(), signal.getSource());
    }

    private int mapSignalToLoop(EvolutionSignal signal) {
        if (signal.isRepairSignal()) return 24;
        if (signal.isOptimizeSignal()) return 27;
        if (signal.isInnovateSignal()) return 26;
        // P5-1: DBS VOC/KAIZEN 信号映射
        if (signal.getCategory() == EvolutionSignal.SignalCategory.VOC) return 4;
        if (signal.getType() == EvolutionSignal.SignalType.KAIZEN_EVENT) return 31;
        return 24;
    }

    private CrossLoopEvent.EventPriority mapSignalToPriority(EvolutionSignal signal) {
        return switch (signal.getCategory()) {
            case REPAIR -> CrossLoopEvent.EventPriority.SELF_HEALING;
            case OPTIMIZE -> CrossLoopEvent.EventPriority.DEGRADATION;
            case INNOVATE -> CrossLoopEvent.EventPriority.KNOWLEDGE;
            case VOC -> CrossLoopEvent.EventPriority.KNOWLEDGE;
        };
    }

    @Override
    public GovernanceReport orchestrate() {
        if (!properties.isEnabled()) {
            return GovernanceReport.empty();
        }

        List<CrossLoopEvent> events = new ArrayList<>();
        while (!pendingEvents.isEmpty()) {
            events.add(pendingEvents.poll());
        }

        if (events.isEmpty()) {
            return GovernanceReport.empty();
        }

        lastOrchestrationTime = Instant.now();

        // 优先级仲裁
        List<CrossLoopEvent> sorted = arbiter.arbitrate(events);

        GovernanceReport.Builder reportBuilder = GovernanceReport.builder();
        int executed = 0, successful = 0, failed = 0, escalated = 0;

        for (CrossLoopEvent event : sorted) {
            long startTime = System.currentTimeMillis();
            String result = dispatch(event);
            long duration = System.currentTimeMillis() - startTime;

            executed++;
            reportBuilder.addExecution(new GovernanceReport.EventExecution(
                event.getSourceLoop(), event.getEventType(), result, duration));

            if ("success".equals(result)) {
                successful++;
            } else if ("escalated".equals(result)) {
                escalated++;
            } else {
                failed++;
            }

            totalProcessedEvents++;
        }

        reportBuilder.totalEvents(sorted.size())
            .executedEvents(executed)
            .successfulEvents(successful)
            .failedEvents(failed)
            .escalatedEvents(escalated);

        GovernanceReport report = reportBuilder.build();
        
        // P31: 审计轨迹保存
        auditTrail.addLast(report);
        if (auditTrail.size() > 100) auditTrail.removeFirst();
        
        // P31: 审计持久化到数据库
        persistAudit(report);
        
        log.info("[P31-A] Orchestration completed: {} events processed ({} success, {} failed, {} escalated)",
            report.executedEvents(), report.successfulEvents(), report.failedEvents(), report.escalatedEvents());

        return report;
    }

    /**
     * P31: 审计持久化到数据库。
     */
    private void persistAudit(GovernanceReport report) {
        try {
            InterventionDecisionEntity audit = new InterventionDecisionEntity();
            audit.setDepartment("_governance");
            audit.setOperationType("GOVERNANCE_ORCHESTRATION");
            audit.setAiDecision("executed=" + report.executedEvents() + ",success=" + report.successfulEvents());
            audit.setHumanDecision("automated");
            audit.setStatus("COMPLETED");
            audit.setCreatedAt(Instant.now());
            auditRepository.save(audit);
            log.debug("P31: Governance audit persisted: id={}", audit.getId());
        } catch (Exception e) {
            log.warn("P31: Failed to persist audit (non-critical): {}", e.getMessage());
        }
    }

    private String dispatch(CrossLoopEvent event) {
        int loopId = event.getSourceLoop();

        // P7-1: DBS 技能关联映射 — 各 dispatch 执行前记录关联的 DBS 技能
        String dbsSkill = mapLoopToDbsSkill(loopId);
        if (dbsSkill != null) {
            log.debug("[P31-DBS] 闭环{}关联DBS技能: {}", loopId, dbsSkill);
        }

        try {
            return switch (loopId) {
                case 1 -> dispatchWebSocketRecovery(event);
                case 24 -> dispatchSelfHealing(event);
                case 27 -> dispatchDegradation(event);
                case 30 -> dispatchSecurity(event);
                case 28 -> dispatchReceipt(event);
                case 25 -> dispatchEconomic(event);
                case 26 -> dispatchKnowledge(event);
                case 29 -> dispatchPersonality(event);
                default -> {
                    log.warn("Unknown loop ID: {}", loopId);
                    yield "unknown_loop";
                }
            };
        } catch (Exception e) {
            log.error("[P31-A] Dispatch failed for loop {}: {}", loopId, e.getMessage());
            return "error:" + e.getMessage();
        }
    }

    /**
     * P7-1: 闭环到 DBS 技能的映射。
     * 各 dispatch 方法关联的 DBS 技能提供方法论指导，不替代执行逻辑。
     *
     * 映射关系：
     * - 闭环24（自愈）→ dbs-problem-solving（5-Why+A3+PDCA）
     * - 闭环1（WS恢复）→ dbs-value-stream-mapping（价值流分析）
     * - 闭环27（降级）→ dbs-problem-solving（问题解决）
     * - 闭环30（安全）→ dbs-standard-work（标准作业）
     * - 闭环28（回执）→ dbs-visual-management（可视化管理）
     * - 闭环25（经济）→ dbs-value-stream-mapping（价值流分析）
     * - 闭环26（知识）→ dbs-5s-audit + dbs-standard-work
     * - 闭环29（个性）→ dbs-talent-development（人才发展）
     */
    private String mapLoopToDbsSkill(int loopId) {
        return switch (loopId) {
            case 24 -> "dbs-problem-solving";
            case 1 -> "dbs-value-stream-mapping";
            case 27 -> "dbs-problem-solving";
            case 30 -> "dbs-standard-work";
            case 28 -> "dbs-visual-management";
            case 25 -> "dbs-value-stream-mapping";
            case 26 -> "dbs-standard-work";
            case 29 -> "dbs-talent-development";
            default -> null;
        };
    }

    private String dispatchSelfHealing(CrossLoopEvent event) {
        if (selfHealingOrchestrator == null || !selfHealingOrchestrator.isEnabled()) {
            log.warn("[P31-A] SelfHealingOrchestrator not available or disabled");
            return "disabled";
        }

        // P31: 真正执行自愈（简化版：直接调用 orchestrate）
        String issueId = event.getPayload() != null ? (String) event.getPayload().get("issueId") : null;
        if (issueId != null) {
            HealthIssue issue = new HealthIssue(issueId, "CrossLoopEvent triggered", HealthIssue.Severity.HIGH);
            issue.setType(HealthIssue.IssueType.CONNECTIVITY);

            SelfHealingResult result = selfHealingOrchestrator.orchestrate(issue);
            log.info("[P31] SelfHealing executed: issueId={}, success={}, duration={}ms",
                issueId, result.success(), result.durationMs());
            return result.success() ? "success" : "failed";
        }

        log.info("[P31] SelfHealing dispatched (no issue found): eventType={}", event.getEventType());
        return "success";
    }

    private String dispatchWebSocketRecovery(CrossLoopEvent event) {
        String eventType = event.getEventType();
        log.info("[闭环1] WebSocket transport error received: type={}", eventType);

        if (selfHealingOrchestrator != null && selfHealingOrchestrator.isEnabled()) {
            HealthIssue issue = new HealthIssue(
                "ws-transport-" + event.dedupeKey(),
                "WebSocket transport error: " + eventType,
                HealthIssue.Severity.HIGH);
            issue.setType(HealthIssue.IssueType.CONNECTIVITY);

            SelfHealingResult result = selfHealingOrchestrator.orchestrate(issue);
            log.info("[闭环1] WebSocket recovery executed: success={}, duration={}ms",
                result.success(), result.durationMs());
            return result.success() ? "success" : "failed";
        }

        log.info("[闭环1] WebSocket recovery dispatched (self-healing disabled): type={}", eventType);
        return "disabled";
    }

    private String dispatchDegradation(CrossLoopEvent event) {
        // P31: 真正执行降级切换 (AppModeUtil 为静态工具类，直接调用静态方法)
        String eventType = event.getEventType();
        if (eventType.contains("enter_degraded")) {
            AppModeUtil.setDegraded("CrossLoopEvent: " + eventType);
            log.info("[P31] Degradation executed: mode=degraded");
        } else if (eventType.contains("exit_degraded")) {
            AppModeUtil.clearDegraded();
            log.info("[P31] Degradation executed: mode=normal");
        }
        return "success";
    }

    private String dispatchSecurity(CrossLoopEvent event) {
        Map<String, Object> payload = event.getPayload();
        String brainId = payload != null ? (String) payload.get("brainId") : null;
        String violationType = payload != null ? (String) payload.get("violationType") : event.getEventType();

        if (brainId != null && sandboxViolationTracker != null) {
            sandboxViolationTracker.recordViolation(brainId, violationType);
            log.info("[P31] Security violation recorded: brain={}, type={}", brainId, violationType);
        }

        if (brainId != null && brainBoundaryEnforcer != null) {
            try {
                brainBoundaryEnforcer.checkAction(brainId, "SECURITY_EVENT:" + violationType);
                log.info("[P31] Boundary enforcement triggered for brain={}", brainId);
            } catch (Exception e) {
                log.warn("[P31] Boundary enforcement failed: {}", e.getMessage());
            }
        }

        log.warn("[P31] Security event dispatched: type={}, brain={}", event.getEventType(), brainId);
        return brainId != null ? "success" : "escalated";
    }

    private String dispatchReceipt(CrossLoopEvent event) {
        Map<String, Object> payload = event.getPayload();
        String employeeCode = payload != null ? (String) payload.get("employeeCode") : null;
        Double qualityScore = payload != null ? (Double) payload.get("qualityScore") : null;

        if (employeeCode != null && performanceStatsService != null) {
            double delta = (qualityScore != null && qualityScore < 0.5) ? -0.2 : 0.1;
            performanceStatsService.adjustWeight(employeeCode, delta);
            log.info("[P31] Receipt weight adjusted: employee={}, delta={}, quality={}",
                employeeCode, delta, qualityScore);
        }

        // P28-B: 审核结果经验沉淀
        if (employeeCode != null && qualityScore != null && knowledgeConsumptionFeedback != null) {
            String knowledgeKey = "receipt-experience:" + employeeCode;
            boolean helpful = qualityScore > 0.5;
            knowledgeConsumptionFeedback.recordFeedback(
                knowledgeKey, helpful, "execution-receipt-review", "P31-governance");
            log.info("[P28-B] Receipt experience precipitated: employee={}, quality={}", employeeCode, qualityScore);
        }

        log.info("[P31] Receipt event dispatched: type={}", event.getEventType());
        return "success";
    }

    private String dispatchEconomic(CrossLoopEvent event) {
        Map<String, Object> payload = event.getPayload();
        String employeeId = payload != null ? (String) payload.get("employeeId") : null;
        Integer amountCents = payload != null ? (Integer) payload.get("amountCents") : null;

        if (employeeId != null && amountCents != null && ledgerService != null) {
            String sourceType = (String) payload.getOrDefault("sourceType", "GOVERNANCE");
            String sourceId = (String) payload.getOrDefault("sourceId", event.getEventType());
            ledgerService.recordReward(employeeId, amountCents, "P31-governance:" + sourceType);
            log.info("[P31] Economic event recorded: employee={}, amount={}", employeeId, amountCents);
        } else {
            log.info("[P31] Economic event (no action): type={}", event.getEventType());
        }
        return "success";
    }

    private String dispatchKnowledge(CrossLoopEvent event) {
        Map<String, Object> payload = event.getPayload();
        String knowledgeKey = payload != null ? (String) payload.get("knowledgeKey") : null;
        Boolean helpful = payload != null ? (Boolean) payload.get("helpful") : null;

        if (knowledgeKey != null && helpful != null && knowledgeConsumptionFeedback != null) {
            knowledgeConsumptionFeedback.recordFeedback(knowledgeKey, helpful,
                "P31-governance:" + event.getEventType(), "governance-orchestrator");
            log.info("[P31] Knowledge feedback recorded: key={}, helpful={}", knowledgeKey, helpful);
        } else {
            log.info("[P31] Knowledge event (no action): type={}", event.getEventType());
        }
        return "success";
    }

    private String dispatchPersonality(CrossLoopEvent event) {
        Map<String, Object> payload = event.getPayload();
        String brainId = payload != null ? (String) payload.get("brainId") : null;
        Double satisfactionDelta = payload != null ? (Double) payload.get("satisfactionDelta") : null;

        if (brainId != null && brainBoundaryEnforcer != null) {
            try {
                String action = satisfactionDelta != null && satisfactionDelta < -0.2
                    ? "PERSONALITY_ROLLBACK" : "PERSONALITY_EVENT:" + event.getEventType();
                BrainBoundaryEnforcer.BoundaryCheckResult result =
                    brainBoundaryEnforcer.checkAction(brainId, action);
                if (result.isViolation()) {
                    sandboxViolationTracker.recordViolation(brainId, "PERSONALITY_BOUNDARY:" + action);
                    log.info("[P31] Personality boundary violation: brain={}, action={}", brainId, action);
                } else {
                    log.info("[P31] Personality boundary checked: brain={}, allowed={}", brainId, result.isAllowed());
                }
            } catch (Exception e) {
                log.warn("[P31] Personality dispatch failed: {}", e.getMessage());
            }
        } else {
            log.info("[P31] Personality event (no action): type={}", event.getEventType());
        }
        return "success";
    }

    @Override
    public GovernanceStatus getStatus() {
        if (!properties.isEnabled()) {
            return GovernanceStatus.disabled();
        }

        int pending = pendingEvents.size();
        long lastMsAgo = lastOrchestrationTime != null
            ? Instant.now().toEpochMilli() - lastOrchestrationTime.toEpochMilli()
            : -1;

        if (pending > 0) {
            return GovernanceStatus.active(pending, totalProcessedEvents);
        }
        return GovernanceStatus.idle(totalProcessedEvents);
    }

    // ========== P5-1: DBS 改善周机制 ==========

    private static final List<String> KAIZEN_DEPARTMENTS = List.of(
        "tech", "hr", "finance", "sales", "admin", "cs", "legal", "ops", "core"
    );

    /**
     * DBS 改善周：每7天自动审视9个部门大脑运营数据，识别改善机会，生成 KAIZEN_EVENT 信号。
     * 实现进化双驱动模式：被动（异常信号）+ 主动（改善周）。
     */
    @Scheduled(fixedRate = 7 * 24 * 60 * 60 * 1000, initialDelay = 24 * 60 * 60 * 1000)
    public void kaizenWeeklyReview() {
        if (!properties.isEnabled()) return;

        log.info("[P5-1/DBS Kaizen] 改善周开始：审视9个部门大脑运营数据");

        for (String dept : KAIZEN_DEPARTMENTS) {
            try {
                String kaizenContent = generateKaizenInsight(dept);
                if (kaizenContent != null && !kaizenContent.isBlank()) {
                    EvolutionSignal kaizenSignal = new EvolutionSignal(
                        EvolutionSignal.SignalType.KAIZEN_EVENT, kaizenContent);
                    kaizenSignal.setSource("KaizenWeeklyReview:" + dept);
                    kaizenSignal.setBrainDomain(dept);
                    kaizenSignal.setConfidence(0.6);
                    kaizenSignal.addTag("KAIZEN_WEEKLY");
                    kaizenSignal.addMetadata("department", dept);
                    kaizenSignal.addMetadata("reviewType", "weekly");

                    eventPublisher.publishEvent(kaizenSignal);
                    log.info("[P5-1/DBS Kaizen] 改善信号发布: dept={}, insight={}",
                        dept, kaizenContent.length() > 80 ? kaizenContent.substring(0, 80) + "..." : kaizenContent);
                }
            } catch (Exception e) {
                log.warn("[P5-1/DBS Kaizen] 改善周审视失败: dept={}, error={}", dept, e.getMessage());
            }
        }

        log.info("[P5-1/DBS Kaizen] 改善周完成");
    }

    /**
     * 基于部门运营数据生成改善洞察。
     * 当前实现为规则驱动，后续可由 LLM 增强。
     */
    private String generateKaizenInsight(String department) {
        List<String> insights = new ArrayList<>();

        // 检查绩效数据中的低权重员工
        if (performanceStatsService != null) {
            try {
                // 使用 getStatsBatch 获取部门员工绩效
                var stats = performanceStatsService.getStatsBatch(List.of(department));
                long lowPerformerCount = stats.values().stream()
                    .filter(s -> s.normalizedScore() < 0.5)
                    .count();
                if (lowPerformerCount > 0) {
                    insights.add(String.format("%s部门有%d名低绩效员工需关注", department, lowPerformerCount));
                }
            } catch (Exception e) {
                log.debug("Kaizen: failed to get performance stats for {}: {}", department, e.getMessage());
            }
        }

        if (insights.isEmpty()) {
            insights.add(String.format("%s部门运营正常，建议持续优化标准作业流程", department));
        }

        return String.join("；", insights);
    }
}
