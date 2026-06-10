package com.livingagent.core.proxy.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record AnthropicMessagesRequest(
    String model,
    List<AnMessage> messages,
    Object system,
    @JsonProperty("max_tokens") Integer maxTokens,
    Double temperature,
    List<AnTool> tools,
    @JsonProperty("tool_choice") ToolChoice toolChoice,
    Boolean stream
) {
    public record ContentBlock(
        String type,
        String text,
        String id,
        @JsonProperty("tool_use") Map<String, Object> toolUse,
        @JsonProperty("tool_result") Map<String, Object> toolResult,
        Object image,
        @JsonProperty("cache_control") Map<String, String> cacheControl
    ) {}

    public record AnMessage(
        String role,
        Object content
    ) {}

    public record AnTool(
        String name,
        String description,
        @JsonProperty("input_schema") Map<String, Object> inputSchema
    ) {}

    public record ToolChoice(
        String type,
        ToolChoiceFunction function
    ) {}

    public record ToolChoiceFunction(
        String name
    ) {}

    public String resolveModelName() {
        if (model != null && !model.isBlank()) {
            return model;
        }
        return "claude-sonnet-4-20250514";
    }
}
