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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WeComOAuthService implements OAuthService {

    private static final Logger log = LoggerFactory.getLogger(WeComOAuthService.class);

    private static final String WECOM_AUTH_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String WECOM_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String WECOM_USER_INFO_URL = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";
    private static final String WECOM_USER_DETAIL_URL = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserdetail";

    private final HttpClient httpClient;
    private final String corpId;
    private final String agentId;
    private final String corpSecret;
    private final FounderService founderService;
    
    private String accessToken;
    private long tokenExpireTime;
    
    private final Map<String, Employee> employeeCache = new ConcurrentHashMap<>();

    public WeComOAuthService(String corpId, String agentId, String corpSecret, FounderService founderService) {
        this.corpId = corpId;
        this.agentId = agentId;
        this.corpSecret = corpSecret;
        this.founderService = founderService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public WeComOAuthService(String corpId, String agentId, String corpSecret) {
        this(corpId, agentId, corpSecret, null);
    }

    @Override
    public String getProviderName() {
        return "wecom";
    }

    @Override
    public String getAuthorizationUrl(String redirectUri, String state) {
        StringBuilder url = new StringBuilder(WECOM_AUTH_URL);
        url.append("?appid=").append(corpId);
        url.append("&redirect_uri=").append(encodeUrl(redirectUri));
        url.append("&response_type=code");
        url.append("&scope=snsapi_base");
        url.append("&agentid=").append(agentId);
        url.append("&state=").append(state != null ? state : "wecom_oauth");
        url.append("#wechat_redirect");
        
        return url.toString();
    }

    @Override
    public OAuthToken exchangeCodeForToken(String code, String redirectUri) {
        log.info("Exchanging authorization code for token");

        try {
            String token = getAccessToken();
            if (token == null) {
                log.error("Failed to get access token");
                return null;
            }

            String url = WECOM_USER_INFO_URL + "?access_token=" + token + "&code=" + code;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());

            if (result.containsKey("UserId")) {
                return new OAuthToken(
                        (String) result.get("UserId"),
                        null,
                        7200L,
                        "Bearer",
                        null,
                        Instant.now()
                );
            } else {
                log.error("Failed to get user info: {}", result);
                return null;
            }

        } catch (Exception e) {
            log.error("Error exchanging code for token: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public OAuthUserInfo getUserInfo(OAuthToken token) {
        log.info("Getting user info from WeCom");

        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                return null;
            }

            String userId = token.accessToken();
            String url = WECOM_USER_DETAIL_URL + "?access_token=" + accessToken;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("user_ticket", userId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());

            if (result.containsKey("userid")) {
                return new OAuthUserInfo(
                        (String) result.get("userid"),
                        (String) result.get("name"),
                        (String) result.get("email"),
                        (String) result.get("mobile"),
                        (String) result.get("avatar"),
                        (String) result.get("department"),
                        (String) result.get("position"),
                        result
                );
            }
            
            return new OAuthUserInfo(
                    userId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    result
            );

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

        String cacheKey = "wecom_" + userInfo.providerUserId();
        
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
        employee.setOauthProvider("wecom");
        employee.setOauthUserId(userInfo.providerUserId());
        employee.setLastSyncTime(Instant.now());
        employee.setSyncSource("wecom_oauth");

        if (founderService != null && founderService.isFirstUser()) {
            founderService.assignFounderRole(employee);
            log.info("First user detected, assigned Chairman role: {}", employee.getName());
        } else {
            employee.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        }

        employeeCache.put(cacheKey, employee);

        log.info("Created employee from WeCom OAuth: {}", employee.getName());
        return Optional.of(employee);
    }

    @Override
    public OAuthResult authenticate(String code, String redirectUri) {
        log.info("Authenticating with WeCom OAuth");

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

        log.info("WeCom OAuth authentication successful: {}", userInfo.name());
        return OAuthResult.success(employeeOpt.get(), token);
    }

    @Override
    public boolean validateToken(String accessToken) {
        return accessToken != null && !accessToken.isEmpty();
    }

    @Override
    public void revokeToken(String accessToken) {
        log.info("Token revocation requested (WeCom does not support token revocation)");
    }

    private synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        try {
            String url = WECOM_TOKEN_URL + "?corpid=" + corpId + "&corpsecret=" + corpSecret;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = parseJson(response.body());

            if (result.containsKey("access_token")) {
                accessToken = (String) result.get("access_token");
                Integer expiresIn = (Integer) result.get("expires_in");
                tokenExpireTime = System.currentTimeMillis() + (expiresIn != null ? expiresIn * 1000L : 7200000L);
                return accessToken;
            }

        } catch (Exception e) {
            log.error("Failed to get access token: {}", e.getMessage());
        }

        return null;
    }

    private String encodeUrl(String value) {
        if (value == null) return "";
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
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
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }
}
