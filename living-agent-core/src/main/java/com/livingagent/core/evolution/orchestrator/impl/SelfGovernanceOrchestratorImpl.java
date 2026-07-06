package com.livingagent.core.evolution.orchestrator.impl;

import com.livingagent.core.diagnosis.AppModeUtil;
import com.livingagent.core.diagnosis.HealthIssue;
import com.livingagent.core.evolution.orchestrator.*;
import com.livingagent.core.database.repository.InterventionDecisionRepository;
import com.livingagent.core.database.entity.InterventionDecisionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
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
    private final InterventionDecisionRepository auditRepository; // P31: 审计持久化

    private final ConcurrentLinkedDeque<CrossLoopEvent> pendingEvents = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<GovernanceReport> auditTrail = new ConcurrentLinkedDeque<>(); // P31: 审计轨迹
    private volatile int totalProcessedEvents = 0;
    private volatile Instant lastOrchestrationTime = null;

    public SelfGovernanceOrchestratorImpl(
            SelfGovernanceProperties properties,
            LoopPriorityArbiter arbiter,
            SelfHealingOrchestrator selfHealingOrchestrator,
            InterventionDecisionRepository auditRepository) {
        this.properties = properties;
        this.arbiter = arbiter;
        this.selfHealingOrchestrator = selfHealingOrchestrator;
        this.auditRepository = auditRepository;
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

        try {
            return switch (loopId) {
                case 24 -> dispatchSelfHealing(event);
                case 27 -> dispatchDegradation(event);
                case 30 -> dispatchSecurity(event);
                case 28 -> dispatchReceipt(event);
                case 25, 26, 29 -> dispatchLogOnly(event);
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
        log.warn("[P31-A] Security event (high priority): type={}", event.getEventType());
        return "escalated";
    }

    private String dispatchReceipt(CrossLoopEvent event) {
        log.info("[P31-A] Receipt event: type={}", event.getEventType());
        return "success";
    }

    private String dispatchLogOnly(CrossLoopEvent event) {
        log.info("[P31-A] Loop {} event: type={}", event.getSourceLoop(), event.getEventType());
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
}
