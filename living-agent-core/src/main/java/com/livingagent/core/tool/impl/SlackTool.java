package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SlackTool implements Tool {
    private static final String NAME = "slack";
    private static final String DESCRIPTION = "Slack operations: send/edit/delete messages, reactions, pins, member info";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "communication";
    private static final String SLACK_API_BASE = "https://slack.com/api/";

    private String botToken;
    private ToolStats stats = ToolStats.empty(NAME);

    public SlackTool() {
        this.botToken = null;
    }

    public SlackTool(String botToken) {
        this.botToken = botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public String getDepartment() {
        return DEPARTMENT;
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: sendMessage, editMessage, deleteMessage, readMessages, react, reactions, pinMessage, unpinMessage, listPins, memberInfo, emojiList", true)
                .parameter("token", "string", "Slack Bot Token (xoxb-开头)", false)
                .parameter("channel_id", "string", "频道ID", false)
                .parameter("message_id", "string", "消息时间戳 (如 1712023032.1234)", false)
                .parameter("content", "string", "消息内容", false)
                .parameter("emoji", "string", "表情符号 (如 :thumbsup: 或 ✅)", false)
                .parameter("user_id", "string", "用户ID", false)
                .parameter("limit", "integer", "返回消息数量限制", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("messaging", "reactions", "pins", "member_info", "emoji_list");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("action parameter is required");
        }

        String token = params.getString("token");
        if (token != null && !token.isBlank()) {
            this.botToken = token;
        }

        if (botToken == null || botToken.isBlank()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Slack Bot Token is required. Set via constructor or token parameter");
        }

        try {
            ToolResult result;
            switch (action.toLowerCase()) {
                case "sendmessage":
                    result = sendMessage(params);
                    break;
                case "editmessage":
                    result = editMessage(params);
                    break;
                case "deletemessage":
                    result = deleteMessage(params);
                    break;
                case "readmessages":
                    result = readMessages(params);
                    break;
                case "react":
                    result = addReaction(params);
                    break;
                case "reactions":
                    result = listReactions(params);
                    break;
                case "pinmessage":
                    result = pinMessage(params);
                    break;
                case "unpinmessage":
                    result = unpinMessage(params);
                    break;
                case "listpins":
                    result = listPins(params);
                    break;
                case "memberinfo":
                    result = memberInfo(params);
                    break;
                case "emojilist":
                    result = emojiList();
                    break;
                default:
                    stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
                    return ToolResult.failure("Unknown action: " + action);
            }
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("Slack API error: " + e.getMessage());
        }
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null) {
            throw new IllegalArgumentException("action parameter is required");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(NAME);
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    private ToolResult sendMessage(ToolParams params) throws Exception {
        String channelId = params.getString("channel_id");
        String content = params.getString("content");
        
        if (channelId == null || content == null) {
            return ToolResult.failure("channel_id and content are required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("text", content);

        String response = postRequest("chat.postMessage", body);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to send message: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("message", "Message sent", "ts", result.get("ts")));
    }

    private ToolResult editMessage(ToolParams params) throws Exception {
        String channelId = params.getString("channel_id");
        String messageId = params.getString("message_id");
        String content = params.getString("content");
        
        if (channelId == null || messageId == null || content == null) {
            return ToolResult.failure("channel_id, message_id, and content are required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("ts", messageId);
        body.put("text", content);

        String response = postRequest("chat.update", body);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to edit message: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("message", "Message updated"));
    }

    private ToolResult deleteMessage(ToolParams params) throws Exception {
        String channelId = params.getString("channel_id");
        String messageId = params.getString("message_id");
        
        if (channelId == null || messageId == null) {
            return ToolResult.failure("channel_id and message_id are required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("ts", messageId);

        String response = postRequest("chat.delete", body);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to delete message: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("message", "Message deleted"));
    }

    private ToolResult readMessages(ToolParams params) throws Exception {
        String channelId = params.getString("channel_id");
        if (channelId == null) {
            return ToolResult.failure("channel_id is required");
        }

        Integer limitInt = params.getInteger("limit");
        int limit = limitInt != null ? limitInt : 20;
        
        String response = getRequest("conversations.history?channel=" + channelId + "&limit=" + limit);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to read messages: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("messages", result.get("messages")));
    }

    private ToolResult addReaction(ToolParams params) throws Exception {
        String channelId = params.getString("channel_id");
        String messageId = params.getString("message_id");
        String emoji = params.getString("emoji");
        
        if (channelId == null || messageId == null || emoji == null) {
            return ToolResult.failure("channel_id, message_id, and emoji are required");
        }

        if (!emoji.startsWith(":")) {
            emoji = ":" + emoji + ":";
        }
        if (emoji.startsWith(":") && !emoji.endsWith(":")) {
            emoji = emoji + ":";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("timestamp", messageId);
        body.put("name", emoji.replace(":", ""));

        String response = postRequest("reactions.add", body);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to add reaction: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("message", "Reaction added"));
    }

    private ToolResult listReactions(ToolParams params) throws Exception {
        String channelId = params.getString("channel_id");
        String messageId = params.getString("message_id");
        
        if (channelId == null || messageId == null) {
            return ToolResult.failure("channel_id and message_id are required");
        }

        String response = getRequest("reactions.get?channel=" + channelId + "&timestamp=" + messageId);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to list reactions: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("reactions", result.get("message")));
    }

    private ToolResult pinMessage(ToolParams params) throws Exception {
        String channelId = params.getString("channel_id");
        String messageId = params.getString("message_id");
        
        if (channelId == null || messageId == null) {
            return ToolResult.failure("channel_id and message_id are required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("timestamp", messageId);

        String response = postRequest("pins.add", body);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to pin message: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("message", "Message pinned"));
    }

    private ToolResult unpinMessage(ToolParams params) throws Exception {
        String channelId = params.getString("channel_id");
        String messageId = params.getString("message_id");
        
        if (channelId == null || messageId == null) {
            return ToolResult.failure("channel_id and message_id are required");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("channel", channelId);
        body.put("timestamp", messageId);

        String response = postRequest("pins.remove", body);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to unpin message: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("message", "Message unpinned"));
    }

    private ToolResult listPins(ToolParams params) throws Exception {
        String channelId = params.getString("channel_id");
        if (channelId == null) {
            return ToolResult.failure("channel_id is required");
        }

        String response = getRequest("pins.list?channel=" + channelId);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to list pins: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("pins", result.get("items")));
    }

    private ToolResult memberInfo(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        if (userId == null) {
            return ToolResult.failure("user_id is required");
        }

        String response = getRequest("users.info?user=" + userId);
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to get member info: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("user", result.get("user")));
    }

    private ToolResult emojiList() throws Exception {
        String response = getRequest("emoji.list");
        Map<String, Object> result = parseSlackResponse(response);
        
        if (!isSuccess(result)) {
            return ToolResult.failure("Failed to list emojis: " + result.get("error"));
        }
        
        return ToolResult.success(Map.of("emojis", result.get("emoji")));
    }

    private String getRequest(String endpoint) throws Exception {
        URL url = new URL(SLACK_API_BASE + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + botToken);
        connection.setRequestProperty("Content-Type", "application/json");

        return readResponse(connection);
    }

    private String postRequest(String endpoint, Map<String, Object> body) throws Exception {
        URL url = new URL(SLACK_API_BASE + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + botToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String jsonBody = toJson(body);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        return readResponse(connection);
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        return response.toString();
    }

    private boolean isSuccess(Map<String, Object> result) {
        Object ok = result.get("ok");
        return ok != null && Boolean.TRUE.equals(ok);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSlackResponse(String json) {
        return new HashMap<>();
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":").append(toJsonValue(entry.getValue()));
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + escapeJson((String) value) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) return toJson((Map<String, Object>) value);
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJsonValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
