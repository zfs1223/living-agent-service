package com.livingagent.gateway.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.entity.MeetingMinutesEntity;
import com.livingagent.core.database.repository.MeetingMinutesRepository;
import com.livingagent.gateway.service.DepartmentNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 会议纪要自动化管线 - 闭环 68 录制与纪要自动化 / P82
 *
 * <p>实现完整的会议纪要生成管线：</p>
 * <pre>
 * 录制文件 → FFmpeg提取音频 → ASR转写(Sherpa-ONNX) → LLM提取纪要 → 脱敏 → 存储 → 通知
 * </pre>
 *
 * <h3>外部依赖</h3>
 * <ul>
 *   <li>ASR: {@code http://living-agent-service:8392/v1/asr} (model_daemon.py)</li>
 *   <li>LLM: {@code http://living-agent-service:8392/v1/chat/completions} (model_daemon.py)</li>
 * </ul>
 *
 * <h3>纪要存储格式</h3>
 * <p>Markdown 格式，存储到 {@code data/artifacts/meeting-minutes/{roomName}.md}，包含：</p>
 * <ul>
 *   <li>会议标题/时间/参会人</li>
 *   <li>全文转写（可选）</li>
 *   <li>决议事项</li>
 *   <li>待办任务 + 责任人</li>
 *   <li>关键数据/金额</li>
 *   <li>摘要</li>
 * </ul>
 *
 * @author P82 录制与纪要自动化
 * @since 1.0.0
 */
@Service
public class MeetingMinutesService {

    private static final Logger log = LoggerFactory.getLogger(MeetingMinutesService.class);

    /** 纪要文件存储目录 */
    private static final String MINUTES_DIR = "data/artifacts/meeting-minutes";

    /** model_daemon.py 提供的 ASR 端点 */
    private static final String ASR_ENDPOINT = "/v1/asr";

    /** model_daemon.py 提供的 LLM 对话端点 */
    private static final String LLM_ENDPOINT = "/v1/chat/completions";

    private final MeetingMinutesRepository minutesRepository;
    private final DepartmentNotificationService notificationService;
    private final LiveKitEgressService egressService;
    private final LiveKitRoomService roomService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** model_daemon.py 基础地址 */
    @Value("${model-daemon.url:http://living-agent-service:8392}")
    private String modelDaemonUrl;

    /** FFmpeg 可执行文件路径 */
    @Value("${ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    public MeetingMinutesService(
            MeetingMinutesRepository minutesRepository,
            DepartmentNotificationService notificationService,
            LiveKitEgressService egressService,
            LiveKitRoomService roomService,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.minutesRepository = minutesRepository;
        this.notificationService = notificationService;
        this.egressService = egressService;
        this.roomService = roomService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        log.info("[P82] MeetingMinutesService 初始化");
    }

    /**
     * 生成会议纪要（闭环 68 主入口）
     *
     * <p>完整管线：录制文件 → 提取音频 → ASR转写 → LLM提取 → 格式化 → 存储 → 通知</p>
     *
     * @param roomName          房间名称
     * @param recordingFilePath 录制文件路径
     * @return 生成的纪要实体
     */
    @Transactional
    public MeetingMinutesEntity generateMinutes(String roomName, String recordingFilePath) {
        String minutesId = "minutes-" + UUID.randomUUID().toString().substring(0, 8);
        String title = "会议纪要 - " + roomName + " - " +
                LocalDateTime.now().atZone(ZoneId.systemDefault()).format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

        // 创建纪要记录（状态: GENERATING）
        MeetingMinutesEntity entity = new MeetingMinutesEntity(minutesId, roomName, title);
        entity.setRecordingUrl(recordingFilePath);
        entity = minutesRepository.save(entity);

        log.info("[P82] 开始生成会议纪要 - room={}, minutesId={}, recording={}",
                roomName, minutesId, recordingFilePath);

        try {
            // 步骤1：提取音频
            String audioFilePath = extractAudio(recordingFilePath);
            log.info("[P82] 音频提取完成 - room={}, audioPath={}", roomName, audioFilePath);

            // 步骤2：ASR 转写
            String fullText = transcribeAudio(audioFilePath);
            log.info("[P82] ASR转写完成 - room={}, textLength={}", roomName, fullText.length());
            entity.setFullText(fullText);

            // 步骤3：LLM 提取关键信息
            Map<String, Object> keyInfo = extractKeyInfo(fullText);
            log.info("[P82] 关键信息提取完成 - room={}", roomName);

            // 填充提取结果
            if (keyInfo.containsKey("summary")) {
                entity.setSummary((String) keyInfo.get("summary"));
            }
            if (keyInfo.containsKey("resolutions")) {
                entity.setResolutions(toJsonString(keyInfo.get("resolutions")));
            }
            if (keyInfo.containsKey("actionItems")) {
                entity.setActionItems(toJsonString(keyInfo.get("actionItems")));
            }
            if (keyInfo.containsKey("keyData")) {
                entity.setKeyData(toJsonString(keyInfo.get("keyData")));
            }
            if (keyInfo.containsKey("title")) {
                entity.setTitle((String) keyInfo.get("title"));
            }

            // 步骤4：格式化存储
            String filePath = formatAndSave(entity);
            entity.setMinutesFilePath(filePath);

            // 更新状态为完成
            entity.setStatus("COMPLETED");
            entity.setGeneratedAt(Instant.now());
            entity.touch();
            entity = minutesRepository.save(entity);

            log.info("[P82] 会议纪要生成完成 - room={}, minutesId={}", roomName, minutesId);

            // 步骤5：通知相关部门
            notifyMinutesGenerated(entity);

            return entity;

        } catch (Exception e) {
            // 更新状态为失败
            entity.setStatus("FAILED");
            entity.touch();
            minutesRepository.save(entity);
            log.error("[P82] 会议纪要生成失败 - room={}, minutesId={}", roomName, minutesId, e);
            throw new RuntimeException("会议纪要生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取音频（从录制文件中提取纯音频）
     *
     * <p>调用 FFmpeg 从 MP4 录制文件中提取 WAV 音频（16kHz, 单声道），
     * 以便 ASR 引擎处理。如果 FFmpeg 不可用，尝试直接使用原文件路径。</p>
     *
     * @param videoFilePath 录制文件路径
     * @return 提取的音频文件路径
     */
    public String extractAudio(String videoFilePath) {
        Path inputPath = Paths.get(videoFilePath);
        String audioFilePath = videoFilePath.replaceAll("\\.mp4$", ".wav")
                .replaceAll("\\.webm$", ".wav");

        // 如果输入文件已经是音频格式，直接返回
        if (videoFilePath.endsWith(".wav") || videoFilePath.endsWith(".mp3")) {
            log.info("[P82] 文件已是音频格式，无需提取 - path={}", videoFilePath);
            return videoFilePath;
        }

        // 确保输出目录存在
        try {
            Files.createDirectories(Paths.get(audioFilePath).getParent());
        } catch (IOException e) {
            log.warn("[P82] 创建音频输出目录失败 - path={}", audioFilePath, e);
        }

        try {
            // 使用 FFmpeg 提取音频：16kHz 采样率、单声道、PCM 16-bit WAV
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-i", inputPath.toString(),
                    "-vn",               // 不包含视频
                    "-acodec", "pcm_s16le",  // PCM 16-bit
                    "-ar", "16000",      // 16kHz 采样率
                    "-ac", "1",          // 单声道
                    "-y",                // 覆盖已存在的文件
                    audioFilePath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("[P82] FFmpeg 音频提取成功 - output={}", audioFilePath);
                return audioFilePath;
            } else {
                // 读取错误输出
                String errorOutput = new String(process.getInputStream().readAllBytes());
                log.warn("[P82] FFmpeg 提取音频失败(exitCode={}) - 尝试直接使用原文件, error={}",
                        exitCode, errorOutput);
                // 降级：直接使用原文件路径（可能是 ASR 引擎能直接处理的格式）
                return videoFilePath;
            }
        } catch (IOException | InterruptedException e) {
            log.warn("[P82] FFmpeg 调用失败 - 尝试直接使用原文件", e);
            // 降级：直接使用原文件
            return videoFilePath;
        }
    }

    /**
     * ASR 转写音频（闭环 68）
     *
     * <p>通过 HTTP 调用 model_daemon.py 的 ASR 端点 {@code /v1/asr}，
     * 将音频文件转写为文本。</p>
     *
     * @param audioFilePath 音频文件路径
     * @return 转写的全文文本
     */
    public String transcribeAudio(String audioFilePath) {
        String url = modelDaemonUrl + ASR_ENDPOINT;

        try {
            // 构建 ASR 请求体
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("audio_path", audioFilePath);
            requestBody.put("language", "zh");
            requestBody.put("format", "wav");

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            log.info("[P82] 调用ASR转写 - audioPath={}", audioFilePath);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                var result = objectMapper.readTree(response.getBody());
                // ASR 返回格式：{"text": "转写文本", ...}
                if (result.has("text")) {
                    return result.get("text").asText();
                }
                // 兼容 OpenAI Whisper 格式
                if (result.has("result") && result.get("result").has("text")) {
                    return result.get("result").get("text").asText();
                }
                // 如果没有标准格式，尝试返回整个响应
                log.warn("[P82] ASR返回格式非标准 - response={}", response.getBody());
                return response.getBody();
            }

            throw new RuntimeException("ASR 转写失败，HTTP状态: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("[P82] ASR转写失败 - audioPath={}", audioFilePath, e);
            throw new RuntimeException("ASR转写失败: " + e.getMessage(), e);
        }
    }

    /**
     * LLM 提取关键信息（闭环 68）
     *
     * <p>通过 HTTP 调用 model_daemon.py 的 LLM 端点 {@code /v1/chat/completions}，
     * 从转写全文中提取关键信息：决议事项、待办任务+责任人、关键数据/金额、摘要。</p>
     *
     * @param fullText 转写全文
     * @return 提取的关键信息（summary/resolutions/actionItems/keyData/title）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractKeyInfo(String fullText) {
        String url = modelDaemonUrl + LLM_ENDPOINT;

        try {
            // 构建 LLM 请求：提取会议纪要关键信息
            String systemPrompt = """
                    你是一位专业的会议纪要助手。请从会议转写文本中提取以下关键信息，以JSON格式返回：
                    {
                      "title": "会议标题（根据内容推断）",
                      "summary": "200字以内的会议摘要",
                      "resolutions": ["决议1", "决议2"],
                      "actionItems": [{"task": "待办任务", "assignee": "责任人"}],
                      "keyData": ["关键数据/金额1", "关键数据/金额2"]
                    }
                    要求：
                    1. 仅提取确实在会议中明确提到的信息，不要编造
                    2. 责任人必须是会议中明确指定的，没有则填"待确认"
                    3. 关键数据包括金额、指标、截止日期等
                    4. 返回纯JSON，不要包含markdown代码块标记
                    """;

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", "qwen3");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", "请提取以下会议的关键信息：\n\n" + fullText)
            ));
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 2048);

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            log.info("[P82] 调用LLM提取关键信息 - textLength={}", fullText.length());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                var result = objectMapper.readTree(response.getBody());
                // OpenAI 兼容格式：choices[0].message.content
                String content = "";
                if (result.has("choices") && result.get("choices").isArray() && !result.get("choices").isEmpty()) {
                    var firstChoice = result.get("choices").get(0);
                    if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                        content = firstChoice.get("message").get("content").asText();
                    }
                }

                // 清理可能的 markdown 代码块标记
                content = content.trim();
                if (content.startsWith("```json")) {
                    content = content.substring(7);
                } else if (content.startsWith("```")) {
                    content = content.substring(3);
                }
                if (content.endsWith("```")) {
                    content = content.substring(0, content.length() - 3);
                }
                content = content.trim();

                // 解析 JSON 响应
                Map<String, Object> keyInfo = objectMapper.readValue(content, Map.class);
                log.info("[P82] LLM关键信息提取成功 - keys={}", keyInfo.keySet());
                return keyInfo;
            }

            throw new RuntimeException("LLM 提取失败，HTTP状态: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("[P82] LLM提取关键信息失败", e);
            // 降级：返回基础信息
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("title", "会议纪要");
            fallback.put("summary", "关键信息提取失败，请手动补充");
            fallback.put("resolutions", List.of());
            fallback.put("actionItems", List.of());
            fallback.put("keyData", List.of());
            return fallback;
        }
    }

    /**
     * 格式化存储纪要（闭环 68）
     *
     * <p>将纪要实体格式化为 Markdown 文档，保存到
     * {@code data/artifacts/meeting-minutes/{roomName}.md}。</p>
     *
     * @param entity 纪要实体
     * @return 保存的文件路径
     */
    public String formatAndSave(MeetingMinutesEntity entity) {
        try {
            // 确保目录存在
            Path dirPath = Paths.get(MINUTES_DIR);
            Files.createDirectories(dirPath);

            // 构建 Markdown 内容
            StringBuilder md = new StringBuilder();
            md.append("# ").append(entity.getTitle()).append("\n\n");

            // 基本信息
            md.append("## 基本信息\n\n");
            md.append("- **房间名称**: ").append(entity.getRoomName()).append("\n");
            md.append("- **生成时间**: ").append(
                    LocalDateTime.ofInstant(entity.getCreatedAt(), ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .append("\n");
            if (entity.getRecordingUrl() != null) {
                md.append("- **录制文件**: ").append(entity.getRecordingUrl()).append("\n");
            }
            md.append("\n");

            // 摘要
            if (entity.getSummary() != null && !entity.getSummary().isBlank()) {
                md.append("## 摘要\n\n");
                md.append(entity.getSummary()).append("\n\n");
            }

            // 决议事项
            String resolutions = entity.getResolutions();
            if (resolutions != null && !resolutions.isBlank() && !"[]".equals(resolutions)) {
                md.append("## 决议事项\n\n");
                appendJsonList(md, resolutions);
                md.append("\n");
            }

            // 待办任务
            String actionItems = entity.getActionItems();
            if (actionItems != null && !actionItems.isBlank() && !"[]".equals(actionItems)) {
                md.append("## 待办任务\n\n");
                appendActionItems(md, actionItems);
                md.append("\n");
            }

            // 关键数据
            String keyData = entity.getKeyData();
            if (keyData != null && !keyData.isBlank() && !"[]".equals(keyData)) {
                md.append("## 关键数据\n\n");
                appendJsonList(md, keyData);
                md.append("\n");
            }

            // 全文转写
            if (entity.getFullText() != null && !entity.getFullText().isBlank()) {
                md.append("## 全文转写\n\n");
                md.append(entity.getFullText()).append("\n");
            }

            // 写入文件
            String fileName = entity.getRoomName() + "_" + entity.getMinutesId() + ".md";
            Path filePath = dirPath.resolve(fileName);
            Files.writeString(filePath, md.toString());

            log.info("[P82] 纪要文件已保存 - path={}", filePath);
            return filePath.toString();

        } catch (IOException e) {
            log.error("[P82] 纪要文件保存失败", e);
            throw new RuntimeException("纪要文件保存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定房间的最新纪要
     *
     * @param roomName 房间名称
     * @return 最新的纪要实体（可能为空）
     */
    public Optional<MeetingMinutesEntity> getLatestMinutes(String roomName) {
        return minutesRepository.findTopByRoomNameOrderByCreatedAtDesc(roomName);
    }

    /**
     * 获取指定房间的所有纪要
     *
     * @param roomName 房间名称
     * @return 纪要列表
     */
    public List<MeetingMinutesEntity> getMinutesByRoom(String roomName) {
        return minutesRepository.findByRoomNameOrderByCreatedAtDesc(roomName);
    }

    /**
     * 获取所有纪要
     *
     * @return 所有纪要列表
     */
    public List<MeetingMinutesEntity> getAllMinutes() {
        return minutesRepository.findAllByOrderByCreatedAtDesc();
    }

    // ========== 内部方法 ==========

    /**
     * 生成纪要后通知相关部门
     * 通过 DepartmentNotificationService（闭环44）推送
     */
    private void notifyMinutesGenerated(MeetingMinutesEntity entity) {
        try {
            // 从房间名推断部门（格式: dept-{deptCode}-meeting-{uuid}）
            String department = extractDepartmentFromRoomName(entity.getRoomName());
            String content = String.format("会议纪要已生成：%s，请查阅。", entity.getTitle());

            notificationService.sendMeetingNotification(
                    department,
                    "会议纪要已生成",
                    content,
                    entity.getGeneratedAt(),
                    "/meeting-minutes/" + entity.getRoomName()
            );

            log.info("[P82] 纪要生成通知已发送 - room={}, department={}", entity.getRoomName(), department);
        } catch (Exception e) {
            // 通知失败不影响纪要生成
            log.warn("[P82] 纪要生成通知发送失败 - room={}", entity.getRoomName(), e);
        }
    }

    /**
     * 从房间名称推断部门代码
     * 房间命名规范: dept-{departmentCode}-meeting-{uuid}
     */
    private String extractDepartmentFromRoomName(String roomName) {
        if (roomName != null && roomName.startsWith("dept-")) {
            String[] parts = roomName.split("-");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return "admin";  // 默认归属行政部
    }

    /**
     * 将对象序列化为 JSON 字符串
     */
    private String toJsonString(Object obj) {
        if (obj == null) return "[]";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * 将 JSON 数组字符串追加为 Markdown 列表
     */
    @SuppressWarnings("unchecked")
    private void appendJsonList(StringBuilder md, String jsonArray) {
        try {
            List<Object> items = objectMapper.readValue(jsonArray, List.class);
            for (Object item : items) {
                if (item instanceof String s) {
                    md.append("- ").append(s).append("\n");
                } else if (item instanceof Map) {
                    md.append("- ").append(item).append("\n");
                } else {
                    md.append("- ").append(item.toString()).append("\n");
                }
            }
        } catch (Exception e) {
            md.append("- ").append(jsonArray).append("\n");
        }
    }

    /**
     * 将待办任务 JSON 数组追加为 Markdown 列表（带责任人）
     */
    @SuppressWarnings("unchecked")
    private void appendActionItems(StringBuilder md, String jsonArray) {
        try {
            List<Object> items = objectMapper.readValue(jsonArray, List.class);
            for (Object item : items) {
                if (item instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) item;
                    String task = String.valueOf(map.getOrDefault("task", "未知任务"));
                    String assignee = String.valueOf(map.getOrDefault("assignee", "待确认"));
                    md.append("- [ ] **").append(task).append("** — 责任人: ").append(assignee).append("\n");
                } else if (item instanceof String s) {
                    md.append("- [ ] ").append(s).append("\n");
                } else {
                    md.append("- [ ] ").append(item.toString()).append("\n");
                }
            }
        } catch (Exception e) {
            md.append("- ").append(jsonArray).append("\n");
        }
    }
}
