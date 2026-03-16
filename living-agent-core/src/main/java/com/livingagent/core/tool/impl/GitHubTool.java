package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class GitHubTool implements Tool {
    private static final String NAME = "github";
    private static final String DESCRIPTION = "GitHub operations via gh CLI: issues, PRs, CI runs, code review, API queries";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "devops";
    
    private ToolStats stats = ToolStats.empty(NAME);

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
                .parameter("action", "string", "操作类型: pr_list, pr_view, pr_create, pr_merge, pr_checks, issue_list, issue_create, issue_close, issue_comment, run_list, run_view, api", true)
                .parameter("repo", "string", "仓库名称 (owner/repo格式)", false)
                .parameter("number", "integer", "PR或Issue编号", false)
                .parameter("title", "string", "标题", false)
                .parameter("body", "string", "内容描述", false)
                .parameter("state", "string", "状态: open, closed, all", false)
                .parameter("limit", "integer", "返回数量限制", false)
                .parameter("endpoint", "string", "API端点 (用于api操作)", false)
                .parameter("jq", "string", "JQ过滤表达式", false)
                .parameter("base", "string", "目标分支", false)
                .parameter("head", "string", "源分支", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("github-integration", "pr-management", "issue-management", "workflow-management");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        
        String action = params.getString("action");
        if (action == null) {
            return ToolResult.failure("action parameter is required");
        }

        if (!isGhInstalled()) {
            return ToolResult.failure("gh CLI is not installed. Install with: brew install gh or apt install gh");
        }

        if (!isGhAuthenticated()) {
            return ToolResult.failure("gh CLI is not authenticated. Run: gh auth login");
        }

        try {
            ToolResult result = switch (action.toLowerCase()) {
                case "pr_list" -> listPullRequests(params);
                case "pr_view" -> viewPullRequest(params);
                case "pr_create" -> createPullRequest(params);
                case "pr_merge" -> mergePullRequest(params);
                case "pr_checks" -> checkPullRequest(params);
                case "issue_list" -> listIssues(params);
                case "issue_create" -> createIssue(params);
                case "issue_close" -> closeIssue(params);
                case "issue_comment" -> commentIssue(params);
                case "run_list" -> listWorkflowRuns(params);
                case "run_view" -> viewWorkflowRun(params);
                case "api" -> apiQuery(params);
                default -> ToolResult.failure("Unknown action: " + action);
            };
            
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Error executing GitHub operation: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null) {
            throw new IllegalArgumentException("action parameter is required");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy != null && policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    private boolean isGhInstalled() {
        try {
            Process process = new ProcessBuilder("gh", "--version").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isGhAuthenticated() {
        try {
            Process process = new ProcessBuilder("gh", "auth", "status").start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private ToolResult listPullRequests(ToolParams params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("pr");
        cmd.add("list");
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }
        
        String state = params.getString("state");
        if (state == null) state = "open";
        cmd.add("--state");
        cmd.add(state);
        
        Integer limit = params.getInteger("limit");
        if (limit == null) limit = 10;
        cmd.add("--limit");
        cmd.add(String.valueOf(limit));
        
        cmd.add("--json");
        cmd.add("number,title,state,author,createdAt,url");

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("pull_requests", parseJsonArray(result)));
    }

    private ToolResult viewPullRequest(ToolParams params) {
        Integer number = params.getInteger("number");
        if (number == null) {
            return ToolResult.failure("number parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("pr");
        cmd.add("view");
        cmd.add(String.valueOf(number));
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }
        
        cmd.add("--json");
        cmd.add("number,title,body,state,author,additions,deletions,changedFiles,mergeable,url");

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(parseJsonObject(result));
    }

    private ToolResult createPullRequest(ToolParams params) {
        String title = params.getString("title");
        String body = params.getString("body");
        
        if (title == null) {
            return ToolResult.failure("title parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("pr");
        cmd.add("create");
        cmd.add("--title");
        cmd.add(title);
        
        if (body != null) {
            cmd.add("--body");
            cmd.add(body);
        }
        
        String base = params.getString("base");
        if (base != null) {
            cmd.add("--base");
            cmd.add(base);
        }
        
        String head = params.getString("head");
        if (head != null) {
            cmd.add("--head");
            cmd.add(head);
        }
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("url", result.trim(), "message", "Pull request created"));
    }

    private ToolResult mergePullRequest(ToolParams params) {
        Integer number = params.getInteger("number");
        if (number == null) {
            return ToolResult.failure("number parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("pr");
        cmd.add("merge");
        cmd.add(String.valueOf(number));
        cmd.add("--squash");
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("message", "Pull request merged", "output", result.trim()));
    }

    private ToolResult checkPullRequest(ToolParams params) {
        Integer number = params.getInteger("number");
        if (number == null) {
            return ToolResult.failure("number parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("pr");
        cmd.add("checks");
        cmd.add(String.valueOf(number));
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("checks", result.trim()));
    }

    private ToolResult listIssues(ToolParams params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("issue");
        cmd.add("list");
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }
        
        String state = params.getString("state");
        if (state == null) state = "open";
        cmd.add("--state");
        cmd.add(state);
        
        Integer limit = params.getInteger("limit");
        if (limit == null) limit = 10;
        cmd.add("--limit");
        cmd.add(String.valueOf(limit));
        
        cmd.add("--json");
        cmd.add("number,title,state,labels,createdAt,url");

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("issues", parseJsonArray(result)));
    }

    private ToolResult createIssue(ToolParams params) {
        String title = params.getString("title");
        String body = params.getString("body");
        
        if (title == null) {
            return ToolResult.failure("title parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("issue");
        cmd.add("create");
        cmd.add("--title");
        cmd.add(title);
        
        if (body != null) {
            cmd.add("--body");
            cmd.add(body);
        }
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("url", result.trim(), "message", "Issue created"));
    }

    private ToolResult closeIssue(ToolParams params) {
        Integer number = params.getInteger("number");
        if (number == null) {
            return ToolResult.failure("number parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("issue");
        cmd.add("close");
        cmd.add(String.valueOf(number));
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("message", "Issue closed", "output", result.trim()));
    }

    private ToolResult commentIssue(ToolParams params) {
        Integer number = params.getInteger("number");
        String body = params.getString("body");
        
        if (number == null || body == null) {
            return ToolResult.failure("number and body parameters are required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("issue");
        cmd.add("comment");
        cmd.add(String.valueOf(number));
        cmd.add("--body");
        cmd.add(body);
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("message", "Comment added", "output", result.trim()));
    }

    private ToolResult listWorkflowRuns(ToolParams params) {
        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("run");
        cmd.add("list");
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }
        
        Integer limit = params.getInteger("limit");
        if (limit == null) limit = 10;
        cmd.add("--limit");
        cmd.add(String.valueOf(limit));
        
        cmd.add("--json");
        cmd.add("id,name,status,conclusion,createdAt,headBranch");

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("runs", parseJsonArray(result)));
    }

    private ToolResult viewWorkflowRun(ToolParams params) {
        Integer runId = params.getInteger("number");
        if (runId == null) {
            return ToolResult.failure("number (run_id) parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("run");
        cmd.add("view");
        cmd.add(String.valueOf(runId));
        
        String repo = params.getString("repo");
        if (repo != null) {
            cmd.add("--repo");
            cmd.add(repo);
        }

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("run_details", result.trim()));
    }

    private ToolResult apiQuery(ToolParams params) {
        String endpoint = params.getString("endpoint");
        if (endpoint == null) {
            return ToolResult.failure("endpoint parameter is required");
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("gh");
        cmd.add("api");
        cmd.add(endpoint);
        
        String jq = params.getString("jq");
        if (jq != null) {
            cmd.add("--jq");
            cmd.add(jq);
        }

        String result = executeCommand(cmd.toArray(new String[0]));
        return ToolResult.success(Map.of("result", result.trim()));
    }

    private String executeCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private Object parseJsonObject(String json) {
        return json;
    }

    private Object parseJsonArray(String json) {
        return json;
    }
}
