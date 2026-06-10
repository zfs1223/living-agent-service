/**
 * 任务 OS 通知
 * - 仅高/紧急任务（priority >= 3）触发
 * - 支持免打扰时段
 * - 支持按部门/难度过滤
 */
import { wsClient } from './ws-client';
import { notify, isInQuietHours } from './notifications';
import { claimTask } from './api-client';
import { openTaskBoard } from './task-board-tray';
import type { PublicTask, TaskNotificationConfig } from '../shared/types';

const DEFAULT_CONFIG: TaskNotificationConfig = {
  enabled: true,
  minPriority: 3,
  departments: [],
  difficultyFilter: [],
  quietHours: {
    enabled: false,
    start: '22:00',
    end: '08:00'
  },
  autoClaim: false
};

let config: TaskNotificationConfig = { ...DEFAULT_CONFIG };
const notifiedTaskIds = new Set<string>();

export function initTaskNotification(): void {
  // 订阅任务发布事件
  wsClient.on('public_task_published', (data) => {
    handleNewTask(data);
  });
}

function handleNewTask(task: PublicTask): void {
  if (!config.enabled) return;
  if (notifiedTaskIds.has(task.taskId)) return;
  if (task.priority < config.minPriority) return;
  if (config.departments.length > 0 && task.department && !config.departments.includes(task.department)) {
    return;
  }
  if (config.difficultyFilter.length > 0 && !config.difficultyFilter.includes(task.difficulty)) {
    return;
  }
  if (config.quietHours.enabled && isInQuietHours(config.quietHours.start, config.quietHours.end)) {
    return;
  }

  notifiedTaskIds.add(task.taskId);
  if (notifiedTaskIds.size > 200) {
    const arr = [...notifiedTaskIds];
    notifiedTaskIds.clear();
    arr.slice(-100).forEach((x) => notifiedTaskIds.add(x));
  }

  const emoji = task.priority >= 5 ? '🚨' : task.priority >= 3 ? '🔥' : '📋';
  notify({
    title: `${emoji} 新任务：${task.taskType}`,
    body: `${task.description}\n奖励：${task.reward} 积分 · 预计 ${task.estimatedHours} 小时`,
    urgency: task.priority >= 5 ? 'critical' : 'normal',
    actions: [
      { text: '查看任务', callback: () => openTaskBoard() },
      {
        text: config.autoClaim ? '已自动接取' : '直接接取',
        callback: async () => {
          if (config.autoClaim) return;
          try {
            await claimTask(task.taskId);
          } catch (e) {
            console.error('[task-notification] claim failed:', e);
          }
        }
      }
    ]
  });
}

export function setNotificationConfig(c: Partial<TaskNotificationConfig>): void {
  config = { ...config, ...c };
}

export function getNotificationConfig(): TaskNotificationConfig {
  return { ...config };
}

export function destroyTaskNotification(): void {
  notifiedTaskIds.clear();
}
