package com.livingagent.gateway.dto.anthropic;

import java.util.List;

public record AnthropicMessagesResponse(
    String id,
    String type,
    String role,
    List<ContentBlock> content,
    String model,
    String stop_reason,
    String stop_sequence,
    Usage usage
) {
    public record ContentBlock(
        String type,
        String text,
        ToolUseContent tool_use
    ) {
        public record ToolUseContent(
            String id,
            String name,
            Object input
        ) {}
    }

    public record Usage(
        int input_tokens,
        int output_tokens
    ) {}
}
