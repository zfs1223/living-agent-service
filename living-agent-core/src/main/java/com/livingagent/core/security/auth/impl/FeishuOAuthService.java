package com.livingagent.core.security.auth.impl;

import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.Employee;
import com.livingagent.core.security.UserIdentity;
import com.livingagent.core.security.auth.FounderService;
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

public class FeishuOAuthService implements OAuthService {

    private static final Logger log = LoggerFactory.getLogger(FeishuOAuthService.class);

    private static final String FEISHU_AUTH_URL = "https://open.feishu.cn/open-apis/authen/v1/authorize";
    private static final String FEISHU_TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token";
    private static final String FEISHU_USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";

    private final HttpClient httpClient;
    private final String appId;
    private final String appSecret;
    private final FounderService founderService;
    
    private String tenantAccessToken;
    private long tokenExpireTime;
    
    private final Map<String, Employee> employeeCache = new ConcurrentHashMap<>();

    public FeishuOAuthService(String appId, String appSecret, FounderService founderService) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.founderService = founderService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public FeishuOAuthService(String appId, String appSecret) {
        this(appId, appSecret, null);
    }

    @Override
    public String getProviderName() {
        return "feishu";
    }

    @Override
    public String getAuthorizationUrl(String redirectUri, String state) {
        StringBuilder url = new StringBuilder("https://passport.feishu.cn/suite/passport/page/authorize");
        url.append("?redirect_uri=").append(encodeUrl(redirectUri));
        url.append("&app_id=").append(appId);
        url.append("&state=").append(state != null ? state : "feishu_oauth");
        
        return url.toString();
    }

    @Override
    public OAuthToken exchangeCodeForToken(String code, String redirectUri) {
        log.info("Exchanging authorization code for token");

        try {
            String tenantToken = getTenantAccessToken();
            if (tenantToken == null) {
                log.error("Failed to get tenant access token");
                return null;
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("grant_type", "authorization_code");
            requestBody.put("code", code);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_TOKEN_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + tenantToken)
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());

            if (result.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data.containsKey("access_token")) {
                    return new OAuthToken(
                            (String) data.get("access_token"),
                            (String) data.get("refresh_token"),
                            getLong(data, "expires_in"),
                            "Bearer",
                            (String) data.get("token_type"),
                            Instant.now()
                    );
                }
            }
            
            log.error("Failed to get access token: {}", result);
            return null;

        } catch (Exception e) {
            log.error("Error exchanging code for token: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public OAuthUserInfo getUserInfo(OAuthToken token) {
        log.info("Getting user info from Feishu");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_USER_INFO_URL))
                    .header("Authorization", "Bearer " + token.accessToken())
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());

            if (result.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                return new OAuthUserInfo(
                        (String) data.get("open_id"),
                        (String) data.get("name"),
                        (String) data.get("email"),
                        (String) data.get("mobile"),
                        (String) data.get("avatar_url"),
                        (String) data.get("department_id"),
                        (String) data.get("job_title"),
                        data
                );
            }
            
            log.error("Failed to get user info: {}", result);
            return null;

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

        String cacheKey = "feishu_" + userInfo.providerUserId();
        
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
        employee.setOauthProvider("feishu");
        employee.setOauthUserId(userInfo.providerUserId());
        employee.setLastSyncTime(Instant.now());
        employee.setSyncSource("feishu_oauth");

        if (founderService != null && founderService.isFirstUser()) {
            founderService.assignFounderRole(employee);
            log.info("First user detected, assigned Chairman role: {}", employee.getName());
        } else {
            employee.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        }

        employeeCache.put(cacheKey, employee);

        log.info("Created employee from Feishu OAuth: {}", employee.getName());
        return Optional.of(employee);
    }

    @Override
    public OAuthResult authenticate(String code, String redirectUri) {
        log.info("Authenticating with Feishu OAuth");

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

        log.info("Feishu OAuth authentication successful: {}", userInfo.name());
        return OAuthResult.success(employeeOpt.get(), token);
    }

    @Override
    public boolean validateToken(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(FEISHU_USER_INFO_URL))
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
        log.info("Token revocation requested (Feishu does not support token revocation via API)");
    }

    private synchronized String getTenantAccessToken() {
        if (tenantAccessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return tenantAccessToken;
        }

        try {
            String url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
            
            Map<String, Object> body = new HashMap<>();
            body.put("app_id", appId);
            body.put("app_secret", appSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());

            if (result.containsKey("tenant_access_token")) {
                tenantAccessToken = (String) result.get("tenant_access_token");
                Integer expire = (Integer) result.get("expire");
                tokenExpireTime = System.currentTimeMillis() + (expire != null ? expire * 1000L : 7200000L);
                return tenantAccessToken;
            }

        } catch (Exception e) {
            log.error("Failed to get tenant access token: {}", e.getMessage());
        }

        return null;
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
        if (!json.startsWith("{") && !json.startsWith("[")) return result;
        
        if (json.startsWith("[")) {
            result.put("_array", parseArray(json));
            return result;
        }

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

    private Object parseArray(String json) {
        return json;
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
