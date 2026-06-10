package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class LlmBasedFixedEmployeeDispatcher implements FixedEmployeeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LlmBasedFixedEmployeeDispatcher.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DISPATCH_SYSTEM_PROMPT = """
        你是企业主大脑，负责根据任务需求从可用员工中选择最合适的执行团队。
        
        你需要：
        1. 分析任务目标和部门计划
        2. 评估每位候选员工的能力匹配度
        3. 考虑员工负载和历史表现
        4. 选择最合适的员工组合
        5. 为每位员工分配明确的角色和职责
        
        你必须只输出一个合法的JSON对象，不要包含任何其他文字。
        JSON结构如下：
        {
          "assignments": [
            {
              "employeeCode": "T02",
              "role": "架构设计",
              "reason": "系统架构设计能力匹配，且当前负载较低",
              "confidence": 0.9
            },
            {
              "employeeCode": "T09",
              "role": "前端实现",
              "reason": "前端开发能力匹配，UI交互经验丰富",
              "confidence": 0.85
            }
          ]
        }
        
        如果没有合适的员工，返回空数组：{"assignments": []}
        """;

    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final BrainRegistry brainRegistry;
    private final RegistryBackedFixedEmployeeDispatcher fallbackDispatcher;
    private final AutonomyTraceService traceService;

    public LlmBasedFixedEmployeeDispatcher(
            FixedEmployeeRegistry fixedEmployeeRegistry,
            BrainRegistry brainRegistry,
            AutonomyTraceService traceService) {
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.brainRegistry = brainRegistry;
        this.fallbackDispatcher = new RegistryBackedFixedEmployeeDispatcher(fixedEmployeeRegistry);
        this.traceService = traceService;
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

        String requestId = mainBrainTaskPlan.planId();

        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            log.debug("MainBrain not available for employee dispatch, using rule-based fallback");
            traceService.recordEvent(AutonomyTraceEvent.of(
                requestId, "employee_assignment_planned", "LlmBasedFixedEmployeeDispatcher",
                "MainBrain unavailable, using rule-based fallback"
            ));
            return fallbackDispatcher.planAssignments(mainBrainTaskPlan, departmentTaskPlan, sessionId, userId);
        }

        try {
            String userPrompt = buildDispatchPrompt(mainBrainTaskPlan, departmentTaskPlan);
            String llmResponse = mainBrain.callLlm(DISPATCH_SYSTEM_PROMPT, userPrompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                log.debug("LLM returned empty for employee dispatch, using rule-based fallback");
                return fallbackWithTrace(
                    fallbackDispatcher.planAssignments(mainBrainTaskPlan, departmentTaskPlan, sessionId, userId),
                    requestId, "LLM returned empty, using rule-based fallback");
            }

            List<EmployeeWorkAssignment> llmAssignments = parseDispatchResponse(
                llmResponse, mainBrainTaskPlan, departmentTaskPlan, sessionId, userId);

            if (llmAssignments != null && !llmAssignments.isEmpty()) {
                log.info("LLM dispatched {} employees for department {}", llmAssignments.size(), departmentTaskPlan.department());
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "employee_assignment_planned", "LlmBasedFixedEmployeeDispatcher",
                    "LLM-driven employee dispatch",
                    Map.of("dispatcher_type", "llm_based",
                           "employeeCount", String.valueOf(llmAssignments.size()),
                           "department", departmentTaskPlan.department())
                ));
                return llmAssignments;
            }

            return fallbackWithTrace(
                fallbackDispatcher.planAssignments(mainBrainTaskPlan, departmentTaskPlan, sessionId, userId),
                requestId, "LLM dispatch parse failed or empty, using rule-based fallback");

        } catch (Exception e) {
            log.warn("LLM employee dispatch failed: {}, using rule-based fallback", e.getMessage());
            return fallbackWithTrace(
                fallbackDispatcher.planAssignments(mainBrainTaskPlan, departmentTaskPlan, sessionId, userId),
                requestId, "LLM call failed: " + e.getMessage());
        }
    }

    @Override
    public List<EmployeeWorkAssignment> reassign(MainBrainTaskPlan plan, List<String> failedEmployeeCodes) {
        if (plan == null || failedEmployeeCodes == null || failedEmployeeCodes.isEmpty()) {
            return List.of();
        }

        // LLM 换人重派：构建排除失败员工的 prompt，尝试 LLM 选择替代员工
        MainBrain mainBrain = brainRegistry.get(MainBrain.ID)
            .filter(b -> b instanceof MainBrain)
            .map(b -> (MainBrain) b)
            .orElse(null);

        if (mainBrain == null) {
            log.debug("MainBrain not available for reassign, using rule-based fallback");
            return fallbackDispatcher.reassign(plan, failedEmployeeCodes);
        }

        try {
            String reassignPrompt = buildReassignPrompt(plan, failedEmployeeCodes);
            String llmResponse = mainBrain.callLlm(DISPATCH_SYSTEM_PROMPT, reassignPrompt);

            if (llmResponse == null || llmResponse.isBlank()) {
                log.debug("LLM returned empty for reassign, using rule-based fallback");
                return fallbackDispatcher.reassign(plan, failedEmployeeCodes);
            }

            // 复用已有的解析逻辑，为每个部门计划解析 LLM 返回的分派结果
            List<EmployeeWorkAssignment> reassigned = new ArrayList<>();
            for (DepartmentTaskPlan departmentTaskPlan : plan.departmentPlans()) {
                List<EmployeeWorkAssignment> parsed = parseDispatchResponse(
                    llmResponse, plan, departmentTaskPlan, null, null);
                if (parsed != null && !parsed.isEmpty()) {
                    // 过滤掉已失败的员工
                    Set<String> excluded = new HashSet<>(failedEmployeeCodes);
                    reassigned.addAll(parsed.stream()
                        .filter(a -> !excluded.contains(a.employeeCode()))
                        .toList());
                }
            }

            if (!reassigned.isEmpty()) {
                traceService.recordEvent(AutonomyTraceEvent.of(
                    plan.planId(), "employee_reassigned", "LlmBasedFixedEmployeeDispatcher",
                    "LLM-driven employee reassignment",
                    Map.of("failedEmployeeCodes", String.join(",", failedEmployeeCodes),
                           "reassignedCount", String.valueOf(reassigned.size()))
                ));
                return reassigned;
            }

            return fallbackDispatcher.reassign(plan, failedEmployeeCodes);
        } catch (Exception e) {
            log.warn("LLM reassign failed: {}, using rule-based fallback", e.getMessage());
            return fallbackDispatcher.reassign(plan, failedEmployeeCodes);
        }
    }

    private String buildReassignPrompt(MainBrainTaskPlan plan, List<String> failedEmployeeCodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("【换人重派】以下员工执行失败，需要重新选择替代员工：\n");
        sb.append("失败员工: ").append(String.join("、", failedEmployeeCodes)).append("\n\n");
        sb.append("任务目标: ").append(plan.goal()).append("\n");
        sb.append("任务类型: ").append(plan.taskType()).append("\n");
        sb.append("主责部门: ").append(plan.primaryDepartment()).append("\n");

        for (DepartmentTaskPlan dp : plan.departmentPlans()) {
            sb.append("\n部门: ").append(dp.department()).append("\n");
            sb.append("部门目标: ").append(dp.objective()).append("\n");
            sb.append("期望交付物: ").append(String.join("、", dp.expectedDeliverables())).append("\n");
            sb.append("建议角色: ").append(String.join("、", dp.suggestedRoles())).append("\n");

            List<FixedEmployeeRegistry.FixedEmployeeDefinition> candidates =
                fixedEmployeeRegistry.getDefinitionsByDepartment(dp.department());
            sb.append("可用员工（排除失败员工）:\n");
            for (FixedEmployeeRegistry.FixedEmployeeDefinition def : candidates) {
                if (failedEmployeeCodes.contains(def.code())) continue;
                sb.append("- ").append(def.code()).append(": ").append(def.name())
                  .append("（").append(def.title()).append("）")
                  .append(" 能力: ").append(String.join("、", def.capabilities()))
                  .append("\n");
            }
        }

        sb.append("\n请从以上可用员工中选择替代执行团队。");
        return sb.toString();
    }

    private String buildDispatchPrompt(MainBrainTaskPlan mainBrainTaskPlan, DepartmentTaskPlan departmentTaskPlan) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务目标: ").append(mainBrainTaskPlan.goal()).append("\n");
        sb.append("任务类型: ").append(mainBrainTaskPlan.taskType()).append("\n");
        sb.append("主责部门: ").append(mainBrainTaskPlan.primaryDepartment()).append("\n");
        sb.append("部门目标: ").append(departmentTaskPlan.objective()).append("\n");
        sb.append("期望交付物: ").append(String.join("、", departmentTaskPlan.expectedDeliverables())).append("\n");
        sb.append("验收标准: ").append(String.join("；", departmentTaskPlan.acceptanceCriteria())).append("\n");
        sb.append("建议角色: ").append(String.join("、", departmentTaskPlan.suggestedRoles())).append("\n");

        List<FixedEmployeeRegistry.FixedEmployeeDefinition> candidates =
            fixedEmployeeRegistry.getDefinitionsByDepartment(departmentTaskPlan.department());

        sb.append("\n可用员工:\n");
        for (FixedEmployeeRegistry.FixedEmployeeDefinition def : candidates) {
            sb.append("- ").append(def.code()).append(": ").append(def.name())
              .append("（").append(def.title()).append("）")
              .append(" 能力: ").append(String.join("、", def.capabilities()))
              .append(" 技能: ").append(def.requiredSkills() != null ? String.join("、", def.requiredSkills()) : "无")
              .append(" 工具: ").append(String.join("、", def.tools()))
              .append("\n");
        }

        sb.append("\n请从以上员工中选择最合适的执行团队。");
        return sb.toString();
    }

    private List<EmployeeWorkAssignment> parseDispatchResponse(
            String llmResponse,
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            String sessionId,
            String userId) {

        String json = extractJson(llmResponse);
        if (json == null) return null;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            Object assignmentsObj = parsed.get("assignments");
            if (!(assignmentsObj instanceof List<?> assignmentList)) {
                return null;
            }

            List<EmployeeWorkAssignment> assignments = new ArrayList<>();
            for (Object item : assignmentList) {
                if (!(item instanceof Map<?, ?> assignmentMap)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> am = (Map<String, Object>) assignmentMap;

                String employeeCode = (String) am.getOrDefault("employeeCode", "");
                String role = (String) am.getOrDefault("role", "");
                String reason = (String) am.getOrDefault("reason", "");

                Optional<FixedEmployeeRegistry.FixedEmployeeDefinition> defOpt =
                    fixedEmployeeRegistry.getDefinitionByCode(employeeCode);
                if (defOpt.isEmpty()) {
                    log.warn("LLM suggested unknown employee code: {}, skipping", employeeCode);
                    continue;
                }

                FixedEmployeeRegistry.FixedEmployeeDefinition def = defOpt.get();

                Map<String, Object> context = new LinkedHashMap<>();
                context.put("sessionId", sessionId);
                context.put("userId", userId);
                context.put("planId", mainBrainTaskPlan.planId());
                context.put(TaskMetadataKeys.TASK_TYPE, mainBrainTaskPlan.taskType());
                context.put("department", departmentTaskPlan.department());
                context.put("selectionReason", reason);
                context.put("dispatcher_type", "llm_based");

                assignments.add(new EmployeeWorkAssignment(
                    UUID.randomUUID().toString(),
                    departmentTaskPlan.department(),
                    def.code(),
                    def.neuronId(),
                    def.name(),
                    role.isBlank() ? def.title() : role,
                    departmentTaskPlan.objective(),
                    buildInstruction(mainBrainTaskPlan, departmentTaskPlan, def, role),
                    departmentTaskPlan.expectedDeliverables(),
                    def.tools(),
                    context
                ));
            }
            return assignments;

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse LLM dispatch response: {}", e.getMessage());
            return null;
        }
    }

    private String buildInstruction(
            MainBrainTaskPlan mainBrainTaskPlan,
            DepartmentTaskPlan departmentTaskPlan,
            FixedEmployeeRegistry.FixedEmployeeDefinition definition,
            String role) {
        return "请以" + definition.name() + "（" + definition.title() + "）的职责执行子任务。"
            + "\n任务目标：" + mainBrainTaskPlan.goal()
            + "\n部门目标：" + departmentTaskPlan.objective()
            + "\n本次角色：" + (role.isBlank() ? definition.title() : role)
            + "\n任务类型：" + mainBrainTaskPlan.taskType()
            + "\n期望产物：" + String.join("、", departmentTaskPlan.expectedDeliverables())
            + "\n验收标准：" + String.join("；", departmentTaskPlan.acceptanceCriteria())
            + "\n你的能力：" + String.join("、", definition.capabilities())
            + "\n可用工具：" + String.join("、", definition.tools());
    }

    private List<EmployeeWorkAssignment> fallbackWithTrace(
            List<EmployeeWorkAssignment> assignments, String requestId, String reason) {
        traceService.recordEvent(AutonomyTraceEvent.of(
            requestId, "employee_assignment_planned", "LlmBasedFixedEmployeeDispatcher",
            reason,
            Map.of("dispatcher_type", "rule_based_fallback")
        ));
        return assignments;
    }

    private String extractJson(String response) {
        if (response == null) return null;
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf("\n") + 1;
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) return trimmed.substring(start, end).trim();
        }
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n") + 1;
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) return trimmed.substring(start, end).trim();
        }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) return trimmed.substring(braceStart, braceEnd + 1);
        return null;
    }
}
