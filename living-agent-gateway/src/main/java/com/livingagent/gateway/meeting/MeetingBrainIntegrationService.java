package com.livingagent.gateway.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livingagent.core.database.repository.MeetingMinutesRepository;
import com.livingagent.gateway.service.DepartmentChatService;
import com.livingagent.gateway.service.DepartmentChatService.DepartmentChatResult;
import com.livingagent.gateway.service.DepartmentNotificationService;
import com.livingagent.gateway.websocket.DepartmentWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * 部门大脑感知会议服务 - 闭环 46 对话管理 / P85 会议深度集成
 *
 * <p>会议状态变化时通知部门大脑，使部门大脑具备对会议的感知能力，
 * 从而在对话中可以关联会议上下文、提醒参会、跟进纪要等。</p>
 *
 * <h3>集成闭环</h3>
 * <ul>
 *   <li>闭环 46（对话管理）：会议状态变化注入对话上下文</li>
 *   <li>闭环 44（通知推送）：通过 DepartmentNotificationService 推送通知</li>
 *   <li>闭环 68（录制纪要）：纪要生成后通知部门大脑</li>
 * </ul>
 *
 * <h3>通知方式</h3>
 * <ol>
 *   <li>WebSocket 系统消息：通过 DepartmentWebSocketHandler 向部门在线用户广播</li>
 *   <li>通知队列：通过 DepartmentNotificationService 持久化通知</li>
 *   <li>部门大脑注入：通过 DepartmentChatService 向部门大脑发送系统消息</li>
 * </ol>
 *
 * @author P85 会议深度集成
 * @since 1.0.0
 */
