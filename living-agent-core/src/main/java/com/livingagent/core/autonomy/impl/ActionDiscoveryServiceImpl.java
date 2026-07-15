package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.ActionDiscoveryService;
import com.livingagent.core.autonomy.ExecutionCapability;
import com.livingagent.core.employee.EmployeeCapabilityProfile;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.ToolSchema;
import com.livingagent.core.tool.backend.BackendRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 行动发现服务实现（64-A-2）
 * 基于员工编制定义 + 工具注册表 + 工具健康状态，动态生成能力清单。
 */
public class ActionDiscoveryServiceImpl implements ActionDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ActionDiscoveryServiceImpl.class);

    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final ToolRegistry toolRegistry;
    private final BackendRegistry backendRegistry;

    public ActionDiscoveryServiceImpl(FixedEmployeeRegistry fixedEmployeeRegistry,
                                       ToolRegistry toolRegistry,
                                       BackendRegistry backendRegistry) {
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.toolRegistry = toolRegistry;
        this.backendRegistry = backendRegistry;
    }

    @Override
    public EmployeeCapabilityProfile discoverCapabilities(String employeeCode) {
        if (fixedEmployeeRegistry == null) {
            log.debug("No FixedEmployeeRegistry, returning empty profile for {}", employeeCode);
            return EmployeeCapabilityProfile.empty(employeeCode);
        }

        var defOpt = fixedEmployeeRegistry.getDefinitionByCode(employeeCode);
        if (defOpt.isEmpty()) {
            log.debug("Employee {} not in FixedEmployeeRegistry", employeeCode);
            return EmployeeCapabilityProfile.empty(employeeCode);
        }

        var def = defOpt.get();

        // 构建工具能力清单
        List<EmployeeCapabilityProfile.ToolCapability> toolCaps = new ArrayList<>();
        if (def.tools() != null) {
            for (String toolName : def.tools()) {
                List<String> caps = resolveToolCapabilities(toolName);
                boolean healthy = checkToolHealthy(toolName);
                toolCaps.add(new EmployeeCapabilityProfile.ToolCapability(toolName, caps, healthy));
            }
        }

        // 从编制定义中提取能力
        List<ExecutionCapability> capabilities = resolveCapabilities(def.capabilities());

        // 从工具能力中推断支持的任务类型
        List<String> supportedTaskTypes = inferTaskTypes(capabilities, toolCaps);

        // 从编制定义中提取知识领域
        List<String> knowledgeDomains = def.roles() != null ? def.roles() : List.of();

        return new EmployeeCapabilityProfile(
            employeeCode,
            def.department(),
            toolCaps,
            supportedTaskTypes,
            capabilities,
            knowledgeDomains,
            0,
            5
        );
    }

    @Override
    public ToolHealthStatus checkToolHealth(String toolName) {
        if (backendRegistry != null) {
            var health = backendRegistry.healthCheck(toolName);
            return new ToolHealthStatus(toolName, health.healthy(), health.latencyMs(), health.detail());
        }
        // 无 BackendRegistry 时降级为注册表检查
        if (toolRegistry != null && toolRegistry.exists(toolName)) {
            return new ToolHealthStatus(toolName, true, -1, "已注册（无健康检查）");
        }
        return ToolHealthStatus.unknown(toolName);
    }

    @Override
    public Optional<ToolMatch> findBestTool(String employeeCode, String taskDescription,
                                             ExecutionCapability required) {
        var profile = discoverCapabilities(employeeCode);
        if (profile.availableTools().isEmpty()) {
            return Optional.empty();
        }

        // 简单匹配：优先找健康且能力匹配的工具
        String bestToolName = null;
        double bestScore = 0;

        for (var tc : profile.availableTools()) {
            if (!tc.healthy()) continue;
            double score = computeRelevanceScore(tc, required, taskDescription);
            if (score > bestScore) {
                bestScore = score;
                bestToolName = tc.toolName();
            }
        }

        if (bestToolName != null && bestScore > 0) {
            String bestName = bestToolName;
            List<String> fallbacks = profile.availableTools().stream()
                .filter(tc -> !tc.toolName().equals(bestName) && tc.healthy())
                .map(EmployeeCapabilityProfile.ToolCapability::toolName)
                .limit(3)
                .toList();
            return Optional.of(new ToolMatch(bestName, bestScore,
                "能力匹配得分 " + bestScore, fallbacks));
        }

        return Optional.empty();
    }

    private List<String> resolveToolCapabilities(String toolName) {
        if (toolRegistry == null) return List.of();
        return toolRegistry.get(toolName)
            .map(Tool::getSchema)
            .map(ToolSchema::capabilities)
            .orElse(List.of());
    }

    private boolean checkToolHealthy(String toolName) {
        if (backendRegistry != null) {
            return backendRegistry.healthCheck(toolName).healthy();
        }
        return toolRegistry != null && toolRegistry.exists(toolName);
    }

    private List<ExecutionCapability> resolveCapabilities(List<String> capabilityNames) {
        if (capabilityNames == null) return List.of();
        List<ExecutionCapability> result = new ArrayList<>();
        for (String name : capabilityNames) {
            try {
                result.add(ExecutionCapability.valueOf(name));
            } catch (IllegalArgumentException e) {
                log.debug("Unknown capability: {}", name);
            }
        }
        return result;
    }

    private List<String> inferTaskTypes(List<ExecutionCapability> capabilities,
                                         List<EmployeeCapabilityProfile.ToolCapability> toolCaps) {
        List<String> types = new ArrayList<>();
        for (ExecutionCapability cap : capabilities) {
            types.add(cap.name().toLowerCase());
        }
        for (var tc : toolCaps) {
            for (String cap : tc.capabilities()) {
                if (!types.contains(cap.toLowerCase())) {
                    types.add(cap.toLowerCase());
                }
            }
        }
        return types;
    }

    private double computeRelevanceScore(EmployeeCapabilityProfile.ToolCapability tc,
                                          ExecutionCapability required,
                                          String taskDescription) {
        double score = 0;
        // 能力标签直接匹配
        if (tc.capabilities().contains(required.name())) {
            score += 0.6;
        }
        // 描述模糊匹配
        if (taskDescription != null) {
            String lower = taskDescription.toLowerCase();
            for (String cap : tc.capabilities()) {
                if (lower.contains(cap.toLowerCase())) {
                    score += 0.2;
                    break;
                }
            }
        }
        // 健康加成
        if (tc.healthy()) {
            score += 0.2;
        }
        return score;
    }
}
