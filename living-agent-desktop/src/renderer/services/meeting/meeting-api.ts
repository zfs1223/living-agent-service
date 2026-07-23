/**
 * 会议 API 客户端
 *
 * P83 桌面端会议 UI 的后端 API 封装。
 * 闭环 67（会议管理）的前端 API 调用层。
 *
 * API 端点对齐 LIVEKIT_INTEGRATION_PLAN.md §5.4:
 * - POST   /api/meetings                      创建会议（闭环 67-A）
 * - GET    /api/meetings                      列出会议（按部门过滤，闭环 14→67）
 * - GET    /api/meetings/{roomName}           获取会议详情
 * - DELETE /api/meetings/{roomName}           结束会议（触发闭环 68）
 * - POST   /api/meetings/{roomName}/token     获取参会 token（闭环 38→67）
 * - GET    /api/meetings/{roomName}/participants 获取参会者列表
 *
 * 调用模式：与桌面端 ProjectsPage / AdminPage 一致，
 * 通过 fetch + backendUrl + token 直接请求后端 REST API。
 * 后续可在主进程 api-client.ts 中注册会议 API 并桥接到 renderer。
 */

// ─── 类型定义 ──────────────────────────────────────────────

/** 会议状态 */
export type MeetingStatus = 'active' | 'scheduled' | 'finished';

/** 会议信息 */
export interface MeetingInfo {
  /** LiveKit 房间名（唯一标识） */
  roomName: string;
  /** 会议主题 */
  title: string;
  /** 所属部门（对齐 P14，自动填充 currentUser.department） */
  department: string;
  /** 会议状态 */
  status: MeetingStatus;
  /** 最大参会人数 */
  maxParticipants: number;
  /** 当前参会人数 */
  participantCount: number;
  /** 创建人 */
  createdBy: string;
  /** 创建时间 */
  createdAt: string;
  /** 会议开始时间 */
  startedAt?: string;
  /** 会议结束时间 */
  finishedAt?: string;
}

/** 创建会议请求 */
export interface CreateMeetingRequest {
  /** 会议主题 */
  title: string;
  /** 最大参会人数（默认 50） */
  maxParticipants?: number;
  /** 所属部门（默认取当前用户部门） */
  department?: string;
}

/** 创建会议响应 */
export interface CreateMeetingResponse {
  roomName: string;
  title: string;
  token: string;
  livekitUrl: string;
}

/** 参会 token 响应 */
export interface MeetingTokenResponse {
  token: string;
  livekitUrl: string;
}

/** 参会者信息 */
export interface ParticipantInfo {
  identity: string;
  name?: string;
  department?: string;
  joinedAt?: string;
  state: 'connected' | 'disconnected';
}

// ─── API 客户端 ────────────────────────────────────────────

/**
 * 获取认证请求头
 * 与 ProjectsPage / AdminPage 模式一致，从主进程获取 token
 */
async function getAuthHeaders(): Promise<Record<string, string>> {
  const token = await window.livingAgentAPI.auth.getToken();
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

/**
 * 处理 API 响应
 * 统一响应格式：ApiResponse<T> — data 在 response.data 字段
 */
async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`API ${res.status}: ${text}`);
  }
  const json: any = await res.json();
  // ApiResponse 格式：{ success, data, error, errorDescription }
  if (json && typeof json === 'object' && 'data' in json) {
    return json.data as T;
  }
  return json as T;
}

/**
 * 会议 API 客户端类
 *
 * 用法:
 *   const api = new MeetingApi('http://localhost:8382');
 *   const meetings = await api.listMeetings();
 *   const created = await api.createMeeting('技术部周会', 20);
 */
export class MeetingApi {
  constructor(private backendUrl: string) {}

  /**
   * 创建会议（闭环 67-A）
   * @param title 会议主题
   * @param maxParticipants 最大参会人数，默认 50
   */
  async createMeeting(title: string, maxParticipants: number = 50): Promise<CreateMeetingResponse> {
    const headers = await getAuthHeaders();
    const res = await fetch(`${this.backendUrl}/api/meetings`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ title, maxParticipants }),
    });
    return handleResponse<CreateMeetingResponse>(res);
  }

  /**
   * 列出会议（闭环 67 列表）
   * @param status 按状态过滤（可选）: active / scheduled / finished
   * @param department 按部门过滤（可选）
   */
  async listMeetings(status?: MeetingStatus, department?: string): Promise<MeetingInfo[]> {
    const headers = await getAuthHeaders();
    const params = new URLSearchParams();
    if (status) params.set('status', status);
    if (department) params.set('department', department);
    const qs = params.toString();
    const res = await fetch(`${this.backendUrl}/api/meetings${qs ? '?' + qs : ''}`, { headers });
    return handleResponse<MeetingInfo[]>(res);
  }

  /**
   * 获取会议详情
   * @param roomName LiveKit 房间名
   */
  async getMeeting(roomName: string): Promise<MeetingInfo> {
    const headers = await getAuthHeaders();
    const res = await fetch(`${this.backendUrl}/api/meetings/${encodeURIComponent(roomName)}`, { headers });
    return handleResponse<MeetingInfo>(res);
  }

  /**
   * 结束会议（触发闭环 68）
   * @param roomName LiveKit 房间名
   */
  async endMeeting(roomName: string): Promise<void> {
    const headers = await getAuthHeaders();
    const res = await fetch(`${this.backendUrl}/api/meetings/${encodeURIComponent(roomName)}`, {
      method: 'DELETE',
      headers,
    });
    await handleResponse<void>(res);
  }

  /**
   * 获取参会 token（闭环 38→67 认证桥接）
   * LAS token → LiveKit JWT，前端拿到 token 后连接 LiveKit Server
   * @param roomName LiveKit 房间名
   */
  async getToken(roomName: string): Promise<MeetingTokenResponse> {
    const headers = await getAuthHeaders();
    const res = await fetch(`${this.backendUrl}/api/meetings/${encodeURIComponent(roomName)}/token`, {
      method: 'POST',
      headers,
    });
    return handleResponse<MeetingTokenResponse>(res);
  }

  /**
   * 获取参会者列表
   * @param roomName LiveKit 房间名
   */
  async listParticipants(roomName: string): Promise<ParticipantInfo[]> {
    const headers = await getAuthHeaders();
    const res = await fetch(`${this.backendUrl}/api/meetings/${encodeURIComponent(roomName)}/participants`, { headers });
    return handleResponse<ParticipantInfo[]>(res);
  }
}

/**
 * 创建会议 API 实例的工厂函数
 * @param backendUrl 后端 URL
 */
export function createMeetingApi(backendUrl: string): MeetingApi {
  return new MeetingApi(backendUrl);
}
