/**
 * 主进程 + 渲染进程共享类型
 * 跨边界传递的对象必须在此定义
 */

export const APP_NAME = 'Living Agent';

/**
 * 后端默认地址
 *
 * 注意：留空表示"未配置"。生产环境装到客户端 PC 后，必须在"设置"中填入真实的远程后端 URL
 * （如 https://api.living-agent.example.com），并持久化到 userData/backend-config.json
 *
 * 设计原因：避免使用 http://localhost:8382 这类本地地址给生产用户造成误连。
 * 首次启动渲染层会检测"未配置"状态，强制进入"设置"页引导配置。
 */
export const DEFAULT_BACKEND_URL = '';
export const DEFAULT_BACKEND_WS = '';

/* ============ 用户/鉴权 ============ */

/** 桌面端当前登录用户（与 frontend User 对齐） */
export interface DesktopUser {
  id: string;
  name: string;
  email: string;
  avatar: string | null;
  department: string | null;
  identity: string;
  accessLevel: string;
  role: 'platform_admin' | 'org_admin' | 'agent_admin' | 'member';
  founder: boolean;
  tenantId: string;
}

export interface AuthSession {
  token: string;
  userId: string;
  expiresAt?: number;
}

export interface UserContext {
  id: string;
  displayName: string;
  departmentId: string | null;
  isDepartmentLeader: boolean;
  isChairman: boolean;
  accessLevel: 'CHAT_ONLY' | 'LIMITED' | 'DEPARTMENT' | 'FULL';
}

/* ============ 产物 ============ */

export type Visibility = 'PRIVATE' | 'DEPARTMENT' | 'PUBLIC' | 'RESTRICTED';

export interface ArtifactRecord {
  id: string;
  executionId?: string;
  taskId?: string;
  employeeCode?: string;
  department?: string;
  path: string;
  name: string;
  type: string;
  size: number;
  createdAt: string;
  createdBy?: string;
  participantIds?: string[];
  visibility: Visibility;
  viewerDepartments?: string[];
  visibleToLeader: boolean;
}

export interface LocalArtifactEntry {
  executionId: string;
  fileName: string;
  sourcePath: string;
  localPath: string;
  sha256: string;
  size: number;
  copiedAt: string;
}

/* ============ 任务栏 ============ */

export type Difficulty = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT' | 'MASTER';

export interface PublicTask {
  taskId: string;
  taskType: string;
  description: string;
  priority: number;
  requiredCapability: string;
  difficulty: Difficulty;
  estimatedHours: number;
  reward: number;
  department?: string;
  createdAt: string;
  visibility?: Visibility;
}

export interface TaskNotificationConfig {
  enabled: boolean;
  minPriority: number;
  departments: string[];
  difficultyFilter: Difficulty[];
  quietHours: {
    enabled: boolean;
    start: string;     // "22:00"
    end: string;       // "08:00"
  };
  autoClaim: boolean;
}

/* ============ 本地保存 ============ */

export interface LocalSaveConfig {
  enabled: boolean;
  basePath: string;
  scopes: {
    artifacts: boolean;
    conversations: boolean;
    receipts: boolean;
    screenshots: boolean;
  };
  syncStrategy: 'local-only' | 'cloud-sync';
  capacity: {
    maxBytes: number;
    retentionDays: number;
  };
}

export interface LocalSaveStats {
  totalBytes: number;
  fileCount: number;
  lastSyncAt: string | null;
  oldestFileAt: string | null;
}

export interface SavedInfo {
  path: string;
  size: number;
  scope: keyof LocalSaveConfig['scopes'];
  savedAt: string;
}

/* ============ IPC 事件 ============ */

export type IpcChannel =
  | 'backend:check'
  | 'backend:get-url'
  | 'backend:set-url'
  | 'auth:get-token'
  | 'auth:set-token'
  | 'auth:clear-token'
  | 'fs:open-artifact'
  | 'fs:show-in-folder'
  | 'notify'
  | 'localsave:get-config'
  | 'localsave:set-config'
  | 'localsave:choose-path'
  | 'localsave:open-folder'
  | 'localsave:sync'
  | 'localsave:stats'
  | 'taskboard:pending-count'
  | 'taskboard:refresh'
  | 'floating:show'
  | 'floating:hide'
  | 'window:minimize-to-tray'
  | 'window:show'
  | 'window:quit'
  | 'app:version'
  | 'app:platform'
  | 'win-automation:start'
  | 'win-automation:stop'
  | 'win-automation:status'
  | 'win-automation:execute';

export type IpcEvent =
  | 'localsave:saved'
  | 'taskboard:new-task'
  | 'taskboard:count-changed'
  | 'backend:status-changed'
  | 'auth:changed';

/* ============ Windows 自动化 ============ */

/**
 * Windows 自动化操作类型（与后端 WindowsAutomationTool.OPERATION_PERMISSIONS 对齐）
 * 详细说明见 docs/WINDOWS_MCP_INTEGRATION_PLAN.md §2.1
 */
export type WinAutomationOperation =
  // UIA 控件操作
  | 'click' | 'type' | 'scroll' | 'move' | 'shortcut' | 'snapshot' | 'screenshot'
  // 条件等待
  | 'wait' | 'wait_for'
  // PowerShell
  | 'shell'
  // 进程管理
  | 'process_list' | 'process_kill'
  // 注册表
  | 'registry_get' | 'registry_set' | 'registry_delete' | 'registry_list'
  // 文件系统
  | 'filesystem_read' | 'filesystem_write' | 'filesystem_copy' | 'filesystem_move'
  | 'filesystem_delete' | 'filesystem_list' | 'filesystem_search' | 'filesystem_info'
  // 剪贴板
  | 'clipboard_get' | 'clipboard_set'
  // 其他
  | 'notification' | 'scrape'
  // 虚拟桌面
  | 'vdm_switch' | 'vdm_create' | 'vdm_move_window';

/** Windows 自动化执行结果 */
export interface WinAutomationResult {
  success: boolean;
  result?: unknown;
  error?: string;
}

/** Windows 自动化服务状态 */
export interface WinAutomationStatus {
  running: boolean;
}
