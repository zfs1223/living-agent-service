package com.livingagent.core.proxy.anthropic.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.proxy.anthropic.AnthropicMessagesRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnthropicToOpenAiConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> convert(AnthropicMessagesRequest request, String resolvedModel) {
        Map<String, Object> openAiRequest = new HashMap<>();
        openAiRequest.put("model", resolvedModel);
        openAiRequest.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : 4096);
        if (request.temperature() != null) {
            openAiRequest.put("temperature", request.temperature());
        }
        if (Boolean.TRUE.equals(request.stream())) {
            openAiRequest.put("stream", true);
            openAiRequest.put("stream_options", Map.of("include_usage", true));
        }
        List<Map<String, Object>> messages = convertMessages(request);
        if (!messages.isEmpty()) {
            openAiRequest.put("messages", messages);
        }
        if (request.tools() != null && !request.tools().isEmpty()) {
            List<Map<String, Object>> tools = convertTools(request.tools());
            openAiRequest.put("tools", tools);
            if (request.toolChoice() != null) {
                openAiRequest.put("tool_choice", convertToolChoice(request.toolChoice()));
            }
        }
        return openAiRequest;
    }

    private List<Map<String, Object>> convertMessages(AnthropicMessagesRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Object system = request.system();
        if (system instanceof String s && !s.isBlank()) {
            messages.add(Map.of("role", "system", "content", s));
        } else if (system instanceof List<?> blocks) {
            StringBuilder sb = new StringBuilder();
            for (Object block : blocks) {
                if (block instanceof AnthropicMessagesRequest.ContentBlock cb) {
                    if ("text".equals(cb.type()) && cb.text() != null) {
                        sb.append(cb.text()).append("\n");
                    }
                } else if (block instanceof Map<?, ?> m) {
                    if ("text".equals(m.get("type"))) {
                        sb.append(m.get("text")).append("\n");
                    }
                }
            }
            if (!sb.isEmpty()) {
                messages.add(Map.of("role", "system", "content", sb.toString().trim()));
            }
        }
        if (request.messages() != null) {
            for (AnthropicMessagesRequest.AnMessage msg : request.messages()) {
                messages.add(convertMessage(msg));
            }
        }
        return messages;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertMessage(AnthropicMessagesRequest.AnMessage msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("role", msg.role());
        Object content = msg.content();
        if (content instanceof String s) {
            result.put("content", s);
        } else if (content instanceof List<?> blocks) {
            List<Map<String, Object>> converted = new ArrayList<>();
            for (Object block : blocks) {
                if (block instanceof Map<?, ?> m) {
                    String type = (String) m.get("type");
                    switch (type) {
                        case "text" -> converted.add(Map.of("type", "text", "text", m.get("text")));
                        case "thinking" -> {
                            Object thinking = m.get("thinking");
                            if (thinking instanceof String t) {
                                converted.add(Map.of("role", "assistant", "content", "<thinking>" + t + "</thinking>"));
                            }
                        }
                        case "tool_use" -> {
                            Map<String, Object> toolUse = new HashMap<>();
                            toolUse.put("type", "function");
                            Map<String, Object> function = new HashMap<>();
                            function.put("name", m.get("name"));
                            function.put("arguments", m.get("input") != null ? m.get("input").toString() : "{}");
                            toolUse.put("function", function);
                            converted.add(toolUse);
                        }
                        case "tool_result" -> {
                            Object output = m.get("content");
                            converted.add(Map.of("role", "tool", "content", output != null ? output.toString() : ""));
                        }
                        default -> converted.add(Map.of("type", "text", "text", m.toString()));
                    }
                } else if (block instanceof AnthropicMessagesRequest.ContentBlock cb) {
                    switch (cb.type()) {
                        case "text" -> converted.add(Map.of("type", "text", "text", cb.text()));
                        case "thinking" -> converted.add(Map.of("role", "assistant", "content", "<thinking>" + cb.text() + "</thinking>"));
                        case "tool_use" -> {
                            Map<String, Object> toolUse = new HashMap<>();
                            toolUse.put("type", "function");
                            Map<String, Object> function = new HashMap<>();
                            function.put("name", cb.toolUse() != null ? cb.toolUse().get("name") : "unknown");
                            function.put("arguments", cb.toolUse() != null ? cb.toolUse().getOrDefault("input", "{}").toString() : "{}");
                            toolUse.put("function", function);
                            converted.add(toolUse);
                        }
                    }
                }
            }
            result.put("content", converted);
        }
        return result;
    }

    private List<Map<String, Object>> convertTools(List<AnthropicMessagesRequest.AnTool> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AnthropicMessagesRequest.AnTool tool : tools) {
            Map<String, Object> t = new HashMap<>();
            t.put("type", "function");
            Map<String, Object> function = new HashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description() != null ? tool.description() : "");
            function.put("parameters", tool.inputSchema() != null ? tool.inputSchema() : Map.of("type", "object", "properties", Map.of()));
            t.put("function", function);
            result.add(t);
        }
        return result;
    }

    private Object convertToolChoice(AnthropicMessagesRequest.ToolChoice toolChoice) {
        if (toolChoice == null) return null;
        return switch (toolChoice.type()) {
            case "auto" -> "auto";
            case "any" -> "required";
            case "tool" -> {
                Map<String, Object> tc = new HashMap<>();
                tc.put("type", "function");
                Map<String, Object> f = new HashMap<>();
                f.put("name", toolChoice.function() != null ? toolChoice.function().name() : "");
                tc.put("function", f);
                yield tc;
            }
            default -> "auto";
        };
    }
}
