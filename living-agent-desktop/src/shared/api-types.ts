/**
 * 桌面端 IPC API 类型契约
 * - 由 preload/index.ts 暴露的 window.livingAgentAPI 类型来源
 * - 主进程 / 渲染进程共享，避免跨目录 import 引起 rootDir/include 边界问题
 */
import type {
  LocalSaveConfig,
  LocalSaveStats,
  SavedInfo,
  PublicTask,
  TaskNotificationConfig,
  ArtifactRecord,
  DesktopUser
} from './types';

// 重新导出 DesktopUser，方便渲染层直接从 @shared/api-types 导入
export type { DesktopUser };

/** 员工来源类型 */
export type EmployeeOrigin = 'fixed' | 'personal' | 'human' | 'evolved';

export interface ClientInfo {
  clientId: string;
  hostname: string;
  platform: string;
  osUser: string;
  appVersion: string;
  createdAt: string;
}

export interface BackendStatusInfo {
  status: 'online' | 'offline';
  url: string;
}

export interface CreditBalance {
  balance: number;
  userId?: string;
  updatedAt?: string;
}

/** 公司选项（多公司登录选择） */
export interface TenantOption {
  tenantId: string;
  departmentName: string;
  employeeName: string;
  founder: boolean;
}

/** 多公司登录选择结果 */
export interface TenantSelectionResult {
  status: 'tenant_required';
  phone: string;
  tenants: TenantOption[];
}

/** 登录成功结果 */
export interface LoginSuccessResult {
  accessToken: string;
  user: DesktopUser;
}

/** 手机验证码登录响应（联合类型） */
export type PhoneLoginResponse = LoginSuccessResult | TenantSelectionResult;

/** 密码登录响应（联合类型） */
export type PasswordLoginResponse = LoginSuccessResult | TenantSelectionResult;

export interface LivingAgentAPI {
  /* ============ 后端连接 ============ */
  checkBackend: () => Promise<{ ok: boolean; url: string; error?: string }>;
  getBackendUrl: () => Promise<string>;
  setBackendUrl: (url: string) => Promise<string>;
  isBackendConfigured: () => Promise<boolean>;

  /* ============ 鉴权 ============ */
  auth: {
    getToken: () => Promise<string | null>;
    setToken: (token: string) => Promise<void>;
    clearToken: () => Promise<void>;
    // 手机号登录（与 frontend 对齐）
    smsSend: (phone: string, type?: string) => Promise<{ success: boolean; message: string; expiresIn: number; code?: string }>;
    // 手机验证码登录（返回联合类型：成功 | 需选择公司）
    phoneLogin: (phone: string, code: string) => Promise<PhoneLoginResponse>;
    // 手机号 + 密码登录（返回联合类型：成功 | 需选择公司）
    passwordLogin: (phone: string, password: string) => Promise<PasswordLoginResponse>;
    // 多公司选择后完成登录（手机验证码）
    phoneLoginWithTenant: (phone: string, code: string, tenantId: string) => Promise<LoginSuccessResult>;
    // 多公司选择后完成登录（密码）
    loginWithTenant: (phone: string, password: string, tenantId: string) => Promise<LoginSuccessResult>;
    // 修改当前用户密码
    changePassword: (oldPassword: string, newPassword: string) => Promise<{ success: boolean }>;
    me: () => Promise<DesktopUser>;
    // 声纹登录
    voicePrintLogin: (audioBuffer: ArrayBuffer) => Promise<{ accessToken: string; user: DesktopUser }>;
    voicePrintStatus: () => Promise<{ available: boolean; message?: string }>;
  };

  /* ============ 文件系统 ============ */
  openArtifact: (path: string) => Promise<boolean>;
  showInFolder: (path: string) => Promise<void>;

  /* ============ OS 通知 ============ */
  notify: (title: string, body: string) => Promise<void>;

