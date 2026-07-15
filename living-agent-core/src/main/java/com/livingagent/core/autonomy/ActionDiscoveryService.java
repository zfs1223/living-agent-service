package com.livingagent.core.autonomy;

import com.livingagent.core.employee.EmployeeCapabilityProfile;

import java.util.List;
import java.util.Optional;

/**
 * 行动发现服务（64-A-2）
 * 让员工在执行前知道自己能做什么，替代关键词猜测。
 * 基于员工编制定义 + 工具注册表 + 工具健康状态，动态生成能力清单。
 */
public interface ActionDiscoveryService {

    /** 发现员工可用行动 */
    EmployeeCapabilityProfile discoverCapabilities(String employeeCode);

    /** 检查工具是否可用 */
    ToolHealthStatus checkToolHealth(String toolName);

    /** 匹配最佳工具 */
    Optional<ToolMatch> findBestTool(String employeeCode, String taskDescription, ExecutionCapability required);

    /** 工具健康状态 */
    record ToolHealthStatus(
        String toolName,
        boolean healthy,
        long latencyMs,
        String detail
    ) {
        public static ToolHealthStatus unknown(String toolName) {
            return new ToolHealthStatus(toolName, false, -1, "未注册");
        }
    }

    /** 工具匹配结果 */
    record ToolMatch(
        String toolName,
        double relevanceScore,
        String reason,
        List<String> fallbackTools
    ) {}
}
