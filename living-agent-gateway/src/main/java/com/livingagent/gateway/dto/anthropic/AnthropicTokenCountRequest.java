package com.livingagent.gateway.dto.anthropic;

import java.util.List;

public record AnthropicTokenCountRequest(
    String model,
    List<AnthropicMessage> messages,
    Object system,
    List<AnthropicTool> tools,
    String tool_choice
) {
    public record AnthropicMessage(
        String role,
        Object content
    ) {}
}