  /* ============ 本地保存 ============ */
  localSave: {
    getConfig: () => Promise<LocalSaveConfig>;
    setConfig: (cfg: LocalSaveConfig) => Promise<LocalSaveConfig>;
    choosePath: () => Promise<string | null>;
    openFolder: () => Promise<void>;
    triggerSync: () => Promise<{ savedCount: number; removedCount: number; skipped: number }>;
    getStats: () => Promise<LocalSaveStats>;
  };

  /* ============ 任务栏 ============ */
  taskBoard: {
    getPendingCount: () => Promise<number>;
    refresh: () => Promise<number>;
    claimTop: () => Promise<PublicTask | null>;
    cache: () => Promise<PublicTask[]>;
    loadCache: (dept?: string) => Promise<PublicTask[]>;
    clearCache: () => Promise<void>;
    list: (dept?: string) => Promise<PublicTask[]>;
    claim: (taskId: string) => Promise<unknown>;
    getNotificationConfig: () => Promise<TaskNotificationConfig>;
    setNotificationConfig: (cfg: TaskNotificationConfig) => Promise<void>;
  };

  /* ============ 积分 ============ */
  credits: {
    getBalance: () => Promise<CreditBalance>;
  };

  /* ============ 任务中心悬浮窗 ============ */
  floating: {
    show: () => Promise<void>;
    hide: () => Promise<void>;
    setExpanded: (expanded: boolean) => Promise<void>;
  };

  /* ============ 产物 ============ */
  artifacts: {
    myVisible: (params?: { page?: number; size?: number }) => Promise<ArtifactRecord[]>;
    download: (artifactId: string) => Promise<string>;
    save: (artifactId: string, fileName: string) => Promise<{ saved: boolean; path?: string }>;
  };

  /* ============ 窗口控制 ============ */
  window: {
    minimizeToTray: () => Promise<void>;
    show: () => Promise<void>;
    quit: () => Promise<void>;
  };

  /* ============ 应用信息 ============ */
  app: {
    getVersion: () => Promise<string>;
    getPlatform: () => Promise<NodeJS.Platform>;
    getUserDataPath: () => Promise<string>;
    getClientId: () => Promise<string>;
    getClientInfo: () => Promise<ClientInfo>;
    resetClientId: () => Promise<string>;
  };

  /* ============ Windows 自动化 ============ */
  winAutomation: {
    start: () => Promise<{ success: boolean; running?: boolean; error?: string }>;
    stop: () => Promise<{ success: boolean }>;
    status: () => Promise<{ running: boolean }>;
    execute: (operation: string, args?: Record<string, unknown>) =>
      Promise<{ success: boolean; result?: unknown; error?: string }>;
  };

  /* ============ 习惯录制 ============ */
  recorder: {
    start: (config: { targetApp: string; noteMode: string }) => Promise<{ success: boolean; error?: string }>;
    stop: () => Promise<{ success: boolean; result?: unknown; error?: string }>;
    pause: () => Promise<{ success: boolean }>;
    resume: () => Promise<{ success: boolean }>;
    status: () => Promise<{
      recording: boolean;
      paused: boolean;
      stepCount: number;
      pendingNoteIndex: number | null;
      steps: any[];
    }>;
    setNote: (index: number, text: string) => Promise<{ success: boolean }>;
    skipNote: () => Promise<{ success: boolean }>;
  };

  /* ============ 审批 ============ */
  approval: {
    list: (status?: string) => Promise<any[]>;
    detail: (id: string) => Promise<any>;
    approve: (id: string, stepId: string, comment?: string) => Promise<any>;
    reject: (id: string, stepId: string, comment?: string) => Promise<any>;
    cancel: (id: string) => Promise<any>;
  };

  /* ============ 消息 ============ */
  message: {
    list: (limit?: number) => Promise<any[]>;
    markRead: (id: string) => Promise<void>;
    markAllRead: () => Promise<void>;
    unreadCount: () => Promise<number>;
  };

