/**
 * 办公室聊天页面 - 左侧办公室房间布局 + 右侧聊天功能
 *
 * 布局：
 * - 左侧：办公室房间（两个区域：主工位、讨论区）
 *   - 主工位：working/active/learning/evolving 状态的员工
 *   - 讨论区：idle/busy 状态的员工
 *   - 其他状态（error/offline/disabled等）在员工头部显示消息气泡
 * - 右侧：聊天功能，包括新建对话、历史记录
 */
import { useEffect, useState, useRef, useCallback } from 'react';
import PixelEmployee from './PixelEmployee';

// 员工数据类型
interface Employee {
  id: string;
  name: string;
  title?: string;
  status: string;
  currentTask?: string;
  lastActiveAt?: string;
  department?: string;
  instanceNum?: number;
}

// 聊天消息类型
interface ChatMessage {
  messageId?: string;
  userId?: string;
  userName?: string;
  content: string;
  timestamp?: string;
  isSelf: boolean;
  role?: 'user' | 'assistant';
}

// 对话类型
interface Conversation {
  conversationId: string;
  title: string;
  departmentCode: string;
  status: string;
  lastActivityAt: string;
  createdAt: string;
}

// 区域类型：只有两个区域
type ZoneId = 'workstation' | 'discussion';

// 根据状态获取区域（只有两个区域）
function getZoneByStatus(status: string): ZoneId {
  const s = status.toLowerCase();
  // 工作状态 → 主工位（兼容后端枚举值 ACTIVE/LEARNING 等）
  if (['working', 'active', 'learning', 'evolving', 'evolating', 'busy'].includes(s)) {
    return 'workstation';
  }
  // 其他所有状态 → 讨论区（idle/error/offline/disabled/dormant/archived/terminated等）
  return 'discussion';
}

// 区域配置
const ZONES: Record<ZoneId, { title: string; hint: string; icon: string }> = {
  workstation: { title: '主工位', hint: '正在工作的员工', icon: '💼' },
  discussion: { title: '讨论区', hint: '休息、协作或异常状态', icon: '💬' },
};

