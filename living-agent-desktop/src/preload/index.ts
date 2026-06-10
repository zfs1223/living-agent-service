/**
 * Preload 脚本
 * - 暴露 window.livingAgentAPI
 * - 严格类型安全（contextBridge）
 */
import { contextBridge, ipcRenderer } from 'electron';
import type {
  LocalSaveConfig,
  LocalSaveStats,
  SavedInfo,
  PublicTask,
  TaskNotificationConfig,
  ArtifactRecord
} from '../shared/types';

const api = {
  /* ============ 后端连接 ============ */
  checkBackend: () => ipcRenderer.invoke('backend:check') as Promise<{ ok: boolean; url: string; error?: string }>,
  getBackendUrl: () => ipcRenderer.invoke('backend:get-url') as Promise<string>,
  setBackendUrl: (url: string) => ipcRenderer.invoke('backend:set-url', url) as Promise<string>,
  isBackendConfigured: () => ipcRenderer.invoke('backend:is-configured') as Promise<boolean>,

  /* ============ 鉴权 ============ */
  auth: {
    getToken: () => ipcRenderer.invoke('auth:get-token') as Promise<string | null>,
    setToken: (token: string) => ipcRenderer.invoke('auth:set-token', token) as Promise<void>,
    clearToken: () => ipcRenderer.invoke('auth:clear-token') as Promise<void>
  },

  /* ============ 文件系统 ============ */
  openArtifact: (path: string) => ipcRenderer.invoke('fs:open-artifact', path) as Promise<boolean>,
  showInFolder: (path: string) => ipcRenderer.invoke('fs:show-in-folder', path) as Promise<void>,

  /* ============ OS 通知 ============ */
  notify: (title: string, body: string) => ipcRenderer.invoke('notify', title, body) as Promise<void>,

  /* ============ 本地保存 ============ */
  localSave: {
    getConfig: () => ipcRenderer.invoke('localsave:get-config') as Promise<LocalSaveConfig>,
    setConfig: (cfg: LocalSaveConfig) => ipcRenderer.invoke('localsave:set-config', cfg) as Promise<LocalSaveConfig>,
    choosePath: () => ipcRenderer.invoke('localsave:choose-path') as Promise<string | null>,
    openFolder: () => ipcRenderer.invoke('localsave:open-folder') as Promise<void>,
    triggerSync: () => ipcRenderer.invoke('localsave:sync') as Promise<{ savedCount: number; removedCount: number; skipped: number }>,
    getStats: () => ipcRenderer.invoke('localsave:stats') as Promise<LocalSaveStats>
  },

  /* ============ 任务栏 ============ */
  taskBoard: {
    getPendingCount: () => ipcRenderer.invoke('taskboard:pending-count') as Promise<number>,
    refresh: () => ipcRenderer.invoke('taskboard:refresh') as Promise<number>,
    claimTop: () => ipcRenderer.invoke('taskboard:claim-top') as Promise<PublicTask | null>,
    cache: () => ipcRenderer.invoke('taskboard:cache') as Promise<PublicTask[]>,
    loadCache: (dept?: string) => ipcRenderer.invoke('taskboard:load-cache', dept) as Promise<PublicTask[]>,
    clearCache: () => ipcRenderer.invoke('taskboard:clear-cache') as Promise<void>,
    // 桌面端独立：拉取最新 + 按 ID 接取（不依赖 web 端组件）
    list: (dept?: string) => ipcRenderer.invoke('taskboard:list', dept) as Promise<PublicTask[]>,
    claim: (taskId: string) => ipcRenderer.invoke('taskboard:claim', taskId) as Promise<any>,
    getNotificationConfig: () => ipcRenderer.invoke('taskboard:notification-config:get') as Promise<TaskNotificationConfig>,
    setNotificationConfig: (cfg: TaskNotificationConfig) =>
      ipcRenderer.invoke('taskboard:notification-config:set', cfg) as Promise<void>
  },

  /* ============ 积分（桌面端独立，不共享 web 端 services） ============ */
  credits: {
    getBalance: () => ipcRenderer.invoke('credits:get-balance') as Promise<{ balance: number; userId?: string; updatedAt?: string }>
  },

  /* ============ 任务中心悬浮窗 ============ */
  floating: {
    show: () => ipcRenderer.invoke('floating:show') as Promise<void>,
    hide: () => ipcRenderer.invoke('floating:hide') as Promise<void>,
    setExpanded: (expanded: boolean) => ipcRenderer.invoke('floating:set-expanded', expanded) as Promise<void>
  },

  /* ============ 产物 ============ */
  artifacts: {
    myVisible: (params?: { page?: number; size?: number }) =>
      ipcRenderer.invoke('artifacts:my-visible', params) as Promise<ArtifactRecord[]>,
    download: (artifactId: string) =>
      ipcRenderer.invoke('artifacts:download', artifactId) as Promise<string>
  },

  /* ============ 窗口控制 ============ */
  window: {
    minimizeToTray: () => ipcRenderer.invoke('window:minimize-to-tray') as Promise<void>,
    show: () => ipcRenderer.invoke('window:show') as Promise<void>,
    quit: () => ipcRenderer.invoke('window:quit') as Promise<void>
  },

  /* ============ 应用信息 ============ */
  app: {
    getVersion: () => ipcRenderer.invoke('app:version') as Promise<string>,
    getPlatform: () => ipcRenderer.invoke('app:platform') as Promise<NodeJS.Platform>,
    getUserDataPath: () => ipcRenderer.invoke('app:user-data-path') as Promise<string>,
    // 客户端唯一标识：安装时生成并持久化，所有 HTTP/WS 请求都自动携带
    getClientId: () => ipcRenderer.invoke('app:client-id') as Promise<string>,
    getClientInfo: () =>
      ipcRenderer.invoke('app:client-info') as Promise<{
        clientId: string;
        hostname: string;
        platform: string;
        osUser: string;
        appVersion: string;
        createdAt: string;
      }>,
    resetClientId: () => ipcRenderer.invoke('app:reset-client-id') as Promise<string>
  },

  /* ============ 事件订阅 ============ */
  on: (channel: string, callback: (data: any) => void) => {
    const wrapped = (_: unknown, data: any) => callback(data);
    ipcRenderer.on(channel, wrapped);
    return () => ipcRenderer.removeListener(channel, wrapped);
  },

  onLocalSaveSaved: (cb: (info: SavedInfo) => void) => {
    const wrapped = (_: unknown, info: SavedInfo) => cb(info);
    ipcRenderer.on('localsave:saved', wrapped);
    return () => ipcRenderer.removeListener('localsave:saved', wrapped);
  },

  onTaskBoardCountChanged: (cb: (info: { count: number }) => void) => {
    const wrapped = (_: unknown, info: { count: number }) => cb(info);
    ipcRenderer.on('taskboard:count-changed', wrapped);
    return () => ipcRenderer.removeListener('taskboard:count-changed', wrapped);
  },

  onNewTask: (cb: (data: any) => void) => {
    const wrapped = (_: unknown, data: any) => cb(data);
    ipcRenderer.on('taskboard:new-task', wrapped);
    return () => ipcRenderer.removeListener('taskboard:new-task', wrapped);
  },

  onAuthChanged: (cb: (info: { hasToken: boolean }) => void) => {
    const wrapped = (_: unknown, info: { hasToken: boolean }) => cb(info);
    ipcRenderer.on('auth:changed', wrapped);
    return () => ipcRenderer.removeListener('auth:changed', wrapped);
  },

  onNavigate: (cb: (path: string) => void) => {
    const wrapped = (_: unknown, p: string) => cb(p);
    ipcRenderer.on('navigate', wrapped);
    return () => ipcRenderer.removeListener('navigate', wrapped);
  },

  onBackendStatusChanged: (cb: (info: { status: 'online' | 'offline'; url: string }) => void) => {
    const wrapped = (_: unknown, info: any) => cb(info);
    ipcRenderer.on('backend:status-changed', wrapped);
    return () => ipcRenderer.removeListener('backend:status-changed', wrapped);
  }
};

contextBridge.exposeInMainWorld('livingAgentAPI', api);
