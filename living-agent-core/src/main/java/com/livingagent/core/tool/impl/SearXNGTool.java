package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SearXNGTool implements Tool {
    private static final String NAME = "searxng";
    private static final String DESCRIPTION = "Free privacy-respecting metasearch engine. No API key needed. Searches multiple engines simultaneously.";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "search";
    
    private static final List<String> DEFAULT_INSTANCES = Arrays.asList(
            "https://searx.be",
            "https://search.bus-hit.me",
            "https://search.rowie.at",
            "https://searx.fmac.xyz"
    );
    
    private final List<String> instances;
    private final int timeout;
    private String currentInstance;
    private ToolStats stats = ToolStats.empty(NAME);

    public SearXNGTool() {
        this(DEFAULT_INSTANCES, 15000);
    }

    public SearXNGTool(List<String> instances, int timeout) {
        this.instances = instances != null ? new ArrayList<>(instances) : DEFAULT_INSTANCES;
        this.timeout = timeout > 0 ? timeout : 15000;
        this.currentInstance = this.instances.get(0);
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
                .parameter("query", "string", "搜索查询语句", true)
                .parameter("engines", "string", "搜索引擎: google, bing, duckduckgo, baidu (逗号分隔)", false)
                .parameter("category", "string", "搜索类别: general, images, news, videos, music", false)
                .parameter("max_results", "integer", "最大结果数 (1-20)", false)
                .parameter("language", "string", "语言代码 (zh-CN, en, ja等)", false)
                .parameter("safesearch", "integer", "安全搜索级别: 0=关闭, 1=中等, 2=严格", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("web_search", "multi_engine", "privacy_focused", "fallback_instances");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String query = params.getString("query");
        if (query == null || query.isBlank()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("query parameter is required");
        }

        String engines = params.getString("engines");
        String category = params.getString("category");
        if (category == null) category = "general";
        
        Integer maxResultsInt = params.getInteger("max_results");
        int maxResults = maxResultsInt != null ? Math.min(maxResultsInt, 20) : 10;
        
        String language = params.getString("language");
        if (language == null) language = "zh-CN";
        
        Integer safeSearchInt = params.getInteger("safesearch");
        int safeSearch = safeSearchInt != null ? safeSearchInt : 1;

        try {
            String response = search(query, engines, category, language, safeSearch);
            List<Map<String, Object>> results = parseResults(response, maxResults);
            
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(Map.of(
                    "query", query,
                    "total_results", results.size(),
                    "results", results,
                    "engine", "searxng",
                    "instance", currentInstance
            ));
        } catch (Exception e) {
            return tryFallbackInstances(query, engines, category, language, safeSearch, maxResults, e, startTime);
        }
    }

    @Override
    public void validate(ToolParams params) {
        String query = params.getString("query");
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query parameter is required");
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

    private String search(String query, String engines, String category, String language, int safeSearch) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(currentInstance);
        urlBuilder.append("/search?q=");
        urlBuilder.append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        urlBuilder.append("&format=json");
        
        if (engines != null && !engines.isBlank()) {
            urlBuilder.append("&engines=").append(engines);
        }
        if (category != null && !category.isBlank()) {
            urlBuilder.append("&categories=").append(category);
        }
        if (language != null && !language.isBlank()) {
            urlBuilder.append("&language=").append(language);
        }
        urlBuilder.append("&safesearch=").append(safeSearch);

        URL url = new URL(urlBuilder.toString());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; LivingAgent/1.0)");
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("HTTP error: " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        connection.disconnect();
        return response.toString();
    }

    private ToolResult tryFallbackInstances(String query, String engines, String category, 
                                             String language, int safeSearch, int maxResults, 
                                             Exception originalError, long startTime) {
        for (String instance : instances) {
            if (instance.equals(currentInstance)) continue;
            
            this.currentInstance = instance;
            try {
                String response = search(query, engines, category, language, safeSearch);
                List<Map<String, Object>> results = parseResults(response, maxResults);
                
                stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
                return ToolResult.success(Map.of(
                        "query", query,
                        "total_results", results.size(),
                        "results", results,
                        "engine", "searxng",
                        "instance", currentInstance
                ));
            } catch (Exception ignored) {
                continue;
            }
        }
        
        stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
        return ToolResult.failure("All SearXNG instances failed: " + originalError.getMessage());
    }

    private List<Map<String, Object>> parseResults(String json, int maxResults) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        int resultsStart = json.indexOf("\"results\":[");
        if (resultsStart == -1) return results;
        
        int pos = resultsStart + 11;
        int count = 0;
        
        while (count < maxResults && pos < json.length()) {
            int objStart = json.indexOf("{", pos);
            if (objStart == -1) break;
            
            int objEnd = findMatchingBrace(json, objStart);
            if (objEnd == -1) break;
            
            String obj = json.substring(objStart, objEnd + 1);
            Map<String, Object> result = parseResultObject(obj);
            if (!result.isEmpty()) {
                results.add(result);
                count++;
            }
            
            pos = objEnd + 1;
        }
        
        return results;
    }

    private Map<String, Object> parseResultObject(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        String title = extractString(json, "title");
        String url = extractString(json, "url");
        String content = extractString(json, "content");
        String engine = extractString(json, "engine");
        
        if (title != null && url != null) {
            result.put("title", title);
            result.put("url", url);
            if (content != null) result.put("content", content);
            if (engine != null) result.put("engine", engine);
        }
        
        return result;
    }

    private String extractString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start == -1) return null;
        
        start += searchKey.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case '"': value.append('"'); break;
                    case '\\': value.append('\\'); break;
                    case 'n': value.append('\n'); break;
                    case 't': value.append('\t'); break;
                    case 'r': value.append('\r'); break;
                    default: value.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        
        return value.toString();
    }

    private int findMatchingBrace(String json, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        
        return -1;
    }

    public void addInstance(String instance) {
        if (instance != null && !instance.isBlank() && !instances.contains(instance)) {
            instances.add(instance);
        }
    }

    public List<String> getInstances() {
        return Collections.unmodifiableList(instances);
    }

    public String getCurrentInstance() {
        return currentInstance;
    }
}
