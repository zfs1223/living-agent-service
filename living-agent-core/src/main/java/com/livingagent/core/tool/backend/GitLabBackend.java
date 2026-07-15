package com.livingagent.core.tool.backend;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * GitLab 后端（64-B-1）
 * 发现方式：application.yml 中的 tool.gitlab.base-url
 * 健康检查：GET /api/v4/version
 */
public class GitLabBackend implements ExternalToolBackend {

    private static final String TOOL_NAME = "gitlab";
    private static final int DEFAULT_PORT = 8385;
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String baseUrl;

    public GitLabBackend(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "http://gitlab:" + DEFAULT_PORT;
    }

    @Override
    public String toolName() { return TOOL_NAME; }

    @Override
    public DiscoveryResult discover() {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/v4/version").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(CONNECT_TIMEOUT_MS);
            conn.setRequestProperty("PRIVATE-TOKEN", "skip");
            int code = conn.getResponseCode();
            if (code == 200 || code == 401) {
                return DiscoveryResult.available("gitlab", baseUrl);
            }
            return DiscoveryResult.unavailable("GitLab 返回 HTTP " + code);
        } catch (Exception e) {
            return DiscoveryResult.unavailable("GitLab 不可达: " + e.getMessage());
        }
    }

    @Override
    public HealthStatus healthCheck() {
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/api/v4/version").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(CONNECT_TIMEOUT_MS);
            int code = conn.getResponseCode();
            long latency = System.currentTimeMillis() - start;
            if (code == 200 || code == 401) {
                return HealthStatus.healthy(latency);
            }
            return HealthStatus.unhealthy("GitLab 返回 HTTP " + code);
        } catch (Exception e) {
            return HealthStatus.unreachable();
        }
    }

    @Override
    public String installHint() {
        return "GitLab 服务不可用。请检查 " + baseUrl + " 是否可达，以及 docker-compose.yml 中 gitlab 服务的状态。";
    }
}
