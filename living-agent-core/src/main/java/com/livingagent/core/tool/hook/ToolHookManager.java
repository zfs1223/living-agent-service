package com.livingagent.core.tool.hook;

import com.livingagent.core.tool.ToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class ToolHookManager {

    private static final Logger log = LoggerFactory.getLogger(ToolHookManager.class);

    private final Map<String, PreToolHook> preToolHooks = new ConcurrentHashMap<>();
    private final Map<String, PostToolHook> postToolHooks = new ConcurrentHashMap<>();
    private final Map<String, ToolErrorHook> errorHooks = new ConcurrentHashMap<>();
    private final Map<String, ToolTimeoutHook> timeoutHooks = new ConcurrentHashMap<>();

    private volatile boolean enabled = true;
    private volatile boolean logOnlyMode = false;

    @PostConstruct
    public void init() {
        log.info("ToolHookManager initialized with {} pre-hooks, {} post-hooks, {} error-hooks",
                preToolHooks.size(), postToolHooks.size(), errorHooks.size());
    }

    public void registerPreToolHook(String name, PreToolHook hook) {
        preToolHooks.put(name, hook);
        log.info("Registered PreToolHook: {}", name);
    }

    public void registerPostToolHook(String name, PostToolHook hook) {
        postToolHooks.put(name, hook);
        log.info("Registered PostToolHook: {}", name);
    }

    public void registerErrorHook(String name, ToolErrorHook hook) {
        errorHooks.put(name, hook);
        log.info("Registered ToolErrorHook: {}", name);
    }

    public void registerTimeoutHook(String name, ToolTimeoutHook hook) {
        timeoutHooks.put(name, hook);
        log.info("Registered ToolTimeoutHook: {}", name);
    }

    public void unregisterPreToolHook(String name) {
        preToolHooks.remove(name);
        log.info("Unregistered PreToolHook: {}", name);
    }

    public void unregisterPostToolHook(String name) {
        postToolHooks.remove(name);
        log.info("Unregistered PostToolHook: {}", name);
    }

    public ToolHookResult executePreHooks(String toolName, ToolContext context) {
        if (!enabled) {
            return ToolHookResult.allow("Hooks disabled");
        }

        if (preToolHooks.isEmpty()) {
            return ToolHookResult.allow("No pre-hooks registered");
        }

        for (Map.Entry<String, PreToolHook> entry : preToolHooks.entrySet()) {
            String hookName = entry.getKey();
            PreToolHook hook = entry.getValue();

            try {
                ToolHookResult result = hook.execute(toolName, context);

                if (logOnlyMode) {
                    log.debug("PreHook [{}] for tool [{}]: {}", hookName, toolName, result);
                    continue;
                }

                if (result.isDenied()) {
                    log.warn("PreHook [{}] DENIED tool [{}]: {}", hookName, toolName, result.getMessage());
                    return result;
                }

                if (result.isWarn()) {
                    log.warn("PreHook [{}] WARNED on tool [{}]: {}", hookName, toolName, result.getMessage());
                    return result;
                }

            } catch (Exception e) {
                log.error("PreHook [{}] failed for tool [{}]: {}", hookName, toolName, e.getMessage());
                if (!logOnlyMode) {
                    return ToolHookResult.deny("Hook execution failed: " + e.getMessage());
                }
            }
        }

        return ToolHookResult.allow();
    }

    public ToolHookResult executePostHooks(String toolName, ToolContext context, Object result) {
        if (!enabled) {
            return ToolHookResult.allow("Hooks disabled");
        }

        if (postToolHooks.isEmpty()) {
            return ToolHookResult.allow("No post-hooks registered");
        }

        for (Map.Entry<String, PostToolHook> entry : postToolHooks.entrySet()) {
            String hookName = entry.getKey();
            PostToolHook hook = entry.getValue();

            try {
                ToolHookResult hookResult = hook.execute(toolName, context, result);

                if (logOnlyMode) {
                    log.debug("PostHook [{}] for tool [{}]: {}", hookName, toolName, hookResult);
                    continue;
                }

                if (hookResult.isDenied()) {
                    log.warn("PostHook [{}] DENIED result for tool [{}]: {}", hookName, toolName, hookResult.getMessage());
                    return hookResult;
                }

            } catch (Exception e) {
                log.error("PostHook [{}] failed for tool [{}]: {}", hookName, toolName, e.getMessage());
                if (!logOnlyMode) {
                    return ToolHookResult.deny("Post-hook execution failed: " + e.getMessage());
                }
            }
        }

        return ToolHookResult.allow();
    }

    public ToolHookResult executeErrorHooks(String toolName, ToolContext context, Throwable error) {
        if (!enabled) {
            return ToolHookResult.allow("Hooks disabled");
        }

        if (errorHooks.isEmpty()) {
            return ToolHookResult.allow("No error-hooks registered");
        }

        for (Map.Entry<String, ToolErrorHook> entry : errorHooks.entrySet()) {
            String hookName = entry.getKey();
            ToolErrorHook hook = entry.getValue();

            try {
                ToolHookResult result = hook.execute(toolName, context, error);

                if (logOnlyMode) {
                    log.debug("ErrorHook [{}] for tool [{}]: {}", hookName, toolName, result);
                    continue;
                }

                if (result.isDenied()) {
                    log.warn("ErrorHook [{}] DENIED error handling for tool [{}]: {}", hookName, toolName, result.getMessage());
                    return result;
                }

            } catch (Exception e) {
                log.error("ErrorHook [{}] failed for tool [{}]: {}", hookName, toolName, e.getMessage());
            }
        }

        return ToolHookResult.allow();
    }

    public ToolHookResult executeTimeoutHooks(String toolName, ToolContext context) {
        if (!enabled) {
            return ToolHookResult.allow("Hooks disabled");
        }

        if (timeoutHooks.isEmpty()) {
            return ToolHookResult.allow("No timeout-hooks registered");
        }

        for (Map.Entry<String, ToolTimeoutHook> entry : timeoutHooks.entrySet()) {
            String hookName = entry.getKey();
            ToolTimeoutHook hook = entry.getValue();

            try {
                ToolHookResult result = hook.execute(toolName, context);

                if (logOnlyMode) {
                    log.debug("TimeoutHook [{}] for tool [{}]: {}", hookName, toolName, result);
                    continue;
                }

                if (result.isDenied()) {
                    log.warn("TimeoutHook [{}] DENIED timeout handling for tool [{}]: {}", hookName, toolName, result.getMessage());
                    return result;
                }

            } catch (Exception e) {
                log.error("TimeoutHook [{}] failed for tool [{}]: {}", hookName, toolName, e.getMessage());
            }
        }

        return ToolHookResult.allow();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("ToolHookManager enabled={}", enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setLogOnlyMode(boolean logOnlyMode) {
        this.logOnlyMode = logOnlyMode;
        log.info("ToolHookManager logOnlyMode={}", logOnlyMode);
    }

    public boolean isLogOnlyMode() {
        return logOnlyMode;
    }

    public int getPreHookCount() {
        return preToolHooks.size();
    }

    public int getPostHookCount() {
        return postToolHooks.size();
    }

    public int getErrorHookCount() {
        return errorHooks.size();
    }

    public int getTimeoutHookCount() {
        return timeoutHooks.size();
    }

    public void clearAllHooks() {
        preToolHooks.clear();
        postToolHooks.clear();
        errorHooks.clear();
        timeoutHooks.clear();
        log.info("Cleared all tool hooks");
    }

    @FunctionalInterface
    public interface PreToolHook {
        ToolHookResult execute(String toolName, ToolContext context);
    }

    @FunctionalInterface
    public interface PostToolHook {
        ToolHookResult execute(String toolName, ToolContext context, Object result);
    }

    @FunctionalInterface
    public interface ToolErrorHook {
        ToolHookResult execute(String toolName, ToolContext context, Throwable error);
    }

    @FunctionalInterface
    public interface ToolTimeoutHook {
        ToolHookResult execute(String toolName, ToolContext context);
    }

    public static class LoggingPreToolHook implements PreToolHook {
        private final String name;

        public LoggingPreToolHook(String name) {
            this.name = name;
        }

        @Override
        public ToolHookResult execute(String toolName, ToolContext context) {
            return ToolHookResult.allow("Logged: " + toolName);
        }
    }
}
