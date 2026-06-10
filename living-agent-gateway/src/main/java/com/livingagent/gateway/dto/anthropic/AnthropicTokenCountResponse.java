package com.livingagent.gateway.dto.anthropic;

public record AnthropicTokenCountResponse(
    int input_tokens,
    int cache_creation_input_tokens,
    int cache_read_input_tokens
) {}
