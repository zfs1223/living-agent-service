package com.livingagent.core.employee;

import com.livingagent.core.autonomy.ExecutionCapability;

import java.util.List;

/**
 * 员工能力清单（64-A-2）
 * 描述固定数字员工可执行的能力、可用工具及健康状态。
 * 供行动发现服务/LLM调度器使用，替代关键词匹配。
 */
public record EmployeeCapabilityProfile(
    String employeeCode,
    String department,
    List<ToolCapability> availableTools,
    List<String> supportedTaskTypes,
    List<ExecutionCapability> capabilities,
    List<String> knowledgeDomains,
    int currentLoad,
    int maxLoad
) {
    public record ToolCapability(
        String toolName,
        List<String> capabilities,
        boolean healthy
    ) {}

    public boolean hasCapability(ExecutionCapability cap) {
        return capabilities != null && capabilities.contains(cap);
    }

    public boolean hasTool(String toolName) {
        return availableTools != null && availableTools.stream()
            .anyMatch(tc -> tc.toolName().equals(toolName));
    }

    public boolean isOverloaded() {
        return currentLoad >= maxLoad;
    }

    public static EmployeeCapabilityProfile empty(String employeeCode) {
        return new EmployeeCapabilityProfile(
            employeeCode, null, List.of(), List.of(), List.of(), List.of(), 0, 1
        );
    }
}
