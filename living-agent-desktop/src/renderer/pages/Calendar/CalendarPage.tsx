/**
 * 日历页面主组件
 * P84 / 闭环 67-D
 *
 * 功能：
 * - 周视图默认展示，支持月/周/日视图切换
 * - 从 /api/meeting-schedules 获取会议预约数据
 * - 点击空白时间槽打开预约表单（预填时间）
 * - 点击事件打开详情弹窗
 * - 支持拖拽调整时间和时长
 * - 中文本地化
 * - 事件颜色按会议类型区分
 * - 顶部显示当天日期和操作按钮
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import zhCnLocale from '@fullcalendar/core/locales/zh-cn';
import { startOfWeek, endOfWeek, format } from 'date-fns';
import { zhCN } from 'date-fns/locale';
import './CalendarPage.css';

/* ============ 类型定义 ============ */

/** 会议类型枚举 */
type MeetingType = 'DEPARTMENT' | 'CROSS_DEPT' | 'PROJECT' | 'TRAINING' | 'ALL_HANDS';

/** 会议状态枚举 */
type MeetingStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

/** 会议数据（后端返回） */
interface MeetingSchedule {
  id: string;
  title: string;
  meetingType: MeetingType;
  status: MeetingStatus;
  startTime: string;
  endTime: string;
  department?: string;
  organizer?: string;
  attendees?: string[];
  location?: string;
  description?: string;
}

/** FullCalendar 事件对象 */
interface CalendarEvent {
  id: string;
  title: string;
  start: string;
  end: string;
  backgroundColor: string;
  borderColor: string;
  extendedProps: {
    meetingType: MeetingType;
    status: MeetingStatus;
    department?: string;
    organizer?: string;
    attendees?: string[];
    location?: string;
    description?: string;
  };
}

/** 预约表单数据 */
interface MeetingFormData {
  title: string;
  meetingType: MeetingType;
  startTime: string;
  endTime: string;
  department?: string;
  location?: string;
  description?: string;
}

/** 组件 Props */
interface CalendarPageProps {
  backendUrl: string;
}

/* ============ 常量映射 ============ */

/** 会议类型颜色映射 */
const MEETING_TYPE_COLORS: Record<MeetingType, string> = {
  DEPARTMENT: '#3b82f6',   // 部门例会 - 蓝色
  CROSS_DEPT: '#8b5cf6',   // 跨部门 - 紫色
  PROJECT: '#06b6d4',      // 项目 - 青色
  TRAINING: '#f97316',     // 培训 - 橙色
  ALL_HANDS: '#ec4899',    // 全员 - 粉色
};

/** 会议状态颜色映射 */
const MEETING_STATUS_COLORS: Record<MeetingStatus, string> = {
  SCHEDULED: '',           // 使用类型颜色
  IN_PROGRESS: '',         // 使用类型颜色
  COMPLETED: '#22c55e',    // 已完成 - 绿色
  CANCELLED: '#9ca3af',    // 已取消 - 灰色
};

/** 会议类型中文标签 */
const MEETING_TYPE_LABELS: Record<MeetingType, string> = {
  DEPARTMENT: '部门例会',
  CROSS_DEPT: '跨部门',
  PROJECT: '项目',
  TRAINING: '培训',
  ALL_HANDS: '全员',
};

/** 会议状态中文标签 */
const MEETING_STATUS_LABELS: Record<MeetingStatus, string> = {
  SCHEDULED: '已排期',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
};

/* ============ 主组件 ============ */

