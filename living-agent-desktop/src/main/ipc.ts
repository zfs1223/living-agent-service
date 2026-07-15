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
import { getBackendUrl, setBackendUrl, loadBackendUrl, isBackendConfigured, getPublicTasks, claimTask, getMyCredits, listMyVisibleArtifacts, downloadArtifact, sendSmsCode, phoneLogin, getCurrentUser, getApprovalList, getApprovalDetail, approveApproval, rejectApproval, cancelApproval, getMessages, markMessageRead, markAllMessagesRead, getUnreadCount, listAgents, getAgent, startAgent, stopAgent, listInterventions, respondIntervention, escalateIntervention, listSkills, browseSkills, bindSkill, unbindSkill, getProactiveDigest, listHabits, listProactiveNotifications, listPosts, createPost, likePost, getPlazaStats } from './api-client';
import { loadConfig, saveConfig, getCachedConfig, resetCachedConfig } from './local-save-config';
import { localSaveSync, triggerSync, openLocalSaveFolder, getLocalSaveStats } from './local-save-sync';
import { refreshPendingCount, claimTopPriorityTask } from './task-board-tray';
import { showFloatingTaskBoard, hideFloatingTaskBoard, setFloatingExpanded } from './floating-task-board';
import { cacheVisibleTasks, loadCachedTasks, clearAllCache } from './task-board-cache';
import { setNotificationConfig, getNotificationConfig } from './task-notification';
import { notify } from './notifications';
import { showMainWindow, hideMainWindow } from './window';
import { getOrCreateClientId, getCachedClientId, resetClientId } from './client-id';
import { winAutomationService } from './win-automation-service';
import { wsClient } from './ws-client';

