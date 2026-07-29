/**
 * IM 即时通讯页面
 *
 * 布局：左侧会话列表(280px) + 右侧消息面板
 * - 使用 imClient 监听 NEW_MESSAGE 事件更新消息
 * - 调用 /api/contacts 和 /api/im/messages REST API（前端预留接口调用）
 */
import { useEffect, useState, useRef, useCallback } from 'react';
import { ContactItem, Contact } from './ContactItem';
import { imClient } from '../../services/im/im-ws-client';
import './IMPage.css';

/** 后端返回的会话联系人数据 */
interface IMContactAPI {
  userId: string;
  contactId: string;
  contactType: string;
  muted: boolean;
  pinned: boolean;
  hidden: boolean;
  lastMessageContent: string | null;
  lastMessageTime: string | null;
  unreadCount: number;
}

/** 后端返回的消息数据 */
interface IMMessageAPI {
  messageId: string;
  senderId: string;
  recipientId: string;
  content: string;
  type: string;
  replyToId: string | null;
  createdAt: string;
  readAt: string | null;
  deletedAt: string | null;
}

/** 组件内部使用的消息数据 */
interface IMMessage {
  messageId: string;
  senderId: string;
  recipientId: string;
  content: string;
  type: string;
  replyToId: string | null;
  createdAt: string;
  readAt: string | null;
  deletedAt: string | null;
  self: boolean;
}

// ============ API 调用函数 ============

const API_BASE = '/api';

async function getAuthHeaders(): Promise<HeadersInit> {
  const token = await window.livingAgentAPI.auth.getToken();
  return { Authorization: `Bearer ${token || ''}`, 'Content-Type': 'application/json' };
}

/** 获取会话联系人列表 */
async function fetchContacts(includeHidden = false): Promise<IMContactAPI[]> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE}/im/contacts?includeHidden=${includeHidden}`, { headers });
  if (!res.ok) throw new Error(`fetchContacts failed: ${res.status}`);
  const json = await res.json();
  return json.data || json || [];
}

/** 获取聊天消息历史 */
async function fetchMessages(contactId: string, before?: string, limit = 20): Promise<IMMessageAPI[]> {
  const headers = await getAuthHeaders();
  const params = new URLSearchParams({ contactId, limit: String(limit) });
  if (before) params.set('before', before);
  const res = await fetch(`${API_BASE}/im/messages?${params.toString()}`, { headers });
  if (!res.ok) throw new Error(`fetchMessages failed: ${res.status}`);
  const json = await res.json();
  return json.data || json || [];
}

/** 设置免打扰 */
async function setContactMuted(contactId: string, muted: boolean): Promise<void> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE}/im/contacts/${encodeURIComponent(contactId)}/muted`, {
    method: 'PUT',
    headers,
    body: JSON.stringify({ muted }),
  });
  if (!res.ok) throw new Error(`setContactMuted failed: ${res.status}`);
}

/** 设置置顶 */
async function setContactPinned(contactId: string, pinned: boolean): Promise<void> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE}/im/contacts/${encodeURIComponent(contactId)}/pinned`, {
    method: 'PUT',
    headers,
    body: JSON.stringify({ pinned }),
  });
  if (!res.ok) throw new Error(`setContactPinned failed: ${res.status}`);
}

/** 设置隐藏 */
async function setContactHidden(contactId: string, hidden: boolean): Promise<void> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE}/im/contacts/${encodeURIComponent(contactId)}/hidden`, {
    method: 'PUT',
    headers,
    body: JSON.stringify({ hidden }),
  });
  if (!res.ok) throw new Error(`setContactHidden failed: ${res.status}`);
}

/** 撤回消息 */
async function recallMessageAPI(messageId: string): Promise<void> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE}/im/messages/${encodeURIComponent(messageId)}/recall`, {
    method: 'POST',
    headers,
  });
  if (!res.ok) throw new Error(`recallMessage failed: ${res.status}`);
}

/** 标记消息 */
async function markMessageAPI(messageId: string, markType: string): Promise<void> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE}/im/messages/${encodeURIComponent(messageId)}/mark`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ markType }),
  });
  if (!res.ok) throw new Error(`markMessage failed: ${res.status}`);
}

