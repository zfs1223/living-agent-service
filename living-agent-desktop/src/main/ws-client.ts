/**
 * WebSocket 客户端
 * - 连接到后端（默认 /ws/agent）
 * - 自动重连
 * - 事件分发到本地监听器
 */
import WebSocket from 'ws';
import { wsUrlFor } from './api-client';
import { loadToken } from './auth';
import { getCachedClientId } from './client-id';

export type WsEventType =
  | 'public_task_published'
  | 'public_task_updated'
  | 'public_task_claimed'
  | 'employee_task_update'
  | 'execution_event'
  | 'chat_response'
  | 'audio_full'
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

  async connect(path: string = '/ws/agent', params: Record<string, string> = {}): Promise<void> {
    this.currentPath = path;
    const token = await loadToken();
    if (!token) {
      console.warn('[ws-client] No token, cannot connect');
      return;
    }
    // 携带 clientId：后端用于识别是哪个客户端的 WebSocket
    const clientId = getCachedClientId();
    const search = new URLSearchParams({
      token,
      ...(clientId ? { clientId } : {}),
      ...params
    }).toString();
    const url = `${wsUrlFor(path)}?${search}`;

    this.disconnect();
    this.socket = new WebSocket(url);

    this.socket.on('open', () => {
      console.log('[ws-client] Connected:', path);
      this.startPing();
    });

    this.socket.on('message', (data) => {
      try {
        const msg = JSON.parse(data.toString());
        if (msg && msg.type) {
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
    });

    this.socket.on('close', () => {
      console.log('[ws-client] Disconnected');
      this.stopPing();
      if (!this.isQuitting) {
        this.reconnectTimer = setTimeout(() => this.connect(path, params), 5_000);
      }
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

  private startPing(): void {
    this.stopPing();
    this.pingTimer = setInterval(() => {
      this.send('ping', {});
    }, 30_000);
  }

  private stopPing(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }

  shutdown(): void {
    this.isQuitting = true;
    this.disconnect();
  }
}

export const wsClient = new WSClient();
