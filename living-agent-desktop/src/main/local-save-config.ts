/**
 * 本地保存配置
 * - 路径：{userData}/local-save/config.json
 * - 默认路径：~/Documents/LivingAgent
 * - 用户可修改（设置 UI）
 */
import { app } from 'electron';
import { writeFile, readFile, mkdir } from 'fs/promises';
import { existsSync } from 'fs';
import { join } from 'path';
import { SHARED_CONSTANTS } from '../shared/constants';
import type { LocalSaveConfig, LocalSaveStats } from '../shared/types';

const DEFAULT_CONFIG: LocalSaveConfig = {
  enabled: false,
  basePath: getDefaultBasePath(),
  scopes: {
    artifacts: true,
    conversations: true,
    receipts: true,
    screenshots: false
  },
  syncStrategy: 'local-only',
  capacity: {
    maxBytes: SHARED_CONSTANTS.MAX_LOCAL_SAVE_BYTES,
    retentionDays: SHARED_CONSTANTS.DEFAULT_RETENTION_DAYS
  }
};

function getDefaultBasePath(): string {
  // Windows: C:\Users\{user}\Documents\LivingAgent
  // macOS/Linux: ~/Documents/LivingAgent
  const docs = app.getPath('documents');
  return join(docs, 'LivingAgent');
}

function getConfigPath(): string {
  return join(app.getPath('userData'), 'local-save', 'config.json');
}

let cachedConfig: LocalSaveConfig | null = null;

export async function loadConfig(): Promise<LocalSaveConfig> {
  if (cachedConfig) return cachedConfig;
  const cfgPath = getConfigPath();
  if (existsSync(cfgPath)) {
    try {
      const data = JSON.parse(await readFile(cfgPath, 'utf-8'));
      cachedConfig = { ...DEFAULT_CONFIG, ...data };
    } catch (e) {
      console.error('[local-save-config] Failed to load, using default:', e);
      cachedConfig = { ...DEFAULT_CONFIG };
    }
  } else {
    cachedConfig = { ...DEFAULT_CONFIG };
    await saveConfig(cachedConfig);
  }
  return cachedConfig!;
}

export async function saveConfig(cfg: LocalSaveConfig): Promise<void> {
  const dir = join(app.getPath('userData'), 'local-save');
  if (!existsSync(dir)) {
    await mkdir(dir, { recursive: true });
  }
  await writeFile(getConfigPath(), JSON.stringify(cfg, null, 2), 'utf-8');
  cachedConfig = cfg;
}

export function getCachedConfig(): LocalSaveConfig | null {
  return cachedConfig;
}

export function resetCachedConfig(): void {
  cachedConfig = null;
}

/**
 * 解析服务器路径 → 本地路径
 * 服务器: data/artifacts/by-execution/{execId}/{empCode}/{file}
 * 本地:   {basePath}/artifacts/{year}/{month}/{execId}/{file}
 */
export function resolveLocalPath(
  executionId: string,
  fileName: string,
  completedAt: string
): string {
  if (!cachedConfig) throw new Error('Config not loaded');
  const date = new Date(completedAt);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');

  // 安全清理路径
  const safeExecId = executionId.replace(/[\\/:*?"<>|]/g, '_');
  const safeFileName = fileName.replace(/[\\/:*?"<>|]/g, '_');

  return join(
    cachedConfig.basePath,
    'artifacts',
    String(year),
    month,
    safeExecId,
    safeFileName
  );
}

/**
 * 统计本地保存目录
 */
export async function computeStats(): Promise<LocalSaveStats> {
  if (!cachedConfig) {
    return { totalBytes: 0, fileCount: 0, lastSyncAt: null, oldestFileAt: null };
  }
  // TODO: 实际遍历目录计算（避免阻塞，异步分批）
  return {
    totalBytes: 0,
    fileCount: 0,
    lastSyncAt: null,
    oldestFileAt: null
  };
}
