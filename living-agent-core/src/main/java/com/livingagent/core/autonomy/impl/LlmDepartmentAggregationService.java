package com.livingagent.core.autonomy.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.autonomy.*;
import com.livingagent.core.brain.impl.MainBrain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * LLM 驾动的部门级聚合服务实现。
 *
 * <p>在规则版 DefaultDepartmentAggregationService 基础上增加语义理解能力：
 * - 一致性检查：LLM 分析前后端接口是否对齐、文档与代码是否匹配
 * - 质量评估：LLM 语义评估成果质量
 * - 问题发现：LLM 发现潜在问题和不一致
 * - 修复建议：LLM 生成修复建议
 *
 * <p>当 LLM 调用失败时，自动降级到规则版结果。
 *
 * <p>此类不使用 @Service 注解，而是在 GatewayConfig 中显式注册，
 * 以便根据 MainBrain 可用性决定是否使用 LLM 增强版。
 */
public class LlmDepartmentAggregationService implements DepartmentAggregationService {

    private static final Logger log = LoggerFactory.getLogger(LlmDepartmentAggregationService.class);

    private static final String AGGREGATION_SYSTEM_PROMPT = """
你是一个部门级成果聚合分析器。请分析以下部门交付物，判断：

1. 完整性：所有子任务是否真正完成（不仅看状态，还要看内容）
2. 一致性：各交付物之间是否一致
   - 技术部门：前端和后端接口是否对齐
   - 文档部门：文档和代码是否匹配
   - 财务部门：财务数据和报告是否一致
3. 质量：整体质量评分（0-1.0）
4. 问题：发现的潜在问题
5. 建议：修复建议

请以JSON格式返回，不要包含其他内容：
{
  "consistency_check": "PASSED" 或 "ISSUES_FOUND",
  "consistency_issues": ["问题1", "问题2"],
  "quality_score": 0.85,
  "potential_issues": ["潜在问题1"],
  "fix_suggestions": ["建议1"],
  "overall_assessment": "总体评价"
}
""";

    private final DepartmentAggregationService fallback;
    private final MainBrain mainBrain;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, DepartmentDeliverable> deliverablesById = new ConcurrentHashMap<>();

