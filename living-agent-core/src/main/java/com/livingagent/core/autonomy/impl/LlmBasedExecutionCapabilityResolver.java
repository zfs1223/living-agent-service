package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.brain.Brain;
import com.livingagent.core.brain.BrainRegistry;
import com.livingagent.core.brain.impl.MainBrain;
import com.livingagent.core.autonomy.llm.LlmDecisionClient;
import com.livingagent.core.autonomy.llm.LlmDecisionClient.LlmDecisionRequest;
import com.livingagent.core.autonomy.llm.LlmDecisionClient.LlmDecisionResult;
import com.livingagent.core.autonomy.llm.LlmDecisionClient.LlmFallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 LLM 的执行能力解析器。
 * 策略：LLM 语义判断优先 → 规则兜底 → 枚举校验 → 置信度检查。
 *
 * 当 LLM 不可用或返回无效结果时，自动降级到 DefaultExecutionCapabilityResolver。
 */
public class LlmBasedExecutionCapabilityResolver implements ExecutionCapabilityResolver {

    private static final Logger log = LoggerFactory.getLogger(LlmBasedExecutionCapabilityResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BrainRegistry brainRegistry;
    private final DefaultExecutionCapabilityResolver fallback;
    private final LlmDecisionClient llmDecisionClient;

    private static final String SYSTEM_PROMPT = """
        你是执行能力分类器。根据用户的任务描述，判断需要哪种执行能力。

        可选的执行能力（ExecutionCapability）及其含义：
        - WEB_APP_BUILD: 网页、Web App、网页游戏、H5、小工具页面
        - DOCUMENT_GENERATION: 文档、方案、报告、SOP、说明书
        - DATA_ANALYSIS: 表格、指标、趋势、数据诊断
        - CODE_CHANGE: 修改已有代码、修 Bug、加接口
        - CODE_REVIEW: 代码审查、质量检查、安全检查
        - ARCHITECTURE_DESIGN: 架构设计、技术方案、模块拆分
        - RESEARCH_ANALYSIS: 调研、竞品分析、资料整理
        - BUSINESS_PLAN: 商业方案、销售方案、运营方案
        - CUSTOMER_SUPPORT: 客服回复、工单处理、FAQ
        - LEGAL_REVIEW: 合同、合规、法律风险审查
        - FINANCE_ANALYSIS: 财务分析、预算、成本、报销判断
        - HR_WORKFLOW: 招聘、绩效、人事流程
        - OPERATION_PLAN: 运营活动、流程优化、排期计划
        - FILE_SYSTEM_QUERY: 文件系统查询、目录列表、文件读取

        请严格按以下 JSON 格式返回：
        {
          "executionCapability": "枚举值",
          "artifactType": "CODE | DOCUMENT | DATA | DESIGN | REPORT | OTHER",
          "executionMode": "AUTONOMOUS | SEMI_AUTONOMOUS | ASSISTED | MANUAL",
          "confidence": 0.0-1.0,
          "reason": "判断理由"
        }

        如果无法确定，将 confidence 设为 0.3 以下，并附上 reason 说明。
        """;

    public LlmBasedExecutionCapabilityResolver(BrainRegistry brainRegistry) {
        this.brainRegistry = brainRegistry;
        this.fallback = new DefaultExecutionCapabilityResolver();
        this.llmDecisionClient = null;
    }

    public LlmBasedExecutionCapabilityResolver(BrainRegistry brainRegistry, LlmDecisionClient llmDecisionClient) {
        this.brainRegistry = brainRegistry;
        this.fallback = new DefaultExecutionCapabilityResolver();
        this.llmDecisionClient = llmDecisionClient;
    }

    @Override
    public ExecutionCapabilityResolution resolve(ExecutionCapabilityRequest request) {
        // 1. 先尝试规则匹配（快速路径，无需 LLM 调用）
        ExecutionCapabilityResolution ruleResult = fallback.resolve(request);
        if (ruleResult.confidence() >= 0.85 && !ruleResult.requiresClarification()) {
            log.debug("Rule-based resolution sufficient: capability={}, confidence={}",
                ruleResult.executionCapability(), ruleResult.confidence());
            return ruleResult;
        }

        // 2. 规则置信度不足，尝试 LLM 语义判断
        try {
            ExecutionCapabilityResolution llmResult = resolveWithLlm(request);
            if (llmResult != null && llmResult.confidence() >= 0.6) {
                log.info("LLM resolution succeeded: capability={}, confidence={}, ruleConfidence={}",
                    llmResult.executionCapability(), llmResult.confidence(), ruleResult.confidence());
                return llmResult;
            }
            if (llmResult != null) {
                log.debug("LLM confidence too low: {}, falling back to rule result", llmResult.confidence());
            }
        } catch (Exception e) {
            log.warn("LLM resolution failed, falling back to rule-based: {}", e.getMessage());
        }

        // 3. LLM 失败或置信度不足，返回规则结果
        return ruleResult;
    }

    private ExecutionCapabilityResolution resolveWithLlm(ExecutionCapabilityRequest request) {
        String userPrompt = buildUserPrompt(request);

        // 优先使用 LlmDecisionClient（结构化输出）
        if (llmDecisionClient != null) {
            return resolveWithDecisionClient(request, userPrompt);
        }

        // 回退到 MainBrain.callLlm()
        return resolveWithMainBrain(request, userPrompt);
    }

    private ExecutionCapabilityResolution resolveWithDecisionClient(ExecutionCapabilityRequest request, String userPrompt) {
        @SuppressWarnings("unchecked")
        LlmDecisionRequest<Map<String, Object>> decisionRequest = new LlmDecisionRequest<>(
            "execution-capability-resolver-v1",
            SYSTEM_PROMPT,
            userPrompt,
            null,
            null,
            (Class<Map<String, Object>>)(Class<?>)Map.class,
            (req, reason) -> null
        );

        LlmDecisionResult<Map<String, Object>> result = llmDecisionClient.decideWithRetry(decisionRequest, 2);
        if (result.success() && result.data() != null) {
            return parseLlmResponse(result.data());
        }
        log.debug("LlmDecisionClient failed: {}", result.error());
        return null;
    }

    private ExecutionCapabilityResolution resolveWithMainBrain(ExecutionCapabilityRequest request, String userPrompt) {
        Optional<Brain> mainBrainOpt = brainRegistry != null ? brainRegistry.get(MainBrain.ID) : Optional.empty();
        if (mainBrainOpt.isEmpty() || !(mainBrainOpt.get() instanceof MainBrain mainBrain)) {
            log.debug("MainBrain not available for LLM resolution");
            return null;
        }

        String rawResponse = mainBrain.callLlm(SYSTEM_PROMPT, userPrompt);
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> parsed = parseJsonToMap(rawResponse);
            return parseLlmResponse(parsed);
        } catch (Exception e) {
            log.warn("Failed to parse MainBrain LLM response: {}", e.getMessage());
            return null;
        }
    }

