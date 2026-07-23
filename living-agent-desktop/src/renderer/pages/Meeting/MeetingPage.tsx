/**
 * 会议管理主页面 — 闭环 67 入口
 *
 * P83 桌面端会议 UI 的主页，对齐 LIVEKIT_INTEGRATION_PLAN.md §6.4。
 *
 * 布局：
 * - 左侧：会议列表（进行中 / 已预约 / 已结束三个 Tab）
 * - 右侧：创建会议按钮 + 加入会议入口（输入 roomName 快速加入）
 *
 * 权限对齐 P14（对齐 LIVEKIT_INTEGRATION_PLAN.md §7.1）：
 * - INTERNAL_ENTERPRISE（董事长）→ 可创建跨部门会议，可查看所有会议
 * - INTERNAL_ACTIVE（在职员工）→ 可创建本部门会议，可查看本部门会议
 * - INTERNAL_PROBATION（试用期）→ 不可创建，可查看本部门会议
 * - 其他身份 → 不可创建，不可查看
 *
 * 注意：桌面端路由不使用 react-router，通过 App.tsx 的 view state 切换页面。
 * 导航到 MeetingRoom 页面通过 onNavigateToRoom 回调实现。
 */
import { useEffect, useState, useCallback } from 'react';
import {
  createMeetingApi,
  type MeetingInfo,
  type MeetingStatus,
  type CreateMeetingResponse,
} from '../../services/meeting/meeting-api';
import type { DesktopUser } from '@shared/api-types';

// ─── Props ────────────────────────────────────────────────

interface MeetingPageProps {
  /** 后端 URL，与 OfficeChatPage 一致 */
  backendUrl: string;
  /** 是否已登录 */
  hasToken: boolean;
  /** 当前登录用户 */
  currentUser: DesktopUser | null;
  /** 跳转到会议室页面的回调 */
  onNavigateToRoom: (roomName: string) => void;
  /** 登录回调 */
  onLogin: () => void;
}

// ─── 权限判定（对齐 P14 + LIVEKIT_INTEGRATION_PLAN §7.1） ──────

/** 判断用户是否可以创建会议 */
function canCreateMeeting(user: DesktopUser | null): boolean {
  if (!user) return false;
  return user.identity === 'INTERNAL_ENTERPRISE' || user.identity === 'INTERNAL_ACTIVE';
}

/** 判断用户是否可以查看会议 */
function canViewMeeting(user: DesktopUser | null): boolean {
  if (!user) return false;
  // 离职员工和外来访客不可查看
  return user.identity !== 'INTERNAL_DEPARTED' && user.identity !== 'EXTERNAL_VISITOR';
}

// ─── 部门名称映射 ────────────────────────────────────────

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

// ─── 组件 ─────────────────────────────────────────────────

