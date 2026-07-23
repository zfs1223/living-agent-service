/**
 * 会议室页面 — 闭环 67-B（会议执行）
 *
 * P83 桌面端会议 UI 的核心页面，对齐 LIVEKIT_INTEGRATION_PLAN.md §6.3。
 * 已集成 @livekit/components-react + livekit-client。
 *
 * 功能：
 * - 实时音视频会议（集成 LiveKit React SDK）
 * - 视频网格展示参会者（VideoConference 组件）
 * - 音频控制 / 视频控制 / 屏幕共享（ControlBar 组件）
 * - 音频渲染（RoomAudioRenderer）
 * - 聊天侧边栏（保留自定义实现）
 * - 连接状态显示和计时器
 * - 离开会议按钮
 *
 * 连接流程（闭环 38→67 认证桥接）：
 * 1. 从 props 获取 roomName
 * 2. 调用 POST /api/meetings/{roomName}/token 获取 LiveKit JWT token
 * 3. 使用 <LiveKitRoom> + token 连接 LiveKit Server
 * 4. 会议结束后断开连接，通过 onLeave 回调返回 MeetingPage
 *
 * 注意：桌面端路由不使用 react-router，通过 App.tsx 的 view state 切换页面。
 */
import { useEffect, useState, useRef, useCallback } from 'react';
import {
  LiveKitRoom,
  VideoConference,
  RoomAudioRenderer,
  ControlBar,
  useLocalParticipant,
  useConnectionState,
  useParticipants,
} from '@livekit/components-react';
import { ConnectionState } from 'livekit-client';
import '@livekit/components-styles';
import { createMeetingApi } from '../../services/meeting/meeting-api';

// ─── Props ────────────────────────────────────────────────

interface MeetingRoomProps {
  /** 后端 URL */
  backendUrl: string;
  /** LiveKit 房间名 */
  roomName: string;
  /** 是否已登录 */
  hasToken: boolean;
  /** 离开会议，返回会议列表 */
  onLeave: () => void;
}

// ─── 聊天消息类型 ────────────────────────────────────────

interface ChatMessage {
  id: string;
  sender: string;
  content: string;
  timestamp: number;
  isSelf: boolean;
}

// ─── 会议内部组件（在 LiveKitRoom 上下文内使用 hooks） ──

/**
 * 顶部工具栏 — 需要在 LiveKitRoom 上下文内，使用 useConnectionState / useParticipants
 */
function MeetingHeader({
  meetingTitle,
  onLeave,
  elapsedSeconds,
}: {
  meetingTitle: string;
  onLeave: () => void;
  elapsedSeconds: number;
}) {
  const connectionState = useConnectionState();
  const participants = useParticipants();
  const connected = connectionState === ConnectionState.Connected;

  /** 格式化持续时间 */
  function formatElapsed(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      padding: '8px 16px', background: '#161b22', borderBottom: '1px solid #30363d',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span style={{ fontSize: 14, fontWeight: 600, color: '#e6edf3' }}>{meetingTitle}</span>
        <span style={{
          fontSize: 11, padding: '2px 8px', borderRadius: 999,
          background: connected ? 'rgba(47,229,141,0.12)' : 'rgba(255,80,80,0.12)',
          color: connected ? '#2fe58d' : '#f44',
        }}>
          {connected ? '已连接' : '未连接'}
        </span>
        <span style={{ fontSize: 12, color: '#8b949e', fontFamily: 'monospace' }}>
          {formatElapsed(elapsedSeconds)}
        </span>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontSize: 11, color: '#8b949e' }}>
          {participants.length} 人参会
        </span>
        <button
          onClick={onLeave}
          style={{
            padding: '4px 12px', borderRadius: 6,
            background: '#da3633', color: '#fff', border: 'none',
            cursor: 'pointer', fontSize: 12, fontWeight: 600,
          }}
        >
          离开会议
        </button>
      </div>
    </div>
  );
}

/**
 * 聊天侧边栏组件
 * 保留自定义实现，后续可集成 LiveKit DataChannel
 */
