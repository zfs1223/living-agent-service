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

    /** P1-2: LLM 响应最小有效长度阈值，低于此值视为异常短响应 */
    private static final int MIN_VALID_RESPONSE_LENGTH = 20;

    private static final String DISPATCH_SYSTEM_PROMPT = """
        你是企业主大脑，负责根据任务需求从可用员工中选择最合适的执行团队。

        ## 输出格式要求（严格遵守）

        你必须只输出一个合法的 JSON 对象，不要包含任何其他文字、解释或 markdown 标记。
        直接输出 JSON，不要用 ```json 包裹。

        JSON 结构：
        {"assignments": [{"employeeCode": "员工代码", "role": "分配角色", "reason": "选择理由", "confidence": 0.9}]}

        ## 选择原则

        1. 优先选择能力与任务匹配的员工
        2. 考虑员工负载和历史表现
        3. 如果没有合适员工，返回 {"assignments": []}

        ## 示例输出

        {"assignments": [{"employeeCode": "T09", "role": "前端实现", "reason": "前端开发能力匹配", "confidence": 0.85}]}
        """;

    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final BrainRegistry brainRegistry;
    private final RegistryBackedFixedEmployeeDispatcher fallbackDispatcher;
    private final AutonomyTraceService traceService;
    private final PerformanceStatsService performanceStatsService;

    public LlmBasedFixedEmployeeDispatcher(
            FixedEmployeeRegistry fixedEmployeeRegistry,
            BrainRegistry brainRegistry,
            AutonomyTraceService traceService) {
        this(fixedEmployeeRegistry, brainRegistry, traceService, null);
    }

    public LlmBasedFixedEmployeeDispatcher(
            FixedEmployeeRegistry fixedEmployeeRegistry,
            BrainRegistry brainRegistry,
            AutonomyTraceService traceService,
            PerformanceStatsService performanceStatsService) {
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.brainRegistry = brainRegistry;
        this.fallbackDispatcher = new RegistryBackedFixedEmployeeDispatcher(fixedEmployeeRegistry);
        this.traceService = traceService;
        this.performanceStatsService = performanceStatsService;
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
            log.debug("LLM dispatch prompt for department {}: promptLength={} chars",
                departmentTaskPlan.department(), userPrompt.length());

            String llmResponse = mainBrain.callLlm(DISPATCH_SYSTEM_PROMPT, userPrompt);

            // 增加 LLM 返回内容的日志诊断
            log.info("LLM dispatch response for department {}: responseLength={} chars, content={}",
                departmentTaskPlan.department(),
                llmResponse != null ? llmResponse.length() : 0,
                llmResponse != null ? (llmResponse.length() > 200 ? llmResponse.substring(0, 200) + "..." : llmResponse) : "null");

            // P1-2: 检测异常短响应，重试一次
            if (llmResponse != null && llmResponse.length() < MIN_VALID_RESPONSE_LENGTH) {
                log.warn("LLM returned abnormally short response ({} chars): '{}', retrying once",
                    llmResponse.length(), llmResponse);
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "llm_dispatch_retry", "LlmBasedFixedEmployeeDispatcher",
                    "LLM returned short response, retrying",
                    Map.of("shortResponse", llmResponse, "length", String.valueOf(llmResponse.length()))
                ));
                llmResponse = mainBrain.callLlm(DISPATCH_SYSTEM_PROMPT, userPrompt);
                log.info("LLM dispatch retry response: responseLength={} chars",
                    llmResponse != null ? llmResponse.length() : 0);
            }

            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("LLM returned empty for employee dispatch, using rule-based fallback");
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

        // 添加任务需要的工具信息，让 LLM 知道需要选择有对应工具的员工
        List<String> requiredTools = mainBrainTaskPlan.requiredTools();
        if (requiredTools != null && !requiredTools.isEmpty()) {
            sb.append("所需工具: ").append(String.join("、", requiredTools)).append("\n");
            sb.append("\n**重要提示**: 必须选择拥有以上所需工具的员工，否则任务无法执行。\n");
        }

        List<FixedEmployeeRegistry.FixedEmployeeDefinition> candidates =
            fixedEmployeeRegistry.getDefinitionsByDepartment(departmentTaskPlan.department());

        // P28-A: 获取员工绩效数据
        Map<String, PerformanceStatsService.EmployeePerformanceStats> statsMap = Map.of();
        if (performanceStatsService != null && !candidates.isEmpty()) {
            try {
                List<String> codes = candidates.stream().map(FixedEmployeeRegistry.FixedEmployeeDefinition::code).toList();
                statsMap = performanceStatsService.getStatsBatch(codes);
            } catch (Exception e) {
                log.debug("Failed to get performance stats for dispatch prompt: {}", e.getMessage());
            }
        }

        sb.append("\n可用员工:\n");
        for (FixedEmployeeRegistry.FixedEmployeeDefinition def : candidates) {
            sb.append("- ").append(def.code()).append(": ").append(def.name())
              .append("（").append(def.title()).append("）")
              .append(" 能力: ").append(String.join("、", def.capabilities()))
              .append(" 技能: ").append(def.requiredSkills() != null ? String.join("、", def.requiredSkills()) : "无")
              .append(" 工具: ").append(String.join("、", def.tools()));

            // P28-A: 注入绩效评分和分派权重
            PerformanceStatsService.EmployeePerformanceStats stats = statsMap.get(def.code());
            if (stats != null) {
                double dispatchWeight = performanceStatsService.getDispatchWeight(def.code());
                sb.append(" 绩效评分: ").append(String.format("%.1f", stats.normalizedScore()));
                sb.append(" 分派权重: ").append(String.format("%.2f", dispatchWeight));
                if (dispatchWeight < 0.3) {
                    sb.append(" [低绩效-慎重选择]");
                }
            }
            sb.append("\n");
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

        log.debug("extractJson input: length={}, first50={}",
            trimmed.length(), trimmed.length() > 50 ? trimmed.substring(0, 50) : trimmed);

        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf("\n") + 1;
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                String extracted = trimmed.substring(start, end).trim();
                log.debug("extractJson: extracted from ```json block, length={}", extracted.length());
                return extracted;
            }
        }
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n") + 1;
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                String extracted = trimmed.substring(start, end).trim();
                log.debug("extractJson: extracted from ``` block, length={}", extracted.length());
                return extracted;
            }
        }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            String extracted = trimmed.substring(braceStart, braceEnd + 1);
            log.debug("extractJson: extracted from braces, length={}", extracted.length());
            return extracted;
        }

        // LLM 可能返回了非 JSON 格式（如 "无"、"好的" 等），记录警告
        log.warn("extractJson: no JSON found in response, content={}",
            trimmed.length() > 100 ? trimmed.substring(0, 100) + "..." : trimmed);
        return null;
    }
}
