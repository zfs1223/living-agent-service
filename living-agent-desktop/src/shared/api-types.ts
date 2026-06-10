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
  ArtifactRecord
} from './types';

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

  /* ============ 事件订阅 ============ */
  on: (channel: string, callback: (data: any) => void) => () => void;
  onLocalSaveSaved: (cb: (info: SavedInfo) => void) => () => void;
  onTaskBoardCountChanged: (cb: (info: { count: number }) => void) => () => void;
  onNewTask: (cb: (data: any) => void) => () => void;
  onAuthChanged: (cb: (info: { hasToken: boolean }) => void) => () => void;
  onNavigate: (cb: (path: string) => void) => () => void;
  onBackendStatusChanged: (cb: (info: BackendStatusInfo) => void) => () => void;
}
