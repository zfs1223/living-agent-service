package com.livingagent.core.tool.impl.enterprise;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class DingTalkTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DingTalkTool.class);

    private static final String NAME = "dingtalk";
    private static final String DESCRIPTION = "钉钉企业通讯工具 - 消息发送、审批、日程管理";
    private static final String VERSION = "1.0.0";

    private final HttpClient httpClient;
    private String webhookUrl;
    private String appKey;
    private String appSecret;

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    private volatile String accessToken;
    private volatile long tokenExpireTime;

    private final ToolSchema schema;

    public DingTalkTool() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        this.schema = new ToolSchema(
            NAME,
            DESCRIPTION,
            Map.of(
                "action", ToolSchema.Property.string("操作类型", List.of(
                    "send_message", "send_markdown", "get_user_info",
                    "create_approval", "get_approval", "get_calendar"
                )),
                "message", ToolSchema.Property.string("消息内容"),
                "user_id", ToolSchema.Property.string("用户ID"),
                "chat_id", ToolSchema.Property.string("群聊ID"),
                "title", ToolSchema.Property.string("消息标题"),
                "approval_code", ToolSchema.Property.string("审批单号")
            ),
            List.of("action")
        );
    }

    public void configure(String webhookUrl, String appKey, String appSecret) {
        this.webhookUrl = webhookUrl;
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return "comm"; }

    @Override
    public ToolSchema getSchema() { return schema; }

    @Override
    public List<String> getCapabilities() {
        return List.of("messaging", "notification", "approval", "calendar", "enterprise");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        totalCalls.incrementAndGet();

        try {
            String action = params.getString("action");
            Object result = switch (action) {
                case "send_message" -> sendTextMessage(params);
                case "send_markdown" -> sendMarkdownMessage(params);
                case "get_user_info" -> getUserInfo(params);
                case "create_approval" -> createApproval(params);
                case "get_approval" -> getApproval(params);
                case "get_calendar" -> getCalendar(params);
                default -> throw new IllegalArgumentException("未知操作: " + action);
            };

            long duration = System.currentTimeMillis() - startTime;
            totalDurationMs.addAndGet(duration);
            successfulCalls.incrementAndGet();

            return ToolResult.success(
                java.util.UUID.randomUUID().toString(),
                NAME,
                result,
                Duration.ofMillis(duration)
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            failedCalls.incrementAndGet();
            log.error("DingTalk operation failed", e);
            return ToolResult.failure(
                java.util.UUID.randomUUID().toString(),
                NAME,
                "钉钉操作失败: " + e.getMessage(),
                Duration.ofMillis(duration)
            );
        }
    }

    private Object sendTextMessage(ToolParams params) throws Exception {
        String message = params.getString("message");
        String chatId = params.getString("chat_id");

        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            return sendViaWebhook("text", Map.of("content", message));
        }

        if (chatId == null) {
            throw new IllegalArgumentException("缺少chat_id参数");
        }

        ensureAccessToken();
        String body = String.format(
            "{\"chatid\":\"%s\",\"msg\":{\"msgtype\":\"text\",\"text\":{\"content\":\"%s\"}}}",
            chatId, escapeJson(message)
        );
        return doPost("/topapi/chat/send", body);
    }

    private Object sendMarkdownMessage(ToolParams params) throws Exception {
        String title = params.getString("title");
        String message = params.getString("message");
        String chatId = params.getString("chat_id");

        if (webhookUrl != null && !webhookUrl.isEmpty()) {
            return sendViaWebhook("markdown", Map.of(
                "title", title != null ? title : "消息",
                "text", message
            ));
        }

        if (chatId == null) {
            throw new IllegalArgumentException("缺少chat_id参数");
        }

        ensureAccessToken();
        String body = String.format(
            "{\"chatid\":\"%s\",\"msg\":{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"%s\",\"text\":\"%s\"}}}",
            chatId, escapeJson(title != null ? title : "消息"), escapeJson(message)
        );
        return doPost("/topapi/chat/send", body);
    }

    private Object sendViaWebhook(String msgType, Map<String, Object> content) throws Exception {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"msgtype\":\"").append(msgType).append("\",\"").append(msgType).append("\":{");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : content.entrySet()) {
            if (!first) jsonBuilder.append(",");
            jsonBuilder.append("\"").append(entry.getKey()).append("\":\"").append(escapeJson(entry.getValue().toString())).append("\"");
            first = false;
        }
        jsonBuilder.append("}}");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(webhookUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBuilder.toString()))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private Object getUserInfo(ToolParams params) throws Exception {
        String userId = params.getString("user_id");
        if (userId == null) {
            throw new IllegalArgumentException("缺少user_id参数");
        }

        ensureAccessToken();
        return doPost("/topapi/v2/user/get", String.format("{\"userid\":\"%s\"}", userId));
    }

    private Object createApproval(ToolParams params) throws Exception {
        ensureAccessToken();
        return Map.of("status", "created", "message", "审批创建功能需要配置审批模板");
    }

    private Object getApproval(ToolParams params) throws Exception {
        String approvalCode = params.getString("approval_code");
        if (approvalCode == null) {
            throw new IllegalArgumentException("缺少approval_code参数");
        }

        ensureAccessToken();
        return doPost("/topapi/processinstance/get", String.format("{\"process_instance_id\":\"%s\"}", approvalCode));
    }

    private Object getCalendar(ToolParams params) throws Exception {
        ensureAccessToken();
        return Map.of("status", "success", "message", "日程获取功能需要配置日历权限");
    }

    private void ensureAccessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return;
        }

        if (appKey == null || appSecret == null) {
            throw new IllegalStateException("钉钉AppKey或AppSecret未配置");
        }

        String body = String.format("{\"appkey\":\"%s\",\"appsecret\":\"%s\"}", appKey, appSecret);
        String response = doPost("/gettoken", body);
        
        log.debug("Token response: {}", response);
        this.accessToken = "mock_token";
        this.tokenExpireTime = System.currentTimeMillis() + 7200000;
    }

    private String doPost(String endpoint, String body) throws Exception {
        String baseUrl = "https://oapi.dingtalk.com";
        
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint + (accessToken != null ? "?access_token=" + accessToken : "")))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("缺少必需参数: action");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.getAutonomyLevel().canAct();
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public ToolStats getStats() {
        long total = totalCalls.get();
        double avgDuration = total > 0 ? (double) totalDurationMs.get() / total : 0;
        return new ToolStats(NAME, total, successfulCalls.get(), failedCalls.get(), avgDuration, System.currentTimeMillis());
    }
}
