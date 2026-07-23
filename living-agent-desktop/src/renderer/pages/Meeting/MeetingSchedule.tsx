/**
 * 会议预约页面 — 闭环 67-D 预约管理 / P84
 *
 * 功能：
 * - 预约表单：主题、描述、部门、开始时间、结束时间、最大参会人数、是否录制、提醒时间
 * - 日期/时间选择器
 * - 参会人选择（从员工列表选择）
 * - 提交 → 调用 POST /api/meeting-schedules
 * - 预约列表展示（SCHEDULED/ACTIVE/COMPLETED/CANCELLED）
 * - 取消预约
 *
 * 权限对齐 P14（对齐 LIVEKIT_INTEGRATION_PLAN.md §7.1）：
 * - INTERNAL_ENTERPRISE → 可创建跨部门预约，可查看/取消所有
 * - INTERNAL_ACTIVE → 可创建本部门预约，可查看/取消自己创建的
 * - INTERNAL_PROBATION → 不可创建，可查看本部门
 * - 其他 → 不可创建，不可查看
 *
 * 注意：桌面端路由不使用 react-router，通过 App.tsx 的 view state 切换页面。
 * 本页面作为 MeetingPage 的子页面或独立 tab 使用。
 */
import { useEffect, useState, useCallback } from 'react';
import type { DesktopUser } from '@shared/api-types';

// ─── Props ────────────────────────────────────────────────

interface MeetingScheduleProps {
  /** 后端 URL */
  backendUrl: string;
  /** 是否已登录 */
  hasToken: boolean;
  /** 当前登录用户 */
  currentUser: DesktopUser | null;
  /** 跳转到会议室页面的回调 */
  onNavigateToRoom?: (roomName: string) => void;
}

// ─── 类型定义 ──────────────────────────────────────────────

/** 预约状态 */
export type ScheduleStatus = 'SCHEDULED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

/** 预约信息 */
export interface MeetingSchedule {
  scheduleId: string;
  title: string;
  description?: string;
  creatorId: string;
  department: string;
  roomName?: string;
  maxParticipants: number;
  scheduledStart: string;
  scheduledEnd: string;
  durationMinutes: number;
  actualStart?: string;
  actualEnd?: string;
  status: ScheduleStatus;
  reminderSent: boolean;
  reminderMinutesBefore: number;
  enableRecording: boolean;
  metadataJson?: string;
  createdAt: string;
  updatedAt: string;
}

/** 创建预约请求体 */
export interface CreateScheduleRequest {
  title: string;
  description?: string;
  department?: string;
  scheduledStart: string;
  scheduledEnd: string;
  maxParticipants: number;
  reminderMinutesBefore: number;
  enableRecording: boolean;
  metadataJson?: string;
}

// ─── 权限判定 ──────────────────────────────────────────────

function canCreateSchedule(user: DesktopUser | null): boolean {
  if (!user) return false;
  return user.identity === 'INTERNAL_ENTERPRISE' || user.identity === 'INTERNAL_ACTIVE';
}

function canViewSchedule(user: DesktopUser | null): boolean {
  if (!user) return false;
  return user.identity !== 'INTERNAL_DEPARTED' && user.identity !== 'EXTERNAL_VISITOR';
}

// ─── 部门名称映射 ────────────────────────────────────────────

const DEPT_NAMES: Record<string, string> = {
  tech: '技术部',
  hr: '人力资源',
  finance: '财务部',
  sales: '销售部',
  cs: '客服部',
  admin: '行政部',
  legal: '法务部',
  ops: '运营部',
  core: '核心层',
  cross_dept: '跨部门',
};

function getDeptName(code: string): string {
  return DEPT_NAMES[code] || code;
}

// ─── API 工具 ─────────────────────────────────────────────

async function getAuthHeaders(): Promise<Record<string, string>> {
  const token = await window.livingAgentAPI.auth.getToken();
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`API ${res.status}: ${text}`);
  }
  const json: any = await res.json();
  if (json && typeof json === 'object' && 'data' in json) {
    return json.data as T;
  }
  return json as T;
}

// ─── 时间工具 ─────────────────────────────────────────────

/** 将本地 datetime-local input 值转为 ISO 8601 */
function localDatetimeToIso(value: string): string {
  if (!value) return '';
  return new Date(value).toISOString();
}

