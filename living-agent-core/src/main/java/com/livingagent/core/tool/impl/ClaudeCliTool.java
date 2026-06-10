package com.livingagent.core.tool.impl;

import com.livingagent.core.sandbox.ClaudeExecutionGateway;
import com.livingagent.core.sandbox.ExecutionResult;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.Tool;
import com.livingagent.core.tool.ToolContext;
import com.livingagent.core.tool.ToolResult;
import com.livingagent.core.tool.ToolSchema;
import com.livingagent.core.tool.ToolStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Claude CLI 工具（兼容层实现）
 *
 * 设计目标：
 * 1) 通过 ClaudeExecutionGateway 管理 CLI 会话与参数映射
 * 2) 对外提供 Claude CLI 语义化参数，兼容 free-claude-code-main 的会话模式
 * 3) 参数设计参考 free-claude-code-main（session/resume/fork/output-format/verbose）
 */
public class ClaudeCliTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliTool.class);

    private static final String NAME = "claude_cli";
    private static final String VERSION = "0.1.0";
    private static final String DEPARTMENT = "tech";

    private static final String DESCRIPTION = """
        Claude CLI integration for code generation, review, test, debug and repo-oriented tasks.
        
        Actions:
        - prompt: send a prompt to Claude CLI
        - resume: continue an existing Claude session (args: resume_session_id)
        - status: query cli session status
        - start: start async Claude CLI job
        - poll: poll async job result (args: job_id)
        - cancel: cancel async job (args: job_id)
        
        Common args:
        - prompt
        - resume_session_id
        - fork_session
        - model
        - output_format (stream-json | json | text)
        - verbose
        - allowed_dirs
        - add_dir
        - worktree
        - system_prompt
        - max_turns
        """;

    private final ClaudeExecutionGateway executionGateway;
    private final ToolSchema schema;
    private volatile ToolStats stats;

    public ClaudeCliTool(ClaudeExecutionGateway executionGateway) {
        this.executionGateway = executionGateway;
        this.schema = buildSchema();
        this.stats = ToolStats.empty(NAME);
    }

    private ToolSchema buildSchema() {
        return ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string", "操作类型: prompt, resume, status", true)
            .parameter("prompt", "string", "用户提示词/任务描述", false)
            .parameter("resume_session_id", "string", "恢复会话ID（用于 resume）", false)
            .parameter("job_id", "string", "异步任务ID（用于 poll/cancel）", false)
            .parameter("fork_session", "boolean", "是否从会话分叉（用于 resume）", false)
            .parameter("output_format", "string", "输出格式: stream-json, json, text", false)
            .parameter("verbose", "boolean", "是否输出详细日志", false)
            .parameter("model", "string", "Claude CLI 模型名称或别名", false)
            .parameter("allowed_dirs", "array", "允许访问的目录白名单", false)
            .parameter("add_dir", "array", "额外可访问目录", false)
            .parameter("worktree", "boolean", "是否使用隔离工作树", false)
            .parameter("system_prompt", "string", "自定义系统提示词", false)
            .parameter("max_turns", "number", "最大思考轮数", false)
            .parameter("options", "object", "透传扩展参数", false)
            .build();
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
    public ToolSchema getSchema() {
        return schema;
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("code_generation", "code_review", "testing", "debugging", "session_resume");
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: action");
        }

        String normalized = action.toLowerCase();
        if (!("prompt".equals(normalized)
            || "resume".equals(normalized)
            || "status".equals(normalized)
            || "start".equals(normalized)
            || "poll".equals(normalized)
            || "cancel".equals(normalized))) {
            throw new IllegalArgumentException("Unsupported action: " + action);
        }

        if ("status".equals(normalized)) {
            return;
        }

        if (("prompt".equals(normalized) || "resume".equals(normalized) || "start".equals(normalized))
            && (params.getString("prompt") == null || params.getString("prompt").isBlank())) {
            throw new IllegalArgumentException("Missing required parameter: prompt");
        }

        if (("poll".equals(normalized) || "cancel".equals(normalized))
            && (params.getString("job_id") == null || params.getString("job_id").isBlank())) {
            throw new IllegalArgumentException("Missing required parameter: job_id");
        }
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long start = System.currentTimeMillis();

        String action = params.getString("action");
        if (action == null || action.isBlank()) {
            return ToolResult.failure("Missing required parameter: action");
        }

        String normalizedAction = action.toLowerCase();
        String sessionId = context != null ? context.sessionId() : null;

        try {
            if ("status".equals(normalizedAction)) {
                Map<String, Object> snapshot = executionGateway.getSessionSnapshot(sessionId);
                stats = stats.recordCall(true, System.currentTimeMillis() - start);
                return ToolResult.success(snapshot);
            }

            if ("start".equals(normalizedAction)) {
                Map<String, Object> gatewayParams = buildGatewayParams("prompt", params);
                Map<String, Object> started = executionGateway.startAsyncJob(sessionId, gatewayParams);
                stats = stats.recordCall(true, System.currentTimeMillis() - start);
                return ToolResult.success(started);
            }

            if ("poll".equals(normalizedAction)) {
                Map<String, Object> polled = executionGateway.pollAsyncJob(params.getString("job_id"));
                stats = stats.recordCall(true, System.currentTimeMillis() - start);
                return ToolResult.success(polled);
            }

            if ("cancel".equals(normalizedAction)) {
                Map<String, Object> cancelled = executionGateway.cancelAsyncJob(params.getString("job_id"));
                stats = stats.recordCall(true, System.currentTimeMillis() - start);
                return ToolResult.success(cancelled);
            }

            Map<String, Object> gatewayParams = buildGatewayParams(normalizedAction, params);
            ExecutionResult result = executionGateway.execute(sessionId, gatewayParams).join();

            long duration = System.currentTimeMillis() - start;
            if (result.success()) {
                Map<String, Object> data = new HashMap<>();
                data.put("output", result.getOutput());
                data.put("duration_ms", result.durationMs());
                data.put("execution_id", result.executionId());
                data.put("action", normalizedAction);

                Map<String, Object> metrics = result.metrics();
                if (metrics != null && !metrics.isEmpty()) {
                    data.put("events", metrics.getOrDefault("stream_events", List.of()));
                    data.put("event_count", metrics.getOrDefault("stream_event_count", 0));
                    data.put("parsed_session_id", metrics.get("parsed_session_id"));
                    data.put("provider", metrics.getOrDefault("provider", "claude-cli"));
                    data.put("raw_metrics", metrics);
                }

                stats = stats.recordCall(true, duration);
                return ToolResult.success(data);
            }

            stats = stats.recordCall(false, duration);
            return ToolResult.failure("Claude CLI command failed: " + result.stderr());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            stats = stats.recordCall(false, duration);
            log.error("Failed to execute Claude CLI action: {}", normalizedAction, e);
            return ToolResult.failure("Execution error: " + e.getMessage());
        }
    }

    private Map<String, Object> buildGatewayParams(String action, ToolParams params) {
        Map<String, Object> out = new HashMap<>();
        out.put("provider", "claude-cli");
        out.put("action", action);

        Optional.ofNullable(params.getString("prompt")).ifPresent(v -> out.put("prompt", v));
        Optional.ofNullable(params.getString("resume_session_id")).ifPresent(v -> out.put("resume_session_id", v));
        Optional.ofNullable(params.getBoolean("fork_session")).ifPresent(v -> out.put("fork_session", v));
        Optional.ofNullable(params.getString("output_format")).ifPresent(v -> out.put("output_format", v));
        Optional.ofNullable(params.getBoolean("verbose")).ifPresent(v -> out.put("verbose", v));
        Optional.ofNullable(params.getString("model")).ifPresent(v -> out.put("model", v));
        Optional.ofNullable(params.getBoolean("worktree")).ifPresent(v -> out.put("worktree", v));
        Optional.ofNullable(params.getString("system_prompt")).ifPresent(v -> out.put("system_prompt", v));
        Object maxTurns = params.get("max_turns");
        if (maxTurns instanceof Number number) {
            out.put("max_turns", number);
        }
        // 与 free-claude-code-main 默认行为保持一致：默认关闭权限确认
        out.put("dangerously_skip_permissions", true);

        Object allowedDirs = params.get("allowed_dirs");
        if (allowedDirs instanceof List<?>) {
            out.put("allowed_dirs", allowedDirs);
        }

        Object addDir = params.get("add_dir");
        if (addDir instanceof List<?>) {
            out.put("add_dir", addDir);
        }

        Object options = params.get("options");
        if (options instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    out.put(key, entry.getValue());
                }
            }
        }

        return out;
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy != null && policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        // 与 TraeTool 保持一致；若后续引入外部目录访问可切换为 true
        return false;
    }

    @Override
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    public void closeSession(String sessionId) {
        executionGateway.closeSession(sessionId);
    }

    public void closeAllSessions() {
        executionGateway.closeAllSessions();
    }
}