/** 获取总未读数 */
async function fetchUnreadCount(): Promise<number> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${API_BASE}/im/contacts/unread-count`, { headers });
  if (!res.ok) throw new Error(`fetchUnreadCount failed: ${res.status}`);
  const json = await res.json();
  return json.data?.unread_count ?? json.unread_count ?? 0;
}

interface IMPageProps {
  backendUrl: string;
  hasToken: boolean;
  currentUserId?: string;
}

/** 右键菜单状态 */
interface ContextMenuState {
  visible: boolean;
  x: number;
  y: number;
  messageId: string | null;
  isSelf: boolean;
}

export function IMPage({ backendUrl, hasToken, currentUserId }: IMPageProps) {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [selectedContactId, setSelectedContactId] = useState<string | null>(null);
  const [messages, setMessages] = useState<IMMessage[]>([]);
  const [inputText, setInputText] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [wsConnected, setWsConnected] = useState(false);
  const [contextMenu, setContextMenu] = useState<ContextMenuState>({ visible: false, x: 0, y: 0, messageId: null, isSelf: false });
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const selectedContact = contacts.find(c => c.id === selectedContactId) || null;

  // 连接 IM WebSocket 并注册事件监听
  useEffect(() => {
    if (!backendUrl || !hasToken) return;

    void (async () => {
      const token = await window.livingAgentAPI.auth.getToken();
      if (token) {
        imClient.connect(backendUrl, token);
      }
    })();

    const offConnect = imClient.on('connected', () => setWsConnected(true));
    const offDisconnect = imClient.on('disconnected', () => setWsConnected(false));

    // 监听新消息（后端 NEW_MESSAGE 格式）
    const offNewMessage = imClient.on('NEW_MESSAGE', (data: any) => {
      const senderId = data.senderId || '';
      const recipientId = data.recipientId || '';
      // 判断这条消息属于哪个会话联系人
      const contactId = senderId === currentUserId ? recipientId : senderId;
      const msg: IMMessage = {
        messageId: data.messageId || Date.now().toString(),
        senderId,
        recipientId,
        content: data.content || '',
        type: data.type || 'TEXT',
        replyToId: data.replyToId || null,
        createdAt: data.createdAt || new Date().toISOString(),
        readAt: null,
        deletedAt: null,
        self: senderId === currentUserId,
      };
      // 如果消息属于当前选中会话，追加到消息列表
      if (contactId === selectedContactId) {
        setMessages(prev => [...prev, msg]);
        // 收到对方消息时发送 ACK
        if (!msg.self) {
          imClient.send({ type: 'ACK', messageId: msg.messageId });
        }
      }
      // 更新会话列表的最后消息和未读数
      setContacts(prev => prev.map(c =>
        c.id === contactId
          ? {
              ...c,
              lastMessageContent: msg.content,
              lastMessageTime: msg.createdAt,
              unreadCount: c.id === selectedContactId ? c.unreadCount : c.unreadCount + 1,
            }
          : c
      ));
    });

    // 监听消息撤回
    const offMessageRecalled = imClient.on('MESSAGE_RECALLED', (data: any) => {
      const recalledId = data.messageId;
      if (!recalledId) return;
      setMessages(prev => prev.map(m =>
        m.messageId === recalledId ? { ...m, deletedAt: new Date().toISOString(), content: '[消息已撤回]' } : m
      ));
    });

    // 监听消息已读回执
    const offMessageAck = imClient.on('MESSAGE_ACK', (data: any) => {
      const ackedId = data.messageId;
      if (!ackedId) return;
      setMessages(prev => prev.map(m =>
        m.messageId === ackedId ? { ...m, readAt: new Date().toISOString() } : m
      ));
    });

    return () => {
      offConnect();
      offDisconnect();
      offNewMessage();
      offMessageRecalled();
      offMessageAck();
    };
  }, [backendUrl, hasToken, currentUserId, selectedContactId]);

  // 加载联系人列表
  useEffect(() => {
    if (!backendUrl || !hasToken) return;
    void loadContacts();
  }, [backendUrl, hasToken]);

  // 选中联系人时加载消息并发送 MARK_READ
  useEffect(() => {
    if (!selectedContactId || !backendUrl || !hasToken) return;
    void loadMessages(selectedContactId);
    // 通过 WebSocket 标记已读
    imClient.send({ type: 'MARK_READ', contactId: selectedContactId });
    // 清除本地未读计数
    setContacts(prev => prev.map(c =>
      c.id === selectedContactId ? { ...c, unreadCount: 0 } : c
    ));
  }, [selectedContactId, backendUrl, hasToken]);

  // 消息列表自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 点击任意位置关闭右键菜单
  useEffect(() => {
    if (!contextMenu.visible) return;
    const handleClick = () => setContextMenu(prev => ({ ...prev, visible: false }));
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, [contextMenu.visible]);

  async function loadContacts() {
    try {
      const apiList = await fetchContacts(false);
      const list: Contact[] = apiList.map((c: IMContactAPI) => ({
        id: c.contactId,
        name: c.contactId,
        avatar: null,
        lastMessageContent: c.lastMessageContent || '',
        lastMessageTime: c.lastMessageTime || '',
        unreadCount: c.unreadCount || 0,
        pinned: c.pinned || false,
        muted: c.muted || false,
        hidden: c.hidden || false,
        contactType: c.contactType || '',
      }));
      setContacts(list);
    } catch (e) {
      console.warn('[IM] 加载联系人失败:', e);
    }
  }

  async function loadMessages(contactId: string) {
    try {
      const apiList = await fetchMessages(contactId, undefined, 50);
      const list: IMMessage[] = apiList.map((m: IMMessageAPI) => ({
        messageId: m.messageId,
        senderId: m.senderId,
        recipientId: m.recipientId,
        content: m.content || '',
        type: m.type || 'TEXT',
        replyToId: m.replyToId,
        createdAt: m.createdAt,
        readAt: m.readAt,
        deletedAt: m.deletedAt,
        self: m.senderId === currentUserId,
      }));
      setMessages(list);
    } catch (e) {
      console.warn('[IM] 加载消息失败:', e);
    }
  }

  const handleSend = useCallback(() => {
    if (!inputText.trim() || !selectedContactId) return;
    const content = inputText.trim();
    // 通过 WebSocket 发送消息，使用后端规定的 SEND_MESSAGE 格式
    imClient.send({
      type: 'SEND_MESSAGE',
      recipientId: selectedContactId,
      content,
      messageType: 'TEXT',
    });
    // 乐观更新：立即在消息列表中显示
    const optimisticMsg: IMMessage = {
      messageId: `local_${Date.now()}`,
      senderId: currentUserId || '',
      recipientId: selectedContactId,
      content,
      type: 'TEXT',
      replyToId: null,
      createdAt: new Date().toISOString(),
      readAt: null,
      deletedAt: null,
      self: true,
    };
    setMessages(prev => [...prev, optimisticMsg]);
    // 更新会话列表最后消息
    setContacts(prev => prev.map(c =>
      c.id === selectedContactId
        ? { ...c, lastMessageContent: content, lastMessageTime: optimisticMsg.createdAt }
        : c
    ));
    setInputText('');
  }, [inputText, selectedContactId, currentUserId]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter 发送，Shift+Enter 换行
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }, [handleSend]);

  // 右键菜单 — 撤回消息
  const handleContextMenu = useCallback((e: React.MouseEvent, msg: IMMessage) => {
    e.preventDefault();
    setContextMenu({
      visible: true,
      x: e.clientX,
      y: e.clientY,
      messageId: msg.messageId,
      isSelf: msg.self,
    });
  }, []);

  const handleRecallMessage = useCallback(async () => {
    if (!contextMenu.messageId) return;
    const msgId = contextMenu.messageId;
    try {
      await recallMessageAPI(msgId);
      // 同时通过 WebSocket 发送撤回
      imClient.send({ type: 'RECALL_MESSAGE', messageId: msgId });
      // 本地立即更新
      setMessages(prev => prev.map(m =>
        m.messageId === msgId ? { ...m, deletedAt: new Date().toISOString(), content: '[消息已撤回]' } : m
      ));
    } catch (e) {
      console.warn('[IM] 撤回消息失败:', e);
    }
    setContextMenu(prev => ({ ...prev, visible: false }));
  }, [contextMenu.messageId]);

  // 联系人操作：免打扰/置顶/隐藏
  const handleToggleMuted = useCallback(async (contactId: string, muted: boolean) => {
    try {
      await setContactMuted(contactId, !muted);
      setContacts(prev => prev.map(c =>
        c.id === contactId ? { ...c, muted: !muted } : c
      ));
    } catch (e) {
      console.warn('[IM] 设置免打扰失败:', e);
    }
  }, []);

  const handleTogglePinned = useCallback(async (contactId: string, pinned: boolean) => {
    try {
      await setContactPinned(contactId, !pinned);
      setContacts(prev => prev.map(c =>
        c.id === contactId ? { ...c, pinned: !pinned } : c
      ));
    } catch (e) {
      console.warn('[IM] 设置置顶失败:', e);
    }
  }, []);

  const handleToggleHidden = useCallback(async (contactId: string, hidden: boolean) => {
    try {
      await setContactHidden(contactId, !hidden);
      setContacts(prev => prev.map(c =>
        c.id === contactId ? { ...c, hidden: !hidden } : c
      ));
    } catch (e) {
      console.warn('[IM] 设置隐藏失败:', e);
    }
  }, []);

  // 按搜索过滤联系人（排除隐藏的会话）
  const filteredContacts = (searchQuery
    ? contacts.filter(c => c.name.toLowerCase().includes(searchQuery.toLowerCase()))
    : contacts
  ).filter(c => !c.hidden);

  // 置顶排序：置顶的会话排在前面
  const sortedContacts = [...filteredContacts].sort((a, b) => {
    if (a.pinned && !b.pinned) return -1;
    if (!a.pinned && b.pinned) return 1;
    return 0;
  });

  if (!hasToken) {
    return (
      <div className="im-page">
        <div className="message-panel__empty">
          <div className="message-panel__empty-icon">🔒</div>
          <div className="message-panel__empty-text">请先登录以使用即时通讯</div>
        </div>
      </div>
    );
  }

  return (
    <div className="im-page">
      {/* 左侧会话列表 */}
      <div className="contact-list">
        <div className="contact-list__search">
          <input
            className="contact-list__search-input"
            placeholder="搜索联系人"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
          />
        </div>
        <div className={`im-page__status ${wsConnected ? 'im-page__status--connected' : 'im-page__status--disconnected'}`}>
          {wsConnected ? 'IM 在线' : 'IM 离线'}
        </div>
        <div className="contact-list__items">
          {sortedContacts.map(contact => (
            <ContactItem
              key={contact.id}
              contact={contact}
              selected={contact.id === selectedContactId}
              onClick={() => setSelectedContactId(contact.id)}
            />
          ))}
          {sortedContacts.length === 0 && (
            <div style={{ padding: 24, textAlign: 'center', color: '#555', fontSize: 13 }}>
              {searchQuery ? '无匹配联系人' : '暂无会话'}
            </div>
          )}
        </div>
      </div>

      {/* 右侧消息面板 */}
      <div className="message-panel">
        {selectedContact ? (
          <>
            <div className="message-panel__header">
              <span className="message-panel__title">{selectedContact.name}</span>
              <div className="message-panel__actions">
                <button
                  className="message-panel__action-btn"
                  title={selectedContact.pinned ? '取消置顶' : '置顶'}
                  onClick={() => handleTogglePinned(selectedContact.id, selectedContact.pinned)}
                >
                  {selectedContact.pinned ? '取消置顶' : '置顶'}
                </button>
                <button
                  className="message-panel__action-btn"
                  title={selectedContact.muted ? '取消免打扰' : '免打扰'}
                  onClick={() => handleToggleMuted(selectedContact.id, selectedContact.muted)}
                >
                  {selectedContact.muted ? '取消免打扰' : '免打扰'}
                </button>
                <button
                  className="message-panel__action-btn"
                  title="隐藏会话"
                  onClick={() => handleToggleHidden(selectedContact.id, selectedContact.hidden)}
                >
                  隐藏
                </button>
              </div>
            </div>
            <div className="message-panel__messages">
              {messages.map(msg => (
                <div
                  key={msg.messageId}
                  className={`message-bubble ${msg.self ? 'message-bubble--self' : 'message-bubble--other'}${msg.deletedAt ? ' message-bubble--recalled' : ''}`}
                  onContextMenu={(e) => handleContextMenu(e, msg)}
                >
                  <div>
                    <div className="message-bubble__content">
                      {msg.deletedAt ? (
                        <span style={{ color: '#999', fontStyle: 'italic' }}>[消息已撤回]</span>
                      ) : (
                        msg.content
                      )}
                    </div>
                    <div className="message-bubble__time">
                      {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''}
                      {msg.self && msg.readAt && <span style={{ marginLeft: 4, fontSize: 10, color: '#4caf50' }}>已读</span>}
                    </div>
                  </div>
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>
            <div className="message-panel__input">
              <textarea
                className="message-panel__input-textarea"
                placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                value={inputText}
                onChange={e => setInputText(e.target.value)}
                onKeyDown={handleKeyDown}
                rows={1}
              />
              <button
                className="message-panel__send-btn"
                disabled={!inputText.trim() || !wsConnected}
                onClick={handleSend}
              >
                发送
              </button>
            </div>

            {/* 右键菜单 */}
            {contextMenu.visible && (
              <div
                className="message-panel__context-menu"
                style={{ position: 'fixed', left: contextMenu.x, top: contextMenu.y, zIndex: 1000 }}
              >
                {contextMenu.isSelf && (
                  <div className="message-panel__context-menu-item" onClick={handleRecallMessage}>
                    撤回消息
                  </div>
                )}
              </div>
            )}
          </>
        ) : (
          <div className="message-panel__empty">
            <div className="message-panel__empty-icon">💬</div>
            <div className="message-panel__empty-text">选择联系人开始聊天</div>
          </div>
        )}
      </div>
    </div>
  );
}
