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
import java.time.Duration;
import java.util.*;

public class FeishuNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(FeishuNotifier.class);

    private static final String FEISHU_WEBHOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/";

    private final HttpClient httpClient;
    private final String webhookKey;
    private volatile boolean available = true;

    public FeishuNotifier(String webhookKey) {
        this.webhookKey = webhookKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String getChannelName() {
        return "feishu";
    }

    @Override
    public boolean send(Alert alert) {
        log.info("Sending Feishu message: {}", alert.title());

        try {
            String url = FEISHU_WEBHOOK_URL + webhookKey;
            String payload = buildInteractiveMessage(alert);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            boolean success = response.statusCode() == 200 && response.body().contains("\"code\":0");
            
            if (!success) {
                log.warn("Feishu send failed: {}", response.body());
                available = false;
            } else {
                available = true;
            }

            return success;

        } catch (Exception e) {
            log.error("Failed to send Feishu message: {}", e.getMessage());
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
        return available && webhookKey != null && !webhookKey.isEmpty();
    }

    private String buildInteractiveMessage(Alert alert) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("msg_type", "interactive");

        Map<String, Object> card = new LinkedHashMap<>();
        
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", createTextElement(alert.title(), true));
        header.put("template", getColorTemplate(alert.level()));
        card.put("header", header);

        List<Map<String, Object>> elements = new ArrayList<>();
        
        Map<String, Object> contentElement = new LinkedHashMap<>();
        contentElement.put("tag", "div");
        contentElement.put("text", createTextElement(alert.content(), false));
        elements.add(contentElement);
        
        if (alert.level() != null) {
            Map<String, Object> levelElement = new LinkedHashMap<>();
            levelElement.put("tag", "div");
            levelElement.put("fields", List.of(
                    Map.of("is_short", true, "text", createTextElement("**级别**: " + alert.level().name(), false))
            ));
            elements.add(levelElement);
        }
        
        if (alert.actionUrl() != null && !alert.actionUrl().isEmpty()) {
            Map<String, Object> actionElement = new LinkedHashMap<>();
            actionElement.put("tag", "action");
            actionElement.put("actions", List.of(
                    Map.of(
                            "tag", "button",
                            "text", createTextElement("查看详情", false),
                            "type", "primary",
                            "url", alert.actionUrl()
                    )
            ));
            elements.add(actionElement);
        }
        
        Map<String, Object> noteElement = new LinkedHashMap<>();
        noteElement.put("tag", "note");
        noteElement.put("elements", List.of(
                createTextElement("发送时间: " + new Date(), false)
        ));
        elements.add(noteElement);
        
        card.put("elements", elements);
        message.put("card", card);

        return toJson(message);
    }

    private Map<String, Object> createTextElement(String content, boolean isTitle) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("tag", isTitle ? "plain_text" : "lark_md");
        element.put("content", content);
        return element;
    }

    private String getColorTemplate(AlertNotifier.AlertLevel level) {
        if (level == null) {
            return "blue";
        }
        return switch (level) {
            case CRITICAL -> "red";
            case ERROR -> "red";
            case WARNING -> "orange";
            case INFO -> "blue";
            case DEBUG -> "grey";
        };
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
            } else if (value instanceof List) {
                sb.append(listToJson((List<?>) value));
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

    private String listToJson(List<?> list) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(",");
            if (item == null) {
                sb.append("null");
            } else if (item instanceof String) {
                sb.append("\"").append(escapeJson((String) item)).append("\"");
            } else if (item instanceof Map) {
                sb.append(toJson((Map<String, Object>) item));
            } else if (item instanceof Number || item instanceof Boolean) {
                sb.append(item);
            } else {
                sb.append("\"").append(escapeJson(item.toString())).append("\"");
            }
            first = false;
        }
        sb.append("]");
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