/** 将 ISO 8601 时间转为本地 datetime-local input 值 */
function isoToLocalDatetime(iso: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  // 格式: YYYY-MM-DDTHH:MM
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 格式化时间显示 */
function formatDateTime(iso: string): string {
  if (!iso) return '';
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

// ─── 组件 ─────────────────────────────────────────────────

export function MeetingSchedule({
  backendUrl,
  hasToken,
  currentUser,
  onNavigateToRoom,
}: MeetingScheduleProps) {
  // ── 状态 ──
  const [schedules, setSchedules] = useState<MeetingSchedule[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<ScheduleStatus | 'all'>('all');

  // 表单状态
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<CreateScheduleRequest>({
    title: '',
    description: '',
    department: currentUser?.department || '',
    scheduledStart: '',
    scheduledEnd: '',
    maxParticipants: 50,
    reminderMinutesBefore: 15,
    enableRecording: false,
    metadataJson: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [conflict, setConflict] = useState<string | null>(null);

  const canCreate = canCreateSchedule(currentUser);
  const canView = canViewSchedule(currentUser);

  // ── 加载预约列表 ──
  const loadSchedules = useCallback(async () => {
    if (!hasToken || !backendUrl) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const headers = await getAuthHeaders();
      const params = new URLSearchParams();
      if (tab !== 'all') params.set('status', tab);
      // 非 INTERNAL_ENTERPRISE 只查本部门
      if (currentUser?.identity !== 'INTERNAL_ENTERPRISE' && currentUser?.department) {
        params.set('department', currentUser.department);
      }
      const qs = params.toString();
      const res = await fetch(`${backendUrl}/api/meeting-schedules${qs ? '?' + qs : ''}`, { headers });
      const list = await handleResponse<MeetingSchedule[]>(res);
      setSchedules(Array.isArray(list) ? list : []);
    } catch (e: any) {
      setError(e.message || '加载预约列表失败');
    } finally {
      setLoading(false);
    }
  }, [backendUrl, hasToken, tab, currentUser]);

  useEffect(() => {
    void loadSchedules();
  }, [loadSchedules]);

  // ── 创建预约 ──
  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!formData.title.trim() || !formData.scheduledStart || !formData.scheduledEnd) return;
    setSubmitting(true);
    setConflict(null);
    try {
      const headers = await getAuthHeaders();
      const body = {
        ...formData,
        department: currentUser?.identity === 'INTERNAL_ENTERPRISE'
          ? formData.department
          : currentUser?.department || formData.department,
        scheduledStart: localDatetimeToIso(formData.scheduledStart),
        scheduledEnd: localDatetimeToIso(formData.scheduledEnd),
      };
      const res = await fetch(`${backendUrl}/api/meeting-schedules`, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
      });
      if (!res.ok) {
        const errData = await res.json().catch(() => null);
        if (errData?.errorDescription) {
          throw new Error(errData.errorDescription);
        }
        throw new Error(`创建失败: ${res.status}`);
      }
      // 重置表单
      setFormData({
        title: '',
        description: '',
        department: currentUser?.department || '',
        scheduledStart: '',
        scheduledEnd: '',
        maxParticipants: 50,
        reminderMinutesBefore: 15,
        enableRecording: false,
        metadataJson: '',
      });
      setShowForm(false);
      await loadSchedules();
    } catch (e: any) {
      if (e.message?.includes('冲突')) {
        setConflict(e.message);
      } else {
        setError(e.message || '创建预约失败');
      }
    } finally {
      setSubmitting(false);
    }
  }

  // ── 取消预约 ──
  async function handleCancel(scheduleId: string) {
    try {
      const headers = await getAuthHeaders();
      await fetch(`${backendUrl}/api/meeting-schedules/${scheduleId}?reason=用户取消`, {
        method: 'DELETE',
        headers,
      });
      await loadSchedules();
    } catch (e: any) {
      setError(e.message || '取消预约失败');
    }
  }

  // ── 未登录提示 ──
  if (!hasToken) {
    return (
      <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>📅</div>
        <h2>会议预约需要登录</h2>
        <p style={{ fontSize: 13, color: '#666' }}>登录后可预约和管理会议</p>
      </div>
    );
  }

  // ── 无权限提示 ──
  if (!canView) {
    return (
      <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>🔒</div>
        <h2>无预约权限</h2>
        <p style={{ fontSize: 13, color: '#666' }}>当前身份无权访问会议预约功能</p>
      </div>
    );
  }

  // ── 状态分组 ──
  const filteredSchedules = tab === 'all'
    ? schedules
    : schedules.filter(s => s.status === tab);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 24 }}>
      {/* ── 顶部标题和操作 ── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 20 }}>📅 会议预约</h1>
          <span style={{ fontSize: 12, color: '#888' }}>
            {currentUser?.department ? `${getDeptName(currentUser.department)}预约管理` : '预约管理'}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {canCreate && (
            <button
              className="btn btn-primary"
              onClick={() => setShowForm(true)}
              style={{ fontSize: 13, padding: '6px 16px' }}
            >
              ➕ 新建预约
            </button>
          )}
          <button
            className="btn"
            onClick={() => void loadSchedules()}
            style={{ fontSize: 13, padding: '6px 16px' }}
          >
            🔄 刷新
          </button>
        </div>
      </div>

      {/* ── 错误提示 ── */}
      {error && (
        <div style={{
          padding: '8px 12px', borderRadius: 6, marginBottom: 12,
          background: 'rgba(239,68,68,0.1)', color: '#e53e3e', fontSize: 13,
        }}>
          {error}
          <button
            onClick={() => setError('')}
            style={{ marginLeft: 8, background: 'none', border: 'none', color: '#e53e3e', cursor: 'pointer' }}
          >
            ✕
          </button>
        </div>
      )}

      {/* ── 统计条 ── */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 12, fontSize: 12 }}>
        <span style={{ color: '#faad14' }}>● 已预约 {schedules.filter(s => s.status === 'SCHEDULED').length}</span>
        <span style={{ color: '#52c41a' }}>● 进行中 {schedules.filter(s => s.status === 'ACTIVE').length}</span>
        <span style={{ color: '#8c8c8c' }}>● 已完成 {schedules.filter(s => s.status === 'COMPLETED').length}</span>
        <span style={{ color: '#ff4d4f' }}>● 已取消 {schedules.filter(s => s.status === 'CANCELLED').length}</span>
      </div>

      {/* ── Tab 切换 ── */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 12, borderBottom: '1px solid #333' }}>
        {(['all', 'SCHEDULED', 'ACTIVE', 'COMPLETED', 'CANCELLED'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            style={{
              padding: '6px 16px', border: 'none', cursor: 'pointer',
              background: tab === t ? '#6366f1' : 'transparent',
              color: tab === t ? '#fff' : '#888',
              fontWeight: 600, fontSize: 12, borderRadius: '6px 6px 0 0',
              transition: 'all 0.2s',
            }}
          >
            {t === 'all' ? '全部' : t === 'SCHEDULED' ? '已预约' : t === 'ACTIVE' ? '进行中' : t === 'COMPLETED' ? '已完成' : '已取消'}
          </button>
        ))}
      </div>

      {/* ── 预约列表 ── */}
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>加载预约列表...</div>
        ) : filteredSchedules.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>
            <div style={{ fontSize: 36, marginBottom: 8 }}>📅</div>
            <p>暂无预约</p>
            {canCreate && <p style={{ fontSize: 11, color: '#666' }}>点击"新建预约"创建一个会议预约</p>}
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 8 }}>
            {filteredSchedules.map(schedule => (
              <ScheduleCard
                key={schedule.scheduleId}
                schedule={schedule}
                onCancel={handleCancel}
                onJoin={onNavigateToRoom}
                canCancel={
                  currentUser?.identity === 'INTERNAL_ENTERPRISE' ||
                  schedule.creatorId === currentUser?.id
                }
              />
            ))}
          </div>
        )}
      </div>

      {/* ── 预约表单对话框 ── */}
      {showForm && (
        <div
          className="login-dialog-overlay"
          onClick={() => setShowForm(false)}
        >
          <div
            className="login-dialog"
            onClick={e => e.stopPropagation()}
            style={{ width: 520, maxHeight: '85vh', overflowY: 'auto' }}
          >
            <h2 style={{ marginTop: 0 }}>➕ 新建会议预约</h2>

            <form onSubmit={e => void handleSubmit(e)}>
              {/* 会议主题 */}
              <div style={{ marginBottom: 12 }}>
                <label style={labelStyle}>会议主题 *</label>
                <input
                  value={formData.title}
                  onChange={e => setFormData(prev => ({ ...prev, title: e.target.value }))}
                  placeholder="例如：技术部周会"
                  required
                  autoFocus
                  style={inputStyle}
                />
              </div>

              {/* 会议描述 */}
              <div style={{ marginBottom: 12 }}>
                <label style={labelStyle}>会议描述</label>
                <textarea
                  value={formData.description}
                  onChange={e => setFormData(prev => ({ ...prev, description: e.target.value }))}
                  placeholder="可选：会议议程、注意事项等"
                  rows={3}
                  style={{ ...inputStyle, resize: 'vertical', minHeight: 60 }}
                />
              </div>

              {/* 部门（仅董事长可选） */}
              {currentUser?.identity === 'INTERNAL_ENTERPRISE' && (
                <div style={{ marginBottom: 12 }}>
                  <label style={labelStyle}>所属部门</label>
                  <select
                    value={formData.department}
                    onChange={e => setFormData(prev => ({ ...prev, department: e.target.value }))}
                    style={inputStyle}
                  >
                    {Object.entries(DEPT_NAMES).map(([code, name]) => (
                      <option key={code} value={code}>{name}</option>
                    ))}
                  </select>
                </div>
              )}

              {/* 开始时间 */}
              <div style={{ marginBottom: 12 }}>
                <label style={labelStyle}>开始时间 *</label>
                <input
                  type="datetime-local"
                  value={formData.scheduledStart}
                  onChange={e => setFormData(prev => ({ ...prev, scheduledStart: e.target.value }))}
                  required
                  style={inputStyle}
                />
              </div>

              {/* 结束时间 */}
              <div style={{ marginBottom: 12 }}>
                <label style={labelStyle}>结束时间 *</label>
                <input
                  type="datetime-local"
                  value={formData.scheduledEnd}
                  onChange={e => setFormData(prev => ({ ...prev, scheduledEnd: e.target.value }))}
                  required
                  style={inputStyle}
                />
              </div>

              {/* 最大参会人数 */}
              <div style={{ marginBottom: 12 }}>
                <label style={labelStyle}>最大参会人数</label>
                <select
                  value={formData.maxParticipants}
                  onChange={e => setFormData(prev => ({ ...prev, maxParticipants: Number(e.target.value) }))}
                  style={inputStyle}
                >
                  <option value={10}>10 人</option>
                  <option value={20}>20 人</option>
                  <option value={30}>30 人</option>
                  <option value={50}>50 人</option>
                  <option value={100}>100 人</option>
                </select>
              </div>

              {/* 提醒时间 */}
              <div style={{ marginBottom: 12 }}>
                <label style={labelStyle}>提前提醒</label>
                <select
                  value={formData.reminderMinutesBefore}
                  onChange={e => setFormData(prev => ({ ...prev, reminderMinutesBefore: Number(e.target.value) }))}
                  style={inputStyle}
                >
                  <option value={5}>5 分钟</option>
                  <option value={10}>10 分钟</option>
                  <option value={15}>15 分钟</option>
                  <option value={30}>30 分钟</option>
                  <option value={60}>1 小时</option>
                </select>
              </div>

              {/* 是否录制 */}
              <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
                <input
                  type="checkbox"
                  checked={formData.enableRecording}
                  onChange={e => setFormData(prev => ({ ...prev, enableRecording: e.target.checked }))}
                  id="enableRecording"
                />
                <label htmlFor="enableRecording" style={{ fontSize: 13, color: '#ddd', cursor: 'pointer' }}>
                  启用会议录制
                </label>
              </div>

              {/* 参会人员（JSON 格式，后续可升级为人员选择器） */}
              <div style={{ marginBottom: 12 }}>
                <label style={labelStyle}>参会人员ID（逗号分隔）</label>
                <input
                  value={formData.metadataJson ? JSON.parse(formData.metadataJson).participantIds?.join(',') || '' : ''}
                  onChange={e => {
                    const ids = e.target.value.split(',').map(s => s.trim()).filter(Boolean);
                    setFormData(prev => ({
                      ...prev,
                      metadataJson: ids.length > 0 ? JSON.stringify({ participantIds: ids }) : '',
                    }));
                  }}
                  placeholder="例如: emp-001, emp-002, emp-003"
                  style={inputStyle}
                />
              </div>

              {/* 部门提示 */}
              <div style={{
                padding: '6px 10px', borderRadius: 4, marginBottom: 12,
                background: 'rgba(99,102,241,0.08)', fontSize: 11, color: '#888',
              }}>
                {currentUser?.identity === 'INTERNAL_ENTERPRISE'
                  ? '董事长身份：可创建跨部门预约'
                  : `预约将归属「${getDeptName(currentUser?.department || '')}」部门`}
              </div>

              {/* 冲突提示 */}
              {conflict && (
                <div style={{
                  padding: '8px 12px', borderRadius: 6, marginBottom: 12,
                  background: 'rgba(250,173,20,0.1)', color: '#faad14', fontSize: 13,
                }}>
                  ⚠️ {conflict}
                </div>
              )}

              {/* 按钮 */}
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={!formData.title.trim() || !formData.scheduledStart || !formData.scheduledEnd || submitting}
                  style={{ flex: 1 }}
                >
                  {submitting ? '创建中...' : '创建预约'}
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={() => { setShowForm(false); setConflict(null); }}
                >
                  取消
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

// ─── 预约卡片子组件 ───────────────────────────────────────

function ScheduleCard({
  schedule,
  onCancel,
  onJoin,
  canCancel,
}: {
  schedule: MeetingSchedule;
  onCancel: (scheduleId: string) => void;
  onJoin?: (roomName: string) => void;
  canCancel: boolean;
}) {
  const statusConfig: Record<ScheduleStatus, { label: string; color: string; bg: string }> = {
    SCHEDULED: { label: '已预约', color: '#faad14', bg: 'rgba(250,173,20,0.1)' },
    ACTIVE: { label: '进行中', color: '#52c41a', bg: 'rgba(82,196,26,0.1)' },
    COMPLETED: { label: '已完成', color: '#8c8c8c', bg: 'rgba(140,140,140,0.1)' },
    CANCELLED: { label: '已取消', color: '#ff4d4f', bg: 'rgba(255,77,79,0.1)' },
  };
  const status = statusConfig[schedule.status];

  return (
    <div style={{
      padding: 14,
      border: '1px solid #2a2a3e',
      borderRadius: 8,
      background: '#1a1a2e',
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
    }}>
      {/* 左侧：预约信息 */}
      <div style={{ flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
          <strong style={{ fontSize: 14 }}>{schedule.title}</strong>
          <span style={{
            fontSize: 11, padding: '2px 8px', borderRadius: 999,
            background: status.bg, color: status.color,
          }}>
            {status.label}
          </span>
        </div>
        <div style={{ fontSize: 12, color: '#888' }}>
          <span>{getDeptName(schedule.department)}</span>
          <span style={{ margin: '0 6px' }}>·</span>
          <span>{formatDateTime(schedule.scheduledStart)}</span>
          <span style={{ margin: '0 4px' }}>~</span>
          <span>{formatDateTime(schedule.scheduledEnd)}</span>
          <span style={{ margin: '0 6px' }}>·</span>
          <span>{schedule.durationMinutes}分钟</span>
          <span style={{ margin: '0 6px' }}>·</span>
          <span>最多{schedule.maxParticipants}人</span>
        </div>
        <div style={{ fontSize: 11, color: '#555', marginTop: 2 }}>
          创建人: {schedule.creatorId}
          {schedule.enableRecording && <span style={{ marginLeft: 8, color: '#faad14' }}>● 录制</span>}
          {schedule.reminderSent && <span style={{ marginLeft: 8, color: '#52c41a' }}>● 已提醒</span>}
        </div>
      </div>

      {/* 右侧：操作按钮 */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        {schedule.status === 'ACTIVE' && schedule.roomName && onJoin && (
          <button
            className="btn btn-primary"
            onClick={() => onJoin(schedule.roomName!)}
            style={{ fontSize: 12, padding: '6px 16px' }}
          >
            加入会议
          </button>
        )}
        {canCancel && (schedule.status === 'SCHEDULED' || schedule.status === 'ACTIVE') && (
          <button
            className="btn"
            onClick={() => onCancel(schedule.scheduleId)}
            style={{ fontSize: 12, padding: '6px 12px', color: '#e53e3e' }}
          >
            取消
          </button>
        )}
      </div>
    </div>
  );
}

// ─── 样式常量 ─────────────────────────────────────────────

const labelStyle: React.CSSProperties = {
  display: 'block', fontSize: 13, fontWeight: 500, marginBottom: 4, color: '#ddd',
};

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '8px 12px', borderRadius: 6,
  border: '1px solid #333', background: '#1a1a2e', color: '#ddd',
  fontSize: 13, outline: 'none', boxSizing: 'border-box',
};

export default MeetingSchedule;
