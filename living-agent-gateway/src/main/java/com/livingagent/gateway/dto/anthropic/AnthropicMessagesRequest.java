package com.livingagent.gateway.dto.anthropic;

import java.util.List;

public record AnthropicMessagesRequest(
    String model,
    List<AnthropicMessage> messages,
    Object system,
    Integer max_tokens,
    Double temperature,
    List<AnthropicTool> tools,
    String tool_choice,
    Boolean stream
) {
    public record AnthropicMessage(
        String role,
        Object content
    ) {}

    public record AnthropicContentBlock(
        String type,
        String text,
        AnthropicImageSource source,
        AnthropicToolUseContent tool_use,
        AnthropicToolResultContent tool_result
    ) {
        public record AnthropicImageSource(
            String type,
            String media_type,
            String data
        ) {}

        public record AnthropicToolUseContent(
            String id,
            String name,
            Object input
        ) {}

        public record AnthropicToolResultContent(
            String tool_use_id,
            Object content
        ) {}
    }
}
