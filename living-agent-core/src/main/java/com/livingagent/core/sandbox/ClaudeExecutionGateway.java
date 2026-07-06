package com.livingagent.core.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Claude CLI 执行网关：
 * - 管理 Claude CLI 会话到 SandboxSession 的映射
 * - 将结构化参数转换为 claude CLI 参数
 * - 在当前 SandboxSession 能力下提供“准流式”解析（基于 stream-json 行解析）
 * - 提供异步任务模型：start / poll / cancel
 */
public class ClaudeExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(ClaudeExecutionGateway.class);
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\"(?:session_id|sessionId)\"\\s*:\\s*\"([^\"]+)\"");

    private final SandboxService sandboxService;
    private final ConcurrentMap<String, SandboxSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ClaudeSessionState> sessionStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ClaudeAsyncJob> asyncJobs = new ConcurrentHashMap<>();
    private final ClaudeCliProperties claudeCliProperties;

    // P22: 可选的事件发布器，用于失败/超时时触发反馈闭环
    private volatile org.springframework.context.ApplicationEventPublisher eventPublisher;

    public ClaudeExecutionGateway(SandboxService sandboxService, ClaudeCliProperties claudeCliProperties) {
        this.sandboxService = sandboxService;
        this.claudeCliProperties = claudeCliProperties;
    }

    public void setEventPublisher(org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public CompletableFuture<ExecutionResult> execute(String sessionId, Map<String, Object> params) {
        SandboxSession session = getOrCreateSession(sessionId);
        if (session == null) {
            return CompletableFuture.completedFuture(
                ExecutionResult.error("claude-no-session", "Failed to create sandbox session")
            );
        }

        String action = stringValue(params.get("action"), "prompt").toLowerCase();
        List<String> args = buildClaudeArgs(action, params);
        Map<String, String> env = buildEnvironment(params);
        long start = System.currentTimeMillis();

        log.debug("Claude CLI execute: sessionId={}, action={}, args={}, envKeys={}",
            sessionId, action, args, env.keySet());
        return session.executeCommand(claudeCliProperties.getCommand(), args, env)
            .thenApply(result -> enrichResult(sessionId, action, params, result, start));
    }

    public CompletableFuture<ExecutionResult> executeWithProxy(String sessionId, Map<String, Object> params, Map<String, String> proxyEnv) {
        SandboxSession session = getOrCreateSession(sessionId);
        if (session == null) {
            return CompletableFuture.completedFuture(
                ExecutionResult.error("claude-no-session", "Failed to create sandbox session")
            );
        }

        String action = stringValue(params.get("action"), "prompt").toLowerCase();
        List<String> args = buildClaudeArgs(action, params);
        Map<String, String> env = buildEnvironment(params);
        if (proxyEnv != null) {
            env.putAll(proxyEnv);
        }
        long start = System.currentTimeMillis();

        log.debug("Claude CLI proxy execute: sessionId={}, action={}, args={}, envKeys={}",
            sessionId, action, args, env.keySet());
        return session.executeCommand(claudeCliProperties.getCommand(), args, env)
            .thenApply(result -> enrichResult(sessionId, action, params, result, start));
    }

    public Map<String, Object> startAsyncJob(String sessionId, Map<String, Object> params) {
        String action = stringValue(params.get("action"), "prompt").toLowerCase();
        String sid = normalizeSessionId(sessionId);
        String jobId = "claude-job-" + UUID.randomUUID().toString().substring(0, 8);

        CompletableFuture<ExecutionResult> future = execute(sid, params);
        ClaudeAsyncJob job = new ClaudeAsyncJob(jobId, sid, action, Instant.now(), future);
        asyncJobs.put(jobId, job);

        return Map.of(
            "job_id", jobId,
            "session_id", sid,
            "action", action,
            "state", "running",
            "started_at", job.startedAt
        );
    }

    public Map<String, Object> pollAsyncJob(String jobId) {
        ClaudeAsyncJob job = asyncJobs.get(jobId);
        if (job == null) {
            return Map.of(
                "job_id", jobId,
                "state", "not_found"
            );
        }

        if (!job.future.isDone()) {
            return Map.of(
                "job_id", jobId,
                "session_id", job.sessionId,
                "action", job.action,
                "state", "running",
                "started_at", job.startedAt
            );
        }

        try {
            ExecutionResult result = job.future.join();
            Map<String, Object> out = new HashMap<>();
            out.put("job_id", jobId);
            out.put("session_id", job.sessionId);
            out.put("action", job.action);
            out.put("state", "completed");
            out.put("success", result.success());
            out.put("exit_code", result.exitCode());
            out.put("output", result.getOutput());
            out.put("duration_ms", result.durationMs());
            out.put("execution_id", result.executionId());
            if (result.metrics() != null && !result.metrics().isEmpty()) {
                out.put("events", result.metrics().getOrDefault("stream_events", List.of()));
                out.put("event_count", result.metrics().getOrDefault("stream_event_count", 0));
                out.put("parsed_session_id", result.metrics().get("parsed_session_id"));
                out.put("raw_metrics", result.metrics());
            }
            return out;
        } catch (Exception e) {
            return Map.of(
                "job_id", jobId,
                "session_id", job.sessionId,
                "action", job.action,
                "state", "failed",
                "error", e.getMessage()
            );
        }
    }

    public Map<String, Object> cancelAsyncJob(String jobId) {
        ClaudeAsyncJob job = asyncJobs.get(jobId);
        if (job == null) {
            return Map.of(
                "job_id", jobId,
                "state", "not_found"
            );
        }

        boolean cancelled = job.future.cancel(true);
        String state = cancelled ? "cancelled" : (job.future.isDone() ? "completed" : "running");

        return Map.of(
            "job_id", jobId,
            "session_id", job.sessionId,
            "action", job.action,
            "state", state,
            "cancelled", cancelled
        );
    }

    public Map<String, Object> getSessionSnapshot(String sessionId) {
        String sid = normalizeSessionId(sessionId);
        ClaudeSessionState state = sessionStates.get(sid);
        if (state == null) {
            return Map.of("session_id", sid, "state", "unknown");
        }
        return state.toMap();
    }

    public void closeSession(String sessionId) {
        String sid = normalizeSessionId(sessionId);
        SandboxSession session = sessions.remove(sid);
        if (session != null) {
            session.close();
        }
        sessionStates.remove(sid);
    }

    public void closeAllSessions() {
        sessions.values().forEach(SandboxSession::close);
        sessions.clear();
        sessionStates.clear();
        asyncJobs.clear();
    }

    private ExecutionResult enrichResult(String sessionId,
                                         String action,
                                         Map<String, Object> params,
                                         ExecutionResult result,
                                         long startTime) {
        String sid = normalizeSessionId(sessionId);
        long gatewayDuration = System.currentTimeMillis() - startTime;
        List<String> eventLines = extractJsonEventLines(result.stdout());
        String parsedClaudeSessionId = extractSessionId(eventLines);

        // P22-B: 返回码检查与超时检测
        boolean timeoutDetected = gatewayDuration > claudeCliProperties.getJobTimeoutMinutes() * 60_000L;
        boolean outputEmpty = (result.stdout() == null || result.stdout().isBlank())
            && (result.stderr() == null || result.stderr().isBlank());
        if (timeoutDetected) {
            log.warn("Claude CLI timeout detected: {}ms (threshold: {}min) sessionId={}",
                gatewayDuration, claudeCliProperties.getJobTimeoutMinutes(), sid);
        }
        if (!result.success() && outputEmpty) {
            log.error("Claude CLI failed with empty output: exitCode={} sessionId={}",
                result.exitCode(), sid);
        }

        // P22: 失败/超时时发布 EvolutionSignal 触发反馈闭环
        if ((timeoutDetected || !result.success()) && eventPublisher != null) {
            try {
                com.livingagent.core.evolution.signal.EvolutionSignal signal = new com.livingagent.core.evolution.signal.EvolutionSignal(
                    com.livingagent.core.evolution.signal.EvolutionSignal.SignalType.ERROR,
                    String.format("Claude CLI %s: exitCode=%d, sessionId=%s",
                        timeoutDetected ? "timeout" : "failure", result.exitCode(), sid)
                );
                signal.setSource("claude-cli-gateway");
                signal.setCategory(com.livingagent.core.evolution.signal.EvolutionSignal.SignalCategory.REPAIR);
                signal.addTag("claude-cli");
                if (timeoutDetected) signal.addTag("timeout");
                signal.addMetadata("exitCode", result.exitCode());
                signal.addMetadata("timeout", timeoutDetected);
                signal.addMetadata("sessionId", sid);
                eventPublisher.publishEvent(signal);
                log.debug("P22: Published EvolutionSignal for Claude CLI failure (timeout={}, exitCode={})", timeoutDetected, result.exitCode());
            } catch (Exception e) {
                log.warn("P22: Failed to publish EvolutionSignal: {}", e.getMessage());
            }
        }

        // P22-C: 输出解析验证
        String outputFormat = stringValue(params.get("output_format"), "stream-json");
        boolean parseSuccess = true;
        if ("stream-json".equals(outputFormat) && !result.stdout().isBlank()) {
            parseSuccess = !eventLines.isEmpty();
            if (!parseSuccess) {
                log.warn("Claude CLI stream-json output has no parseable JSON lines, falling back to raw text: sessionId={}", sid);
            }
        }

        Map<String, Object> metrics = new HashMap<>(result.metrics() != null ? result.metrics() : Map.of());
        metrics.put("provider", "claude-cli");
        metrics.put("action", action);
        metrics.put("stream_event_count", eventLines.size());
        metrics.put("stream_events", eventLines);
        metrics.put("requested_output_format", outputFormat);
        metrics.put("gateway_duration_ms", gatewayDuration);
        metrics.put("timeout_detected", timeoutDetected);
        metrics.put("output_parse_success", parseSuccess);
        if (parsedClaudeSessionId != null) {
            metrics.put("parsed_session_id", parsedClaudeSessionId);
        }

        sessionStates.compute(sid, (k, old) -> {
            ClaudeSessionState next = old != null ? old : new ClaudeSessionState(sid);
            next.lastAction = action;
            next.lastUpdatedAt = Instant.now();
            next.lastExitCode = result.exitCode();
            next.lastSuccess = result.success();
            next.lastError = result.stderr();
            next.lastEventCount = eventLines.size();
            if (parsedClaudeSessionId != null && !parsedClaudeSessionId.isBlank()) {
                next.claudeSessionId = parsedClaudeSessionId;
            }
            return next;
        });

        return new ExecutionResult(
            result.executionId(),
            result.success(),
            result.exitCode(),
            result.stdout(),
            result.stderr(),
            result.durationMs(),
            metrics,
            result.executedAt()
        );
    }

    private SandboxSession getOrCreateSession(String sessionId) {
        String sid = normalizeSessionId(sessionId);
        return sessions.computeIfAbsent(sid, id -> {
            if (!sandboxService.isAvailable()) {
                log.warn("Sandbox service unavailable for Claude session: {}", id);
                return null;
            }
            Optional<SandboxSession> created = sandboxService.createSession(id, SandboxService.SandboxConfig.TRAE_DEFAULT);
            if (created.isEmpty()) {
                log.warn("Failed to create Claude sandbox session: {}", id);
            }
            return created.orElse(null);
        });
    }

    private List<String> buildClaudeArgs(String action, Map<String, Object> params) {
        List<String> args = new ArrayList<>();

        if ("resume".equals(action)) {
            String resumeSessionId = stringValue(params.get("resume_session_id"), null);
            if (resumeSessionId != null && !resumeSessionId.isBlank()) {
                args.add("--resume");
                args.add(resumeSessionId);
            }
            if (booleanValue(params.get("fork_session"))) {
                args.add("--fork-session");
            }
        }

        if ("status".equals(action)) {
            args.add("--version");
            return args;
        }

        String prompt = stringValue(params.get("prompt"), null);
        if (prompt != null && !prompt.isBlank()) {
            args.add("-p");
            args.add(prompt);
        }

        String model = stringValue(params.get("model"), null);
        if (model != null && !model.isBlank()) {
            args.add("--model");
            args.add(model);
        }

        args.add("--output-format");
        args.add(stringValue(params.get("output_format"), "stream-json"));

        if (booleanValue(params.get("verbose"))) {
            args.add("--verbose");
        }

        if (booleanValue(params.get("worktree"))) {
            args.add("--worktree");
        }

        if (booleanValue(params.get("dangerously_skip_permissions"))) {
            args.add("--dangerously-skip-permissions");
        }

        Object allowedDirs = params.get("allowed_dirs");
        if (allowedDirs instanceof List<?> list) {
            for (Object d : list) {
                if (d != null) {
                    args.add("--add-dir");
                    args.add(String.valueOf(d));
                }
            }
        }

        Object addDir = params.get("add_dir");
        if (addDir instanceof List<?> list) {
            for (Object d : list) {
                if (d != null) {
                    args.add("--add-dir");
                    args.add(String.valueOf(d));
                }
            }
        }

        String systemPrompt = stringValue(params.get("system_prompt"), null);

        // 按部门注入技能目录
        String skillsBaseDir = "/app/skills";
        args.add("--add-dir");
        args.add(skillsBaseDir + "/core");
        String department = stringValue(params.get("department"),
            claudeCliProperties.getProxy().getDefaultDepartmentId());
        if (department != null && !department.isBlank()) {
            args.add("--add-dir");
            args.add(skillsBaseDir + "/" + department);
        }

        // 服务发现系统提示词
        StringBuilder servicePrompt = new StringBuilder();
        servicePrompt.append("你是 Living Agent Service 的数字员工，通过 Claude Code CLI 执行任务。\n\n");
        servicePrompt.append("## 可用服务\n");
        servicePrompt.append("- PostgreSQL: psql -h postgres -U livingagent -d livingagent\n");
        servicePrompt.append("- Redis: redis-cli -h redis\n");
        servicePrompt.append("- Qdrant: curl http://qdrant:6333/collections\n");
        servicePrompt.append("- Jenkins: curl http://jenkins:8080/api/json\n");
        servicePrompt.append("- GitLab: git remote add origin http://gitlab:8929/...\n");
        servicePrompt.append("- OpenProject: curl http://openproject:8080/api/v3/projects\n");
        servicePrompt.append("- MemOS: curl http://memos:8381/openapi.json\n");
        servicePrompt.append("- RuView: curl http://ruview-sensing:3000/health\n\n");
        servicePrompt.append("## 当前部门技能\n");
        servicePrompt.append("核心技能: ").append(skillsBaseDir).append("/core/\n");
        if (department != null && !department.isBlank()) {
            servicePrompt.append("部门技能: ").append(skillsBaseDir).append("/").append(department).append("/\n");
        }
        servicePrompt.append("参考 SKILL.md 文件获取技能详细说明。\n");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            servicePrompt.append("\n").append(systemPrompt);
        }
        args.add("--append-system-prompt");
        args.add(servicePrompt.toString());

        Object maxTurns = params.get("max_turns");
        if (maxTurns instanceof Number number) {
            args.add("--max-turns");
            args.add(String.valueOf(number.intValue()));
        }

        String settingsJson = stringValue(params.get("settings_json"), null);
        if (settingsJson != null && !settingsJson.isBlank()) {
            args.add("--settings");
            args.add(settingsJson);
        }

        if (claudeCliProperties.isMcpEnabled()) {
            String mcpConfigPath = stringValue(params.get("mcp_config_path"),
                claudeCliProperties.getMcpConfigPath());
            if (mcpConfigPath != null && !mcpConfigPath.isBlank()) {
                if (mcpConfigPath.startsWith("classpath:")) {
                    String resourcePath = mcpConfigPath.substring("classpath:".length());
                    var resource = getClass().getClassLoader().getResource(resourcePath);
                    if (resource != null) {
                        args.add("--mcp-config");
                        args.add(resource.getPath());
                    } else {
                        log.warn("MCP config resource not found: {}", mcpConfigPath);
                    }
                } else {
                    args.add("--mcp-config");
                    args.add(mcpConfigPath);
                }
            }
        }

        return args;
    }

    private Map<String, String> buildEnvironment(Map<String, Object> params) {
        Map<String, String> env = new HashMap<>();
        ClaudeCliProperties.Proxy proxyConfig = claudeCliProperties.getProxy();
        env.put("ANTHROPIC_API_KEY", proxyConfig.getApiKeyPlaceholder());
        env.put("ANTHROPIC_BASE_URL", proxyConfig.getBaseUrl());
        env.put("ANTHROPIC_API_URL", proxyConfig.getApiUrl());
        env.put("TERM", "dumb");
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("CLAUDE_CODE_SHELL", claudeCliProperties.getCommand());
        if (claudeCliProperties.isBashNoLogin()) {
            env.put("CLAUDE_BASH_NO_LOGIN", "1");
        }
        env.put("CLAUDE_PLUGINS_DIR", "/home/livingagent/.claude/plugins");

        // CodeGraph 环境变量
        if (claudeCliProperties.getCodegraph().isEnabled()) {
            ClaudeCliProperties.Codegraph cg = claudeCliProperties.getCodegraph();
            env.put("CODEGRAPH_WATCH_DEBOUNCE_MS", String.valueOf(cg.getWatchDebounceMs()));
            if (!cg.isAutoSync()) {
                env.put("CODEGRAPH_NO_DAEMON", "1");
            }
        }

        // 服务对接环境变量
        env.put("GITLAB_URL", System.getenv().getOrDefault("GITLAB_BASE_URL", "http://gitlab:8929"));
        env.put("JENKINS_URL", System.getenv().getOrDefault("JENKINS_BASE_URL", "http://jenkins:8080"));
        env.put("OPENPROJECT_URL", System.getenv().getOrDefault("OPENPROJECT_BASE_URL", "http://openproject:8080"));

        String cliPath = stringValue(params.get("claude_path"), null);
        if (cliPath != null && !cliPath.isBlank()) {
            env.put("CLAUDE_CODE_SHELL", cliPath);
        }
        String shellOverride = stringValue(params.get("shell"), null);
        if (shellOverride != null && !shellOverride.isBlank()) {
            env.put("CLAUDE_CODE_SHELL", shellOverride);
        }

        Object extraEnv = params.get("env");
        if (extraEnv instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() != null) {
                    env.put(key, String.valueOf(entry.getValue()));
                }
            }
        }

        return env;
    }

    private String normalizeSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
    }

    private List<String> extractJsonEventLines(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : stdout.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private String extractSessionId(List<String> eventLines) {
        for (String line : eventLines) {
            Matcher matcher = SESSION_ID_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static String stringValue(Object value, String defaultValue) {
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean b && b;
    }

    private static final class ClaudeSessionState {
        private final String sessionId;
        private String claudeSessionId;
        private String lastAction;
        private boolean lastSuccess;
        private int lastExitCode;
        private String lastError;
        private int lastEventCount;
        private Instant lastUpdatedAt;

        private ClaudeSessionState(String sessionId) {
            this.sessionId = sessionId;
            this.lastUpdatedAt = Instant.now();
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new HashMap<>();
            out.put("session_id", sessionId);
            out.put("claude_session_id", claudeSessionId);
            out.put("last_action", lastAction);
            out.put("last_success", lastSuccess);
            out.put("last_exit_code", lastExitCode);
            out.put("last_error", lastError);
            out.put("last_event_count", lastEventCount);
            out.put("last_updated_at", lastUpdatedAt);
            out.put("state", "active");
            return out;
        }
    }

    private static final class ClaudeAsyncJob {
        private final String jobId;
        private final String sessionId;
        private final String action;
        private final Instant startedAt;
        private final CompletableFuture<ExecutionResult> future;

        private ClaudeAsyncJob(String jobId,
                               String sessionId,
                               String action,
                               Instant startedAt,
                               CompletableFuture<ExecutionResult> future) {
            this.jobId = jobId;
            this.sessionId = sessionId;
            this.action = action;
            this.startedAt = startedAt;
            this.future = future;
        }
    }
}
