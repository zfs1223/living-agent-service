package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NotionTool implements Tool {
    private static final String NAME = "notion";
    private static final String DESCRIPTION = "Notion API for creating and managing pages, databases, and blocks";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "productivity";
    private static final String NOTION_API_BASE = "https://api.notion.com/v1/";
    private static final String NOTION_VERSION = "2025-09-03";

    private String apiKey;
    private ToolStats stats = ToolStats.empty(NAME);

    public NotionTool() {
        this.apiKey = null;
    }

    public NotionTool(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: search, get_page, get_blocks, create_page, update_page, query_database, create_database", true)
                .parameter("api_key", "string", "Notion API Key (ntn_开头)", false)
                .parameter("page_id", "string", "页面ID", false)
                .parameter("database_id", "string", "数据库ID", false)
                .parameter("query", "string", "搜索查询", false)
                .parameter("title", "string", "页面标题", false)
                .parameter("properties", "object", "页面属性 (JSON)", false)
                .parameter("content", "string", "页面内容", false)
                .parameter("filter", "object", "数据库查询过滤条件 (JSON)", false)
                .parameter("sort", "object", "排序条件 (JSON)", false)
                .build();
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("page_management", "database_operations", "content_creation", "search");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("action parameter is required");
        }

        String key = params.getString("api_key");
        if (key != null && !key.isBlank()) {
            this.apiKey = key;
        }

        if (apiKey == null || apiKey.isBlank()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Notion API key is required. Set via constructor or api_key parameter");
        }

        try {
            ToolResult result;
            switch (action.toLowerCase()) {
                case "search":
                    result = search(params);
                    break;
                case "get_page":
                    result = getPage(params);
                    break;
                case "get_blocks":
                    result = getBlocks(params);
                    break;
                case "create_page":
                    result = createPage(params);
                    break;
                case "update_page":
                    result = updatePage(params);
                    break;
                case "query_database":
                    result = queryDatabase(params);
                    break;
                case "create_database":
                    result = createDatabase(params);
                    break;
                default:
                    result = ToolResult.failure("Unknown action: " + action);
            }
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Notion API error: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("action parameter is required");
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

    private ToolResult search(ToolParams params) throws Exception {
        String query = params.getString("query");
        
        Map<String, Object> body = new HashMap<>();
        if (query != null && !query.isBlank()) {
            body.put("query", query);
        }

        String response = postRequest("search", body);
        return ToolResult.success(Map.of("results", parseJson(response)));
    }

    private ToolResult getPage(ToolParams params) throws Exception {
        String pageId = params.getString("page_id");
        if (pageId == null || pageId.isBlank()) {
            return ToolResult.failure("page_id is required");
        }

        String response = getRequest("pages/" + normalizeId(pageId));
        return ToolResult.success(parseJson(response));
    }

    private ToolResult getBlocks(ToolParams params) throws Exception {
        String pageId = params.getString("page_id");
        if (pageId == null || pageId.isBlank()) {
            return ToolResult.failure("page_id is required");
        }

        String response = getRequest("blocks/" + normalizeId(pageId) + "/children");
        return ToolResult.success(Map.of("blocks", parseJson(response)));
    }

    private ToolResult createPage(ToolParams params) throws Exception {
        String databaseId = params.getString("database_id");
        String title = params.getString("title");
        String content = params.getString("content");
        
        if (databaseId == null || databaseId.isBlank()) {
            return ToolResult.failure("database_id is required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("parent", Map.of("database_id", normalizeId(databaseId)));

        Map<String, Object> properties = new HashMap<>();
        if (title != null && !title.isBlank()) {
            properties.put("Name", Map.of(
                "title", List.of(Map.of(
                    "text", Map.of("content", title)
                ))
            ));
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> customProperties = params.get("properties");
        if (customProperties != null) {
            properties.putAll(customProperties);
        }
        body.put("properties", properties);

        if (content != null && !content.isBlank()) {
            body.put("children", List.of(
                Map.of(
                    "object", "block",
                    "type", "paragraph",
                    "paragraph", Map.of(
                        "rich_text", List.of(
                            Map.of("text", Map.of("content", content))
                        )
                    )
                )
            ));
        }

        String response = postRequest("pages", body);
        return ToolResult.success(Map.of("page", parseJson(response), "message", "Page created"));
    }

    private ToolResult updatePage(ToolParams params) throws Exception {
        String pageId = params.getString("page_id");
        if (pageId == null || pageId.isBlank()) {
            return ToolResult.failure("page_id is required");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = params.get("properties");
        if (properties == null || properties.isEmpty()) {
            return ToolResult.failure("properties is required for update");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("properties", properties);

        String response = patchRequest("pages/" + normalizeId(pageId), body);
        return ToolResult.success(Map.of("page", parseJson(response), "message", "Page updated"));
    }

    private ToolResult queryDatabase(ToolParams params) throws Exception {
        String databaseId = params.getString("database_id");
        if (databaseId == null || databaseId.isBlank()) {
            return ToolResult.failure("database_id is required");
        }

        Map<String, Object> body = new HashMap<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = params.get("filter");
        if (filter != null) {
            body.put("filter", filter);
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> sort = params.get("sort");
        if (sort != null) {
            body.put("sorts", List.of(sort));
        }

        String response = postRequest("data_sources/" + normalizeId(databaseId) + "/query", body);
        return ToolResult.success(Map.of("results", parseJson(response)));
    }

    private ToolResult createDatabase(ToolParams params) throws Exception {
        String pageId = params.getString("page_id");
        String title = params.getString("title");
        
        if (pageId == null || pageId.isBlank()) {
            return ToolResult.failure("page_id (parent page) is required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("parent", Map.of("page_id", normalizeId(pageId)));
        
        if (title != null && !title.isBlank()) {
            body.put("title", List.of(
                Map.of("text", Map.of("content", title))
            ));
        }

        Map<String, Object> defaultProperties = new HashMap<>();
        defaultProperties.put("Name", Map.of("title", Map.of()));
        defaultProperties.put("Status", Map.of(
            "select", Map.of(
                "options", List.of(
                    Map.of("name", "Todo"),
                    Map.of("name", "In Progress"),
                    Map.of("name", "Done")
                )
            )
        ));
        defaultProperties.put("Date", Map.of("date", Map.of()));
        body.put("properties", defaultProperties);

        String response = postRequest("data_sources", body);
        return ToolResult.success(Map.of("database", parseJson(response), "message", "Database created"));
    }

    private String normalizeId(String id) {
        return id.replace("-", "");
    }

    private String getRequest(String endpoint) throws Exception {
        URL url = new URL(NOTION_API_BASE + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Notion-Version", NOTION_VERSION);
        connection.setRequestProperty("Content-Type", "application/json");

        return readResponse(connection);
    }

    private String postRequest(String endpoint, Map<String, Object> body) throws Exception {
        URL url = new URL(NOTION_API_BASE + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Notion-Version", NOTION_VERSION);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String jsonBody = toJson(body);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        return readResponse(connection);
    }

    private String patchRequest(String endpoint, Map<String, Object> body) throws Exception {
        URL url = new URL(NOTION_API_BASE + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PATCH");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Notion-Version", NOTION_VERSION);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String jsonBody = toJson(body);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        return readResponse(connection);
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        if (responseCode >= 400) {
            throw new RuntimeException("Notion API error (" + responseCode + "): " + response);
        }

        return response.toString();
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(toJsonValue(entry.getValue()));
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeJson((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) return toJson((Map<String, Object>) value);
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJsonValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Object parseJson(String json) {
        return json;
    }
}
