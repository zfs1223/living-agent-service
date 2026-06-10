package com.livingagent.core.tool.impl.enterprise;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.tool.*;

/**
 * 飞书工具公共基类，封装 Token 管理、HTTP 请求构建、响应解析等公共逻辑。
 * <p>
 * 子类只需实现 {@link #doExecute} 方法处理各自的 action 分发。
 */
public abstract class AbstractFeishuTool implements Tool {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String appId;
    protected final String appSecret;
    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper;

    protected String accessToken;
    protected long tokenExpireTime;
    protected ToolStats stats;

    protected AbstractFeishuTool(String appId, String appSecret, String toolName) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
        this.stats = ToolStats.empty(toolName);
    }

    // ========== Token 管理 ==========

    protected synchronized void ensureAccessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return;
        }

        String url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";

        Map<String, String> body = Map.of(
            "app_id", appId,
            "app_secret", appSecret
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                accessToken = (String) result.get("tenant_access_token");
                Object expireObj = result.get("expire");
                long expire = 7200L;
                if (expireObj instanceof Number) {
                    expire = ((Number) expireObj).longValue();
                }
                tokenExpireTime = System.currentTimeMillis() + expire * 1000L - 60000L;
                log.info("飞书 access_token 获取成功");
            } else {
                throw new RuntimeException("获取access_token失败: " + result.get("msg"));
            }
        } else {
            throw new RuntimeException("获取access_token失败: HTTP " + response.statusCode());
        }
    }

    // ========== HTTP 请求构建 ==========

    protected HttpRequest buildGetRequest(String url) {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
    }

    protected HttpRequest buildPostRequest(String url, Object body) throws Exception {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
    }

    protected HttpRequest buildPutRequest(String url, Object body) throws Exception {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
    }

    protected HttpRequest buildPatchRequest(String url, Object body) throws Exception {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + accessToken)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
    }

    protected HttpRequest buildDeleteRequest(String url) {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .DELETE()
            .build();
    }

    // ========== 响应解析 ==========

    protected HttpResponse<String> sendRequest(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected Map<String, Object> parseResponse(HttpResponse<String> response) throws Exception {
        return objectMapper.readValue(response.body(), Map.class);
    }

    protected ToolResult handleResponse(HttpResponse<String> response, String errorPrefix) throws Exception {
        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return ToolResult.success(data);
            } else {
                return ToolResult.failure(errorPrefix + ": " + result.get("msg"));
            }
        } else {
            return ToolResult.failure(errorPrefix + ": HTTP " + response.statusCode());
        }
    }

    protected ToolResult handleResponseWithMessage(HttpResponse<String> response, String successMessage, String errorPrefix) throws Exception {
        if (response.statusCode() == 200) {
            Map<String, Object> result = parseResponse(response);
            Integer code = (Integer) result.get("code");
            if (code != null && code == 0) {
                return ToolResult.success(Map.of("message", successMessage));
            } else {
                return ToolResult.failure(errorPrefix + ": " + result.get("msg"));
            }
        } else {
            return ToolResult.failure(errorPrefix + ": HTTP " + response.statusCode());
        }
    }

    // ========== Tool 接口默认实现 ==========

    @Override
    public void validate(ToolParams params) {
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("缺少必要参数: action");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) {
        return policy.isToolAllowed(getName());
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    public boolean isActionAllowed(String action, AccessLevel accessLevel) {
        if (accessLevel != AccessLevel.FULL) {
            return false;
        }
        return true;
    }

    @Override
    public ToolStats getStats() {
        return stats;
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        if (action == null || action.isEmpty()) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("缺少必要参数: action");
        }

        try {
            ensureAccessToken();
            ToolResult result = doExecute(action, params);
            stats = stats.recordCall(result.success(), System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("飞书操作失败: {}", e.getMessage(), e);
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            return ToolResult.failure("操作失败: " + e.getMessage());
        }
    }

    /**
     * 子类实现此方法处理各自的 action 分发。
     */
    protected abstract ToolResult doExecute(String action, ToolParams params) throws Exception;
}
