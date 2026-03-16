package com.livingagent.core.provider;

import com.livingagent.core.tool.ToolSchema;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Provider {

    String name();

    ProviderCapabilities capabilities();

    ToolsPayload convertTools(List<ToolSchema> tools);

    CompletableFuture<String> simpleChat(String message, String model, double temperature);

    CompletableFuture<String> chatWithSystem(String systemPrompt, String message, String model, double temperature);

    CompletableFuture<ChatResponse> chat(ChatRequest request);

    boolean supportsNativeTools();

    boolean supportsVision();

    boolean supportsStreaming();

    record ProviderCapabilities(
        boolean nativeToolCalling,
        boolean vision
    ) {
        public static ProviderCapabilities basic() {
            return new ProviderCapabilities(false, false);
        }

        public static ProviderCapabilities withTools() {
            return new ProviderCapabilities(true, false);
        }

        public static ProviderCapabilities full() {
            return new ProviderCapabilities(true, true);
        }
    }

    record ToolsPayload(Object payload, ToolsPayloadType type) {
        public enum ToolsPayloadType {
            GEMINI,
            ANTHROPIC,
            OPENAI,
            PROMPT_GUIDED
        }
    }

    record ChatRequest(
        List<ChatMessage> messages,
        List<ToolSchema> tools,
        String model,
        double temperature,
        int maxTokens
    ) {}

    record ChatMessage(
        String role,
        String content,
        List<ToolCallData> toolCalls,
        List<ToolResultData> toolResults
    ) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, null, null);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content, null, null);
        }

        public static ChatMessage assistantWithTools(String content, List<ToolCallData> toolCalls) {
            return new ChatMessage("assistant", content, toolCalls, null);
        }

        public static ChatMessage toolResult(List<ToolResultData> results) {
            return new ChatMessage("tool", null, null, results);
        }
    }

    record ToolCallData(String id, String name, String arguments) {}

    record ToolResultData(String callId, String content) {}

    record ChatResponse(
        String content,
        List<ToolCallData> toolCalls,
        int promptTokens,
        int completionTokens,
        String finishReason
    ) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
