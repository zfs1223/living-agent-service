package com.livingagent.core.model.pool;

import java.util.stream.Stream;

public interface LlmClient {
    String complete(String prompt, String model, int maxTokens);
    Stream<String> stream(String prompt, String model, int maxTokens);

    default String embed(String text, String model) {
        throw new UnsupportedOperationException("Embedding not supported by this client");
    }
}
