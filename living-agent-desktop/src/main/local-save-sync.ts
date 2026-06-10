/**
 * 本地保存同步服务
 * - 监听 WebSocket 事件（employee_task_update / execution_event）
 * - 拉取服务器产物 → 写入本地副本
 * - 启动时全量同步（仅"我可见的"）
 * - 角色变更时重同步
 * - 定期清理过期文件
 */
import { EventEmitter } from 'events';
import { writeFile, mkdir, readdir, stat, unlink, rmdir } from 'fs/promises';
import { existsSync } from 'fs';
import { join, dirname } from 'path';
import { createHash } from 'crypto';
import { shell } from 'electron';
import { loadConfig, getCachedConfig, resolveLocalPath, computeStats } from './local-save-config';
import { listMyVisibleArtifacts, downloadArtifact } from './api-client';
import { wsClient } from './ws-client';
import { sendToMainWindow } from './window';
import { SHARED_CONSTANTS } from '../shared/constants';
import type { ArtifactRecord, LocalArtifactEntry, SavedInfo } from '../shared/types';

class LocalSaveSyncService extends EventEmitter {
  private syncing = false;
  private syncDebounce: NodeJS.Timeout | null = null;
  private cleanupTimer: NodeJS.Timeout | null = null;
  private inventory: Map<string, LocalArtifactEntry> = new Map(); // key: {execId}/{file}

  async start(): Promise<void> {
    await loadConfig();
    this.subscribeEvents();
    this.startCleanupTimer();
    // 启动时全量同步（防抖）
    this.debouncedSync();
  }

  stop(): void {
    if (this.cleanupTimer) clearInterval(this.cleanupTimer);
    if (this.syncDebounce) clearTimeout(this.syncDebounce);
  }

  private subscribeEvents(): void {
    // 监听任务完成事件
    wsClient.on('employee_task_update', (data) => {
      if (data?.status === 'COMPLETED') {
        this.debouncedSync();
      }
    });
    wsClient.on('execution_event', (data) => {
      if (data?.type === 'artifact_ready') {
        this.handleArtifactReady(data).catch((e) =>
          console.error('[local-save] handleArtifactReady failed:', e)
        );
      }
    });
  }

  /**
   * 防抖同步
   */
  private debouncedSync(): void {
    if (this.syncDebounce) clearTimeout(this.syncDebounce);
    this.syncDebounce = setTimeout(() => {
      this.fullSync().catch((e) => console.error('[local-save] sync failed:', e));
    }, SHARED_CONSTANTS.SYNC_DEBOUNCE_MS);
  }

  /**
   * 全量同步：拉取"我可见的"产物 → 复制到本地
   */
  async fullSync(): Promise<{ savedCount: number; removedCount: number; skipped: number }> {
    if (this.syncing) return { savedCount: 0, removedCount: 0, skipped: 0 };
    const cfg = getCachedConfig();
    if (!cfg?.enabled) {
      return { savedCount: 0, removedCount: 0, skipped: 0 };
    }
    this.syncing = true;

    try {
      const records = await listMyVisibleArtifacts({ page: 0, size: 200 });
      let savedCount = 0;
      let skipped = 0;

      for (const record of records) {
        try {
          const result = await this.copyArtifact(record);
          if (result === 'saved') savedCount++;
          else skipped++;
        } catch (e) {
          console.error('[local-save] Failed to copy:', record?.id, e);
        }
      }

      // 清理已无权限的本地副本
      const removedCount = await this.cleanupUnauthorized(records);

      return { savedCount, removedCount, skipped };
    } finally {
      this.syncing = false;
    }
  }

  private async handleArtifactReady(artifact: ArtifactRecord): Promise<void> {
    const cfg = getCachedConfig();
    if (!cfg?.enabled || !cfg.scopes.artifacts) return;
    await this.copyArtifact(artifact);
  }

  /**
   * 复制单个产物到本地
   */
  private async copyArtifact(record: ArtifactRecord): Promise<'saved' | 'skipped'> {
    const cfg = getCachedConfig();
    if (!cfg) throw new Error('Config not loaded');

    if (!record.executionId || !record.name) return 'skipped';

    const localPath = resolveLocalPath(record.executionId, record.name, record.createdAt);
    const key = `${record.executionId}/${record.name}`;

    // 已存在 → 检查 SHA-256
    if (existsSync(localPath)) {
      return 'skipped';
    }

    // 下载并写入
    if (!record.id) return 'skipped';
    const data = await downloadArtifact(record.id);
    await mkdir(dirname(localPath), { recursive: true });
    await writeFile(localPath, data);

    // 记录到 inventory
    const sha256 = createHash('sha256').update(data).digest('hex');
    this.inventory.set(key, {
      executionId: record.executionId,
      fileName: record.name,
      sourcePath: record.path || '',
      localPath,
      sha256,
      size: data.length,
      copiedAt: new Date().toISOString()
    });

    const info: SavedInfo = {
      path: localPath,
      size: data.length,
      scope: 'artifacts',
      savedAt: new Date().toISOString()
    };
    this.emit('saved', info);
    sendToMainWindow('localsave:saved', info);

    return 'saved';
  }

  /**
   * 清理已无权限的本地副本
   */
  private async cleanupUnauthorized(authorizedRecords: ArtifactRecord[]): Promise<number> {
    const authorizedKeys = new Set(
      authorizedRecords
        .filter((r) => r.executionId && r.name)
        .map((r) => `${r.executionId}/${r.name}`)
    );

    let removed = 0;
    for (const [key, entry] of this.inventory.entries()) {
      if (!authorizedKeys.has(key)) {
        try {
          if (existsSync(entry.localPath)) {
            await unlink(entry.localPath);
            removed++;
          }
        } catch (e) {
          // 文件可能已被用户删除，忽略
        }
        this.inventory.delete(key);
      }
    }
    return removed;
  }

  /**
   * 清理过期文件
   */
  private async cleanupExpired(): Promise<void> {
    const cfg = getCachedConfig();
    if (!cfg?.enabled) return;
    const retentionMs = cfg.capacity.retentionDays * 24 * 60 * 60 * 1000;
    const now = Date.now();

    for (const entry of this.inventory.values()) {
      try {
        const st = await stat(entry.localPath);
        if (now - st.mtime.getTime() > retentionMs) {
          await unlink(entry.localPath);
          this.inventory.delete(`${entry.executionId}/${entry.fileName}`);
        }
      } catch (e) {
        // ignore
      }
    }
  }

  private startCleanupTimer(): void {
    // 每天清理一次
    this.cleanupTimer = setInterval(() => {
      this.cleanupExpired().catch((e) => console.error('[local-save] cleanup failed:', e));
    }, 24 * 60 * 60 * 1000);
  }

  /**
   * 角色变更时重同步
   */
  async onUserRoleChanged(): Promise<{ savedCount: number; removedCount: number; skipped: number }> {
    return this.fullSync();
  }
}

export const localSaveSync = new LocalSaveSyncService();

/* ============ IPC 暴露函数 ============ */

export async function startLocalSaveSync(): Promise<void> {
  await localSaveSync.start();
}

export function stopLocalSaveSync(): void {
  localSaveSync.stop();
}

export async function triggerSync(): Promise<{ savedCount: number; removedCount: number; skipped: number }> {
  return localSaveSync.fullSync();
}

export function openLocalSaveFolder(): void {
  const cfg = getCachedConfig();
  if (!cfg?.basePath) return;
  shell.openPath(cfg.basePath);
}

export async function getLocalSaveStats() {
  return computeStats();
}
