package com.livingagent.core.model.pool;

import com.livingagent.core.model.pool.client.AnthropicClient;
import com.livingagent.core.model.pool.client.OpenAiCompatibleClient;

public class LlmClientFactory {

    public static LlmClient create(ProviderConfig config) {
        String apiKey = decryptApiKey(config.getApiKeyEncrypted());
        String baseUrl = config.getBaseUrl();

        return switch (config.getProtocol()) {
            case ANTHROPIC -> new AnthropicClient(apiKey, baseUrl, config.isSupportsToolChoice());
            case OPENAI_COMPATIBLE, GEMINI, OPENAI_RESPONSES ->
                new OpenAiCompatibleClient(apiKey, baseUrl, config.isSupportsToolChoice());
        };
    }

    private static String decryptApiKey(String encryptedKey) {
        if (encryptedKey == null || encryptedKey.isEmpty()) {
            return "";
        }
        return encryptedKey;
    }
}
