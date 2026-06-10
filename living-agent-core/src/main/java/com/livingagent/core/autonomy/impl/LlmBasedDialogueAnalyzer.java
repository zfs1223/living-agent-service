package com.livingagent.core.autonomy.impl;

import com.livingagent.core.autonomy.DialogueAnalyzer;
import com.livingagent.core.autonomy.DialogueDecision;
import com.livingagent.core.autonomy.DialogueDecision.MessageKind;
import com.livingagent.core.autonomy.context.DecisionContext;
import com.livingagent.core.autonomy.context.DecisionContextBuilder;
import com.livingagent.core.autonomy.llm.LlmDecisionClient;
import com.livingagent.core.autonomy.llm.LlmDecisionClient.LlmDecisionRequest;
import com.livingagent.core.autonomy.llm.LlmDecisionClient.LlmDecisionResult;
import com.livingagent.core.autonomy.llm.LlmDecisionClient.JsonSchema;
import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.employee.registry.FixedEmployeeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Map.entry;

public class LlmBasedDialogueAnalyzer implements DialogueAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(LlmBasedDialogueAnalyzer.class);

    private static final String ANALYSIS_SYSTEM_PROMPT_TEMPLATE = """
        你是一个企业的消息分类器，负责对用户输入进行快速分类和分析。
        
        你的职责是：
        1. 将消息分类为以下类型之一：
           - CHAT: 闲聊、问候、无关话题
           - TASK: 明确的可执行任务请求
           - PROJECT: 需要多阶段推进的项目计划
           - APPROVAL: 需要审批的请求（报销、请假、预算等）
           - CONSULTATION: 业务咨询（需要专业知识但不一定执行任务）
           - CROSS_DEPARTMENT: 明确涉及多个部门的协调需求
           - KNOWLEDGE: 知识查询（制度、流程、规范等）
        2. 识别用户的具体意图
        3. 评估任务复杂度（1-5）
        4. 评估风险等级（1-5）及风险原因
           风险等级判定标准：
           - 1-2（低风险）：知识查询、项目评估、文档生成、数据分析、咨询建议、代码审查、常规开发任务
           - 3（中等风险）：涉及生产环境变更、数据迁移、架构重构、批量操作
           - 4-5（高风险）：仅限以下场景：删除生产数据、修改权限体系、财务付款执行、合同签署、安全漏洞修复
           注意：绝大多数用户请求应为 1-2 级，不要轻易给出 3 以上评级
        5. 判断是否涉及跨部门协调
        6. 推荐主责部门
        7. 判断信息是否充分，是否需要追问
        
        你必须只输出一个合法的JSON对象，不要包含任何其他文字、解释或markdown标记。
        JSON结构如下：
        {
          "kind": "TASK",
          "intent": "web_development",
          "primaryDepartment": "tech",
          "complexity": 3,
          "riskLevel": 1,
          "riskReasons": [],
          "crossDepartment": false,
          "supportingDepartments": [],
          "requiresClarification": false,
          "clarificationQuestion": null,
          "decisionReason": "用户请求开发网页，属于技术部职责"
        }
        
        可用部门：
        %s
        
        注意事项：
        - 如果用户意图不明确或缺少关键信息，设置 requiresClarification=true 并给出追问
        - 不要默认选择技术部，应根据用户请求内容选择最合适的部门
        - 如果无法确定部门，primaryDepartment 设为 null
        """;

    private static final Map<String, Object> ANALYSIS_SCHEMA = Map.ofEntries(
        entry("type", "object"),
        entry("properties", Map.ofEntries(
            entry("kind", Map.ofEntries(entry("type", "string"), entry("enum", List.of("CHAT", "TASK", "PROJECT", "APPROVAL", "CONSULTATION", "CROSS_DEPARTMENT", "KNOWLEDGE")))),
            entry("intent", Map.ofEntries(entry("type", "string"))),
            entry("primaryDepartment", Map.ofEntries(entry("type", "string"))),
            entry("complexity", Map.ofEntries(entry("type", "integer"), entry("minimum", 1), entry("maximum", 5))),
            entry("riskLevel", Map.ofEntries(entry("type", "integer"), entry("minimum", 1), entry("maximum", 5))),
            entry("riskReasons", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
            entry("crossDepartment", Map.ofEntries(entry("type", "boolean"))),
            entry("supportingDepartments", Map.ofEntries(entry("type", "array"), entry("items", Map.ofEntries(entry("type", "string"))))),
            entry("requiresClarification", Map.ofEntries(entry("type", "boolean"))),
            entry("clarificationQuestion", Map.ofEntries(entry("type", "string"))),
            entry("decisionReason", Map.ofEntries(entry("type", "string")))
        )),
        entry("required", List.of("kind", "intent", "complexity", "riskLevel"))
    );

    private final BrainRegistry brainRegistry;
    private final FixedEmployeeRegistry fixedEmployeeRegistry;
    private final LlmDecisionClient llmDecisionClient;
    private final DecisionContextBuilder decisionContextBuilder;
    private final RuleBasedDialogueAnalyzer fallbackAnalyzer;

    public LlmBasedDialogueAnalyzer(BrainRegistry brainRegistry, FixedEmployeeRegistry fixedEmployeeRegistry) {
        this.brainRegistry = brainRegistry;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.llmDecisionClient = null;
        this.decisionContextBuilder = null;
        this.fallbackAnalyzer = new RuleBasedDialogueAnalyzer();
    }

    public LlmBasedDialogueAnalyzer(BrainRegistry brainRegistry, FixedEmployeeRegistry fixedEmployeeRegistry, LlmDecisionClient llmDecisionClient) {
        this.brainRegistry = brainRegistry;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.llmDecisionClient = llmDecisionClient;
        this.decisionContextBuilder = null;
        this.fallbackAnalyzer = new RuleBasedDialogueAnalyzer();
    }

    public LlmBasedDialogueAnalyzer(BrainRegistry brainRegistry, FixedEmployeeRegistry fixedEmployeeRegistry, LlmDecisionClient llmDecisionClient, DecisionContextBuilder decisionContextBuilder) {
        this.brainRegistry = brainRegistry;
        this.fixedEmployeeRegistry = fixedEmployeeRegistry;
        this.llmDecisionClient = llmDecisionClient;
        this.decisionContextBuilder = decisionContextBuilder;
        this.fallbackAnalyzer = new RuleBasedDialogueAnalyzer();
    }

    @Override
    @SuppressWarnings("unchecked")
    public DialogueDecision analyze(String message, String userId, String department, String sessionId) {
        String requestId = UUID.randomUUID().toString();

        Brain mainBrainBrain = brainRegistry.get(MainBrain.ID).orElse(null);
        if (!(mainBrainBrain instanceof MainBrain mainBrain)) {
            log.debug("MainBrain not available for dialogue analysis, using rule-based fallback");
            return fallbackWithTrace(fallbackAnalyzer.analyze(message, userId, department, sessionId),
                "MainBrain unavailable, using rule-based fallback");
        }

        try {
            String systemPrompt = buildDynamicSystemPrompt();
            String userPrompt = buildAnalysisPrompt(message, department);

            Map<String, Object> parsed;

            if (llmDecisionClient != null) {
                JsonSchema schema = JsonSchema.of("DialogueAnalysis", ANALYSIS_SCHEMA);
                LlmDecisionClient.LlmFallback<Map<String, Object>> fallback = (req, reason) -> Map.of(
                    "kind", "CHAT", "intent", "general_chat",
                    "primaryDepartment", department != null ? department : "main",
                    "complexity", 1, "riskLevel", 1
                );
                DecisionContext context = decisionContextBuilder != null
                    ? decisionContextBuilder.build(message, userId, sessionId, department)
                    : null;
                LlmDecisionRequest<Map<String, Object>> request = LlmDecisionRequest.of(
                    "v1", systemPrompt, userPrompt, context, schema,
                    (Class<Map<String, Object>>) (Class<?>) Map.class,
                    fallback
                );
                LlmDecisionResult<Map<String, Object>> result = llmDecisionClient.decideWithRetry(request, 2);
                if (!result.success()) {
                    log.debug("LlmDecisionClient failed for dialogue analysis: {}, using rule-based fallback", result.error());
                    return fallbackWithTrace(fallbackAnalyzer.analyze(message, userId, department, sessionId),
                        "LlmDecisionClient failed: " + result.error());
                }
                parsed = result.data();
            } else {
                String llmResponse = mainBrain.callLlm(systemPrompt, userPrompt);
                if (llmResponse == null || llmResponse.isBlank()) {
                    return fallbackWithTrace(fallbackAnalyzer.analyze(message, userId, department, sessionId),
                        "LLM returned empty, using rule-based fallback");
                }
                parsed = parseJsonToMap(llmResponse);
                if (parsed == null) {
                    return fallbackWithTrace(fallbackAnalyzer.analyze(message, userId, department, sessionId),
                        "LLM response parse failed, using rule-based fallback");
                }
            }

            DialogueDecision decision = buildDecisionFromParsed(parsed, requestId, userId, sessionId, message, department);
            if (decision != null) {
                log.debug("LLM-based dialogue analysis: kind={}, intent={}, primaryDept={}, complexity={}",
                    decision.kind(), decision.intent(), decision.primaryDepartment(), decision.complexity());
                return decision;
            }

            return fallbackWithTrace(fallbackAnalyzer.analyze(message, userId, department, sessionId),
                "LLM response parse failed, using rule-based fallback");

        } catch (Exception e) {
            log.warn("LLM dialogue analysis failed: {}, using rule-based fallback", e.getMessage());
            return fallbackWithTrace(fallbackAnalyzer.analyze(message, userId, department, sessionId),
                "LLM call failed: " + e.getMessage() + ", using rule-based fallback");
        }
    }

    private DialogueDecision buildDecisionFromParsed(
            Map<String, Object> parsed, String requestId, String userId,
            String sessionId, String message, String currentDepartment) {
        try {
            String kindStr = getString(parsed, "kind", "CHAT");
            MessageKind kind = parseKind(kindStr);
            String intent = getString(parsed, "intent", "general_chat");
            String primaryDepartment = getString(parsed, "primaryDepartment", currentDepartment);
            if ("null".equals(primaryDepartment)) primaryDepartment = currentDepartment;
            int complexity = getInt(parsed, "complexity", 3);
            int riskLevel = getInt(parsed, "riskLevel", 1);
            boolean crossDepartment = getBoolean(parsed, "crossDepartment", false);
            List<String> supportingDepartments = getStringList(parsed, "supportingDepartments");
            boolean requiresClarification = getBoolean(parsed, "requiresClarification", false);
            String clarificationQuestion = getString(parsed, "clarificationQuestion", null);
            String decisionReason = getString(parsed, "decisionReason", null);
            List<String> riskReasons = getStringList(parsed, "riskReasons");

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("analyzer_type", "llm_based");
            if (decisionReason != null) metadata.put("decisionReason", decisionReason);
            if (!riskReasons.isEmpty()) metadata.put("riskReasons", riskReasons);

            boolean requiresTaskExecution = kind == MessageKind.TASK || kind == MessageKind.PROJECT
                || kind == MessageKind.APPROVAL || kind == MessageKind.CROSS_DEPARTMENT;

            return new DialogueDecision(
                requestId,
                sessionId,
                userId,
                message,
                kind,
                intent,
                primaryDepartment != null ? primaryDepartment : currentDepartment,
                mapDepartmentToBrain(primaryDepartment != null ? primaryDepartment : currentDepartment),
                supportingDepartments,
                requiresTaskExecution,
                requiresClarification,
                clarificationQuestion,
                Math.min(Math.max(complexity, 1), 5),
                Math.min(Math.max(riskLevel, 1), 5),
                Map.copyOf(metadata)
            );
        } catch (Exception e) {
            log.warn("Failed to build DialogueDecision from parsed LLM response: {}", e.getMessage());
            return null;
        }
    }

    private String buildDynamicSystemPrompt() {
        StringBuilder deptList = new StringBuilder();
        fixedEmployeeRegistry.getAllDefinitions().stream()
            .map(d -> d.department())
            .distinct()
            .sorted()
            .forEach(dept -> {
                long count = fixedEmployeeRegistry.getDefinitionsByDepartment(dept).size();
                String names = fixedEmployeeRegistry.getDefinitionsByDepartment(dept).stream()
                    .map(d -> d.code() + ":" + d.name() + "(" + d.title() + ")")
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
                deptList.append("- ").append(dept).append(" (").append(count).append("名员工): ")
                    .append(names).append("\n");
            });

        return String.format(ANALYSIS_SYSTEM_PROMPT_TEMPLATE, deptList.toString());
    }

    private String buildAnalysisPrompt(String message, String department) {
        return String.format("当前用户所在部门: %s\n用户消息: %s",
            department != null ? department : "未知",
            message != null ? message : "");
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
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    trimmed.substring(braceStart, braceEnd + 1), Map.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private DialogueDecision fallbackWithTrace(DialogueDecision decision, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>(decision.metadata());
        metadata.put("analyzer_type", "rule_based_fallback");
        metadata.put("fallback_reason", reason);
        return new DialogueDecision(
            decision.requestId(), decision.sessionId(), decision.userId(),
            decision.originalMessage(), decision.kind(), decision.intent(),
            decision.primaryDepartment(), decision.primaryBrainId(),
            decision.supportingDepartments(), decision.requiresTaskExecution(),
            decision.requiresClarification(), decision.clarificationQuestion(),
            decision.complexity(), decision.riskLevel(), Map.copyOf(metadata)
        );
    }

    private MessageKind parseKind(String kindStr) {
        try { return MessageKind.valueOf(kindStr.toUpperCase()); }
        catch (IllegalArgumentException e) { return MessageKind.CHAT; }
    }

    private String mapDepartmentToBrain(String department) {
        if (department == null) return "MainBrain";
        Optional<Brain> match = brainRegistry.getAll().stream()
            .filter(b -> b.getDepartment() != null && b.getDepartment().equalsIgnoreCase(department))
            .findFirst();
        if (match.isPresent()) return match.get().getId();
        return switch (department.toLowerCase()) {
            case "tech" -> "TechBrain";
            case "hr" -> "HrBrain";
            case "finance" -> "FinanceBrain";
            case "sales" -> "SalesBrain";
            case "cs" -> "CsBrain";
            case "legal" -> "LegalBrain";
            case "admin" -> "AdminBrain";
            case "ops" -> "OpsBrain";
            case "main", "core" -> "MainBrain";
            default -> department + "Brain";
        };
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String str) {
            try { return Integer.parseInt(str); }
            catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String str) return Boolean.parseBoolean(str);
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return List.of();
    }
}
