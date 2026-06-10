package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DefaultFinalResponseCoordinator implements FinalResponseCoordinator {

    private static final Logger log = LoggerFactory.getLogger(DefaultFinalResponseCoordinator.class);

    @Override
    public FinalResponseStrategy determineStrategy(
            String requestId,
            String department,
            DialogueDecision decision,
            BrainRoutingDecision routingDecision,
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentExecutionResult executionResult) {

        // P0-2.4: 检查是否需要人工接管
        if (isNeedsHumanIntervention(executionResult)) {
            log.info("FinalResponseCoordinator: needsHumanIntervention=true, strategy=ESCALATE_TO_HUMAN requestId={}", requestId);
            return FinalResponseStrategy.ESCALATE_TO_HUMAN;
        }

        if (decision.requiresTaskExecution() || mainBrainTaskPlan != null) {
            if (executionResult != null && "WAITING_RECEIPT".equals(executionResult.status())) {
                log.info("FinalResponseCoordinator: execution waiting for receipts, strategy=WAIT_FOR_RECEIPTS requestId={}", requestId);
                return FinalResponseStrategy.WAIT_FOR_RECEIPTS;
            }
            log.info("FinalResponseCoordinator: execution task detected, strategy=MAIN_BRAIN_COMPOSE requestId={}", requestId);
            return FinalResponseStrategy.MAIN_BRAIN_COMPOSE;
        }

        if (decision.kind() == DialogueDecision.MessageKind.CROSS_DEPARTMENT) {
            log.info("FinalResponseCoordinator: cross-department task, strategy=MAIN_BRAIN_COMPOSE requestId={}", requestId);
            return FinalResponseStrategy.MAIN_BRAIN_COMPOSE;
        }

        log.debug("FinalResponseCoordinator: consultation message, strategy=DEPARTMENT_BRAIN_DIRECT requestId={}", requestId);
        return FinalResponseStrategy.DEPARTMENT_BRAIN_DIRECT;
    }

    /**
     * 从 DepartmentExecutionResult 的 metadata 中提取 needsHumanIntervention 标记
     */
    private boolean isNeedsHumanIntervention(DepartmentExecutionResult executionResult) {
        if (executionResult == null) {
            return false;
        }
        Map<String, Object> metadata = executionResult.metadata();
        if (metadata == null) {
            return false;
        }
        Object flag = metadata.get("needsHumanIntervention");
        return Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag));
    }
}
