/**
 * 客户端唯一标识
 *
 * 目的：
 * - 桌面端以安装包形式分发到多台客户端 PC
 * - 每台机器安装时生成一个持久化的 clientId，存到 userData/client-id.json
 * - 所有 HTTP/WS 请求携带此 clientId，便于后端：
 *   1. 审计：哪个客户端发起了请求
 *   2. 路由：WindowsAppTool 根据 clientId 把 windows_automation 任务派发到对应 PC 的 pywinauto 节点
 *   3. 排障：日志 / 任务回执可以追溯到具体物理机
 *
 * 规则：
 * - 首次启动时生成 v4 UUID
 * - 持久化到 userData 下的 client-id.json（含 hostname、platform、createdAt 元数据）
 * - 后续启动直接读，不再变更
 * - 支持用户手动重置（重装系统 / 迁移到新机器时）
 */
import { app } from 'electron';
import { writeFile, readFile } from 'fs/promises';
import { existsSync } from 'fs';
import { join } from 'path';
import { hostname, platform, userInfo, networkInterfaces } from 'os';
import { randomUUID } from 'crypto';

export interface ClientInfo {
  clientId: string;
  hostname: string;
  platform: NodeJS.Platform;
  osUser: string;
  macAddress: string;  // 主网卡 MAC 地址（硬件指纹）
  appVersion: string;
  createdAt: string;
}

let cached: ClientInfo | null = null;

function getClientIdPath(): string {
  return join(app.getPath('userData'), 'client-id.json');
}

/**
 * 获取主网卡 MAC 地址（硬件指纹）
 * 用于设备唯一性验证，确保同一台机器只能有一个 clientId
 */
function getPrimaryMacAddress(): string {
  const nets = networkInterfaces();
  for (const name of Object.keys(nets)) {
    // 跳过虚拟网卡
    if (name.toLowerCase().includes('virtual') || name.toLowerCase().includes('vmware') || name.toLowerCase().includes('vbox')) {
      continue;
    }
    for (const net of nets[name] || []) {
      // 选择第一个非内部、IPv4 接口
      if (!net.internal && net.family === 'IPv4') {
        return net.mac || '';
      }
    }
  }
  return '';
}

/**
 * 读取或生成 clientId
 */
export async function getOrCreateClientId(): Promise<ClientInfo> {
  if (cached) return cached;
  const path = getClientIdPath();
  if (existsSync(path)) {
    try {
      const raw = JSON.parse(await readFile(path, 'utf-8'));
      // 校验关键字段
      if (raw && typeof raw.clientId === 'string' && raw.clientId.length >= 8) {
        cached = raw as ClientInfo;
        return cached;
      }
    } catch (e) {
      console.warn('[client-id] Failed to read existing, regenerating:', e);
    }
  }

  // 生成新的
  const info: ClientInfo = {
    clientId: randomUUID(),
    hostname: hostname(),
    platform: platform(),
    osUser: userInfo().username,
    macAddress: getPrimaryMacAddress(),  // 硬件指纹
    appVersion: app.getVersion(),
    createdAt: new Date().toISOString()
  };

  try {
    await writeFile(path, JSON.stringify(info, null, 2), 'utf-8');
  } catch (e) {
    console.error('[client-id] Failed to persist:', e);
  }
  cached = info;
  return info;
}

/** 同步获取（仅在已加载过时可用） */
export function getCachedClientId(): string | null {
  return cached?.clientId ?? null;
}

/** 同步获取完整设备信息（仅在已加载过时可用） */
export function getCachedClientInfo(): ClientInfo | null {
  return cached;
}

/**
 * 手动重置（设置页 / 迁移机器时用）
 */
export async function resetClientId(): Promise<ClientInfo> {
  cached = null;
  return getOrCreateClientId();
}