function ChatSidebar({
  showChat,
  onClose,
}: {
  showChat: boolean;
  onClose: () => void;
}) {
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [chatInput, setChatInput] = useState('');
  const { localParticipant } = useLocalParticipant();

  /** 发送聊天消息 */
  function handleSendChat() {
    if (!chatInput.trim()) return;
    const msg: ChatMessage = {
      id: `msg_${Date.now()}`,
      sender: localParticipant.name || localParticipant.identity,
      content: chatInput.trim(),
      timestamp: Date.now(),
      isSelf: true,
    };
    setChatMessages(prev => [...prev, msg]);
    setChatInput('');

    // TODO: 后续集成 LiveKit DataChannel 后，通过 localParticipant.publishData() 发送
  }

  if (!showChat) return null;

  return (
    <div style={{
      width: 300, borderLeft: '1px solid #30363d', background: '#161b22',
      display: 'flex', flexDirection: 'column',
    }}>
      {/* 聊天标题 */}
      <div style={{
        padding: '8px 12px', borderBottom: '1px solid #30363d',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <span style={{ fontSize: 13, fontWeight: 600, color: '#e6edf3' }}>会议聊天</span>
        <button
          onClick={onClose}
          style={{ background: 'none', border: 'none', color: '#8b949e', cursor: 'pointer', fontSize: 14 }}
        >
          ✕
        </button>
      </div>

      {/* 聊天消息列表 */}
      <div style={{ flex: 1, overflowY: 'auto', padding: 8 }}>
        {chatMessages.length === 0 && (
          <div style={{ textAlign: 'center', color: '#484f58', padding: 24, fontSize: 12 }}>
            暂无消息，发送第一条消息吧
          </div>
        )}
        {chatMessages.map(msg => (
          <div key={msg.id} style={{
            marginBottom: 8, display: 'flex',
            justifyContent: msg.isSelf ? 'flex-end' : 'flex-start',
          }}>
            <div style={{
              maxWidth: '80%', padding: '6px 10px', borderRadius: 8,
              background: msg.isSelf ? '#6366f1' : '#21262d',
              color: '#e6edf3', fontSize: 12, lineHeight: 1.4,
            }}>
              {!msg.isSelf && (
                <div style={{ fontSize: 10, color: '#8b949e', marginBottom: 2 }}>{msg.sender}</div>
              )}
              {msg.content}
            </div>
          </div>
        ))}
      </div>

      {/* 聊天输入 */}
      <div style={{ padding: 8, borderTop: '1px solid #30363d' }}>
        <div style={{ display: 'flex', gap: 6 }}>
          <input
            value={chatInput}
            onChange={e => setChatInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') handleSendChat(); }}
            placeholder="输入消息..."
            style={{
              flex: 1, padding: '6px 10px', borderRadius: 6,
              border: '1px solid #30363d', background: '#0d1117',
              color: '#e6edf3', fontSize: 12, outline: 'none',
            }}
          />
          <button
            onClick={handleSendChat}
            disabled={!chatInput.trim()}
            style={{
              padding: '6px 12px', borderRadius: 6, border: 'none',
              background: chatInput.trim() ? '#6366f1' : '#21262d',
              color: chatInput.trim() ? '#fff' : '#484f58',
              cursor: 'pointer', fontSize: 12,
            }}
          >
            发送
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * 会议主内容区 — 在 LiveKitRoom 上下文内，整合 VideoConference + 聊天侧边栏
 */
function MeetingContent({
  meetingTitle,
  onLeave,
  elapsedSeconds,
}: {
  meetingTitle: string;
  onLeave: () => void;
  elapsedSeconds: number;
}) {
  const [showChat, setShowChat] = useState(false);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#0d1117' }}>
      {/* 顶部工具栏 */}
      <MeetingHeader
        meetingTitle={meetingTitle}
        onLeave={onLeave}
        elapsedSeconds={elapsedSeconds}
      />

      {/* 主内容区 */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* 视频会议区域 — LiveKit VideoConference 组件自动处理视频网格 */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div style={{ flex: 1, position: 'relative' }}>
            <VideoConference />
          </div>
        </div>

        {/* 聊天侧边栏 */}
        <ChatSidebar showChat={showChat} onClose={() => setShowChat(false)} />
      </div>

      {/* 底部控制栏 — LiveKit ControlBar 组件提供麦克风/摄像头/屏幕共享/离开等控制 */}
      <div style={{
        padding: '8px 16px', background: '#161b22', borderTop: '1px solid #30363d',
        display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 8,
      }}>
        <ControlBar
          variation="minimal"
          controls={{
            microphone: true,
            camera: true,
            screenShare: true,
            leave: true,
          }}
        />
        {/* 自定义聊天开关按钮 — ControlBar 不内置聊天按钮，此处补充 */}
        <button
          onClick={() => setShowChat(!showChat)}
          style={{
            width: 44, height: 44, borderRadius: '50%',
            background: showChat ? '#6366f1' : '#21262d',
            border: showChat ? '2px solid #818cf8' : '2px solid #30363d',
            color: showChat ? '#fff' : '#e6edf3', fontSize: 18,
            cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'all 0.2s',
          }}
          title="聊天"
        >
          💬
        </button>
      </div>

      {/* 音频渲染器 — 负责播放远端参会者的音频 */}
      <RoomAudioRenderer />
    </div>
  );
}

// ─── 主组件 ────────────────────────────────────────────────

export function MeetingRoom({
  backendUrl,
  roomName,
  hasToken,
  onLeave,
}: MeetingRoomProps) {
  // ── 状态 ──
  const [token, setToken] = useState<string>('');
  const [livekitUrl, setLivekitUrl] = useState<string>('');
  const [connecting, setConnecting] = useState(true);
  const [error, setError] = useState('');
  const [meetingTitle, setMeetingTitle] = useState(roomName);

  // 连接持续时间
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [timerActive, setTimerActive] = useState(false);
  const elapsedTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const api = createMeetingApi(backendUrl);

  // ── 获取参会 Token ──
  const fetchToken = useCallback(async () => {
    if (!hasToken || !roomName) return;
    try {
      setConnecting(true);
      setError('');
      const result = await api.getToken(roomName);
      setToken(result.token);
      setLivekitUrl(result.livekitUrl);
    } catch (e: any) {
      setError(e.message || '获取会议 token 失败');
    } finally {
      setConnecting(false);
    }
  }, [backendUrl, hasToken, roomName]);

  useEffect(() => {
    void fetchToken();
  }, [fetchToken]);

  // ── 获取会议详情（标题） ──
  useEffect(() => {
    if (!hasToken || !roomName) return;
    api.getMeeting(roomName).then(m => {
      if (m.title) setMeetingTitle(m.title);
    }).catch(() => {
      // 忽略，使用 roomName 作为标题
    });
  }, [hasToken, roomName]);

  // ── 计时器 ──
  useEffect(() => {
    if (timerActive) {
      elapsedTimerRef.current = setInterval(() => {
        setElapsedSeconds(prev => prev + 1);
      }, 1000);
    }
    return () => {
      if (elapsedTimerRef.current) clearInterval(elapsedTimerRef.current);
    };
  }, [timerActive]);

  // ── LiveKit 连接成功回调 ──
  function handleConnected() {
    setTimerActive(true);
  }

  // ── LiveKit 断开连接回调 ──
  function handleDisconnected() {
    setTimerActive(false);
  }

  // ── LiveKit 连接错误回调 ──
  function handleError(error: Error) {
    setError(error.message || 'LiveKit 连接失败');
    setTimerActive(false);
  }

  // ── 离开会议 ──
  function handleLeave() {
    setTimerActive(false);
    onLeave();
  }

  // ── 格式化时间 ──
  function formatElapsed(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  // ── 连接中状态 ──
  if (connecting) {
    return (
      <div style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
        height: '100%', color: '#999', gap: 16,
      }}>
        <div style={{
          width: 48, height: 48, border: '3px solid #333', borderTopColor: '#6366f1',
          borderRadius: '50%', animation: 'spin 1s linear infinite',
        }} />
        <div>正在连接会议 {roomName}...</div>
        <button className="btn" onClick={onLeave} style={{ fontSize: 13 }}>
          取消
        </button>
        <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      </div>
    );
  }

  // ── 错误状态 ──
  if (error && !token) {
    return (
      <div style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
        height: '100%', color: '#e53e3e', gap: 12,
      }}>
        <div style={{ fontSize: 36 }}>⚠</div>
        <div>连接会议失败</div>
        <div style={{ fontSize: 12, color: '#888', maxWidth: 400, textAlign: 'center' }}>{error}</div>
        <button className="btn" onClick={onLeave} style={{ fontSize: 13, marginTop: 8 }}>
          返回会议列表
        </button>
      </div>
    );
  }

  // ── 等待 token ──
  if (!token || !livekitUrl) {
    return (
      <div style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
        height: '100%', color: '#999', gap: 16,
      }}>
        <div>正在准备会议凭证...</div>
        <button className="btn" onClick={onLeave} style={{ fontSize: 13 }}>
          取消
        </button>
      </div>
    );
  }

  // ── 会议室主界面 — 使用 LiveKitRoom 包裹整个会议 ──
  return (
    <LiveKitRoom
      serverUrl={livekitUrl}
      token={token}
      connect={true}
      audio={true}
      video={true}
      onConnected={handleConnected}
      onDisconnected={handleDisconnected}
      onError={handleError}
      data-lk-theme="default"
      style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
    >
      <MeetingContent
        meetingTitle={meetingTitle}
        onLeave={handleLeave}
        elapsedSeconds={elapsedSeconds}
      />
    </LiveKitRoom>
  );
}

export default MeetingRoom;
