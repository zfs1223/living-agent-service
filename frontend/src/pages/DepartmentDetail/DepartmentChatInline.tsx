import { useEffect, useRef, useState, useCallback } from 'react';
import { useAuthStore } from '../../stores';
import { useTranslation } from 'react-i18next';
import { IconMessageCircle2, IconHistory, IconTrash, IconPlus, IconX } from '@tabler/icons-react';
import { departmentApi } from '../../services/api';

interface InlineChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp?: string;
}

interface ConversationItem {
  conversationId: string;
  title: string;
  departmentCode: string;
  status: string;
  lastActivityAt: string;
  lastMessageAt: string | null;
  createdAt: string;
}

export type ExecutionEventData = {
  type: 'execution_event';
  eventType: string;
  executionId: string;
  timestamp: string;
  employeeCode?: string;
  receiptStatus?: string;
  completedCount?: number;
  failedCount?: number;
  totalCount?: number;
  [key: string]: unknown;
};

export type EmployeeStatusChangedData = {
  type: 'employee_status_changed';
  employeeId: string;
  employeeName: string;
  oldStatus: string;
  newStatus: string;
  timestamp: string;
};

export default function DepartmentChatInline({ departmentCode, deptName, onExecutionEvent, onEmployeeStatusChanged }: {
  departmentCode: string;
  deptName: string;
  onExecutionEvent?: (event: ExecutionEventData) => void;
  onEmployeeStatusChanged?: (event: EmployeeStatusChangedData) => void;
}) {
  const user = useAuthStore((s) => s.user);
  const token = useAuthStore((s) => s.token);
  const { t, i18n } = useTranslation();
  const isChinese = i18n.language?.startsWith('zh');
  const [messages, setMessages] = useState<InlineChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [connected, setConnected] = useState(false);
  const [isWaiting, setIsWaiting] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [showSidebar, setShowSidebar] = useState(false);
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [activeConvTitle, setActiveConvTitle] = useState<string>('');
  const [requirementStatus, setRequirementStatus] = useState<string | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);

  const isEnterprise = Boolean(user && (user.identity === 'INTERNAL_ENTERPRISE' || user.access_level === 'FULL'));
  const canAccessDepartmentBrain = isEnterprise;

  const loadConversations = useCallback(async () => {
    if (!departmentCode || !token) return;
    setLoadingHistory(true);
    try {
      const list = await departmentApi.listConversations(departmentCode);
      setConversations(list || []);
    } catch (error) {
      console.warn('[DeptChat] Failed to load conversations:', error);
    } finally {
      setLoadingHistory(false);
    }
  }, [departmentCode, token]);

  const loadConversationMessages = useCallback(async (convId: string) => {
    try {
      const history = await departmentApi.getConversationHistory(departmentCode, convId, 100);
      const normalized: InlineChatMessage[] = (history || []).map((item: any) => ({
        role: item.role === 'assistant' || item.role === 'system' ? item.role : 'user',
        content: item.content || '',
        timestamp: item.timestamp,
      }));
      setMessages(normalized);
      setConversationId(convId);
      const conv = (conversations || []).find((c) => c.conversationId === convId);
      setActiveConvTitle(conv?.title || '');
      setShowSidebar(false);
    } catch (error: any) {
      if (error?.status === 404) {
        setMessages([]);
        setConversationId(convId);
        setShowSidebar(false);
      } else {
        console.warn('[DeptChat] Failed to load conversation history:', error);
      }
    }
  }, [departmentCode, conversations]);

  const deleteConversation = async (convId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await departmentApi.deleteConversation(departmentCode, convId);
      setConversations((prev) => prev.filter((c) => c.conversationId !== convId));
      if (conversationId === convId) {
        setMessages([]);
        setConversationId(null);
        setActiveConvTitle('');
      }
    } catch (error) {
      console.warn('[DeptChat] Failed to delete conversation:', error);
    }
  };

  const startNewConversation = () => {
    setMessages([]);
    setConversationId(null);
    setActiveConvTitle('');
    setShowSidebar(false);
  };

  const formatTime = (isoStr: string) => {
    try {
      const d = new Date(isoStr);
      const now = new Date();
      const isToday = d.toDateString() === now.toDateString();
      const yesterday = new Date(now);
      yesterday.setDate(yesterday.getDate() - 1);
      const isYesterday = d.toDateString() === yesterday.toDateString();
      if (isToday) {
        return t('deptOffice.today') + ' ' + d.toLocaleTimeString(isChinese ? 'zh-CN' : 'en-US', { hour: '2-digit', minute: '2-digit' });
      }
      if (isYesterday) {
        return t('deptOffice.yesterday') + ' ' + d.toLocaleTimeString(isChinese ? 'zh-CN' : 'en-US', { hour: '2-digit', minute: '2-digit' });
      }
      return d.toLocaleDateString(isChinese ? 'zh-CN' : 'en-US', { month: 'short', day: 'numeric' });
    } catch {
      return '';
    }
  };

  const groupConversationsByDate = (convList: ConversationItem[]) => {
    const groups: { label: string; items: ConversationItem[] }[] = [];
    const today = new Date().toDateString();
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayStr = yesterday.toDateString();

    const todayItems: ConversationItem[] = [];
    const yesterdayItems: ConversationItem[] = [];
    const olderItems: ConversationItem[] = [];

    for (const conv of convList) {
      const d = new Date(conv.lastActivityAt || conv.createdAt);
      const ds = d.toDateString();
      if (ds === today) todayItems.push(conv);
      else if (ds === yesterdayStr) yesterdayItems.push(conv);
      else olderItems.push(conv);
    }

    if (todayItems.length > 0) groups.push({ label: t('deptOffice.today'), items: todayItems });
    if (yesterdayItems.length > 0) groups.push({ label: t('deptOffice.yesterday'), items: yesterdayItems });
    if (olderItems.length > 0) groups.push({ label: t('deptOffice.earlier'), items: olderItems });

    return groups;
  };

  useEffect(() => {
    if (!token || !canAccessDepartmentBrain || !departmentCode) return;

    let cancelled = false;
    const MAX_RETRIES = 10;
    const INITIAL_BACKOFF_MS = 2000;
    const MAX_BACKOFF_MS = 30000;
    const HEARTBEAT_INTERVAL_MS = 30000; // 30秒心跳
    let retryCount = 0;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let heartbeatTimer: ReturnType<typeof setInterval> | null = null;

    loadConversations();

    const connect = () => {
      if (cancelled) return;

      const currentToken = useAuthStore.getState().token;
      if (!currentToken) return;

      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = `${protocol}//${window.location.host}/ws/dept/${encodeURIComponent(departmentCode)}?token=${currentToken}`;

      console.log('[DeptChat] Connecting to:', wsUrl.replace(/\?token=.*/, '?token=***'));

      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        if (cancelled) { ws.close(); return; }
        setConnected(true);
        retryCount = 0;
        // 启动心跳，防止被服务端判定为僵尸连接
        if (heartbeatTimer) clearInterval(heartbeatTimer);
        heartbeatTimer = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'ping' }));
          }
        }, HEARTBEAT_INTERVAL_MS);
      };
      ws.onclose = (event) => {
        if (cancelled) return;
        setConnected(false);
        // 清理心跳
        if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }

        if (event.code === 4001) {
          const freshToken = useAuthStore.getState().token;
          if (freshToken && freshToken !== currentToken) {
            retryCount = 0;
            reconnectTimer = setTimeout(connect, 1000);
            return;
          }
        }

        if (retryCount >= MAX_RETRIES) {
          console.warn('[DeptChat] Max reconnection retries reached, stopping');
          return;
        }

        const backoff = Math.min(INITIAL_BACKOFF_MS * Math.pow(2, retryCount), MAX_BACKOFF_MS);
        retryCount++;
        console.log(`[DeptChat] Reconnecting in ${backoff}ms (retry ${retryCount}/${MAX_RETRIES})`);
        reconnectTimer = setTimeout(connect, backoff);
      };
      ws.onerror = () => {
        if (!cancelled) setConnected(false);
      };
      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (['connected', 'pong', 'PONG', 'ONLINE_USERS', 'USER_JOINED', 'USER_LEFT', 'TYPING', 'CHAT', 'BRAIN_RESPONSE', 'control', 'aborted'].includes(data.type)) {
            return;
          }
          if (data.type === 'thinking') {
            setIsWaiting(false);
            setMessages((prev) => [...prev, { role: 'assistant', content: '...', timestamp: new Date().toISOString() }]);
            return;
          }
          if (data.type === 'done') {
            setIsWaiting(false);
            if (data.conversationId) {
              const newConvId = data.conversationId;
              setConversationId(newConvId);
              loadConversations();
            }
            setMessages((prev) => {
              const filtered = prev.filter((m) => !(m.role === 'assistant' && m.content === '...'));
              return [...filtered, { role: 'assistant', content: data.content || '', timestamp: new Date().toISOString() }];
            });
            return;
          }
          if (data.type === 'error' || data.type === 'quota_exceeded') {
            setIsWaiting(false);
            setMessages((prev) => [...prev, { role: 'system', content: data.message || t('chat.error'), timestamp: new Date().toISOString() }]);
            return;
          }
          if (data.type === 'chunk' || data.type === 'response') {
            setIsWaiting(false);
            setMessages((prev) => {
              const filtered = prev.filter((m) => !(m.role === 'assistant' && m.content === '...'));
              const last = prev[prev.length - 1];
              if (last && last.role === 'assistant' && last.content !== '...') {
                const updated = [...prev.slice(0, -1), { ...last, content: last.content + (data.content || '') }];
                return updated;
              }
              return [...filtered, { role: 'assistant', content: data.content || '', timestamp: new Date().toISOString() }];
            });
            return;
          }
          if (data.type === 'execution_event') {
            if (onExecutionEvent) {
              onExecutionEvent(data as ExecutionEventData);
            }
            // 更新需求状态
            if (data.requirementStatus) {
              setRequirementStatus(data.requirementStatus as string);
            }
            return;
          }
          // ✅ 员工状态变化事件：实时更新办公室区域
          if (data.type === 'employee_status_changed') {
            if (onEmployeeStatusChanged) {
              onEmployeeStatusChanged(data as EmployeeStatusChangedData);
            }
            return;
          }
        } catch {
          // ignore parse errors
        }
      };
    };

    connect();

    return () => {
      cancelled = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (heartbeatTimer) clearInterval(heartbeatTimer);
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, [token, departmentCode, canAccessDepartmentBrain, t, loadConversations, onExecutionEvent]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
  }, [messages]);

  const sendMessage = () => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;
    if (!input.trim()) return;

    setIsWaiting(true);
    const userMsg = input.trim();
    setMessages((prev) => [...prev, { role: 'user', content: userMsg, timestamp: new Date().toISOString() }]);

    wsRef.current.send(JSON.stringify({ type: 'CHAT', content: userMsg, conversationId }));
    setInput('');
    if (inputRef.current) {
      inputRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && !e.nativeEvent.isComposing && !isWaiting) {
      e.preventDefault();
      sendMessage();
    }
  };

  const resizeTextarea = () => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    const natural = el.scrollHeight;
    el.style.height = Math.min(natural, 100) + 'px';
    el.style.overflowY = natural > 100 ? 'auto' : 'hidden';
  };

  if (!canAccessDepartmentBrain) {
    return (
      <section className="card office-panel office-panel--compact">
        <div style={{ padding: '16px', textAlign: 'center', color: 'var(--text-tertiary)' }}>
          {t('deptOffice.insufficientPermissions')}
        </div>
      </section>
    );
  }

  const groupedConversations = groupConversationsByDate(conversations);

  return (
    <section className="card office-panel office-panel--chat-inline" style={{ minHeight: '320px', maxHeight: '480px', display: 'flex', flexDirection: 'column' }}>
      <div className="office-panel__header" style={{ flexShrink: 0 }}>
        <div style={{ minWidth: 0, flex: 1 }}>
          <h2 style={{ fontSize: '14px', display: 'flex', alignItems: 'center', gap: '4px' }}>
            <IconMessageCircle2 size={16} /> {activeConvTitle || t('deptOffice.departmentChat')}
          </h2>
          <p style={{ fontSize: '11px' }}>
            {t('deptOffice.chatWithBrain', { name: deptName })}
          </p>
        </div>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: '4px', alignItems: 'center', flexShrink: 0 }}>
          <button
            onClick={startNewConversation}
            className="btn btn-ghost"
            style={{ padding: '4px 6px', fontSize: '11px', display: 'flex', alignItems: 'center', gap: '2px' }}
            title={t('deptOffice.newChat')}
          >
            <IconPlus size={13} />
          </button>
          <button
            onClick={() => { setShowSidebar(!showSidebar); if (!showSidebar) loadConversations(); }}
            className="btn btn-ghost"
            style={{ padding: '4px 6px', fontSize: '11px', display: 'flex', alignItems: 'center', gap: '2px' }}
            title={t('deptOffice.chatHistory')}
          >
            <IconHistory size={13} />
          </button>
          <span className={`status-dot ${connected ? 'running' : 'stopped'}`} />
        </div>
      </div>

      {showSidebar && (
        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '8px',
          borderBottom: '1px solid var(--border-subtle)',
          background: 'var(--bg-tertiary)',
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
            <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)' }}>
              {t('deptOffice.history')}
            </span>
            <button onClick={() => setShowSidebar(false)} className="btn btn-ghost" style={{ padding: '2px 4px' }}>
              <IconX size={14} />
            </button>
          </div>
          {loadingHistory ? (
            <div style={{ textAlign: 'center', color: 'var(--text-tertiary)', fontSize: '12px', padding: '16px' }}>
              {t('deptOffice.loading')}
            </div>
          ) : conversations.length === 0 ? (
            <div style={{ textAlign: 'center', color: 'var(--text-tertiary)', fontSize: '12px', padding: '16px' }}>
              {t('deptOffice.noConversations')}
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {groupedConversations.map((group) => (
                <div key={group.label}>
                  <div style={{ fontSize: '10px', fontWeight: 600, color: 'var(--text-tertiary)', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '4px', paddingLeft: '2px' }}>
                    {group.label}
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                    {group.items.map((conv) => (
                      <div
                        key={conv.conversationId}
                        onClick={() => loadConversationMessages(conv.conversationId)}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          padding: '6px 8px',
                          borderRadius: '6px',
                          background: conversationId === conv.conversationId ? 'var(--accent-primary-dim)' : 'transparent',
                          cursor: 'pointer',
                          fontSize: '12px',
                          transition: 'background 0.15s',
                        }}
                        onMouseEnter={(e) => { if (conversationId !== conv.conversationId) e.currentTarget.style.background = 'var(--bg-secondary)'; }}
                        onMouseLeave={(e) => { if (conversationId !== conv.conversationId) e.currentTarget.style.background = 'transparent'; }}
                      >
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', lineHeight: '1.3' }}>
                            {conv.title || t('deptOffice.untitled')}
                          </div>
                        </div>
                        <button
                          onClick={(e) => deleteConversation(conv.conversationId, e)}
                          style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-tertiary)', padding: '2px', marginLeft: '4px', opacity: 0.5 }}
                          title={t('deptOffice.delete')}
                        >
                          <IconTrash size={11} />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <div style={{
        flex: 1,
        overflowY: 'auto',
        padding: '12px',
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
        minHeight: '180px',
      }}>
        {messages.length === 0 && (
          <div style={{ textAlign: 'center', color: 'var(--text-tertiary)', fontSize: '12px', padding: '24px 0' }}>
            {conversationId
              ? t('deptOffice.continueConversation')
              : t('deptOffice.startNewConversation')
            }
          </div>
        )}
        {messages.map((msg, idx) => (
          <div key={idx} style={{
            alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start',
            maxWidth: '85%',
            padding: '8px 12px',
            borderRadius: msg.role === 'user' ? '12px 12px 4px 12px' : '12px 12px 12px 4px',
            background: msg.role === 'user' ? 'var(--accent-primary)' : msg.role === 'system' ? 'var(--bg-tertiary)' : 'var(--bg-secondary)',
            color: msg.role === 'user' ? '#fff' : msg.role === 'system' ? 'var(--text-tertiary)' : 'var(--text-primary)',
            fontSize: '13px',
            lineHeight: '1.5',
            wordBreak: 'break-word',
          }}>
            {msg.content}
          </div>
        ))}
        {isWaiting && (
          <div style={{ alignSelf: 'flex-start', padding: '8px 12px', fontSize: '12px', color: 'var(--text-tertiary)' }}>
            {t('deptOffice.thinking')}
          </div>
        )}
        {requirementStatus && (
          <div style={{ alignSelf: 'flex-start', padding: '4px 12px', fontSize: '11px',
            color: requirementStatus === 'REQUIREMENT_CONFIRMED' || requirementStatus === 'EXECUTING' || requirementStatus === 'COMPLETED'
              ? 'var(--color-success, #10b981)' : requirementStatus === 'NEEDS_CLARIFICATION' || requirementStatus === 'CLARIFICATION_PENDING'
              ? 'var(--color-warning, #f59e0b)' : 'var(--text-tertiary)',
            opacity: 0.8
          }}>
            {requirementStatus === 'DRAFT' ? '📋 需求草稿' :
             requirementStatus === 'NEEDS_CLARIFICATION' ? '❓ 需要澄清需求' :
             requirementStatus === 'CLARIFICATION_PENDING' ? '⏳ 等待用户确认' :
             requirementStatus === 'REQUIREMENT_CONFIRMED' ? '✅ 需求已确认' :
             requirementStatus === 'PLANNING' ? '📐 规划中' :
             requirementStatus === 'PLANNED' ? '📋 已规划' :
             requirementStatus === 'ASSIGNED' ? '👥 已分配' :
             requirementStatus === 'EXECUTING' ? '⚡ 执行中' :
             requirementStatus === 'COMPLETED' ? '✅ 已完成' :
             requirementStatus === 'FAILED' ? '❌ 执行失败' : requirementStatus}
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div style={{ flexShrink: 0, borderTop: '1px solid var(--border-subtle)', padding: '8px', display: 'flex', gap: '8px' }}>
        <textarea
          ref={inputRef}
          value={input}
          onChange={(e) => { setInput(e.target.value); requestAnimationFrame(resizeTextarea); }}
          onKeyDown={handleKeyDown}
          placeholder={t('deptOffice.messagePlaceholder')}
          rows={1}
          style={{
            flex: 1,
            resize: 'none',
            padding: '8px 12px',
            borderRadius: '8px',
            border: '1px solid var(--border-subtle)',
            background: 'var(--bg-tertiary)',
            color: 'var(--text-primary)',
            fontSize: '13px',
            outline: 'none',
            minHeight: '36px',
            maxHeight: '100px',
          }}
        />
        <button
          onClick={sendMessage}
          disabled={!input.trim() || isWaiting || !connected}
          className="btn btn-primary"
          style={{ padding: '8px 16px', fontSize: '13px', height: 'fit-content', alignSelf: 'flex-end' }}
        >
          {t('deptOffice.send')}
        </button>
      </div>
    </section>
  );
}
