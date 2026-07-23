/**
 * 应用入口（Electron 主进程）
 * 启动流程：连接检测 → 窗口创建 → 托盘 → 菜单 → IPC → 事件订阅
 *
 * 详细参考：HERMES_COMPARISON_AND_BORROWING_PLAN.md §3.5
 */
import { app, BrowserWindow } from 'electron';
import { createMainWindow } from './window';
import { initTray, destroyTray } from './tray';
import { buildApplicationMenu } from './menu';
import { registerIpcHandlers, unregisterIpcHandlers } from './ipc';
import { initShortcuts, destroyShortcuts } from './shortcuts';
import { startConnectionMonitor, stopConnectionMonitor } from './connection';
import { initFloatingTaskBoard, destroyFloatingTaskBoard } from './floating-task-board';
import { initQuickView, destroyQuickView } from './quick-view/quick-view-window';
import { initTaskBoardTray } from './task-board-tray';
import { initTaskNotification, destroyTaskNotification } from './task-notification';
import { startLocalSaveSync, stopLocalSaveSync } from './local-save-sync';
import { appOnReady, appOnActivate, appOnBeforeQuit, getMainWindow } from './window';
import { SHARED_CONSTANTS } from '../shared/constants';
import { getOrCreateClientId } from './client-id';
import { loadBackendUrl, isBackendConfigured } from './api-client';
import { wsClient } from './ws-client';
import { loadToken } from './auth';
import { winAutomationService } from './win-automation-service';
import { operationRecorder } from './recorder-controller';
import { initScreenshotService, destroyScreenshotService } from './screenshot/screenshot-service';

// 单实例锁：避免多开
const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
  app.quit();
}

app.setName(SHARED_CONSTANTS.APP_NAME);
app.setAppUserModelId('com.livingagent.desktop');

let mainWindow: BrowserWindow | null = null;

app.whenReady().then(async () => {
  // 0. 加载后端 URL 持久化配置
  //    必须在 clientId 初始化前完成：HTTP/WS 请求会用到 backendUrl
  //    若未加载就开始发请求，会拿不到真实的远程后端地址
  try {
    const url = await loadBackendUrl();
    console.log(
      `[LivingAgent] backendUrl=${url} configured=${isBackendConfigured()}`
    );
  } catch (e) {
    console.error('[LivingAgent] Failed to load backend config:', e);
  }

  // 0.1 优先加载/生成 clientId：所有 HTTP/WS 请求都依赖此值
  //    若生成失败，登录鉴权 / 后端审计 / WindowsAppTool 路由都会受影响
  try {
    const info = await getOrCreateClientId();
    console.log(
      `[LivingAgent] clientId=${info.clientId} host=${info.hostname} platform=${info.platform} user=${info.osUser}`
    );
  } catch (e) {
    console.error('[LivingAgent] Failed to initialize clientId:', e);
  }

  // 1. 注册 IPC handlers
  registerIpcHandlers();

  // 1.1 初始化截图服务（P2）
  initScreenshotService();

  // 2. 创建主窗口
  mainWindow = createMainWindow();

  // 2.1 设置录制控制器的主窗口引用
  operationRecorder.setMainWindow(mainWindow);

  // 3. 创建托盘
  initTray();
  await initTaskBoardTray();

  // 4. 菜单栏
  buildApplicationMenu();

  // 5. 启动连接检测
  await startConnectionMonitor();

  // 6. 全局快捷键
  initShortcuts();

  // 7. 任务通知
  initTaskNotification();

  // 8. 任务中心悬浮窗
  initFloatingTaskBoard();

  // 8.1 P7: Quick View 悬浮对话（延迟创建，用户触发时创建窗口）
  initQuickView();

  // 9. 本地保存同步
  await startLocalSaveSync();

  // 10. 建立 WebSocket 连接（实时任务通知、执行事件推送）
  const token = await loadToken();
  if (token) {
    try {
      await wsClient.connect('/ws/agent');
      console.log('[LivingAgent] WebSocket connected to /ws/agent');
    } catch (e) {
      console.error('[LivingAgent] Failed to connect WebSocket:', e);
    }
  } else {
    console.warn('[LivingAgent] No token, WebSocket will connect after login');
  }

  // 11. 启动 Windows 自动化服务（内嵌 Python 子进程）
  //     仅 Windows 平台启动；后端通过 WebSocket 转发 WIN_AUTOMATION_CALL 调用
  try {
    await winAutomationService.start();
    if (winAutomationService.isRunning()) {
      console.log('[LivingAgent] Windows automation service started successfully');
    } else {
      const startError = winAutomationService.getStartError();
      console.error('[LivingAgent] Windows automation service FAILED to start:', startError);
      console.error('[LivingAgent] Please ensure Python is installed and run: pip install -r resources/win-automation/requirements.txt');
    }
  } catch (e) {
    console.error('[LivingAgent] Failed to start Windows automation service:', e);
  }

  // 应用 ready 钩子
  appOnReady(mainWindow);
}).catch((err) => {
  console.error('[LivingAgent] App initialization failed:', err);
  app.quit();
});

app.on('second-instance', () => {
  const w = getMainWindow();
  if (w) {
    if (w.isMinimized()) w.restore();
    w.show();
    w.focus();
  }
});

appOnActivate(() => {
  if (BrowserWindow.getAllWindows().length === 0) {
    mainWindow = createMainWindow();
  }
});

appOnBeforeQuit(() => {
  // 清理资源
  destroyTaskNotification();
  destroyFloatingTaskBoard();
  destroyQuickView(); // P7: 销毁 Quick View
  destroyShortcuts();
  destroyTray();
  stopConnectionMonitor();
  stopLocalSaveSync();
  winAutomationService.stop();
  operationRecorder.forceStop();
  destroyScreenshotService(); // P2: 销毁截图服务
  unregisterIpcHandlers();
});

app.on('window-all-closed', () => {
  // macOS 上保留 Dock 图标，Windows/Linux 退出
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
