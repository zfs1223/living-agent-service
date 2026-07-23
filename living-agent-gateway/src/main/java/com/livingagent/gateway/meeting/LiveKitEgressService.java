package com.livingagent.gateway.meeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * LiveKit 录制(Egress)管理服务 - 闭环 68-A 录制管理 / P82
 *
 * <p>通过 LiveKit Server Egress API 管理会议录制，包括开始/停止/查询录制任务。</p>
 *
 * <h3>LiveKit Egress API 认证</h3>
 * <p>与 RoomService 共用同一套认证机制，使用 LiveKit API key/secret 签名的 JWT token。
 * 认证方式同 {@link LiveKitRoomService}，通过 Bearer token 鉴权。</p>
 *
 * <h3>API 端点</h3>
 * <ul>
 *   <li>POST /twirp/livekit.Egress/StartRoomCompositeEgress - 开始录制（合成所有参与者音视频）</li>
 *   <li>POST /twirp/livekit.Egress/StopEgress - 停止录制</li>
 *   <li>POST /twirp/livekit.Egress/ListEgress - 列出录制任务</li>
 * </ul>
 *
 * <h3>录制文件输出</h3>
 * <p>录制文件输出到 {@code data/recordings/} 目录，文件名格式为
 * {@code {roomName}_{timestamp}.mp4}。</p>
 *
 * @author P82 录制与纪要自动化
 * @since 1.0.0
 */
