package com.livingagent.core.proxy.anthropic.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnthropicSseBuilder {

    private static final Logger log = LoggerFactory.getLogger(AnthropicSseBuilder.class);

    private static final String MESSAGE_START = "message_start";
    private static final String CONTENT_BLOCK_START = "content_block_start";
    private static final String CONTENT_BLOCK_DELTA = "content_block_delta";
    private static final String CONTENT_BLOCK_STOP = "content_block_stop";
    private static final String MESSAGE_DELTA = "message_delta";
    private static final String PING = "ping";
    private static final String MESSAGE_STOP = "message_stop";
    private static final String ERROR = "error";

    private final SseEmitter emitter;
    private final String modelId;

    public AnthropicSseBuilder(SseEmitter emitter, String modelId) {
        this.emitter = emitter;
        this.modelId = modelId;
    }

    public void messageStart() throws IOException {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", "msg_" + System.currentTimeMillis());
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("model", modelId);
        message.put("content", new Object[]{});
        message.put("stop_reason", null);
        message.put("stop_sequence", null);
        message.put("usage", Map.of("input_tokens", 0, "output_tokens", 0));

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", MESSAGE_START);
        event.put("message", message);

        sendSseEvent(MESSAGE_START, event);
    }

    public void contentBlockStart(int index, String type, String id, String name) throws IOException {
        Map<String, Object> contentBlock = new LinkedHashMap<>();
        contentBlock.put("type", type);
        contentBlock.put("index", index);
        if ("text".equals(type)) {
            contentBlock.put("text", "");
        } else if ("tool_use".equals(type)) {
            contentBlock.put("id", id != null ? id : "toolu_" + System.currentTimeMillis());
            contentBlock.put("name", name != null ? name : "unknown");
            contentBlock.put("input", new LinkedHashMap<>());
        } else if ("thinking".equals(type)) {
            contentBlock.put("thinking", "");
            contentBlock.put("signature", "");
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", CONTENT_BLOCK_START);
        event.put("index", index);
        event.put("content_block", contentBlock);

        sendSseEvent(CONTENT_BLOCK_START, event);
    }

    public void contentBlockDelta(int index, String text) throws IOException {
        Map<String, Object> delta = Map.of("type", "text_delta", "text", text != null ? text : "");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", CONTENT_BLOCK_DELTA);
        event.put("index", index);
        event.put("delta", delta);

        sendSseEvent(CONTENT_BLOCK_DELTA, event);
    }

    public void thinkingDelta(int index, String thinking) throws IOException {
        Map<String, Object> delta = Map.of("type", "thinking_delta", "thinking", thinking != null ? thinking : "");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", CONTENT_BLOCK_DELTA);
        event.put("index", index);
        event.put("delta", delta);

        sendSseEvent(CONTENT_BLOCK_DELTA, event);
    }

    public void toolUseDelta(int index, String name, String inputJson) throws IOException {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("type", "input_json_delta");
        delta.put("partial_json", inputJson != null ? inputJson : "");

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", CONTENT_BLOCK_DELTA);
        event.put("index", index);
        event.put("delta", delta);

        sendSseEvent(CONTENT_BLOCK_DELTA, event);
    }

    public void contentBlockStop(int index) throws IOException {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", CONTENT_BLOCK_STOP);
        event.put("index", index);

        sendSseEvent(CONTENT_BLOCK_STOP, event);
    }

    public void messageDelta(String stopReason, int outputTokens) throws IOException {
        Map<String, Object> usage = Map.of("output_tokens", outputTokens);

        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("stop_reason", stopReason != null ? stopReason : "end_turn");
        delta.put("stop_sequence", null);
        delta.put("usage", usage);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", MESSAGE_DELTA);
        event.put("delta", delta);
        event.put("usage", usage);

        sendSseEvent(MESSAGE_DELTA, event);
    }

    public void messageStop(String stopReason) throws IOException {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", MESSAGE_STOP);
        event.put("stop_reason", stopReason != null ? stopReason : "end_turn");

        sendSseEvent(MESSAGE_STOP, event);
    }

    public void ping() throws IOException {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", PING);
        sendSseEvent(PING, event);
    }

    public void error(String type, String message) {
        try {
            Map<String, Object> errorDetail = new LinkedHashMap<>();
            errorDetail.put("type", type != null ? type : "api_error");
            errorDetail.put("message", message != null ? message : "Unknown error");

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", ERROR);
            event.put("error", errorDetail);

            sendSseEvent(ERROR, event);
        } catch (IOException e) {
            log.warn("SSE error event send failed: {}", e.getMessage());
        }
    }

    public void close() {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE emitter close failed (expected during stream completion): {}", e.getMessage());
        }
    }

    private void sendSseEvent(String eventName, Object data) throws IOException {
        SseEmitter.SseEventBuilder event = SseEmitter.event()
            .name(eventName)
            .data(data);
        emitter.send(event);
    }
}
