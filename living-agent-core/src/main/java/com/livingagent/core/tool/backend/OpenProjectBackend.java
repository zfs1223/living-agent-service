package com.livingagent.core.tool.backend;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * OpenProject 后端（64-B-1）
 * 发现方式：application.yml 中的 tool.openproject.base-url
 * 健康检查：GET /api/v3/configuration
 */
public class OpenProjectBackend implements ExternalToolBackend {

    private static final String TOOL_NAME = "openproject";
    private static final int DEFAULT_PORT = 8386;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String baseUrl;

    public OpenProjectBackend(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "http://openproject:" + DEFAULT_PORT;
    }

    @Override
    public String toolName() { return TOOL_NAME; }

    @Override
    public DiscoveryResult discover() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/v3/configuration").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(CONNECT_TIMEOUT_MS);
            int code = conn.getResponseCode();
            if (code == 200) {
                return DiscoveryResult.available("openproject", baseUrl);
            }
            return DiscoveryResult.unavailable("OpenProject 返回 HTTP " + code);
        } catch (Exception e) {
            return DiscoveryResult.unavailable("OpenProject 不可达: " + e.getMessage());
        }
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/v3/configuration").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(CONNECT_TIMEOUT_MS);
            int code = conn.getResponseCode();
            long latency = System.currentTimeMillis() - start;
            if (code == 200) {
                return HealthStatus.healthy(latency);
            }
            return HealthStatus.unhealthy("OpenProject 返回 HTTP " + code);
        } catch (Exception e) {
            return HealthStatus.unreachable();
        }
    }

    @Override
    public String installHint() {
        return "OpenProject 服务不可用。请检查 " + baseUrl + " 是否可达，以及 docker-compose.yml 中 openproject 服务的状态。";
    }
}
