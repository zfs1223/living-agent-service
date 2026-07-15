package com.livingagent.core.tool.backend;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Jenkins 后端（64-B-1）
 * 发现方式：application.yml 中的 tool.jenkins.base-url
 * 健康检查：GET /api/json
 */
public class JenkinsBackend implements ExternalToolBackend {

    private static final String TOOL_NAME = "jenkins";
    private static final int DEFAULT_PORT = 8384;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String baseUrl;

    public JenkinsBackend(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "http://jenkins:" + DEFAULT_PORT;
    }

    @Override
    public String toolName() { return TOOL_NAME; }

    @Override
    public DiscoveryResult discover() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/json").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(CONNECT_TIMEOUT_MS);
            int code = conn.getResponseCode();
            if (code == 200 || code == 401 || code == 403) {
                return DiscoveryResult.available("jenkins", baseUrl);
            }
            return DiscoveryResult.unavailable("Jenkins 返回 HTTP " + code);
        } catch (Exception e) {
            return DiscoveryResult.unavailable("Jenkins 不可达: " + e.getMessage());
        }
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/json").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(CONNECT_TIMEOUT_MS);
            int code = conn.getResponseCode();
            long latency = System.currentTimeMillis() - start;
            if (code == 200 || code == 401 || code == 403) {
                return HealthStatus.healthy(latency);
            }
            return HealthStatus.unhealthy("Jenkins 返回 HTTP " + code);
        } catch (Exception e) {
            return HealthStatus.unreachable();
        }
    }

    @Override
    public String installHint() {
        return "Jenkins 服务不可用。请检查 " + baseUrl + " 是否可达，以及 docker-compose.yml 中 jenkins 服务的状态。";
    }
}