export function MeetingPage({
  backendUrl,
  hasToken,
  currentUser,
  onNavigateToRoom,
  onLogin,
}: MeetingPageProps) {
  // ── 状态 ──
  const [meetings, setMeetings] = useState<MeetingInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // 会议列表 Tab 切换
  const [tab, setTab] = useState<MeetingStatus | 'all'>('all');

  // 创建会议对话框
  const [showCreateDialog, setShowCreateDialog] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newMaxParticipants, setNewMaxParticipants] = useState(50);
  const [creating, setCreating] = useState(false);

  // 加入会议输入
  const [joinRoomName, setJoinRoomName] = useState('');

  // API 客户端
  const api = createMeetingApi(backendUrl);

  // ── 加载会议列表 ──
  const loadMeetings = useCallback(async () => {
    if (!hasToken || !backendUrl) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const status = tab === 'all' ? undefined : tab;
      const department = currentUser?.identity === 'INTERNAL_ENTERPRISE'
        ? undefined  // 董事长查看所有部门
        : currentUser?.department || undefined;
      const list = await api.listMeetings(status, department);
      setMeetings(Array.isArray(list) ? list : []);
    } catch (e: any) {
      setError(e.message || '加载会议列表失败');
    } finally {
      setLoading(false);
    }
  }, [backendUrl, hasToken, tab, currentUser]);

  useEffect(() => {
    void loadMeetings();
  }, [loadMeetings]);

  // ── 创建会议 ──
  async function handleCreateMeeting(e: React.FormEvent) {
    e.preventDefault();
    if (!newTitle.trim()) return;
    setCreating(true);
    try {
      const result: CreateMeetingResponse = await api.createMeeting(
        newTitle.trim(),
        newMaxParticipants,
      );
      setNewTitle('');
      setNewMaxParticipants(50);
      setShowCreateDialog(false);
      // 创建成功后直接跳转到会议室
      onNavigateToRoom(result.roomName);
    } catch (e: any) {
      setError(e.message || '创建会议失败');
    } finally {
      setCreating(false);
    }
  }

  // ── 加入会议 ──
  async function handleJoinMeeting() {
    if (!joinRoomName.trim()) return;
    onNavigateToRoom(joinRoomName.trim());
    setJoinRoomName('');
  }

  // ── 结束会议 ──
  async function handleEndMeeting(roomName: string) {
    try {
      await api.endMeeting(roomName);
      await loadMeetings();
    } catch (e: any) {
      setError(e.message || '结束会议失败');
    }
  }

  // ── 权限判定 ──
  const canCreate = canCreateMeeting(currentUser);
  const canView = canViewMeeting(currentUser);

  // ── 未登录提示 ──
  if (!hasToken) {
    return (
      <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>📡</div>
        <h2>会议功能需要登录</h2>
        <p style={{ fontSize: 13, color: '#666' }}>登录后可查看和参加会议</p>
        <button
          onClick={onLogin}
          style={{
            marginTop: 16, padding: '8px 24px', borderRadius: 8,
            background: '#6366f1', color: '#fff', border: 'none',
            cursor: 'pointer', fontWeight: 600, fontSize: 13,
          }}
        >
          登录
        </button>
      </div>
    );
  }

  // ── 无权限提示 ──
  if (!canView) {
    return (
      <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>🔒</div>
        <h2>无会议权限</h2>
        <p style={{ fontSize: 13, color: '#666' }}>当前身份无权访问会议功能</p>
      </div>
    );
  }

  // ── 按状态分组 ──
  const activeMeetings = meetings.filter(m => m.status === 'active');
  const scheduledMeetings = meetings.filter(m => m.status === 'scheduled');
  const finishedMeetings = meetings.filter(m => m.status === 'finished');

  // 当前 Tab 过滤后的列表
  const filteredMeetings = tab === 'all'
    ? meetings
    : tab === 'active'
      ? activeMeetings
      : tab === 'scheduled'
        ? scheduledMeetings
        : finishedMeetings;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 24 }}>
      {/* ── 顶部标题和操作 ── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 20 }}>📡 会议</h1>
          <span style={{ fontSize: 12, color: '#888' }}>
            {currentUser?.department ? `${getDeptName(currentUser.department)}会议` : '会议管理'}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {canCreate && (
            <button
              className="btn btn-primary"
              onClick={() => setShowCreateDialog(true)}
              style={{ fontSize: 13, padding: '6px 16px' }}
            >
              ➕ 创建会议
            </button>
          )}
          <button
            className="btn"
            onClick={() => void loadMeetings()}
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

      {/* ── 快速加入会议 ── */}
      <div style={{
        padding: 12, borderRadius: 8, marginBottom: 16,
        border: '1px solid rgba(99,102,241,0.3)', background: 'rgba(99,102,241,0.04)',
      }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <input
            value={joinRoomName}
            onChange={e => setJoinRoomName(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') void handleJoinMeeting(); }}
            placeholder="输入会议房间名快速加入..."
            style={{
              flex: 1, padding: '8px 12px', borderRadius: 6, border: '1px solid #333',
              background: '#1a1a2e', color: '#ddd', fontSize: 13, outline: 'none',
            }}
          />
          <button
            className="btn btn-primary"
            onClick={() => void handleJoinMeeting()}
            disabled={!joinRoomName.trim()}
            style={{ fontSize: 13, padding: '8px 16px', whiteSpace: 'nowrap' }}
          >
            加入会议
          </button>
        </div>
      </div>

      {/* ── 状态统计条 ── */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 12, fontSize: 12 }}>
        <span style={{ color: '#52c41a' }}>● 进行中 {activeMeetings.length}</span>
        <span style={{ color: '#faad14' }}>● 已预约 {scheduledMeetings.length}</span>
        <span style={{ color: '#8c8c8c' }}>● 已结束 {finishedMeetings.length}</span>
      </div>

      {/* ── Tab 切换 ── */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 12, borderBottom: '1px solid #333' }}>
        {(['all', 'active', 'scheduled', 'finished'] as const).map(t => (
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
            {t === 'all' ? '全部' : t === 'active' ? '进行中' : t === 'scheduled' ? '已预约' : '已结束'}
          </button>
        ))}
      </div>

      {/* ── 会议列表 ── */}
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>加载会议列表...</div>
        ) : filteredMeetings.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>
            <div style={{ fontSize: 36, marginBottom: 8 }}>📡</div>
            <p>暂无会议</p>
            {canCreate && (
              <p style={{ fontSize: 11, color: '#666' }}>点击"创建会议"发起一个新的视频会议</p>
            )}
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 8 }}>
            {filteredMeetings.map(meeting => (
              <MeetingCard
                key={meeting.roomName}
                meeting={meeting}
                onJoin={() => onNavigateToRoom(meeting.roomName)}
                onEnd={() => void handleEndMeeting(meeting.roomName)}
                canEnd={
                  // 只有创建人或董事长可以结束会议
                  currentUser?.identity === 'INTERNAL_ENTERPRISE' ||
                  meeting.createdBy === currentUser?.id
                }
              />
            ))}
          </div>
        )}
      </div>

      {/* ── 创建会议对话框 ── */}
      {showCreateDialog && (
        <div
          className="login-dialog-overlay"
          onClick={() => setShowCreateDialog(false)}
        >
          <div
            className="login-dialog"
            onClick={e => e.stopPropagation()}
            style={{ width: 420 }}
          >
            <h2 style={{ marginTop: 0 }}>➕ 创建会议</h2>

            <form onSubmit={e => void handleCreateMeeting(e)}>
              <div style={{ marginBottom: 12 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 500, marginBottom: 4, color: '#ddd' }}>
                  会议主题
                </label>
                <input
                  value={newTitle}
                  onChange={e => setNewTitle(e.target.value)}
                  placeholder="例如：技术部周会"
                  required
                  autoFocus
                  style={{
                    width: '100%', padding: '8px 12px', borderRadius: 6,
                    border: '1px solid #333', background: '#1a1a2e', color: '#ddd',
                    fontSize: 13, outline: 'none', boxSizing: 'border-box',
                  }}
                />
              </div>

              <div style={{ marginBottom: 12 }}>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 500, marginBottom: 4, color: '#ddd' }}>
                  最大参会人数
                </label>
                <select
                  value={newMaxParticipants}
                  onChange={e => setNewMaxParticipants(Number(e.target.value))}
                  style={{
                    width: '100%', padding: 8, borderRadius: 6,
                    border: '1px solid #333', background: '#1a1a2e', color: '#ddd',
                    fontSize: 13, outline: 'none',
                  }}
                >
                  <option value={10}>10 人</option>
                  <option value={20}>20 人</option>
                  <option value={30}>30 人</option>
                  <option value={50}>50 人</option>
                </select>
              </div>

              {/* 部门信息提示 */}
              <div style={{
                padding: '6px 10px', borderRadius: 4, marginBottom: 12,
                background: 'rgba(99,102,241,0.08)', fontSize: 11, color: '#888',
              }}>
                {currentUser?.identity === 'INTERNAL_ENTERPRISE'
                  ? '董事长身份：可创建跨部门会议'
                  : `会议将归属「${getDeptName(currentUser?.department || '')}」部门`}
              </div>

              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={!newTitle.trim() || creating}
                  style={{ flex: 1 }}
                >
                  {creating ? '创建中...' : '创建并进入'}
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={() => setShowCreateDialog(false)}
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