@Service
public class MeetingBrainIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(MeetingBrainIntegrationService.class);

    /** 系统消息前缀标识，用于部门大脑识别会议事件 */
    private static final String SYSTEM_PREFIX = "[系统-会议事件] ";

    private final DepartmentChatService departmentChatService;
    private final DepartmentNotificationService notificationService;
    private final DepartmentWebSocketHandler webSocketHandler;
    private final MeetingMinutesRepository minutesRepository;
    private final ObjectMapper objectMapper;

    public MeetingBrainIntegrationService(
            DepartmentChatService departmentChatService,
            DepartmentNotificationService notificationService,
            DepartmentWebSocketHandler webSocketHandler,
            MeetingMinutesRepository minutesRepository,
            ObjectMapper objectMapper) {
        this.departmentChatService = departmentChatService;
        this.notificationService = notificationService;
        this.webSocketHandler = webSocketHandler;
        this.minutesRepository = minutesRepository;
        this.objectMapper = objectMapper;
        log.info("[P85] MeetingBrainIntegrationService 初始化");
    }

    // ========== 公开接口 ==========

    /**
     * 会议开始时通知部门大脑
     *
     * <p>当 LiveKit 房间中有第一个参与者加入时触发，
     * 向部门大脑发送系统消息，如"技术部会议'架构评审'已开始,参会3人"。</p>
     *
     * @param department       部门代码（如 tech、hr）
     * @param roomName         LiveKit 房间名称
     * @param title            会议标题
     * @param participantCount 当前参会人数
     */
    public void notifyMeetingStarted(String department, String roomName, String title, int participantCount) {
        String content = String.format("%s会议'%s'已开始,参会%d人", department, title, participantCount);
        String systemMessage = SYSTEM_PREFIX + content;

        log.info("[P85] 会议开始通知 - dept={}, room={}, title={}, participants={}",
                department, roomName, title, participantCount);

        // 1. 向部门在线用户广播 WebSocket 系统消息
        broadcastMeetingEvent(department, "MEETING_STARTED", Map.of(
                "roomName", roomName,
                "title", title,
                "participantCount", participantCount,
                "timestamp", Instant.now().toString()
        ));

        // 2. 通过通知队列持久化（闭环 44）
        notificationService.sendMeetingNotification(
                department,
                "会议已开始: " + title,
                content,
                Instant.now(),
                "/meetings/" + roomName
        );

        // 3. 向部门大脑注入系统消息（闭环 46）
        injectSystemMessageToBrain(department, systemMessage);

        log.info("[P85] 会议开始通知完成 - dept={}, room={}", department, roomName);
    }

    /**
     * 会议结束时通知部门大脑
     *
     * <p>当会议房间关闭或最后一个参与者离开时触发，
     * 告知部门大脑会议已结束及基本统计信息。</p>
     *
     * @param department      部门代码
     * @param roomName        LiveKit 房间名称
     * @param title           会议标题
     * @param durationMinutes 会议持续时长（分钟）
     * @param hasMinutes      是否已生成纪要
     */
    public void notifyMeetingEnded(String department, String roomName, String title,
                                   int durationMinutes, boolean hasMinutes) {
        String minutesStatus = hasMinutes ? "已生成" : "待生成";
        String content = String.format("%s会议'%s'已结束,时长%d分钟,纪要%s",
                department, title, durationMinutes, minutesStatus);
        String systemMessage = SYSTEM_PREFIX + content;

        log.info("[P85] 会议结束通知 - dept={}, room={}, title={}, duration={}min, hasMinutes={}",
                department, roomName, title, durationMinutes, hasMinutes);

        // 1. 向部门在线用户广播 WebSocket 系统消息
        broadcastMeetingEvent(department, "MEETING_ENDED", Map.of(
                "roomName", roomName,
                "title", title,
                "durationMinutes", durationMinutes,
                "hasMinutes", hasMinutes,
                "timestamp", Instant.now().toString()
        ));

        // 2. 通过通知队列持久化（闭环 44）
        notificationService.sendMeetingNotification(
                department,
                "会议已结束: " + title,
                content,
                Instant.now(),
                "/meetings/" + roomName
        );

        // 3. 向部门大脑注入系统消息（闭环 46）
        injectSystemMessageToBrain(department, systemMessage);

        log.info("[P85] 会议结束通知完成 - dept={}, room={}", department, roomName);
    }

    /**
     * 纪要生成后通知部门大脑
     *
     * <p>当 MeetingMinutesService 生成纪要后调用，
     * 使部门大脑可以主动关联纪要内容到后续对话中。</p>
     *
     * @param department    部门代码
     * @param roomName      LiveKit 房间名称
     * @param minutesSummary 纪要摘要内容
     */
    public void notifyMinutesGenerated(String department, String roomName, String minutesSummary) {
        String content = String.format("%s会议纪要已生成 - %s", department, roomName);
        String systemMessage = SYSTEM_PREFIX + content + "。摘要: " +
                (minutesSummary != null && minutesSummary.length() > 200
                        ? minutesSummary.substring(0, 200) + "..."
                        : minutesSummary);

        log.info("[P85] 纪要生成通知 - dept={}, room={}, summaryLength={}",
                department, roomName, minutesSummary != null ? minutesSummary.length() : 0);

        // 1. 向部门在线用户广播 WebSocket 系统消息
        broadcastMeetingEvent(department, "MINUTES_GENERATED", Map.of(
                "roomName", roomName,
                "minutesSummary", minutesSummary != null ? minutesSummary : "",
                "timestamp", Instant.now().toString()
        ));

        // 2. 通过通知队列持久化（闭环 44）
        notificationService.sendMeetingNotification(
                department,
                "会议纪要已生成",
                content + "，请查阅。",
                Instant.now(),
                "/meeting-minutes/" + roomName
        );

        // 3. 向部门大脑注入系统消息（闭环 46），纪要内容有助于大脑关联上下文
        injectSystemMessageToBrain(department, systemMessage);

        log.info("[P85] 纪要生成通知完成 - dept={}, room={}", department, roomName);
    }

    // ========== 内部方法 ==========

    /**
     * 向部门大脑注入系统消息
     *
     * <p>通过 DepartmentChatService 向部门大脑发送一条系统消息，
     * 使大脑将会议事件纳入对话上下文。此消息不会触发完整的决策流程，
     * 仅作为上下文注入。</p>
     *
     * @param department   部门代码
     * @param systemMessage 系统消息内容
     */
    private void injectSystemMessageToBrain(String department, String systemMessage) {
        try {
            // 使用 DepartmentChatService.processDepartmentChat 发送系统消息
            // 注意：此方法需要 authorization 参数，系统消息使用内部系统身份
            // 实际使用时需要系统内部 token 或改造为无认证的系统消息接口
            DepartmentChatResult result = departmentChatService.processDepartmentChat(
                    department, systemMessage, null);

            if (result.success()) {
                log.debug("[P85] 系统消息注入部门大脑成功 - dept={}", department);
            } else {
                log.warn("[P85] 系统消息注入部门大脑失败 - dept={}, status={}, reason={}",
                        department, result.status(), result.reason());
            }
        } catch (Exception e) {
            // 注入失败不影响其他通知渠道
            log.warn("[P85] 系统消息注入部门大脑异常 - dept={}, error={}",
                    department, e.getMessage());
        }
    }

    /**
     * 通过 WebSocket 向部门在线用户广播会议事件
     *
     * <p>使用 DepartmentWebSocketHandler.broadcastRawJson 向部门所有
     * 在线 WebSocket 连接发送会议事件消息。</p>
     *
     * @param department 部门代码
     * @param eventType  事件类型（MEETING_STARTED/MEETING_ENDED/MINUTES_GENERATED）
     * @param data       事件数据
     */
    private void broadcastMeetingEvent(String department, String eventType, Map<String, Object> data) {
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", "meeting_event");
            message.put("eventType", eventType);
            message.put("department", department);
            message.put("timestamp", Instant.now().toString());
            message.putAll(data);

            String rawJson = objectMapper.writeValueAsString(message);
            webSocketHandler.broadcastRawJson(department, rawJson);

            log.debug("[P85] WebSocket 会议事件广播成功 - dept={}, eventType={}", department, eventType);
        } catch (Exception e) {
            log.warn("[P85] WebSocket 会议事件广播失败 - dept={}, eventType={}, error={}",
                    department, eventType, e.getMessage());
        }
    }

    /**
     * 从房间名称推断部门代码
     *
     * <p>房间命名规范: dept-{departmentCode}-meeting-{uuid}</p>
     *
     * @param roomName 房间名称
     * @return 部门代码，默认 admin
     */
    private String extractDepartmentFromRoomName(String roomName) {
        if (roomName != null && roomName.startsWith("dept-")) {
            String[] parts = roomName.split("-");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return "admin";
    }
}
