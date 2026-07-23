package com.livingagent.gateway.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * LiveKit 会议室管理服务 - 闭环 67-A 创建 / 67-C 状态
 *
 * <p>通过 LiveKit Server REST API 管理会议室的创建、查询、删除，
 * 以及参会者的列表查询和移除操作。</p>
 *
 * <h3>LiveKit Server API 认证</h3>
 * <p>所有 API 调用需在 HTTP Header 中携带 LiveKit 签名：
 * <pre>Authorization: Bearer {livekit-jwt-token}</pre>
 * 其中 token 由 {@link LiveKitTokenService} 生成，具有 roomAdmin 权限。</p>
 *
 * <h3>API 端点</h3>
 * <ul>
 *   <li>POST /twirp/livekit.RoomService/CreateRoom - 创建房间</li>
 *   <li>POST /twirp/livekit.RoomService/ListRooms - 列出房间</li>
 *   <li>POST /twirp/livekit.RoomService/DeleteRoom - 删除房间</li>
 *   <li>POST /twirp/livekit.RoomService/ListParticipants - 列出参与者</li>
 *   <li>POST /twirp/livekit.RoomService/RemoveParticipant - 移除参与者</li>
 * </ul>
 *
 * @author P81 LiveKit 部署与会议基础
 * @since 1.0.0
 */
