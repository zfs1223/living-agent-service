package com.livingagent.gateway.dto;

public record ProviderTestRequest(
    String testModel,
    String baseUrl,
    String apiKeyEncrypted
) {}
