package com.livingagent.core.proxy.anthropic.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class OpenAiStreamChunkParser {

    private static final Logger log = LoggerFactory.getLogger(OpenAiStreamChunkParser.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringBuilder textBuffer = new StringBuilder();
    private final List<ToolCallAccumulator> toolCallAccumulators = new ArrayList<>();
    private String finishReason;
    private Map<String, Integer> usage;

    public void parseStream(InputStream inputStream, Consumer<StreamChunk> chunkConsumer) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.equals("data: [DONE]")) {
                    if (line.equals("data: [DONE]")) {
                        if (finishReason == null) {
                            finishReason = "stop";
                        }
                        chunkConsumer.accept(new StreamChunk(ChunkType.FINISH, textBuffer.toString(), null, null, null, finishReason, usage));
                    }
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String json = line.substring(5).trim();
                try {
                    JsonNode root = objectMapper.readTree(json);
                    parseChunk(root, chunkConsumer);
                } catch (Exception e) {
                    log.warn("SSE chunk parse error, skipping: {}", e.getMessage());
                }
            }
        }
    }

    private void parseChunk(JsonNode root, Consumer<StreamChunk> chunkConsumer) {
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.get("delta");
            if (delta != null) {
                JsonNode contentNode = delta.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    String content = contentNode.asText();
                    textBuffer.append(content);
                    chunkConsumer.accept(new StreamChunk(ChunkType.TEXT, content, null, null, null, null, null));
                }
                JsonNode reasoningContent = delta.get("reasoning_content");
                if (reasoningContent != null && !reasoningContent.isNull()) {
                    String reasoning = reasoningContent.asText();
                    chunkConsumer.accept(new StreamChunk(ChunkType.THINKING, reasoning, null, null, null, null, null));
                }
                JsonNode toolCalls = delta.get("tool_calls");
                if (toolCalls != null && toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        int index = tc.has("index") ? tc.get("index").asInt() : 0;
                        String callId = tc.has("id") ? tc.get("id").asText() : null;
                        JsonNode function = tc.get("function");
                        String toolName = null;
                        String toolArgs = null;
                        if (function != null) {
                            toolName = function.has("name") ? function.get("name").asText() : null;
                            toolArgs = function.has("arguments") ? function.get("arguments").asText() : null;
                        }
                        while (toolCallAccumulators.size() <= index) {
                            toolCallAccumulators.add(new ToolCallAccumulator());
                        }
                        ToolCallAccumulator acc = toolCallAccumulators.get(index);
                        if (callId != null) acc.callId = callId;
                        if (toolName != null) acc.name = toolName;
                        if (toolArgs != null) acc.argsBuilder.append(toolArgs);
                        chunkConsumer.accept(new StreamChunk(ChunkType.TOOL_USE, toolArgs, acc.callId, acc.name, acc.argsBuilder.toString(), null, null));
                    }
                }
            }
            JsonNode finish = choice.get("finish_reason");
            if (finish != null && !finish.isNull()) {
                finishReason = finish.asText();
            }
        }
        JsonNode usageNode = root.get("usage");
        if (usageNode != null && !usageNode.isNull()) {
            usage = new HashMap<>();
            usage.put("input_tokens", usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0);
            usage.put("output_tokens", usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0);
        }
    }

    public record StreamChunk(
        ChunkType type,
        String content,
        String toolCallId,
        String toolName,
        String toolArgs,
        String finishReason,
        Map<String, Integer> usage
    ) {}

    public enum ChunkType {
        TEXT, TOOL_USE, THINKING, FINISH
    }

    private static class ToolCallAccumulator {
        String callId;
        String name;
        StringBuilder argsBuilder = new StringBuilder();
    }
}
