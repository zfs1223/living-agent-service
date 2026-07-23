package com.livingagent.core.evolution.conflict.impl;

import com.livingagent.core.evolution.conflict.ConflictResolutionService;
import com.livingagent.core.evolution.orchestrator.CrossLoopEvent;
import com.livingagent.core.evolution.orchestrator.CrossLoopEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * P13: 冲突调和与 5Ps 对齐服务默认实现。
 */
@Service
public class DefaultConflictResolutionService implements ConflictResolutionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultConflictResolutionService.class);

    /** 权限隔离约束：以下大脑不允许被跨部门直接访问 */
    private static final Set<String> ISOLATED_BRAINS = Set.of("MainBrain");

    private final CrossLoopEventBus eventBus;

    public DefaultConflictResolutionService(CrossLoopEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public CircuitBreakerDecision evaluateCircuitBreaker(String signalCategory, double confidence, String content) {
        CircuitBreakerDecision.DecisionType type;
        boolean requiresHuman = false;
        String reason;

        switch (signalCategory.toUpperCase()) {
            case "REPAIR":
                type = CircuitBreakerDecision.DecisionType.AUTO_EXECUTE;
                reason = "REPAIR信号：自动熔断执行（置信度=" + String.format("%.2f", confidence) + "）";
                break;
            case "OPTIMIZE":
                type = CircuitBreakerDecision.DecisionType.AUTO_WITH_LOG;
                reason = "OPTIMIZE信号：自动执行+日志记录";
                break;
            case "INNOVATE":
                if (confidence > 0.8) {
                    type = CircuitBreakerDecision.DecisionType.AUTO_WITH_LOG;
                    reason = "INNOVATE信号高置信度：自动执行";
                } else {
                    type = CircuitBreakerDecision.DecisionType.HUMAN_APPROVAL;
                    requiresHuman = true;
                    reason = "INNOVATE信号低置信度：需人工审批兜底";
                }
                break;
            case "VOC":
                type = CircuitBreakerDecision.DecisionType.AUTO_PASS;
                reason = "VOC信号：低风险客户反馈自动放行";
                break;
            default:
                type = CircuitBreakerDecision.DecisionType.REJECTED;
                reason = "未知信号分类：" + signalCategory;
        }

        CircuitBreakerDecision decision = new CircuitBreakerDecision(
            java.util.UUID.randomUUID().toString(), signalCategory, confidence,
            type, reason, requiresHuman, Instant.now());

        log.info("[P13/CB] 熔断决策: category={}, type={}, human={}, confidence={:.2f}",
            signalCategory, type, requiresHuman, confidence);

        return decision;
    }

    @Override
    public CollaborationArbitration arbitrateCollaboration(String requestingBrain, String targetBrain, String operation) {
        boolean approved = true;
        String condition = "无限制";

        // 权限隔离检查
        if (ISOLATED_BRAINS.contains(targetBrain)) {
            approved = false;
            condition = "目标大脑" + targetBrain + "受权限隔离保护，禁止直接跨部门访问";
        }

        // MainBrain 仲裁条件
        if (approved && !requestingBrain.equals(targetBrain)) {
            condition = "需经MainBrain仲裁确认，权限隔离不妥协";
        }

        CollaborationArbitration result = new CollaborationArbitration(
            java.util.UUID.randomUUID().toString(), requestingBrain, targetBrain,
            operation, approved, condition, Instant.now());

        log.info("[P13/Collab] 协作仲裁: {}→{} op={} approved={} condition={}",
            requestingBrain, targetBrain, operation, approved, condition);

        return result;
    }

    @Override
    public ImprovementMode determineImprovementMode(String employeeOrigin, String improvementType) {
        return switch (employeeOrigin.toLowerCase()) {
            case "fixed" -> ImprovementMode.AUTO;        // 固定数字员工：自动模式
            case "human" -> ImprovementMode.COLLABORATIVE; // 人类员工：协作模式
            case "personal" -> {
                // 个人助手：标准作业类自动，创新型需人工确认
                yield "standard_work".equals(improvementType)
                    ? ImprovementMode.AUTO
                    : ImprovementMode.HYBRID;
            }
            default -> ImprovementMode.HYBRID;
        };
    }

    @Override
    public FivePsAlignment assessFivePsAlignment() {
        // P13-4: 5Ps 对齐度评估
        // 当前实现为规则驱动评估，后续可由 LLM 增强
        double purposeScore = 0.8;     // Purpose: 系统存在意义 — 已有主脑六步决策法
        double philosophyScore = 0.9;  // Philosophy: DBS方法论 — 12个技能已定义
        double processScore = 0.85;    // Process: 执行流程 — 闭环1-66覆盖
        double peopleScore = 0.7;      // People: 人才体系 — 四级能力等级已实现，人类员工路径待完善
        double planScore = 0.6;        // Plan: 战略部署 — Hoshin Kanri技能已定义，代码实现待完善

        List<String> gaps = new ArrayList<>();
        if (peopleScore < 0.8) gaps.add("People: 人类员工职业发展路径代码实现待完善");
        if (planScore < 0.8) gaps.add("Plan: Hoshin Kanri 四级联动的代码实现待完善");

        double overall = (purposeScore + philosophyScore + processScore + peopleScore + planScore) / 5;

        log.info("[P13/5Ps] 5Ps对齐评估: overall={:.2f}, gaps={}", overall, gaps.size());

        return new FivePsAlignment(
            purposeScore, philosophyScore, processScore, peopleScore, planScore,
            overall, gaps, Instant.now());
    }
}
