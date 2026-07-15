package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.tool.backend.BackendRegistry;
import com.livingagent.core.tool.backend.ExternalToolBackend;

import java.util.ArrayList;
import java.util.List;

/**
 * 行动准备度检查器（64-B-2）
 * 在任务执行前检查：工具权限、工具健康、前置条件、输入完整性。
 */
public class ActionReadinessChecker {

    private final BackendRegistry backendRegistry;

    public ActionReadinessChecker(BackendRegistry backendRegistry) {
        this.backendRegistry = backendRegistry;
    }

    public enum Readiness {
        READY,
        BLOCKED,
        DEGRADED
    }

    public record ReadinessResult(
        Readiness readiness,
        List<String> blockers,
        List<String> warnings
    ) {
        public boolean isReady() { return readiness == Readiness.READY; }
        public boolean isBlocked() { return readiness == Readiness.BLOCKED; }
    }

    /**
     * 执行前检查清单：
     * 1. 工具健康检查（ExternalToolBackend.healthCheck）
     * 2. 前置条件检查（如代码审查需要 MR）
     * 3. 输入完整性（任务描述非空、有交付物定义）
     */
    public ReadinessResult check(EmployeeWorkAssignment assignment, List<String> tools) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 工具健康检查
        for (String toolName : tools) {
            ExternalToolBackend.HealthStatus health = backendRegistry.healthCheck(toolName);
            if (!health.healthy()) {
                String installHint = backendRegistry.getBackend(toolName)
                    .map(ExternalToolBackend::installHint)
                    .orElse("无安装指引");
                blockers.add("工具 " + toolName + " 不可用: " + health.detail() + " | " + installHint);
            } else if (health.latencyMs() > 3000) {
                warnings.add("工具 " + toolName + " 响应较慢: " + health.latencyMs() + "ms");
            }
        }

        // 前置条件检查
        checkPrerequisites(assignment, blockers, warnings);

        // 输入完整性检查
        checkInputCompleteness(assignment, blockers, warnings);

        Readiness readiness;
        if (!blockers.isEmpty()) {
            readiness = Readiness.BLOCKED;
        } else if (!warnings.isEmpty()) {
            readiness = Readiness.DEGRADED;
        } else {
            readiness = Readiness.READY;
        }

        return new ReadinessResult(readiness, blockers, warnings);
    }

    private void checkPrerequisites(EmployeeWorkAssignment assignment,
                                     List<String> blockers, List<String> warnings) {
        if (assignment.context() == null) return;

        String taskType = (String) assignment.context().get("taskType");
        if (taskType == null) return;

        switch (taskType) {
            case "code_review" -> {
                if (!assignment.context().containsKey("mergeRequestId") &&
                    !assignment.context().containsKey("repositoryUrl")) {
                    blockers.add("代码审查任务需要 mergeRequestId 或 repositoryUrl");
                }
            }
            case "cicd_pipeline" -> {
                if (!assignment.context().containsKey("repositoryUrl")) {
                    blockers.add("CI/CD 流水线任务需要 repositoryUrl");
                }
            }
            case "finance_workflow" -> {
                if (!assignment.context().containsKey("invoiceId") &&
                    !assignment.context().containsKey("receiptId")) {
                    warnings.add("财务流程建议提供 invoiceId 或 receiptId");
                }
            }
            case "legal_review" -> {
                if (!assignment.context().containsKey("documentPath")) {
                    blockers.add("法务审查任务需要 documentPath");
                }
            }
        }
    }

    private void checkInputCompleteness(EmployeeWorkAssignment assignment,
                                         List<String> blockers, List<String> warnings) {
        if (assignment.objective() == null || assignment.objective().isBlank()) {
            blockers.add("任务目标(objective)为空");
        }
        if (assignment.allowedTools() == null || assignment.allowedTools().isEmpty()) {
            warnings.add("未分配任何工具，将仅使用 LLM 生成");
        }
        if (assignment.expectedDeliverables() == null || assignment.expectedDeliverables().isEmpty()) {
            warnings.add("未定义期望交付物，输出验证将跳过交付物检查");
        }
    }
}
