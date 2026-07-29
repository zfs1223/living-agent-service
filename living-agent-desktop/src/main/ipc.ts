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
import { getBackendUrl, setBackendUrl, loadBackendUrl, isBackendConfigured, getPublicTasks, claimTask, getMyCredits, listMyVisibleArtifacts, downloadArtifact, sendSmsCode, phoneLogin, passwordLogin, changePassword, getCurrentUser, getApprovalList, getApprovalDetail, approveApproval, rejectApproval, cancelApproval, getMessages, markMessageRead, markAllMessagesRead, getUnreadCount, listAgents, getAgent, startAgent, stopAgent, createAgent, listModels, listInterventions, respondIntervention, escalateIntervention, listSkills, browseSkills, bindSkill, unbindSkill, getProactiveDigest, listHabits, listProactiveNotifications, listPosts, createPost, likePost, getPlazaStats } from './api-client';
import { loadConfig, saveConfig, getCachedConfig, resetCachedConfig } from './local-save-config';
import { localSaveSync, triggerSync, openLocalSaveFolder, getLocalSaveStats } from './local-save-sync';
import { refreshPendingCount, claimTopPriorityTask } from './task-board-tray';
import { showFloatingTaskBoard, hideFloatingTaskBoard, setFloatingExpanded } from './floating-task-board';
import { cacheVisibleTasks, loadCachedTasks, clearAllCache } from './task-board-cache';
import { setNotificationConfig, getNotificationConfig } from './task-notification';
import { notify } from './notifications';
import { showMainWindow, hideMainWindow, getMainWindow } from './window';
import { getOrCreateClientId, getCachedClientId, resetClientId } from './client-id';
import { winAutomationService } from './win-automation-service';
import { operationRecorder } from './recorder-controller';
import { wsClient } from './ws-client';
import { showQuickView, hideQuickView, toggleQuickView, setQuickViewTyping, getQuickViewWindow } from './quick-view/quick-view-window';
import { forwardMessageToMainWindow, forwardResponseToQuickView, syncDepartmentToQuickView, pushProactiveNotification, triggerScreenshot } from './quick-view/quick-view-controller';

