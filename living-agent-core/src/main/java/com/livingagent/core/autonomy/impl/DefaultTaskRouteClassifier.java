package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.DialogueDecision;
import com.livingagent.core.autonomy.TaskRouteClassifier;
import com.livingagent.core.autonomy.TaskRouteResult;
import com.livingagent.core.brain.BrainRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认任务路由分类器实现。
 *
 * <p>路由规则：
 * <ol>
 *   <li>CROSS_DEPARTMENT 类型 → 走主脑拆解</li>
 *   <li>非 TASK/PROJECT/APPROVAL 类型（CHAT/CONSULTATION 等）→ 单部门直达</li>
 *   <li>有协作部门 → 走主脑拆解</li>
 *   <li>主部门与用户所在部门一致且无协作部门 → 单部门直达</li>
 *   <li>主部门与用户所在部门不一致 → 走主脑拆解（可能需要跨部门）</li>
 *   <li>无法判断 → 走主脑拆解（Fallback 保护）</li>
 * </ol>
 */
public class DefaultTaskRouteClassifier implements TaskRouteClassifier {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskRouteClassifier.class);

    private final BrainRegistry brainRegistry;

    public DefaultTaskRouteClassifier(BrainRegistry brainRegistry) {
        this.brainRegistry = brainRegistry;
    }

    @Override
    public TaskRouteResult classify(DialogueDecision decision, String department) {
        if (decision == null) {
            return TaskRouteResult.crossDepartment("Decision is null, fallback to main brain", null);
        }

        // 1. 明确跨部门 → 主脑拆解
        if (decision.kind() == DialogueDecision.MessageKind.CROSS_DEPARTMENT) {
            log.debug("Route=CROSS_DEPARTMENT: explicit cross-department kind");
            return TaskRouteResult.crossDepartment("Explicit cross-department request", decision);
        }

        // 2. 非任务类型 → 单部门直达（闲聊/咨询等不需要主脑规划）
        if (decision.kind() != DialogueDecision.MessageKind.TASK
            && decision.kind() != DialogueDecision.MessageKind.PROJECT
            && decision.kind() != DialogueDecision.MessageKind.APPROVAL) {
            String dept = resolveDepartment(decision, department);
            log.debug("Route=SINGLE_DEPARTMENT: non-task kind={}, dept={}", decision.kind(), dept);
            return TaskRouteResult.singleDepartment(dept, "Non-task type, direct to department brain", decision);
        }

        // 3. 需要澄清 → 先澄清
        if (decision.requiresClarification()) {
            log.debug("Route=CLARIFICATION_NEEDED: requiresClarification=true");
            return TaskRouteResult.clarificationNeeded("Decision requires clarification before routing", decision);
        }

        // 4. 有协作部门 → 主脑拆解
        if (decision.supportingDepartments() != null && !decision.supportingDepartments().isEmpty()) {
            log.debug("Route=CROSS_DEPARTMENT: has supporting departments={}", decision.supportingDepartments());
            return TaskRouteResult.crossDepartment(
                "Task requires " + decision.supportingDepartments().size() + " supporting departments", decision);
        }

        // 5. 主部门明确且与用户部门一致 → 单部门直达
        String primaryDept = decision.primaryDepartment();
        if (primaryDept != null && !primaryDept.isBlank()) {
            if (department != null && department.equalsIgnoreCase(primaryDept)) {
                // 验证目标部门有对应大脑（getByDepartment 返回 Optional，需用 isPresent() 检查）
                if (brainRegistry != null && brainRegistry.getByDepartment(primaryDept).isPresent()) {
                    log.debug("Route=SINGLE_DEPARTMENT: primaryDept={} matches user dept={}", primaryDept, department);
                    return TaskRouteResult.singleDepartment(primaryDept,
                        "Single department task, direct to " + primaryDept + " brain", decision);
                }
            }
            // 主部门与用户部门不一致 → 可能需要跨部门协调
            if (department != null && !department.equalsIgnoreCase(primaryDept)) {
                log.debug("Route=CROSS_DEPARTMENT: primaryDept={} differs from user dept={}", primaryDept, department);
                return TaskRouteResult.crossDepartment(
                    "Primary department (" + primaryDept + ") differs from user department (" + department + ")", decision);
            }
        }

        // 6. 用户在部门对话中且无协作部门 → 根据 complexity 判断
        // P1-5: complexity<=2 且无协作部门 → 单部门直达，否则走主脑拆解
        if (department != null && !department.isBlank()) {
            if (brainRegistry != null && brainRegistry.getByDepartment(department).isPresent()) {
                // 简单任务（complexity <= 2）直接走部门大脑
                if (decision.complexity() <= 2) {
                    log.debug("Route=SINGLE_DEPARTMENT: user in dept={}, complexity={} <= 2, no supporting depts",
                        department, decision.complexity());
                    return TaskRouteResult.singleDepartment(department,
                        "Simple task (complexity=" + decision.complexity() + "), direct to department brain", decision);
                }
                // 复杂任务（complexity > 2）走主脑拆解
                log.debug("Route=CROSS_DEPARTMENT: user in dept={}, complexity={} > 2, needs main brain planning",
                    department, decision.complexity());
                return TaskRouteResult.crossDepartment(
                    "Complex task (complexity=" + decision.complexity() + "), needs main brain planning", decision);
            }
        }

        // 7. 无法判断 → Fallback 走主脑
        log.debug("Route=CROSS_DEPARTMENT: fallback, cannot determine single department routing");
        return TaskRouteResult.crossDepartment("Cannot determine single department routing, fallback to main brain", decision);
    }

    private String resolveDepartment(DialogueDecision decision, String fallbackDepartment) {
        if (decision.primaryDepartment() != null && !decision.primaryDepartment().isBlank()) {
            return decision.primaryDepartment();
        }
        return fallbackDepartment;
    }
}
