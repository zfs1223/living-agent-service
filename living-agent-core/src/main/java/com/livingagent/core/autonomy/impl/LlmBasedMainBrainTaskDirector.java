package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.autonomy.ExecutionCapability;
import com.livingagent.core.autonomy.ArtifactType;
import com.livingagent.core.autonomy.ExecutionMode;
import com.livingagent.core.autonomy.TaskMetadataKeys;
import com.livingagent.core.autonomy.llm.LlmDecisionClient;
import com.livingagent.core.autonomy.llm.LlmDecisionClient.LlmDecisionRequest;
import com.livingagent.core.autonomy.llm.LlmDecisionClient.LlmDecisionResult;
import com.livingagent.core.autonomy.llm.LlmDecisionClient.JsonSchema;
import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import com.livingagent.core.knowledge.KnowledgeEntry;
import com.livingagent.core.knowledge.KnowledgeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Map.entry;
import java.util.concurrent.CompletableFuture;

public class LlmBasedMainBrainTaskDirector implements MainBrainTaskDirector {

    private static final Logger log = LoggerFactory.getLogger(LlmBasedMainBrainTaskDirector.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TASK_PLAN_SYSTEM_PROMPT_TEMPLATE = """
        你是 Living Agent 的主脑 MainBrain，职责是先确认用户需求是否明确，再决定是否规划任务、分派员工和启动执行。

        【硬性约束】
        1. 在 requirementStatus != REQUIREMENT_CONFIRMED 之前，不得输出 employee assignments
        2. clarificationQuestions 必须是结构化列表，且每个问题都可直接用于下一轮追问
        3. executionCapability、artifactType、executionMode 必须从枚举中选择，不得自由发挥
        4. 如果用户回答与历史需求冲突，必须重新进入需求确认流程
        5. 如果任务存在跨部门协作风险，必须标注 supportingDepartments 并保持主脑总控

        【你的目标】
        1. 识别用户意图和任务目标
        2. 判断当前需求是否明确、可执行、可验收
        3. 如果不明确，优先输出澄清问题，不得直接分派员工
        4. 如果已明确，输出正式任务计划，并给出标准化执行能力
        5. 必须维护需求版本，避免多轮对话中需求漂移
        6. 最终回复必须引用真实计划、真实回执和真实产物

        你必须只输出一个合法的JSON对象，不要包含任何其他文字、解释或markdown标记。
        JSON结构如下：
        {
          "intent": "用户意图",
          "requirementStatus": "REQUIREMENT_CONFIRMED",
          "requirementSummary": "需求摘要",
          "requirementVersion": 1,
          "clarificationQuestions": [],
          "taskType": "任务类型",
          "objective": "任务目标",
          "roughComplexity": 3,
          "riskLevel": 2,
          "executionCapability": "WEB_APP_BUILD",
          "artifactType": "INTERACTIVE_WEB_PAGE",
          "executionMode": "ARTIFACT_ONLY",
          "primaryDepartment": "主责部门代码",
          "supportingDepartments": [],
          "deliverables": ["交付物1"],
          "acceptanceCriteria": ["验收标准1"],
          "requiredSkills": ["技能1"],
          "requiredTools": ["工具1"],
          "departmentPlans": [
            {
              "department": "部门代码",
              "objective": "部门目标",
              "suggestedRoles": ["角色1"],
              "suggestedEmployeeCodes": ["员工编码1"]
            }
          ],
          "nextSteps": ["下一步1"],
          "summary": "总结"
        }

        requirementStatus 可选值（必须从中选择一个）：
        DRAFT(初始表达), NEEDS_CLARIFICATION(需要追问), CLARIFICATION_PENDING(等待用户回答),
        REQUIREMENT_CONFIRMED(需求已明确，可规划), PLANNING(规划中), PLANNED(已规划),
        ASSIGNED(已分派), EXECUTING(执行中), COMPLETED(完成), FAILED(失败)

        executionCapability 可选值（必须从中选择一个）：
        WEB_APP_BUILD(网页/Web App/网页游戏/H5), DOCUMENT_GENERATION(文档/方案/报告),
        DATA_ANALYSIS(数据分析), CODE_CHANGE(修改代码/Bug修复), CODE_REVIEW(代码审查),
        ARCHITECTURE_DESIGN(架构设计), RESEARCH_ANALYSIS(调研分析),
        BUSINESS_PLAN(商业方案), CUSTOMER_SUPPORT(客服回复),
        LEGAL_REVIEW(法务审核), FINANCE_ANALYSIS(财务分析),
        HR_WORKFLOW(人事流程), OPERATION_PLAN(运营计划),
        APPROVAL_REQUIRED(需审批), HUMAN_HANDOFF(需人工),
        FILE_SYSTEM_QUERY(文件查询/目录列表/工作目录浏览)

        artifactType 可选值（必须从中选择一个）：
        INTERACTIVE_WEB_PAGE, WEB_PROJECT, DOCUMENT, DATA_REPORT, CODE_PATCH,
        REVIEW_REPORT, ARCHITECTURE_SPEC, BUSINESS_PROPOSAL, SUPPORT_REPLY,
        LEGAL_MEMO, FINANCE_REPORT, HR_DOCUMENT, OPERATION_RUNBOOK,
        APPROVAL_REQUEST, HUMAN_HANDOFF_NOTE, TOOL_RESULT

        executionMode 可选值（必须从中选择一个）：
        ARTIFACT_ONLY(只生成产物), DOCKER_SANDBOX(Docker沙箱执行),
        LOCAL_RESTRICTED(受限本地执行), HUMAN_REVIEW_REQUIRED(需人工审核),
        APPROVAL_REQUIRED(需审批), TOOL_EXECUTION(调用工具直接返回结果), NO_EXECUTION(只回答/澄清)

        可用工具：
        file_edit — 源码文件编辑工具，支持 read_file(读取文件), write_file(写入文件), list_dir(列出目录), search_code(搜索代码)
        当用户请求涉及文件浏览、目录列表、文件内容查看时，必须选择 FILE_SYSTEM_QUERY + TOOL_EXECUTION

        可用部门和员工：
        %s

        注意事项：
        - 不要默认选择技术部，应根据任务内容选择最合适的部门
        - suggestedEmployeeCodes 必须从上面列出的实际员工编码中选择
        - 如果需求不明确，requirementStatus 设为 NEEDS_CLARIFICATION，并在 clarificationQuestions 中列出追问
        - 如果需求已明确，requirementStatus 设为 REQUIREMENT_CONFIRMED，并输出完整计划
        - 网页游戏、H5游戏、前端游戏等必须选 WEB_APP_BUILD
        - roughComplexity 范围 1-5，riskLevel 范围 1-5
        """;

    private static final Map<String, Object> TASK_PLAN_SCHEMA = Map.ofEntries(
        entry("type", "object"),
        entry("properties", Map.ofEntries(
            entry("intent", Map.ofEntries(entry("type", "string"))),
            entry("requirementStatus", Map.ofEntries(entry("type", "string"))),
            entry("requirementSummary", Map.ofEntries(entry("type", "string"))),
            entry("requirementVersion", Map.ofEntries(entry("type", "integer"))),
            entry("clarificationQuestions", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
            entry("taskType", Map.ofEntries(entry("type", "string"))),
            entry("objective", Map.ofEntries(entry("type", "string"))),
            entry("roughComplexity", Map.ofEntries(entry("type", "integer"))),
            entry("riskLevel", Map.ofEntries(entry("type", "integer"))),
            entry("executionCapability", Map.ofEntries(entry("type", "string"))),
            entry("artifactType", Map.ofEntries(entry("type", "string"))),
            entry("executionMode", Map.ofEntries(entry("type", "string"))),
            entry("primaryDepartment", Map.ofEntries(entry("type", "string"))),
            entry("supportingDepartments", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
            entry("deliverables", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
            entry("acceptanceCriteria", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
            entry("requiredSkills", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
            entry("requiredTools", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
            entry("departmentPlans", Map.ofEntries(
                entry("type", "array"),
                entry("items", Map.ofEntries(
                    entry("type", "object"),
                    entry("properties", Map.ofEntries(
                        entry("department", Map.ofEntries(entry("type", "string"))),
                        entry("objective", Map.ofEntries(entry("type", "string"))),
                        entry("suggestedRoles", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
                        entry("suggestedEmployeeCodes", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string")))))
                    ))
                ))
            )),
            entry("nextSteps", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
            entry("summary", Map.ofEntries(entry("type", "string")))
        )),
        entry("required", List.of("intent", "requirementStatus", "primaryDepartment"))
    );

    private final BrainRegistry brainRegistry;
    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final LlmDecisionClient llmDecisionClient;
    private final RuleBasedMainBrainTaskDirector fallbackDirector;
    private final AutonomyTraceService traceService;

    public LlmBasedMainBrainTaskDirector(BrainRegistry brainRegistry, FixedEmployeeRegistry fixedEmployeeRegistry, AutonomyTraceService traceService) {
        this.brainRegistry = brainRegistry;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.llmDecisionClient = null;
        this.fallbackDirector = new RuleBasedMainBrainTaskDirector();
        this.traceService = traceService;
    }

    public LlmBasedMainBrainTaskDirector(BrainRegistry brainRegistry, FixedEmployeeRegistry fixedEmployeeRegistry, AutonomyTraceService traceService, LlmDecisionClient llmDecisionClient) {
        this.brainRegistry = brainRegistry;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.llmDecisionClient = llmDecisionClient;
        this.fallbackDirector = new RuleBasedMainBrainTaskDirector();
        this.traceService = traceService;
    }

    @Override
    public CompletableFuture<MainBrainTaskPlan> planWithKnowledge(
            IntakeClassification intake,
            DialogueDecision decision,
            String userMessage,
            String userId,
            String sessionId,
            String currentDepartment,
            KnowledgeManager knowledgeManager) {

        if (knowledgeManager != null) {
            try {
                List<KnowledgeEntry> relevantKnowledge = knowledgeManager.search(userMessage, 5);
                if (!relevantKnowledge.isEmpty()) {
                    StringBuilder knowledgeContext = new StringBuilder("\n\n【相关知识】\n");
                    for (KnowledgeEntry entry : relevantKnowledge) {
                        knowledgeContext.append("- ").append(entry.getKey()).append(": ")
                            .append(entry.getContent()).append("\n");
                    }
                    String enhancedMessage = userMessage + knowledgeContext;
                    return plan(intake, decision, enhancedMessage, userId, sessionId, currentDepartment);
                }
            } catch (Exception e) {
                log.warn("Knowledge injection failed, proceeding without knowledge: {}", e.getMessage());
            }
        }

        return plan(intake, decision, userMessage, userId, sessionId, currentDepartment);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<MainBrainTaskPlan> plan(
            IntakeClassification intake,
            DialogueDecision decision,
            String userMessage,
            String userId,
            String sessionId,
            String currentDepartment) {

        return CompletableFuture.supplyAsync(() -> {
            String requestId = decision.requestId() != null ? decision.requestId() : UUID.randomUUID().toString();

            Brain mainBrainBrain = brainRegistry.get(MainBrain.ID).orElse(null);
            if (!(mainBrainBrain instanceof MainBrain mainBrain)) {
                log.warn("MainBrain not found or not running, falling back to rule-based director");
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "main_brain_planned", "LlmBasedMainBrainTaskDirector",
                    "MainBrain unavailable, using rule-based fallback",
                    Map.of("reason", "MainBrain not found or not running")
                ));
                return fallbackDirector.plan(intake, decision, userMessage, userId, sessionId, currentDepartment).join();
            }

            try {
                String systemPrompt = buildDynamicSystemPrompt();
                String userPrompt = buildUserPrompt(intake, decision, userMessage, currentDepartment);

                Map<String, Object> parsed;

                if (llmDecisionClient != null) {
                    JsonSchema schema = JsonSchema.of("TaskPlan", TASK_PLAN_SCHEMA);
                    LlmDecisionClient.LlmFallback<Map<String, Object>> fallback = (req, reason) -> null;
                    LlmDecisionRequest<Map<String, Object>> request = LlmDecisionRequest.of(
                        "v1", systemPrompt, userPrompt, null, schema,
                        (Class<Map<String, Object>>) (Class<?>) Map.class,
                        fallback
                    );
                    LlmDecisionResult<Map<String, Object>> result = llmDecisionClient.decideWithRetry(request, 2);
                    if (!result.success() || result.data() == null) {
                        log.warn("LlmDecisionClient failed for task planning: {}, falling back to rule-based", result.error());
                        traceService.recordEvent(AutonomyTraceEvent.of(
                            requestId, "main_brain_planned", "LlmBasedMainBrainTaskDirector",
                            "LlmDecisionClient failed, using rule-based fallback"
                        ));
                        return fallbackDirector.plan(intake, decision, userMessage, userId, sessionId, currentDepartment).join();
                    }
                    parsed = result.data();
                } else {
                    String llmResponse = mainBrain.callLlm(systemPrompt, userPrompt);
                    if (llmResponse == null || llmResponse.isBlank()) {
                        log.warn("LLM returned empty response, falling back to rule-based director");
                        traceService.recordEvent(AutonomyTraceEvent.of(
                            requestId, "main_brain_planned", "LlmBasedMainBrainTaskDirector",
                            "LLM returned empty, using rule-based fallback"
                        ));
                        return fallbackDirector.plan(intake, decision, userMessage, userId, sessionId, currentDepartment).join();
                    }
                    parsed = parseJsonToMap(llmResponse);
                    if (parsed == null) {
                        log.warn("Failed to parse LLM response as task plan, falling back to rule-based");
                        traceService.recordEvent(AutonomyTraceEvent.of(
                            requestId, "main_brain_planned", "LlmBasedMainBrainTaskDirector",
                            "LLM response parse failed, using rule-based fallback"
                        ));
                        return fallbackDirector.plan(intake, decision, userMessage, userId, sessionId, currentDepartment).join();
                    }
                }

                MainBrainTaskPlan llmPlan = buildPlanFromParsed(parsed, requestId, decision, currentDepartment);
                if (llmPlan != null) {
                    log.info("LLM-generated task plan: type={}, primary={}, deliverables={}",
                        llmPlan.taskType(), llmPlan.primaryDepartment(), llmPlan.deliverables().size());
                    traceService.recordEvent(AutonomyTraceEvent.of(
                        requestId, "main_brain_planned", "LlmBasedMainBrainTaskDirector",
                        "LLM-driven task plan generated successfully",
                        Map.of(
                            "director_type", "llm_based",
                            TaskMetadataKeys.TASK_TYPE, llmPlan.taskType(),
                            "primaryDepartment", llmPlan.primaryDepartment()
                        )
                    ));
                    return llmPlan;
                }

                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "main_brain_planned", "LlmBasedMainBrainTaskDirector",
                    "LLM response parse failed, using rule-based fallback"
                ));
                return fallbackDirector.plan(intake, decision, userMessage, userId, sessionId, currentDepartment).join();

            } catch (Exception e) {
                log.error("LLM task planning failed: {}, falling back to rule-based", e.getMessage());
                traceService.recordEvent(AutonomyTraceEvent.of(
                    requestId, "main_brain_planned", "LlmBasedMainBrainTaskDirector",
                    "LLM call failed, using rule-based fallback: " + e.getMessage()
                ));
                return fallbackDirector.plan(intake, decision, userMessage, userId, sessionId, currentDepartment).join();
            }
        });
    }

    private MainBrainTaskPlan buildPlanFromParsed(
            Map<String, Object> parsed, String requestId, DialogueDecision decision, String currentDepartment) {
        try {
            String taskType = getString(parsed, TaskMetadataKeys.TASK_TYPE, "general_task");
            String intent = getString(parsed, "intent", decision.intent());
            String objective = getString(parsed, "objective", decision.intent());
            String goal = objective != null && !objective.isBlank() ? objective : decision.intent();
            String primaryDepartment = getString(parsed, "primaryDepartment", currentDepartment != null ? currentDepartment : "main");
            if ("null".equals(primaryDepartment)) primaryDepartment = currentDepartment != null ? currentDepartment : "main";
            List<String> supportingDepartments = getStringList(parsed, "supportingDepartments");
            List<String> deliverables = getStringList(parsed, "deliverables");
            List<String> acceptanceCriteria = getStringList(parsed, "acceptanceCriteria");
            List<String> requiredSkills = getStringList(parsed, "requiredSkills");
            List<String> requiredTools = getStringList(parsed, "requiredTools");
            List<String> clarificationQuestions = getStringList(parsed, "clarificationQuestions");

            // 解析需求状态
            RequirementStatus requirementStatus = parseRequirementStatus(getString(parsed, "requirementStatus", null));
            String requirementSummary = getString(parsed, "requirementSummary", goal);
            int requirementVersion = getInt(parsed, "requirementVersion", 1);

            // 解析复杂度和风险等级
            int roughComplexity = getInt(parsed, "roughComplexity", decision.complexity());
            int riskLevel = getInt(parsed, "riskLevel", decision.riskLevel());

            // P0-5 新增：解析 executionCapability/artifactType/executionMode
            ExecutionCapability executionCapability = parseEnum(getString(parsed, "executionCapability", null), ExecutionCapability.class);
            ArtifactType artifactType = parseEnum(getString(parsed, "artifactType", null), ArtifactType.class);
            ExecutionMode executionMode = parseEnum(getString(parsed, "executionMode", null), ExecutionMode.class);

            List<DepartmentTaskPlan> departmentPlans = parseDepartmentPlans(parsed, deliverables, acceptanceCriteria, currentDepartment);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("director_type", "llm_based_main_brain_director");
            metadata.put("source_intent", decision.intent());
            metadata.put("nextSteps", getStringList(parsed, "nextSteps"));
            metadata.put("summary", getString(parsed, "summary", ""));

            return new MainBrainTaskPlan(
                requestId + "-plan", requestId, null, null,
                requirementStatus, requirementVersion, requirementSummary,
                taskType, intent, null, goal, roughComplexity, riskLevel,
                executionCapability, artifactType, executionMode,
                primaryDepartment, supportingDepartments,
                deliverables, acceptanceCriteria, requiredSkills, requiredTools,
                departmentPlans, List.of(), null,
                clarificationQuestions, null,
                metadata,
                java.time.Instant.now(), null, java.time.Instant.now(), null, null
            );
        } catch (Exception e) {
            log.warn("Failed to build MainBrainTaskPlan from parsed LLM response: {}", e.getMessage());
            return null;
        }
    }

    // P0-5 改进：默认值改为 NEEDS_CLARIFICATION（更安全）
    private RequirementStatus parseRequirementStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) return RequirementStatus.NEEDS_CLARIFICATION;
        try {
            return RequirementStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return RequirementStatus.NEEDS_CLARIFICATION;
        }
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String buildDynamicSystemPrompt() {
        StringBuilder employeeList = new StringBuilder();
        fixedEmployeeRegistry.getAllDefinitions().stream()
            .map(d -> d.department())
            .distinct()
            .sorted()
            .forEach(dept -> {
                employeeList.append(dept).append(":\n");
                fixedEmployeeRegistry.getDefinitionsByDepartment(dept).forEach(d -> {
                    employeeList.append("  - ").append(d.code()).append(": ").append(d.name())
                        .append(" (").append(d.title()).append(")")
                        .append(" 能力: ").append(String.join(",", d.capabilities()))
                        .append(" 工具: ").append(String.join(",", d.tools()))
                        .append("\n");
                });
            });
        return String.format(TASK_PLAN_SYSTEM_PROMPT_TEMPLATE, employeeList.toString());
    }

    private String buildUserPrompt(IntakeClassification intake, DialogueDecision decision, String userMessage, String currentDepartment) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前用户所在部门: ").append(currentDepartment != null ? currentDepartment : "未知").append("\n");
        sb.append("消息分类: ").append(intake.kind().name()).append("\n");
        sb.append("识别的意图: ").append(intake.roughIntent()).append("\n");
        sb.append("复杂度: ").append(intake.roughComplexity()).append("\n");
        sb.append("可能需要跨部门: ").append(intake.likelyCrossDepartment()).append("\n");
        if (decision.riskLevel() > 3) sb.append("风险等级: ").append(decision.riskLevel()).append("\n");
        if (decision.requiresClarification() && decision.clarificationQuestions() != null && !decision.clarificationQuestions().isEmpty())
            sb.append("需要追问: ").append(String.join("；", decision.clarificationQuestions())).append("\n");
        sb.append("用户消息: ").append(userMessage);
        return sb.toString();
    }

    private List<DepartmentTaskPlan> parseDepartmentPlans(Map<String, Object> parsed, List<String> defaultDeliverables, List<String> defaultAcceptanceCriteria, String currentDepartment) {
        List<DepartmentTaskPlan> plans = new ArrayList<>();
        Object dpObj = parsed.get("departmentPlans");
        if (dpObj instanceof List<?> dpList) {
            for (Object item : dpList) {
                if (item instanceof Map<?, ?> dpMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dp = (Map<String, Object>) dpMap;
                    String dept = getString(dp, "department", currentDepartment != null ? currentDepartment : "main");
                    String objective = getString(dp, "objective", "完成部门任务");
                    List<String> roles = getStringList(dp, "suggestedRoles");
                    List<String> codes = getStringList(dp, "suggestedEmployeeCodes");
                    plans.add(DepartmentTaskPlan.of(dept, objective, roles, codes, defaultDeliverables, defaultAcceptanceCriteria));
                }
            }
        }
        if (plans.isEmpty()) {
            String fallbackDept = currentDepartment != null ? currentDepartment : "main";
            plans.add(DepartmentTaskPlan.of(fallbackDept, "完成部门任务", List.of(), List.of(), defaultDeliverables, defaultAcceptanceCriteria));
        }
        return plans;
    }

    private Map<String, Object> parseJsonToMap(String response) {
        if (response == null) return null;
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf("\n") + 1;
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) trimmed = trimmed.substring(start, end).trim();
        } else if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n") + 1;
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) trimmed = trimmed.substring(start, end).trim();
        }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            try { return objectMapper.readValue(trimmed.substring(braceStart, braceEnd + 1), Map.class); }
            catch (Exception e) { return null; }
        }
        return null;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return List.of();
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Cannot parse '{}' as {}, ignoring", value, enumClass.getSimpleName());
            return null;
        }
    }
}