const IPC_CHANNELS: readonly string[] = [
  'backend:check', 'backend:get-url', 'backend:is-configured', 'backend:set-url',
  'auth:get-token', 'auth:set-token', 'auth:clear-token', 'auth:sms-send', 'auth:phone-login', 'auth:password-login', 'auth:phone-login-with-tenant', 'auth:login-with-tenant', 'auth:change-password', 'auth:me',
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
  'recorder:start', 'recorder:stop', 'recorder:pause', 'recorder:resume', 'recorder:status', 'recorder:set-note', 'recorder:skip-note',
  'approval:list', 'approval:detail', 'approval:approve', 'approval:reject', 'approval:cancel',
  'message:list', 'message:mark-read', 'message:mark-all-read', 'message:unread-count',
  'ws:connect', 'ws:disconnect', 'ws:switch-channel', 'ws:status', 'ws:send',
  'agent:list', 'agent:get', 'agent:start', 'agent:stop', 'agent:create', 'agent:update', 'agent:delete',
  'model:list',
  'intervention:list', 'intervention:respond', 'intervention:escalate',
  'skill:list', 'skill:browse', 'skill:bind', 'skill:unbind',
  'proactive:digest', 'proactive:habits', 'proactive:notifications',
  'plaza:posts', 'plaza:create', 'plaza:like', 'plaza:stats',
  // P2: 截图工具
  'screenshot:capture-full', 'screenshot:capture-region', 'screenshot:apply-crop', 'screenshot:save-temp', 'screenshot:open-editor', 'screenshot:close-editor',
  // P7: Quick View 悬浮对话
  'quickview:show', 'quickview:hide', 'quickview:toggle', 'quickview:send', 'quickview:set-typing', 'quickview:switch-department', 'quickview:trigger-screenshot', 'quickview:open-in-main-window',
  // P8: 工作目录管理 (Workspace)
  'workspace:list', 'workspace:authorize', 'workspace:revoke', 'workspace:select-directory',
  // P10: 语音输入
  'voice:start-recording', 'voice:stop-recording',
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

  // 手机号 + 密码登录（可能需要选择公司）
  ipcMain.handle('auth:password-login', async (_e, phone: string, password: string) => {
    return passwordLogin(phone, password);
  });

  // 手机验证码 + 选择公司后登录
  ipcMain.handle('auth:phone-login-with-tenant', async (_e, phone: string, code: string, tenantId: string) => {
    const { phoneLoginWithTenant: fn } = await import('./api-client');
    return fn(phone, code, tenantId);
  });

  // 密码 + 选择公司后登录
  ipcMain.handle('auth:login-with-tenant', async (_e, phone: string, password: string, tenantId: string) => {
    const { loginWithTenant: fn } = await import('./api-client');
    return fn(phone, password, tenantId);
  });

  // 修改当前用户密码
  ipcMain.handle('auth:change-password', async (_e, oldPassword: string, newPassword: string) => {
    return changePassword(oldPassword, newPassword);
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

  // ============ 操作录制（习惯录制） ============
  ipcMain.handle('recorder:start', async (_e, config: { target_app: string; note_mode: string }) => {
    try {
      const result = await operationRecorder.start(config as { target_app: string; note_mode: 'all' | 'key' | 'summary' });
      return result;
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  ipcMain.handle('recorder:stop', async () => {
    try {
      const result = await operationRecorder.stop();
      return { success: true, result };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  ipcMain.handle('recorder:pause', async () => {
    operationRecorder.pause();
    return { success: true };
  });

  ipcMain.handle('recorder:resume', async () => {
    operationRecorder.resume();
    return { success: true };
  });

  ipcMain.handle('recorder:status', async () => {
    return {
      recording: operationRecorder.isRecording,
      paused: operationRecorder.isPaused,
      stepCount: operationRecorder.stepCount,
      pendingNoteIndex: operationRecorder.pendingNoteIndex,
      steps: operationRecorder.currentSteps
    };
  });

  ipcMain.handle('recorder:set-note', async (_e, index: number, text: string) => {
    operationRecorder.setNote(index, text);
    return { success: true };
  });

  ipcMain.handle('recorder:skip-note', async () => {
    operationRecorder.skipNote();
    return { success: true };
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

  ipcMain.handle('agent:create', async (_e, data: { name: string; role_description?: string; agent_type?: string; department?: string; skill_ids?: string[]; primary_model_id?: string; fallback_model_id?: string }) => {
    return createAgent(data);
  });

  ipcMain.handle('agent:update', async (_e, id: string, data: { name?: string; role_description?: string; primary_model_id?: string; skill_ids?: string[] }) => {
    return updateAgent(id, data);
  });

  ipcMain.handle('agent:delete', async (_e, id: string) => {
    return deleteAgent(id);
  });

  ipcMain.handle('model:list', async () => {
    return listModels();
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
  ipcMain.handle('skill:list', async (_e, personalAssistant?: boolean) => {
    return listSkills(personalAssistant);
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

  // ============ P7: Quick View 悬浮对话 ============
  ipcMain.handle('quickview:show', async () => {
    showQuickView();
  });

  ipcMain.handle('quickview:hide', async () => {
    hideQuickView();
  });

  ipcMain.handle('quickview:toggle', async () => {
    toggleQuickView();
  });

  ipcMain.handle('quickview:send', async (_e, data: { content: string; attachments?: any[]; metadata?: any }) => {
    // 转发到主窗口 OfficeChatPage
    forwardMessageToMainWindow(data);
  });

  ipcMain.handle('quickview:set-typing', async (_e, typing: boolean) => {
    setQuickViewTyping(typing);
  });

  ipcMain.handle('quickview:switch-department', async (_e, department: string) => {
    // 同步部门切换到主窗口
    const mainWindow = getMainWindow();
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('quickview:switch-department', { department });
    }
    // 同步到 Quick View
    syncDepartmentToQuickView({ department, locked: false });
  });

  ipcMain.handle('quickview:trigger-screenshot', async () => {
    triggerScreenshot();
  });

  ipcMain.handle('quickview:open-in-main-window', async () => {
    // 显示主窗口并跳转到聊天页面
    showMainWindow();
    const mainWindow = getMainWindow();
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('navigate', '/chat');
    }
  });

  // ============ P8: 工作目录管理 (Workspace) ============
  const WORKSPACES_FILE = join(app.getPath('userData'), 'workspaces.json');

  // 初始化 workspaces.json（使用立即执行函数支持 await）
  (async () => {
    try {
      await mkdir(dirname(WORKSPACES_FILE), { recursive: true });
      if (!existsSync(WORKSPACES_FILE)) {
        await writeFile(WORKSPACES_FILE, JSON.stringify([], null, 2));
      }
    } catch (e) {
      console.error('[ipc] Failed to initialize workspaces file:', e);
    }
  })();

  ipcMain.handle('workspace:list', async () => {
    try {
      const data = await readFile(WORKSPACES_FILE, 'utf-8');
      return JSON.parse(data) || [];
    } catch (e) {
      console.error('[workspace:list] Failed to read workspaces:', e);
      return [];
    }
  });

  ipcMain.handle('workspace:authorize', async (_e, data: { path: string; name?: string; scope?: 'read' | 'read-write' }) => {
    try {
      let workspaces: any[] = [];
      try {
        const existing = await readFile(WORKSPACES_FILE, 'utf-8');
        workspaces = JSON.parse(existing) || [];
      } catch {
        workspaces = [];
      }

      // 检查是否已存在
      if (workspaces.some(w => w.path === data.path)) {
        throw new Error('该目录已授权');
      }

      const newWorkspace = {
        id: `ws-${Date.now()}`,
        path: data.path,
        name: data.name || data.path.split(/[\\/]/).pop() || 'workspace',
        authorizedAt: new Date().toISOString(),
        scope: data.scope || 'read'
      };

      workspaces.push(newWorkspace);
      await writeFile(WORKSPACES_FILE, JSON.stringify(workspaces, null, 2));
      return newWorkspace;
    } catch (e: any) {
      console.error('[workspace:authorize] Failed to authorize workspace:', e);
      throw e;
    }
  });

  ipcMain.handle('workspace:revoke', async (_e, id: string) => {
    try {
      let workspaces: any[] = [];
      try {
        const existing = await readFile(WORKSPACES_FILE, 'utf-8');
        workspaces = JSON.parse(existing) || [];
      } catch {
        workspaces = [];
      }

      const filtered = workspaces.filter(w => w.id !== id);
      await writeFile(WORKSPACES_FILE, JSON.stringify(filtered, null, 2));
      return filtered;
    } catch (e: any) {
      console.error('[workspace:revoke] Failed to revoke workspace:', e);
      throw e;
    }
  });

  ipcMain.handle('workspace:select-directory', async () => {
    const mainWindow = getMainWindow();
    if (!mainWindow || mainWindow.isDestroyed()) {
      return null;
    }

    const result = await dialog.showOpenDialog(mainWindow, {
      properties: ['openDirectory'],
      title: '选择工作目录'
    });

    if (!result.canceled && result.filePaths.length > 0) {
      return result.filePaths[0];
    }
    return null;
  });
}

export function unregisterIpcHandlers(): void {
  for (const channel of IPC_CHANNELS) {
    ipcMain.removeHandler(channel);
  }
}