@Service
public class LiveKitRoomService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitRoomService.class);

    /** LiveKit Twirp RPC API 前缀 */
    private static final String TWIRP_ROOM_SERVICE = "/twirp/livekit.RoomService/";

    private final LiveKitConfig liveKitConfig;
    private final LiveKitTokenService tokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LiveKitRoomService(
            LiveKitConfig liveKitConfig,
            LiveKitTokenService tokenService,
            @Qualifier("liveKitRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.liveKitConfig = liveKitConfig;
        this.tokenService = tokenService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        log.info("[P81] LiveKitRoomService 初始化 - apiUrl={}", liveKitConfig.getApiUrl());
    }

    /**
     * 创建会议室（闭环 67-A 会议创建闭环）
     *
     * @param name           房间名称（建议格式: dept-{deptCode}-meeting-{uuid}）
     * @param maxParticipants 最大参与人数
     * @return 创建结果（包含房间信息）
     */
    public Map<String, Object> createRoom(String name, int maxParticipants) {
        String url = liveKitConfig.getApiUrl() + TWIRP_ROOM_SERVICE + "CreateRoom";

        try {
            // 构建请求体（Twirp JSON 格式）
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("name", name);
            requestBody.put("maxParticipants", maxParticipants);
            // 空房间超时 300 秒（5 分钟）
            requestBody.put("emptyTimeout", 300);
            // 所有人离开后 30 秒关闭
            requestBody.put("departureTimeout", 30);

            // 设置房间元数据
            ObjectNode metadata = objectMapper.createObjectNode();
            metadata.put("createdBy", "las-system");
            metadata.put("createdAt", java.time.Instant.now().toString());
            requestBody.put("metadata", metadata.toString());

            HttpEntity<String> entity = createAuthenticatedEntity(requestBody.toString());

            log.info("[P81] 创建会议室 - name={}, maxParticipants={}", name, maxParticipants);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode result = objectMapper.readTree(response.getBody());
                Map<String, Object> roomInfo = parseRoomInfo(result);
                log.info("[P81] 会议室创建成功 - name={}, sid={}", name, roomInfo.get("sid"));
                return roomInfo;
            }

            log.warn("[P81] 会议室创建响应异常 - status={}", response.getStatusCode());
            return Map.of("error", "unexpected_response", "status", response.getStatusCode().toString());

        } catch (RestClientException e) {
            log.error("[P81] 创建会议室失败（网络错误） - name={}", name, e);
            throw new RuntimeException("创建会议室失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[P81] 创建会议室失败 - name={}", name, e);
            throw new RuntimeException("创建会议室失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出所有会议室（闭环 67-A 查询）
     *
     * @return 房间列表
     */
    public List<Map<String, Object>> listRooms() {
        String url = liveKitConfig.getApiUrl() + TWIRP_ROOM_SERVICE + "ListRooms";

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();

            HttpEntity<String> entity = createAuthenticatedEntity(requestBody.toString());

            log.debug("[P81] 查询会议室列表");
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode result = objectMapper.readTree(response.getBody());
                List<Map<String, Object>> rooms = new ArrayList<>();

                if (result.has("rooms") && result.get("rooms").isArray()) {
                    for (JsonNode room : result.get("rooms")) {
                        rooms.add(parseRoomInfo(room));
                    }
                }

                log.debug("[P81] 查询到 {} 个会议室", rooms.size());
                return rooms;
            }

            return Collections.emptyList();

        } catch (RestClientException e) {
            log.error("[P81] 查询会议室列表失败（网络错误）", e);
            throw new RuntimeException("查询会议室列表失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[P81] 查询会议室列表失败", e);
            throw new RuntimeException("查询会议室列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除会议室/结束会议（闭环 67-C 会议状态闭环）
     *
     * @param roomName 房间名称
     */
    public void deleteRoom(String roomName) {
        String url = liveKitConfig.getApiUrl() + TWIRP_ROOM_SERVICE + "DeleteRoom";

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("room", roomName);

            HttpEntity<String> entity = createAuthenticatedEntity(requestBody.toString());

            log.info("[P81] 结束会议室 - room={}", roomName);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[P81] 会议室已结束 - room={}", roomName);
            } else {
                log.warn("[P81] 结束会议室响应异常 - room={}, status={}", roomName, response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("[P81] 结束会议室失败（网络错误） - room={}", roomName, e);
            throw new RuntimeException("结束会议室失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[P81] 结束会议室失败 - room={}", roomName, e);
            throw new RuntimeException("结束会议室失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出会议室参与者（闭环 67-B 会议执行闭环）
     *
     * @param roomName 房间名称
     * @return 参与者列表
     */
    public List<Map<String, Object>> listParticipants(String roomName) {
        String url = liveKitConfig.getApiUrl() + TWIRP_ROOM_SERVICE + "ListParticipants";

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("room", roomName);

            HttpEntity<String> entity = createAuthenticatedEntity(requestBody.toString());

            log.debug("[P81] 查询参与者 - room={}", roomName);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode result = objectMapper.readTree(response.getBody());
                List<Map<String, Object>> participants = new ArrayList<>();

                if (result.has("participants") && result.get("participants").isArray()) {
                    for (JsonNode participant : result.get("participants")) {
                        participants.add(parseParticipantInfo(participant));
                    }
                }

                log.debug("[P81] 查询到 {} 位参与者 - room={}", participants.size(), roomName);
                return participants;
            }

            return Collections.emptyList();

        } catch (RestClientException e) {
            log.error("[P81] 查询参与者失败（网络错误） - room={}", roomName, e);
            throw new RuntimeException("查询参与者失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[P81] 查询参与者失败 - room={}", roomName, e);
            throw new RuntimeException("查询参与者失败: " + e.getMessage(), e);
        }
    }

    /**
     * 移除参与者（闭环 67-B 会议执行闭环）
     *
     * @param roomName 房间名称
     * @param identity 参与者 identity
     */
    public void removeParticipant(String roomName, String identity) {
        String url = liveKitConfig.getApiUrl() + TWIRP_ROOM_SERVICE + "RemoveParticipant";

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("room", roomName);
            requestBody.put("identity", identity);

            HttpEntity<String> entity = createAuthenticatedEntity(requestBody.toString());

            log.info("[P81] 移除参与者 - room={}, identity={}", roomName, identity);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[P81] 参与者已移除 - room={}, identity={}", roomName, identity);
            } else {
                log.warn("[P81] 移除参与者响应异常 - room={}, identity={}, status={}",
                        roomName, identity, response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("[P81] 移除参与者失败（网络错误） - room={}, identity={}", roomName, identity, e);
            throw new RuntimeException("移除参与者失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[P81] 移除参与者失败 - room={}, identity={}", roomName, identity, e);
            throw new RuntimeException("移除参与者失败: " + e.getMessage(), e);
        }
    }

    // ========== 内部方法 ==========

    /**
     * 创建带认证的 HTTP 请求实体
     * 使用 LiveKit API key/secret 生成 admin token 用于服务端 API 调用
     */
    private HttpEntity<String> createAuthenticatedEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 生成服务端管理 token（roomList 权限，可管理所有房间）
        String adminToken = tokenService.generateToken(
                "las-server-admin",  // 服务端内部身份
                "",                  // 空 roomName 表示管理所有房间
                true,               // canPublish
                true,               // canSubscribe
                Map.of("role", "server-admin")  // 元数据
        );
        headers.setBearerAuth(adminToken);

        return new HttpEntity<>(body, headers);
    }

    /**
     * 解析房间信息
     */
    private Map<String, Object> parseRoomInfo(JsonNode roomNode) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (roomNode.has("sid")) info.put("sid", roomNode.get("sid").asText());
        if (roomNode.has("name")) info.put("name", roomNode.get("name").asText());
        if (roomNode.has("emptyTimeout")) info.put("emptyTimeout", roomNode.get("emptyTimeout").asInt());
        if (roomNode.has("departureTimeout")) info.put("departureTimeout", roomNode.get("departureTimeout").asInt());
        if (roomNode.has("maxParticipants")) info.put("maxParticipants", roomNode.get("maxParticipants").asInt());
        if (roomNode.has("creationTime")) info.put("creationTime", roomNode.get("creationTime").asLong());
        if (roomNode.has("metadata")) info.put("metadata", roomNode.get("metadata").asText());
        if (roomNode.has("numParticipants")) info.put("numParticipants", roomNode.get("numParticipants").asInt());
        if (roomNode.has("numPublishers")) info.put("numPublishers", roomNode.get("numPublishers").asInt());
        if (roomNode.has("activeRecording")) info.put("activeRecording", roomNode.get("activeRecording").asBoolean());
        return info;
    }

    /**
     * 解析参与者信息
     */
    private Map<String, Object> parseParticipantInfo(JsonNode participantNode) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (participantNode.has("sid")) info.put("sid", participantNode.get("sid").asText());
        if (participantNode.has("identity")) info.put("identity", participantNode.get("identity").asText());
        if (participantNode.has("state")) info.put("state", participantNode.get("state").asText());
        if (participantNode.has("metadata")) info.put("metadata", participantNode.get("metadata").asText());
        if (participantNode.has("joinedAt")) info.put("joinedAt", participantNode.get("joinedAt").asLong());
        if (participantNode.has("name")) info.put("name", participantNode.get("name").asText());

        // 解析音视频轨道
        if (participantNode.has("tracks") && participantNode.get("tracks").isArray()) {
            List<Map<String, Object>> tracks = new ArrayList<>();
            for (JsonNode track : participantNode.get("tracks")) {
                Map<String, Object> trackInfo = new LinkedHashMap<>();
                if (track.has("sid")) trackInfo.put("sid", track.get("sid").asText());
                if (track.has("name")) trackInfo.put("name", track.get("name").asText());
                if (track.has("source")) trackInfo.put("source", track.get("source").asText());
                if (track.has("muted")) trackInfo.put("muted", track.get("muted").asBoolean());
                tracks.add(trackInfo);
            }
            info.put("tracks", tracks);
        }

        return info;
    }
}