    private String buildUserPrompt(ExecutionCapabilityRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下任务并判断执行能力：\n\n");
        if (request.userMessage() != null) sb.append("用户消息: ").append(request.userMessage()).append("\n");
        if (request.taskType() != null) sb.append("任务类型: ").append(request.taskType()).append("\n");
        if (request.intent() != null) sb.append("意图: ").append(request.intent()).append("\n");
        if (request.deliverables() != null && !request.deliverables().isEmpty())
            sb.append("交付物: ").append(String.join(", ", request.deliverables())).append("\n");
        if (request.requiredSkills() != null && !request.requiredSkills().isEmpty())
            sb.append("所需技能: ").append(String.join(", ", request.requiredSkills())).append("\n");
        if (request.department() != null) sb.append("部门: ").append(request.department()).append("\n");
        return sb.toString();
    }

    private ExecutionCapabilityResolution parseLlmResponse(Map<String, Object> parsed) {
        try {
            String capabilityStr = (String) parsed.get("executionCapability");
            String artifactTypeStr = (String) parsed.get("artifactType");
            String executionModeStr = (String) parsed.get("executionMode");
            double confidence = parsed.get("confidence") instanceof Number n ? n.doubleValue() : 0.5;
            String reason = (String) parsed.get("reason");

            ExecutionCapability capability = parseEnum(capabilityStr, ExecutionCapability.class);
            ArtifactType artifactType = parseEnum(artifactTypeStr, ArtifactType.class);
            ExecutionMode executionMode = parseEnum(executionModeStr, ExecutionMode.class);

            if (capability == null) {
                log.debug("LLM returned unknown capability: {}", capabilityStr);
                return null;
            }

            return ExecutionCapabilityResolution.resolved(
                capability,
                artifactType != null ? artifactType : ArtifactType.DOCUMENT,
                executionMode != null ? executionMode : ExecutionMode.LOCAL_RESTRICTED,
                confidence,
                reason != null ? reason : "LLM classification"
            );
        } catch (Exception e) {
            log.warn("Failed to parse LLM response map: {}", e.getMessage());
            return null;
        }
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass) {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // 尝试模糊匹配
            for (T constant : enumClass.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(value.trim())) {
                    return constant;
                }
            }
            return null;
        }
    }

    private Map<String, Object> parseJsonToMap(String raw) {
        String json = raw.trim();
        // 剥离 markdown 代码块标记
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastBacktick = json.lastIndexOf("```");
            if (firstNewline > 0 && lastBacktick > firstNewline) {
                json = json.substring(firstNewline + 1, lastBacktick).trim();
            }
        }
        // 提取 JSON 对象
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
