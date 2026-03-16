package com.livingagent.core.tool;

import java.util.Map;

public record ToolCall(
    String id,
    String name,
    Map<String, Object> arguments
) {
    public static ToolCall of(String name, Map<String, Object> arguments) {
        return new ToolCall(java.util.UUID.randomUUID().toString(), name, arguments);
    }

    public static ToolCall of(String id, String name, Map<String, Object> arguments) {
        return new ToolCall(id, name, arguments);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArg(String key) {
        return (T) arguments.get(key);
    }

    public String getArgString(String key) {
        Object value = arguments.get(key);
        return value != null ? value.toString() : null;
    }
}
