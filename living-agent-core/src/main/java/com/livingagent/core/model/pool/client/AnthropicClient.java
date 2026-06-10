package com.livingagent.core.model.pool.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.livingagent.core.model.pool.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

public class AnthropicClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final boolean supportsToolChoice;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicClient(String apiKey, String baseUrl, boolean supportsToolChoice) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.anthropic.com";
        this.supportsToolChoice = supportsToolChoice;
    }

    @Override
    public String complete(String prompt, String model, int maxTokens) {
        try {
            String urlStr = baseUrl + "/v1/messages";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            var messages = body.putArray("messages");
            var userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);

            String jsonBody = objectMapper.writeValueAsString(body);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            try (InputStream is = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
                String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (status >= 200 && status < 400) {
                    var root = objectMapper.readTree(response);
                    return root.path("content").get(0).path("text").asText();
                } else {
                    throw new RuntimeException("Anthropic API error: " + status + " - " + response);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Anthropic API", e);
        }
    }

    @Override
    public Stream<String> stream(String prompt, String model, int maxTokens) {
        throw new UnsupportedOperationException("Streaming not yet implemented for AnthropicClient");
    }
}
