package com.livingagent.core.security.auth.impl;

import com.livingagent.core.security.Employee;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.auth.OAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DingTalkOAuthService implements OAuthService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkOAuthService.class);

    private static final String DINGTALK_AUTH_URL = "https://login.dingtalk.com/oauth2/auth";
    private static final String DINGTALK_TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    private static final String DINGTALK_USER_INFO_URL = "https://api.dingtalk.com/v1.0/contact/users/me";

    private final HttpClient httpClient;
    private final String appKey;
    private final String appSecret;
    private final String corpId;
    
    private final Map<String, Employee> employeeCache = new ConcurrentHashMap<>();

    public DingTalkOAuthService(String appKey, String appSecret, String corpId) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.corpId = corpId;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String getProviderName() {
        return "dingtalk";
    }

    @Override
    public String getAuthorizationUrl(String redirectUri, String state) {
        StringBuilder url = new StringBuilder(DINGTALK_AUTH_URL);
        url.append("?redirect_uri=").append(encodeUrl(redirectUri));
        url.append("&response_type=code");
        url.append("&client_id=").append(appKey);
        url.append("&scope=openid");
        url.append("&state=").append(state != null ? state : "dingtalk_oauth");
        url.append("&prompt=consent");
        
        return url.toString();
    }

    @Override
    public OAuthToken exchangeCodeForToken(String code, String redirectUri) {
        log.info("Exchanging authorization code for token");

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("clientId", appKey);
            requestBody.put("clientSecret", appSecret);
            requestBody.put("code", code);
            requestBody.put("grantType", "authorization_code");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DINGTALK_TOKEN_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());

            if (result.containsKey("accessToken")) {
                return new OAuthToken(
                        (String) result.get("accessToken"),
                        (String) result.get("refreshToken"),
                        getLong(result, "expireIn"),
                        "Bearer",
                        (String) result.get("scope"),
                        Instant.now()
                );
            } else {
                log.error("Failed to get access token: {}", result);
                return null;
            }

        } catch (Exception e) {
            log.error("Error exchanging code for token: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public OAuthUserInfo getUserInfo(OAuthToken token) {
        log.info("Getting user info from DingTalk");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DINGTALK_USER_INFO_URL))
                    .header("Authorization", "Bearer " + token.accessToken())
                    .header("x-acs-dingtalk-access-token", token.accessToken())
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());

            if (result.containsKey("openId")) {
                return new OAuthUserInfo(
                        (String) result.get("openId"),
                        (String) result.get("nickName"),
                        (String) result.get("email"),
                        (String) result.get("mobile"),
                        (String) result.get("avatarUrl"),
                        (String) result.get("deptId"),
                        (String) result.get("title"),
                        result
                );
            } else {
                log.error("Failed to get user info: {}", result);
                return null;
            }

        } catch (Exception e) {
            log.error("Error getting user info: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Optional<Employee> findOrCreateEmployee(OAuthUserInfo userInfo) {
        if (userInfo == null) {
            return Optional.empty();
        }

        String cacheKey = "dingtalk_" + userInfo.providerUserId();
        
        Employee cached = employeeCache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        Employee employee = new Employee();
        employee.setEmployeeId(cacheKey);
        employee.setName(userInfo.name());
        employee.setEmail(userInfo.email());
        employee.setPhone(userInfo.phone());
        employee.setDepartment(userInfo.department());
        employee.setPosition(userInfo.position());
        employee.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        employee.setOauthProvider("dingtalk");
        employee.setOauthUserId(userInfo.providerUserId());
        employee.setLastSyncTime(Instant.now());
        employee.setSyncSource("dingtalk_oauth");

        employeeCache.put(cacheKey, employee);

        log.info("Created employee from DingTalk OAuth: {}", employee.getName());
        return Optional.of(employee);
    }

    @Override
    public OAuthResult authenticate(String code, String redirectUri) {
        log.info("Authenticating with DingTalk OAuth");

        OAuthToken token = exchangeCodeForToken(code, redirectUri);
        if (token == null) {
            return OAuthResult.failed("token_error", "Failed to exchange authorization code");
        }

        OAuthUserInfo userInfo = getUserInfo(token);
        if (userInfo == null) {
            return OAuthResult.failed("user_info_error", "Failed to get user info");
        }

        Optional<Employee> employeeOpt = findOrCreateEmployee(userInfo);
        if (employeeOpt.isEmpty()) {
            return OAuthResult.failed("employee_error", "Failed to create employee");
        }

        log.info("DingTalk OAuth authentication successful: {}", userInfo.name());
        return OAuthResult.success(employeeOpt.get(), token);
    }

    @Override
    public boolean validateToken(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DINGTALK_USER_INFO_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;

        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void revokeToken(String accessToken) {
        log.info("Token revocation requested (DingTalk does not support token revocation)");
    }

    private String encodeUrl(String value) {
        if (value == null) return "";
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
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

    private Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;

        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) return result;

        json = json.substring(1, json.length() - 1);

        int depth = 0;
        StringBuilder current = new StringBuilder();
        String currentKey = null;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                current.append(c);
                continue;
            }

            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }

            if (!inString) {
                if (c == '{' || c == '[') {
                    depth++;
                    current.append(c);
                } else if (c == '}' || c == ']') {
                    depth--;
                    current.append(c);
                } else if (c == ':' && depth == 0) {
                    currentKey = current.toString().trim();
                    if (currentKey.startsWith("\"") && currentKey.endsWith("\"")) {
                        currentKey = currentKey.substring(1, currentKey.length() - 1);
                    }
                    current = new StringBuilder();
                } else if (c == ',' && depth == 0) {
                    if (currentKey != null) {
                        result.put(currentKey, parseValue(current.toString().trim()));
                    }
                    currentKey = null;
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }

        if (currentKey != null) {
            result.put(currentKey, parseValue(current.toString().trim()));
        }

        return result;
    }

    private Object parseValue(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return null;
        }

        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }

        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;

        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