    public LlmDepartmentAggregationService(
            DepartmentAggregationService fallback,
            @Lazy MainBrain mainBrain) {
        this.fallback = fallback;
        this.mainBrain = mainBrain;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AggregationResult aggregate(String department, String planId, String objective) {
        log.info("LLM aggregation started: department={}, planId={}", department, planId);

        // 1. 先用规则版完成基础聚合
        AggregationResult ruleResult = fallback.aggregate(department, planId, objective);
        if (!ruleResult.success()) {
            log.info("Rule-based aggregation failed/incomplete, skipping LLM analysis: status={}",
                ruleResult.deliverable() != null ? ruleResult.deliverable().status() : "null");
            return ruleResult;  // 基础聚合未通过，无需 LLM 分析
        }

        // 2. LLM 语义分析
        try {
            DepartmentDeliverable deliverable = ruleResult.deliverable();
            String llmAnalysis = callLlmForAggregation(department, objective, deliverable);

            if (llmAnalysis == null || llmAnalysis.isBlank()) {
                log.warn("LLM aggregation analysis returned empty, using rule-based result");
                return ruleResult;
            }

            // 3. 解析 LLM 结果，更新聚合状态
            AggregationResult enhancedResult = parseAndUpdateResult(ruleResult, llmAnalysis, department, planId, objective);

            log.info("LLM aggregation completed: department={}, enhancedQuality={}",
                department, enhancedResult.deliverable() != null ? enhancedResult.deliverable().overallQualityScore() : "null");

            return enhancedResult;
        } catch (Exception e) {
            log.warn("LLM aggregation analysis failed, using rule-based result: {}", e.getMessage());
            return ruleResult;  // Fallback 到规则版结果
        }
    }

    /**
     * 调用 LLM 进行聚合分析。
     */
    private String callLlmForAggregation(String department, String objective, DepartmentDeliverable deliverable) {
        // 构建交付物摘要
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("部门：").append(department).append("\n");
        userPrompt.append("目标：").append(objective).append("\n");
        userPrompt.append("交付物列表：\n");

        if (deliverable.items() != null && !deliverable.items().isEmpty()) {
            for (DepartmentDeliverable.DeliverableItem item : deliverable.items()) {
                userPrompt.append("- 员工: ").append(item.employeeCode());
                userPrompt.append(", 任务类型: ").append(item.taskType());
                userPrompt.append(", 摘要: ").append(item.summary());
                userPrompt.append(", 审查通过: ").append(item.reviewPassed());
                userPrompt.append(", 质量分: ").append(String.format("%.2f", item.qualityScore()));
                if (item.artifactPaths() != null && !item.artifactPaths().isEmpty()) {
                    userPrompt.append(", 产物: ").append(String.join(", ", item.artifactPaths()));
                }
                userPrompt.append("\n");
            }
        } else {
            userPrompt.append("（无交付物）\n");
        }

        userPrompt.append("\n整体质量分（规则版）：").append(String.format("%.2f", deliverable.overallQualityScore()));
        userPrompt.append("\n状态：").append(deliverable.status());

        // 调用 LLM
        try {
            String response = mainBrain.callLlm(AGGREGATION_SYSTEM_PROMPT, userPrompt.toString(), 1024, 0.3);
            return response;
        } catch (Exception e) {
            log.warn("LLM call failed for aggregation analysis: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 LLM 结果，更新聚合状态。
     */
    private AggregationResult parseAndUpdateResult(AggregationResult ruleResult, String llmAnalysis,
                                                     String department, String planId, String objective) {
        try {
            // 尝试解析 JSON
            JsonNode jsonNode = objectMapper.readTree(llmAnalysis);

            // 提取字段
            String consistencyCheck = jsonNode.has("consistency_check")
                ? jsonNode.get("consistency_check").asText() : "PASSED";
            List<String> consistencyIssues = extractList(jsonNode, "consistency_issues");
            double qualityScore = jsonNode.has("quality_score")
                ? jsonNode.get("quality_score").asDouble() : ruleResult.deliverable().overallQualityScore();
            List<String> potentialIssues = extractList(jsonNode, "potential_issues");
            List<String> fixSuggestions = extractList(jsonNode, "fix_suggestions");
            String overallAssessment = jsonNode.has("overall_assessment")
                ? jsonNode.get("overall_assessment").asText() : "";

            // 合并问题列表
            List<String> allIssues = new ArrayList<>();
            if (ruleResult.qualityIssues() != null) {
                allIssues.addAll(ruleResult.qualityIssues());
            }
            allIssues.addAll(consistencyIssues);
            allIssues.addAll(potentialIssues);

            // 确定最终状态
            DepartmentDeliverable.AggregationStatus finalStatus = ruleResult.deliverable().status();
            if ("ISSUES_FOUND".equals(consistencyCheck) && !consistencyIssues.isEmpty()) {
                finalStatus = DepartmentDeliverable.AggregationStatus.QUALITY_ISSUES;
            }

            // 构建增强后的交付物
            String deliverableId = "deliverable-llm-" + department + "-" + System.currentTimeMillis();
            String enhancedSummary = ruleResult.deliverable().summary();
            if (!overallAssessment.isBlank()) {
                enhancedSummary = enhancedSummary + "\nLLM评估: " + overallAssessment;
            }

            DepartmentDeliverable enhancedDeliverable = new DepartmentDeliverable(
                deliverableId,
                department,
                planId,
                objective,
                finalStatus,
                ruleResult.deliverable().items(),
                enhancedSummary,
                allIssues,
                qualityScore,
                Instant.now()
            );

            // 缓存交付物
            deliverablesById.put(deliverableId, enhancedDeliverable);

            // 构建结果
            if (finalStatus == DepartmentDeliverable.AggregationStatus.COMPLETE && allIssues.isEmpty()) {
                return AggregationResult.success(enhancedDeliverable);
            } else if (!allIssues.isEmpty()) {
                return AggregationResult.qualityIssues(enhancedDeliverable, allIssues);
            } else {
                return AggregationResult.success(enhancedDeliverable);
            }

        } catch (Exception e) {
            log.warn("Failed to parse LLM analysis JSON, using rule-based result: {}",
                e.getMessage());
            // 尝试简单文本解析
            return parseAsText(ruleResult, llmAnalysis, department, planId, objective);
        }
    }

    /**
     * 从 JSON 节点提取字符串列表。
     */
    private List<String> extractList(JsonNode jsonNode, String fieldName) {
        if (!jsonNode.has(fieldName) || !jsonNode.get(fieldName).isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : jsonNode.get(fieldName)) {
            result.add(item.asText());
        }
        return result;
    }

    /**
     * 作为纯文本解析 LLM 结果（当 JSON 解析失败时）。
     */
    private AggregationResult parseAsText(AggregationResult ruleResult, String llmAnalysis,
                                           String department, String planId, String objective) {
        // 简单文本分析：检查是否包含关键词
        boolean hasIssues = llmAnalysis.contains("问题") || llmAnalysis.contains("不一致")
            || llmAnalysis.contains("ISSUES_FOUND") || llmAnalysis.contains("失败");

        List<String> issues = new ArrayList<>();
        if (hasIssues) {
            issues.add("LLM分析发现问题（详见原始响应）");
        }

        // 构建增强后的交付物
        String deliverableId = "deliverable-llm-text-" + department + "-" + System.currentTimeMillis();
        String enhancedSummary = ruleResult.deliverable().summary() + "\nLLM分析: " + llmAnalysis;

        DepartmentDeliverable enhancedDeliverable = new DepartmentDeliverable(
            deliverableId,
            department,
            planId,
            objective,
            hasIssues ? DepartmentDeliverable.AggregationStatus.QUALITY_ISSUES : ruleResult.deliverable().status(),
            ruleResult.deliverable().items(),
            enhancedSummary,
            issues,
            ruleResult.deliverable().overallQualityScore(),
            Instant.now()
        );

        deliverablesById.put(deliverableId, enhancedDeliverable);

        if (hasIssues) {
            return AggregationResult.qualityIssues(enhancedDeliverable, issues);
        } else {
            return AggregationResult.success(enhancedDeliverable);
        }
    }

    @Override
    public Optional<DepartmentDeliverable> getDeliverable(String deliverableId) {
        // 先查 LLM 版缓存
        DepartmentDeliverable llmDeliverable = deliverablesById.get(deliverableId);
        if (llmDeliverable != null) {
            return Optional.of(llmDeliverable);
        }
        // 再查规则版
        return fallback.getDeliverable(deliverableId);
    }

    @Override
    public List<DepartmentDeliverable> getDeliverablesByDepartment(String department) {
        // 合并 LLM 版和规则版
        List<DepartmentDeliverable> llmDeliverables = deliverablesById.values().stream()
            .filter(d -> d.department().equals(department))
            .collect(Collectors.toList());
        List<DepartmentDeliverable> ruleDeliverables = fallback.getDeliverablesByDepartment(department);
        List<DepartmentDeliverable> all = new ArrayList<>(llmDeliverables);
        all.addAll(ruleDeliverables);
        return all;
    }

    @Override
    public List<DepartmentDeliverable> getDeliverablesByPlan(String planId) {
        // 合并 LLM 版和规则版
        List<DepartmentDeliverable> llmDeliverables = deliverablesById.values().stream()
            .filter(d -> planId.equals(d.planId()))
            .collect(Collectors.toList());
        List<DepartmentDeliverable> ruleDeliverables = fallback.getDeliverablesByPlan(planId);
        List<DepartmentDeliverable> all = new ArrayList<>(llmDeliverables);
        all.addAll(ruleDeliverables);
        return all;
    }
}