  /* ============ WebSocket 通道管理 ============ */
  ws: {
    connect: (path: string, params?: Record<string, string>) => Promise<{ success: boolean; channel?: string; error?: string }>;
    disconnect: () => Promise<{ success: boolean }>;
    switchChannel: (path: string, params?: Record<string, string>) => Promise<{ success: boolean; channel?: string; error?: string }>;
    status: () => Promise<{ connected: boolean; channel: string }>;
    send: (type: string, data: any) => Promise<{ success: boolean }>;
  };

  /* ============ Agent 管理 (P1-1) ============ */
  agent: {
    list: () => Promise<any[]>;
    get: (id: string) => Promise<any>;
    start: (id: string) => Promise<any>;
    stop: (id: string) => Promise<any>;
    create: (data: { name: string; role_description?: string; agent_type?: string; department?: string; skill_ids?: string[]; primary_model_id?: string; fallback_model_id?: string }) => Promise<any>;
    delete: (id: string) => Promise<any>;
  };

  model: {
    list: () => Promise<any[]>;
  };

  /* ============ 干预决策 (P1-2) ============ */
  intervention: {
    list: (status?: string) => Promise<any[]>;
    respond: (id: string, action: string, comment?: string) => Promise<any>;
    escalate: (id: string, reason: string) => Promise<any>;
  };

  /* ============ 技能管理 (P1-3) ============ */
  skill: {
    list: () => Promise<any[]>;
    browse: (section: string, params?: Record<string, string>) => Promise<any>;
    bind: (agentId: string, skillId: string) => Promise<any>;
    unbind: (agentId: string, skillId: string) => Promise<any>;
  };

  /* ============ 主动服务 (P1-4) ============ */
  proactive: {
    digest: () => Promise<any>;
    habits: () => Promise<any[]>;
    notifications: () => Promise<any[]>;
  };

  /* ============ 广场 (P1-5) ============ */
  plaza: {
    posts: (params?: Record<string, string>) => Promise<any[]>;
    create: (data: { title: string; content: string; tags?: string[] }) => Promise<any>;
    like: (postId: string) => Promise<any>;
    stats: () => Promise<any>;
  };

  /* ============ 事件订阅 ============ */
  on: (channel: string, callback: (data: any) => void) => () => void;
  onLocalSaveSaved: (cb: (info: SavedInfo) => void) => () => void;
  onTaskBoardCountChanged: (cb: (info: { count: number }) => void) => () => void;
  onNewTask: (cb: (data: any) => void) => () => void;
  onAuthChanged: (cb: (info: { hasToken: boolean }) => void) => () => void;
  onNavigate: (cb: (path: string) => void) => () => void;
  onBackendStatusChanged: (cb: (info: BackendStatusInfo) => void) => () => void;

  /* ============ P6: 快捷键事件 ============ */
  onFocusChatInput: (cb: () => void) => () => void;
  onQuickAskWithSelection: (cb: () => void) => () => void;

  /* ============ P10: 语音输入事件 ============ */
  onVoiceInputToggle: (cb: () => void) => () => void;

  /* ============ P8: 工作目录管理 (Workspace) ============ */
  workspace: {
    list: () => Promise<Array<{
      id: string;
      path: string;
      name: string;
      authorizedAt: string;
      scope: 'read' | 'read-write';
    }>>;
    authorize: (data: { path: string; name?: string; scope?: 'read' | 'read-write' }) => Promise<{
      id: string;
      path: string;
      name: string;
      authorizedAt: string;
      scope: 'read' | 'read-write';
    }>;
    revoke: (id: string) => Promise<any[]>;
    selectDirectory: () => Promise<string | null>;
  };

  /* ============ 习惯录制事件 ============ */
  onRecorderStatus: (cb: (info: { recording: boolean; paused: boolean; stepCount: number }) => void) => () => void;
  onRecorderStep: (cb: (step: any) => void) => () => void;
  onRecorderNoteRequest: (cb: (info: { index: number; operation: string; suggestion: string }) => void) => () => void;
  onRecorderResult: (cb: (result: any) => void) => () => void;
  onRecorderError: (cb: (info: { message: string }) => void) => () => void;
}
