package com.livingagent.core.autonomy.context;

import java.util.List;
import java.util.Map;

public record DecisionContext(
    RequestContext request,
    UserContext user,
    List<BrainContext> brains,
    List<EmployeeContext> employees,
    List<ToolContext> tools,
    List<KnowledgeContext> knowledge,
    ProjectContext project,
    ApprovalContext approval,
    ConstraintsContext constraints
) {
    public static DecisionContext empty() {
        return new DecisionContext(
            null, null, List.of(), List.of(), List.of(), List.of(), null, null, null
        );
    }

    public record RequestContext(
        String message,
        String sessionId,
        String requestId,
        String entrypoint,
        Map<String, Object> metadata
    ) {}

    public record UserContext(
        String userId,
        String userName,
        String department,
        List<String> roles,
        String accessLevel,
        boolean isFounder,
        Map<String, Object> preferences
    ) {}

    public record BrainContext(
        String department,
        String brainId,
        String state,
        String modelId,
        Map<String, Object> capabilities
    ) {}

    public record EmployeeContext(
        String code,
        String name,
        String department,
        String title,
        List<String> roles,
        List<String> capabilities,
        List<String> tools,
        double currentLoad,
        double recentPerformance,
        boolean active,
        String neuronId
    ) {}

    public record ToolContext(
        String name,
        String description,
        String riskLevel,
        boolean requiresApproval,
        Map<String, Object> schema
    ) {}

    public record KnowledgeContext(
        String key,
        String title,
        String summary,
        String source,
        String layer
    ) {}

    public record ProjectContext(
        String projectId,
        String projectName,
        String phase,
        List<String> activeTasks,
        List<String> milestones
    ) {}

    public record ApprovalContext(
        boolean required,
        String approvalType,
        List<String> rules,
        String currentStatus
    ) {}

    public record ConstraintsContext(
        String tenantId,
        String dataSensitivity,
        List<String> allowedActions,
        List<String> forbiddenActions
    ) {}

    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        
        if (request != null) {
            sb.append("## 请求上下文\n");
            sb.append("- 消息: ").append(request.message()).append("\n");
            sb.append("- 入口: ").append(request.entrypoint()).append("\n");
        }
        
        if (user != null) {
            sb.append("\n## 用户上下文\n");
            sb.append("- 用户: ").append(user.userName()).append(" (").append(user.userId()).append(")\n");
            sb.append("- 部门: ").append(user.department()).append("\n");
            sb.append("- 角色: ").append(String.join(", ", user.roles())).append("\n");
            sb.append("- 权限级别: ").append(user.accessLevel()).append("\n");
        }
        
        if (!brains.isEmpty()) {
            sb.append("\n## 可用大脑\n");
            for (BrainContext brain : brains) {
                sb.append("- ").append(brain.department()).append(": ").append(brain.brainId())
                  .append(" (").append(brain.state()).append(")\n");
            }
        }
        
        if (!employees.isEmpty()) {
            sb.append("\n## 可用员工\n");
            for (EmployeeContext emp : employees) {
                sb.append("- ").append(emp.code()).append(": ").append(emp.name())
                  .append(" (").append(emp.title()).append(", ").append(emp.department()).append(")\n");
                sb.append("  能力: ").append(String.join(", ", emp.capabilities())).append("\n");
                sb.append("  工具: ").append(String.join(", ", emp.tools())).append("\n");
                sb.append("  负载: ").append(String.format("%.0f%%", emp.currentLoad() * 100))
                  .append(", 绩效: ").append(String.format("%.1f", emp.recentPerformance())).append("\n");
            }
        }
        
        if (!tools.isEmpty()) {
            sb.append("\n## 可用工具\n");
            for (ToolContext tool : tools) {
                sb.append("- ").append(tool.name()).append(": ").append(tool.description())
                  .append(" (风险: ").append(tool.riskLevel()).append(")\n");
            }
        }
        
        if (!knowledge.isEmpty()) {
            sb.append("\n## 相关知识\n");
            for (KnowledgeContext k : knowledge) {
                sb.append("- ").append(k.title()).append(": ").append(k.summary()).append("\n");
            }
        }
        
        if (project != null) {
            sb.append("\n## 项目上下文\n");
            sb.append("- 项目: ").append(project.projectName()).append(" (").append(project.phase()).append(")\n");
            if (!project.activeTasks().isEmpty()) {
                sb.append("- 活跃任务: ").append(String.join(", ", project.activeTasks())).append("\n");
            }
        }
        
        if (approval != null && approval.required()) {
            sb.append("\n## 审批要求\n");
            sb.append("- 审批类型: ").append(approval.approvalType()).append("\n");
            sb.append("- 当前状态: ").append(approval.currentStatus()).append("\n");
        }
        
        if (constraints != null) {
            sb.append("\n## 约束条件\n");
            sb.append("- 租户: ").append(constraints.tenantId()).append("\n");
            sb.append("- 数据敏感度: ").append(constraints.dataSensitivity()).append("\n");
            if (!constraints.forbiddenActions().isEmpty()) {
                sb.append("- 禁止操作: ").append(String.join(", ", constraints.forbiddenActions())).append("\n");
            }
        }
        
        return sb.toString();
    }
}
