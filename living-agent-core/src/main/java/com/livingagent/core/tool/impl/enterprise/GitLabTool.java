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
    private Map<String, String> employeeAccounts;

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

    public void setEmployeeAccounts(Map<String, String> employeeAccounts) {
        this.employeeAccounts = employeeAccounts != null ? employeeAccounts : Map.of();
        log.info("GitLabTool configured with {} employee accounts: {}",
            this.employeeAccounts.size(),
            this.employeeAccounts.keySet());
    }

    private String resolveAccessToken(ToolContext context) {
        if (context != null && context.employeeCode() != null && employeeAccounts != null) {
            String employeeToken = employeeAccounts.get(context.employeeCode());
            if (employeeToken != null && !employeeToken.isEmpty()) {
                log.debug("Using employee-specific GitLab token for employee: {}", context.employeeCode());
                return employeeToken;
            }
        }
        return accessToken;
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

        if (gitlabUrl == null) {
            return ToolResult.failure(
                java.util.UUID.randomUUID().toString(),
                NAME,
                "GitLab未配置，请设置gitlabUrl",
                Duration.ZERO
            );
        }

        String effectiveToken = resolveAccessToken(context);

        try {
            String action = params.getString("action");
            Object result = switch (action) {
                case "list_projects" -> listProjects(params, effectiveToken);
                case "get_project" -> getProject(params, effectiveToken);
                case "list_mrs" -> listMergeRequests(params, effectiveToken);
                case "get_mr" -> getMergeRequest(params, effectiveToken);
                case "create_mr_comment" -> createMrComment(params, effectiveToken);
                case "list_commits" -> listCommits(params, effectiveToken);
                case "get_file" -> getFile(params, effectiveToken);
                case "search" -> search(params, effectiveToken);
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

    private Object listProjects(ToolParams params, String token) throws Exception {
        String endpoint = "/api/v4/projects";
        String query = params.getString("search_query");
        if (query != null && !query.isEmpty()) {
            endpoint += "?search=" + URLEncoder.encode(query, "UTF-8");
        }
        return doGet(endpoint, token);
    }

    private Object getProject(ToolParams params, String token) throws Exception {
        String projectId = params.getString("project_id");
        if (projectId == null) {
            throw new IllegalArgumentException("缺少project_id参数");
        }
        return doGet("/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8"), token);
    }

    private Object listMergeRequests(ToolParams params, String token) throws Exception {
        String projectId = params.getString("project_id");
        String endpoint = projectId != null
            ? "/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8") + "/merge_requests"
            : "/api/v4/merge_requests";
        return doGet(endpoint, token);
    }

    private Object getMergeRequest(ToolParams params, String token) throws Exception {
        String projectId = params.getString("project_id");
        Integer mrIid = params.getInteger("mr_iid");
        if (projectId == null || mrIid == null) {
            throw new IllegalArgumentException("缺少project_id或mr_iid参数");
        }
        return doGet("/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8") +
                    "/merge_requests/" + mrIid, token);
    }

    private Object createMrComment(ToolParams params, String token) throws Exception {
        String projectId = params.getString("project_id");
        Integer mrIid = params.getInteger("mr_iid");
        String comment = params.getString("comment");

        if (projectId == null || mrIid == null || comment == null) {
            throw new IllegalArgumentException("缺少必要参数");
        }

        String body = "{\"body\":\"" + escapeJson(comment) + "\"}";
        return doPost("/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8") +
                     "/merge_requests/" + mrIid + "/notes", body, token);
    }

    private Object listCommits(ToolParams params, String token) throws Exception {
        String projectId = params.getString("project_id");
        String ref = params.getString("ref");

        if (projectId == null) {
            throw new IllegalArgumentException("缺少project_id参数");
        }

        String endpoint = "/api/v4/projects/" + URLEncoder.encode(projectId, "UTF-8") + "/repository/commits";
        if (ref != null) {
            endpoint += "?ref_name=" + URLEncoder.encode(ref, "UTF-8");
        }
        return doGet(endpoint, token);
    }

    private Object getFile(ToolParams params, String token) throws Exception {
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
        return doGet(endpoint, token);
    }

    private Object search(ToolParams params, String token) throws Exception {
        String query = params.getString("search_query");
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("缺少search_query参数");
        }
        return doGet("/api/v4/search?query=" + URLEncoder.encode(query, "UTF-8"), token);
    }

    private Object doGet(String endpoint, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + endpoint))
            .GET();
        if (token != null && !token.isEmpty()) {
            builder.header("PRIVATE-TOKEN", token);
        }
        HttpRequest request = builder.build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response);
    }

    private Object doPost(String endpoint, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(gitlabUrl + endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null && !token.isEmpty()) {
            builder.header("PRIVATE-TOKEN", token);
        }
        HttpRequest request = builder.build();

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
