/**
 * 任务栏托盘红点
 * - 拉取待接取任务数
 * - 订阅 public_task_published 事件
 * - 定时轮询兜底
 * - 控制托盘红点显示
 */
import { getPublicTasks, claimTask } from './api-client';
import { wsClient } from './ws-client';
import { setBadgeVisible } from './tray';
import { sendToMainWindow, showMainWindow } from './window';
import { SHARED_CONSTANTS } from '../shared/constants';
import type { PublicTask } from '../shared/types';

let pendingCount = 0;
let pollTimer: NodeJS.Timeout | null = null;
let lastTasks: PublicTask[] = [];

export async function initTaskBoardTray(): Promise<void> {
  // 订阅 WebSocket 事件
  wsClient.on('public_task_published', () => {
    refreshPendingCount();
  });
  wsClient.on('public_task_updated', () => {
    refreshPendingCount();
  });
  wsClient.on('public_task_claimed', () => {
    refreshPendingCount();
  });

  // 启动时拉取
  await refreshPendingCount();

  // 定时轮询兜底
  startPolling();
}

function startPolling(): void {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(() => {
    refreshPendingCount().catch((e) =>
      console.error('[task-board-tray] poll failed:', e)
    );
  }, SHARED_CONSTANTS.POLL_INTERVAL_MS);
}

export async function refreshPendingCount(): Promise<number> {
  try {
    const tasks = (await getPublicTasks()) as PublicTask[];
    lastTasks = tasks || [];
    pendingCount = tasks.length;
    setBadgeVisible(pendingCount > 0, pendingCount);
    sendToMainWindow('taskboard:count-changed', { count: pendingCount });
    return pendingCount;
  } catch (e) {
    console.error('[task-board-tray] refresh failed:', e);
    return 0;
  }
}

export function getPendingCount(): number {
  return pendingCount;
}

export function getLastTasks(): PublicTask[] {
  return lastTasks;
}

/**
 * 快速接取最优先任务（全局快捷键调用）
 */
export async function claimTopPriorityTask(): Promise<PublicTask | null> {
  if (lastTasks.length === 0) {
    await refreshPendingCount();
    if (lastTasks.length === 0) return null;
  }
  // 按 priority 降序
  const top = [...lastTasks].sort((a, b) => b.priority - a.priority)[0];
  try {
    await claimTask(top.taskId);
    sendToMainWindow('taskboard:new-task', { type: 'claimed', task: top });
    await refreshPendingCount();
    return top;
  } catch (e) {
    console.error('[task-board-tray] claim top task failed:', e);
    return null;
  }
}

export function openTaskBoard(): void {
  showMainWindow();
  const { getMainWindow } = require('./window');
  const w = getMainWindow();
  if (w) w.webContents.send('navigate', '/task-board');
}
