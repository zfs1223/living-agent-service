package com.livingagent.gateway.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.MeetingEntity;
import com.livingagent.core.database.repository.MeetingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

/**
 * LiveKit Webhook 回调处理器 - 闭环 67-C 会议状态闭环 / 闭环 68-A 录制事件
 *
 * <p>接收 LiveKit Server 推送的事件回调，验证签名后分发到对应的事件处理逻辑。</p>
 *
 * <h3>处理的事件类型</h3>
 * <ul>
 *   <li><b>room_started</b> - 会议开始（闭环 67-C）</li>
 *   <li><b>room_finished</b> - 会议结束（闭环 67-C → 触发闭环 68 会议纪要）</li>
 *   <li><b>participant_joined</b> - 参会者加入（闭环 67-B）</li>
 *   <li><b>participant_left</b> - 参会者离开（闭环 67-B）</li>
 *   <li><b>egress_started</b> - 录制开始（闭环 68-A，P82 实现）</li>
 *   <li><b>egress_ended</b> - 录制结束（闭环 68-A → 68-B，P82 实现）</li>
 * </ul>
 *
 * <h3>Webhook 签名验证</h3>
 * <p>LiveKit 使用 HMAC-SHA256 对 Webhook 请求体进行签名，
 * 签名值通过 {@code Authorization} header 传递，格式为：
 * <pre>Bearer {api-key}:{hmac-sha256-hex}</pre></p>
 *
 * @author P81 LiveKit 部署与会议基础
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/livekit")
public class LiveKitWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookHandler.class);

    private final LiveKitConfig liveKitConfig;
    private final ObjectMapper objectMapper;
    private final MeetingRepository meetingRepository;

    public LiveKitWebhookHandler(LiveKitConfig liveKitConfig, ObjectMapper objectMapper,
                                  MeetingRepository meetingRepository) {
        this.liveKitConfig = liveKitConfig;
        this.objectMapper = objectMapper;
        this.meetingRepository = meetingRepository;
        log.info("[P81] LiveKitWebhookHandler 初始化 - webhookSecret 已配置, 使用 MeetingRepository 持久化");
    }

    /**
     * 接收 LiveKit Webhook 回调（闭环 67-C / 68-A）
     *
     * <p>LiveKit Server 在以下事件发生时回调此端点：
     * <ul>
     *   <li>room_started / room_finished - 会议室生命周期</li>
     *   <li>participant_joined / participant_left - 参会者变化</li>
     *   <li>egress_started / egress_ended - 录制事件（P82）</li>
     * </ul></p>
     *
     * @param body       请求体（JSON）
     * @param authHeader Authorization 头（LiveKit 签名）
     * @return 200 OK 表示接收成功
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String body,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // 1. 验证 Webhook 签名
        if (!verifyWebhookSignature(body, authHeader)) {
            log.warn("[P81] LiveKit Webhook 签名验证失败");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // 2. 解析事件
            JsonNode eventNode = objectMapper.readTree(body);
            String eventType = eventNode.has("event") ? eventNode.get("event").asText() : "unknown";

            log.info("[P81] 收到 LiveKit Webhook 事件: {}", eventType);

            // 3. 分发到对应处理器
            switch (eventType) {
                case "room_started" -> handleRoomStarted(eventNode);
                case "room_finished" -> handleRoomFinished(eventNode);
                case "participant_joined" -> handleParticipantJoined(eventNode);
                case "participant_left" -> handleParticipantLeft(eventNode);
                case "egress_started" -> handleEgressStarted(eventNode);
                case "egress_ended" -> handleEgressEnded(eventNode);
                default -> log.warn("[P81] 未处理的 LiveKit 事件类型: {}", eventType);
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("[P81] 处理 LiveKit Webhook 失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ========== 事件处理器 ==========

    /**
     * 处理会议开始事件（闭环 67-C）
     * room_started → 会议状态变为 ACTIVE，持久化到数据库
     */
    @Transactional
    private void handleRoomStarted(JsonNode eventNode) {
        JsonNode roomNode = eventNode.has("room") ? eventNode.get("room") : eventNode;
        String roomName = roomNode.has("name") ? roomNode.get("name").asText() : "unknown";
        String roomSid = roomNode.has("sid") ? roomNode.get("sid").asText() : "";

        log.info("[P81][67-C] 会议开始 - room={}, sid={}", roomName, roomSid);

        // 持久化到数据库（upsert：已存在则更新，否则创建）
        meetingRepository.findByRoomName(roomName).ifPresentOrElse(
            existing -> {
                existing.setStatus("ACTIVE");
                existing.setRoomSid(roomSid);
                existing.setStartedAt(java.time.Instant.now());
                existing.touch();
                meetingRepository.save(existing);
            },
            () -> {
                String department = MeetingEntity.parseDepartmentFromRoomName(roomName);
                MeetingEntity meeting = new MeetingEntity(roomName, roomSid, department);
                meetingRepository.save(meeting);
            }
        );

        // TODO: 通知部门大脑"会议已开始"（闭环 46）
    }

    /**
     * 处理会议结束事件（闭环 67-C → 触发闭环 68）
     * room_finished → 会议状态变为 FINISHED，触发纪要管线
     */
    @Transactional
    private void handleRoomFinished(JsonNode eventNode) {
        JsonNode roomNode = eventNode.has("room") ? eventNode.get("room") : eventNode;
        String roomName = roomNode.has("name") ? roomNode.get("name").asText() : "unknown";
        String roomSid = roomNode.has("sid") ? roomNode.get("sid").asText() : "";

        log.info("[P81][67-C] 会议结束 - room={}, sid={}", roomName, roomSid);

        // 持久化到数据库
        meetingRepository.findByRoomName(roomName).ifPresent(existing -> {
            existing.setStatus("FINISHED");
            existing.setFinishedAt(java.time.Instant.now());
            existing.touch();
            meetingRepository.save(existing);
        });

        // TODO: P82 触发闭环 68 - MeetingMinutesService.generateMinutes(roomName)
        // TODO: 通知部门大脑"会议已结束，纪要生成中"（闭环 46）
    }

    /**
     * 处理参会者加入事件（闭环 67-B）
     * participant_joined → 更新参会者列表，持久化到数据库
     */
    @Transactional
    private void handleParticipantJoined(JsonNode eventNode) {
        JsonNode roomNode = eventNode.has("room") ? eventNode.get("room") : eventNode;
        JsonNode participantNode = eventNode.has("participant") ? eventNode.get("participant") : eventNode;

        String roomName = roomNode.has("name") ? roomNode.get("name").asText() : "unknown";
        String identity = participantNode.has("identity") ? participantNode.get("identity").asText() : "unknown";
        String participantName = participantNode.has("name") ? participantNode.get("name").asText() : identity;

        log.info("[P81][67-B] 参会者加入 - room={}, identity={}, name={}", roomName, identity, participantName);

        // 持久化到数据库
        meetingRepository.findByRoomName(roomName).ifPresent(existing -> {
            existing.setParticipantCount(existing.getParticipantCount() + 1);
            existing.touch();
            meetingRepository.save(existing);
        });
    }

    /**
     * 处理参会者离开事件（闭环 67-B）
     * participant_left → 更新参会者列表，持久化到数据库
     */
    @Transactional
    private void handleParticipantLeft(JsonNode eventNode) {
        JsonNode roomNode = eventNode.has("room") ? eventNode.get("room") : eventNode;
        JsonNode participantNode = eventNode.has("participant") ? eventNode.get("participant") : eventNode;

        String roomName = roomNode.has("name") ? roomNode.get("name").asText() : "unknown";
        String identity = participantNode.has("identity") ? participantNode.get("identity").asText() : "unknown";

        log.info("[P81][67-B] 参会者离开 - room={}, identity={}", roomName, identity);

        // 持久化到数据库
        meetingRepository.findByRoomName(roomName).ifPresent(existing -> {
            existing.setParticipantCount(Math.max(0, existing.getParticipantCount() - 1));
            existing.touch();
            meetingRepository.save(existing);
        });
    }

    /**
     * 处理录制开始事件（闭环 68-A，P82 完整实现）
     */
    @Transactional
    private void handleEgressStarted(JsonNode eventNode) {
        String egressId = eventNode.has("egressId") ? eventNode.get("egressId").asText() : "unknown";
        String roomName = eventNode.has("roomName") ? eventNode.get("roomName").asText() : "unknown";

        log.info("[P81][68-A] 录制开始 - room={}, egressId={}", roomName, egressId);

        // 持久化录制状态
        meetingRepository.findByRoomName(roomName).ifPresent(existing -> {
            existing.setRecordingActive(true);
            existing.setEgressId(egressId);
            existing.touch();
            meetingRepository.save(existing);
        });
    }

    /**
     * 处理录制结束事件（闭环 68-A → 68-B，P82 完整实现）
     * 录制文件就绪 → 触发 ASR 转写 → LLM 纪要提取
     */
    @Transactional
    private void handleEgressEnded(JsonNode eventNode) {
        String egressId = eventNode.has("egressId") ? eventNode.get("egressId").asText() : "unknown";
        String roomName = eventNode.has("roomName") ? eventNode.get("roomName").asText() : "unknown";
        String fileLocation = eventNode.has("file") ? eventNode.get("file").asText() : "";

        log.info("[P81][68-A] 录制结束 - room={}, egressId={}, file={}", roomName, egressId, fileLocation);

        // 更新录制状态
        meetingRepository.findByRoomName(roomName).ifPresent(existing -> {
            existing.setRecordingActive(false);
            existing.touch();
            meetingRepository.save(existing);
        });

        // TODO: P82 触发闭环 68-B
        // MeetingMinutesService.generateMinutes(roomName, fileLocation)
        // → FFmpeg 提取音频 → ASR 转写 → LLM 提取决议 → 推送通知
    }

    // ========== Webhook 签名验证 ==========

    /**
     * 验证 LiveKit Webhook 签名
     *
     * <p>LiveKit Webhook 的 Authorization header 格式：
     * <pre>Bearer {api-key}:{hmac-sha256-hex}</pre>
     * 其中 hmac-sha256-hex = HMAC-SHA256(requestBody, apiSecret) 的十六进制表示</p>
     *
     * @param body       请求体原文
     * @param authHeader Authorization header 值
     * @return 签名是否有效
     */
    private boolean verifyWebhookSignature(String body, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[P81] Webhook 缺少 Authorization header");
            return false;
        }

        try {
            String token = authHeader.substring(7);
            int colonIndex = token.indexOf(':');
            if (colonIndex < 0) {
                log.warn("[P81] Webhook token 格式错误（缺少冒号分隔）");
                return false;
            }

            String receivedApiKey = token.substring(0, colonIndex);
            String receivedSignature = token.substring(colonIndex + 1);

            // 验证 API Key 匹配
            if (!liveKitConfig.getApiKey().equals(receivedApiKey)) {
                log.warn("[P81] Webhook API Key 不匹配 - received={}", receivedApiKey);
                return false;
            }

            // 计算 HMAC-SHA256 签名
            String expectedSignature = hmacSha256Hex(body.getBytes(StandardCharsets.UTF_8), liveKitConfig.getApiSecret());

            // 比较签名（常量时间比较，防止时序攻击）
            return constantTimeEquals(expectedSignature, receivedSignature);

        } catch (Exception e) {
            log.error("[P81] Webhook 签名验证异常", e);
            return false;
        }
    }

    /**
     * 计算 HMAC-SHA256 并返回十六进制字符串
     */
    private String hmacSha256Hex(byte[] data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        Key key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(key);
        byte[] hash = mac.doFinal(data);

        // 转换为十六进制字符串
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 常量时间字符串比较（防止时序攻击）
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    // ========== 状态查询接口（内部使用） ==========

    /**
     * 获取会议状态（供其他服务查询，从数据库读取）
     *
     * @param roomName 房间名称
     * @return 会议状态信息，不存在返回空 Map
     */
    public Map<String, Object> getMeetingState(String roomName) {
        return meetingRepository.findByRoomName(roomName)
                .map(m -> {
                    Map<String, Object> state = new LinkedHashMap<>();
                    state.put("roomName", m.getRoomName());
                    state.put("status", m.getStatus());
                    state.put("department", m.getDepartment());
                    state.put("participantCount", m.getParticipantCount());
                    state.put("recordingActive", m.isRecordingActive());
                    state.put("startedAt", m.getStartedAt() != null ? m.getStartedAt().toString() : null);
                    state.put("finishedAt", m.getFinishedAt() != null ? m.getFinishedAt().toString() : null);
                    return state;
                })
                .orElse(Map.of());
    }

    /**
     * 获取所有会议状态（供 VitalSignsService 监控使用，闭环 32 扩展）
     *
     * @return 所有活跃会议的状态
     */
    public Map<String, Map<String, Object>> getAllMeetingStates() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        meetingRepository.findByStatusOrderByStartedAtDesc("ACTIVE").forEach(m -> {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("roomName", m.getRoomName());
            state.put("status", m.getStatus());
            state.put("department", m.getDepartment());
            state.put("participantCount", m.getParticipantCount());
            state.put("recordingActive", m.isRecordingActive());
            state.put("startedAt", m.getStartedAt() != null ? m.getStartedAt().toString() : null);
            result.put(m.getRoomName(), state);
        });
        return result;
    }
}
