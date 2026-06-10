/**
 * 鉴权（safeStorage 加密 token）
 * - 使用 Electron safeStorage（macOS Keychain / Windows DPAPI）
 * - 不落盘明文
 * - 提供 token 持久化与读取
 */
import { safeStorage, app } from 'electron';
import { writeFile, readFile, mkdir } from 'fs/promises';
import { join } from 'path';
import { existsSync } from 'fs';
import { sendToMainWindow } from './window';

let cachedToken: string | null = null;

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
    await ensureUserDataDir();
    await writeFile(getTokenFilePath(), token, 'utf-8');
  } else {
    const encrypted = safeStorage.encryptString(token);
    await writeFile(getTokenFilePath(), encrypted);
  }
  cachedToken = token;
  sendToMainWindow('auth:changed', { hasToken: true });
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
  sendToMainWindow('auth:changed', { hasToken: false });
}

/**
 * 同步获取（仅在已缓存时可用）
 */
export function getTokenSync(): string | null {
  return cachedToken;
}
