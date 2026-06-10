package com.livingagent.gateway.dto;

import java.util.UUID;

public record ModelRequest(
    String providerId,
    String modelName,
    String displayName,
    int contextWindow,
    int maxOutputTokens,
    boolean supportsVision,
    boolean supportsReasoning,
    Double temperature,
    boolean enabled,
    boolean recommended,
    String bestFor,
    String inputTypes
) {}
