package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class HttpTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(HttpTool.class);

    private static final String NAME = "http_request";
    private static final String DESCRIPTION = "发送HTTP请求获取数据";
    private static final String VERSION = "1.0.0";

    private final HttpClient httpClient;
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successfulCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    private final ToolSchema schema;

    public HttpTool() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        this.schema = new ToolSchema(
            NAME,
            DESCRIPTION,
            Map.of(
                "url", ToolSchema.Property.string("请求URL"),
                "method", ToolSchema.Property.string("HTTP方法", List.of("GET", "POST", "PUT", "DELETE")),
                "headers", ToolSchema.Property.object("请求头"),
                "body", ToolSchema.Property.string("请求体"),
                "timeout", ToolSchema.Property.integer("超时时间(秒)")
            ),
            List.of("url")
        );
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return "core"; }

    @Override
    public ToolSchema getSchema() { return schema; }

    @Override
    public List<String> getCapabilities() {
        return List.of("http", "rest", "api", "web");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        totalCalls.incrementAndGet();

        try {
            String url = params.getString("url");
            String method = params.getString("method");
            if (method == null) method = "GET";

            @SuppressWarnings("unchecked")
            Map<String, String> headers = params.get("headers");
            String body = params.getString("body");
            Integer timeout = params.getInteger("timeout");
            if (timeout == null) timeout = 30;

            validateUrl(url);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout));

            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }

            if ("GET".equalsIgnoreCase(method)) {
                requestBuilder.GET();
            } else if ("POST".equalsIgnoreCase(method)) {
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            } else if ("PUT".equalsIgnoreCase(method)) {
                requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
            } else if ("DELETE".equalsIgnoreCase(method)) {
                requestBuilder.DELETE();
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long duration = System.currentTimeMillis() - startTime;
            totalDurationMs.addAndGet(duration);
            successfulCalls.incrementAndGet();

            return ToolResult.success(
                java.util.UUID.randomUUID().toString(),
                NAME,
                Map.of(
                    "status", response.statusCode(),
                    "body", response.body(),
                    "headers", response.headers().map(),
                    "duration_ms", duration
                ),
                Duration.ofMillis(duration)
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            failedCalls.incrementAndGet();
            log.error("HTTP request failed", e);
            return ToolResult.failure(
                java.util.UUID.randomUUID().toString(),
                NAME,
                "HTTP请求失败: " + e.getMessage(),
                Duration.ofMillis(duration)
            );
        }
    }

    private void validateUrl(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL不能为空");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("URL必须以http://或https://开头");
        }
    }

    @Override
    public void validate(ToolParams params) {
        String url = params.getString("url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("缺少必需参数: url");
        }
        validateUrl(url);
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.getAutonomyLevel().canAct();
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public ToolStats getStats() {
        long total = totalCalls.get();
        double avgDuration = total > 0 ? (double) totalDurationMs.get() / total : 0;
        return new ToolStats(NAME, total, successfulCalls.get(), failedCalls.get(), avgDuration, System.currentTimeMillis());
    }
}
