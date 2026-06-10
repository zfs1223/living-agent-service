package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.*;

import java.util.stream.Collectors;

public class DefaultMainBrainResponseComposer implements MainBrainResponseComposer {

    @Override
    public String composeUserResponse(
            String requestId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            java.util.List<EmployeeWorkAssignment> employeeAssignments,
            String brainRawResponse,
            DepartmentExecutionResult executionResult) {

        if (brainRawResponse != null && !brainRawResponse.isBlank()) {
            StringBuilder enriched = new StringBuilder();
            enriched.append(brainRawResponse);

            if (mainBrainTaskPlan != null && employeeAssignments != null && !employeeAssignments.isEmpty()) {
                enriched.append("\n\n---\n");
                enriched.append("**任务信息**：").append(mainBrainTaskPlan.goal()).append("\n");
                enriched.append("**类型**：").append(mainBrainTaskPlan.taskType()).append("\n");

                enriched.append("**执行团队**：\n");
                for (EmployeeWorkAssignment assignment : employeeAssignments) {
                    enriched.append("- ").append(assignment.employeeName())
                        .append("（").append(assignment.role()).append("）[")
                        .append(assignment.employeeCode()).append("]\n");
                }

                if (mainBrainTaskPlan.deliverables() != null && !mainBrainTaskPlan.deliverables().isEmpty()) {
                    enriched.append("\n**交付物**：\n");
                    for (String d : mainBrainTaskPlan.deliverables()) {
                        enriched.append("- ").append(d).append("\n");
                    }
                }
                if (mainBrainTaskPlan.acceptanceCriteria() != null && !mainBrainTaskPlan.acceptanceCriteria().isEmpty()) {
                    enriched.append("\n**验收标准**：\n");
                    for (String c : mainBrainTaskPlan.acceptanceCriteria()) {
                        enriched.append("- ").append(c).append("\n");
                    }
                }

                if (executionResult != null) {
                    if ("WAITING_RECEIPT".equals(executionResult.status())) {
                        enriched.append("\n⏳ 已派发任务至 ").append(executionResult.dispatchedAssignments().size())
                            .append(" 位员工，等待执行回执...");
                    } else if ("NO_ASSIGNMENT".equals(executionResult.status())) {
                        enriched.append("\n⚠️ 暂无员工分派，请确认部门是否有可用执行人员");
                    }
                }
            }
            return enriched.toString();
        }
        return "任务已接收，正在处理中...";
    }

    @Override
    public String composeProgressResponse(
            String requestId,
            String department,
            MainBrainTaskPlan mainBrainTaskPlan,
            java.util.List<EmployeeWorkAssignment> employeeAssignments,
            DepartmentExecutionResult executionResult) {

        if (executionResult == null || executionResult.dispatchedAssignments().isEmpty()) {
            return "暂无执行进度。";
        }

        StringBuilder progress = new StringBuilder();
        progress.append("**执行进度**（").append(executionResult.status()).append("）\n\n");
        for (EmployeeExecutionDispatch dispatch : executionResult.dispatchedAssignments()) {
            String icon = switch (dispatch.status()) {
                case "DISPATCHED" -> "🔄";
                case "COMPLETED" -> "✅";
                case "FAILED" -> "❌";
                case "WAITING_RECEIPT" -> "⏳";
                default -> "❓";
            };
            progress.append(icon).append(" ")
                .append(dispatch.employeeCode()).append(": ")
                .append(dispatch.status()).append("\n");
        }
        return progress.toString();
    }
}
