package com.livingagent.gateway.meeting;

import com.livingagent.core.database.entity.MeetingScheduleEntity;
import com.livingagent.core.notification.EventDrivenNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会议日历同步服务 - P84 会议预约与通知 / 闭环 44/67-D
 *
 * <p>策略模式 + 回退机制：优先使用主适配器同步，失败时按优先级回退。</p>
 * <p>无论日历同步成功与否，都通过 EventDrivenNotifier（闭环 44）发送通知。</p>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */
@Service
public class MeetingCalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(MeetingCalendarSyncService.class);

    @Value("${meeting.calendar.primary:local}")
    private String primaryAdapter;

    @Value("${meeting.calendar.fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Value("${meeting.calendar.fallback.order:local}")
    private String fallbackOrderStr;

    private final List<CalendarSyncAdapter> adapters;
    private final EventDrivenNotifier eventDrivenNotifier;

    /** scheduleId → externalEventId 映射 */
    private final Map<String, String> eventMapping = new ConcurrentHashMap<>();

    public MeetingCalendarSyncService(
            List<CalendarSyncAdapter> adapters,
            EventDrivenNotifier eventDrivenNotifier) {
        this.adapters = adapters != null ? adapters : List.of();
        this.eventDrivenNotifier = eventDrivenNotifier;
        log.info("[P84] MeetingCalendarSyncService 初始化 - {} 个适配器: {}",
                this.adapters.size(),
                this.adapters.stream().map(CalendarSyncAdapter::getName).toList());
    }

    /**
     * 创建日历事件并同步到参会者（闭环 67-D-1）
     */
    public void syncToCalendar(MeetingScheduleEntity schedule) {
        CalendarSyncAdapter adapter = getPrimaryAdapter();

        // 尝试主适配器
        if (adapter != null) {
            try {
                if (adapter.isAvailable()) {
                    String externalEventId = adapter.createEvent(schedule);
                    eventMapping.put(schedule.getScheduleId(), externalEventId);
                    log.info("[P84] Meeting {} synced to {} calendar: {}",
                            schedule.getScheduleId(), adapter.getName(), externalEventId);
                } else if (fallbackEnabled) {
                    tryFallback(schedule, adapter.getName());
                }
            } catch (Exception e) {
                log.error("[P84] Primary calendar sync failed ({}): {}", adapter.getName(), e.getMessage());
                if (fallbackEnabled) {
                    tryFallback(schedule, adapter.getName());
                }
            }
        } else {
            // 无主适配器，直接回退到 local
            CalendarSyncAdapter localAdapter = getAdapterByName("local");
            if (localAdapter != null && localAdapter.isAvailable()) {
                try {
                    String eventId = localAdapter.createEvent(schedule);
                    eventMapping.put(schedule.getScheduleId(), eventId);
                    log.info("[P84] Meeting {} synced to local calendar (fallback): {}", schedule.getScheduleId(), eventId);
                } catch (Exception e) {
                    log.error("[P84] Local calendar sync also failed: {}", e.getMessage());
                }
            }
        }

        // 发送通知（闭环 44）— 无论日历同步成功与否都发送
        sendNotification(schedule);
    }

    /**
     * 更新日历事件
     */
    public void updateCalendar(MeetingScheduleEntity schedule) {
        String externalEventId = eventMapping.get(schedule.getScheduleId());
        if (externalEventId == null) {
            log.warn("[P84] No external event found for meeting {}", schedule.getScheduleId());
            return;
        }

        CalendarSyncAdapter adapter = getPrimaryAdapter();
        if (adapter != null && adapter.isAvailable()) {
            try {
                adapter.updateEvent(externalEventId, schedule);
                log.info("[P84] Meeting {} calendar updated on {}", schedule.getId(), adapter.getName());
            } catch (Exception e) {
                log.error("[P84] Failed to update calendar event: {}", e.getMessage());
            }
        }
    }

    /**
     * 删除日历事件
     */
    public void deleteFromCalendar(String scheduleId) {
        String externalEventId = eventMapping.get(scheduleId);
        if (externalEventId == null) return;

        CalendarSyncAdapter adapter = getPrimaryAdapter();
        if (adapter != null && adapter.isAvailable()) {
            try {
                adapter.deleteEvent(externalEventId);
                eventMapping.remove(scheduleId);
                log.info("[P84] Meeting {} calendar event deleted from {}", scheduleId, adapter.getName());
            } catch (Exception e) {
                log.error("[P84] Failed to delete calendar event: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取指定预约的 iCal 文件内容（用于导出/下载）
     */
    public Optional<String> getICalContent(String scheduleId) {
        CalendarSyncAdapter localAdapter = getAdapterByName("local");
        if (localAdapter instanceof LocalICalSyncAdapter local) {
            // 读取 .ics 文件
            try {
                String filename = scheduleId + ".ics";
                String content = java.nio.file.Files.readString(
                        java.nio.file.Paths.get(local.getStoragePath(), filename));
                return Optional.of(content);
            } catch (Exception e) {
                log.warn("[P84] Failed to read iCal file for schedule {}: {}", scheduleId, e.getMessage());
            }
        }
        return Optional.empty();
    }

    // ========== 内部方法 ==========

    /**
     * 回退机制：按优先级尝试其他适配器
     */
    private void tryFallback(MeetingScheduleEntity schedule, String failedAdapter) {
        List<String> fallbackOrder = getFallbackOrder();
        fallbackOrder.remove(failedAdapter);

        for (String adapterName : fallbackOrder) {
            CalendarSyncAdapter adapter = getAdapterByName(adapterName);
            if (adapter != null && adapter.isAvailable()) {
                try {
                    String externalEventId = adapter.createEvent(schedule);
                    eventMapping.put(schedule.getScheduleId(), externalEventId);
                    log.info("[P84] Meeting {} synced to {} calendar (fallback): {}",
                            schedule.getScheduleId(), adapterName, externalEventId);
                    return;
                } catch (Exception e) {
                    log.warn("[P84] Fallback adapter {} failed: {}", adapterName, e.getMessage());
                }
            }
        }

        log.warn("[P84] All calendar adapters failed for meeting {}", schedule.getScheduleId());
    }

    /**
     * 发送会议邀请通知（复用闭环 44 EventDrivenNotifier）
     */
    private void sendNotification(MeetingScheduleEntity schedule) {
        try {
            eventDrivenNotifier.pushNotification(
                    EventDrivenNotifier.Notification.builder()
                            .department(schedule.getDepartment())
                            .title("会议邀请：" + schedule.getTitle())
                            .content(buildInviteMessage(schedule))
                            .type(EventDrivenNotifier.NotificationType.REMINDER)
                            .priority(EventDrivenNotifier.NotificationPriority.MEDIUM)
                            .data(Map.of(
                                    "scheduleId", schedule.getId(),
                                    "actionUrl", "/meeting/schedule/" + schedule.getId(),
                                    "attachmentUrl", "/api/meeting-schedules/" + schedule.getId() + "/calendar.ics"
                            ))
                            .build()
            );
            log.info("[P84] Meeting invite notification sent for schedule {}", schedule.getId());
        } catch (Exception e) {
            log.error("[P84] Failed to send meeting notification: {}", e.getMessage());
        }
    }

    private CalendarSyncAdapter getPrimaryAdapter() {
        return getAdapterByName(primaryAdapter);
    }

    private CalendarSyncAdapter getAdapterByName(String name) {
        return adapters.stream()
                .filter(a -> a.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private List<String> getFallbackOrder() {
        return new ArrayList<>(Arrays.asList(fallbackOrderStr.split(",")));
    }

    private String buildInviteMessage(MeetingScheduleEntity schedule) {
        return String.format(
                "会议主题：%s\n时间：%s\n时长：%d 分钟\n地点：%s\n\n请点击查看详情或下载日历文件。",
                schedule.getTitle(),
                schedule.getScheduledStart(),
                schedule.getDurationMinutes(),
                schedule.getLocation() != null ? schedule.getLocation() : "线上"
        );
    }
}
