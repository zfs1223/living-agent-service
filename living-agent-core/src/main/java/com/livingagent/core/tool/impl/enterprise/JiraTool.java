package com.livingagent.core.tool.impl.enterprise;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

public class JiraTool implements Tool {
    
    private static final Logger log = LoggerFactory.getLogger(JiraTool.class);
    private static final String NAME = "jira";
    private static final String DESCRIPTION = "Jira项目管理工具，用于查询和管理Jira任务、缺陷和项目";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "project";
    
    private final String baseUrl;
    private final String apiToken;
    private final String email;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private ToolStats stats = ToolStats.empty(NAME);
    
    private final ToolSchema schema;
    
    public JiraTool(String baseUrl, String email, String apiToken) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.email = email;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
        
        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string", "操作类型: search_issue, get_issue, create_issue, update_issue, add_comment, search_user", true)
            .parameter("issue_key", "string", "任务键值 (如 PROJ-123)", false)
            .parameter("jql", "string", "JQL查询语句", false)
            .parameter("summary", "string", "任务摘要", false)
            .parameter("description", "string", "任务描述", false)
            .parameter("issue_type", "string", "任务类型: Bug, Task, Story, Epic", false)
            .parameter("priority", "string", "优先级: Highest, High, Medium, Low, Lowest", false)
            .parameter("assignee", "string", "分配人账号", false)
            .parameter("comment", "string", "评论内容", false)
            .parameter("project_key", "string", "项目键值", false)
            .parameter("max_results", "integer", "最大返回结果数", false)
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
    public ToolSchema getSchema() { return schema; }
    