@Service
public class LiveKitEgressService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitEgressService.class);

    /** LiveKit Twirp Egress API 前缀 */
    private static final String TWIRP_EGRESS_SERVICE = "/twirp/livekit.Egress/";

    /** 录制文件输出目录 */
    private static final String RECORDINGS_DIR = "data/recordings/";

    private final LiveKitConfig liveKitConfig;
    private final LiveKitTokenService tokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LiveKitEgressService(
            LiveKitConfig liveKitConfig,
            LiveKitTokenService tokenService,
            @Qualifier("liveKitRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.liveKitConfig = liveKitConfig;
        this.tokenService = tokenService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        log.info("[P82] LiveKitEgressService 初始化 - apiUrl={}", liveKitConfig.getApiUrl());
    }

    /**
     * 开始录制（闭环 68-A）
     *
     * <p>使用 RoomComposite 模式录制，将所有参与者的音视频合成为单个文件。
     * 录制文件以 MP4 格式存储到本地文件系统。</p>
     *
     * @param roomName       房间名称
     * @param outputFilePath 输出文件路径（如 data/recordings/meeting-xxx.mp4），
     *                       为空则自动生成
     * @return 录制任务信息（包含 egressId）
     */
    public Map<String, Object> startRecording(String roomName, String outputFilePath) {
        String url = liveKitConfig.getApiUrl() + TWIRP_EGRESS_SERVICE + "StartRoomCompositeEgress";

        try {
            // 如果未指定输出路径，自动生成
            if (outputFilePath == null || outputFilePath.isBlank()) {
                String timestamp = String.valueOf(System.currentTimeMillis());
                outputFilePath = RECORDINGS_DIR + roomName + "_" + timestamp + ".mp4";
            }

            // 构建请求体（Twirp JSON 格式）
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("roomName", roomName);

            // 使用 File 输出（本地文件系统存储）
            ObjectNode fileOutput = objectMapper.createObjectNode();
            fileOutput.put("filepath", outputFilePath);
            // 输出格式：MP4（合流模式）
            ObjectNode file = objectMapper.createObjectNode();
            file.put("file", fileOutput);
            // 不使用 preset，默认合流模式
            requestBody.set("output", file);

            // 彸属房间名用于 Egress 查询
            requestBody.put("roomName", roomName);

            HttpEntity<String> entity = createAuthenticatedEntity(requestBody.toString());

            log.info("[P82] 开始录制 - room={}, outputPath={}", roomName, outputFilePath);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode result = objectMapper.readTree(response.getBody());
                Map<String, Object> egressInfo = parseEgressInfo(result);
                log.info("[P82] 录制已启动 - room={}, egressId={}", roomName, egressInfo.get("egressId"));
                return egressInfo;
            }

            log.warn("[P82] 开始录制响应异常 - status={}", response.getStatusCode());
            return Map.of("error", "unexpected_response", "status", response.getStatusCode().toString());

        } catch (RestClientException e) {
            log.error("[P82] 开始录制失败（网络错误） - room={}", roomName, e);
            throw new RuntimeException("开始录制失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[P82] 开始录制失败 - room={}", roomName, e);
            throw new RuntimeException("开始录制失败: " + e.getMessage(), e);
        }
    }

    /**
     * 停止录制（闭环 68-A）
     *
     * @param egressId 录制任务ID（由 startRecording 返回）
     * @return 停止结果
     */
    public Map<String, Object> stopRecording(String egressId) {
        String url = liveKitConfig.getApiUrl() + TWIRP_EGRESS_SERVICE + "StopEgress";

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("egressId", egressId);

            HttpEntity<String> entity = createAuthenticatedEntity(requestBody.toString());

            log.info("[P82] 停止录制 - egressId={}", egressId);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode result = objectMapper.readTree(response.getBody());
                Map<String, Object> egressInfo = parseEgressInfo(result);
                log.info("[P82] 录制已停止 - egressId={}, status={}", egressId, egressInfo.get("status"));
                return egressInfo;
            }

            log.warn("[P82] 停止录制响应异常 - egressId={}, status={}", egressId, response.getStatusCode());
            return Map.of("error", "unexpected_response", "status", response.getStatusCode().toString());

        } catch (RestClientException e) {
            log.error("[P82] 停止录制失败（网络错误） - egressId={}", egressId, e);
            throw new RuntimeException("停止录制失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[P82] 停止录制失败 - egressId={}", egressId, e);
            throw new RuntimeException("停止录制失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列出录制任务（闭环 68-A）
     *
     * @param roomName 房间名称（可选，为空则列出所有录制任务）
     * @return 录制任务列表
     */
    public List<Map<String, Object>> listRecordings(String roomName) {
        String url = liveKitConfig.getApiUrl() + TWIRP_EGRESS_SERVICE + "ListEgress";

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            if (roomName != null && !roomName.isBlank()) {
                requestBody.put("roomName", roomName);
            }

            HttpEntity<String> entity = createAuthenticatedEntity(requestBody.toString());

            log.debug("[P82] 查询录制任务 - room={}", roomName);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode result = objectMapper.readTree(response.getBody());
                List<Map<String, Object>> egressItems = new ArrayList<>();

                // LiveKit Egress ListEgress 返回的 JSON 中，items 为录制任务数组
                String itemsField = "items";
                if (result.has(itemsField) && result.get(itemsField).isArray()) {
                    for (JsonNode item : result.get(itemsField)) {
                        egressItems.add(parseEgressInfo(item));
                    }
                }

                log.debug("[P82] 查询到 {} 个录制任务 - room={}", egressItems.size(), roomName);
                return egressItems;
            }

            return Collections.emptyList();

        } catch (RestClientException e) {
            log.error("[P82] 查询录制任务失败（网络错误） - room={}", roomName, e);
            throw new RuntimeException("查询录制任务失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[P82] 查询录制任务失败 - room={}", roomName, e);
            throw new RuntimeException("查询录制任务失败: " + e.getMessage(), e);
        }
    }

    // ========== 内部方法 ==========

    /**
     * 创建带认证的 HTTP 请求实体
     * 使用 LiveKit API key/secret 生成 admin token 用于服务端 API 调用
     * 认证方式与 LiveKitRoomService 保持一致
     */
    private HttpEntity<String> createAuthenticatedEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 生成服务端管理 token（roomAdmin 权限，可管理所有房间的录制）
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
     * 解析录制任务信息
     */
    private Map<String, Object> parseEgressInfo(JsonNode egressNode) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (egressNode.has("egressId")) info.put("egressId", egressNode.get("egressId").asText());
        if (egressNode.has("roomId")) info.put("roomId", egressNode.get("roomId").asText());
        if (egressNode.has("roomName")) info.put("roomName", egressNode.get("roomName").asText());
        if (egressNode.has("status")) info.put("status", egressNode.get("status").asText());
        if (egressNode.has("startedAt")) info.put("startedAt", egressNode.get("startedAt").asLong());
        if (egressNode.has("endedAt")) info.put("endedAt", egressNode.get("endedAt").asLong());
        if (egressNode.has("error")) info.put("error", egressNode.get("error").asText());

        // 解析文件输出信息
        if (egressNode.has("file") && !egressNode.get("file").isNull()) {
            JsonNode fileNode = egressNode.get("file");
            Map<String, Object> fileInfo = new LinkedHashMap<>();
            if (fileNode.has("filename")) fileInfo.put("filename", fileNode.get("filename").asText());
            if (fileNode.has("filepath")) fileInfo.put("filepath", fileNode.get("filepath").asText());
            if (fileNode.has("size")) fileInfo.put("size", fileNode.get("size").asLong());
            if (fileNode.has("duration")) fileInfo.put("duration", fileNode.get("duration").asLong());
            info.put("file", fileInfo);
        }

        return info;
    }
}
