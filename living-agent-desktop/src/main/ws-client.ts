/**
 * WebSocket 客户端
 * - 连接到后端（默认 /ws/agent）
 * - 自动重连（指数退避）
 * - 事件分发到本地监听器
 * - 携带设备信息 + 应用列表上报
 * - 处理后端转发的 WIN_AUTOMATION_CALL 消息（Windows 自动化操作）
 */
import WebSocket from 'ws';
import { wsUrlFor } from './api-client';
import { loadToken } from './auth';
import { getCachedClientId, getCachedClientInfo } from './client-id';
import { getInstalledAppsString } from './app-scanner';
import { winAutomationService } from './win-automation-service';
import { SHARED_CONSTANTS } from '../shared/constants';

export type WsEventType =
  | 'public_task_published'
  | 'public_task_updated'
  | 'public_task_claimed'
  | 'employee_task_update'
  | 'execution_event'
  | 'chat_response'
  | 'audio_full'
  | 'device_registered'
  | 'win_automation_call'
  | 'error'
  | 'pong';

type Listener = (data: any) => void;

class WSClient {
  private socket: WebSocket | null = null;
  private listeners = new Map<WsEventType, Set<Listener>>();
  private reconnectTimer: NodeJS.Timeout | null = null;
  private pingTimer: NodeJS.Timeout | null = null;
  private isQuitting = false;
  private currentPath = '/ws/agent';
  private reconnectAttempts = 0;
  private cachedApps: string | null = null;
  private lastPongAt = 0;

