package com.livingagent.gateway.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LiveKit JWT Token 生成服务 - 闭环 38→67 认证桥接
 *
 * <p>为 LAS 用户生成 LiveKit 参会 token，实现 LAS 认证体系到 LiveKit 会议体系的桥接。
 * 不使用 livekit-server-sdk Java 库，直接使用 JWS (HMAC-SHA256) 实现 JWT 生成，
 * 避免引入额外依赖。</p>
 *
 * <h3>LiveKit JWT 结构</h3>
 * <pre>
 * Header:  { "alg": "HS256", "typ": "JWT" }
 * Payload: {
 *   "iss": "{apiKey}",           // LiveKit API Key
 *   "sub": "{apiKey}",           // LiveKit API Key
 *   "jti": "{uniqueId}",         // 唯一标识
 *   "nbf": {notBefore},          // 生效时间（秒）
 *   "exp": {expiration},         // 过期时间（秒）
 *   "video": {
 *     "roomJoin": true,
 *     "room": "{roomName}",
 *     "canPublish": true/false,
 *     "canSubscribe": true/false,
 *     "canPublishData": true,
 *     "canRecord": false
 *   },
 *   "metadata": "{jsonString}"   // LAS 用户元数据
 * }
 * </pre>
 *
 * @author P81 LiveKit 部署与会议基础
 * @since 1.0.0
 */
@Service
public class LiveKitTokenService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitTokenService.class);

    /** JWT 签名算法 */
    private static final String HMAC_SHA256 = "HmacSHA256";

    /** token 有效期：6 小时（秒） */
    private static final long TOKEN_TTL_SECONDS = 6 * 3600;

    /** 录制专用 token 有效期：24 小时（秒） */
    private static final long RECORDING_TOKEN_TTL_SECONDS = 24 * 3600;

    private final LiveKitConfig liveKitConfig;
    private final ObjectMapper objectMapper;

    public LiveKitTokenService(LiveKitConfig liveKitConfig, ObjectMapper objectMapper) {
        this.liveKitConfig = liveKitConfig;
        this.objectMapper = objectMapper;
        log.info("[P81] LiveKitTokenService 初始化 - apiKey={}", liveKitConfig.getApiKey());
    }

    /**
     * 为 LAS 用户生成 LiveKit 参会 token（闭环 38→67 认证桥接）
     *
     * @param userId      LAS 用户ID，作为 LiveKit 参会者 identity
     * @param roomName    会议房间名
     * @param canPublish  是否可以发布音视频（主持人=true，观众=false）
     * @param canSubscribe 是否可以订阅音视频（通常为 true）
     * @return LiveKit JWT token 字符串
     */
    public String generateToken(String userId, String roomName, boolean canPublish, boolean canSubscribe) {
        return generateToken(userId, roomName, canPublish, canSubscribe, false, null);
    }

    /**
     * 为 LAS 用户生成 LiveKit 参会 token（携带元数据）
     *
     * @param userId       LAS 用户ID
     * @param roomName     会议房间名
     * @param canPublish   是否可以发布音视频
     * @param canSubscribe 是否可以订阅音视频
     * @param metadata     附加元数据（如部门、身份等）
     * @return LiveKit JWT token 字符串
     */
    public String generateToken(String userId, String roomName, boolean canPublish,
                                boolean canSubscribe, Map<String, Object> metadata) {
        return generateToken(userId, roomName, canPublish, canSubscribe, false, metadata);
    }

    /**
     * 生成录制专用 token（闭环 68-A 录制→转写）
     * 录制 token 具有录制权限和较长的有效期
     *
     * @param userId   操作者ID
     * @param roomName 会议房间名
     * @return 录制专用 LiveKit JWT token
     */
    public String generateTokenForRecording(String userId, String roomName) {
        return generateToken(userId, roomName, true, true, true, null);
    }

    /**
     * 核心 token 生成方法
     *
     * @param userId       LAS 用户ID
     * @param roomName     会议房间名
     * @param canPublish   是否可以发布音视频
     * @param canSubscribe 是否可以订阅音视频
     * @param canRecord    是否可以录制
     * @param metadata     附加元数据
     * @return LiveKit JWT token 字符串
     */
    private String generateToken(String userId, String roomName, boolean canPublish,
                                 boolean canSubscribe, boolean canRecord,
                                 Map<String, Object> metadata) {
        try {
            long now = System.currentTimeMillis() / 1000;
            long ttl = canRecord ? RECORDING_TOKEN_TTL_SECONDS : TOKEN_TTL_SECONDS;

            // 构建 Header
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            // 构建 Payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("iss", liveKitConfig.getApiKey());
            payload.put("sub", liveKitConfig.getApiKey());
            payload.put("jti", java.util.UUID.randomUUID().toString());
            payload.put("nbf", now);
            payload.put("exp", now + ttl);

            // 构建 video 权限声明
            Map<String, Object> video = new LinkedHashMap<>();
            video.put("roomJoin", true);
            video.put("room", roomName);
            video.put("canPublish", canPublish);
            video.put("canSubscribe", canSubscribe);
            video.put("canPublishData", canPublish);
            video.put("canRecord", canRecord);
            payload.put("video", video);

            // 参会者 identity（LiveKit 用于区分参会者）
            payload.put("identity", userId);

            // 可选：附加元数据
            if (metadata != null && !metadata.isEmpty()) {
                payload.put("metadata", objectMapper.writeValueAsString(metadata));
            }

            // 编码 JWT
            String token = encodeJwt(header, payload);

            log.debug("[P81] 生成 LiveKit token - userId={}, room={}, canPublish={}, canSubscribe={}, canRecord={}, ttl={}s",
                    userId, roomName, canPublish, canSubscribe, canRecord, ttl);

            return token;

        } catch (Exception e) {
            log.error("[P81] 生成 LiveKit token 失败 - userId={}, room={}", userId, roomName, e);
            throw new RuntimeException("生成 LiveKit token 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用 HMAC-SHA256 编码 JWT
     *
     * @param header  JWT Header
     * @param payload JWT Payload
     * @return 编码后的 JWT 字符串
     */
    private String encodeJwt(Map<String, Object> header, Map<String, Object> payload) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // Base64url 编码 Header
        String headerJson = mapper.writeValueAsString(header);
        String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));

        // Base64url 编码 Payload
        String payloadJson = mapper.writeValueAsString(payload);
        String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

        // 构造签名输入
        String signingInput = encodedHeader + "." + encodedPayload;

        // HMAC-SHA256 签名
        byte[] signature = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), liveKitConfig.getApiSecret());

        // 拼接 JWT
        return signingInput + "." + base64UrlEncode(signature);
    }

    /**
     * HMAC-SHA256 签名
     */
    private byte[] hmacSha256(byte[] data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        Key key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(key);
        return mac.doFinal(data);
    }

    /**
     * Base64url 编码（无填充）
     * 符合 RFC 7515 规范：使用 URL 安全字符，移除填充符
     */
    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(data);
    }
}
