/**
 * 鉴权（safeStorage 加密 token）
 * - 使用 Electron safeStorage（macOS Keychain / Windows DPAPI）
 * - 不落盘明文
 * - 提供 token 持久化与读取
 * - Token 变更时触发 WebSocket 重连
 */
import { safeStorage, app } from 'electron';
import { writeFile, readFile, mkdir } from 'fs/promises';
import { join } from 'path';
import { existsSync } from 'fs';
import { sendToMainWindow } from './window';

let cachedToken: string | null = null;

// WebSocket 连接状态管理
let wsConnected = false;

function getTokenFilePath(): string {
  return join(app.getPath('userData'), 'token.enc');
}

function getUserDataDir(): string {
  return app.getPath('userData');
}

async function ensureUserDataDir(): Promise<void> {
  const dir = getUserDataDir();
  if (!existsSync(dir)) {
    await mkdir(dir, { recursive: true });
  }
}

/**
 * 保存 token（加密）
 */
export async function saveToken(token: string): Promise<void> {
  if (!safeStorage.isEncryptionAvailable()) {
    console.warn('[auth] safeStorage encryption not available, using plain file');
    // 弹出警告对话框，提示用户安全风险
    const { dialog } = await import('electron');
    dialog.showMessageBox({
      type: 'warning',
      title: '安全警告',
      message: '系统加密功能不可用',
      detail: 'Token 将以明文形式存储在本地，可能存在安全风险。\n\n建议：\n1. 确保操作系统支持加密存储\n2. 在安全的环境中使用本应用\n3. 定期清理本地缓存',
      buttons: ['我知道了']
    }).catch(() => {});
    await ensureUserDataDir();
    await writeFile(getTokenFilePath(), token, 'utf-8');
  } else {
    const encrypted = safeStorage.encryptString(token);
    await writeFile(getTokenFilePath(), encrypted);
  }
  cachedToken = token;
  sendToMainWindow('auth:changed', { hasToken: true });
  
  // Token 变更时触发 WebSocket 重连
  await reconnectWebSocket();
}

/**
 * 读取 token
 */
export async function loadToken(): Promise<string | null> {
  if (cachedToken) return cachedToken;
  const filePath = getTokenFilePath();
  if (!existsSync(filePath)) return null;

  try {
    if (safeStorage.isEncryptionAvailable()) {
      const encrypted = await readFile(filePath);
      cachedToken = safeStorage.decryptString(encrypted);
    } else {
      cachedToken = await readFile(filePath, 'utf-8');
    }
    return cachedToken;
  } catch (e) {
    console.error('[auth] Failed to load token:', e);
    return null;
  }
}

/**
 * 清除 token
 */
export async function clearToken(): Promise<void> {
  const filePath = getTokenFilePath();
  if (existsSync(filePath)) {
    try {
      const { unlink } = await import('fs/promises');
      await unlink(filePath);
    } catch (e) {
      console.error('[auth] Failed to delete token file:', e);
    }
  }
  cachedToken = null;
  wsConnected = false;
  sendToMainWindow('auth:changed', { hasToken: false });
  
  // Token 清除时断开 WebSocket 连接
  await disconnectWebSocket();
}

/**
 * 重新连接 WebSocket
 */
async function reconnectWebSocket(): Promise<void> {
  try {
    const { wsClient } = await import('./ws-client');
    if (wsClient.isConnected()) {
      console.log('[auth] Token changed, reconnecting WebSocket...');
      await wsClient.disconnect();
    }
    console.log('[auth] Connecting WebSocket with new token...');
    await wsClient.connect('/ws/agent');
    wsConnected = true;
    console.log('[auth] WebSocket reconnected successfully');
  } catch (e) {
    console.error('[auth] Failed to reconnect WebSocket:', e);
  }
}

/**
 * 断开 WebSocket 连接
 */
async function disconnectWebSocket(): Promise<void> {
  try {
    const { wsClient } = await import('./ws-client');
    if (wsClient.isConnected()) {
      console.log('[auth] Disconnecting WebSocket...');
      await wsClient.disconnect();
      wsConnected = false;
      console.log('[auth] WebSocket disconnected');
    }
  } catch (e) {
    console.error('[auth] Failed to disconnect WebSocket:', e);
  }
}

/**
 * 同步获取（仅在已缓存时可用）
 */
export function getTokenSync(): string | null {
  return cachedToken;
}
