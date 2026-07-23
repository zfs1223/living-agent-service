package com.livingagent.gateway.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.gateway.websocket.DepartmentWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * AI 实时字幕服务 - 闭环 68 录制纪要扩展 / P85 会议深度集成
 *
 * <p>会议中实时语音转文字字幕，将参会者的语音实时转写为文字，
 * 并通过 WebSocket 广播给会议中所有参与者，实现实时字幕显示。</p>
 *
 * <h3>处理链路</h3>
 * <pre>
 * 音频数据(LiveKit WebHook/SDK) → ASR转写(model_daemon.py /v1/asr) → WebSocket广播字幕
 * </pre>
 *
 * <h3>字幕格式</h3>
 * <pre>
 * {
 *   "type": "caption",
 *   "participantId": "xxx",
 *   "text": "说的内容",
 *   "timestamp": 1234567890,
 *   "roomName": "dept-tech-meeting-abc123"
 * }
 * </pre>
 *
 * <h3>外部依赖</h3>
 * <ul>
 *   <li>ASR: {@code http://living-agent-service:8392/v1/asr} (model_daemon.py Sherpa-ONNX)</li>
 *   <li>WebSocket: DepartmentWebSocketHandler.broadcastRawJson 广播字幕</li>
 * </ul>
 *
 * @author P85 会议深度集成
 * @since 1.0.0
 */
@Service
public class MeetingCaptionService {

    private static final Logger log = LoggerFactory.getLogger(MeetingCaptionService.class);

    /** model_daemon.py 提供的 ASR 端点 */
    private static final String ASR_ENDPOINT = "/v1/asr";

    /** 字幕消息类型标识 */
    private static final String CAPTION_TYPE = "caption";

    private final DepartmentWebSocketHandler webSocketHandler;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** model_daemon.py 基础地址 */
    @Value("${model-daemon.url:http://living-agent-service:8392}")
    private String modelDaemonUrl;

    /** 是否启用实时字幕（默认启用） */
    @Value("${meeting.caption.enabled:true}")
    private boolean captionEnabled;

    /** 字幕文本最小长度阈值，低于此长度的转写结果不广播（过滤噪音） */
    @Value("${meeting.caption.min-text-length:2}")
    private int minTextLength;

    /** 当前活跃的会议房间与部门的映射缓存 */
    private final Map<String, String> roomDepartmentCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 参会者最近一次字幕时间戳（用于防抖） */
    private final Map<String, Long> lastCaptionTimestamp = new java.util.concurrent.ConcurrentHashMap<>();

    /** 字幕防抖间隔（毫秒），同一参会者在此间隔内不重复发送 */
    @Value("${meeting.caption.debounce-ms:300}")
    private long debounceMs;

    public MeetingCaptionService(
            DepartmentWebSocketHandler webSocketHandler,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.webSocketHandler = webSocketHandler;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        log.info("[P85] MeetingCaptionService 初始化");
    }

    // ========== 公开接口 ==========

    /**
     * 收到音频数据时触发
     *
     * <p>当 LiveKit 通过 WebHook 或 SDK 推送参会者的音频数据时调用，
     * 将音频数据送入 ASR 转写，然后将转写结果广播为字幕。</p>
     *
     * <p>音频来源：</p>
     * <ul>
     *   <li>LiveKit WebHook: participant_track 事件中的音频轨道数据</li>
     *   <li>LiveKit SDK: 通过 Egress API 获取的音频流</li>
     *   <li>前端上传: 参会者浏览器捕获的音频片段</li>
     * </ul>
     *
     * @param roomName     LiveKit 房间名称
     * @param participantId 参会者ID
     * @param audioData    音频数据（Base64 编码的 PCM/Opus 数据）
     */
    public void onAudioTrackAvailable(String roomName, String participantId, byte[] audioData) {
        if (!captionEnabled) {
            return;
        }

        if (audioData == null || audioData.length == 0) {
            log.debug("[P85] 音频数据为空，跳过 - room={}, participant={}", roomName, participantId);
            return;
        }

        // 防抖检查：同一参会者在 debounceMs 内不重复处理
        String debounceKey = roomName + ":" + participantId;
        long now = System.currentTimeMillis();
        Long lastTime = lastCaptionTimestamp.get(debounceKey);
        if (lastTime != null && (now - lastTime) < debounceMs) {
            return;
        }
        lastCaptionTimestamp.put(debounceKey, now);

        log.debug("[P85] 收到音频数据 - room={}, participant={}, dataLength={}",
                roomName, participantId, audioData.length);

        try {
            // ASR 转写
            String text = transcribeChunk(audioData);
            if (text == null || text.isBlank() || text.length() < minTextLength) {
                log.debug("[P85] ASR 转写结果过短，跳过广播 - room={}, participant={}, textLength={}",
                        roomName, participantId, text != null ? text.length() : 0);
                return;
            }

            // 广播字幕
            broadcastCaption(roomName, participantId, text);

        } catch (Exception e) {
            log.warn("[P85] 音频处理失败 - room={}, participant={}, error={}",
                    roomName, participantId, e.getMessage());
        }
    }

    /**
     * 调用 ASR 转写音频片段
     *
     * <p>通过 HTTP 调用 model_daemon.py 的 ASR 端点 {@code /v1/asr}，
     * 将音频字节数据转写为文本。</p>
     *
     * <p>支持两种输入格式：</p>
     * <ul>
     *   <li>Base64 编码的音频数据（通过 audio_base64 参数）</li>
     *   <li>音频文件路径（通过 audio_path 参数）</li>
     * </ul>
     *
     * @param audioData 音频字节数据
     * @return 转写文本，失败返回 null
     */
    public String transcribeChunk(byte[] audioData) {
        String url = modelDaemonUrl + ASR_ENDPOINT;

        try {
            // 构建 ASR 请求体：使用 Base64 编码的音频数据
            String audioBase64 = Base64.getEncoder().encodeToString(audioData);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("audio_base64", audioBase64);
            requestBody.put("language", "zh");
            requestBody.put("format", "wav");
            // 实时字幕模式，启用流式转写优化
            requestBody.put("streaming", true);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            log.debug("[P85] 调用ASR转写 - dataLength={}", audioData.length);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                var result = objectMapper.readTree(response.getBody());
                // ASR 返回格式：{"text": "转写文本", ...}
                if (result.has("text")) {
                    return result.get("text").asText();
                }
                // 兼容嵌套格式
                if (result.has("result") && result.get("result").has("text")) {
                    return result.get("result").get("text").asText();
                }
                log.warn("[P85] ASR返回格式非标准 - response={}", response.getBody());
                return null;
            }

            log.warn("[P85] ASR转写失败 - httpStatus={}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.error("[P85] ASR转写异常 - error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 通过 WebSocket 广播字幕
     *
     * <p>将转写文本作为字幕消息，通过 DepartmentWebSocketHandler 广播给
     * 会议所在部门的所有在线 WebSocket 连接。</p>
     *
     * <p>字幕消息格式：</p>
     * <pre>
     * {
     *   "type": "caption",
     *   "participantId": "xxx",
     *   "text": "说的内容",
     *   "timestamp": 1234567890,
     *   "roomName": "dept-tech-meeting-abc123"
     * }
     * </pre>
     *
     * @param roomName      LiveKit 房间名称
     * @param participantId 参会者ID
     * @param text          转写文本
     */
    public void broadcastCaption(String roomName, String participantId, String text) {
        try {
            // 从房间名推断部门
            String department = extractDepartmentFromRoomName(roomName);
            roomDepartmentCache.putIfAbsent(roomName, department);

            Map<String, Object> captionMessage = new LinkedHashMap<>();
            captionMessage.put("type", CAPTION_TYPE);
            captionMessage.put("participantId", participantId != null ? participantId : "");
            captionMessage.put("text", text != null ? text : "");
            captionMessage.put("timestamp", Instant.now().toEpochMilli());
            captionMessage.put("roomName", roomName);

            String rawJson = objectMapper.writeValueAsString(captionMessage);
            webSocketHandler.broadcastRawJson(department, rawJson);

            log.debug("[P85] 字幕广播成功 - dept={}, room={}, participant={}, textLength={}",
                    department, roomName, participantId, text != null ? text.length() : 0);

        } catch (Exception e) {
            log.warn("[P85] 字幕广播失败 - room={}, participant={}, error={}",
                    roomName, participantId, e.getMessage());
        }
    }

    // ========== 管理接口 ==========

    /**
     * 注册房间与部门的映射
     *
     * <p>在会议开始时调用，明确房间归属的部门，
     * 避免每次广播时都要从房间名推断部门。</p>
     *
     * @param roomName   房间名称
     * @param department 部门代码
     */
    public void registerRoomDepartment(String roomName, String department) {
        roomDepartmentCache.put(roomName, department);
        log.info("[P85] 房间部门映射注册 - room={}, department={}", roomName, department);
    }

    /**
     * 移除房间与部门的映射
     *
     * <p>在会议结束时调用，清理缓存。</p>
     *
     * @param roomName 房间名称
     */
    public void unregisterRoomDepartment(String roomName) {
        roomDepartmentCache.remove(roomName);
        log.info("[P85] 房间部门映射移除 - room={}", roomName);
    }

    /**
     * 获取字幕服务状态
     *
     * @return 状态信息
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("captionEnabled", captionEnabled);
        status.put("minTextLength", minTextLength);
        status.put("debounceMs", debounceMs);
        status.put("activeRooms", roomDepartmentCache.size());
        status.put("asrEndpoint", modelDaemonUrl + ASR_ENDPOINT);
        return status;
    }

    // ========== 内部方法 ==========

    /**
     * 从房间名称推断部门代码
     *
     * <p>优先使用缓存映射，其次从房间命名规范推断，
     * 房间命名规范: dept-{departmentCode}-meeting-{uuid}</p>
     *
     * @param roomName 房间名称
     * @return 部门代码，默认 admin
     */
    private String extractDepartmentFromRoomName(String roomName) {
        // 优先使用缓存
        String cached = roomDepartmentCache.get(roomName);
        if (cached != null) {
            return cached;
        }

        // 从房间名推断
        if (roomName != null && roomName.startsWith("dept-")) {
            String[] parts = roomName.split("-");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return "admin";
    }
}
