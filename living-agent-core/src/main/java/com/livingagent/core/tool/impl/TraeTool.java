package com.livingagent.core.tool.impl;

import com.livingagent.core.sandbox.*;
import com.livingagent.core.tool.*;
import com.livingagent.core.security.SecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TraeTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(TraeTool.class);
    private static final String NAME = "trae";
    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = """
        Trae CLI: AI-powered development assistant for code generation, review, and testing.
        
        Actions:
        - init: Initialize a new project (args: project_type)
        - generate: Generate code from description (args: description)
        - review: Review code for issues (args: file_path)
        - test: Run tests (args: test_path)
        - refactor: Refactor code (args: file_path, description)
        - debug: Debug code issues (args: file_path, error_message)
        """;
    
    private static final String DEPARTMENT = "tech";
    
    private final SandboxService sandboxService;
    private final Map<String, SandboxSession> sessions;
    private final ToolSchema schema;
    private volatile ToolStats stats;

    public TraeTool(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
        this.sessions = new ConcurrentHashMap<>();
        this.schema = buildSchema();
        this.stats = ToolStats.empty(NAME);
    }

    private ToolSchema buildSchema() {
        return ToolSchema.builder()
            .name(NAME)
            .description(DESCRIPTION)
            .parameter("action", "string", "操作类型: init, generate, review, test, refactor, debug", true)
            .parameter("project_type", "string", "项目类型: spring-boot, react, vue, python, rust (用于 init)", false)
            .parameter("description", "string", "功能描述 (用于 generate, refactor)", false)
            .parameter("file_path", "string", "文件路径 (用于 review, refactor, debug)", false)
            .parameter("test_path", "string", "测试路径 (用于 test)", false)
            .parameter("error_message", "string", "错误信息 (用于 debug)", false)
            .parameter("options", "object", "额外选项", false)
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
        return List.of("code_generation", "code_review", "testing", "refactoring", "debugging");
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("Missing required parameter: action");
        }
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            return ToolResult.failure("Missing required parameter: action");
        }
        
        String sessionId = context.sessionId();
        SandboxSession session = getOrCreateSession(sessionId);
        
        if (session == null) {
            return ToolResult.failure("Failed to create sandbox session");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> traeParams = buildTraeParams(params);
            
            ExecutionResult result = session.executeTraeCommand(action, traeParams).join();
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (result.success()) {
                Map<String, Object> data = new HashMap<>();
                data.put("output", result.getOutput());
                data.put("duration_ms", result.durationMs());
                data.put("execution_id", result.executionId());
                
                stats = stats.recordCall(true, duration);
                return ToolResult.success(data);
            } else {
                stats = stats.recordCall(false, duration);
                return ToolResult.failure("Trae command failed: " + result.stderr());
            }
            
        } catch (Exception e) {
            log.error("Failed to execute Trae command: {}", action, e);
            long duration = System.currentTimeMillis() - startTime;
            stats = stats.recordCall(false, duration);
            return ToolResult.failure("Execution error: " + e.getMessage());
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
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }
    
    private SandboxSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            if (sandboxService.isAvailable()) {
                return sandboxService.createSession(id, SandboxService.SandboxConfig.TRAE_DEFAULT)
                    .orElse(null);
            }
            return null;
        });
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildTraeParams(ToolParams params) {
        Map<String, Object> traeParams = new HashMap<>();
        
        Optional.ofNullable(params.getString("project_type"))
            .ifPresent(v -> traeParams.put("type", v));
        
        Optional.ofNullable(params.getString("description"))
            .ifPresent(v -> traeParams.put("description", v));
        
        Optional.ofNullable(params.getString("file_path"))
            .ifPresent(v -> traeParams.put("file", v));
        
        Optional.ofNullable(params.getString("test_path"))
            .ifPresent(v -> traeParams.put("path", v));
        
        Optional.ofNullable(params.getString("error_message"))
            .ifPresent(v -> traeParams.put("error", v));
        
        Object options = params.get("options");
        if (options instanceof Map) {
            traeParams.putAll((Map<String, Object>) options);
        }
        
        return traeParams;
    }
    
    public void closeSession(String sessionId) {
        SandboxSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }
    
    public void closeAllSessions() {
        sessions.values().forEach(SandboxSession::close);
        sessions.clear();
    }
}