const IPC_CHANNELS: readonly string[] = [
  'backend:check', 'backend:get-url', 'backend:is-configured', 'backend:set-url',
  'auth:get-token', 'auth:set-token', 'auth:clear-token', 'auth:sms-send', 'auth:phone-login', 'auth:me',
  'fs:open-artifact', 'fs:show-in-folder',
  'notify',
  'localsave:get-config', 'localsave:set-config', 'localsave:choose-path', 'localsave:open-folder', 'localsave:sync', 'localsave:stats',
  'taskboard:pending-count', 'taskboard:refresh', 'taskboard:claim-top', 'taskboard:cache', 'taskboard:load-cache', 'taskboard:clear-cache', 'taskboard:list', 'taskboard:claim', 'taskboard:notification-config:get', 'taskboard:notification-config:set',
  'floating:show', 'floating:hide', 'floating:set-expanded',
  'artifacts:my-visible', 'artifacts:download', 'artifacts:save',
  'credits:get-balance',
  'window:minimize-to-tray', 'window:show', 'window:quit',
  'app:version', 'app:platform', 'app:user-data-path', 'app:client-id', 'app:client-info', 'app:reset-client-id',
  'win-automation:start', 'win-automation:stop', 'win-automation:status', 'win-automation:execute',
  'approval:list', 'approval:detail', 'approval:approve', 'approval:reject', 'approval:cancel',
  'message:list', 'message:mark-read', 'message:mark-all-read', 'message:unread-count',
  'ws:connect', 'ws:disconnect', 'ws:switch-channel', 'ws:status', 'ws:send',
  'agent:list', 'agent:get', 'agent:start', 'agent:stop',
  'intervention:list', 'intervention:respond', 'intervention:escalate',
  'skill:list', 'skill:browse', 'skill:bind', 'skill:unbind',
  'proactive:digest', 'proactive:habits', 'proactive:notifications',
  'plaza:posts', 'plaza:create', 'plaza:like', 'plaza:stats'
];

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

  // ============ 手机号登录（与 frontend 对齐） ============
  ipcMain.handle('auth:sms-send', async (_e, phone: string, type: string = 'login') => {
    return sendSmsCode(phone, type);
  });

  ipcMain.handle('auth:phone-login', async (_e, phone: string, code: string) => {
    const result = await phoneLogin(phone, code);
    // 登录成功后通知渲染层
    return result;
  });

  // 声纹登录：接收渲染进程传来的录音Buffer
  ipcMain.handle('auth:voiceprint-login', async (_e, audioBuffer: ArrayBuffer) => {
    const blob = new Blob([audioBuffer], { type: 'audio/webm' });
    const { voicePrintLogin } = await import('./api-client');
    return voicePrintLogin(blob);
  });

  // 声纹服务状态
  ipcMain.handle('auth:voiceprint-status', async () => {
    const { getVoicePrintStatus } = await import('./api-client');
    return getVoicePrintStatus();
  });

  ipcMain.handle('auth:me', async () => {
    return getCurrentUser();
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
    // 同步工作目录到后端（将本地路径映射为容器路径）
    if (cfg.basePath) {
      try {
        const backendUrl = getBackendUrl();
        const token = await loadToken();
        // 容器内路径固定为 /app/user-workspace（与 docker-compose.yml 映射对应）
        const containerPath = '/app/user-workspace';
        await fetch(`${backendUrl}/api/v1/system/workspace/config`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
          },
          body: JSON.stringify({ root: containerPath })
        });
        console.log('[ipc] Workspace root synced to backend:', containerPath);
      } catch (e) {
        console.warn('[ipc] Failed to sync workspace root to backend:', e);
      }
    }
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

  ipcMain.handle('taskboard:list', async (_e, dept?: string) => {
    return getPublicTasks(dept);
  });

  ipcMain.handle('taskboard:claim', async (_e, taskId: string) => {
    try {
      const result = await claimTask(taskId);
      return { ok: true, data: result };
    } catch (e: any) {
      return { ok: false, error: e.message || String(e) };
    }
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

  ipcMain.handle('artifacts:save', async (_e, { artifactId, fileName }: { artifactId: string; fileName: string }) => {
    const data = await downloadArtifact(artifactId);
    const { filePath, canceled } = await dialog.showSaveDialog({
      title: '保存产物',
      defaultPath: fileName,
      filters: [{ name: '所有文件', extensions: ['*'] }]
    });
    if (canceled || !filePath) return { saved: false };
    await writeFile(filePath, data);
    return { saved: true, path: filePath };
  });

  // ============ 积分余额 ============
  ipcMain.handle('credits:get-balance', async () => {
    return getMyCredits();
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

  // ============ Windows 自动化 ============
  ipcMain.handle('win-automation:start', async () => {
    try {
      await winAutomationService.start();
      return { success: true, running: winAutomationService.isRunning() };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  ipcMain.handle('win-automation:stop', async () => {
    winAutomationService.stop();
    return { success: true };
  });

  ipcMain.handle('win-automation:status', async () => {
    return { running: winAutomationService.isRunning() };
  });

  ipcMain.handle('win-automation:execute', async (_e, operation: string, args?: Record<string, any>) => {
    try {
      const result = await winAutomationService.execute(operation, args ?? {});
      return { success: true, result };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  // ============ 审批 ============
  ipcMain.handle('approval:list', async (_e, status?: string) => {
    return getApprovalList(status);
  });

  ipcMain.handle('approval:detail', async (_e, id: string) => {
    return getApprovalDetail(id);
  });

  ipcMain.handle('approval:approve', async (_e, id: string, stepId: string, comment?: string) => {
    return approveApproval(id, stepId, comment);
  });

  ipcMain.handle('approval:reject', async (_e, id: string, stepId: string, comment?: string) => {
    return rejectApproval(id, stepId, comment);
  });

  ipcMain.handle('approval:cancel', async (_e, id: string) => {
    return cancelApproval(id);
  });

  // ============ 消息 ============
  ipcMain.handle('message:list', async (_e, limit?: number) => {
    return getMessages(limit);
  });

  ipcMain.handle('message:mark-read', async (_e, id: string) => {
    return markMessageRead(id);
  });

  ipcMain.handle('message:mark-all-read', async () => {
    return markAllMessagesRead();
  });

  ipcMain.handle('message:unread-count', async () => {
    return getUnreadCount();
  });

  // ============ WebSocket 通道管理 ============
  ipcMain.handle('ws:connect', async (_e, path: string, params?: Record<string, string>) => {
    try {
      await wsClient.connect(path, params ?? {});
      return { success: true, channel: wsClient.getCurrentChannel() };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  ipcMain.handle('ws:disconnect', async () => {
    wsClient.disconnect();
    return { success: true };
  });

  ipcMain.handle('ws:switch-channel', async (_e, path: string, params?: Record<string, string>) => {
    try {
      await wsClient.switchChannel(path, params ?? {});
      return { success: true, channel: wsClient.getCurrentChannel() };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  ipcMain.handle('ws:status', async () => {
    return {
      connected: wsClient.isConnected(),
      channel: wsClient.getCurrentChannel(),
    };
  });

  ipcMain.handle('ws:send', async (_e, type: string, data: any) => {
    wsClient.send(type, data);
    return { success: true };
  });

  // ============ Agent 管理 (P1-1) ============
  ipcMain.handle('agent:list', async () => {
    return listAgents();
  });

  ipcMain.handle('agent:get', async (_e, id: string) => {
    return getAgent(id);
  });

  ipcMain.handle('agent:start', async (_e, id: string) => {
    return startAgent(id);
  });

  ipcMain.handle('agent:stop', async (_e, id: string) => {
    return stopAgent(id);
  });

  // ============ 干预决策 (P1-2) ============
  ipcMain.handle('intervention:list', async (_e, status?: string) => {
    return listInterventions(status);
  });

  ipcMain.handle('intervention:respond', async (_e, id: string, action: string, comment?: string) => {
    return respondIntervention(id, action, comment);
  });

  ipcMain.handle('intervention:escalate', async (_e, id: string, reason: string) => {
    return escalateIntervention(id, reason);
  });

  // ============ 技能管理 (P1-3) ============
  ipcMain.handle('skill:list', async () => {
    return listSkills();
  });

  ipcMain.handle('skill:browse', async (_e, section: string, params?: Record<string, string>) => {
    return browseSkills(section, params ?? {});
  });

  ipcMain.handle('skill:bind', async (_e, agentId: string, skillId: string) => {
    return bindSkill(agentId, skillId);
  });

  ipcMain.handle('skill:unbind', async (_e, agentId: string, skillId: string) => {
    return unbindSkill(agentId, skillId);
  });

  // ============ 主动服务 (P1-4) ============
  ipcMain.handle('proactive:digest', async () => {
    return getProactiveDigest();
  });

  ipcMain.handle('proactive:habits', async () => {
    return listHabits();
  });

  ipcMain.handle('proactive:notifications', async () => {
    return listProactiveNotifications();
  });

  // ============ 广场 (P1-5) ============
  ipcMain.handle('plaza:posts', async (_e, params?: Record<string, string>) => {
    return listPosts(params ?? {});
  });

  ipcMain.handle('plaza:create', async (_e, data: { title: string; content: string; tags?: string[] }) => {
    return createPost(data);
  });

  ipcMain.handle('plaza:like', async (_e, postId: string) => {
    return likePost(postId);
  });

  ipcMain.handle('plaza:stats', async () => {
    return getPlazaStats();
  });
}

export function unregisterIpcHandlers(): void {
  for (const channel of IPC_CHANNELS) {
    ipcMain.removeHandler(channel);
  }
}
