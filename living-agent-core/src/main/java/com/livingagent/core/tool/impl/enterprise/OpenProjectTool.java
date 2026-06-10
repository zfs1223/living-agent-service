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

public class OpenProjectTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(OpenProjectTool.class);
    private static final String NAME = "jira";
    private static final String DESCRIPTION = "项目管理工具 (OpenProject)，用于查询和管理任务、缺陷和项目";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "project";

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private ToolStats stats = ToolStats.empty(NAME);

    private final ToolSchema schema;

    public OpenProjectTool(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey != null ? apiKey : "";
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();

        this.schema = ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string", "操作类型: search_issue, get_issue, create_issue, update_issue, add_comment, search_user", true)
            .parameter("issue_key", "string", "任务ID (工作包ID)", false)
            .parameter("jql", "string", "查询过滤条件", false)
            .parameter("summary", "string", "任务摘要", false)
            .parameter("description", "string", "任务描述", false)
            .parameter("issue_type", "string", "任务类型: Task, Bug, Feature, Milestone", false)
            .parameter("priority", "string", "优先级: Low, Normal, High, Immediate", false)
            .parameter("assignee", "string", "分配人用户ID", false)
            .parameter("comment", "string", "评论内容", false)
            .parameter("project_key", "string", "项目标识符", false)
            .parameter("max_results", "integer", "最大返回结果数", false)
            .build();
    }

    @Override public String getName() { return NAME; }
    @Override public String getDescription() { return DESCRIPTION; }
    @Override public String getVersion() { return VERSION; }
    @Override public String getDepartment() { return DEPARTMENT; }
    @Override public ToolSchema getSchema() { return schema; }

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
            log.error("OpenProject操作失败: {}", e.getMessage(), e);
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

    @Override public boolean isAllowed(SecurityPolicy policy) { return policy.isToolAllowed(NAME); }
    @Override public boolean requiresApproval() { return false; }
    @Override public ToolStats getStats() { return stats; }

    private HttpRequest.Builder addAuth(HttpRequest.Builder builder) {
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString(
                ("apikey:" + apiKey).getBytes()));
        }
        return builder;
    }

    private ToolResult searchIssues(ToolParams params) throws Exception {
        String filter = params.getString("jql");
        Integer maxResultsInt = params.getInteger("max_results");
        int pageSize = maxResultsInt != null ? maxResultsInt : 50;

        String url = baseUrl + "/api/v3/work_packages?pageSize=" + pageSize;
        if (filter != null && !filter.isEmpty()) {
            url += "&filters=" + java.net.URLEncoder.encode("[{\"subject\":{\"operator\":\"~\",\"values\":[\"" + filter + "\"]}}]", "UTF-8");
        }

        HttpRequest request = addAuth(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Map<String, Object> embedded = (Map<String, Object>) result.get("_embedded");
            List<Map<String, Object>> elements = embedded != null
                ? (List<Map<String, Object>>) embedded.get("elements") : List.of();

            List<Map<String, Object>> simplifiedIssues = new ArrayList<>();
            if (elements != null) {
                for (Map<String, Object> wp : elements) {
                    Map<String, Object> simplified = new HashMap<>();
                    simplified.put("key", wp.get("id"));
                    simplified.put("summary", wp.get("subject"));
                    Map<String, Object> wpEmbedded = (Map<String, Object>) wp.get("_embedded");
                    if (wpEmbedded != null) {
                        Map<String, Object> status = (Map<String, Object>) wpEmbedded.get("status");
                        simplified.put("status", status != null ? status.get("name") : null);
                        Map<String, Object> priority = (Map<String, Object>) wpEmbedded.get("priority");
                        simplified.put("priority", priority != null ? priority.get("name") : null);
                        Map<String, Object> assignee = (Map<String, Object>) wpEmbedded.get("assignee");
                        simplified.put("assignee", assignee != null ? assignee.get("name") : null);
                    }
                    simplifiedIssues.add(simplified);
                }
            }

            Map<String, Object> totalObj = (Map<String, Object>) result.get("total");
            return ToolResult.success(Map.of(
                "total", totalObj != null ? totalObj.get("count") : simplifiedIssues.size(),
                "issues", simplifiedIssues
            ));
        } else {
            return ToolResult.failure("搜索失败: HTTP " + response.statusCode() + " - " + response.body());
        }
    }

    private ToolResult getIssue(ToolParams params) throws Exception {
        String issueKey = params.getString("issue_key");
        if (issueKey == null || issueKey.isEmpty()) {
            return ToolResult.failure("缺少必要参数: issue_key");
        }

        String url = baseUrl + "/api/v3/work_packages/" + issueKey;

        HttpRequest request = addAuth(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> wp = objectMapper.readValue(response.body(), Map.class);
            Map<String, Object> embedded = (Map<String, Object>) wp.get("_embedded");

            Map<String, Object> result = new HashMap<>();
            result.put("key", wp.get("id"));
            result.put("summary", wp.get("subject"));
            result.put("description", wp.get("description") instanceof Map
                ? ((Map<String, Object>) wp.get("description")).get("raw") : null);
            Map<String, Object> status = embedded != null ? (Map<String, Object>) embedded.get("status") : null;
            result.put("status", status != null ? status.get("name") : null);
            Map<String, Object> priority = embedded != null ? (Map<String, Object>) embedded.get("priority") : null;
            result.put("priority", priority != null ? priority.get("name") : null);
            Map<String, Object> type = embedded != null ? (Map<String, Object>) embedded.get("type") : null;
            result.put("issueType", type != null ? type.get("name") : null);
            Map<String, Object> assignee = embedded != null ? (Map<String, Object>) embedded.get("assignee") : null;
            result.put("assignee", assignee != null ? assignee.get("name") : null);
            result.put("created", wp.get("createdAt"));
            result.put("updated", wp.get("updatedAt"));

            return ToolResult.success(result);
        } else {
            return ToolResult.failure("获取任务失败: HTTP " + response.statusCode());
        }
    }

    private ToolResult createIssue(ToolParams params) throws Exception {
        String projectKey = params.getString("project_key");
        String summary = params.getString("summary");
        String issueType = params.getString("issue_type");
        String description = params.getString("description");
        String priority = params.getString("priority");
        String assignee = params.getString("assignee");

        if (projectKey == null || summary == null) {
            return ToolResult.failure("缺少必要参数: project_key 或 summary");
        }

        Map<String, Object> wpData = new HashMap<>();
        wpData.put("subject", summary);
        wpData.put("_links", Map.of("project", Map.of("href", "/api/v3/projects/" + projectKey)));

        if (description != null) {
            wpData.put("description", Map.of("format", "markdown", "raw", description));
        } else {
            wpData.put("description", Map.of("format", "markdown", "raw", ""));
        }

        if (issueType != null) {
            String typeId = switch (issueType.toLowerCase()) {
                case "bug" -> "1";
                case "feature" -> "2";
                case "milestone" -> "5";
                default -> "1";
            };
            wpData.put("_links", new HashMap<>(((Map<String, Object>) wpData.get("_links"))));
            ((Map<String, Object>) wpData.get("_links")).put("type", Map.of("href", "/api/v3/types/" + typeId));
        }

        if (priority != null) {
            String priorityId = switch (priority.toLowerCase()) {
                case "low" -> "8";
                case "normal" -> "4";
                case "high" -> "6";
                case "immediate" -> "7";
                default -> "4";
            };
            if (!wpData.containsKey("_links") || !(wpData.get("_links") instanceof HashMap)) {
                wpData.put("_links", new HashMap<>(((Map<String, Object>) wpData.get("_links"))));
            }
            ((Map<String, Object>) wpData.get("_links")).put("priority", Map.of("href", "/api/v3/priorities/" + priorityId));
        }

        if (assignee != null) {
            if (!wpData.containsKey("_links") || !(wpData.get("_links") instanceof HashMap)) {
                wpData.put("_links", new HashMap<>(((Map<String, Object>) wpData.get("_links"))));
            }
            ((Map<String, Object>) wpData.get("_links")).put("assignee", Map.of("href", "/api/v3/users/" + assignee));
        }

        String url = baseUrl + "/api/v3/work_packages";
        String body = objectMapper.writeValueAsString(wpData);

        HttpRequest request = addAuth(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            return ToolResult.success(Map.of(
                "key", result.get("id"),
                "id", result.get("id"),
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

        Map<String, Object> wpData = new HashMap<>();
        Map<String, Object> links = new HashMap<>();

        String summary = params.getString("summary");
        if (summary != null) {
            wpData.put("subject", summary);
        }
        String priority = params.getString("priority");
        if (priority != null) {
            String priorityId = switch (priority.toLowerCase()) {
                case "low" -> "8";
                case "normal" -> "4";
                case "high" -> "6";
                case "immediate" -> "7";
                default -> "4";
            };
            links.put("priority", Map.of("href", "/api/v3/priorities/" + priorityId));
        }
        String assignee = params.getString("assignee");
        if (assignee != null) {
            links.put("assignee", Map.of("href", "/api/v3/users/" + assignee));
        }

        if (!links.isEmpty()) {
            wpData.put("_links", links);
        }

        if (wpData.isEmpty()) {
            return ToolResult.failure("没有要更新的字段");
        }

        String url = baseUrl + "/api/v3/work_packages/" + issueKey;
        String body = objectMapper.writeValueAsString(wpData);

        HttpRequest request = addAuth(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json"))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
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

        String url = baseUrl + "/api/v3/work_packages/" + issueKey + "/activities";
        String body = objectMapper.writeValueAsString(Map.of(
            "comment", Map.of("format", "markdown", "raw", comment)
        ));

        HttpRequest request = addAuth(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

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

        String url = baseUrl + "/api/v3/users?filters=" +
            java.net.URLEncoder.encode("[{\"name\":{\"operator\":\"~\",\"values\":[\"" + query + "\"]}}]", "UTF-8");

        HttpRequest request = addAuth(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Map<String, Object> embedded = (Map<String, Object>) result.get("_embedded");
            List<Map<String, Object>> elements = embedded != null
                ? (List<Map<String, Object>>) embedded.get("elements") : List.of();

            List<Map<String, Object>> simplifiedUsers = new ArrayList<>();
            for (Map<String, Object> user : elements) {
                Map<String, Object> simplified = new HashMap<>();
                simplified.put("accountId", user.get("id"));
                simplified.put("displayName", user.get("name"));
                simplified.put("emailAddress", user.get("email"));
                simplified.put("active", user.get("status") != null && "active".equals(((Map<String, Object>) user.get("status")).get("name")));
                simplifiedUsers.add(simplified);
            }

            return ToolResult.success(Map.of("users", simplifiedUsers));
        } else {
            return ToolResult.failure("搜索用户失败: HTTP " + response.statusCode());
        }
    }
}
