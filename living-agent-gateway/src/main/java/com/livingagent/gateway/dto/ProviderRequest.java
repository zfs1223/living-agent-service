package com.livingagent.gateway.dto;

import com.livingagent.core.model.pool.Protocol;

public record ProviderRequest(
    String id,
    String displayName,
    Protocol protocol,
    String baseUrl,
    String apiKeyEncrypted,
    boolean enabled,
    boolean supportsToolChoice,
    int defaultMaxTokens,
    boolean autoDiscoverModels
) {}