const CalendarPage: React.FC<CalendarPageProps> = ({ backendUrl }) => {
  const calendarRef = useRef<FullCalendar>(null);

  /* --- 状态 --- */
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [currentDate, setCurrentDate] = useState<Date>(new Date());
  const [activeView, setActiveView] = useState<string>('timeGridWeek');

  // 预约表单弹窗
  const [showForm, setShowForm] = useState<boolean>(false);
  const [formData, setFormData] = useState<MeetingFormData>({
    title: '',
    meetingType: 'DEPARTMENT',
    startTime: '',
    endTime: '',
    department: '',
    location: '',
    description: '',
  });
  const [submitting, setSubmitting] = useState<boolean>(false);

  // 详情弹窗
  const [showDetail, setShowDetail] = useState<boolean>(false);
  const [selectedMeeting, setSelectedMeeting] = useState<MeetingSchedule | null>(null);

  /* ============ API 调用 ============ */

  /** 获取认证 Token */
  const getAuthHeaders = useCallback((): Record<string, string> => {
    const token = window.livingAgentAPI?.auth?.getToken?.() || '';
    return {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    };
  }, []);

  /** 获取会议列表 */
  const fetchMeetings = useCallback(async (start: Date, end: Date) => {
    setLoading(true);
    try {
      const startStr = start.toISOString();
      const endStr = end.toISOString();
      const url = `${backendUrl}/api/meeting-schedules?start=${encodeURIComponent(startStr)}&end=${encodeURIComponent(endStr)}`;

      const response = await fetch(url, {
        method: 'GET',
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(`获取会议列表失败: ${response.status}`);
      }

      const result = await response.json();
      const meetings: MeetingSchedule[] = result.data || result;

      const calendarEvents: CalendarEvent[] = meetings.map((m) => {
        const statusColor = MEETING_STATUS_COLORS[m.status];
        const typeColor = MEETING_TYPE_COLORS[m.meetingType];
        const bgColor = statusColor || typeColor;

        return {
          id: m.id,
          title: m.title,
          start: m.startTime,
          end: m.endTime,
          backgroundColor: bgColor,
          borderColor: bgColor,
          extendedProps: {
            meetingType: m.meetingType,
            status: m.status,
            department: m.department,
            organizer: m.organizer,
            attendees: m.attendees,
            location: m.location,
            description: m.description,
          },
        };
      });

      setEvents(calendarEvents);
    } catch (err) {
      console.error('[CalendarPage] 获取会议列表失败:', err);
    } finally {
      setLoading(false);
    }
  }, [backendUrl, getAuthHeaders]);

  /** 创建会议预约 */
  const createMeeting = useCallback(async (data: MeetingFormData) => {
    setSubmitting(true);
    try {
      const url = `${backendUrl}/api/meeting-schedules`;
      const response = await fetch(url, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify(data),
      });

      if (!response.ok) {
        throw new Error(`创建会议失败: ${response.status}`);
      }

      // 刷新日历数据
      const api = calendarRef.current?.getApi();
      if (api) {
        const start = api.view.activeStart;
        const end = api.view.activeEnd;
        await fetchMeetings(start, end);
      }
      setShowForm(false);
    } catch (err) {
      console.error('[CalendarPage] 创建会议失败:', err);
    } finally {
      setSubmitting(false);
    }
  }, [backendUrl, getAuthHeaders, fetchMeetings]);

  /** 拖拽/调整更新会议时间 */
  const updateMeetingTime = useCallback(async (
    meetingId: string,
    startTime: string,
    endTime: string
  ) => {
    try {
      const url = `${backendUrl}/api/meeting-schedules/${encodeURIComponent(meetingId)}`;
      const response = await fetch(url, {
        method: 'PATCH',
        headers: getAuthHeaders(),
        body: JSON.stringify({ startTime, endTime }),
      });

      if (!response.ok) {
        throw new Error(`更新会议时间失败: ${response.status}`);
      }
    } catch (err) {
      console.error('[CalendarPage] 更新会议时间失败:', err);
      // 回滚：重新获取数据
      const api = calendarRef.current?.getApi();
      if (api) {
        await fetchMeetings(api.view.activeStart, api.view.activeEnd);
      }
    }
  }, [backendUrl, getAuthHeaders, fetchMeetings]);

  /* ============ FullCalendar 回调 ============ */

  /** 日期范围变化时加载数据 */
  const handleDatesSet = useCallback((info: { start: Date; end: Date; view: { type: string } }) => {
    setCurrentDate(info.start);
    setActiveView(info.view.type);
    fetchMeetings(info.start, info.end);
  }, [fetchMeetings]);

  /** 点击空白时间槽 -> 打开预约表单 */
  const handleDateClick = useCallback((info: { date: Date; dateStr: string; allDay: boolean }) => {
    if (info.allDay) {
      // 点击全天区域，预填当天 09:00-10:00
      const start = new Date(info.date);
      start.setHours(9, 0, 0, 0);
      const end = new Date(info.date);
      end.setHours(10, 0, 0, 0);
      setFormData({
        title: '',
        meetingType: 'DEPARTMENT',
        startTime: start.toISOString(),
        endTime: end.toISOString(),
        department: '',
        location: '',
        description: '',
      });
    } else {
      // 点击时间槽，预填对应时间，默认 1 小时
      const start = new Date(info.dateStr);
      const end = new Date(start.getTime() + 60 * 60 * 1000);
      setFormData({
        title: '',
        meetingType: 'DEPARTMENT',
        startTime: start.toISOString(),
        endTime: end.toISOString(),
        department: '',
        location: '',
        description: '',
      });
    }
    setShowForm(true);
    setShowDetail(false);
  }, []);

  /** 点击事件 -> 打开详情弹窗 */
  const handleEventClick = useCallback((info: { event: { id: string; extendedProps: CalendarEvent['extendedProps']; title: string; start: Date | null; end: Date | null } }) => {
    const ev = info.event;
    setSelectedMeeting({
      id: ev.id,
      title: ev.title,
      meetingType: ev.extendedProps.meetingType,
      status: ev.extendedProps.status,
      startTime: ev.start?.toISOString() || '',
      endTime: ev.end?.toISOString() || '',
      department: ev.extendedProps.department,
      organizer: ev.extendedProps.organizer,
      attendees: ev.extendedProps.attendees,
      location: ev.extendedProps.location,
      description: ev.extendedProps.description,
    });
    setShowDetail(true);
    setShowForm(false);
  }, []);

  /** 拖拽事件结束 */
  const handleEventDrop = useCallback((info: { event: { id: string; start: Date | null; end: Date | null }; revert: () => void }) => {
    const { event, revert } = info;
    if (!event.start || !event.end) {
      revert();
      return;
    }
    updateMeetingTime(event.id, event.start.toISOString(), event.end.toISOString());
  }, [updateMeetingTime]);

  /** 拖拽调整事件时长 */
  const handleEventResize = useCallback((info: { event: { id: string; start: Date | null; end: Date | null }; revert: () => void }) => {
    const { event, revert } = info;
    if (!event.start || !event.end) {
      revert();
      return;
    }
    updateMeetingTime(event.id, event.start.toISOString(), event.end.toISOString());
  }, [updateMeetingTime]);

  /* ============ 顶部操作 ============ */

  /** 新建会议按钮 */
  const handleNewMeeting = useCallback(() => {
    const now = new Date();
    // 取整到下一个整点
    const start = new Date(now);
    start.setHours(now.getHours() + 1, 0, 0, 0);
    const end = new Date(start.getTime() + 60 * 60 * 1000);

    setFormData({
      title: '',
      meetingType: 'DEPARTMENT',
      startTime: start.toISOString(),
      endTime: end.toISOString(),
      department: '',
      location: '',
      description: '',
    });
    setShowForm(true);
    setShowDetail(false);
  }, []);

  /** 刷新按钮 */
  const handleRefresh = useCallback(() => {
    const api = calendarRef.current?.getApi();
    if (api) {
      fetchMeetings(api.view.activeStart, api.view.activeEnd);
    }
  }, [fetchMeetings]);

  /* ============ 表单操作 ============ */

  const handleFormChange = useCallback((field: keyof MeetingFormData, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  }, []);

  const handleFormSubmit = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    createMeeting(formData);
  }, [createMeeting, formData]);

  const handleFormClose = useCallback(() => {
    setShowForm(false);
  }, []);

  const handleDetailClose = useCallback(() => {
    setShowDetail(false);
    setSelectedMeeting(null);
  }, []);

  /* ============ 格式化工具 ============ */

  /** 格式化日期显示 */
  const formatCurrentDate = (date: Date): string => {
    const weekStart = startOfWeek(date, { weekStartsOn: 1, locale: zhCN });
    const weekEnd = endOfWeek(date, { weekStartsOn: 1, locale: zhCN });

    if (activeView === 'dayGridMonth') {
      return format(date, 'yyyy年 M月', { locale: zhCN });
    }
    if (activeView === 'timeGridDay') {
      return format(date, 'M月d日 EEEE', { locale: zhCN });
    }
    // 周视图
    return `${format(weekStart, 'M月d日', { locale: zhCN })} - ${format(weekEnd, 'M月d日', { locale: zhCN })}`;
  };

  /** 格式化时间用于表单 */
  const formatDateTimeLocal = (isoStr: string): string => {
    if (!isoStr) return '';
    const d = new Date(isoStr);
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  };

  /** 格式化时间用于详情展示 */
  const formatTimeDisplay = (isoStr: string): string => {
    if (!isoStr) return '--';
    return format(new Date(isoStr), 'yyyy-MM-dd HH:mm', { locale: zhCN });
  };

  /* ============ 渲染 ============ */

  return (
    <div className="calendar-page">
      {/* 顶部工具栏 */}
      <div className="calendar-toolbar">
        <div className="calendar-toolbar-left">
          <h2 className="calendar-current-date">{formatCurrentDate(currentDate)}</h2>
          {loading && <span className="calendar-loading-indicator">加载中...</span>}
        </div>
        <div className="calendar-toolbar-right">
          <button
            className="calendar-btn calendar-btn-primary"
            onClick={handleNewMeeting}
            disabled={loading}
          >
            新建会议
          </button>
          <button
            className="calendar-btn calendar-btn-default"
            onClick={handleRefresh}
            disabled={loading}
          >
            刷新
          </button>
        </div>
      </div>

      {/* FullCalendar 日历主体 */}
      <div className="calendar-body">
        <FullCalendar
          ref={calendarRef}
          plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
          initialView="timeGridWeek"
          locale={zhCnLocale}
          firstDay={1}
          slotMinTime="07:00:00"
          slotMaxTime="22:00:00"
          slotDuration="00:30:00"
          slotLabelInterval="01:00:00"
          headerToolbar={{
            left: 'prev,next today',
            center: '',
            right: 'dayGridMonth,timeGridWeek,timeGridDay',
          }}
          buttonText={{
            today: '今天',
            month: '月',
            week: '周',
            day: '日',
          }}
          events={events}
          editable={true}
          selectable={true}
          selectMirror={true}
          dayMaxEvents={true}
          weekends={true}
          nowIndicator={true}
          allDaySlot={true}
          height="100%"
          datesSet={handleDatesSet}
          dateClick={handleDateClick}
          eventClick={handleEventClick}
          eventDrop={handleEventDrop}
          eventResize={handleEventResize}
          eventResizableFromStart={true}
        />
      </div>

      {/* 新建会议表单弹窗 */}
      {showForm && (
        <div className="calendar-overlay" onClick={handleFormClose}>
          <div className="calendar-modal" onClick={(e) => e.stopPropagation()}>
            <div className="calendar-modal-header">
              <h3>新建会议预约</h3>
              <button className="calendar-modal-close" onClick={handleFormClose}>
                &times;
              </button>
            </div>
            <form className="calendar-form" onSubmit={handleFormSubmit}>
              <div className="calendar-form-group">
                <label>会议标题</label>
                <input
                  type="text"
                  value={formData.title}
                  onChange={(e) => handleFormChange('title', e.target.value)}
                  placeholder="请输入会议标题"
                  required
                />
              </div>
              <div className="calendar-form-row">
                <div className="calendar-form-group">
                  <label>会议类型</label>
                  <select
                    value={formData.meetingType}
                    onChange={(e) => handleFormChange('meetingType', e.target.value)}
                  >
                    {Object.entries(MEETING_TYPE_LABELS).map(([key, label]) => (
                      <option key={key} value={key}>{label}</option>
                    ))}
                  </select>
                </div>
                <div className="calendar-form-group">
                  <label>所属部门</label>
                  <input
                    type="text"
                    value={formData.department || ''}
                    onChange={(e) => handleFormChange('department', e.target.value)}
                    placeholder="选填"
                  />
                </div>
              </div>
              <div className="calendar-form-row">
                <div className="calendar-form-group">
                  <label>开始时间</label>
                  <input
                    type="datetime-local"
                    value={formatDateTimeLocal(formData.startTime)}
                    onChange={(e) => handleFormChange('startTime', new Date(e.target.value).toISOString())}
                    required
                  />
                </div>
                <div className="calendar-form-group">
                  <label>结束时间</label>
                  <input
                    type="datetime-local"
                    value={formatDateTimeLocal(formData.endTime)}
                    onChange={(e) => handleFormChange('endTime', new Date(e.target.value).toISOString())}
                    required
                  />
                </div>
              </div>
              <div className="calendar-form-group">
                <label>会议地点</label>
                <input
                  type="text"
                  value={formData.location || ''}
                  onChange={(e) => handleFormChange('location', e.target.value)}
                  placeholder="选填"
                />
              </div>
              <div className="calendar-form-group">
                <label>会议说明</label>
                <textarea
                  value={formData.description || ''}
                  onChange={(e) => handleFormChange('description', e.target.value)}
                  placeholder="选填"
                  rows={3}
                />
              </div>
              <div className="calendar-form-actions">
                <button
                  type="button"
                  className="calendar-btn calendar-btn-default"
                  onClick={handleFormClose}
                  disabled={submitting}
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="calendar-btn calendar-btn-primary"
                  disabled={submitting}
                >
                  {submitting ? '提交中...' : '确认预约'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* 会议详情弹窗 */}
      {showDetail && selectedMeeting && (
        <div className="calendar-overlay" onClick={handleDetailClose}>
          <div className="calendar-modal" onClick={(e) => e.stopPropagation()}>
            <div className="calendar-modal-header">
              <h3>会议详情</h3>
              <button className="calendar-modal-close" onClick={handleDetailClose}>
                &times;
              </button>
            </div>
            <div className="calendar-detail">
              <div className="calendar-detail-title">
                <span
                  className="calendar-detail-type-badge"
                  style={{ backgroundColor: MEETING_STATUS_COLORS[selectedMeeting.status] || MEETING_TYPE_COLORS[selectedMeeting.meetingType] }}
                >
                  {MEETING_TYPE_LABELS[selectedMeeting.meetingType]}
                </span>
                <span className="calendar-detail-status">
                  {MEETING_STATUS_LABELS[selectedMeeting.status]}
                </span>
              </div>
              <h4 className="calendar-detail-meeting-title">{selectedMeeting.title}</h4>

              <div className="calendar-detail-section">
                <div className="calendar-detail-row">
                  <span className="calendar-detail-label">开始时间</span>
                  <span className="calendar-detail-value">{formatTimeDisplay(selectedMeeting.startTime)}</span>
                </div>
                <div className="calendar-detail-row">
                  <span className="calendar-detail-label">结束时间</span>
                  <span className="calendar-detail-value">{formatTimeDisplay(selectedMeeting.endTime)}</span>
                </div>
                {selectedMeeting.organizer && (
                  <div className="calendar-detail-row">
                    <span className="calendar-detail-label">组织者</span>
                    <span className="calendar-detail-value">{selectedMeeting.organizer}</span>
                  </div>
                )}
                {selectedMeeting.department && (
                  <div className="calendar-detail-row">
                    <span className="calendar-detail-label">所属部门</span>
                    <span className="calendar-detail-value">{selectedMeeting.department}</span>
                  </div>
                )}
                {selectedMeeting.location && (
                  <div className="calendar-detail-row">
                    <span className="calendar-detail-label">会议地点</span>
                    <span className="calendar-detail-value">{selectedMeeting.location}</span>
                  </div>
                )}
                {selectedMeeting.attendees && selectedMeeting.attendees.length > 0 && (
                  <div className="calendar-detail-row">
                    <span className="calendar-detail-label">参会人员</span>
                    <span className="calendar-detail-value">{selectedMeeting.attendees.join(', ')}</span>
                  </div>
                )}
                {selectedMeeting.description && (
                  <div className="calendar-detail-row calendar-detail-row-block">
                    <span className="calendar-detail-label">会议说明</span>
                    <span className="calendar-detail-value">{selectedMeeting.description}</span>
                  </div>
                )}
              </div>
            </div>
            <div className="calendar-form-actions">
              <button
                type="button"
                className="calendar-btn calendar-btn-default"
                onClick={handleDetailClose}
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default CalendarPage;
