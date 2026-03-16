package com.livingagent.core.tool;

import com.livingagent.core.security.SecurityPolicy;

import java.util.List;
import java.util.Map;

public interface Tool {

    String getName();

    String getDescription();

    String getVersion();

    String getDepartment();

    ToolSchema getSchema();

    List<String> getCapabilities();

    ToolResult execute(ToolParams params, ToolContext context);

    void validate(ToolParams params);

    boolean isAllowed(SecurityPolicy policy);

    boolean requiresApproval();

    ToolStats getStats();

    record ToolParams(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        public <T> T get(String key) {
            return (T) args.get(key);
        }

        public String getString(String key) {
            Object value = args.get(key);
            return value != null ? value.toString() : null;
        }

        public Integer getInteger(String key) {
            Object value = args.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return null;
        }

        public Boolean getBoolean(String key) {
            Object value = args.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return null;
        }

        public static ToolParams of(Map<String, Object> args) {
            return new ToolParams(args != null ? args : Map.of());
        }
    }
}
