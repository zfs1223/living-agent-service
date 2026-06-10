/**
 * 后端 REST API 客户端
 * - 统一管理 baseURL
 * - 自动注入 Authorization header
 * - 处理 ApiResponse 格式
 */
import { app } from 'electron';
import { writeFile, readFile, mkdir } from 'fs/promises';
import { join } from 'path';
import { existsSync } from 'fs';
import { DEFAULT_BACKEND_URL } from '../shared/constants';
import { loadToken } from './auth';
import { getCachedClientId } from './client-id';

let backendUrl: string = DEFAULT_BACKEND_URL;
let backendConfigured: boolean = false; // 标识是否用户显式配置过

function getConfigPath(): string {
  return join(app.getPath('userData'), 'backend-config.json');
}

export async function loadBackendUrl(): Promise<string> {
  const cfgPath = getConfigPath();
  if (existsSync(cfgPath)) {
    try {
      const data = JSON.parse(await readFile(cfgPath, 'utf-8'));
      const url = (data.url || '').replace(/\/+$/, '');
      if (url) {
        backendUrl = url;
        backendConfigured = true;
      }
    } catch (e) {
      console.error('[api-client] Failed to load backend config:', e);
    }
  }
  return backendUrl;
}

export async function setBackendUrl(url: string): Promise<string> {
  const clean = (url || '').replace(/\/+$/, '');
  if (!clean) {
    throw new Error('后端 URL 不能为空');
  }
  // 简单校验：必须是 http(s)://
  if (!/^https?:\/\//i.test(clean)) {
    throw new Error('后端 URL 必须以 http:// 或 https:// 开头');
  }
  backendUrl = clean;
  backendConfigured = true;
  const dir = app.getPath('userData');
  if (!existsSync(dir)) {
    await mkdir(dir, { recursive: true });
  }
  await writeFile(
    getConfigPath(),
    JSON.stringify({ url: clean, configuredAt: new Date().toISOString() }, null, 2),
    'utf-8'
  );
  return clean;
}

export function getBackendUrl(): string {
  return backendUrl;
}

/** 后端是否已配置（区别于默认值占位） */
export function isBackendConfigured(): boolean {
  return backendConfigured && !!backendUrl;
}

/** 同步 ws URL（依赖已加载的 backendUrl） */
export function wsUrlFor(path: string): string {
  const httpUrl = backendUrl;
  return httpUrl.replace(/^http/, 'ws') + path;
}

/**
 * REST 请求封装
 */
export async function apiRequest<T = any>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${backendUrl}${endpoint.startsWith('/') ? endpoint : '/' + endpoint}`;
  const token = await loadToken();

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined)
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  // 携带客户端标识：后端用于审计 / 任务路由（WindowsAppTool 派发到对应 pywinauto 节点）
  const clientId = getCachedClientId();
  if (clientId) {
    headers['X-Client-Id'] = clientId;
  }

  const res = await fetch(url, { ...options, headers });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`API ${res.status}: ${text || res.statusText}`);
  }

  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    const json: any = await res.json();
    // ApiResponse 格式：{ success, data, error, errorDescription }
    if (json && typeof json === 'object' && 'success' in json) {
      if (!json.success) {
        throw new Error(json.errorDescription || json.error || 'API call failed');
      }
      return json.data as T;
    }
    return json as T;
  }
  return (await res.text()) as unknown as T;
}

/* ============ 任务栏 API ============ */

export async function getPublicTasks(department?: string): Promise<any[]> {
  const qs = department ? `?department=${encodeURIComponent(department)}` : '';
  return apiRequest<any[]>(`/api/tasks/public${qs}`);
}

export async function claimTask(taskId: string): Promise<any> {
  return apiRequest<any>(`/api/tasks/${taskId}/claim`, { method: 'POST' });
}

export interface CreditBalance {
  balance: number;
  userId?: string;
  updatedAt?: string;
}

/** 桌面端独立调用：与 web 端 creditApi.getBalance 等价，但不共享代码 */
export async function getMyCredits(): Promise<CreditBalance> {
  return apiRequest<CreditBalance>('/api/credits/balance');
}

/* ============ 产物 API ============ */

export async function listMyVisibleArtifacts(params: {
  page?: number;
  size?: number;
} = {}): Promise<any[]> {
  const page = params.page ?? 0;
  const size = params.size ?? 50;
  return apiRequest<any[]>(`/api/artifacts/my-visible?page=${page}&size=${size}`);
}

export async function listArtifactsByDepartment(department: string): Promise<any[]> {
  return apiRequest<any[]>(`/api/artifacts/by-department/${encodeURIComponent(department)}`);
}

export async function downloadArtifact(artifactId: string): Promise<Buffer> {
  const url = `${backendUrl}/api/artifacts/${artifactId}/download`;
  const token = await loadToken();
  const res = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined
  });
  if (!res.ok) throw new Error(`Download failed: ${res.status}`);
  const arr = await res.arrayBuffer();
  return Buffer.from(arr);
}
