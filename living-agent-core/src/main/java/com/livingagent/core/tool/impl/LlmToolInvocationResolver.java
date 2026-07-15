package com.livingagent.core.tool.impl;

import com.livingagent.core.autonomy.EmployeeWorkAssignment;
import com.livingagent.core.autonomy.llm.LlmDecisionClient;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolInvocationProtocol;
import com.livingagent.core.tool.ToolRegistry;
import com.livingagent.core.tool.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LLM 工具意图解析器（64-C-2）
 * 使用 LLM 语义解析任务描述到工具调用计划，
 * LLM 不可用时自动降级到关键词匹配。
 */
public class LlmToolInvocationResolver {

    private static final Logger log = LoggerFactory.getLogger(LlmToolInvocationResolver.class);

    private final LlmDecisionClient llmClient;
    private final ToolRegistry toolRegistry;

    public LlmToolInvocationResolver(LlmDecisionClient llmClient, ToolRegistry toolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 使用 LLM 解析任务描述到工具调用计划。
     * LLM 不可用时自动降级到关键词匹配。
     */
    @SuppressWarnings("unchecked")
    public ToolInvocationProtocol.InvocationPlan resolve(String taskDescription,
                                                          EmployeeWorkAssignment assignment) {
        if (llmClient != null) {
            try {
                String toolContext = buildToolContext(assignment);
                String systemPrompt = "你是一个工具路由器。根据任务描述，选择最合适的工具和操作。" +
                    "返回JSON格式：{toolName, action, params, fallbackTools, requiresLlmSummary}";
                String userPrompt = "任务描述：" + taskDescription + "\n\n可用工具：" + toolContext;

                Map<String, Object> schemaMap = new LinkedHashMap<>();
                schemaMap.put("type", "object");
                List<String> required = List.of("toolName", "action");
                schemaMap.put("required", required);
                Map<String, Object> properties = new LinkedHashMap<>();
                properties.put("toolName", Map.of("type", "string"));
                properties.put("action", Map.of("type", "string"));
                properties.put("params", Map.of("type", "object"));
                properties.put("fallbackTools", Map.of("type", "array", "items", Map.of("type", "string")));
                properties.put("requiresLlmSummary", Map.of("type", "boolean"));
                schemaMap.put("properties", properties);

                var jsonSchema = LlmDecisionClient.JsonSchema.of("ToolInvocationPlan", schemaMap);
                var request = LlmDecisionClient.LlmDecisionRequest.of(
                    systemPrompt, userPrompt, jsonSchema, Map.class
                );

                var result = llmClient.decide(request);
                if (result.success() && result.data() != null) {
                    Map<String, Object> data = (Map<String, Object>) result.data();
                    return parsePlan(data);
                }
                log.debug("LLM tool resolution failed, falling back to keyword: {}", result.error());
            } catch (Exception e) {
                log.debug("LLM tool resolution exception, falling back to keyword: {}", e.getMessage());
            }
        }

        return fallbackKeywordResolve(taskDescription, assignment);
    }

    private String buildToolContext(EmployeeWorkAssignment assignment) {
        if (toolRegistry == null) return "无可用工具";
        StringBuilder sb = new StringBuilder();
        List<String> toolNames = assignment != null && assignment.allowedTools() != null
            ? assignment.allowedTools() : toolRegistry.getAll().stream().map(Tool::getName).toList();

        for (String name : toolNames) {
            var toolOpt = toolRegistry.get(name);
            if (toolOpt.isPresent()) {
                Tool t = toolOpt.get();
                ToolSchema schema = t.getSchema();
                sb.append("- ").append(name).append(": ").append(t.getDescription());
                if (schema != null && schema.capabilities() != null && !schema.capabilities().isEmpty()) {
                    sb.append(" [").append(String.join(", ", schema.capabilities())).append("]");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private ToolInvocationProtocol.InvocationPlan parsePlan(Map<String, Object> data) {
        String toolName = (String) data.getOrDefault("toolName", "");
        String action = (String) data.getOrDefault("action", "");
        Map<String, Object> params = data.containsKey("params") && data.get("params") instanceof Map
            ? (Map<String, Object>) data.get("params") : Map.of();
        List<String> fallbackTools = data.containsKey("fallbackTools") && data.get("fallbackTools") instanceof List
            ? (List<String>) data.get("fallbackTools") : List.of();
        boolean requiresLlmSummary = data.containsKey("requiresLlmSummary")
            && Boolean.TRUE.equals(data.get("requiresLlmSummary"));

        return new ToolInvocationProtocol.InvocationPlan(toolName, action, params,
            fallbackTools, requiresLlmSummary);
    }

    private ToolInvocationProtocol.InvocationPlan fallbackKeywordResolve(String taskDescription,
                                                                          EmployeeWorkAssignment assignment) {
        if (taskDescription == null || toolRegistry == null) {
            return ToolInvocationProtocol.InvocationPlan.direct("unknown", "unknown", Map.of());
        }

        String lower = taskDescription.toLowerCase();
        List<String> availableTools = assignment != null && assignment.allowedTools() != null
            ? assignment.allowedTools()
            : toolRegistry.getAll().stream().map(Tool::getName).toList();

        // 关键词 → 工具名 映射
        Map<String, String> keywordMap = new LinkedHashMap<>();
        keywordMap.put("代码审查|code review|mr|merge request", "GitLabTool");
        keywordMap.put("gitlab|仓库|repository", "GitLabTool");
        keywordMap.put("jenkins|构建|ci/cd|流水线|pipeline", "JenkinsTool");
        keywordMap.put("openproject|项目|issue|任务", "OpenProjectTool");
        keywordMap.put("jira|issue|工单", "JiraTool");
        keywordMap.put("claude|ai对话", "ClaudeCliTool");
        keywordMap.put("文件|目录|folder|file", "FileEditTool");
        keywordMap.put("docker|容器|container", "DockerTool");
        keywordMap.put("网页|爬虫|crawl|scrape", "WebCrawlerTool");
        keywordMap.put("搜索|search|查询资料", "TavilySearchTool");
        keywordMap.put("pdf", "PdfTool");
        keywordMap.put("office|docx|xlsx", "OfficeTool");
        keywordMap.put("天气|weather", "WeatherTool");
        keywordMap.put("notion|笔记", "NotionTool");
        keywordMap.put("飞书|feishu", "FeishuTool");
        keywordMap.put("slack", "SlackTool");
        keywordMap.put("http|api|请求", "HttpTool");

        for (var entry : keywordMap.entrySet()) {
            String[] keywords = entry.getKey().split("\\|");
            for (String kw : keywords) {
                if (lower.contains(kw)) {
                    String toolName = entry.getValue();
                    if (availableTools.contains(toolName) || toolRegistry.exists(toolName)) {
                        return ToolInvocationProtocol.InvocationPlan.withLlmSummary(
                            toolName, "auto_resolve", Map.of("keyword", kw));
                    }
                }
            }
        }

        return ToolInvocationProtocol.InvocationPlan.direct("unknown", "keyword_fallback", Map.of());
    }
}
