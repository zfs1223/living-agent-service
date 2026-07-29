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
import PixelEmployee, { type EmployeeOrigin } from './PixelEmployee';
import MessageRenderer from './MessageRenderer';
import FileUploader, { FilePreview } from './FileUploader';
import TraceVisualizer, { type TraceStepData } from '../../components/TraceVisualizer/TraceVisualizer';
import AgentProgressCards, { type AgentCardData } from '../../components/AgentProgressCards/AgentProgressCards';
import DeptRecommendBanner, { type DeptRecommendation, recommendByFileType, recommendByKeywords } from '../../components/DeptRecommend/DeptRecommendBanner';
import RememberButton from '../../components/MemoryControls/RememberButton';
import MemoryPanel from '../../components/MemoryControls/MemoryPanel';
import DeptQuickActions from '../../components/DeptQuickActions/DeptQuickActions';
import VoiceInputButton from '../../components/VoiceInput/VoiceInputButton';
import { imClient } from '../../services/im/im-ws-client';
import '../../components/DeptQuickActions/DeptQuickActions.css';
import '../../components/VoiceInput/VoiceInputButton.css';

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
  /**
   * P28: 员工来源（AGENTS.md §5.3 / §7.3）
   * - fixed: 固定数字员工（来自 /fixed-employees/definitions）→ 禁止 /ws/agent 直连
   * - personal: 个人助理（agent.origin=personal）→ 允许 /ws/agent 直连
   * - human: 真实人类（agent.origin=human）→ 允许 /ws/agent 直连
   */
  origin?: EmployeeOrigin;
}

// P5: 消息附件类型（WebSocket 协议扩展）
export interface ChatAttachment {
  fileId: string;              // 附件 ID（由后端 /api/files/upload 返回）
  type: 'image' | 'file' | 'audio' | 'screenshot';
  name: string;                // 原始文件名
  size?: number;               // 字节数
  url?: string;                // 可选：已上传后的可访问 URL
  thumbnailUrl?: string;       // 可选：缩略图 URL（图片类型）
}

