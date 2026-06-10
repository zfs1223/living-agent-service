package com.livingagent.gateway.dto.anthropic;

import java.util.List;
import java.util.Map;

public record AnthropicTool(
    String name,
    String description,
    InputSchema input_schema
) {
    public record InputSchema(
        String type,
        Map<String, Object> properties,
        List<String> required
    ) {}
}
