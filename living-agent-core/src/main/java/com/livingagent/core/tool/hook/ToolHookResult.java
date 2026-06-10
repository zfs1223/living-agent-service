package com.livingagent.core.tool.hook;

import com.livingagent.core.tool.ToolContext;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ToolHookResult {

    public enum Decision {
        ALLOW,
        DENY,
        WARN
    }

    private final Decision decision;
    private final String message;
    private final int exitCode;
    private final Map<String, Object> metadata;
    private final Instant timestamp;

    private ToolHookResult(Decision decision, String message, int exitCode) {
        this.decision = decision;
        this.message = message;
        this.exitCode = exitCode;
        this.metadata = new ConcurrentHashMap<>();
        this.timestamp = Instant.now();
    }

    public static ToolHookResult allow() {
        return new ToolHookResult(Decision.ALLOW, "Allowed", 0);
    }

    public static ToolHookResult allow(String message) {
        return new ToolHookResult(Decision.ALLOW, message, 0);
    }

    public static ToolHookResult deny(String message) {
        return new ToolHookResult(Decision.DENY, message, 2);
    }

    public static ToolHookResult deny(String message, int exitCode) {
        return new ToolHookResult(Decision.DENY, message, exitCode);
    }

    public static ToolHookResult warn(String message) {
        return new ToolHookResult(Decision.WARN, message, 1);
    }

    public ToolHookResult withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public Decision getDecision() { return decision; }
    public String getMessage() { return message; }
    public int getExitCode() { return exitCode; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Instant getTimestamp() { return timestamp; }

    public boolean isAllowed() { return decision == Decision.ALLOW; }
    public boolean isDenied() { return decision == Decision.DENY; }
    public boolean isWarn() { return decision == Decision.WARN; }

    @Override
    public String toString() {
        return String.format("ToolHookResult{decision=%s, message='%s', exitCode=%d}",
                decision, message, exitCode);
    }

    public record HookPhase(
            String name,
            ToolHookType type,
            Instant triggeredAt,
            String toolName,
            ToolContext context
    ) {}

    public enum ToolHookType {
        PRE_TOOL_USE,
        POST_TOOL_USE,
        PRE_TOOL_ERROR,
        POST_TOOL_ERROR,
        PRE_TOOL_TIMEOUT,
        POST_TOOL_TIMEOUT
    }

    public static class Builder {
        private Decision decision = Decision.ALLOW;
        private String message = "";
        private int exitCode = 0;
        private Map<String, Object> metadata = new ConcurrentHashMap<>();

        public Builder decision(Decision decision) {
            this.decision = decision;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder exitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public ToolHookResult build() {
            ToolHookResult result = new ToolHookResult(decision, message, exitCode);
            result.metadata.putAll(metadata);
            return result;
        }
    }
}
