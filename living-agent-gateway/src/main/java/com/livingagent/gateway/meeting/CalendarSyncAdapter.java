package com.livingagent.gateway.meeting;

import com.livingagent.core.database.entity.MeetingScheduleEntity;

import java.util.List;

/**
 * 日历同步适配器接口（策略模式） - P84 会议预约与通知 / 闭环 44/67-D
 *
 * <p>不同 OA 平台实现此接口，支持飞书/企业微信/钉钉/Outlook/本地iCal。</p>
 * <p>配置通过 application.yml 的 meeting.calendar.adapters 控制。</p>
 *
 * @author P84 会议预约与通知
 * @since 1.0.0
 */
public interface CalendarSyncAdapter {

    /**
     * 获取适配器名称（如 feishu/wechat/dingtalk/outlook/local）
     */
    String getName();

    /**
     * 检查适配器是否可用（配置完整 + API 可达）
     */
    boolean isAvailable();

    /**
     * 创建日历事件
     *
     * @param schedule 会议预约数据
     * @return 外部日历事件 ID（用于后续更新/删除）
     */
    String createEvent(MeetingScheduleEntity schedule);

    /**
     * 更新日历事件
     *
     * @param externalEventId 外部日历事件 ID
     * @param schedule        更新后的预约数据
     */
    void updateEvent(String externalEventId, MeetingScheduleEntity schedule);

    /**
     * 删除日历事件
     *
     * @param externalEventId 外部日历事件 ID
     */
    void deleteEvent(String externalEventId);

    /**
     * 邀请参会者（发送日历邀请）
     *
     * @param externalEventId 外部日历事件 ID
     * @param userIds         参会者 LAS userId 列表
     */
    void inviteParticipants(String externalEventId, List<String> userIds);

    /**
     * 取消邀请参会者
     *
     * @param externalEventId 外部日历事件 ID
     * @param userIds         要取消的参会者列表
     */
    void cancelInvitation(String externalEventId, List<String> userIds);
}
