package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.EmployeeEquipmentService;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry.FixedEmployeeDefinition;
import com.livingagent.core.skill.SkillRegistry;
import com.livingagent.core.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NP1-1: 员工动态装备服务实现
 * 
 * 实现"工具技能闭环"中的"装备"和"回收"环节
 * 闭环流程：寻找→匹配→装备→执行→回收
 */
public class DefaultEmployeeEquipmentService implements EmployeeEquipmentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmployeeEquipmentService.class);

    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;

    // 运行时装备状态（任务期间临时装备的工具/技能）
    private final Map<String, Set<String>> runtimeTools = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> runtimeSkills = new ConcurrentHashMap<>();

    public DefaultEmployeeEquipmentService(
            FixedEmployeeRegistry fixedEmployeeRegistry,
            ToolRegistry toolRegistry,
            SkillRegistry skillRegistry) {
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
    }

    @Override
    public EquipmentResult equipTools(String employeeCode, List<String> toolIds) {
        if (employeeCode == null || toolIds == null || toolIds.isEmpty()) {
            return EquipmentResult.failed(employeeCode, toolIds != null ? toolIds : List.of(),
                "employeeCode or toolIds is empty");
        }

        Optional<FixedEmployeeDefinition> defOpt = fixedEmployeeRegistry.getDefinitionByCode(employeeCode);
        if (defOpt.isEmpty()) {
            return EquipmentResult.failed(employeeCode, toolIds,
                "Employee not found: " + employeeCode);
        }

        List<String> equipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        Set<String> currentTools = runtimeTools.computeIfAbsent(employeeCode, k -> ConcurrentHashMap.newKeySet());
        // 加上员工定义中的工具
        FixedEmployeeDefinition def = defOpt.get();
        if (def.tools() != null) {
            currentTools.addAll(def.tools());
        }

        for (String toolId : toolIds) {
            // 检查工具是否已注册
            if (toolRegistry == null || !toolRegistry.exists(toolId)) {
                log.warn("Tool {} not found in registry, skipping equipment for employee {}", toolId, employeeCode);
                failed.add(toolId);
                continue;
            }

            // 检查是否已装备
            if (currentTools.contains(toolId)) {
                skipped.add(toolId);
                continue;
            }

            // 装备工具
            currentTools.add(toolId);
            equipped.add(toolId);
            log.info("Tool {} equipped for employee {}", toolId, employeeCode);
        }

        if (!equipped.isEmpty()) {
            runtimeTools.put(employeeCode, currentTools);
        }

        if (failed.isEmpty()) {
            return EquipmentResult.success(employeeCode, equipped, skipped);
        } else if (!equipped.isEmpty()) {
            return EquipmentResult.partial(employeeCode, equipped, failed);
        } else {
            return EquipmentResult.failed(employeeCode, failed, "All tools failed to equip");
        }
    }

    @Override
    public EquipmentResult equipSkills(String employeeCode, List<String> skillIds) {
        if (employeeCode == null || skillIds == null || skillIds.isEmpty()) {
            return EquipmentResult.failed(employeeCode, skillIds != null ? skillIds : List.of(),
                "employeeCode or skillIds is empty");
        }

        Optional<FixedEmployeeDefinition> defOpt = fixedEmployeeRegistry.getDefinitionByCode(employeeCode);
        if (defOpt.isEmpty()) {
            return EquipmentResult.failed(employeeCode, skillIds,
                "Employee not found: " + employeeCode);
        }

        List<String> equipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        Set<String> currentSkills = runtimeSkills.computeIfAbsent(employeeCode, k -> ConcurrentHashMap.newKeySet());
        FixedEmployeeDefinition def = defOpt.get();
        if (def.requiredSkills() != null) {
            currentSkills.addAll(def.requiredSkills());
        }

        for (String skillId : skillIds) {
            // 检查技能是否已注册
            if (skillRegistry != null && skillRegistry.getSkill(skillId).isEmpty()) {
                log.warn("Skill {} not found in registry, skipping equipment for employee {}", skillId, employeeCode);
                failed.add(skillId);
                continue;
            }

            // 检查是否已装备
            if (currentSkills.contains(skillId)) {
                skipped.add(skillId);
                continue;
            }

            // 装备技能
            currentSkills.add(skillId);
            equipped.add(skillId);
            log.info("Skill {} equipped for employee {}", skillId, employeeCode);
        }

        if (!equipped.isEmpty()) {
            runtimeSkills.put(employeeCode, currentSkills);
        }

        if (failed.isEmpty()) {
            return EquipmentResult.success(employeeCode, equipped, skipped);
        } else if (!equipped.isEmpty()) {
            return EquipmentResult.partial(employeeCode, equipped, failed);
        } else {
            return EquipmentResult.failed(employeeCode, failed, "All skills failed to equip");
        }
    }

    @Override
    public EquipmentResult recycle(String employeeCode, String taskType) {
        // 回收运行时装备的工具和技能（保留员工定义中的工具/技能）
        Set<String> removedTools = new LinkedHashSet<>();
        Set<String> removedSkills = new LinkedHashSet<>();

        Optional<FixedEmployeeDefinition> defOpt = fixedEmployeeRegistry.getDefinitionByCode(employeeCode);

        // 回收工具：移除运行时装备的、不在员工定义中的工具
        Set<String> currentTools = runtimeTools.get(employeeCode);
        if (currentTools != null) {
            Set<String> permanentTools = defOpt.map(d -> d.tools() != null ? new HashSet<>(d.tools()) : Set.<String>of())
                .orElse(Set.of());
            Iterator<String> it = currentTools.iterator();
            while (it.hasNext()) {
                String tool = it.next();
                if (!permanentTools.contains(tool)) {
                    removedTools.add(tool);
                    it.remove();
                }
            }
        }

        // 回收技能：移除运行时装备的、不在员工定义中的技能
        Set<String> currentSkills = runtimeSkills.get(employeeCode);
        if (currentSkills != null) {
            Set<String> permanentSkills = defOpt.map(d -> d.requiredSkills() != null ? new HashSet<>(d.requiredSkills()) : Set.<String>of())
                .orElse(Set.of());
            Iterator<String> it = currentSkills.iterator();
            while (it.hasNext()) {
                String skill = it.next();
                if (!permanentSkills.contains(skill)) {
                    removedSkills.add(skill);
                    it.remove();
                }
            }
        }

        int totalRemoved = removedTools.size() + removedSkills.size();
        if (totalRemoved > 0) {
            log.info("Recycled {} tools and {} skills for employee {} after task {}",
                removedTools.size(), removedSkills.size(), employeeCode, taskType);
        }

        return new EquipmentResult(
            true,
            employeeCode,
            List.of(),
            List.of(),
            new ArrayList<>(totalRemoved > 0 ? removedTools : removedSkills),
            String.format("回收完成: %d 工具, %d 技能", removedTools.size(), removedSkills.size()),
            Map.of("removedTools", removedTools.size(), "removedSkills", removedSkills.size())
        );
    }

    @Override
    public List<String> getEquippedTools(String employeeCode) {
        Set<String> tools = runtimeTools.get(employeeCode);
        if (tools != null) {
            return List.copyOf(tools);
        }
        // 回退到员工定义中的工具
        return fixedEmployeeRegistry.getDefinitionByCode(employeeCode)
            .map(FixedEmployeeDefinition::tools)
            .orElse(List.of());
    }

    @Override
    public List<String> getEquippedSkills(String employeeCode) {
        Set<String> skills = runtimeSkills.get(employeeCode);
        if (skills != null) {
            return List.copyOf(skills);
        }
        // 回退到员工定义中的技能
        return fixedEmployeeRegistry.getDefinitionByCode(employeeCode)
            .map(FixedEmployeeDefinition::requiredSkills)
            .orElse(List.of());
    }

    @Override
    public List<String> checkMissingTools(String employeeCode, List<String> requiredTools) {
        if (requiredTools == null || requiredTools.isEmpty()) {
            return List.of();
        }
        List<String> equipped = getEquippedTools(employeeCode);
        return requiredTools.stream()
            .filter(t -> !equipped.contains(t))
            .toList();
    }
}