/**
 * IM WebSocket 客户端
 *
 * 设计借鉴 HuLa Rust 客户端：
 * - 指数退避重连（1s, 2s, 4s, 8s, 16s, 30s 最大）
 * - PING/PONG 心跳保活（每 10s 发送，30s 超时断开重连）
 * - 离线消息队列（重连后 flush）
 * - 消息处理器注册（on(type, handler)）
 * - 单例导出 imClient
 */

type MessageHandler = (data: any) => void;

interface IMMessage {
  type: string;
  [key: string]: any;
}

class IMWsClient {
  private ws: WebSocket | null = null;
  private url: string = '';
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private heartbeatTimeoutTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempt: number = 0;
  private pendingMessages: IMMessage[] = [];
  private handlers: Map<string, Set<MessageHandler>> = new Map();
  private _connected: boolean = false;

  // 重连参数
  private static readonly RECONNECT_BASE_DELAY = 1000;   // 1s
  private static readonly RECONNECT_MAX_DELAY = 30000;   // 30s

  // 心跳参数
  private static readonly HEARTBEAT_INTERVAL = 10000;    // 每 10s 发送 PING
  private static readonly HEARTBEAT_TIMEOUT = 30000;     // 30s 无 PONG 则断开重连

  get connected(): boolean {
    return this._connected;
  }

  /** 连接到 IM WebSocket 服务 */
  connect(backendUrl: string, token: string): void {
    // 避免重复连接同一地址
    if (this.ws && this._connected && this.url === this.buildUrl(backendUrl, token)) {
      return;
    }

    // 先断开旧连接
    this.disconnect();

    this.url = this.buildUrl(backendUrl, token);
    this.reconnectAttempt = 0;
    this.doConnect();
  }

  /** 断开连接（不再自动重连） */
  disconnect(): void {
    this.clearTimers();
    if (this.ws) {
      // 防止 onclose 触发重连
      this.ws.onclose = null;
      this.ws.close();
      this.ws = null;
    }
    this._connected = false;
    this.url = '';
  }

  /** 发送消息（离线时入队列，重连后自动 flush） */
  send(message: IMMessage): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else {
      this.pendingMessages.push(message);
    }
  }

  /** 注册消息处理器 */
  on(type: string, handler: MessageHandler): () => void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, new Set());
    }
    this.handlers.get(type)!.add(handler);
    // 返回取消注册函数
    return () => {
      this.handlers.get(type)?.delete(handler);
    };
  }

  // ============ 内部方法 ============

  private buildUrl(backendUrl: string, token: string): string {
    const protocol = backendUrl.startsWith('https') ? 'wss' : 'ws';
    const urlBase = backendUrl.replace(/^https?:\/\//, '');
    return `${protocol}://${urlBase}/ws/im?token=${token}`;
  }

  private doConnect(): void {
    if (!this.url) return;

    try {
      this.ws = new WebSocket(this.url);
    } catch (e) {
      console.warn('[im-ws] WebSocket 创建失败:', e);
      this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      console.info('[im-ws] 连接成功');
      this._connected = true;
      this.reconnectAttempt = 0;
      this.startHeartbeat();
      this.flushPendingMessages();
    };

    this.ws.onmessage = (event) => {
      this.resetHeartbeatTimeout();
      try {
        const data = JSON.parse(event.data);
        // PONG 响应只用于心跳检测
        if (data.type === 'PONG' || data.type === 'pong') return;
        this.dispatch(data.type, data);
      } catch {
        // 忽略非 JSON 消息
      }
    };

    this.ws.onclose = (event) => {
      console.info('[im-ws] 连接关闭, code:', event.code);
      this._connected = false;
      this.clearTimers();
      // 非正常关闭则自动重连
      if (event.code !== 1000) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = () => {
      console.warn('[im-ws] 连接错误');
      // onclose 会在 onerror 之后触发，不需要额外处理
    };
  }

  private dispatch(type: string, data: any): void {
    const handlers = this.handlers.get(type);
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(data);
        } catch (e) {
          console.error(`[im-ws] 消息处理器异常 (${type}):`, e);
        }
      });
    }
  }

  private flushPendingMessages(): void {
    if (this.pendingMessages.length === 0) return;
    const messages = this.pendingMessages.splice(0);
    for (const msg of messages) {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify(msg));
      } else {
        // 连接又断了，放回队列
        this.pendingMessages.push(msg);
        break;
      }
    }
  }

  // ============ 心跳 ============

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'PING' }));
        // 设置超时检测
        this.heartbeatTimeoutTimer = setTimeout(() => {
          console.warn('[im-ws] 心跳超时，断开重连');
          if (this.ws) {
            this.ws.onclose = null;
            this.ws.close();
            this.ws = null;
          }
          this._connected = false;
          this.scheduleReconnect();
        }, IMWsClient.HEARTBEAT_TIMEOUT);
      }
    }, IMWsClient.HEARTBEAT_INTERVAL);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (this.heartbeatTimeoutTimer) {
      clearTimeout(this.heartbeatTimeoutTimer);
      this.heartbeatTimeoutTimer = null;
    }
  }

  private resetHeartbeatTimeout(): void {
    if (this.heartbeatTimeoutTimer) {
      clearTimeout(this.heartbeatTimeoutTimer);
      this.heartbeatTimeoutTimer = null;
    }
  }

  // ============ 重连 ============

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return; // 已在重连中

    const delay = Math.min(
      IMWsClient.RECONNECT_BASE_DELAY * Math.pow(2, this.reconnectAttempt),
      IMWsClient.RECONNECT_MAX_DELAY
    );
    this.reconnectAttempt++;

    console.info(`[im-ws] ${delay}ms 后进行第 ${this.reconnectAttempt} 次重连`);

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.doConnect();
    }, delay);
  }

  private clearTimers(): void {
    this.stopHeartbeat();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }
}

/** IM WebSocket 单例 */
export const imClient = new IMWsClient();
