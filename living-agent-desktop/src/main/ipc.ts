/**
 * IPC handlers 注册中心
 * - 暴露给 Preload 调用
 * - 每个 handler 必须 type-safe
 */
import { ipcMain, dialog, shell, app } from 'electron';
import { existsSync } from 'fs';
import { writeFile, readFile, mkdir } from 'fs/promises';
import { join, dirname } from 'path';
import { loadToken, saveToken, clearToken } from './auth';
import { getBackendUrl, setBackendUrl, loadBackendUrl, isBackendConfigured, getPublicTasks, claimTask, getMyCredits, listMyVisibleArtifacts, downloadArtifact } from './api-client';
import { loadConfig, saveConfig, getCachedConfig, resetCachedConfig } from './local-save-config';
import { localSaveSync, triggerSync, openLocalSaveFolder, getLocalSaveStats } from './local-save-sync';
import { refreshPendingCount, claimTopPriorityTask } from './task-board-tray';
import { showFloatingTaskBoard, hideFloatingTaskBoard, setFloatingExpanded } from './floating-task-board';
import { cacheVisibleTasks, loadCachedTasks, clearAllCache } from './task-board-cache';
import { setNotificationConfig, getNotificationConfig } from './task-notification';
import { notify } from './notifications';
import { showMainWindow, hideMainWindow } from './window';
import { getOrCreateClientId, getCachedClientId, resetClientId } from './client-id';

export function registerIpcHandlers(): void {
  // ============ 后端连接 ============
  ipcMain.handle('backend:check', async () => {
    const url = getBackendUrl();
    if (!url) return { ok: false, url: '', error: '后端地址未配置' };
    try {
      const res = await fetch(`${url}/api/health`, { method: 'GET' });
      return { ok: res.ok, url };
    } catch (e) {
      return { ok: false, url, error: String(e) };
    }
  });

  ipcMain.handle('backend:get-url', async () => getBackendUrl());

  ipcMain.handle('backend:is-configured', async () => isBackendConfigured());

  ipcMain.handle('backend:set-url', async (_e, url: string) => {
    const saved = await setBackendUrl(url);
    return saved;
  });

  // ============ 鉴权 ============
  ipcMain.handle('auth:get-token', async () => loadToken());
  ipcMain.handle('auth:set-token', async (_e, token: string) => {
    await saveToken(token);
  });
  ipcMain.handle('auth:clear-token', async () => {
    await clearToken();
  });

  // ============ 文件系统 ============
  ipcMain.handle('fs:open-artifact', async (_e, filePath: string) => {
    if (!existsSync(filePath)) return false;
    await shell.openPath(filePath);
    return true;
  });

  ipcMain.handle('fs:show-in-folder', async (_e, filePath: string) => {
    shell.showItemInFolder(filePath);
  });

  // ============ OS 通知 ============
  ipcMain.handle('notify', async (_e, title: string, body: string) => {
    notify({ title, body });
  });

  // ============ 本地保存 ============
  ipcMain.handle('localsave:get-config', async () => {
    return loadConfig();
  });

  ipcMain.handle('localsave:set-config', async (_e, cfg) => {
    await saveConfig(cfg);
    return cfg;
  });

  ipcMain.handle('localsave:choose-path', async () => {
    const result = await dialog.showOpenDialog({
      title: '选择本地产物保存路径',
      properties: ['openDirectory', 'createDirectory']
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    return result.filePaths[0];
  });

  ipcMain.handle('localsave:open-folder', async () => {
    openLocalSaveFolder();
  });

  ipcMain.handle('localsave:sync', async () => {
    return triggerSync();
  });

  ipcMain.handle('localsave:stats', async () => {
    return getLocalSaveStats();
  });

  // ============ 任务栏 ============
  ipcMain.handle('taskboard:pending-count', async () => {
    return refreshPendingCount();
  });

  ipcMain.handle('taskboard:refresh', async () => {
    return refreshPendingCount();
  });

  ipcMain.handle('taskboard:claim-top', async () => {
    return claimTopPriorityTask();
  });

  ipcMain.handle('taskboard:cache', async () => {
    const tasks = await getPublicTasks();
    await cacheVisibleTasks(tasks);
    return tasks;
  });

  ipcMain.handle('taskboard:load-cache', async (_e, dept?: string) => {
    return loadCachedTasks(dept);
  });

  ipcMain.handle('taskboard:clear-cache', async () => {
    await clearAllCache();
  });

  ipcMain.handle('taskboard:notification-config:get', async () => {
    return getNotificationConfig();
  });

  ipcMain.handle('taskboard:notification-config:set', async (_e, cfg) => {
    setNotificationConfig(cfg);
  });

  // ============ 任务中心悬浮窗 ============
  ipcMain.handle('floating:show', async () => {
    showFloatingTaskBoard();
  });

  ipcMain.handle('floating:hide', async () => {
    hideFloatingTaskBoard();
  });

  ipcMain.handle('floating:set-expanded', async (_e, expanded: boolean) => {
    setFloatingExpanded(expanded);
  });

  // ============ 产物 API ============
  ipcMain.handle('artifacts:my-visible', async (_e, params) => {
    return listMyVisibleArtifacts(params);
  });

  ipcMain.handle('artifacts:download', async (_e, artifactId: string) => {
    const data = await downloadArtifact(artifactId);
    return data.toString('base64');
  });

  // ============ 窗口控制 ============
  ipcMain.handle('window:minimize-to-tray', async () => {
    hideMainWindow();
  });

  ipcMain.handle('window:show', async () => {
    showMainWindow();
  });

  ipcMain.handle('window:quit', async () => {
    (app as any).isQuitting = true;
    app.quit();
  });

  // ============ 应用信息 ============
  ipcMain.handle('app:version', async () => app.getVersion());
  ipcMain.handle('app:platform', async () => process.platform);
  ipcMain.handle('app:user-data-path', async () => app.getPath('userData'));

  // ============ 客户端标识 ============
  ipcMain.handle('app:client-id', async () => {
    const info = await getOrCreateClientId();
    return info.clientId;
  });

  ipcMain.handle('app:client-info', async () => {
    return getOrCreateClientId();
  });

  ipcMain.handle('app:reset-client-id', async () => {
    const info = await resetClientId();
    return info.clientId;
  });
}

export function unregisterIpcHandlers(): void {
  ipcMain.removeAllListeners();
}
