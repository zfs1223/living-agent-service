package com.livingagent.core.proactive.alert.impl;

import com.livingagent.core.proactive.alert.AlertNotifier;
import com.livingagent.core.proactive.alert.AlertNotifier.Alert;
import com.livingagent.core.proactive.alert.AlertNotifier.AlertLevel;
import com.livingagent.core.proactive.alert.AlertNotifier.AlertType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebhookAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookAlertNotifier.class);

    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final String webhookUrl;
    private final String channelName;
    private volatile boolean available = true;

    public WebhookAlertNotifier(String channelName, String webhookUrl) {
        this.channelName = channelName;
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.executorService = Executors.newFixedThreadPool(2);
    }

    @Override
    public String getChannelName() {
        return channelName;
    }

    @Override
    public boolean send(Alert alert) {
        log.info("Sending alert via {}: {} - {}", channelName, alert.level(), alert.title());

        try {
            Map<String, Object> payload = buildPayload(alert);
            String jsonPayload = toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!success) {
                log.warn("Alert send failed via {}: HTTP {}", channelName, response.statusCode());
                available = false;
            } else {
                available = true;
            }
            
            return success;

        } catch (Exception e) {
            log.error("Failed to send alert via {}: {}", channelName, e.getMessage());
            available = false;
            return false;
        }
    }

    @Override
    public boolean sendBatch(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return true;
        }

        log.info("Sending {} alerts via {}", alerts.size(), channelName);

        try {
            CompletableFuture<Boolean>[] futures = alerts.stream()
                    .map(alert -> CompletableFuture.supplyAsync(() -> send(alert), executorService))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            boolean allSuccess = true;
            for (CompletableFuture<Boolean> future : futures) {
                if (!future.get()) {
                    allSuccess = false;
                }
            }
            
            return allSuccess;

        } catch (Exception e) {
            log.error("Failed to send batch alerts via {}: {}", channelName, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available && webhookUrl != null && !webhookUrl.isEmpty();
    }

    protected Map<String, Object> buildPayload(Alert alert) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("alertId", alert.alertId());
        payload.put("title", alert.title());
        payload.put("content", alert.content());
        payload.put("level", alert.level().name());
        payload.put("type", alert.type().name());
        payload.put("timestamp", alert.createdAt().toString());
        
        if (alert.actionUrl() != null) {
            payload.put("actionUrl", alert.actionUrl());
        }
        
        if (alert.targetUsers() != null && !alert.targetUsers().isEmpty()) {
            payload.put("targetUsers", alert.targetUsers());
        }
        
        if (alert.data() != null && !alert.data().isEmpty()) {
            payload.put("data", alert.data());
        }
        
        return payload;
    }

    protected String toJson(Map<String, Object> map) {
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
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof List) {
                sb.append(listToJson((List<?>) value));
            } else if (value instanceof Map) {
                sb.append(toJson((Map<String, Object>) value));
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
            if (item instanceof String) {
                sb.append("\"").append(escapeJson((String) item)).append("\"");
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

    public void shutdown() {
        executorService.shutdown();
    }
}
