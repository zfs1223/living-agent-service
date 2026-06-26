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

public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final String apiKey;
    private final String baseUrl;
    private final boolean supportsToolChoice;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleClient(String apiKey, String baseUrl, boolean supportsToolChoice) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.supportsToolChoice = supportsToolChoice;
    }

    @Override
    public String complete(String prompt, String model, int maxTokens) {
        try {
            String urlStr = baseUrl + "/chat/completions";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
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
                    return root.path("choices").get(0).path("message").path("content").asText();
                } else {
                    throw new RuntimeException("API error: " + status + " - " + response);
                }
            }
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new RuntimeException("Failed to call OpenAI compatible API: " + detail, e);
        }
    }

    /**
     * 流式生成方法 - <b>未实现</b>。
     *
     * <p>OpenAI 兼容 API 的流式响应需要使用 SSE (Server-Sent Events) 处理，
     * 当前实现尚未完成。请使用 {@link #generate(String, String, int)} 方法替代。</p>
     *
     * <p>实现路径：使用 HttpURLConnection + EventSourceListener 或 WebClient
     * 接收 OpenAI 的 streaming 响应。</p>
     *
     * @param prompt 输入文本
     * @param model 模型标识
     * @param maxTokens 最大 token 数
     * @return 流式响应（未实现）
     * @throws UnsupportedOperationException 总是抛出
     */
    @Override
    public Stream<String> stream(String prompt, String model, int maxTokens) {
        throw new UnsupportedOperationException(
            "Streaming not yet implemented for OpenAiCompatibleClient. Use generate() method instead.");
    }

    @Override
    public String embed(String text, String model) {
        try {
            String urlStr = baseUrl + "/embeddings";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("input", text);

            String jsonBody = objectMapper.writeValueAsString(body);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            try (InputStream is = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
                String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (status >= 200 && status < 400) {
                    var root = objectMapper.readTree(response);
                    var data = root.path("data");
                    if (data.isArray() && data.size() > 0) {
                        var embedding = data.get(0).path("embedding");
                        if (embedding.isArray()) {
                            int dim = embedding.size();
                            return "Embedding OK (dimension=" + dim + ")";
                        }
                    }
                    return "Embedding OK: " + response.substring(0, Math.min(200, response.length()));
                } else {
                    throw new RuntimeException("Embedding API error: " + status + " - " + response);
                }
            }
        } catch (Exception e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new RuntimeException("Failed to call embedding API: " + detail, e);
        }
    }
}
