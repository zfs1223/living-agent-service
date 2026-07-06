/**
 * 后端连接检测
 * - 启动时检测
 * - 心跳监控
 * - 状态变更推送到渲染层
 */
import { DEFAULT_BACKEND_URL, SHARED_CONSTANTS } from '../shared/constants';
import { sendToMainWindow } from './window';
import { getBackendUrl, setBackendUrl } from './api-client';

let monitorTimer: NodeJS.Timeout | null = null;
let currentStatus: 'online' | 'offline' | 'unknown' = 'unknown';

async function checkHealth(): Promise<boolean> {
  const url = getBackendUrl() || DEFAULT_BACKEND_URL;
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 5000);
    const res = await fetch(`${url}/api/health`, {
      method: 'GET',
      signal: controller.signal
    });
    clearTimeout(timer);
    return res.ok;
  } catch (e) {
    return false;
  }
}

async function tick(): Promise<void> {
  const online = await checkHealth();
  const next: typeof currentStatus = online ? 'online' : 'offline';
  if (next !== currentStatus) {
    currentStatus = next;
    sendToMainWindow('backend:status-changed', { status: next, url: getBackendUrl() });
  }
}

export async function startConnectionMonitor(): Promise<void> {
  await tick();
  monitorTimer = setInterval(tick, SHARED_CONSTANTS.HEARTBEAT_INTERVAL_MS);
}

export function stopConnectionMonitor(): void {
  if (monitorTimer) {
    clearInterval(monitorTimer);
    monitorTimer = null;
  }
}

export function getConnectionStatus(): typeof currentStatus {
  return currentStatus;
}

export { setBackendUrl };