    @Override
    public List<String> getCapabilities() {
        return List.of("issue_management", "search", "create_issue", "update_issue", "comments");
    }
    
    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("缺少必要参数: action");
        }
        
        try {
            ToolResult result = switch (action) {
                case "search_issue" -> searchIssues(params);
                case "get_issue" -> getIssue(params);
                case "create_issue" -> createIssue(params);
                case "update_issue" -> updateIssue(params);
                case "add_comment" -> addComment(params);
                case "search_user" -> searchUsers(params);
                default -> ToolResult.failure("不支持的操作: " + action);
            };
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("Jira操作失败: {}", e.getMessage(), e);
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("操作失败: " + e.getMessage());
        }
    }
    
    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("缺少必要参数: action");
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
    
    private String getAuthHeader() {
        return "Basic " + java.util.Base64.getEncoder().encodeToString(
            (email + ":" + apiToken).getBytes());
    }
    
    private ToolResult searchIssues(ToolParams params) throws Exception {
        String jql = params.getString("jql");
        if (jql == null) jql = "";
        Integer maxResultsInt = params.getInteger("max_results");
        int maxResults = maxResultsInt != null ? maxResultsInt : 50;
        
        String url = baseUrl + "/rest/api/3/search?jql=" + 
            java.net.URLEncoder.encode(jql, "UTF-8") + 
            "&maxResults=" + maxResults;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", "Basic " + 
                java.util.Base64.getEncoder().encodeToString(
                    (email + ":" + apiToken).getBytes()))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            List<Map<String, Object>> issues = (List<Map<String, Object>>) result.get("issues");
            
            List<Map<String, Object>> simplifiedIssues = new ArrayList<>();
            if (issues != null) {
                for (Map<String, Object> issue : issues) {
                    Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
                    Map<String, Object> simplified = new HashMap<>();
                    simplified.put("key", issue.get("key"));
                    simplified.put("summary", fields != null ? fields.get("summary") : null);
                    simplified.put("status", fields != null && fields.get("status") != null 
                        ? ((Map<String, Object>) fields.get("status")).get("name") : null);
                    simplified.put("priority", fields != null && fields.get("priority") != null 
                        ? ((Map<String, Object>) fields.get("priority")).get("name") : null);
                    simplified.put("assignee", fields != null && fields.get("assignee") != null 
                        ? ((Map<String, Object>) fields.get("assignee")).get("displayName") : null);
                    simplifiedIssues.add(simplified);
                }
            }
            
            return ToolResult.success(Map.of(
                "total", result.getOrDefault("total", 0),
                "issues", simplifiedIssues
            ));
        } else {
            return ToolResult.failure("搜索失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult getIssue(ToolParams params) throws Exception {
        String issueKey = params.getString("issue_key");
        if (issueKey == null || issueKey.isEmpty()) {
            return ToolResult.failure("缺少必要参数: issue_key");
        }
        
        String url = baseUrl + "/rest/api/3/issue/" + issueKey;
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", "Basic " + 
                java.util.Base64.getEncoder().encodeToString(
                    (email + ":" + apiToken).getBytes()))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            Map<String, Object> issue = objectMapper.readValue(response.body(), Map.class);
            Map<String, Object> fields = (Map<String, Object>) issue.get("fields");
            
            Map<String, Object> result = new HashMap<>();
            result.put("key", issue.get("key"));
            result.put("summary", fields != null ? fields.get("summary") : null);
            result.put("description", fields != null ? fields.get("description") : null);
            result.put("status", fields != null && fields.get("status") != null 
                ? ((Map<String, Object>) fields.get("status")).get("name") : null);
            result.put("priority", fields != null && fields.get("priority") != null 
                ? ((Map<String, Object>) fields.get("priority")).get("name") : null);
            result.put("issueType", fields != null && fields.get("issuetype") != null 
                ? ((Map<String, Object>) fields.get("issuetype")).get("name") : null);
            result.put("assignee", fields != null && fields.get("assignee") != null 
                ? ((Map<String, Object>) fields.get("assignee")).get("displayName") : null);
            result.put("reporter", fields != null && fields.get("reporter") != null 
                ? ((Map<String, Object>) fields.get("reporter")).get("displayName") : null);
            result.put("created", fields != null ? fields.get("created") : null);
            result.put("updated", fields != null ? fields.get("updated") : null);
            
            return ToolResult.success(result);
        } else {
            return ToolResult.failure("获取任务失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult createIssue(ToolParams params) throws Exception {
        String projectKey = params.getString("project_key");
        String summary = params.getString("summary");
        String issueType = params.getString("issue_type");
        if (issueType == null) issueType = "Task";
        String description = params.getString("description");
        String priority = params.getString("priority");
        String assignee = params.getString("assignee");
        
        if (projectKey == null || summary == null) {
            return ToolResult.failure("缺少必要参数: project_key 或 summary");
        }
        
        Map<String, Object> issueData = new HashMap<>();
        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("summary", summary);
        fields.put("issuetype", Map.of("name", issueType));
        
        if (description != null) {
            fields.put("description", Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(Map.of(
                    "type", "paragraph",
                    "content", List.of(Map.of(
                        "type", "text",
                        "text", description
                    ))
                ))
            ));
        }
        
        if (priority != null) {
            fields.put("priority", Map.of("name", priority));
        }
        
        if (assignee != null) {
            fields.put("assignee", Map.of("accountId", assignee));
        }
        
        issueData.put("fields", fields);
        
        String url = baseUrl + "/rest/api/3/issue";
        String body = objectMapper.writeValueAsString(issueData);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + 
                java.util.Base64.getEncoder().encodeToString(
                    (email + ":" + apiToken).getBytes()))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 201) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            return ToolResult.success(Map.of(
                "key", result.get("key"),
                "id", result.get("id"),
                "self", result.get("self"),
                "message", "任务创建成功"
            ));
        } else {
            return ToolResult.failure("创建任务失败: HTTP " + response.statusCode() + " - " + response.body());
        }
    }
    
    private ToolResult updateIssue(ToolParams params) throws Exception {
        String issueKey = params.getString("issue_key");
        if (issueKey == null || issueKey.isEmpty()) {
            return ToolResult.failure("缺少必要参数: issue_key");
        }
        
        Map<String, Object> fields = new HashMap<>();
        
        String summary = params.getString("summary");
        if (summary != null) {
            fields.put("summary", summary);
        }
        String priority = params.getString("priority");
        if (priority != null) {
            fields.put("priority", Map.of("name", priority));
        }
        String assignee = params.getString("assignee");
        if (assignee != null) {
            fields.put("assignee", Map.of("accountId", assignee));
        }
        
        if (fields.isEmpty()) {
            return ToolResult.failure("没有要更新的字段");
        }
        
        String url = baseUrl + "/rest/api/3/issue/" + issueKey;
        String body = objectMapper.writeValueAsString(Map.of("fields", fields));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + 
                java.util.Base64.getEncoder().encodeToString(
                    (email + ":" + apiToken).getBytes()))
            .method("PUT", HttpRequest.BodyPublishers.ofString(body))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 204) {
            return ToolResult.success(Map.of(
                "key", issueKey,
                "message", "任务更新成功"
            ));
        } else {
            return ToolResult.failure("更新任务失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult addComment(ToolParams params) throws Exception {
        String issueKey = params.getString("issue_key");
        String comment = params.getString("comment");
        
        if (issueKey == null || comment == null) {
            return ToolResult.failure("缺少必要参数: issue_key 或 comment");
        }
        
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/comment";
        String body = objectMapper.writeValueAsString(Map.of(
            "body", Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(Map.of(
                    "type", "paragraph",
                    "content", List.of(Map.of(
                        "type", "text",
                        "text", comment
                    ))
                ))
            )
        ));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + 
                java.util.Base64.getEncoder().encodeToString(
                    (email + ":" + apiToken).getBytes()))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 201) {
            return ToolResult.success(Map.of(
                "key", issueKey,
                "message", "评论添加成功"
            ));
        } else {
            return ToolResult.failure("添加评论失败: HTTP " + response.statusCode());
        }
    }
    
    private ToolResult searchUsers(ToolParams params) throws Exception {
        String query = params.getString("query");
        if (query == null || query.isEmpty()) {
            return ToolResult.failure("缺少必要参数: query");
        }
        
        String url = baseUrl + "/rest/api/3/user/search?query=" + 
            java.net.URLEncoder.encode(query, "UTF-8");
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", "Basic " + 
                java.util.Base64.getEncoder().encodeToString(
                    (email + ":" + apiToken).getBytes()))
            .GET()
            .build();
        
        HttpResponse<String> response = httpClient.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            List<Map<String, Object>> users = objectMapper.readValue(response.body(), List.class);
            
            List<Map<String, Object>> simplifiedUsers = new ArrayList<>();
            for (Map<String, Object> user : users) {
                Map<String, Object> simplified = new HashMap<>();
                simplified.put("accountId", user.get("accountId"));
                simplified.put("displayName", user.get("displayName"));
                simplified.put("emailAddress", user.get("emailAddress"));
                simplified.put("active", user.get("active"));
                simplifiedUsers.add(simplified);
            }
            
            return ToolResult.success(Map.of("users", simplifiedUsers));
        } else {
            return ToolResult.failure("搜索用户失败: HTTP " + response.statusCode());
        }
    }
}
