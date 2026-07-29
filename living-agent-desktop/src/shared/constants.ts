/**
 * 共享常量
 */
import { DEFAULT_BACKEND_URL, DEFAULT_BACKEND_WS } from './types';

// 单独 re-export 后端默认地址，方便直接 `import { DEFAULT_BACKEND_URL }` 使用
export { DEFAULT_BACKEND_URL, DEFAULT_BACKEND_WS };

export const SHARED_CONSTANTS = {
  APP_NAME: 'Living Agent',
  DEFAULT_BACKEND_URL,
  DEFAULT_BACKEND_WS,
  CONFIG_FILE: 'config.json',
  TOKEN_FILE: 'token.dat',
  TRAY_NORMAL_ICON: 'tray-normal.png',
  TRAY_RED_ICON: 'tray-badge-red.png',
  POLL_INTERVAL_MS: 5 * 60 * 1000,    // 5 分钟
  HEARTBEAT_INTERVAL_MS: 25 * 1000,   // 25 秒（小于后端 30s 巡检间隔，确保保活）
  MAX_LOCAL_SAVE_BYTES: 10 * 1024 * 1024 * 1024,
  DEFAULT_RETENTION_DAYS: 30,
  MAX_CACHED_TASKS_PER_DEPT: 100,
  TASK_CACHE_TTL_HOURS: 24,
  SYNC_DEBOUNCE_MS: 1000
} as const;