  async connect(path: string = '/ws/agent', params: Record<string, string> = {}): Promise<void> {
    this.currentPath = path;
    const token = await loadToken();
    if (!token) {
      console.warn('[ws-client] No token, cannot connect');
      return;
    }

    // 携带 clientId + 设备信息 + 应用列表
    const clientId = getCachedClientId();
    const clientInfo = getCachedClientInfo();

    // 异步扫描应用列表（首次连接时扫描，后续缓存）
    if (!this.cachedApps) {
      try {
        this.cachedApps = await getInstalledAppsString();
        console.log('[ws-client] Scanned installed apps:', this.cachedApps?.substring(0, 100) + '...');
      } catch (e) {
        console.warn('[ws-client] Failed to scan installed apps:', e);
      }
    }

    // 使用 Sec-WebSocket-Protocol 传递 token（更安全，避免 URL 泄露）
    const search = new URLSearchParams({
      ...(clientId ? { clientId } : {}),
      ...(clientInfo?.hostname ? { hostname: clientInfo.hostname } : {}),
      ...(clientInfo?.macAddress ? { macAddress: clientInfo.macAddress } : {}),
      ...(clientInfo?.platform ? { platform: clientInfo.platform } : {}),
      ...(clientInfo?.osUser ? { osUser: clientInfo.osUser } : {}),
      ...(this.cachedApps ? { applications: this.cachedApps } : {}),
      ...params
    }).toString();
    const url = `${wsUrlFor(path)}?${search}`;

    this.disconnect();

    // 返回 Promise，等待握手完成或失败
    return new Promise((resolve, reject) => {
      // 通过 Sec-WebSocket-Protocol 传递 token（格式：bearer.<token>）
      this.socket = new WebSocket(url, [`bearer.${token}`], { handshakeTimeout: 10000 });

      let handshakeComplete = false;

      this.socket.on('open', () => {
        handshakeComplete = true;
        console.log('[ws-client] Connected:', path);
        this.reconnectAttempts = 0; // 重置退避计数
        this.startPing();
        resolve();
      });

      this.socket.on('message', (data) => {
        try {
          const msg = JSON.parse(data.toString());
          if (msg && msg.type) {
            // pong 响应：更新最后 pong 时间
            if (msg.type === 'pong') {
              this.lastPongAt = Date.now();
              const set = this.listeners.get('pong');
              if (set) set.forEach((cb) => cb(msg.data ?? msg));
              return;
            }
            // 特殊处理 WIN_AUTOMATION_CALL：转发到本地 Python 服务执行后回传结果
            if (msg.type === 'win_automation_call') {
              this.handleWinAutomationCall(msg.data);
              return;
            }
            const set = this.listeners.get(msg.type as WsEventType);
            if (set) set.forEach((cb) => cb(msg.data ?? msg));
            // 通配监听
            const wild = this.listeners.get('*' as any);
            if (wild) wild.forEach((cb) => cb(msg));
          }
        } catch (e) {
          console.error('[ws-client] Failed to parse message:', e);
        }
      });

      this.socket.on('error', (err) => {
        console.error('[ws-client] Error:', err.message);
        if (!handshakeComplete) {
          // 握手阶段失败，拒绝 Promise
          reject(new Error(`WebSocket handshake failed: ${err.message}`));
        }
      });

      this.socket.on('close', () => {
        console.log('[ws-client] Disconnected');
        this.stopPing();
        if (!handshakeComplete) {
          // 握手阶段连接关闭，拒绝 Promise
          reject(new Error('WebSocket connection closed during handshake'));
        }
        if (!this.isQuitting) {
          // 指数退避：2s→4s→8s→16s→30s 上限
          const delay = Math.min(2000 * Math.pow(2, this.reconnectAttempts), 30000);
          this.reconnectAttempts++;
          console.log('[ws-client] Reconnecting in', delay, 'ms (attempt', this.reconnectAttempts, ')');
          this.reconnectTimer = setTimeout(() => {
            this.connect(path, params).catch((e) => {
              // 重连失败的错误已在 connect() 内部处理（触发下一轮重连）
              // 这里只是捕获 Promise rejection，避免 UnhandledPromiseRejectionWarning
              console.debug('[ws-client] Reconnect attempt failed:', e.message);
            });
          }, delay);
        }
      });

      // 设置超时（比 handshakeTimeout 稍长）
      setTimeout(() => {
        if (!handshakeComplete) {
          reject(new Error('WebSocket connection timeout'));
        }
      }, 15000);
    });
  }

  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.stopPing();
    if (this.socket) {
      try {
        this.socket.close();
      } catch (e) {
        // ignore
      }
      this.socket = null;
    }
  }

  /**
   * 检查 WebSocket 是否已连接
   */
  isConnected(): boolean {
    return this.socket !== null && this.socket.readyState === WebSocket.OPEN;
  }

  send(type: string, data: any): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type, data, timestamp: Date.now() }));
    }
  }

  on(type: WsEventType, listener: Listener): () => void {
    if (!this.listeners.has(type)) this.listeners.set(type, new Set());
    this.listeners.get(type)!.add(listener);
    return () => this.off(type, listener);
  }

  off(type: WsEventType, listener: Listener): void {
    this.listeners.get(type)?.delete(listener);
  }

  /**
   * 处理后端转发的 Windows 自动化调用
   * 将操作转发到本地 Python 服务执行，并通过 WebSocket 回传结果
   */
  private handleWinAutomationCall(data: any): void {
    const { id, operation, args } = data ?? {};
    if (id === undefined || !operation) {
      console.warn('[ws-client] Invalid win_automation_call message:', data);
      return;
    }

    const timeout = (args && typeof args.timeoutMs === 'number') ? args.timeoutMs : 30_000;

    winAutomationService
      .execute(operation, args ?? {}, timeout)
      .then((result) => {
        this.send('win_automation_response', { id, success: true, result });
      })
      .catch((error: Error) => {
        this.send('win_automation_response', { id, success: false, error: error.message });
      });
  }

  private startPing(): void {
    this.stopPing();
    this.lastPongAt = Date.now();
    const pingInterval = SHARED_CONSTANTS.HEARTBEAT_INTERVAL_MS;
    this.pingTimer = setInterval(() => {
      // pong 超时检测：超过 2 * pingInterval 未收到 pong 则触发重连
      if (Date.now() - this.lastPongAt > 2 * pingInterval) {
        console.warn('[ws-client] Pong timeout, last pong was', Date.now() - this.lastPongAt, 'ms ago, reconnecting');
        this.disconnect();
        this.connect(this.currentPath);
        return;
      }
      this.send('ping', {});
    }, pingInterval);
  }

  private stopPing(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }

  /**
   * 使 cachedApps 缓存失效，下次连接时将重新扫描已安装应用列表。
   * 在应用安装/卸载等事件中应调用此方法。
   */
  invalidateAppsCache(): void {
    this.cachedApps = null;
  }

  shutdown(): void {
    this.isQuitting = true;
    this.disconnect();
  }
}

export const wsClient = new WSClient();
