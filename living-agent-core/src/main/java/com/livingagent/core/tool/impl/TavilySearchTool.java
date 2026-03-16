package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class TavilySearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(TavilySearchTool.class);

    private static final String NAME = "tavily_search";
    private static final String DESCRIPTION = "AI优化的实时网络搜索引擎，提供准确、结构化的搜索结果";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "search";
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private ToolStats stats = ToolStats.empty(NAME);

    public TavilySearchTool(String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("query", "string", "搜索查询语句", true)
                .parameter("search_depth", "string", "搜索深度: basic(快速) 或 advanced(深度)", false)
                .parameter("max_results", "integer", "最大返回结果数 (1-10)", false)
                .parameter("include_answer", "boolean", "是否包含AI生成的答案摘要", false)
                .parameter("include_domains", "array", "限定搜索域名列表", false)
                .parameter("exclude_domains", "array", "排除搜索域名列表", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("web_search", "ai_optimized", "real_time", "structured_results");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String query = params.getString("query");
        
        if (query == null || query.isEmpty()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("查询语句不能为空");
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Tavily API Key 未配置");
        }
        
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("api_key", apiKey);
            requestBody.put("query", query);
            requestBody.put("search_depth", params.getString("search_depth") != null ? params.getString("search_depth") : "basic");
            
            Integer maxResults = params.getInteger("max_results");
            requestBody.put("max_results", maxResults != null ? maxResults : 5);
            
            Boolean includeAnswer = params.getBoolean("include_answer");
            requestBody.put("include_answer", includeAnswer != null ? includeAnswer : true);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TAVILY_API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();
            
            long requestStartTime = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long responseTime = System.currentTimeMillis() - requestStartTime;
            
            if (response.statusCode() != 200) {
                log.error("Tavily API error: {} - {}", response.statusCode(), response.body());
                stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
                return ToolResult.failure("搜索失败: HTTP " + response.statusCode());
            }
            
            JsonNode root = objectMapper.readTree(response.body());
            
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("query", query);
            output.put("response_time_ms", responseTime);
            
            if (root.has("answer") && !root.get("answer").isNull()) {
                output.put("answer", root.get("answer").asText());
            }
            
            List<Map<String, Object>> results = new ArrayList<>();
            if (root.has("results") && root.get("results").isArray()) {
                for (JsonNode result : root.get("results")) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", result.has("title") ? result.get("title").asText() : "");
                    item.put("url", result.has("url") ? result.get("url").asText() : "");
                    item.put("content", result.has("content") ? result.get("content").asText() : "");
                    if (result.has("score")) {
                        item.put("score", result.get("score").asDouble());
                    }
                    if (result.has("published_date")) {
                        item.put("published_date", result.get("published_date").asText());
                    }
                    results.add(item);
                }
            }
            output.put("results", results);
            output.put("result_count", results.size());
            
            log.info("Tavily search completed: {} results in {}ms", results.size(), responseTime);
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(output);
            
        } catch (Exception e) {
            log.error("Tavily search failed: {}", e.getMessage());
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String query = params.getString("query");
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("查询语句不能为空");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }
}
