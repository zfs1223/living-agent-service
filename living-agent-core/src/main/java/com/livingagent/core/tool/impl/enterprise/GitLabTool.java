package com.livingagent.core.tool.impl.enterprise;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class GitLabTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(GitLabTool.class);

    private static final String NAME = "gitlab";
    private static final String DESCRIPTION = "GitLab 操作工具 - 项目、仓库、MR管理";
    private static final String VERSION = "1.0.0";

    private final HttpClient httpClient;
    private String gitlabUrl;
    private String accessToken;

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    private final ToolSchema schema;

    public GitLabTool() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        this.schema = new ToolSchema(
            NAME,
            DESCRIPTION,
            Map.of(
                "action", ToolSchema.Property.string("操作类型", List.of(
                    "list_projects", "get_project", "list_mrs", "get_mr",
                    "create_mr_comment", "list_commits", "get_file", "search"
                )),
                "project_id", ToolSchema.Property.string("项目ID"),
                "mr_iid", ToolSchema.Property.integer("MR内部ID"),
                "search_query", ToolSchema.Property.string("搜索关键词"),
                "file_path", ToolSchema.Property.string("文件路径"),
                "ref", ToolSchema.Property.string("分支名"),
                "comment", ToolSchema.Property.string("评论内容")
            ),
            List.of("action")
        );
    }

    public void configure(String gitlabUrl, String accessToken) {
        this.gitlabUrl = gitlabUrl;
        this.accessToken = accessToken;
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return "tech"; }

    @Override
    public ToolSchema getSchema() { return schema; }

    @Override
    public List<String> getCapabilities() {
        return List.of("git", "gitlab", "code-review", "mr", "ci-cd");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        totalCalls.incrementAndGet();

        if (gitlabUrl == null || accessToken == null) {
            return ToolResult.failure(
                java.util.UUID.randomUUID().toString(),
                NAME,
                "GitLab未配置，请设置gitlabUrl和accessToken",
                Duration.ZERO
            );
        }

        try {
            String action = params.getString("action");
            Object result = switch (action) {
                case "list_projects" -> listProjects(params);
                case "get_project" -> getProject(params);
                case "list_mrs" -> listMergeRequests(params);
                case "get_mr" -> getMergeRequest(params);
                case "create_mr_comment" -> createMrComment(params);
                case "list_commits" -> listCommits(params);
                case "get_file" -> getFile(params);
                case "search" -> search(params);
                default -> throw new IllegalArgumentException("未知操作: " + action);
            };

            long duration = System.currentTimeMillis() - startTime;
            totalDurationMs.addAndGet(duration);
            successfulCalls.incrementAndGet();

            return ToolResult.success(
                java.util.UUID.randomUUID().toString(),
                NAME,
                result,
                Duration.ofMillis(duration)
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            failedCalls.incrementAndGet();
            log.error("GitLab operation failed", e);
            return ToolResult.failure(
                java.util.UUID.randomUUID().toString(),
                NAME,
                "GitLab操作失败: " + e.getMessage(),
                Duration.ofMillis(duration)
            );
        }
    }

    private Object listProjects(ToolParams params) throws Exception {
        String endpoint = "/api/v4/projects";
        String query = params.getString("search_query");
        if (query != null && !query.isEmpty()) {
            endpoint += "?search=" + URLEncoder.encode(query, "UTF-8");
        }
        return doGet(endpoint);
    }

    private Object getProject(ToolParams params) throws Exception {
        String projectId = params.getString("project_id");
        if (projectId == null) {
            throw new IllegalArgumentException("缺少project_id参数");
        }
        return doGet("/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8"));
    }

    private Object listMergeRequests(ToolParams params) throws Exception {
        String projectId = params.getString("project_id");
        String endpoint = projectId != null
            ? "/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8") + "/merge_requests"
            : "/api/v4/merge_requests";
        return doGet(endpoint);
    }

    private Object getMergeRequest(ToolParams params) throws Exception {
        String projectId = params.getString("project_id");
        Integer mrIid = params.getInteger("mr_iid");
        if (projectId == null || mrIid == null) {
            throw new IllegalArgumentException("缺少project_id或mr_iid参数");
        }
        return doGet("/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8") + 
                    "/merge_requests/" + mrIid);
    }

    private Object createMrComment(ToolParams params) throws Exception {
        String projectId = params.getString("project_id");
        Integer mrIid = params.getInteger("mr_iid");
        String comment = params.getString("comment");
        
        if (projectId == null || mrIid == null || comment == null) {
            throw new IllegalArgumentException("缺少必要参数");
        }

        String body = "{\"body\":\"" + escapeJson(comment) + "\"}";
        return doPost("/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8") + 
                     "/merge_requests/" + mrIid + "/notes", body);
    }

    private Object listCommits(ToolParams params) throws Exception {
        String projectId = params.getString("project_id");
        String ref = params.getString("ref");
        
        if (projectId == null) {
            throw new IllegalArgumentException("缺少project_id参数");
        }

        String endpoint = "/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8") + "/repository/commits";
        if (ref != null) {
            endpoint += "?ref_name=" + URLEncoder.encode(ref, "UTF-8");
        }
        return doGet(endpoint);
    }

    private Object getFile(ToolParams params) throws Exception {
        String projectId = params.getString("project_id");
        String filePath = params.getString("file_path");
        String ref = params.getString("ref");
        
        if (projectId == null || filePath == null) {
            throw new IllegalArgumentException("缺少project_id或file_path参数");
        }

        String endpoint = "/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8") + 
                         "/repository/files/" + URLEncoder.encode(filePath, "UTF-8");
        if (ref != null) {
            endpoint += "?ref=" + URLEncoder.encode(ref, "UTF-8");
        }
        return doGet(endpoint);
    }

    private Object search(ToolParams params) throws Exception {
        String query = params.getString("search_query");
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("缺少search_query参数");
        }
        return doGet("/api/v4/search?query=" + URLEncoder.encode(query, "UTF-8"));
    }

    private Object doGet(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + endpoint))
            .header("PRIVATE-TOKEN", accessToken)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    private Object doPost(String endpoint, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + endpoint))
            .header("PRIVATE-TOKEN", accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    private Object parseResponse(HttpResponse<String> response) throws Exception {
        if (response.statusCode() >= 400) {
            throw new RuntimeException("GitLab API错误: " + response.statusCode() + " - " + response.body());
        }
        return response.body();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("缺少必需参数: action");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.getAutonomyLevel().canAct();
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public ToolStats getStats() {
        long total = totalCalls.get();
        double avgDuration = total > 0 ? (double) totalDurationMs.get() / total : 0;
        return new ToolStats(NAME, total, successfulCalls.get(), failedCalls.get(), avgDuration, System.currentTimeMillis());
    }
}
