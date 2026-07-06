/**
 * 本地保存配置
 * - 路径：{userData}/local-save/config.json
 * - 默认路径：~/Documents/LivingAgent
 * - 用户可修改（设置 UI）
 */
import { app } from 'electron';
import { writeFile, readFile, mkdir, readdir, stat } from 'fs/promises';
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
 * 异步分批遍历目录，计算文件数和总大小，避免阻塞主线程
 */
export async function computeStats(): Promise<LocalSaveStats> {
  if (!cachedConfig) {
    return { totalBytes: 0, fileCount: 0, lastSyncAt: null, oldestFileAt: null };
  }

  const basePath = cachedConfig.basePath;
  if (!existsSync(basePath)) {
    return { totalBytes: 0, fileCount: 0, lastSyncAt: null, oldestFileAt: null };
  }

  let totalBytes = 0;
  let fileCount = 0;
  let lastSyncAt: string | null = null;
  let oldestFileAt: string | null = null;

  // 异步分批遍历目录
  const BATCH_SIZE = 100; // 每批处理 100 个文件
  const queue: string[] = [basePath];

  while (queue.length > 0) {
    const batch = queue.splice(0, BATCH_SIZE);
    const results = await Promise.all(
      batch.map(async (dirPath) => {
        try {
          const entries = await readdir(dirPath, { withFileTypes: true });
          const subdirs: string[] = [];
          const files: string[] = [];

          for (const entry of entries) {
            const fullPath = join(dirPath, entry.name);
            if (entry.isDirectory()) {
              subdirs.push(fullPath);
            } else if (entry.isFile()) {
              files.push(fullPath);
            }
          }

          return { subdirs, files };
        } catch (e) {
          console.warn('[local-save-config] Failed to read directory:', dirPath, e);
          return { subdirs: [], files: [] };
        }
      })
    );

    // 合并结果
    for (const result of results) {
      queue.push(...result.subdirs);

      // 批量获取文件信息
      const fileStats = await Promise.all(
        result.files.map(async (filePath) => {
          try {
            const fileStat = await stat(filePath);
            return {
              size: fileStat.size,
              mtime: fileStat.mtime.toISOString()
            };
          } catch (e) {
            console.warn('[local-save-config] Failed to stat file:', filePath, e);
            return null;
          }
        })
      );

      // 更新统计信息
      for (const fileStat of fileStats) {
        if (fileStat) {
          totalBytes += fileStat.size;
          fileCount++;

          if (!lastSyncAt || fileStat.mtime > lastSyncAt) {
            lastSyncAt = fileStat.mtime;
          }
          if (!oldestFileAt || fileStat.mtime < oldestFileAt) {
            oldestFileAt = fileStat.mtime;
          }
        }
      }
    }

    // 让出主线程，避免阻塞
    await new Promise(resolve => setImmediate(resolve));
  }

  return { totalBytes, fileCount, lastSyncAt, oldestFileAt };
}
