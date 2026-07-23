package com.livingagent.gateway.meeting;

import com.livingagent.core.database.entity.MeetingScheduleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 本地 iCal 导出适配器（兜底方案） - P84 / 闭环 67-D
 *
 * <p>始终启用，作为所有外部 OA 同步失败时的回退方案。
 * 生成标准 iCalendar (.ics) 文件，用户可手动导入 Outlook/Google Calendar 等。</p>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */
public class LocalICalSyncAdapter implements CalendarSyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(LocalICalSyncAdapter.class);
    private static final DateTimeFormatter ICAL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final String storagePath;

    public LocalICalSyncAdapter(String storagePath) {
        this.storagePath = storagePath;
        // 确保目录存在
        try {
            Files.createDirectories(Paths.get(storagePath));
        } catch (IOException e) {
            log.warn("Failed to create iCal storage directory: {}", storagePath);
        }
    }

    @Override
    public String getName() {
        return "local";
    }

    /** 获取存储路径（供 MeetingCalendarSyncService 读取 .ics 文件） */
    public String getStoragePath() {
        return storagePath;
    }

    @Override
    public boolean isAvailable() {
        Path path = Paths.get(storagePath);
        return Files.isDirectory(path) && Files.isWritable(path);
    }

    @Override
    public String createEvent(MeetingScheduleEntity schedule) {
        String icalContent = generateICalendar(schedule);
        String filename = schedule.getId() + ".ics";
        Path filePath = Paths.get(storagePath, filename);

        try {
            Files.writeString(filePath, icalContent,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("[P84] iCal file created: {}", filePath);
            return filename;
        } catch (IOException e) {
            log.error("[P84] Failed to write iCal file: {}", filePath, e);
            throw new CalendarSyncException("Failed to write iCal file: " + filename, e);
        }
    }

    @Override
    public void updateEvent(String filename, MeetingScheduleEntity schedule) {
        // 直接覆盖文件
        createEvent(schedule);
    }

    @Override
    public void deleteEvent(String filename) {
        Path filePath = Paths.get(storagePath, filename);
        try {
            Files.deleteIfExists(filePath);
            log.info("[P84] iCal file deleted: {}", filePath);
        } catch (IOException e) {
            log.warn("[P84] Failed to delete iCal file: {}", filename, e);
        }
    }

    @Override
    public void inviteParticipants(String externalEventId, List<String> userIds) {
        // 本地 iCal 不支持单独邀请，通过 LAS 消息系统发送（闭环 44）
        log.debug("[P84] Local iCal does not support individual invitation; use MeetingCalendarSyncService notification");
    }

    @Override
    public void cancelInvitation(String externalEventId, List<String> userIds) {
        // 同上
        log.debug("[P84] Local iCal does not support individual cancellation; use MeetingCalendarSyncService notification");
    }

    /**
     * 生成 iCalendar 格式内容
     */
    private String generateICalendar(MeetingScheduleEntity schedule) {
        String dtStart = ICAL_FORMATTER.format(schedule.getScheduledStart());
        String dtEnd = ICAL_FORMATTER.format(
                schedule.getScheduledStart().plusSeconds((long) schedule.getDurationMinutes() * 60));
        String dtStamp = ICAL_FORMATTER.format(Instant.now());

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//LAS//Meeting//CN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("BEGIN:VEVENT\r\n");
        sb.append("UID:").append(schedule.getId()).append("@las.meeting\r\n");
        sb.append("DTSTAMP:").append(dtStamp).append("\r\n");
        sb.append("DTSTART:").append(dtStart).append("\r\n");
        sb.append("DTEND:").append(dtEnd).append("\r\n");
        sb.append("SUMMARY:").append(escapeICalText(schedule.getTitle())).append("\r\n");
        if (schedule.getDescription() != null && !schedule.getDescription().isEmpty()) {
            sb.append("DESCRIPTION:").append(escapeICalText(schedule.getDescription())).append("\r\n");
        }
        if (schedule.getLocation() != null && !schedule.getLocation().isEmpty()) {
            sb.append("LOCATION:").append(escapeICalText(schedule.getLocation())).append("\r\n");
        }
        sb.append("ORGANIZER;CN=").append(escapeICalText(schedule.getCreatedBy()))
                .append(":mailto:").append(schedule.getCreatedBy()).append("@las.local\r\n");
        // 15分钟前提醒
        sb.append("BEGIN:VALARM\r\n");
        sb.append("TRIGGER:-PT15M\r\n");
        sb.append("ACTION:DISPLAY\r\n");
        sb.append("DESCRIPTION:会议即将开始\r\n");
        sb.append("END:VALARM\r\n");
        sb.append("END:VEVENT\r\n");
        sb.append("END:VCALENDAR\r\n");

        return sb.toString();
    }

    /**
     * iCalendar 文本转义（RFC 5545）
     */
    private String escapeICalText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n");
    }

    /**
     * 日历同步异常
     */
    public static class CalendarSyncException extends RuntimeException {
        public CalendarSyncException(String message) { super(message); }
        public CalendarSyncException(String message, Throwable cause) { super(message, cause); }
    }
}
