/**
 * 任务本地缓存
 * - 按部门/月份组织
 * - TTL 24h
 * - 离线时显示
 * - 多账号切换时清理
 */
import { writeFile, readFile, mkdir, readdir, unlink } from 'fs/promises';
import { existsSync } from 'fs';
import { join, dirname } from 'path';
import { app } from 'electron';
import { SHARED_CONSTANTS } from '../shared/constants';
import type { PublicTask } from '../shared/types';

function getCacheDir(): string {
  return join(app.getPath('userData'), 'cache', 'task-board');
}

function buildPath(dept: string | undefined, year: number, month: number): string {
  const safeDept = (dept || 'all').replace(/[\\/:*?"<>|]/g, '_');
  return join(
    getCacheDir(),
    String(year),
    String(month).padStart(2, '0'),
    `${safeDept}.json`
  );
}

/**
 * 缓存当前用户可见的公共任务
 */
export async function cacheVisibleTasks(tasks: PublicTask[]): Promise<void> {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth() + 1;

  // 按部门分组
  const grouped = new Map<string, PublicTask[]>();
  for (const task of tasks) {
    const key = task.department || 'all';
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key)!.push(task);
  }

  for (const [dept, items] of grouped) {
    const path = buildPath(dept, year, month);
    await mkdir(dirname(path), { recursive: true });
    // 限制每部门最多 100 条
    const truncated = items.slice(0, SHARED_CONSTANTS.MAX_CACHED_TASKS_PER_DEPT);
    await writeFile(path, JSON.stringify({
      cachedAt: new Date().toISOString(),
      ttl: SHARED_CONSTANTS.TASK_CACHE_TTL_HOURS * 60 * 60 * 1000,
      tasks: truncated
    }, null, 2), 'utf-8');
  }
}

/**
 * 读取缓存
 */
export async function loadCachedTasks(dept?: string): Promise<PublicTask[]> {
  try {
    const dir = getCacheDir();
    if (!existsSync(dir)) return [];

    // 优先读最近月份
    const years = (await readdir(dir)).sort().reverse();
    const items: PublicTask[] = [];

    for (const y of years) {
      const yDir = join(dir, y);
      const months = (await readdir(yDir)).sort().reverse();
      for (const m of months) {
        const mDir = join(yDir, m);
        const files = await readdir(mDir);
        for (const f of files) {
          if (dept && f !== `${dept.replace(/[\\/:*?"<>|]/g, '_')}.json`) continue;
          if (!dept && f !== 'all.json') continue;
          try {
            const content = JSON.parse(await readFile(join(mDir, f), 'utf-8'));
            if (content.cachedAt && content.ttl) {
              const age = Date.now() - new Date(content.cachedAt).getTime();
              if (age > content.ttl) continue; // 过期
            }
            if (Array.isArray(content.tasks)) {
              items.push(...content.tasks);
            }
          } catch (e) {
            // ignore corrupted
          }
        }
      }
    }
    return items;
  } catch (e) {
    console.error('[task-board-cache] load failed:', e);
    return [];
  }
}

/**
 * 清理所有缓存（账号切换时）
 */
export async function clearAllCache(): Promise<void> {
  const dir = getCacheDir();
  if (!existsSync(dir)) return;
  try {
    const years = await readdir(dir);
    for (const y of years) {
      const yDir = join(dir, y);
      const months = await readdir(yDir);
      for (const m of months) {
        const mDir = join(yDir, m);
        const files = await readdir(mDir);
        for (const f of files) {
          await unlink(join(mDir, f));
        }
      }
    }
  } catch (e) {
    console.error('[task-board-cache] clear failed:', e);
  }
}
