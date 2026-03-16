package com.livingagent.core.proactive.alert.impl;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

public class DingTalkNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotifier.class);

    private static final String DINGTALK_WEBHOOK_URL = "https://oapi.dingtalk.com/robot/send?access_token=";

    private final HttpClient httpClient;
    private final String accessToken;
    private final String secret;
    private volatile boolean available = true;

    public DingTalkNotifier(String accessToken) {
        this(accessToken, null);
    }

    public DingTalkNotifier(String accessToken, String secret) {
        this.accessToken = accessToken;
        this.secret = secret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String getChannelName() {
        return "dingtalk";
    }

    @Override
    public boolean send(Alert alert) {
        log.info("Sending DingTalk message: {}", alert.title());

        try {
            String url = buildWebhookUrl();
            String payload = buildMarkdownMessage(alert);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            boolean success = response.statusCode() == 200 && response.body().contains("\"errcode\":0");
            
            if (!success) {
                log.warn("DingTalk send failed: {}", response.body());
                available = false;
            } else {
                available = true;
            }

            return success;

        } catch (Exception e) {
            log.error("Failed to send DingTalk message: {}", e.getMessage());
            available = false;
            return false;
        }
    }

    @Override
    public boolean sendBatch(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return true;
        }

        boolean allSuccess = true;
        for (Alert alert : alerts) {
            if (!send(alert)) {
                allSuccess = false;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return allSuccess;
    }

    @Override
    public boolean isAvailable() {
        return available && accessToken != null && !accessToken.isEmpty();
    }

    private String buildWebhookUrl() {
        String url = DINGTALK_WEBHOOK_URL + accessToken;
        
        if (secret != null && !secret.isEmpty()) {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            String sign = hmacSha256(secret, stringToSign);
            url += "&timestamp=" + timestamp + "&sign=" + sign;
        }
        
        return url;
    }

    private String buildMarkdownMessage(Alert alert) {
        StringBuilder content = new StringBuilder();
        content.append("### ").append(alert.title()).append("\n\n");
        content.append(alert.content()).append("\n\n");
        
        if (alert.level() != null) {
            content.append("**级别**: ").append(alert.level().name()).append("\n\n");
        }
        
        if (alert.actionUrl() != null && !alert.actionUrl().isEmpty()) {
            content.append("[点击查看详情](").append(alert.actionUrl()).append(")\n\n");
        }
        
        content.append("---\n");
        content.append("*发送时间: ").append(new Date()).append("*");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("msgtype", "markdown");
        
        Map<String, Object> markdown = new LinkedHashMap<>();
        markdown.put("title", alert.title());
        markdown.put("text", content.toString());
        message.put("markdown", markdown);

        return toJson(message);
    }

    private String hmacSha256(String secret, String message) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to generate HMAC-SHA256 signature: {}", e.getMessage());
            return "";
        }
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Map) {
                sb.append(toJson((Map<String, Object>) value));
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJson(value.toString())).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