export function OfficeChatPage({
  backendUrl,
  hasToken,
  currentUser,
  onLogin,
  department = 'tech'
}: {
  backendUrl: string;
  hasToken: boolean;
  currentUser: any;
  onLogin: () => void;
  department?: string;
}) {
  // 员工数据
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [loadingEmployees, setLoadingEmployees] = useState(true);

  // 聊天状态
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [connected, setConnected] = useState(false);
  const [isWaiting, setIsWaiting] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [showSidebar, setShowSidebar] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // WebSocket 引用
  const wsRef = useRef<WebSocket | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 当前部门
  const [currentDept, setCurrentDept] = useState(department);

  // 部门列表
  const DEPARTMENTS = [
    { code: 'tech', name: '技术部', icon: '🧠' },
    { code: 'hr', name: '人力资源', icon: '👥' },
    { code: 'finance', name: '财务部', icon: '💰' },
    { code: 'sales', name: '销售部', icon: '📈' },
    { code: 'cs', name: '客服部', icon: '🎧' },
    { code: 'admin', name: '行政部', icon: '🛠️' },
    { code: 'legal', name: '法务部', icon: '⚖️' },
    { code: 'ops', name: '运营部', icon: '🚚' },
  ];

  // 滚动到最新消息
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 加载员工数据
  // 参考 frontend/src/pages/DepartmentDetail/DepartmentDetail.tsx
  // 使用 /fixed-employees/definitions/by-department/{dept} + /agents + /office/department/{dept} 三个 API
  const loadEmployees = useCallback(async () => {
    if (!backendUrl || !hasToken) return;
    setLoadingEmployees(true);
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      const headers: Record<string, string> = { Authorization: `Bearer ${token}` };

      // 并行获取：部门固定员工定义 + 所有 agent + 办公室状态快照
      const [defsRes, agentsRes, officeRes] = await Promise.allSettled([
        fetch(`${backendUrl}/api/fixed-employees/definitions/by-department/${currentDept}`, { headers }),
        fetch(`${backendUrl}/api/agents`, { headers }),
        fetch(`${backendUrl}/api/office/department/${currentDept}`, { headers }),
      ]);

      const fixedDefs: any[] = [];
      const baseAgents: any[] = [];
      let officeSnapshot: any = null;

      if (defsRes.status === 'fulfilled' && defsRes.value.ok) {
        const data = await defsRes.value.json();
        fixedDefs.push(...(data.data || data || []));
      }

      if (agentsRes.status === 'fulfilled' && agentsRes.value.ok) {
        const data = await agentsRes.value.json();
        baseAgents.push(...(data.data || data || []));
      }

      if (officeRes.status === 'fulfilled' && officeRes.value.ok) {
        const data = await officeRes.value.json();
        officeSnapshot = data.data || data;
      }

      // 合并固定员工定义和 agent 状态
      // 参考 DepartmentDetail.tsx 的 fixedEmployees 逻辑
      const list: Employee[] = fixedDefs.map((def: any, i: number) => {
        // 从 baseAgents 中匹配对应的 agent（兼容多种字段名）
        const matched = baseAgents.find((agent: any) => {
          const agentId = agent.id || agent.code || agent.employeeId || '';
          return agentId === def.code || agentId === def.id ||
                 agent.employeeCode === def.code;
        });

        // 从办公室快照中获取员工状态（兼容多种字段名）
        const officeAgent = officeSnapshot?.agents?.find((a: any) => {
          const aId = a.id || a.code || a.employeeId || '';
          return aId === def.code || aId === def.id || a.employeeCode === def.code;
        });

        return {
          id: def.code || `emp-${i}`,
          name: def.name || def.title || def.code || '未知员工',
          title: def.title || def.roles?.[0] || '',
          status: (officeAgent?.status || matched?.status || 'idle').toLowerCase(),
          currentTask: officeAgent?.currentTask || officeAgent?.current_task ||
                       matched?.current_task || matched?.currentTask || def.roles?.[0] || '',
          lastActiveAt: officeAgent?.lastActiveAt || officeAgent?.last_active_at ||
                        matched?.last_active_at || matched?.lastActiveAt || '',
          department: currentDept,
          instanceNum: i,
        };
      });

      // 如果固定员工为空，直接使用 agents
      if (list.length === 0 && baseAgents.length > 0) {
        baseAgents.forEach((agent: any, i: number) => {
          list.push({
            id: agent.id || agent.agent_id || agent.code || `emp-${i}`,
            name: agent.name || agent.display_name || agent.title || '未知员工',
            title: agent.title || agent.role_description || '',
            status: (agent.status || 'idle').toLowerCase(),
            currentTask: agent.current_task || agent.currentTask || '',
            lastActiveAt: agent.last_active_at || agent.lastActiveAt || '',
            department: agent.department || currentDept,
            instanceNum: i,
          });
        });
      }

      setEmployees(list);
    } catch (e) {
      console.warn('[OfficeChat] 加载员工失败:', e);
      setEmployees([]);
    } finally {
      setLoadingEmployees(false);
    }
  }, [backendUrl, hasToken, currentDept]);

  // 加载对话列表
  const loadConversations = useCallback(async () => {
    if (!backendUrl || !hasToken || !currentDept) return;
    setLoadingHistory(true);
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      const res = await fetch(`${backendUrl}/api/dept/${currentDept}/conversations`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setConversations(data.data || data || []);
      }
    } catch (e) {
      console.warn('[OfficeChat] 加载对话列表失败:', e);
    } finally {
      setLoadingHistory(false);
    }
  }, [backendUrl, hasToken, currentDept]);

  // 加载对话历史消息
  // API: GET /api/dept/{department}/conversations/history?conversationId=xxx&limit=100
  const loadConversationMessages = useCallback(async (convId: string) => {
    if (!backendUrl || !hasToken) return;
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      const res = await fetch(`${backendUrl}/api/dept/${currentDept}/conversations/history?conversationId=${convId}&limit=100`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        const history: ChatMessage[] = (data.data || data || []).map((m: any) => ({
          messageId: m.id || m.messageId,
          userId: m.userId || m.senderId,
          userName: m.userName || m.senderName,
          content: m.content || m.message || '',
          timestamp: m.timestamp || m.createdAt,
          isSelf: m.userId === currentUser?.id || m.role === 'user',
          role: m.role || 'user',
        }));
        setMessages(history);
        setConversationId(convId);
        setShowSidebar(false);
      }
    } catch (e) {
      console.warn('[OfficeChat] 加载历史消息失败:', e);
    }
  }, [backendUrl, hasToken, currentDept, currentUser]);

  // 新建对话
  const createNewConversation = useCallback(() => {
    setMessages([]);
    setConversationId(null);
    setShowSidebar(false);
  }, []);

  // WebSocket 连接（带断线重连）
  useEffect(() => {
    if (!hasToken || !backendUrl) return;

    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let reconnectAttempts = 0;
    const MAX_RECONNECT_ATTEMPTS = 5;
    let isManualClose = false;

    /**
     * 处理 win_automation_call 消息
     * 转发到本地 Python 服务执行，并通过 WebSocket 回传结果
     */
    async function handleWinAutomationCall(ws: WebSocket, data: { id?: number; operation?: string; args?: Record<string, unknown> }) {
      const { id, operation, args } = data ?? {};
      if (id === undefined || !operation) {
        console.warn('[OfficeChat] Invalid win_automation_call:', data);
        return;
      }

      try {
        // 确保 winAutomation 服务已启动
        const status = await window.livingAgentAPI.winAutomation.status();
        if (!status.running) {
          console.log('[OfficeChat] Starting winAutomation service...');
          await window.livingAgentAPI.winAutomation.start();
        }

        // 执行操作
        const result = await window.livingAgentAPI.winAutomation.execute(operation, args);
        if (result.success) {
          ws.send(JSON.stringify({
            type: 'win_automation_response',
            data: { id, success: true, result: result.result }
          }));
        } else {
          ws.send(JSON.stringify({
            type: 'win_automation_response',
            data: { id, success: false, error: result.error }
          }));
        }
      } catch (e: unknown) {
        const error = e instanceof Error ? e.message : String(e);
        ws.send(JSON.stringify({
          type: 'win_automation_response',
          data: { id, success: false, error }
        }));
      }
    }

    async function connectWebSocket() {
      const token = await window.livingAgentAPI.auth.getToken();
      if (!token) return;

      // 获取 clientId（用于 win_automation 工具控制本地电脑）
      const clientId = await window.livingAgentAPI.app.getClientId().catch(() => '');

      const wsUrl = backendUrl.replace(/^http/, 'ws');
      // 传递 conversationId 和 clientId（conversationId 用于断线重连，clientId 用于 Windows 自动化）
      const params = new URLSearchParams();
      if (conversationId) params.set('conversationId', conversationId);
      if (clientId) params.set('clientId', clientId);
      const queryString = params.toString();
      const ws = new WebSocket(`${wsUrl}/ws/dept/${currentDept}${queryString ? `?${queryString}` : ''}`, [`bearer.${token}`]);

      ws.onopen = () => {
        setConnected(true);
        wsRef.current = ws;
        reconnectAttempts = 0; // 重置重连计数
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);

          // 处理 win_automation_call：转发到本地 Python 服务执行后回传结果
          if (data.type === 'win_automation_call') {
            handleWinAutomationCall(ws, data.data);
            return; // 不继续处理其他消息类型
          }

          if (data.type === 'done') {
            // 更新 conversationId（服务端在新建对话时会返回）
            if (data.conversationId) {
              setConversationId(data.conversationId);
            }
            setMessages(prev => [...prev, {
              messageId: data.messageId,
              content: data.content || '',
              userName: data.brain || 'Brain',
              timestamp: data.timestamp || new Date().toISOString(),
              isSelf: false,
              role: 'assistant',
            }]);
            setIsWaiting(false);
          } else if (data.type === 'thinking') {
            // AI 思考中提示
            setIsWaiting(true);
          } else if (data.type === 'execution_progress') {
            // 执行进度更新
            if (data.employeeCode) {
              setEmployees(prev => prev.map(emp => {
                if (emp.id === data.employeeCode) {
                  return { ...emp, status: 'working', currentTask: data.taskKey || data.message || '' };
                }
                return emp;
              }));
            }
          } else if (data.type === 'employee_status_changed') {
            // 员工状态变更
            if (data.employeeCode || data.employeeId) {
              const empId = data.employeeCode || data.employeeId;
              setEmployees(prev => prev.map(emp => {
                if (emp.id === empId) {
                  return { ...emp, status: data.status || 'idle', currentTask: data.task || '' };
                }
                return emp;
              }));
            }
          } else if (data.type === 'employee_task_update') {
            // 员工任务更新
            if (data.employeeCode || data.employeeId) {
              const empId = data.employeeCode || data.employeeId;
              setEmployees(prev => prev.map(emp => {
                if (emp.id === empId) {
                  const newStatus = data.receiptStatus === 'COMPLETED' ? 'idle' :
                                   data.receiptStatus === 'FAILED' ? 'error' : 'working';
                  return { ...emp, status: newStatus, currentTask: data.taskKey || '' };
                }
                return emp;
              }));
            }
          } else if (data.type === 'reconnected') {
            // 断线重连成功，恢复 conversationId
            if (data.conversationId) {
              setConversationId(data.conversationId);
            }
            setConnected(true);
          } else if (data.type === 'error') {
            setMessages(prev => [...prev, {
              content: `错误: ${data.message || data.code}`,
              isSelf: false,
              role: 'assistant',
            }]);
            setIsWaiting(false);
          } else if (data.type === 'execution_event') {
            // 执行事件，更新员工状态
            if (data.employeeCode) {
              setEmployees(prev => prev.map(emp => {
                if (emp.id === data.employeeCode) {
                  const newStatus = data.receiptStatus === 'COMPLETED' ? 'idle' :
                                   data.receiptStatus === 'FAILED' ? 'error' : 'working';
                  return { ...emp, status: newStatus, currentTask: data.taskKey || '' };
                }
                return emp;
              }));
            }
            // async_final_response: 将结果摘要添加到聊天消息中
            if (data.eventType === 'async_final_response' && data.summary) {
              setMessages(prev => [...prev, {
                content: data.summary || '',
                userName: '任务执行',
                timestamp: data.timestamp || new Date().toISOString(),
                isSelf: false,
                role: 'assistant',
              }]);
              setIsWaiting(false);
            }
            // waiting_progress: 等待过程中的进度提示
            if (data.eventType === 'waiting_progress' && data.message) {
              setMessages(prev => {
                // 替换上一条等待消息，避免消息堆积
                const last = prev[prev.length - 1];
                if (last && last.role === 'assistant' && last.content.includes('正在执行')) {
                  return [...prev.slice(0, -1), {
                    content: data.message,
                    userName: '任务执行',
                    timestamp: data.timestamp || new Date().toISOString(),
                    isSelf: false,
                    role: 'assistant',
                  }];
                }
                return [...prev, {
                  content: data.message,
                  userName: '任务执行',
                  timestamp: data.timestamp || new Date().toISOString(),
                  isSelf: false,
                  role: 'assistant',
                }];
              });
            }
            // PR-7: proactive_report: 登录时主动汇报
            if (data.type === 'proactive_report' && data.suggestions) {
              const suggestions = data.suggestions as string[];
              const alerts = data.alerts as string[] || [];
              const reportContent = `📋 系统状态汇报\n\n` +
                (suggestions.length > 0 ? `💡 建议：\n${suggestions.map(s => `- ${s}`).join('\n')}\n\n` : '') +
                (alerts.length > 0 ? `⚠️ 警告：\n${alerts.map(a => `- ${a}`).join('\n')}\n\n` : '') +
                `📅 时间：${new Date().toLocaleString()}`;
              setMessages(prev => [...prev, {
                content: reportContent,
                userName: '系统汇报',
                timestamp: data.timestamp || new Date().toISOString(),
                isSelf: false,
                role: 'assistant',
              }]);
            }
          }
        } catch (e) {
          console.warn('[OfficeChat] 解析消息失败:', e);
        }
      };

      ws.onclose = () => {
        setConnected(false);
        wsRef.current = null;
        // 自动重连（指数退避）
        if (!isManualClose && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
          const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);
          reconnectAttempts++;
          console.log(`[OfficeChat] WebSocket 断开，${delay}ms 后重连 (尝试 ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})`);
          reconnectTimer = setTimeout(() => connectWebSocket(), delay);
        }
      };

      ws.onerror = () => {
        // 错误不显示消息，由 onclose 处理重连
        console.warn('[OfficeChat] WebSocket 连接错误');
      };

      return ws;
    }

    let ws: WebSocket | null = null;
    connectWebSocket().then((socket) => { if (socket) ws = socket; });

    return () => {
      isManualClose = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (ws) ws.close();
    };
  }, [backendUrl, currentDept, hasToken, currentUser, conversationId]);

  // 初始加载
  useEffect(() => {
    loadEmployees();
    loadConversations();
  }, [loadEmployees, loadConversations]);

  // 发送消息
  function handleSend() {
    if (!input.trim() || !connected || !wsRef.current) return;

    setMessages(prev => [...prev, {
      content: input,
      userId: currentUser?.id,
      userName: currentUser?.name || '我',
      timestamp: new Date().toISOString(),
      isSelf: true,
      role: 'user',
    }]);
    setIsWaiting(true);

    wsRef.current.send(JSON.stringify({
      type: 'CHAT',
      content: input,
      conversationId: conversationId,
    }));
    setInput('');
  }

  // 未登录提示
  if (!hasToken) {
    return (
      <div className="office-chat-page office-chat-page--centered">
        <div className="login-prompt">
          <h2>🔐 请先登录</h2>
          <p>办公室和聊天功能需要登录后才能使用</p>
          <button className="btn btn-primary" onClick={onLogin}>🔑 登录</button>
        </div>
      </div>
    );
  }

  // 未配置后端提示
  if (!backendUrl) {
    return (
      <div className="office-chat-page office-chat-page--centered">
        <div className="login-prompt">
          <h2>⚠️ 未配置后端地址</h2>
          <p>请先在"设置"中配置后端服务地址</p>
        </div>
      </div>
    );
  }

  // 按区域分组员工（只有两个区域）
  const employeesByZone: Record<ZoneId, Employee[]> = {
    workstation: [],
    discussion: [],
  };
  employees.forEach(emp => {
    employeesByZone[getZoneByStatus(emp.status)].push(emp);
  });

  return (
    <div className="office-chat-page">
      {/* ========== 左侧：办公室房间 ========== */}
      <section className="office-room">
        <header className="office-room__header">
          <h2>🏢 {DEPARTMENTS.find(d => d.code === currentDept)?.name || currentDept} 办公室</h2>
          <select
            value={currentDept}
            onChange={(e) => setCurrentDept(e.target.value)}
            className="dept-selector"
          >
            {DEPARTMENTS.map(d => (
              <option key={d.code} value={d.code}>{d.icon} {d.name}</option>
            ))}
          </select>
        </header>

        {/* 办公室楼层 - 只有两个区域 */}
        <div className="office-floor">
          {loadingEmployees ? (
            <div className="office-floor__loading">加载员工数据...</div>
          ) : (
            (['workstation', 'discussion'] as ZoneId[]).map(zoneId => (
              <div key={zoneId} className={`office-zone office-zone--${zoneId}`}>
                <div className="office-zone__header">
                  <span className="office-zone__icon">{ZONES[zoneId].icon}</span>
                  <div className="office-zone__title-group">
                    <span className="office-zone__title">{ZONES[zoneId].title}</span>
                    <span className="office-zone__hint">{ZONES[zoneId].hint}</span>
                  </div>
                  <span className="office-zone__count">{employeesByZone[zoneId].length}</span>
                </div>
                <div className="office-zone__employees">
                  {employeesByZone[zoneId].length === 0 ? (
                    <div className="office-zone__empty">暂无员工</div>
                  ) : (
                    employeesByZone[zoneId].map(emp => (
                      <PixelEmployee
                        key={emp.id}
                        id={emp.id}
                        name={emp.name}
                        title={emp.title}
                        status={emp.status}
                        currentTask={emp.currentTask}
                        department={emp.department || currentDept}
                        instanceNum={emp.instanceNum}
                      />
                    ))
                  )}
                </div>
              </div>
            ))
          )}
        </div>

        {/* 办公室统计 */}
        <footer className="office-room__footer">
          <span>总计 {employees.length} 名员工</span>
          <span>·</span>
          <span>主工位 {employeesByZone.workstation.length}</span>
          <span>·</span>
          <span>讨论区 {employeesByZone.discussion.length}</span>
        </footer>
      </section>

      {/* ========== 右侧：聊天区域 ========== */}
      <section className="chat-section">
        {/* 聊天工具栏 */}
        <header className="chat-toolbar">
          <div className="chat-toolbar__left">
            <button
              className="btn btn-icon"
              onClick={() => setShowSidebar(!showSidebar)}
              title="历史对话"
            >
              📜
            </button>
            <button
              className="btn btn-icon"
              onClick={createNewConversation}
              title="新建对话"
            >
              ➕
            </button>
            <span className="chat-toolbar__title">
              {conversationId ? '对话' : '新对话'}
            </span>
          </div>
          <div className="chat-toolbar__right">
            <span className={`connection-status ${connected ? 'connected' : 'disconnected'}`}>
              {connected ? '● 已连接' : '○ 未连接'}
            </span>
          </div>
        </header>

        {/* 历史对话侧边栏 */}
        {showSidebar && (
          <aside className="chat-sidebar">
            <header className="chat-sidebar__header">
              <h3>历史对话</h3>
              <button className="btn btn-icon" onClick={() => setShowSidebar(false)}>✕</button>
            </header>
            <div className="chat-sidebar__list">
              {loadingHistory ? (
                <div className="chat-sidebar__loading">加载中...</div>
              ) : conversations.length === 0 ? (
                <div className="chat-sidebar__empty">暂无历史对话</div>
              ) : (
                conversations.map(conv => (
                  <button
                    key={conv.conversationId}
                    className={`chat-sidebar__item ${conversationId === conv.conversationId ? 'active' : ''}`}
                    onClick={() => loadConversationMessages(conv.conversationId)}
                  >
                    <span className="chat-sidebar__item-title">{conv.title || '未命名对话'}</span>
                    <span className="chat-sidebar__item-time">
                      {conv.lastActivityAt ? new Date(conv.lastActivityAt).toLocaleDateString() : ''}
                    </span>
                  </button>
                ))
              )}
            </div>
          </aside>
        )}

        {/* 消息列表 */}
        <div className="chat-messages">
          {messages.length === 0 ? (
            <div className="chat-messages__empty">
              <p>开始与 {DEPARTMENTS.find(d => d.code === currentDept)?.name} Brain 对话</p>
              <p className="hint">输入消息后按 Enter 发送</p>
            </div>
          ) : (
            messages.map((msg, i) => (
              <div
                key={i}
                className={`chat-message ${msg.isSelf ? 'chat-message--self' : 'chat-message--other'} ${msg.role === 'assistant' ? 'chat-message--brain' : ''}`}
              >
                {!msg.isSelf && msg.userName && (
                  <span className="chat-message__author">{msg.userName}</span>
                )}
                <div className="chat-message__content">
                  {msg.content}
                </div>
                {msg.timestamp && (
                  <span className="chat-message__time">
                    {new Date(msg.timestamp).toLocaleTimeString()}
                  </span>
                )}
              </div>
            ))
          )}
          {isWaiting && (
            <div className="chat-message chat-message--waiting">
              <span className="chat-message__author">Brain</span>
              <div className="chat-message__content chat-message__content--waiting">
                <span className="typing-indicator">●●●</span>
                <span>正在思考...</span>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* 输入区域 */}
        <footer className="chat-input">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
            placeholder="输入消息...（Enter 发送）"
            disabled={!connected || isWaiting}
            className="chat-input__field"
          />
          <button
            className="btn btn-primary chat-input__send"
            onClick={handleSend}
            disabled={!connected || !input.trim() || isWaiting}
          >
            发送
          </button>
        </footer>
      </section>
    </div>
  );
}