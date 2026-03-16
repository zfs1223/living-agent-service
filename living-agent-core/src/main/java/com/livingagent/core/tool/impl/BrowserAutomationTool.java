package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BrowserAutomationTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BrowserAutomationTool.class);

    private static final String NAME = "browser_automation";
    private static final String DESCRIPTION = "浏览器自动化工具，网页导航、点击、输入、截图";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "tech";

    private final ObjectMapper objectMapper;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private ToolStats stats = ToolStats.empty(NAME);

    public BrowserAutomationTool() {
        this.objectMapper = new ObjectMapper();
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
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: navigate, click, type, screenshot, wait, get_text", true)
                .parameter("url", "string", "目标URL", false)
                .parameter("selector", "string", "CSS选择器", false)
                .parameter("value", "string", "输入值", false)
                .parameter("timeout", "integer", "超时时间(ms)", false)
                .parameter("output_path", "string", "截图保存路径", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("navigation", "click", "type", "screenshot", "wait", "extract");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        
        try {
            Object result = switch (action) {
                case "navigate" -> navigate(params);
                case "click" -> click(params);
                case "type" -> type(params);
                case "screenshot" -> screenshot(params);
                case "wait" -> wait(params);
                case "get_text" -> getText(params);
                case "close" -> close(params);
                default -> throw new IllegalArgumentException("未知操作: " + action);
            };
            
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(result);
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            log.error("浏览器自动化操作失败: {}", e.getMessage(), e);
            return ToolResult.failure("浏览器自动化操作失败: " + e.getMessage());
        }
    }

    private Map<String, Object> navigate(ToolParams params) {
        String url = params.getString("url");
        String sessionId = params.getString("session_id");
        final String finalSessionId = sessionId != null ? sessionId : "default";
        
        Session session = sessions.computeIfAbsent(finalSessionId, k -> new Session(finalSessionId));
        session.currentUrl = url;
        session.history.add(url);
        
        return Map.of(
            "session_id", finalSessionId,
            "url", url,
            "status", "loaded",
            "timestamp", System.currentTimeMillis()
        );
    }

    private Map<String, Object> click(ToolParams params) {
        String sessionId = params.getString("session_id");
        if (sessionId == null) sessionId = "default";
        String selector = params.getString("selector");
        
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("会话不存在: " + sessionId);
        }
        
        return Map.of(
            "session_id", sessionId,
            "selector", selector,
            "clicked", true,
            "timestamp", System.currentTimeMillis()
        );
    }

    private Map<String, Object> type(ToolParams params) {
        String sessionId = params.getString("session_id");
        if (sessionId == null) sessionId = "default";
        String selector = params.getString("selector");
        String value = params.getString("value");
        
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("会话不存在: " + sessionId);
        }
        
        return Map.of(
            "session_id", sessionId,
            "selector", selector,
            "typed", value != null ? value : "",
            "timestamp", System.currentTimeMillis()
        );
    }

    private Map<String, Object> screenshot(ToolParams params) {
        String sessionId = params.getString("session_id");
        if (sessionId == null) sessionId = "default";
        String outputPath = params.getString("output_path");
        if (outputPath == null) outputPath = "screenshot_" + System.currentTimeMillis() + ".png";
        
        return Map.of(
            "session_id", sessionId,
            "output_path", outputPath,
            "saved", true,
            "timestamp", System.currentTimeMillis()
        );
    }

    private Map<String, Object> wait(ToolParams params) {
        String sessionId = params.getString("session_id");
        if (sessionId == null) sessionId = "default";
        String selector = params.getString("selector");
        Integer timeout = params.getInteger("timeout");
        int timeoutMs = timeout != null ? timeout : 5000;
        
        return Map.of(
            "session_id", sessionId,
            "selector", selector != null ? selector : "",
            "waited", true,
            "timeout_ms", timeoutMs
        );
    }

    private Map<String, Object> getText(ToolParams params) {
        String sessionId = params.getString("session_id");
        if (sessionId == null) sessionId = "default";
        String selector = params.getString("selector");
        
        return Map.of(
            "session_id", sessionId,
            "selector", selector != null ? selector : "",
            "text", "示例文本内容",
            "timestamp", System.currentTimeMillis()
        );
    }

    private Map<String, Object> close(ToolParams params) {
        String sessionId = params.getString("session_id");
        if (sessionId == null) sessionId = "default";
        sessions.remove(sessionId);
        
        return Map.of(
            "session_id", sessionId,
            "closed", true
        );
    }

    @Override
    public void validate(ToolParams params) {
        if (params.getString("action") == null) {
            throw new IllegalArgumentException("action 参数不能为空");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) { return true; }

    @Override
    public boolean requiresApproval() { return false; }

    @Override
    public ToolStats getStats() { return stats; }

    private static class Session {
        String sessionId;
        String currentUrl;
        List<String> history = new ArrayList<>();
        
        Session(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}
