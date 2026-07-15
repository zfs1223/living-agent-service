package com.livingagent.core.skill.feedback;

import com.livingagent.core.autonomy.ExecutionCapability;
import com.livingagent.core.employee.EmployeeCapabilityProfile;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.skill.Skill;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能覆盖度评估器（64-G-1）
 * 评估员工技能覆盖度，识别高价值缺失技能，生成增量优化建议。
 */
public class SkillCoverageEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SkillCoverageEvaluator.class);

    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final SkillRegistry skillRegistry;
    private final ToolRegistry toolRegistry;

    public SkillCoverageEvaluator(FixedEmployeeRegistry fixedEmployeeRegistry,
                                   SkillRegistry skillRegistry,
                                   ToolRegistry toolRegistry) {
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.skillRegistry = skillRegistry;
        this.toolRegistry = toolRegistry;
    }

    public record CoverageReport(
        double coverageRate,
        List<String> coveredAreas,
        List<String> missingAreas,
        List<SkillSuggestion> suggestions
    ) {}

    public record SkillSuggestion(
        String skillName,
        String reason,
        Priority priority,
        String sourceTool
    ) {}

    public enum Priority {
        HIGH, MEDIUM, LOW
    }

    /**
     * 评估员工技能覆盖度
     */
    public CoverageReport evaluate(String employeeCode) {
        if (fixedEmployeeRegistry == null || skillRegistry == null) {
            return new CoverageReport(0, List.of(), List.of("无法评估：注册表不可用"), List.of());
        }

        var defOpt = fixedEmployeeRegistry.getDefinitionByCode(employeeCode);
        if (defOpt.isEmpty()) {
            return new CoverageReport(0, List.of(), List.of("员工 " + employeeCode + " 不在编制中"), List.of());
        }

        var def = defOpt.get();

        // 1. 盘点员工已绑定技能
        List<String> boundSkills = def.requiredSkills() != null ? def.requiredSkills() : List.of();
        List<String> coveredAreas = new ArrayList<>(boundSkills);

        // 2. 从技能注册表获取部门相关技能
        List<Skill> deptSkills = skillRegistry.getAllSkills().stream()
            .filter(s -> {
                String target = s.getTargetBrain();
                if (target == null) return false;
                String deptBrain = def.department() != null
                    ? capitalize(def.department()) + "Brain" : "";
                return target.equals(deptBrain) || target.equals("MainBrain") || "global".equals(s.getScope());
            })
            .toList();

        for (Skill s : deptSkills) {
            if (!coveredAreas.contains(s.getName())) {
                coveredAreas.add(s.getName());
            }
        }

        // 3. 对比部门职责要求的完整能力
        List<String> requiredCapabilities = def.capabilities() != null ? def.capabilities() : List.of();
        List<String> missingAreas = new ArrayList<>();
        for (String cap : requiredCapabilities) {
            boolean covered = coveredAreas.stream()
                .anyMatch(area -> area.toLowerCase().contains(cap.toLowerCase()));
            if (!covered) {
                missingAreas.add(cap);
            }
        }

        // 4. 识别高价值缺失技能
        List<SkillSuggestion> suggestions = generateSuggestions(
            employeeCode, def.department(), boundSkills, deptSkills, missingAreas);

        double coverageRate = requiredCapabilities.isEmpty() ? 1.0
            : 1.0 - (double) missingAreas.size() / requiredCapabilities.size();

        return new CoverageReport(coverageRate, coveredAreas, missingAreas, suggestions);
    }

    private List<SkillSuggestion> generateSuggestions(String employeeCode, String department,
                                                       List<String> boundSkills,
                                                       List<Skill> deptSkills,
                                                       List<String> missingAreas) {
        List<SkillSuggestion> suggestions = new ArrayList<>();

        // 缺失能力 → 建议绑定对应技能
        for (String missing : missingAreas) {
            // 尝试在部门技能中找到匹配
            Skill match = deptSkills.stream()
                .filter(s -> s.getName().toLowerCase().contains(missing.toLowerCase())
                    || (s.getRequiredCapabilities() != null
                        && s.getRequiredCapabilities().stream()
                            .anyMatch(c -> c.equalsIgnoreCase(missing))))
                .findFirst()
                .orElse(null);

            if (match != null) {
                suggestions.add(new SkillSuggestion(
                    match.getName(),
                    "能力 '" + missing + "' 有对应技能但未绑定",
                    Priority.HIGH,
                    "SkillRegistry"
                ));
            } else {
                suggestions.add(new SkillSuggestion(
                    "skill_" + missing.toLowerCase(),
                    "能力 '" + missing + "' 无对应技能，需创建新技能",
                    Priority.MEDIUM,
                    inferSourceTool(missing)
                ));
            }
        }

        // 检查工具能力标签 → 未绑定的工具相关技能
        if (toolRegistry != null) {
            for (var tool : toolRegistry.getByDepartment(department)) {
                ToolSchema schema = tool.getSchema();
                if (schema != null && schema.capabilities() != null) {
                    for (String cap : schema.capabilities()) {
                        boolean hasSkill = boundSkills.stream()
                            .anyMatch(s -> s.toLowerCase().contains(cap.toLowerCase()));
                        if (!hasSkill && !missingAreas.contains(cap)) {
                            suggestions.add(new SkillSuggestion(
                                "skill_" + cap.toLowerCase(),
                                "工具 " + tool.getName() + " 有能力 '" + cap + "' 但无对应技能",
                                Priority.LOW,
                                tool.getName()
                            ));
                        }
                    }
                }
            }
        }

        return suggestions;
    }

    private String inferSourceTool(String capability) {
        try {
            ExecutionCapability cap = ExecutionCapability.valueOf(capability);
            return switch (cap) {
                case CODE_REVIEW -> "GitLabTool";
                case WEB_APP_BUILD -> "ClaudeCliTool";
                case DOCUMENT_GENERATION -> "OfficeTool";
                case DATA_ANALYSIS -> "HttpTool";
                case PROJECT_MANAGEMENT, ISSUE_TRACKING -> "OpenProjectTool";
                default -> "LLM";
            };
        } catch (IllegalArgumentException e) {
            return "LLM";
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