// ─── 会议卡片子组件 ───────────────────────────────────────

function MeetingCard({
  meeting,
  onJoin,
  onEnd,
  canEnd,
}: {
  meeting: MeetingInfo;
  onJoin: () => void;
  onEnd: () => void;
  canEnd: boolean;
}) {
  // 状态标签样式
  const statusConfig: Record<MeetingStatus, { label: string; color: string; bg: string }> = {
    active: { label: '进行中', color: '#52c41a', bg: 'rgba(82,196,26,0.1)' },
    scheduled: { label: '已预约', color: '#faad14', bg: 'rgba(250,173,20,0.1)' },
    finished: { label: '已结束', color: '#8c8c8c', bg: 'rgba(140,140,140,0.1)' },
  };
  const status = statusConfig[meeting.status];

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
      {/* 左侧：会议信息 */}
      <div style={{ flex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
          <strong style={{ fontSize: 14 }}>{meeting.title}</strong>
          <span style={{
            fontSize: 11, padding: '2px 8px', borderRadius: 999,
            background: status.bg, color: status.color,
          }}>
            {status.label}
          </span>
        </div>
        <div style={{ fontSize: 12, color: '#888' }}>
          <span>{getDeptName(meeting.department)}</span>
          <span style={{ margin: '0 6px' }}>·</span>
          <span>{meeting.participantCount}/{meeting.maxParticipants} 人</span>
          <span style={{ margin: '0 6px' }}>·</span>
          <span>{meeting.createdAt ? new Date(meeting.createdAt).toLocaleString('zh-CN') : ''}</span>
        </div>
        <div style={{ fontSize: 11, color: '#555', marginTop: 2 }}>
          房间: {meeting.roomName}
        </div>
      </div>

      {/* 右侧：操作按钮 */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        {meeting.status === 'active' && (
          <button
            className="btn btn-primary"
            onClick={onJoin}
            style={{ fontSize: 12, padding: '6px 16px' }}
          >
            加入
          </button>
        )}
        {meeting.status === 'scheduled' && (
          <button
            className="btn"
            onClick={onJoin}
            style={{ fontSize: 12, padding: '6px 16px' }}
          >
            预加入
          </button>
        )}
        {canEnd && meeting.status === 'active' && (
          <button
            className="btn"
            onClick={onEnd}
            style={{ fontSize: 12, padding: '6px 12px', color: '#e53e3e' }}
          >
            结束
          </button>
        )}
      </div>
    </div>
  );
}

export default MeetingPage;