// P5: 消息元数据类型（WebSocket 协议扩展）
interface ChatMetadata {
  source?: 'manual' | 'screenshot' | 'paste' | 'drag' | 'voice' | 'quickview';
  clientTimestamp?: number;    // 客户端发送时间戳
  [key: string]: unknown;      // 允许扩展字段
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
  /** P5: 附件列表（图片/文件/截图等） */
  attachments?: ChatAttachment[];
  /** P5: 消息元数据（来源/客户端时间戳等） */
  metadata?: ChatMetadata;
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

// ====== IM 即时通讯类型定义（从 IMPage.tsx 复用） ======

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

/** 聊天目标类型：部门大脑对话 或 IM 即时通讯 */
type ChatTarget =
  | { type: 'dept'; id: string }
  | { type: 'im'; id: string; name: string; origin?: string };

/** 右键菜单状态 */
interface IMContextMenuState {
  visible: boolean;
  x: number;
  y: number;
  messageId: string | null;
  isSelf: boolean;
}

// ====== IM API 调用函数 ======

const IM_API_BASE = '/api';

async function getImAuthHeaders(): Promise<HeadersInit> {
  const token = await window.livingAgentAPI.auth.getToken();
  return { Authorization: `Bearer ${token || ''}`, 'Content-Type': 'application/json' };
}

async function fetchImContacts(includeHidden = false): Promise<IMContactAPI[]> {
  const headers = await getImAuthHeaders();
  const res = await fetch(`${IM_API_BASE}/im/contacts?includeHidden=${includeHidden}`, { headers });
  if (!res.ok) throw new Error(`fetchImContacts failed: ${res.status}`);
  const json = await res.json();
  return json.data || json || [];
}

async function fetchImMessages(contactId: string, before?: string, limit = 20): Promise<IMMessageAPI[]> {
  const headers = await getImAuthHeaders();
  const params = new URLSearchParams({ contactId, limit: String(limit) });
  if (before) params.set('before', before);
  const res = await fetch(`${IM_API_BASE}/im/messages?${params.toString()}`, { headers });
  if (!res.ok) throw new Error(`fetchImMessages failed: ${res.status}`);
  const json = await res.json();
  return json.data || json || [];
}

async function setContactMuted(contactId: string, muted: boolean): Promise<void> {
  const headers = await getImAuthHeaders();
  const res = await fetch(`${IM_API_BASE}/im/contacts/${encodeURIComponent(contactId)}/muted`, {
    method: 'PUT', headers, body: JSON.stringify({ muted }),
  });
  if (!res.ok) throw new Error(`setContactMuted failed: ${res.status}`);
}

async function setContactPinned(contactId: string, pinned: boolean): Promise<void> {
  const headers = await getImAuthHeaders();
  const res = await fetch(`${IM_API_BASE}/im/contacts/${encodeURIComponent(contactId)}/pinned`, {
    method: 'PUT', headers, body: JSON.stringify({ pinned }),
  });
  if (!res.ok) throw new Error(`setContactPinned failed: ${res.status}`);
}

async function recallMessageAPI(messageId: string): Promise<void> {
  const headers = await getImAuthHeaders();
  const res = await fetch(`${IM_API_BASE}/im/messages/${encodeURIComponent(messageId)}/recall`, {
    method: 'POST', headers,
  });
  if (!res.ok) throw new Error(`recallMessage failed: ${res.status}`);
}

// 区域类型：只有两个区域
type ZoneId = 'workstation' | 'discussion';

// 状态类型（P2-5）
type StatusId = 'online' | 'busy' | 'away' | 'offline';

// 状态配置（P2-5）
const STATUS_CONFIG: Record<StatusId, { title: string; color: string }> = {
  online: { title: '在线', color: '#52c41a' },
  busy: { title: '忙碌', color: '#faad14' },
  away: { title: '离开', color: '#8c8c8c' },
  offline: { title: '离线', color: '#d9d9d9' },
};

// 根据状态获取状态ID（P2-5）
function getStatusId(status: string): StatusId {
  const s = status.toLowerCase();
  if (['working', 'active', 'learning', 'evolving', 'online'].includes(s)) return 'online';
  if (['busy'].includes(s)) return 'busy';
  if (['idle', 'away', 'dormant'].includes(s)) return 'away';
  return 'offline';
}

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
  department = 'tech',
  forceChannel,
  lockedDepartment
}: {
  backendUrl: string;
  hasToken: boolean;
  currentUser: any;
  onLogin: () => void;
  department?: string;
  forceChannel?: string;
  /**
   * P14: 部门身份绑定与自动锁定
   * 当传入部门编码（如 'hr'/'cs'）时：
   *   1. currentDept 初始化为该值
   *   2. forceChannel 变化时同步（重新登录切换部门）
   *   3. 部门选择器禁用并添加 dept-locked 视觉提示
   * 与 forceChannel（WS 路径，如 '/ws/public'）分离职责，避免冲突。
   */
  lockedDepartment?: string;
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
  // P1: 待发送附件列表
  const [pendingAttachments, setPendingAttachments] = useState<ChatAttachment[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // ====== 新增：会话类型管理（类似微信）======
  const [activeTab, setActiveTab] = useState<'department' | 'contacts' | 'ai'>('department'); // 当前选中的标签
  
  // 个人 AI 助手列表（从后端动态加载）
  const [aiAssistants, setAiAssistants] = useState<Array<{
    id: string;
    name: string;
    type: 'ai';
    icon: string;
  }>>([]);

  // ====== 新增：工作目录选择器 ======
  const [selectedWorkspace, setSelectedWorkspace] = useState<string>(''); // 当前选择的工作目录ID
  const [authorizedWorkspaces, setAuthorizedWorkspaces] = useState<Array<{
    id: string;
    name: string;
    path: string;
    scope: 'read' | 'read-write';
  }>>([]);

  // P8: Trace 可视化
  const [traceSteps, setTraceSteps] = useState<TraceStepData[]>([]);
  const [showTrace, setShowTrace] = useState(false);

  // P9: 多 Agent 并行卡片
  const [agentCards, setAgentCards] = useState<AgentCardData[]>([]);

  // P11: 部门路由推荐
  const [deptRecommendation, setDeptRecommendation] = useState<DeptRecommendation | null>(null);

  // P12: Memory 显式指令
  const [showMemoryPanel, setShowMemoryPanel] = useState(false);

  // P12: 记忆消息
  const handleRemember = useCallback(async (content: string) => {
    if (!backendUrl || !hasToken) return;
    const token = await window.livingAgentAPI.auth.getToken();
    const res = await fetch(`${backendUrl}/api/memory/entries`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({ content, source: 'chat' })
    });
    if (!res.ok) throw new Error('记忆保存失败');
  }, [backendUrl, hasToken]);

  // P12: 删除记忆
  const handleDeleteMemory = useCallback(async (id: string) => {
    if (!backendUrl || !hasToken) return;
    const token = await window.livingAgentAPI.auth.getToken();
    const res = await fetch(`${backendUrl}/api/memory/entries/${id}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` }
    });
    if (!res.ok) throw new Error('删除失败');
  }, [backendUrl, hasToken]);

  // WebSocket 引用
  const wsRef = useRef<WebSocket | null>(null);
  // conversationId ref：供 WebSocket 连接读取最新值，避免将 conversationId 放入 useEffect 依赖
  // （原实现中 conversationId 在依赖数组里，每次 done 消息更新 conversationId 都会销毁重建连接）
  const conversationIdRef = useRef<string | null>(null);
  conversationIdRef.current = conversationId;
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement | null>(null); // P6: 输入框引用（用于快捷键聚焦）

  // ====== 新增：selectedWorkspace ref，避免循环依赖 ======
  const selectedWorkspaceRef = useRef<string>(selectedWorkspace);

  // 同步更新 selectedWorkspaceRef
  useEffect(() => {
    selectedWorkspaceRef.current = selectedWorkspace;
  }, [selectedWorkspace]);

  // P2-5: 区域导航
  const floorRef = useRef<HTMLDivElement>(null);
  const [activeZone, setActiveZone] = useState<ZoneId>('workstation');

  // P14: 当前部门初始化——优先 lockedDepartment，其次 props.department
  const [currentDept, setCurrentDept] = useState(lockedDepartment || department);

  // ====== IM 即时通讯状态 ======
  const [chatTarget, setChatTarget] = useState<ChatTarget>({ type: 'dept', id: lockedDepartment || department });
  const [imMessages, setImMessages] = useState<IMMessage[]>([]);
  const [imContacts, setImContacts] = useState<IMContactAPI[]>([]);
  const [imInputText, setImInputText] = useState('');
  const [imWsConnected, setImWsConnected] = useState(false);
  const [imContextMenu, setImContextMenu] = useState<IMContextMenuState>({ visible: false, x: 0, y: 0, messageId: null, isSelf: false });
  const imMessagesEndRef = useRef<HTMLDivElement>(null);

  // P14: lockedDepartment 变化时（重新登录切换身份）同步 currentDept
  useEffect(() => {
    if (lockedDepartment && lockedDepartment !== currentDept) {
      setCurrentDept(lockedDepartment);
    }
  }, [lockedDepartment]); // eslint-disable-line react-hooks/exhaustive-deps

  // P28: 像素员工点击提示（固定员工直连禁令）
  // 提示类型：info=蓝色（personal/human 可直连提示），warn=橙色（fixed 禁止直连提示）
  const [employeeToast, setEmployeeToast] = useState<{ msg: string; type: 'info' | 'warn' } | null>(null);
  const employeeToastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (!employeeToast) return;
    if (employeeToastTimerRef.current) clearTimeout(employeeToastTimerRef.current);
    employeeToastTimerRef.current = setTimeout(() => setEmployeeToast(null), 3000);
    return () => {
      if (employeeToastTimerRef.current) clearTimeout(employeeToastTimerRef.current);
    };
  }, [employeeToast]);

  // 部门列表
  const DEPARTMENTS = [
    { code: 'core', name: '核心层', icon: '🏢' },
    { code: 'tech', name: '技术部', icon: '🧠' },
    { code: 'hr', name: '人力资源', icon: '👥' },
    { code: 'finance', name: '财务部', icon: '💰' },
    { code: 'sales', name: '销售部', icon: '📈' },
    { code: 'cs', name: '客服部', icon: '🎧' },
    { code: 'admin', name: '行政部', icon: '🛠️' },
    { code: 'legal', name: '法务部', icon: '⚖️' },
    { code: 'ops', name: '运营部', icon: '🚚' },
  ];

  // P2-5: 滚动到指定区域
  const scrollToZone = useCallback((zoneId: ZoneId) => {
    setActiveZone(zoneId);
    const zoneEl = document.getElementById(`zone-${zoneId}`);
    if (zoneEl && floorRef.current) {
      zoneEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, []);

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

      // 并行获取：部门固定员工定义 + 部门 agents + 办公室状态快照
      const [defsRes, agentsRes, officeRes] = await Promise.allSettled([
        fetch(`${backendUrl}/api/fixed-employees/definitions/by-department/${currentDept}`, { headers }),
        fetch(`${backendUrl}/api/agents?department=${currentDept}`, { headers }),
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
      // P28: 固定员工定义一律视为 origin=fixed（AGENTS.md §5.3 禁止 /ws/agent 直连）
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
          // P28: 来自 /fixed-employees/definitions 一律 fixed
          origin: 'fixed' as EmployeeOrigin,
        };
      });

      // 补充 baseAgents 中的员工（人类员工、动态创建员工等）
      // 注意：固定员工已在上面从定义加载，这里只补充其他类型
      baseAgents.forEach((agent: any, i: number) => {
        const agentDept = agent.department || agent.dept || '';
        if (currentDept && agentDept && agentDept !== currentDept) {
          return; // 跳过非当前部门的 agent
        }
        // P28: 从 agent.origin 字段读取来源
        const rawOrigin = (agent.origin || '').toLowerCase();
        const empOrigin: EmployeeOrigin =
          rawOrigin === 'personal' ? 'personal' :
          rawOrigin === 'human' ? 'human' :
          rawOrigin === 'evolved' ? 'evolved' :
          'fixed';

        // 检查是否已在固定员工列表中（避免重复）
        // 注意：def.code 是简化 ID（如 code-reviewer），agent.id 是完整 ID（如 employee://digital/tech/code-reviewer/001）
        const agentId = agent.id || agent.agent_id || agent.code;
        const existsInList = list.some(e => {
          // 精确匹配或后缀匹配
          return e.id === agentId || agentId.endsWith('/' + e.id) || agentId.includes(e.id);
        });
        if (existsInList) return;

        list.push({
          id: agentId || `emp-${i}`,
          name: agent.name || agent.display_name || agent.title || '未知员工',
          title: agent.title || agent.role_description || '',
          status: (agent.status || 'idle').toLowerCase(),
          currentTask: agent.current_task || agent.currentTask || '',
          lastActiveAt: agent.last_active_at || agent.lastActiveAt || '',
          department: agentDept || currentDept,
          instanceNum: i,
          origin: empOrigin,
        });
      });

      // AGENTS.md §0: 办公室布局显示 FIXED + EVOLVED + HUMAN，不显示 PERSONAL（个人助理不是员工）
      setEmployees(list.filter(emp => emp.origin !== 'personal'));
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
    // 重置到部门大脑模式
    setChatTarget({ type: 'dept', id: currentDept });
  }, [currentDept]);

  // WebSocket 连接（带断线重连）
  useEffect(() => {
    if (!hasToken || !backendUrl) return;

    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
    let reconnectAttempts = 0;
    const MAX_RECONNECT_ATTEMPTS = 100; // 企业长连接：不轻易放弃（退避上限 60s）
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

      // forceChannel 模式：使用主进程 ws-client（支持 /ws/public 和 /ws/enterprise）
      if (forceChannel) {
        const params: Record<string, string> = {};
        if (conversationIdRef.current) params.conversationId = conversationIdRef.current;
        const result = await window.livingAgentAPI.ws.connect(forceChannel, params);
        if (result.success) {
          setConnected(true);
        }
        return null;
      }

      // 标准模式：使用自建 WebSocket 连接部门大脑
      if (!token) return;

      // 获取 clientId（用于 win_automation 工具控制本地电脑）
      const clientId = await window.livingAgentAPI.app.getClientId().catch(() => '');

      const wsUrl = backendUrl.replace(/^http/, 'ws');
      // 传递 token + conversationId + clientId
      // token 通过 URL 查询参数传递（不使用 Sec-WebSocket-Protocol，因为 Spring 的子协议
      // 匹配是严格相等，bearer.<token> 无法匹配注册的 bearer，导致 400 错误）
      const params = new URLSearchParams();
      params.set('token', token);
      if (conversationIdRef.current) params.set('conversationId', conversationIdRef.current);
      if (clientId) params.set('clientId', clientId);
      const ws = new WebSocket(`${wsUrl}/ws/dept/${currentDept}?${params.toString()}`);

      ws.onopen = () => {
        setConnected(true);
        wsRef.current = ws;
        reconnectAttempts = 0; // 重置重连计数
        // 心跳保活：每 25s 发送 ping，防止 NAT/防火墙清除空闲连接
        if (heartbeatTimer) clearInterval(heartbeatTimer);
        heartbeatTimer = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'ping' }));
          }
        }, 25000);
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);

          // 心跳 pong 响应，静默忽略
          if (data.type === 'pong' || data.type === 'PONG') return;

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
              // P5: 解析后端返回的 attachments（AI 回复的附件，如生成的文件）
              attachments: Array.isArray(data.attachments) && data.attachments.length > 0
                ? data.attachments as ChatAttachment[]
                : undefined,
            }]);
            setIsWaiting(false);
            // P7: AI 响应由主进程 ws-client.ts 自动转发到 Quick View，此处无需额外处理
          } else if (data.type === 'thinking') {
            // AI 思考中提示
            setIsWaiting(true);
          } else if (data.type === 'execution_progress') {
            // P8: Trace 可视化 - 更新执行步骤
            if (data.stage && data.status) {
              setTraceSteps(prev => {
                const existing = prev.findIndex(s => s.stage === data.stage);
                const newStep: TraceStepData = {
                  stage: data.stage,
                  status: data.status,
                  timestamp: data.timestamp || new Date().toISOString(),
                  message: data.message,
                  details: data.details
                };
                if (existing >= 0) {
                  const updated = [...prev];
                  updated[existing] = newStep;
                  return updated;
                }
                return [...prev, newStep];
              });
              setShowTrace(true);
            }
            // 执行进度更新 - 员工状态
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
          } else if (data.type === 'agent_dispatch') {
            // P9: 多 Agent 并行分派 - 初始化卡片列表
            if (Array.isArray(data.agents)) {
              const newCards: AgentCardData[] = data.agents.map((a: any) => ({
                agentId: a.agentId || a.id,
                agentName: a.agentName || a.name,
                department: a.department,
                task: a.task,
                status: 'pending',
                progress: 0,
                message: '等待执行'
              }));
              setAgentCards(newCards);
            }
          } else if (data.type === 'agent_progress') {
            // P9: Agent 进度更新
            if (data.agentId) {
              setAgentCards(prev => {
                const existing = prev.findIndex(c => c.agentId === data.agentId);
                const update: AgentCardData = {
                  agentId: data.agentId,
                  agentName: data.agentName || data.agentId,
                  department: data.department,
                  task: data.task,
                  status: data.status || 'running',
                  progress: data.progress || 0,
                  message: data.message,
                  startedAt: data.startedAt,
                  completedAt: data.completedAt
                };
                if (existing >= 0) {
                  const updated = [...prev];
                  updated[existing] = { ...updated[existing], ...update };
                  return updated;
                }
                return [...prev, update];
              });
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

      ws.onclose = (event) => {
        setConnected(false);
        wsRef.current = null;
        if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
        // Token 过期：服务端关闭码 4001，等待 token 刷新后快速重连
        if (!isManualClose && event.code === 4001) {
          reconnectAttempts = 0;
          reconnectTimer = setTimeout(() => connectWebSocket(), 1000);
          return;
        }
        // 自动重连（指数退避，上限 60s）
        if (!isManualClose && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
          const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 60000);
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
      if (heartbeatTimer) clearInterval(heartbeatTimer);
      if (ws) ws.close();
    };
  }, [backendUrl, currentDept, hasToken, currentUser]);

  // forceChannel 模式下监听主进程转发的消息
  useEffect(() => {
    if (!forceChannel) return;
    const off = window.livingAgentAPI.on('ws:message', (data: any) => {
      try {
        const msg = typeof data === 'string' ? JSON.parse(data) : data;
        if (msg.type === 'done') {
          if (msg.conversationId) setConversationId(msg.conversationId);
          setMessages(prev => [...prev, {
            messageId: msg.messageId,
            content: msg.content || '',
            userName: msg.brain || 'Assistant',
            timestamp: msg.timestamp || new Date().toISOString(),
            isSelf: false,
            role: 'assistant',
            // P5: 解析后端返回的 attachments（AI 回复的附件）
            attachments: Array.isArray(msg.attachments) && msg.attachments.length > 0
              ? msg.attachments as ChatAttachment[]
              : undefined,
          }]);
          setIsWaiting(false);
        } else if (msg.type === 'thinking') {
          setIsWaiting(true);
        }
      } catch (e) {
        console.warn('[OfficeChat] Failed to parse ws:message:', e);
      }
    });
    return () => off();
  }, [forceChannel]);

  // ====== IM WebSocket 连接与事件监听 ======
  useEffect(() => {
    if (!backendUrl || !hasToken) return;

    void (async () => {
      const token = await window.livingAgentAPI.auth.getToken();
      if (token) {
        imClient.connect(backendUrl, token);
      }
    })();

    const offConnect = imClient.on('connected', () => setImWsConnected(true));
    const offDisconnect = imClient.on('disconnected', () => setImWsConnected(false));

    // 监听新消息
    const offNewMessage = imClient.on('NEW_MESSAGE', (data: any) => {
      const senderId = data.senderId || '';
      const recipientId = data.recipientId || '';
      const contactId = senderId === currentUser?.id ? recipientId : senderId;
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
        self: senderId === currentUser?.id,
      };
      // 如果消息属于当前 IM 选中会话，追加到消息列表
      if (chatTarget.type === 'im' && contactId === chatTarget.id) {
        setImMessages(prev => [...prev, msg]);
        // 收到对方消息时发送 ACK
        if (!msg.self) {
          imClient.send({ type: 'ACK', messageId: msg.messageId });
        }
      }
      // 更新 IM 联系人列表的最后消息和未读数
      setImContacts(prev => prev.map(c =>
        c.contactId === contactId
          ? {
              ...c,
              lastMessageContent: msg.content,
              lastMessageTime: msg.createdAt,
              unreadCount: (chatTarget.type === 'im' && chatTarget.id === contactId) ? c.unreadCount : c.unreadCount + 1,
            }
          : c
      ));
    });

    // 监听消息撤回
    const offMessageRecalled = imClient.on('MESSAGE_RECALLED', (data: any) => {
      const recalledId = data.messageId;
      if (!recalledId) return;
      setImMessages(prev => prev.map(m =>
        m.messageId === recalledId ? { ...m, deletedAt: new Date().toISOString(), content: '[消息已撤回]' } : m
      ));
    });

    // 监听消息已读回执
    const offMessageAck = imClient.on('MESSAGE_ACK', (data: any) => {
      const ackedId = data.messageId;
      if (!ackedId) return;
      setImMessages(prev => prev.map(m =>
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
  }, [backendUrl, hasToken, currentUser?.id, chatTarget]);

  // 加载 IM 联系人列表
  useEffect(() => {
    if (!backendUrl || !hasToken) return;
    void (async () => {
      try {
        const apiList = await fetchImContacts(false);
        setImContacts(apiList);
      } catch (e) {
        console.warn('[OfficeChat] 加载 IM 联系人失败:', e);
      }
    })();
  }, [backendUrl, hasToken]);

  // 选中 IM 联系人时加载消息并发送 MARK_READ
  useEffect(() => {
    if (chatTarget.type !== 'im' || !backendUrl || !hasToken) return;
    void (async () => {
      try {
        const apiList = await fetchImMessages(chatTarget.id, undefined, 50);
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
          self: m.senderId === currentUser?.id,
        }));
        setImMessages(list);
      } catch (e) {
        console.warn('[OfficeChat] 加载 IM 消息失败:', e);
      }
    })();
    // 通过 WebSocket 标记已读
    imClient.send({ type: 'MARK_READ', contactId: chatTarget.id });
    // 清除本地未读计数
    setImContacts(prev => prev.map(c =>
      c.contactId === chatTarget.id ? { ...c, unreadCount: 0 } : c
    ));
  }, [chatTarget, backendUrl, hasToken, currentUser?.id]);

  // IM 消息自动滚动到底部
  useEffect(() => {
    if (chatTarget.type === 'im') {
      imMessagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [imMessages, chatTarget.type]);

  // IM 右键菜单：点击任意位置关闭
  useEffect(() => {
    if (!imContextMenu.visible) return;
    const handleClick = () => setImContextMenu(prev => ({ ...prev, visible: false }));
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, [imContextMenu.visible]);

  // P6: AI 唤起快捷键 — 聚焦输入框
  useEffect(() => {
    const off = window.livingAgentAPI.onFocusChatInput(() => {
      inputRef.current?.focus();
    });
    return () => off();
  }, []);

  // P6: 选中文本并提问 — 读取剪贴板并插入输入框
  useEffect(() => {
    const off = window.livingAgentAPI.onQuickAskWithSelection(async () => {
      try {
        // 尝试读取剪贴板文本
        const text = await navigator.clipboard.readText();
        if (text) {
          setInput(prev => prev ? `${prev}\n${text}` : text);
          inputRef.current?.focus();
        }
      } catch (e) {
        console.warn('[OfficeChat] Failed to read clipboard:', e);
      }
    });
    return () => off();
  }, []);

  // ====== 新增：加载个人 AI 助手列表 ======
  const loadAiAssistants = useCallback(async () => {
    if (!backendUrl || !hasToken) return;
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      
      // 从后端获取当前用户的个人 AI 助手列表
      const res = await fetch(`${backendUrl}/api/agents?origin=personal`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      
      if (res.ok) {
        const data = await res.json();
        // 双保险：后端 listEmployees 已按 origin 过滤，这里再按 origin 二次过滤，
        // 即使后端某次返回了非 personal（如 fixed/evolved/human），也不会误显到聊天对象选择区。
        const assistants = (data.data || data || [])
          .filter((agent: any) => agent.origin === 'personal')
          .map((agent: any) => ({
            id: agent.id,
            name: agent.name,
            type: 'ai' as const,
            icon: agent.icon || '🤖'
          }));
        setAiAssistants(assistants);
      }
    } catch (e) {
      console.warn('[OfficeChat] 加载个人 AI 助手列表失败:', e);
    }
  }, [backendUrl, hasToken]);

  // ====== 新增：加载已授权的工作空间列表 ======
  const loadAuthorizedWorkspaces = useCallback(async () => {
    try {
      // 从桌面端获取已授权的工作空间列表
      const workspaces = await window.livingAgentAPI.workspace.list();
      
      // 转换为前端需要的格式
      const formattedWorkspaces = workspaces.map((ws: any) => ({
        id: ws.id,
        name: ws.name,
        path: ws.path,
        scope: ws.scope || 'read'
      }));
      
      setAuthorizedWorkspaces(formattedWorkspaces);
      
      // 默认选择第一个工作空间（如果有）
      if (formattedWorkspaces.length > 0 && !selectedWorkspaceRef.current) {
        // 尝试恢复上次选择的 workspace
        const lastSelected = localStorage.getItem('office-chat-selected-workspace');
        if (lastSelected && formattedWorkspaces.some(w => w.id === lastSelected)) {
          setSelectedWorkspace(lastSelected);
        } else {
          setSelectedWorkspace(formattedWorkspaces[0].id);
        }
      }
    } catch (e) {
      console.warn('[OfficeChat] 加载工作空间列表失败:', e);
      // Fallback: 使用默认工作目录
      const defaultWorkspace = {
        id: 'default',
        name: '默认目录',
        path: process.env.HOME || process.env.USERPROFILE || '',
        scope: 'read-write' as const
      };
      setAuthorizedWorkspaces([defaultWorkspace]);
      if (!selectedWorkspaceRef.current) {
        setSelectedWorkspace('default');
      }
    }
  }, []);

  // P7: Quick View 消息转发 — 接收 Quick View 发来的消息，直接以显式 content 发送
  // 用 ref 持有最新 sendMessage，避免 useEffect([]) 捕获首帧闭包（connected/input 过期）
  const sendMessageRef = useRef(sendMessage);
  sendMessageRef.current = sendMessage;
  useEffect(() => {
    const off = window.livingAgentAPI.on('quickview:forward-message', (data: { content: string; attachments?: any[]; metadata?: any }) => {
      const qvMetadata: ChatMetadata = {
        source: 'quickview',
        clientTimestamp: Date.now(),
        ...data.metadata,
      };
      sendMessageRef.current(
        data.content,
        data.attachments && data.attachments.length > 0 ? (data.attachments as ChatAttachment[]) : undefined,
        qvMetadata
      );
    });
    return () => off();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // 初始加载
  useEffect(() => {
    loadEmployees();
    loadConversations();
    loadAiAssistants(); // 加载个人 AI 助手列表
    loadAuthorizedWorkspaces();
  }, [loadEmployees, loadConversations, loadAiAssistants, loadAuthorizedWorkspaces]);

  // ====== 新增：持久化用户选择的工作目录 ======
  useEffect(() => {
    if (selectedWorkspace) {
      localStorage.setItem('office-chat-selected-workspace', selectedWorkspace);
      
      // 更新最近使用的目录列表
      const workspace = authorizedWorkspaces.find(w => w.id === selectedWorkspace);
      if (workspace) {
        let recentWorkspaces: any[] = JSON.parse(localStorage.getItem('office-chat-recent-workspaces') || '[]');
        // 移除已有的相同目录，放到开头
        recentWorkspaces = recentWorkspaces.filter(w => w.id !== workspace.id);
        recentWorkspaces.unshift(workspace);
        // 保持最多 10 个最近使用的目录
        recentWorkspaces = recentWorkspaces.slice(0, 10);
        localStorage.setItem('office-chat-recent-workspaces', JSON.stringify(recentWorkspaces));
      }
    }
  }, [selectedWorkspace, authorizedWorkspaces]);

  // 发送消息
  // P5: 支持 attachments 和 metadata 字段（WebSocket 协议扩展）
  //   - attachments: 附件列表（P1 文件上传/P2 截图/P3 剪贴板会填充）
  //   - metadata: 元数据（source 标记消息来源，便于后端 Trace 和统计）
  // P7: 抽取 sendMessage，支持显式传入 content（Quick View 转发用，避免依赖 input 状态的闭包）
  function sendMessage(content: string, attachments?: ChatAttachment[], metadata?: ChatMetadata) {
    const trimmedInput = content.trim();
    if (!trimmedInput && (!attachments || attachments.length === 0)) return;
    if (!connected) return;

    // P5: 合并元数据，默认 source='manual'，记录客户端时间戳
    const finalMetadata: ChatMetadata = {
      source: 'manual',
      clientTimestamp: Date.now(),
      ...metadata,
    };

    setMessages(prev => [...prev, {
      content: trimmedInput,
      userId: currentUser?.id,
      userName: currentUser?.name || '我',
      timestamp: new Date().toISOString(),
      isSelf: true,
      role: 'user',
      attachments: attachments && attachments.length > 0 ? attachments : undefined,
      metadata: finalMetadata,
    }]);
    setIsWaiting(true);

    // ====== 新增：包含工作目录信息 ======
    const workspace = authorizedWorkspaces.find(w => w.id === selectedWorkspace);

    if (forceChannel) {
      // forceChannel 模式：通过主进程 ws-client 发送
      window.livingAgentAPI.ws.send('CHAT', {
        content: trimmedInput,
        conversationId: conversationId,
        recipientId: `brain_${lockedDepartment || currentDept}`, // 默认发送给部门大脑
        // P5: 协议扩展字段（后端兼容忽略未知字段）
        attachments: attachments && attachments.length > 0 ? attachments : undefined,
        metadata: finalMetadata,
        // ====== 新增：工作目录信息 ======
        workspace: workspace ? {
          id: workspace.id,
          path: workspace.path,
          name: workspace.name,
          scope: workspace.scope
        } : null,
      });
    } else if (wsRef.current) {
      wsRef.current.send(JSON.stringify({
        type: 'CHAT',
        content: trimmedInput,
        conversationId: conversationId,
        recipientId: `brain_${lockedDepartment || currentDept}`, // 默认发送给部门大脑
        // P5: 协议扩展字段（后端兼容忽略未知字段）
        attachments: attachments && attachments.length > 0 ? attachments : undefined,
        metadata: finalMetadata,
        // ====== 新增：工作目录信息 ======
        workspace: workspace ? {
          id: workspace.id,
          path: workspace.path,
          name: workspace.name,
          scope: workspace.scope
        } : null,
      }));
    }
    setInput('');
    // P1: 清空待发送附件
    setPendingAttachments([]);
  }

  // UI 发送入口：读取输入框内容
  function handleSend(attachments?: ChatAttachment[], metadata?: ChatMetadata) {
    sendMessage(input, attachments, metadata);
  }

  // P3: 剪贴板智能识别（图片/文件/URL）
  // P11: 粘贴内容时触发部门推荐
  const handlePaste = useCallback(async (e: React.ClipboardEvent) => {
    const clipboardData = e.clipboardData;
    if (!clipboardData) return;

    const items = clipboardData.items;
    const files: File[] = [];
    const texts: string[] = [];

    // 遍历剪贴板项
    for (const item of items) {
      if (item.kind === 'file') {
        const file = item.getAsFile();
        if (file) files.push(file);
      } else if (item.kind === 'string') {
        const text = clipboardData.getData('text');
        if (text) texts.push(text);
      }
    }

    // 处理图片/文件粘贴
    if (files.length > 0) {
      e.preventDefault();
      // 使用 FileUploader 的上传逻辑
      for (const file of files) {
        const attachment: ChatAttachment = {
          fileId: `paste-${Date.now()}-${Math.random().toString(36).slice(2)}`,
          type: file.type.startsWith('image/') ? 'image' : 'file',
          name: file.name || `粘贴文件.${file.type.split('/')[1] || 'bin'}`,
          size: file.size,
          url: URL.createObjectURL(file),
          thumbnailUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined,
        };
        setPendingAttachments(prev => [...prev, attachment]);

        // P11: 根据文件类型推荐部门
        const rec = recommendByFileType(file.name);
        if (rec) setDeptRecommendation(rec);
      }
      setEmployeeToast({ msg: `📎 已粘贴 ${files.length} 个文件`, type: 'info' });
      return;
    }

    // 处理文本粘贴（URL 识别）
    if (texts.length > 0) {
      const text = texts.join('\n');
      // 检查是否是 URL（简单判断）
      const urlPattern = /^https?:\/\/.+/i;
      if (urlPattern.test(text.trim())) {
        // 允许默认粘贴行为，但显示提示
        setEmployeeToast({ msg: `🔗 粘贴了链接`, type: 'info' });
      } else {
        // P11: 根据文本关键词推荐部门
        const rec = recommendByKeywords(text);
        if (rec) setDeptRecommendation(rec);
      }
      // 默认粘贴行为继续
    }
  }, [setEmployeeToast]);

  // ====== IM 消息发送 ======
  const handleImSend = useCallback(() => {
    if (!imInputText.trim() || chatTarget.type !== 'im') return;
    const content = imInputText.trim();
    imClient.send({
      type: 'SEND_MESSAGE',
      recipientId: chatTarget.id,
      content,
      messageType: 'TEXT',
    });
    // 乐观更新
    const optimisticMsg: IMMessage = {
      messageId: `local_${Date.now()}`,
      senderId: currentUser?.id || '',
      recipientId: chatTarget.id,
      content,
      type: 'TEXT',
      replyToId: null,
      createdAt: new Date().toISOString(),
      readAt: null,
      deletedAt: null,
      self: true,
    };
    setImMessages(prev => [...prev, optimisticMsg]);
    // 更新 IM 联系人列表最后消息
    setImContacts(prev => prev.map(c =>
      c.contactId === chatTarget.id
        ? { ...c, lastMessageContent: content, lastMessageTime: optimisticMsg.createdAt }
        : c
    ));
    setImInputText('');
  }, [imInputText, chatTarget, currentUser?.id]);

  const handleImKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleImSend();
    }
  }, [handleImSend]);

  // IM 右键菜单 — 撤回消息
  const handleImContextMenu = useCallback((e: React.MouseEvent, msg: IMMessage) => {
    e.preventDefault();
    setImContextMenu({
      visible: true,
      x: e.clientX,
      y: e.clientY,
      messageId: msg.messageId,
      isSelf: msg.self,
    });
  }, []);

  const handleImRecallMessage = useCallback(async () => {
    if (!imContextMenu.messageId) return;
    const msgId = imContextMenu.messageId;
    try {
      await recallMessageAPI(msgId);
      imClient.send({ type: 'RECALL_MESSAGE', messageId: msgId });
      setImMessages(prev => prev.map(m =>
        m.messageId === msgId ? { ...m, deletedAt: new Date().toISOString(), content: '[消息已撤回]' } : m
      ));
    } catch (e) {
      console.warn('[OfficeChat] IM 撤回消息失败:', e);
    }
    setImContextMenu(prev => ({ ...prev, visible: false }));
  }, [imContextMenu.messageId]);

  // IM 联系人操作
  const handleToggleImMuted = useCallback(async (contactId: string, muted: boolean) => {
    try {
      await setContactMuted(contactId, !muted);
      setImContacts(prev => prev.map(c =>
        c.contactId === contactId ? { ...c, muted: !muted } : c
      ));
    } catch (e) {
      console.warn('[OfficeChat] IM 设置免打扰失败:', e);
    }
  }, []);

  const handleToggleImPinned = useCallback(async (contactId: string, pinned: boolean) => {
    try {
      await setContactPinned(contactId, !pinned);
      setImContacts(prev => prev.map(c =>
        c.contactId === contactId ? { ...c, pinned: !pinned } : c
      ));
    } catch (e) {
      console.warn('[OfficeChat] IM 设置置顶失败:', e);
    }
  }, []);

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

  // P2-5: 按状态分组员工
  const employeesByStatus: Record<StatusId, Employee[]> = {
    online: [],
    busy: [],
    away: [],
    offline: [],
  };
  employees.forEach(emp => {
    employeesByStatus[getStatusId(emp.status)].push(emp);
  });

  return (
    <div className="office-chat-page">
      {/* P28: 像素员工点击提示 Toast（固定员工直连禁令） */}
      {employeeToast && (
        <div
          className={`employee-toast employee-toast--${employeeToast.type}`}
          role="status"
          aria-live="polite"
        >
          {employeeToast.msg}
        </div>
      )}

      {/* ========== 左侧：办公室房间 ========== */}
      <section className="office-room">
        <header className="office-room__header">
          <h2>🏢 {DEPARTMENTS.find(d => d.code === currentDept)?.name || currentDept} 办公室</h2>
          <select
            value={currentDept}
            onChange={(e) => setCurrentDept(e.target.value)}
            className={`dept-selector${lockedDepartment ? ' dept-locked' : ''}`}
            disabled={!!lockedDepartment}
            title={lockedDepartment ? '已锁定到本部门（权限受限）' : '切换部门'}
          >
            {DEPARTMENTS
              .filter(d => {
                // P1-8: 董事长/FULL可访问所有部门，其他仅限本部门
                // P14: lockedDepartment 设置时强制只显示锁定部门（虽然 disabled，但仍需正确渲染）
                if (lockedDepartment) return d.code === lockedDepartment;
                const isEnterpriseUser = currentUser?.accessLevel === 'FULL' || currentUser?.identity === 'INTERNAL_ENTERPRISE';
                return isEnterpriseUser || d.code === currentUser?.department;
              })
              .map(d => (
              <option key={d.code} value={d.code}>{d.icon} {d.name}</option>
            ))}
          </select>
        </header>

        {/* 办公室楼层 - 只有两个区域 */}
        <div className="office-floor" ref={floorRef}>
          {/* P2-5: 区域导航 */}
          <div className="zone-nav">
            {(['workstation', 'discussion'] as ZoneId[]).map(zoneId => (
              <button
                key={zoneId}
                className={`zone-nav__btn ${activeZone === zoneId ? 'zone-nav__btn--active' : ''}`}
                onClick={() => scrollToZone(zoneId)}
              >
                {ZONES[zoneId].icon} {ZONES[zoneId].title}
                <span className="zone-nav__badge">{employeesByZone[zoneId].length}</span>
              </button>
            ))}
          </div>

          {loadingEmployees ? (
            <div className="office-floor__loading">加载员工数据...</div>
          ) : (
            (['workstation', 'discussion'] as ZoneId[]).map(zoneId => (
              <div key={zoneId} id={`zone-${zoneId}`} className={`office-zone office-zone--${zoneId}`}>
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
                        origin={emp.origin}
                        onClick={(_id, origin) => {
                          // P28: 固定员工直连禁令（AGENTS.md §5.3）
                          // - origin=fixed → 禁止 /ws/agent 直连，提示走部门大脑（当前页面已连接 /ws/dept/{dept}）
                          // - origin=personal/human → 允许 /ws/agent 直连（本轮先做提示，独立 /ws/agent 通道待后续 P5/P6 实施）
                          if (origin === 'fixed') {
                            setEmployeeToast({
                              msg: `🔒 固定员工「${emp.name}」请通过部门大脑对话（当前已连接 /ws/dept/${currentDept}）`,
                              type: 'warn',
                            });
                          } else if (origin === 'personal') {
                            setEmployeeToast({
                              msg: `⭐ 个人助理「${emp.name}」支持 /ws/agent 直连（独立通道开发中）`,
                              type: 'info',
                            });
                          } else if (origin === 'human') {
                            setEmployeeToast({
                              msg: `👤 人类员工「${emp.name}」支持 /ws/agent 直连（独立通道开发中）`,
                              type: 'info',
                            });
                          }
                        }}
                      />
                    ))
                  )}
                </div>
              </div>
            ))
          )}
        </div>

        {/* P2-5: 状态统计列表 */}
        <div className="status-stats">
          {(['online', 'busy', 'away', 'offline'] as StatusId[]).map(statusId => (
            <div key={statusId} className="status-stats__item">
              <span className={`status-stats__dot status-stats__dot--${statusId}`}>●</span>
              <span className="status-stats__label">{STATUS_CONFIG[statusId].title}</span>
              <span className="status-stats__count">{employeesByStatus[statusId].length}</span>
            </div>
          ))}
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

      {/* ====== 中间：会话列表（脱离消息框，与办公室房间并列）====== */}
      <section className="conversation-list-panel">
        <header className="conversation-list-header">
          <h3>对话对象</h3>
          <span className={`im-status-dot ${imWsConnected ? 'im-status-dot--online' : 'im-status-dot--offline'}`} title={imWsConnected ? 'IM 在线' : 'IM 离线'}>●</span>
        </header>

        {/* 会话列表内容（可滚动） */}
        <div className="conversation-list">
          {/* 工作 AI - 部门大脑 */}
          <div
            className={`conversation-item ${chatTarget.type === 'dept' && chatTarget.id === currentDept ? 'conversation-item--active' : ''}`}
            onClick={() => setChatTarget({ type: 'dept', id: currentDept })}
            style={{ cursor: 'pointer' }}
          >
            <div className="conversation-item__icon">🏢</div>
            <div className="conversation-item__content">
              <div className="conversation-item__name">{DEPARTMENTS.find(d => d.code === (lockedDepartment || currentDept))?.name || ''} Brain</div>
              <div className="conversation-item__subtitle">部门大脑</div>
            </div>
          </div>

          {/* 同事列表（人类员工 origin='human'，通过 IM 对话） */}
          {employees.filter(emp => emp.origin === 'human').map(employee => {
            const imContact = imContacts.find(c => c.contactId === employee.id);
            const unreadCount = imContact?.unreadCount || 0;
            const lastMsg = imContact?.lastMessageContent || '';
            const lastTime = imContact?.lastMessageTime || '';
            const isPinned = imContact?.pinned || false;
            return (
              <div
                key={employee.id}
                className={`conversation-item ${chatTarget.type === 'im' && chatTarget.id === employee.id ? 'conversation-item--active' : ''}${isPinned ? ' conversation-item--pinned' : ''}`}
                onClick={() => setChatTarget({ type: 'im', id: employee.id, name: employee.name, origin: 'human' })}
                style={{ cursor: 'pointer' }}
              >
                <div className="conversation-item__icon">👤</div>
                <div className="conversation-item__content">
                  <div className="conversation-item__name">{employee.name}</div>
                  <div className="conversation-item__subtitle">{lastMsg ? (lastMsg.length > 20 ? lastMsg.slice(0, 20) + '...' : lastMsg) : '人类同事'}</div>
                </div>
                {lastTime && (
                  <span className="conversation-item__time">{new Date(lastTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</span>
                )}
                {unreadCount > 0 && (
                  <span className="conversation-item__unread">{unreadCount > 99 ? '99+' : unreadCount}</span>
                )}
              </div>
            );
          })}

          {/* AI 助手列表（个人助理 origin='personal'，通过 IM 对话） */}
          {aiAssistants.map(assistant => {
            const imContact = imContacts.find(c => c.contactId === assistant.id);
            const unreadCount = imContact?.unreadCount || 0;
            const lastMsg = imContact?.lastMessageContent || '';
            const lastTime = imContact?.lastMessageTime || '';
            const isPinned = imContact?.pinned || false;
            return (
              <div
                key={assistant.id}
                className={`conversation-item ${chatTarget.type === 'im' && chatTarget.id === assistant.id ? 'conversation-item--active' : ''}${isPinned ? ' conversation-item--pinned' : ''}`}
                onClick={() => setChatTarget({ type: 'im', id: assistant.id, name: assistant.name, origin: 'personal' })}
                style={{ cursor: 'pointer' }}
              >
                <div className="conversation-item__icon">{assistant.icon}</div>
                <div className="conversation-item__content">
                  <div className="conversation-item__name">{assistant.name}</div>
                  <div className="conversation-item__subtitle">{lastMsg ? (lastMsg.length > 20 ? lastMsg.slice(0, 20) + '...' : lastMsg) : 'AI 助手'}</div>
                </div>
                {lastTime && (
                  <span className="conversation-item__time">{new Date(lastTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</span>
                )}
                {unreadCount > 0 && (
                  <span className="conversation-item__unread">{unreadCount > 99 ? '99+' : unreadCount}</span>
                )}
              </div>
            );
          })}
        </div>

        {/* 底部统计信息 */}
        <div className="conversation-list-footer">
          <span className="conversation-list__count">
            {employees.filter(emp => emp.origin === 'human').length} 位人类 · {aiAssistants.length} 个 AI
          </span>
        </div>
      </section>

      {/* ========== 右侧：聊天区域 ========== */}
      <section className="chat-section">
        {chatTarget.type === 'dept' ? (
          <>
            {/* === 部门大脑对话模式（原有逻辑不变） === */}
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
                    {/* P4: 富文本消息渲染（Markdown + 代码块 + 表格 + 附件） */}
                    <div className="chat-message__content">
                      <MessageRenderer
                        content={msg.content}
                        attachments={msg.attachments}
                        isSelf={msg.isSelf}
                        maskSensitive={['finance', 'legal', 'hr'].includes(currentDept)}
                      />
                    </div>
                    {msg.timestamp && (
                      <span className="chat-message__time">
                        {new Date(msg.timestamp).toLocaleTimeString()}
                      </span>
                    )}
                    {/* P12: AI 回复消息上显示"记住这个"按钮 */}
                    {!msg.isSelf && (
                      <div className="chat-message__actions">
                        <RememberButton
                          messageContent={msg.content}
                          messageId={msg.messageId}
                          onRemember={handleRemember}
                        />
                      </div>
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

            {/* P8: Trace 可视化 */}
            {showTrace && traceSteps.length > 0 && (
              <div style={{ padding: '0 16px 12px' }}>
                <TraceVisualizer
                  steps={traceSteps}
                  onClose={() => setShowTrace(false)}
                />
              </div>
            )}

            {/* P9: 多 Agent 并行卡片 */}
            {agentCards.length > 0 && (
              <div style={{ padding: '0 16px 12px' }}>
                <AgentProgressCards
                  agents={agentCards}
                  onClose={() => setAgentCards([])}
                />
              </div>
            )}

            {/* P11: 部门路由推荐 */}
            {deptRecommendation && (
              <div style={{ padding: '0 16px 12px' }}>
                <DeptRecommendBanner
                  recommendation={deptRecommendation}
                  currentDept={currentDept}
                  onAccept={() => {
                    setCurrentDept(deptRecommendation.department);
                    setDeptRecommendation(null);
                  }}
                  onDismiss={() => setDeptRecommendation(null)}
                />
              </div>
            )}

            {/* P12: Memory 面板 */}
            {showMemoryPanel && hasToken && (
              <div style={{ position: 'absolute', right: 16, bottom: 80, zIndex: 1000 }}>
                <MemoryPanel
                  onClose={() => setShowMemoryPanel(false)}
                  onDelete={handleDeleteMemory}
                  backendUrl={backendUrl}
                  token={currentUser?.token}
                />
              </div>
            )}

            {/* P30: 部门特色快捷功能入口栏 */}
            <DeptQuickActions
              department={currentDept}
              onAction={({ prompt }) => {
                setInput(prompt);
                inputRef.current?.focus();
              }}
            />

            {/* 输入区域 */}
            <footer className="chat-input">
              {/* P10: 语音输入按钮（预留，登录态校验） */}
              <button
                className="btn btn-icon chat-input__voice"
                onClick={() => {
                  if (!hasToken) {
                    setEmployeeToast({ msg: '🎤 语音输入需要先登录', type: 'warn' });
                  } else if (currentUser?.accessLevel === 'CHAT_ONLY') {
                    setEmployeeToast({ msg: '🎤 当前身份（CHAT_ONLY）无语音权限', type: 'warn' });
                  } else {
                    setEmployeeToast({ msg: '🎤 语音输入功能开发中（P10）', type: 'info' });
                  }
                }}
                disabled={!connected || isWaiting}
                title={
                  !hasToken ? '语音输入需要先登录' :
                  currentUser?.accessLevel === 'CHAT_ONLY' ? '当前身份无语音权限' :
                  '语音输入（开发中）'
                }
                aria-label="语音输入"
              >
                🎤
              </button>
              {/* P12: 记忆库按钮 */}
              <button
                className="btn btn-icon chat-input__memory"
                onClick={() => setShowMemoryPanel(!showMemoryPanel)}
                disabled={!hasToken}
                title={hasToken ? '打开记忆库' : '记忆库需要先登录'}
                aria-label="记忆库"
              >
                🧠
              </button>
              {/* P1: 文件上传组件 */}
              <FileUploader
                backendUrl={backendUrl}
                onFilesSelected={(attachments) => {
                  setPendingAttachments(prev => [...prev, ...attachments]);
                }}
                onUploadError={(fileName, error) => {
                  setEmployeeToast({ msg: `📎 ${fileName}: ${error}`, type: 'warn' });
                }}
                disabled={!connected || isWaiting}
              />
              {/* P1: 待发送附件预览 */}
              <FilePreview
                attachments={pendingAttachments}
                onRemove={(index) => {
                  setPendingAttachments(prev => prev.filter((_, idx) => idx !== index));
                }}
              />
              <input
                ref={inputRef}
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend(pendingAttachments.length > 0 ? pendingAttachments : undefined)}
                onPaste={handlePaste}
                placeholder="输入消息...（Enter 发送，Ctrl+V 粘贴图片/文件）"
                disabled={!connected || isWaiting}
                className="chat-input__field"
              />
              <button
                className="btn btn-primary chat-input__send"
                onClick={() => handleSend(pendingAttachments.length > 0 ? pendingAttachments : undefined)}
                disabled={!connected || (!input.trim() && pendingAttachments.length === 0) || isWaiting}
              >
                发送
              </button>
              {/* P10: 语音输入按钮 */}
              <VoiceInputButton
                onTranscript={(text) => {
                  setInput(prev => prev ? `${prev} ${text}` : text);
                  inputRef.current?.focus();
                }}
                hasToken={hasToken}
                accessLevel={currentUser?.accessLevel}
                disabled={!connected}
              />
            </footer>

            {/* ====== 工作目录选择器 ====== */}
            <div className="workspace-selector-container">
              <select
                className="workspace-selector"
                value={selectedWorkspace || ''}
                onChange={async (e) => {
                  const value = e.target.value;
                  if (value === 'select') {
                    try {
                      const path = await window.livingAgentAPI.workspace.selectDirectory();
                      if (path) {
                        const workspaces = await window.livingAgentAPI.workspace.list();
                        const existing = workspaces.find((w: any) => w.path === path);
                        if (existing) {
                          setSelectedWorkspace(existing.id);
                        } else {
                          const newWorkspace = await window.livingAgentAPI.workspace.authorize({ path, scope: 'read-write' });
                          setSelectedWorkspace(newWorkspace.id);
                          loadAuthorizedWorkspaces();
                        }
                      }
                    } catch (err: any) {
                      console.error('[OfficeChat] 选择工作目录失败:', err);
                      alert(err.message || '选择工作目录失败');
                    }
                  } else if (value === 'cancel') {
                    setSelectedWorkspace('');
                  } else if (value.startsWith('recent-')) {
                    const workspaceId = value.replace('recent-', '');
                    const recentWorkspaces = JSON.parse(localStorage.getItem('office-chat-recent-workspaces') || '[]');
                    const workspace = recentWorkspaces.find((w: any) => w.id === workspaceId);
                    if (workspace) setSelectedWorkspace(workspace.id);
                  } else if (value === 'change') {
                    const path = await window.livingAgentAPI.workspace.selectDirectory();
                    if (path) {
                      const workspaces = await window.livingAgentAPI.workspace.list();
                      const existing = workspaces.find((w: any) => w.path === path);
                      if (existing) {
                        setSelectedWorkspace(existing.id);
                      } else {
                        const newWorkspace = await window.livingAgentAPI.workspace.authorize({ path, scope: 'read-write' });
                        setSelectedWorkspace(newWorkspace.id);
                        loadAuthorizedWorkspaces();
                      }
                    }
                  } else if (value === 'remove') {
                    try {
                      if (selectedWorkspace) {
                        await window.livingAgentAPI.workspace.revoke(selectedWorkspace);
                        setSelectedWorkspace('');
                        loadAuthorizedWorkspaces();
                      }
                    } catch (err: any) {
                      console.error('[OfficeChat] 移除工作目录失败:', err);
                      alert(err.message || '移除工作目录失败');
                    }
                  } else {
                    setSelectedWorkspace(value);
                  }
                }}
              >
                <option value="" disabled>请选择工作目录</option>
                <option value="select">📁 选择目录</option>
                {(() => {
                  const recentWorkspaces = JSON.parse(localStorage.getItem('office-chat-recent-workspaces') || '[]');
                  if (recentWorkspaces.length === 0) return null;
                  return (
                    <optgroup label="🕐 最近使用">
                      {recentWorkspaces.slice(0, 5).map((ws: any) => (
                        <option key={`recent-${ws.id}`} value={`recent-${ws.id}`}>
                          {ws.name} ({ws.scope === 'read-write' ? '读写' : '只读'})
                        </option>
                      ))}
                    </optgroup>
                  );
                })()}
                {authorizedWorkspaces.length === 0 && (
                  <option value="" disabled>暂无授权目录，请点击"选择目录"</option>
                )}
                {authorizedWorkspaces.map((workspace) => (
                  <option key={workspace.id} value={workspace.id}>
                    📂 {workspace.name} ({workspace.scope === 'read-write' ? '读写' : '只读'}) - {workspace.path}
                  </option>
                ))}
                {selectedWorkspace && (
                  <>
                    <optgroup label="⚙️ 操作">
                      <option value="change">🔄 更换目录</option>
                      <option value="remove">❌ 移除目录</option>
                      <option value="cancel">🚫 取消目录（使用默认目录）</option>
                    </optgroup>
                  </>
                )}
              </select>
            </div>
          </>
        ) : (
          <>
            {/* === IM 即时通讯对话模式 === */}
            {/* 顶栏：对话对象名称 + 置顶/免打扰按钮 */}
            <header className="chat-toolbar">
              <div className="chat-toolbar__left">
                <button
                  className="btn btn-icon"
                  onClick={() => setChatTarget({ type: 'dept', id: currentDept })}
                  title="返回部门大脑对话"
                >
                  ◀
                </button>
                <span className="chat-toolbar__title">
                  {chatTarget.name}
                </span>
              </div>
              <div className="chat-toolbar__right">
                {(() => {
                  const imContact = imContacts.find(c => c.contactId === chatTarget.id);
                  if (!imContact) return null;
                  return (
                    <>
                      <button
                        className="btn btn-icon"
                        title={imContact.pinned ? '取消置顶' : '置顶'}
                        onClick={() => handleToggleImPinned(imContact.contactId, imContact.pinned)}
                        style={{ fontSize: 12 }}
                      >
                        {imContact.pinned ? '📌' : '📍'}
                      </button>
                      <button
                        className="btn btn-icon"
                        title={imContact.muted ? '取消免打扰' : '免打扰'}
                        onClick={() => handleToggleImMuted(imContact.contactId, imContact.muted)}
                        style={{ fontSize: 12 }}
                      >
                        {imContact.muted ? '🔔' : '🔕'}
                      </button>
                    </>
                  );
                })()}
                <span className={`connection-status ${imWsConnected ? 'connected' : 'disconnected'}`}>
                  {imWsConnected ? '● IM在线' : '○ IM离线'}
                </span>
              </div>
            </header>

            {/* IM 消息列表 */}
            <div className="chat-messages">
              {imMessages.length === 0 ? (
                <div className="chat-messages__empty">
                  <p>开始与 {chatTarget.name} 对话</p>
                  <p className="hint">输入消息后按 Enter 发送</p>
                </div>
              ) : (
                imMessages.map(msg => (
                  <div
                    key={msg.messageId}
                    className={`chat-message ${msg.self ? 'chat-message--self' : 'chat-message--other'}${msg.deletedAt ? ' chat-message--recalled' : ''}`}
                    onContextMenu={(e) => handleImContextMenu(e, msg)}
                  >
                    <div className="chat-message__content">
                      {msg.deletedAt ? (
                        <span style={{ color: '#999', fontStyle: 'italic' }}>[消息已撤回]</span>
                      ) : (
                        msg.content
                      )}
                    </div>
                    <span className="chat-message__time">
                      {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''}
                      {msg.self && msg.readAt && <span style={{ marginLeft: 4, fontSize: 10, color: '#4caf50' }}>已读</span>}
                    </span>
                  </div>
                ))
              )}
              <div ref={imMessagesEndRef} />
            </div>

            {/* IM 输入区域 */}
            <footer className="chat-input">
              <textarea
                className="chat-input__field"
                placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                value={imInputText}
                onChange={e => setImInputText(e.target.value)}
                onKeyDown={handleImKeyDown}
                rows={1}
                disabled={!imWsConnected}
                style={{ resize: 'none', minHeight: 36, maxHeight: 120 }}
              />
              <button
                className="btn btn-primary chat-input__send"
                disabled={!imInputText.trim() || !imWsConnected}
                onClick={handleImSend}
              >
                发送
              </button>
            </footer>

            {/* IM 右键菜单 */}
            {imContextMenu.visible && (
              <div
                className="message-panel__context-menu"
                style={{ position: 'fixed', left: imContextMenu.x, top: imContextMenu.y, zIndex: 1000, background: '#fff', border: '1px solid #ddd', borderRadius: 4, boxShadow: '0 2px 8px rgba(0,0,0,0.15)' }}
              >
                {imContextMenu.isSelf && (
                  <div
                    style={{ padding: '8px 16px', cursor: 'pointer', fontSize: 13, whiteSpace: 'nowrap' }}
                    onClick={handleImRecallMessage}
                    onMouseEnter={e => (e.currentTarget.style.background = '#f5f5f5')}
                    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                  >
                    撤回消息
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}