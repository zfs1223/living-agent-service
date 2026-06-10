package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BrowserAutomationTool implements Tool, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BrowserAutomationTool.class);

    private static final String NAME = "browser_automation";
    private static final String DESCRIPTION = "浏览器自动化工具，网页导航、点击、输入、截图";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "tech";

    private volatile Playwright playwright;
    private volatile Browser browser;
    private volatile boolean initFailed = false;
    private String initError = null;

    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();
    private ToolStats stats = ToolStats.empty(NAME);

    public BrowserAutomationTool() {
    }

    private synchronized void ensureBrowser() {
        if (browser != null && browser.isConnected()) {
            return;
        }
        if (initFailed) {
            throw new PlaywrightUnavailableException("Playwright 不可用: " + initError);
        }
        try {
            log.info("懒初始化 Playwright 浏览器...");
            playwright = Playwright.create();

            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList(
                    "--disable-blink-features=AutomationControlled",
                    "--no-sandbox",
                    "--disable-dev-shm-usage"
                ));

            browser = playwright.chromium().launch(options);
            log.info("Playwright 浏览器初始化成功");
        } catch (Exception e) {
            initFailed = true;
            initError = e.getMessage();
            log.error("Playwright 浏览器初始化失败: {}", e.getMessage(), e);
            closeResources();
            throw new PlaywrightUnavailableException("Playwright 初始化失败: " + e.getMessage(), e);
        }
    }

    private BrowserSession getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> {
            ensureBrowser();
            Browser.NewContextOptions ctxOptions = new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setLocale("zh-CN")
                .setTimezoneId("Asia/Shanghai");
            BrowserContext context = browser.newContext(ctxOptions);
            Page page = context.newPage();
            return new BrowserSession(id, context, page);
        });
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
                .parameter("action", "string", "操作类型: navigate, click, type, screenshot, wait, get_text, close", true)
                .parameter("url", "string", "目标URL", false)
                .parameter("selector", "string", "CSS选择器", false)
                .parameter("value", "string", "输入值", false)
                .parameter("timeout", "integer", "超时时间(ms)", false)
                .parameter("output_path", "string", "截图保存路径", false)
                .parameter("session_id", "string", "会话ID", false)
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
        } catch (PlaywrightUnavailableException e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            log.error("Playwright 不可用: {}", e.getMessage());
            return ToolResult.failure("浏览器自动化不可用: " + e.getMessage());
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            log.error("浏览器自动化操作失败: {}", e.getMessage(), e);
            return ToolResult.failure("浏览器自动化操作失败: " + e.getMessage());
        }
    }

    private Map<String, Object> navigate(ToolParams params) {
        String url = params.getString("url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("url 参数不能为空");
        }
        String sessionId = resolveSessionId(params);
        Integer timeoutInt = params.getInteger("timeout");
        int timeoutMs = timeoutInt != null ? timeoutInt : 30000;

        BrowserSession session = getOrCreateSession(sessionId);
        Page page = session.page;

        page.navigate(url, new Page.NavigateOptions().setTimeout(timeoutMs));
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        String title = page.title();
        String currentUrl = page.url();

        return Map.of(
            "session_id", sessionId,
            "url", currentUrl,
            "title", title != null ? title : "",
            "status", "loaded",
            "timestamp", System.currentTimeMillis()
        );
    }

    private Map<String, Object> click(ToolParams params) {
        String sessionId = resolveSessionId(params);
        String selector = params.getString("selector");
        if (selector == null || selector.isEmpty()) {
            throw new IllegalArgumentException("selector 参数不能为空");
        }

        BrowserSession session = getOrCreateSession(sessionId);
        Page page = session.page;

        page.click(selector);

        return Map.of(
            "session_id", sessionId,
            "selector", selector,
            "clicked", true,
            "timestamp", System.currentTimeMillis()
        );
    }

    private Map<String, Object> type(ToolParams params) {
        String sessionId = resolveSessionId(params);
        String selector = params.getString("selector");
        String value = params.getString("value");
        if (selector == null || selector.isEmpty()) {
            throw new IllegalArgumentException("selector 参数不能为空");
        }
        if (value == null) {
            value = "";
        }

        BrowserSession session = getOrCreateSession(sessionId);
        Page page = session.page;

        page.fill(selector, value);

        return Map.of(
            "session_id", sessionId,
            "selector", selector,
            "typed", value,
            "timestamp", System.currentTimeMillis()
        );
    }

    private Map<String, Object> screenshot(ToolParams params) {
        String sessionId = resolveSessionId(params);
        String outputPath = params.getString("output_path");

        BrowserSession session = getOrCreateSession(sessionId);
        Page page = session.page;

        byte[] screenshotBytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        String base64 = Base64.getEncoder().encodeToString(screenshotBytes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", sessionId);
        result.put("base64", base64);
        result.put("size_bytes", screenshotBytes.length);
        if (outputPath != null) {
            result.put("output_path", outputPath);
        }
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    private Map<String, Object> wait(ToolParams params) {
        String sessionId = resolveSessionId(params);
        String selector = params.getString("selector");
        Integer timeoutInt = params.getInteger("timeout");
        int timeoutMs = timeoutInt != null ? timeoutInt : 5000;

        BrowserSession session = getOrCreateSession(sessionId);
        Page page = session.page;

        if (selector != null && !selector.isEmpty()) {
            page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(timeoutMs));
        } else {
            page.waitForTimeout(timeoutMs);
        }

        return Map.of(
            "session_id", sessionId,
            "selector", selector != null ? selector : "",
            "waited", true,
            "timeout_ms", timeoutMs
        );
    }

    private Map<String, Object> getText(ToolParams params) {
        String sessionId = resolveSessionId(params);
        String selector = params.getString("selector");
        if (selector == null || selector.isEmpty()) {
            throw new IllegalArgumentException("selector 参数不能为空");
        }

        BrowserSession session = getOrCreateSession(sessionId);
        Page page = session.page;

        String text = page.textContent(selector);
        if (text == null) {
            text = "";
        }

        return Map.of(
            "session_id", sessionId,
            "selector", selector,
            "text", text.trim(),
            "timestamp", System.currentTimeMillis()
        );
    }

    private Map<String, Object> close(ToolParams params) {
        String sessionId = resolveSessionId(params);
        BrowserSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }

        return Map.of(
            "session_id", sessionId,
            "closed", true
        );
    }

    private String resolveSessionId(ToolParams params) {
        String sessionId = params.getString("session_id");
        return sessionId != null ? sessionId : "default";
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

    @PreDestroy
    @Override
    public void close() {
        for (BrowserSession session : sessions.values()) {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("关闭会话失败: {}", e.getMessage());
            }
        }
        sessions.clear();
        closeResources();
    }

    private void closeResources() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("关闭浏览器失败: {}", e.getMessage());
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                log.warn("关闭 Playwright 失败: {}", e.getMessage());
            }
            playwright = null;
        }
        log.info("BrowserAutomationTool 资源已释放");
    }

    private static class BrowserSession {
        final String sessionId;
        final BrowserContext context;
        final Page page;

        BrowserSession(String sessionId, BrowserContext context, Page page) {
            this.sessionId = sessionId;
            this.context = context;
            this.page = page;
        }

        void close() {
            try {
                if (page != null && !page.isClosed()) {
                    page.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (context != null) {
                    context.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static class PlaywrightUnavailableException extends RuntimeException {
        PlaywrightUnavailableException(String message) {
            super(message);
        }

        PlaywrightUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
