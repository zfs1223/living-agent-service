package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.DepartmentTaskPlan;
import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.autonomy.FixedEmployeeDispatcher;
import com.livingagent.core.autonomy.MainBrainTaskPlan;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class RegistryBackedFixedEmployeeDispatcher implements FixedEmployeeDispatcher {

    private final FixedEmployeeRegistry fixedEmployeeRegistry;

    public RegistryBackedFixedEmployeeDispatcher(FixedEmployeeRegistry fixedEmployeeRegistry) {
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
    }

    @Override
    public List<EmployeeWorkAssignment> planAssignments(
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            String sessionId,
            String userId) {
        if (mainBrainTaskPlan == null || departmentTaskPlan == null) {
            return List.of();
        }

        List<FixedEmployeeRegistry.FixedEmployeeDefinition> definitions = fixedEmployeeRegistry
            .getDefinitionsByDepartment(departmentTaskPlan.department());
        if (definitions.isEmpty()) {
            return List.of();
        }

        Set<String> selectedCodes = new LinkedHashSet<>(departmentTaskPlan.suggestedEmployeeCodes());
        if (selectedCodes.isEmpty()) {
            for (String role : departmentTaskPlan.suggestedRoles()) {
                findByRoleOrCapability(definitions, role).map(FixedEmployeeRegistry.FixedEmployeeDefinition::code).ifPresent(selectedCodes::add);
            }
        }
        if (selectedCodes.isEmpty()) {
            definitions.stream().limit(3).map(FixedEmployeeRegistry.FixedEmployeeDefinition::code).forEach(selectedCodes::add);
        }

        List<EmployeeWorkAssignment> assignments = new ArrayList<>();
        int index = 0;
        for (String code : selectedCodes) {
            Optional<FixedEmployeeRegistry.FixedEmployeeDefinition> definitionOpt = fixedEmployeeRegistry.getDefinitionByCode(code);
            if (definitionOpt.isEmpty()) {
                continue;
            }
            FixedEmployeeRegistry.FixedEmployeeDefinition definition = definitionOpt.get();
            if (!departmentTaskPlan.department().equalsIgnoreCase(definition.department())) {
                continue;
            }
            String role = pickRole(definition, departmentTaskPlan, index);
            assignments.add(new EmployeeWorkAssignment(
                UUID.randomUUID().toString(),
                departmentTaskPlan.department(),
                definition.code(),
                definition.neuronId(),
                definition.name(),
                role,
                departmentTaskPlan.objective(),
                buildInstruction(mainBrainTaskPlan, departmentTaskPlan, definition, role),
                departmentTaskPlan.expectedDeliverables(),
                definition.tools(),
                buildContext(mainBrainTaskPlan, departmentTaskPlan, sessionId, userId)
            ));
            index++;
        }
        return assignments;
    }

    @Override
    public List<EmployeeWorkAssignment> reassign(MainBrainTaskPlan plan, List<String> failedEmployeeCodes) {
        if (plan == null || failedEmployeeCodes == null || failedEmployeeCodes.isEmpty()) {
            return List.of();
        }

        Set<String> excluded = new LinkedHashSet<>(failedEmployeeCodes);
        List<EmployeeWorkAssignment> reassigned = new ArrayList<>();

        for (DepartmentTaskPlan departmentTaskPlan : plan.departmentPlans()) {
            List<FixedEmployeeRegistry.FixedEmployeeDefinition> definitions = fixedEmployeeRegistry
                .getDefinitionsByDepartment(departmentTaskPlan.department());
            if (definitions.isEmpty()) {
                continue;
            }

            // 排除已失败的员工
            List<FixedEmployeeRegistry.FixedEmployeeDefinition> available = definitions.stream()
                .filter(d -> !excluded.contains(d.code()))
                .toList();

            if (available.isEmpty()) {
                continue;
            }

            // 按角色/能力匹配替代员工
            Set<String> selectedCodes = new LinkedHashSet<>();
            for (String role : departmentTaskPlan.suggestedRoles()) {
                findByRoleOrCapability(available, role)
                    .map(FixedEmployeeRegistry.FixedEmployeeDefinition::code)
                    .ifPresent(selectedCodes::add);
            }
            // 如果角色匹配不到，从可用员工中按顺序选取
            if (selectedCodes.isEmpty()) {
                available.stream().limit(3)
                    .map(FixedEmployeeRegistry.FixedEmployeeDefinition::code)
                    .forEach(selectedCodes::add);
            }

            int index = 0;
            for (String code : selectedCodes) {
                Optional<FixedEmployeeRegistry.FixedEmployeeDefinition> definitionOpt = fixedEmployeeRegistry.getDefinitionByCode(code);
                if (definitionOpt.isEmpty()) {
                    continue;
                }
                FixedEmployeeRegistry.FixedEmployeeDefinition definition = definitionOpt.get();
                String role = pickRole(definition, departmentTaskPlan, index);
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("planId", plan.planId());
                context.put(TaskMetadataKeys.TASK_TYPE, plan.taskType());
                context.put("department", departmentTaskPlan.department());
                context.put("reassignedFrom", String.join(",", failedEmployeeCodes));
                context.put("reassignReason", "employee_execution_failed");

                reassigned.add(new EmployeeWorkAssignment(
                    UUID.randomUUID().toString(),
                    departmentTaskPlan.department(),
                    definition.code(),
                    definition.neuronId(),
                    definition.name(),
                    role,
                    departmentTaskPlan.objective(),
                    buildInstruction(plan, departmentTaskPlan, definition, role),
                    departmentTaskPlan.expectedDeliverables(),
                    definition.tools(),
                    context
                ));
                index++;
            }
        }
        return reassigned;
    }

    private Optional<FixedEmployeeRegistry.FixedEmployeeDefinition> findByRoleOrCapability(
            List<FixedEmployeeRegistry.FixedEmployeeDefinition> definitions,
            String role) {
        String normalized = role == null ? "" : role.toLowerCase();
        return definitions.stream()
            .filter(definition -> containsIgnoreCase(definition.roles(), normalized)
                || containsIgnoreCase(definition.capabilities(), normalized)
                || definition.title().toLowerCase().contains(normalized))
            .findFirst();
    }

    private boolean containsIgnoreCase(List<String> values, String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return values != null && values.stream().anyMatch(value -> value != null && value.toLowerCase().contains(expected));
    }

    private String pickRole(
            FixedEmployeeRegistry.FixedEmployeeDefinition definition,
            DepartmentTaskPlan departmentTaskPlan,
            int index) {
        if (departmentTaskPlan.suggestedRoles() != null && index < departmentTaskPlan.suggestedRoles().size()) {
            return departmentTaskPlan.suggestedRoles().get(index);
        }
        if (definition.roles() != null && !definition.roles().isEmpty()) {
            return definition.roles().get(0);
        }
        return definition.title();
    }

    private String buildInstruction(
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            FixedEmployeeRegistry.FixedEmployeeDefinition definition,
            String role) {
        return "请以" + definition.name() + "（" + definition.title() + "）的职责执行子任务。"
            + "\n任务目标：" + mainBrainTaskPlan.goal()
            + "\n部门目标：" + departmentTaskPlan.objective()
            + "\n本次角色：" + role
            + "\n任务类型：" + mainBrainTaskPlan.taskType()
            + "\n期望产物：" + String.join("、", departmentTaskPlan.expectedDeliverables())
            + "\n验收标准：" + String.join("；", departmentTaskPlan.acceptanceCriteria());
    }

    private Map<String, Object> buildContext(
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            String sessionId,
            String userId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sessionId", sessionId);
        context.put("userId", userId);
        context.put("planId", mainBrainTaskPlan.planId());
        context.put(TaskMetadataKeys.TASK_TYPE, mainBrainTaskPlan.taskType());
        context.put("department", departmentTaskPlan.department());
        return context;
    }
}
