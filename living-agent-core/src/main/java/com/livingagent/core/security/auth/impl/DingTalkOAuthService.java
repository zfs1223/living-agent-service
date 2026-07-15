package com.livingagent.core.security.auth.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.security.AccessLevel;
import com.livingagent.core.security.AuthContext;
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

public class DingTalkOAuthService implements OAuthService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkOAuthService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DINGTALK_AUTH_URL = "https://login.dingtalk.com/oauth2/auth";
    private static final String DINGTALK_TOKEN_URL = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
    private static final String DINGTALK_USER_INFO_URL = "https://api.dingtalk.com/v1.0/contact/users/me";

    private final HttpClient httpClient;
    private final String appKey;
    private final String appSecret;
    private final String corpId;
    private final FounderService founderService;
    
    private final Map<String, AuthContext> employeeCache = new ConcurrentHashMap<>();

    public DingTalkOAuthService(String appKey, String appSecret, String corpId, FounderService founderService) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.corpId = corpId;
        this.founderService = founderService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public DingTalkOAuthService(String appKey, String appSecret, String corpId) {
        this(appKey, appSecret, corpId, null);
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
                long expireIn = getLong(result, "expireIn", 7200L);
                Instant expiresAt = Instant.now().plusSeconds(expireIn);
                return new OAuthToken(
                        (String) result.get("accessToken"),
                        (String) result.get("refreshToken"),
                        expiresAt,
                        (String) result.get("scope")
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
                        (String) result.get("deptId"),
                        (String) result.get("title")
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
    public Optional<AuthContext> findOrCreateEmployee(OAuthUserInfo userInfo) {
        if (userInfo == null) {
            return Optional.empty();
        }

        String cacheKey = "dingtalk_" + userInfo.providerUserId();
        
        AuthContext cached = employeeCache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        AuthContext authContext = new AuthContext();
        authContext.setEmployeeId(cacheKey);
        authContext.setName(userInfo.name());
        authContext.setEmail(userInfo.email());
        authContext.setPhone(userInfo.phone());
        authContext.setDepartment(userInfo.department());
        authContext.setPosition(userInfo.position());
        authContext.setOauthProvider("dingtalk");
        authContext.setOauthUserId(userInfo.providerUserId());
        authContext.setLastSyncTime(Instant.now());
        authContext.setSyncSource("dingtalk_oauth");

        if (founderService != null && founderService.isFirstUser()) {
            founderService.assignFounderRole(authContext);
            log.info("First user detected, assigned Enterprise role: {}", authContext.getName());
        } else {
            authContext.setIdentity(UserIdentity.INTERNAL_ACTIVE);
        }

        employeeCache.put(cacheKey, authContext);

        log.info("Created auth context from DingTalk OAuth: {}", authContext.getName());
        return Optional.of(authContext);
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

        Optional<AuthContext> employeeOpt = findOrCreateEmployee(userInfo);
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

    @Override
    public Optional<AuthContext> findByOAuthUserId(String oauthUserId) {
        String cacheKey = "dingtalk_" + oauthUserId;
        return Optional.ofNullable(employeeCache.get(cacheKey));
    }

    private String encodeUrl(String value) {
        if (value == null) return "";
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private Long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("JSON序列化失败", e);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map) return (Map<String, Object>) parsed;
            if (parsed instanceof java.util.List) {
                Map<String, Object> result = new HashMap<>();
                result.put("_array", parsed);
                return result;
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.warn("JSON解析失败: {}", json, e);
            return new HashMap<>();
        }
    }
}
