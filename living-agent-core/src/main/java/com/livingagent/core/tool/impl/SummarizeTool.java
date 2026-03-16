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

public class SummarizeTool implements Tool {
    private static final String NAME = "summarize";
    private static final String DESCRIPTION = "Summarize or extract text from URLs, podcasts, and local files";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "content";
    private static final String SUMMARIZE_API_BASE = "https://api.summarize.sh/v1/";

    private String apiKey;
    private ToolStats stats = ToolStats.empty(NAME);

    public SummarizeTool() {
        this.apiKey = null;
    }

    public SummarizeTool(String apiKey) {
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
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("url", "string", "要摘要的URL (文章、视频、播客等)", false)
                .parameter("file_path", "string", "本地文件路径", false)
                .parameter("api_key", "string", "API密钥 (OpenAI/Anthropic/xAI/Google)", false)
                .parameter("model", "string", "模型名称 (如 google/gemini-3-flash-preview)", false)
                .parameter("length", "string", "摘要长度: short, medium, long, xl, xxl", false)
                .parameter("max_tokens", "integer", "最大输出tokens", false)
                .parameter("extract_only", "boolean", "仅提取文本不摘要", false)
                .parameter("youtube", "string", "YouTube模式: auto, off", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("url_summarization", "file_summarization", "text_extraction", "youtube_support");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String url = params.getString("url");
        String filePath = params.getString("file_path");
        
        if ((url == null || url.isBlank()) && (filePath == null || filePath.isBlank())) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Either url or file_path parameter is required");
        }

        String key = params.getString("api_key");
        if (key != null && !key.isBlank()) {
            this.apiKey = key;
        }

        try {
            ToolResult result;
            if (url != null && !url.isBlank()) {
                result = summarizeUrl(params);
            } else {
                result = summarizeFile(params);
            }
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Summarize error: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String url = params.getString("url");
        String filePath = params.getString("file_path");
        if ((url == null || url.isBlank()) && (filePath == null || filePath.isBlank())) {
            throw new IllegalArgumentException("Either url or file_path parameter is required");
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

    private ToolResult summarizeUrl(ToolParams params) throws Exception {
        String url = params.getString("url");
        String model = params.getString("model");
        if (model == null) model = "google/gemini-3-flash-preview";
        
        String length = params.getString("length");
        Integer maxTokens = params.getInteger("max_tokens");
        Boolean extractOnly = params.getBoolean("extract_only");
        String youtube = params.getString("youtube");

        Map<String, Object> body = new HashMap<>();
        body.put("url", url);
        body.put("model", model);
        
        if (length != null) {
            body.put("length", length);
        }
        if (maxTokens != null) {
            body.put("max_output_tokens", maxTokens);
        }
        if (Boolean.TRUE.equals(extractOnly)) {
            body.put("extract_only", true);
        }
        if (youtube != null) {
            body.put("youtube", youtube);
        }

        String response = postRequest("summarize", body);
        return ToolResult.success(Map.of(
                "url", url,
                "summary", response,
                "model", model
        ));
    }

    private ToolResult summarizeFile(ToolParams params) throws Exception {
        String filePath = params.getString("file_path");
        String model = params.getString("model");
        if (model == null) model = "google/gemini-3-flash-preview";
        
        String length = params.getString("length");

        Map<String, Object> body = new HashMap<>();
        body.put("file_path", filePath);
        body.put("model", model);
        
        if (length != null) {
            body.put("length", length);
        }

        String response = postRequest("summarize/file", body);
        return ToolResult.success(Map.of(
                "file", filePath,
                "summary", response,
                "model", model
        ));
    }

    private String postRequest(String endpoint, Map<String, Object> body) throws Exception {
        URL url = new URL(SUMMARIZE_API_BASE + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        
        if (apiKey != null && !apiKey.isBlank()) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        
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
            throw new RuntimeException("API error (" + responseCode + "): " + response);
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
}
