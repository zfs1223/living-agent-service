/** API service layer */

import type { Agent, TokenResponse, User, Task, ChatMessage } from '../types';
import { API_BASE, request } from './apiBase';
import { getToken } from '../stores';
import type { OfficeDepartmentStatusResponse } from './officeExtendedApi';

/** Legacy/Internal generic fetcher */
export const fetchJson = request;

async function uploadFile(url: string, file: File, extraFields?: Record<string, string>): Promise<any> {
    const token = getToken();
    const formData = new FormData();
    formData.append('file', file);
    if (extraFields) {
        for (const [k, v] of Object.entries(extraFields)) {
            formData.append(k, v);
        }
    }
    const res = await fetch(`${API_BASE}${url}`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData,
        credentials: 'include', // 确保 HttpOnly Cookie 随请求自动发送
    });
    if (!res.ok) {
        const error = await res.json().catch(() => ({ detail: 'Upload failed' }));
        throw new Error(error.detail || `HTTP ${res.status}`);
    }
    return res.json();
}

// Upload with progress tracking via XMLHttpRequest.
// Returns { promise, abort } — call abort() to cancel the upload.
// Progress callback: 0-100 = upload phase, 101 = processing phase (server is parsing the file).
export function uploadFileWithProgress(
    url: string,
    file: File,
    onProgress?: (percent: number) => void,
    extraFields?: Record<string, string>,
    timeoutMs: number = 120_000,
): { promise: Promise<any>; abort: () => void } {
    const xhr = new XMLHttpRequest();
    const promise = new Promise<any>((resolve, reject) => {
        const token = getToken();
        const formData = new FormData();
        formData.append('file', file);
        if (extraFields) {
            for (const [k, v] of Object.entries(extraFields)) {
                formData.append(k, v);
            }
        }
        xhr.open('POST', `${API_BASE}${url}`);
        if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
        xhr.withCredentials = true; // 确保 HttpOnly Cookie 随请求自动发送

        // Upload phase: 0-100%
        xhr.upload.onprogress = (e) => {
            if (e.lengthComputable && onProgress) {
                onProgress(Math.round((e.loaded / e.total) * 100));
            }
        };
        // Upload bytes finished → enter processing phase
        xhr.upload.onload = () => {
            if (onProgress) onProgress(101); // 101 = "processing" sentinel
        };

        xhr.onload = () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                try { resolve(JSON.parse(xhr.responseText)); } catch { resolve(undefined); }
            } else {
                try {
                    const err = JSON.parse(xhr.responseText);
                    reject(new Error(err.detail || `HTTP ${xhr.status}`));
                } catch { reject(new Error(`HTTP ${xhr.status}`)); }
            }
        };
        xhr.onerror = () => reject(new Error('Network error'));
        xhr.ontimeout = () => reject(new Error('Upload timed out'));
        xhr.onabort = () => reject(new Error('Upload cancelled'));
        xhr.timeout = timeoutMs;
        xhr.send(formData);
    });
    return { promise, abort: () => xhr.abort() };
}

// ─── Auth ─────────────────────────────────────────────
export interface PhoneLoginResponse {
    accessToken: string;
    refreshToken: string | null;
    user: {
        id: string;
        email: string;
        name: string;
        avatar: string | null;
        department: string | null;
        identity: string;
        accessLevel: string;
        founder: boolean;
        tenantId: string;
        allowedBrains: string[];
        capabilities: string[];
        skills: string[];
    };
}

export const authApi = {
    sendSmsCode: (data: { phone: string; type: string }) =>
        request<{ success: boolean; message: string; expiresIn: number; code?: string }>('/auth/sms/send', { method: 'POST', body: JSON.stringify(data) }),

    phoneLogin: (data: { phone: string; code: string }) =>
        request<PhoneLoginResponse>('/auth/phone/login', { method: 'POST', body: JSON.stringify(data) }),

    bindPhone: (data: { phone: string; code: string }) =>
        request<{ success: boolean }>('/auth/phone/bind', { method: 'POST', body: JSON.stringify(data) }),

    me: () => request<User>('/auth/me'),

    updateMe: (data: Partial<User>) =>
        request<User>('/auth/me', { method: 'PATCH', body: JSON.stringify(data) }),
};

export interface RegistrationResult {
    employeeId: string;
    name: string;
    identity: string;
    accessLevel: string;
    sessionId: string;
    tenantId?: string;
}

export const creditApi = {
    getBalance: () => request<any>('/credits/balance'),
    getEmployeeBalance: (employeeId: string) => request<any>(`/credits/balance/${encodeURIComponent(employeeId)}`),
    getHistory: (limit?: number) => request<any>(`/credits/history${limit ? `?limit=${limit}` : ''}`),
    getLeaderboard: (department?: string, limit?: number) => {
        const params = new URLSearchParams();
        if (department) params.set('department', department);
        if (limit) params.set('limit', limit.toString());
        const query = params.toString();
        return request<any>(`/credits/leaderboard${query ? `?${query}` : ''}`);
    },
    getStats: () => request<any>('/credits/stats'),
};

export const enterpriseSettingsApi = {
    get: () => request<any>('/enterprise/settings'),
    getByCategory: (category: string) => request<any>(`/enterprise/settings/${encodeURIComponent(category)}`),
    getSetting: (category: string, key: string) => request<any>(`/enterprise/settings/${encodeURIComponent(category)}/${encodeURIComponent(key)}`),
    updateSetting: (category: string, key: string, value: any) => request<any>(`/enterprise/settings/${encodeURIComponent(category)}/${encodeURIComponent(key)}`, { method: 'PUT', body: JSON.stringify({ value }) }),
    batchUpdate: (data: any) => request<any>('/enterprise/settings/batch', { method: 'POST', body: JSON.stringify(data) }),
    updateAll: (data: any) => request<any>('/enterprise/settings', { method: 'PUT', body: JSON.stringify(data) }),
    getHistory: () => request<any[]>('/enterprise/settings/history'),
    resetSetting: (category: string, key: string) => request<any>(`/enterprise/settings/${encodeURIComponent(category)}/${encodeURIComponent(key)}/reset`, { method: 'POST' }),
    listCategories: () => request<string[]>('/enterprise/settings/categories'),
    getVersions: () => request<any[]>('/enterprise/settings/versions'),
    rollbackVersion: (versionId: string) => request<any>(`/enterprise/settings/versions/${encodeURIComponent(versionId)}/rollback`, { method: 'POST' }),
};

export const fixedEmployeeApi = {
    getSummary: () => request<any>('/fixed-employees/summary'),
    getAllDefinitions: () => request<any[]>('/fixed-employees/definitions'),
    getDefinition: (code: string) => request<any>(`/fixed-employees/definitions/${encodeURIComponent(code)}`),
    getDefinitionsByDepartment: (department: string) => request<any[]>(`/fixed-employees/definitions/by-department/${encodeURIComponent(department)}`),
    getGroupedDefinitions: () => request<Record<string, any[]>>('/fixed-employees/grouped'),
};

// ─── System ─────────────────────────────────────────────
export const systemApi = {
    status: () =>
        request<{ hasFounder: boolean; isFirstUser: boolean; isConfigured: boolean; configuredProviders: string[] }>('/system/status'),

    register: (data: { name: string; email: string; phone: string; companyName?: string }) =>
        request<RegistrationResult>('/system/register', { method: 'POST', body: JSON.stringify(data) }),

    config: () =>
        request<{ company_name: string; logo_url?: string }>('/system/config'),

    providers: () =>
        request<any[]>('/system/config/providers'),
};

// ─── Tenants ──────────────────────────────────────────
export const tenantApi = {
    selfCreate: (data: { name: string }) =>
        request<any>('/tenants/self-create', { method: 'POST', body: JSON.stringify(data) }),

    join: (invitationCode: string) =>
        request<any>('/tenants/join', { method: 'POST', body: JSON.stringify({ invitation_code: invitationCode }) }),

    registrationConfig: () =>
        request<{ allow_self_create_company: boolean }>('/tenants/registration-config'),

    resolveByDomain: (domain: string) =>
        request<any>(`/tenants/resolve-by-domain?domain=${encodeURIComponent(domain)}`),
};

export const adminApi = {
    listCompanies: () =>
        request<any[]>('/admin/companies'),

    createCompany: (data: { name: string }) =>
        request<any>('/admin/companies', { method: 'POST', body: JSON.stringify(data) }),

    updateCompany: (id: string, data: any) =>
        request<any>(`/tenants/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

    toggleCompany: (id: string) =>
        request<any>(`/admin/companies/${id}/toggle`, { method: 'PUT' }),

    getPlatformSettings: () =>
        request<any>('/admin/platform-settings'),

    updatePlatformSettings: (data: any) =>
        request<any>('/admin/platform-settings', { method: 'PUT', body: JSON.stringify(data) }),
};

// ─── Agents ───────────────────────────────────────────
export const agentApi = {
    list: (tenantId?: string) => request<Agent[]>(tenantId ? `/agents?tenant_id=${tenantId}` : '/agents'),

    get: (id: string) => request<Agent>(`/agents?id=${encodeURIComponent(id)}`),

    create: (data: any) =>
        request<any>('/agents', { method: 'POST', body: JSON.stringify(data) }),

    update: (id: string, data: Partial<Agent>) =>
        request<Agent>(`/agents/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(data) }),

    delete: (id: string) =>
        request<void>(`/agents/${encodeURIComponent(id)}`, { method: 'DELETE' }),

    start: (id: string) =>
        request<Agent>(`/agents/${encodeURIComponent(id)}/start`, { method: 'POST' }),

    stop: (id: string) =>
        request<Agent>(`/agents/${encodeURIComponent(id)}/stop`, { method: 'POST' }),

    metrics: (id: string) =>
        request<any>(`/agents/${encodeURIComponent(id)}/metrics`),

    collaborators: (id: string) =>
        request<any[]>(`/agents/${encodeURIComponent(id)}/collaborators`),

    templates: () =>
        request<any[]>('/agents/templates'),

    // OpenClaw gateway
    generateApiKey: (id: string) =>
        request<{ api_key: string; message: string }>(`/agents/${encodeURIComponent(id)}/api-key`, { method: 'POST' }),

    gatewayMessages: (id: string) =>
        request<any[]>(`/agents/${encodeURIComponent(id)}/gateway-messages`),

    // 数字员工配置（仅董事长可访问）
    getConfig: (id: string) =>
        request<any>(`/agents/config?id=${encodeURIComponent(id)}`),

    updateConfig: (id: string, config: any) =>
        request<any>(`/agents/config?id=${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(config) }),
};

// ─── Tasks ────────────────────────────────────────────
// Note: AgentTaskController (/agents/{agentId}/tasks) is deprecated.
// All task operations now use the unified /tasks API.
export const taskApi = {
    getPublicTasks: (department?: string) => {
        const params = new URLSearchParams();
        if (department) params.set('department', department);
        return request<any[]>(`/tasks/public${params.toString() ? `?${params.toString()}` : ''}`);
    },

    claimTask: (taskId: string) =>
        request<any>(`/tasks/${taskId}/claim`, { method: 'POST' }),

    submitTask: (taskId: string, result: string) =>
        request<any>(`/tasks/${taskId}/submit`, { method: 'POST', body: JSON.stringify({ result }) }),

    getMyTasks: (status?: string) => {
        const params = new URLSearchParams();
        if (status) params.set('status', status);
        return request<any[]>(`/tasks/my${params.toString() ? `?${params.toString()}` : ''}`);
    },

    // 员工执行历史 (数字员工可用)
    getMyExecutions: (limit = 20) =>
        request<ExecutionReceiptSummary[]>(`/tasks/executions/my?limit=${limit}`),

    // 任务整体进展
    getExecutionProgress: (executionId: string) =>
        request<ExecutionProgress>(`/tasks/executions/${encodeURIComponent(executionId)}/progress`),

    // 部门任务列表
    getDepartmentExecutions: (department: string, limit = 50) =>
        request<ExecutionReceiptSummary[]>(`/tasks/executions/department/${encodeURIComponent(department)}?limit=${limit}`),
};

export interface ExecutionReceiptSummary {
    receiptId: string;
    executionId: string;
    employeeCode: string;
    employeeNeuronId: string;
    status: string;
    summary: string;
    completedAt: string;
    modelName: string;
    modelProvider: string;
}

export interface ExecutionProgress {
    executionId: string;
    totalCount: number;
    completedCount: number;
    failedCount: number;
    receipts: ExecutionReceiptSummary[];
}

// ─── Files ────────────────────────────────────────────
export const fileApi = {
    list: (agentId: string, path: string = '') =>
        request<any[]>(`/agents/${encodeURIComponent(agentId)}/files?path=${encodeURIComponent(path)}`),

    read: (agentId: string, path: string) =>
        request<{ path: string; content: string }>(`/agents/${encodeURIComponent(agentId)}/files/content?path=${encodeURIComponent(path)}`),

    write: (agentId: string, path: string, content: string) =>
        request(`/agents/${encodeURIComponent(agentId)}/files/content?path=${encodeURIComponent(path)}`, {
            method: 'PUT',
            body: JSON.stringify({ content }),
        }),

    delete: (agentId: string, path: string) =>
        request(`/agents/${encodeURIComponent(agentId)}/files/content?path=${encodeURIComponent(path)}`, {
            method: 'DELETE',
        }),

    upload: (agentId: string, file: File, path: string = 'workspace/knowledge_base', onProgress?: (pct: number) => void) =>
        onProgress
            ? uploadFileWithProgress(`/agents/${encodeURIComponent(agentId)}/files/upload?path=${encodeURIComponent(path)}`, file, onProgress).promise
            : uploadFile(`/agents/${encodeURIComponent(agentId)}/files/upload?path=${encodeURIComponent(path)}`, file),

    importSkill: (agentId: string, skillId: string) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/files/import-skill`, {
            method: 'POST',
            body: JSON.stringify({ skill_id: skillId }),
        }),

    downloadUrl: (agentId: string, path: string) => {
        const token = getToken();
        return `${API_BASE}/agents/${encodeURIComponent(agentId)}/files/download?path=${encodeURIComponent(path)}&token=${token}`;
    },
};

// ─── Channel Config ───────────────────────────────────
export const channelApi = {
    get: (agentId: string) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/channel`).catch(() => null),

    create: (agentId: string, data: any) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/channel`, { method: 'POST', body: JSON.stringify(data) }),

    update: (agentId: string, data: any) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/channel`, { method: 'PUT', body: JSON.stringify(data) }),

    delete: (agentId: string) =>
        request<void>(`/agents/${encodeURIComponent(agentId)}/channel`, { method: 'DELETE' }),

    webhookUrl: (agentId: string) =>
        request<{ webhook_url: string }>(`/agents/${encodeURIComponent(agentId)}/channel/webhook-url`).catch(() => null),
};

// ─── Enterprise ───────────────────────────────────────
export const enterpriseApi = {
    llmModels: () => {
        const tid = localStorage.getItem('current_tenant_id');
        return request<any[]>(`/enterprise/llm-models${tid ? `?tenant_id=${tid}` : ''}`);
    },
    templates: () => request<any[]>('/agents/templates'),
    // Enterprise Knowledge Base
    kbFiles: (path: string = '') =>
        request<any[]>(`/enterprise/knowledge-base/files?path=${encodeURIComponent(path)}`),
    kbUpload: (file: File, subPath: string = '') =>
        uploadFile(`/enterprise/knowledge-base/upload?sub_path=${encodeURIComponent(subPath)}`, file),
    kbRead: (path: string) =>
        request<{ path: string; content: string }>(`/enterprise/knowledge-base/content?path=${encodeURIComponent(path)}`),
    kbWrite: (path: string, content: string) =>
        request(`/enterprise/knowledge-base/content?path=${encodeURIComponent(path)}`, {
            method: 'PUT',
            body: JSON.stringify({ content }),
        }),
    kbDelete: (path: string) =>
        request(`/enterprise/knowledge-base/content?path=${encodeURIComponent(path)}`, {
            method: 'DELETE',
        }),
};

// ─── Activity Logs ────────────────────────────────────
export const activityApi = {
    list: (agentId: string, limit = 50) =>
        request<any[]>(`/agents/${encodeURIComponent(agentId)}/activity?limit=${limit}`),
};

// ─── Messages ─────────────────────────────────────────
export const messageApi = {
    inbox: (limit = 50) =>
        request<any[]>(`/messages/inbox?limit=${limit}`),
    unreadCount: () =>
        request<{ unread_count: number }>('/messages/unread-count'),
    markRead: (messageId: string) =>
        request<void>(`/messages/${messageId}/read`, { method: 'PUT' }),
    markAllRead: () =>
        request<void>('/messages/read-all', { method: 'PUT' }),
};

// ─── Schedules ────────────────────────────────────────
export const scheduleApi = {
    list: (agentId: string) =>
        request<any[]>(`/agents/${encodeURIComponent(agentId)}/schedules`),

    create: (agentId: string, data: { name: string; instruction: string; cron_expr: string }) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/schedules`, { method: 'POST', body: JSON.stringify(data) }),

    update: (agentId: string, scheduleId: string, data: any) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/schedules/${scheduleId}`, { method: 'PATCH', body: JSON.stringify(data) }),

    delete: (agentId: string, scheduleId: string) =>
        request<void>(`/agents/${encodeURIComponent(agentId)}/schedules/${scheduleId}`, { method: 'DELETE' }),

    trigger: (agentId: string, scheduleId: string) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/schedules/${scheduleId}/run`, { method: 'POST' }),

    history: (agentId: string, scheduleId: string) =>
        request<any[]>(`/agents/${encodeURIComponent(agentId)}/schedules/${scheduleId}/history`),
};

// ─── Skills ───────────────────────────────────────────
export const skillApi = {
    list: () => request<any[]>('/skills'),
    get: (id: string) => request<any>(`/skills/${id}`),
    create: (data: any) =>
        request<any>('/skills', { method: 'POST', body: JSON.stringify(data) }),
    update: (id: string, data: any) =>
        request<any>(`/skills/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
    delete: (id: string) =>
        request<void>(`/skills/${id}`, { method: 'DELETE' }),
    browse: {
        list: (path: string) => request<any[]>(`/skills/browse/list?path=${encodeURIComponent(path)}`),
        read: (path: string) => request<{ content: string }>(`/skills/browse/read?path=${encodeURIComponent(path)}`),
        write: (path: string, content: string) =>
            request<any>('/skills/browse/write', { method: 'PUT', body: JSON.stringify({ path, content }) }),
        delete: (path: string) =>
            request<any>(`/skills/browse/delete?path=${encodeURIComponent(path)}`, { method: 'DELETE' }),
    },
    clawhub: {
        search: (q: string) => request<any[]>(`/skills/clawhub/search?q=${encodeURIComponent(q)}`),
        detail: (slug: string) => request<any>(`/skills/clawhub/detail/${slug}`),
        install: (slug: string) => request<any>('/skills/clawhub/install', { method: 'POST', body: JSON.stringify({ slug }) }),
    },
    importFromUrl: (url: string) =>
        request<any>('/skills/import-from-url', { method: 'POST', body: JSON.stringify({ url }) }),
    previewUrl: (url: string) =>
        request<any>('/skills/import-from-url/preview', { method: 'POST', body: JSON.stringify({ url }) }),
    settings: {
        getToken: () => request<{ configured: boolean; source: string; masked: string; clawhub_configured: boolean; clawhub_masked: string }>('/skills/settings/token'),
        setToken: (github_token: string) =>
            request<any>('/skills/settings/token', { method: 'PUT', body: JSON.stringify({ github_token }) }),
        setClawhubKey: (clawhub_key: string) =>
            request<any>('/skills/settings/token', { method: 'PUT', body: JSON.stringify({ clawhub_key }) }),
    },
    agentImport: {
        fromClawhub: (agentId: string, slug: string) =>
            request<any>(`/agents/${encodeURIComponent(agentId)}/files/import-from-clawhub`, { method: 'POST', body: JSON.stringify({ slug }) }),
        fromUrl: (agentId: string, url: string) =>
            request<any>(`/agents/${encodeURIComponent(agentId)}/files/import-from-url`, { method: 'POST', body: JSON.stringify({ url }) }),
    },
};

// ─── Triggers (Aware Engine) ──────────────────────────
export const triggerApi = {
    list: (agentId: string) =>
        request<any[]>(`/agents/${encodeURIComponent(agentId)}/triggers`),

    update: (agentId: string, triggerId: string, data: any) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/triggers/${triggerId}`, { method: 'PATCH', body: JSON.stringify(data) }),

    delete: (agentId: string, triggerId: string) =>
        request<void>(`/agents/${encodeURIComponent(agentId)}/triggers/${triggerId}`, { method: 'DELETE' }),
};

// ─── Departments ───────────────────────────────────────
export const departmentApi = {
    list: () =>
        request<any[]>('/departments'),

    get: (id: string) =>
        request<any>(`/departments/${id}`),

    getByCode: (code: string) =>
        request<any>(`/departments/code/${code}`),

    getBrain: (departmentId: string) =>
        request<any>(`/departments/${departmentId}/brain`),

    getAgents: (departmentId: string) =>
        request<any[]>(`/departments/${departmentId}/agents`),

    getMembers: (departmentId: string) =>
        request<any[]>(`/departments/${departmentId}/members`),

    getChatHistory: (departmentId: string, limit = 50, options?: { userId?: string; start?: string; end?: string }) => {
        const params = new URLSearchParams();
        params.set('limit', String(limit));
        if (options?.userId) params.set('userId', options.userId);
        if (options?.start) params.set('start', options.start);
        if (options?.end) params.set('end', options.end);
        const query = params.toString();
        return request<any[]>(`/departments/${departmentId}/chat-history${query ? `?${query}` : ''}`);
    },

    listConversations: (departmentCode: string) =>
        request<any[]>(`/dept/${departmentCode}/conversations`),

    getConversationHistory: (departmentCode: string, conversationId: string, limit = 100) =>
        request<any[]>(`/dept/${departmentCode}/conversations/history?conversationId=${encodeURIComponent(conversationId)}&limit=${limit}`),

    deleteConversation: (departmentCode: string, conversationId: string) =>
        request<any>(`/dept/${departmentCode}/conversations?conversationId=${encodeURIComponent(conversationId)}`, { method: 'DELETE' }),
};

export interface OfficeDepartmentAgentSnapshot {
    id?: string;
    agentId: string;
    agentName?: string;
    name?: string;
    status?: string;
    taskState?: 'pending' | 'running' | 'complete' | 'error';
    fromZone?: string;
    toZone?: string;
    message?: string;
    occurredAt?: number;
    current_task?: string;
    currentTask?: string;
    updated_at?: string;
    last_active_at?: string;
}

export interface OfficeDepartmentSnapshot {
    department?: string;
    departmentName?: string;
    updatedAt?: number;
    status?: string;
    agents?: OfficeDepartmentAgentSnapshot[];
    events?: any[];
}

export const projectApi = {
    list: (departmentId?: string, status?: string) => {
        const params = new URLSearchParams();
        if (departmentId) params.set('department_id', departmentId);
        if (status) params.set('status', status);
        const query = params.toString();
        return request<any[]>(`/projects${query ? `?${query}` : ''}`);
    },

    get: (id: string) =>
        request<any>(`/projects/${id}`),

    create: (data: any) =>
        request<any>('/projects', { method: 'POST', body: JSON.stringify(data) }),

    update: (id: string, data: any) =>
        request<any>(`/projects/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

    delete: (id: string) =>
        request<void>(`/projects/${id}`, { method: 'DELETE' }),

    getTasks: (projectId: string) =>
        request<any[]>(`/projects/${projectId}/tasks`),

    createTask: (projectId: string, data: any) =>
        request<any>(`/projects/${projectId}/tasks`, { method: 'POST', body: JSON.stringify(data) }),

    updateTask: (projectId: string, taskId: string, data: any) =>
        request<any>(`/projects/${projectId}/tasks/${taskId}`, { method: 'PUT', body: JSON.stringify(data) }),

    deleteTask: (projectId: string, taskId: string) =>
        request<void>(`/projects/${projectId}/tasks/${taskId}`, { method: 'DELETE' }),
};

// ─── Approvals ─────────────────────────────────────────
export const approvalApi = {
    list: (status?: string, type?: string) => {
        const params = new URLSearchParams();
        if (status) params.set('status', status);
        if (type) params.set('type', type);
        const query = params.toString();
        return request<any[]>(`/approvals${query ? `?${query}` : ''}`);
    },

    getMyPending: () =>
        request<any[]>('/approvals/my-pending'),

    getMyApprovals: () =>
        request<any[]>('/approvals/my'),

    get: (id: string) =>
        request<any>(`/approvals/${id}`),

    create: (data: any) =>
        request<any>('/approvals', { method: 'POST', body: JSON.stringify(data) }),

    getSteps: (approvalId: string) =>
        request<any[]>(`/approvals/${approvalId}/steps`),

    approve: (approvalId: string, stepId: string, comment?: string) =>
        request<any>(`/approvals/${approvalId}/steps/${stepId}/approve`, {
            method: 'POST',
            body: JSON.stringify({ comment }),
        }),

    reject: (approvalId: string, stepId: string, comment?: string) =>
        request<any>(`/approvals/${approvalId}/steps/${stepId}/reject`, {
            method: 'POST',
            body: JSON.stringify({ comment }),
        }),

    cancel: (approvalId: string) =>
        request<void>(`/approvals/${approvalId}/cancel`, { method: 'POST' }),
};

// ─── Neurons ───────────────────────────────────────────
export const neuronApi = {
    list: () =>
        request<any[]>('/neurons'),

    get: (id: string) =>
        request<any>(`/neurons/${id}`),

    getStatus: (id: string) =>
        request<any>(`/neurons/${id}/status`),

    getMetrics: (id: string) =>
        request<any>(`/neurons/${id}/metrics`),
};

// ─── WebSocket ─────────────────────────────────────────
export const wsApi = {
    chatUrl: (agentId: string, token: string, sessionId?: string) => {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        let url = `${protocol}//${window.location.host}/ws/agent?token=${token}&agentId=${encodeURIComponent(agentId)}`;
        if (sessionId) url += `&sessionId=${sessionId}`;
        return url;
    },

    neuronUrl: (neuronId: string, token: string) => {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${window.location.host}/ws/agent?token=${token}&agentId=${encodeURIComponent(neuronId)}`;
    },

    brainUrl: (brainId: string, token: string) => {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${window.location.host}/ws/dept/${encodeURIComponent(brainId)}?token=${token}`;
    },

    agentUrl: (agentId: string, token: string, sessionId?: string) => {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        let url = `${protocol}//${window.location.host}/ws/agent?token=${token}&agentId=${encodeURIComponent(agentId)}`;
        if (sessionId) url += `&sessionId=${sessionId}`;
        return url;
    },

    deptUrl: (dept: string, token: string) => {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${window.location.host}/ws/dept/${dept}?token=${token}`;
    },

    chairmanUrl: (token: string) => {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${window.location.host}/ws/enterprise?token=${token}`;
    },

    publicUrl: (token: string) => {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${window.location.host}/ws/public?token=${token}`;
    },
};

// ─── Knowledge Base ────────────────────────────────────
export const knowledgeApi = {
    list: () =>
        request<any[]>('/knowledge'),

    get: (id: string) =>
        request<any>(`/knowledge/${id}`),

    create: (data: any) =>
        request<any>('/knowledge', { method: 'POST', body: JSON.stringify(data) }),

    update: (id: string, data: any) =>
        request<any>(`/knowledge/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

    delete: (id: string) =>
        request<void>(`/knowledge/${id}`, { method: 'DELETE' }),
};

// ─── Tools ──────────────────────────────────────────────
export const toolApi = {
    list: () =>
        request<any[]>('/enterprise/tools'),

    getByDepartment: (department: string) =>
        request<any[]>(`/enterprise/tools/by-department/${department}`),
};

// ─── System Settings ────────────────────────────────────
export const systemSettingsApi = {
    get: () =>
        request<any>('/chairman/settings'),

    update: (data: any) =>
        request<any>('/chairman/settings', { method: 'PUT', body: JSON.stringify(data) }),
};

// ─── Interventions ──────────────────────────────────────
export const interventionApi = {
    list: () =>
        request<any[]>('/interventions'),

    get: (id: string) =>
        request<any>(`/interventions/${id}`),

    create: (data: any) =>
        request<any>('/interventions', { method: 'POST', body: JSON.stringify(data) }),
};

// ─── Proactive Service ──────────────────────────────────
export const proactiveApi = {
    getPredictions: () =>
        request<any[]>('/proactive/predictions'),

    trigger: (data: any) =>
        request<any>('/proactive/trigger', { method: 'POST', body: JSON.stringify(data) }),
};

// ─── Voice Prints ───────────────────────────────────────
export const voicePrintApi = {
    list: () =>
        request<any[]>('/auth/voiceprint'),

    register: (data: any) =>
        request<any>('/auth/voiceprint', { method: 'POST', body: JSON.stringify(data) }),

    verify: (audio: Blob | any, speakerId?: string) =>
        request<any>('/auth/voiceprint/verify', { method: 'POST', body: JSON.stringify(audio instanceof Blob ? {} : audio) }),

    delete: (id: string) =>
        request<void>(`/auth/voiceprint/${id}`, { method: 'DELETE' }),

    update: (id: string, data: any) =>
        request<any>(`/auth/voiceprint/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),

    getVerifySettings: () =>
        request<any>('/auth/voiceprint/settings'),

    updateVerifySettings: (data: { command: string; enabled: boolean }) =>
        request<any>('/auth/voiceprint/settings', { method: 'PUT', body: JSON.stringify(data) }),

    verifyWithCommand: (data: { audio: Blob; command: string; speakerId?: string }) =>
        request<any>('/auth/voiceprint/verify-command', { method: 'POST', body: JSON.stringify(data) }),
};

// ─── Offices ─────────────────────────────────────────────
export const officeApi = {
    list: () =>
        request<any[]>('/office'),

    create: (data: any) =>
        request<any>('/office', { method: 'POST', body: JSON.stringify(data) }),
};

// ─── Reception ───────────────────────────────────────────
export const receptionApi = {
    getVisitors: () =>
        request<any[]>('/reception/visitors'),

    checkIn: (data: any) =>
        request<any>('/reception/check-in', { method: 'POST', body: JSON.stringify(data) }),
};

// ─── Evolution ───────────────────────────────────────────
export const evolutionApi = {
    getStatus: () =>
        request<any>('/admin/evolution/status'),

    trigger: () =>
        request<any>('/admin/evolution/trigger', { method: 'POST' }),
};

// ─── Chairman Dashboard ──────────────────────────────────
export const chairmanApi = {
    getDashboard: () =>
        request<any>('/chairman/dashboard'),

    getEmployees: () =>
        request<any[]>('/chairman/employees'),

    getEmployee: (employeeId: string) =>
        request<any>(`/chairman/employees/${employeeId}`),

    updateEmployeeAccess: (employeeId: string, accessLevel: string) =>
        request<any>(`/chairman/employees/${employeeId}/access-level`, {
            method: 'POST',
            body: JSON.stringify({ accessLevel })
        }),

    getDepartments: () =>
        request<any[]>('/chairman/departments'),

    getSystemStatus: () =>
        request<any>('/chairman/system/status'),
};

// ─── LLM Providers ───────────────────────────────────────
export const llmProviderApi = {
    list: () =>
        request<any[]>('/enterprise/llm-providers'),

    listModels: () =>
        request<any[]>('/enterprise/llm-models'),

    createModel: (data: any) =>
        request<any>('/enterprise/llm-models', { method: 'POST', body: JSON.stringify(data) }),

    updateModel: (modelId: string, data: any) =>
        request<any>(`/enterprise/llm-models/${modelId}`, { method: 'PUT', body: JSON.stringify(data) }),

    deleteModel: (modelId: string) =>
        request<void>(`/enterprise/llm-models/${modelId}`, { method: 'DELETE' }),

    testModel: (modelId: string, prompt?: string) =>
        request<any>('/enterprise/llm-test', { method: 'POST', body: JSON.stringify({ modelId, prompt }) }),
};

// ─── Skills (Enterprise) ─────────────────────────────────
export const enterpriseSkillApi = {
    list: () =>
        request<any[]>('/enterprise/skills'),

    getByBrain: (brain: string) =>
        request<any[]>(`/enterprise/skills/by-brain/${brain}`),

    getCounts: () =>
        request<Record<string, number>>('/enterprise/skill-counts'),
};

// ─── Agent Skills ────────────────────────────────────────
export const agentSkillApi = {
    list: (agentId: string) =>
        request<any[]>(`/agents/${encodeURIComponent(agentId)}/skills`),

    bind: (agentId: string, skillName: string) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/skills/${skillName}`, { method: 'POST' }),

    unbind: (agentId: string, skillName: string) =>
        request<void>(`/agents/${encodeURIComponent(agentId)}/skills/${skillName}`, { method: 'DELETE' }),
};

// ─── Agent Sessions ──────────────────────────────────────
export const agentSessionApi = {
    list: (agentId: string) =>
        request<any[]>(`/agents/${encodeURIComponent(agentId)}/sessions`),

    create: (agentId: string, data?: any) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/sessions`, { method: 'POST', body: JSON.stringify(data) }),
};

// ─── Agent Actions ───────────────────────────────────────
export const agentActionApi = {
    getStatus: (agentId: string) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/status`),

    trigger: (agentId: string, action: string, payload?: any) =>
        request<any>(`/agents/${encodeURIComponent(agentId)}/action`, {
            method: 'POST',
            body: JSON.stringify({ action, payload })
        }),

    getActivity: (agentId: string, limit = 50) =>
        request<any[]>(`/agents/${encodeURIComponent(agentId)}/activity?limit=${limit}`),
};

// ─── Tasks (Global) ──────────────────────────────────────
export const globalTaskApi = {
    list: () =>
        request<any[]>('/tasks'),

    create: (data: any) =>
        request<any>('/tasks', { method: 'POST', body: JSON.stringify(data) }),

    get: (taskId: string) =>
        request<any>(`/tasks/${taskId}`),

    update: (taskId: string, data: any) =>
        request<any>(`/tasks/${taskId}`, { method: 'PUT', body: JSON.stringify(data) }),

    delete: (taskId: string) =>
        request<void>(`/tasks/${taskId}`, { method: 'DELETE' }),

    checkout: (taskId: string) =>
        request<any>(`/tasks/${taskId}/checkout`, { method: 'POST' }),

    complete: (taskId: string) =>
        request<any>(`/tasks/${taskId}/complete`, { method: 'POST' }),

    release: (taskId: string) =>
        request<any>(`/tasks/${taskId}/release`, { method: 'POST' }),

    reassign: (taskId: string, employeeId: string) =>
        request<any>(`/tasks/${taskId}/reassign`, {
            method: 'POST',
            body: JSON.stringify({ employee_id: employeeId })
        }),

    getStatistics: () =>
        request<any>('/tasks/statistics'),

    getPending: () =>
        request<any[]>('/tasks/pending'),

    getMyTasks: (status?: string) => {
        const params = new URLSearchParams();
        if (status) params.set('status', status);
        return request<any[]>(`/tasks/my${params.toString() ? `?${params.toString()}` : ''}`);
    },
};

// ─── Project Actions ─────────────────────────────────────
export const projectActionApi = {
    start: (projectId: string) =>
        request<any>(`/projects/${projectId}/start`, { method: 'POST' }),

    complete: (projectId: string) =>
        request<any>(`/projects/${projectId}/complete`, { method: 'POST' }),

    hold: (projectId: string) =>
        request<any>(`/projects/${projectId}/hold`, { method: 'POST' }),

    advancePhase: (projectId: string, phase: string) =>
        request<any>(`/projects/${projectId}/phases/${phase}/advance`, { method: 'POST' }),

    getProgress: (projectId: string) =>
        request<any>(`/projects/${projectId}/progress`),

    setPhaseProgress: (projectId: string, phase: string, progress: number) =>
        request<any>(`/projects/${projectId}/phases/${phase}/progress`, {
            method: 'PUT',
            body: JSON.stringify({ progress })
        }),

    getStatistics: () =>
        request<any>('/projects/statistics'),
};

// ─── Knowledge Base Extended ─────────────────────────────
export const knowledgeExtendedApi = {
    search: (query: string) =>
        request<any[]>(`/knowledge/search?q=${encodeURIComponent(query)}`),

    getCategories: () =>
        request<string[]>('/knowledge/categories'),

    getByCategory: (category: string) =>
        request<any[]>(`/knowledge/category/${category}`),

    getStats: () =>
        request<any>('/knowledge/stats'),

    getFavorites: () =>
        request<any[]>('/knowledge/favorites'),

    addFavorite: (id: string) =>
        request<any>(`/knowledge/${id}/favorite`, { method: 'POST' }),

    removeFavorite: (id: string) =>
        request<void>(`/knowledge/${id}/favorite`, { method: 'DELETE' }),

    // P2-2: Knowledge effect feedback
    submitFeedback: (id: string, helpful: boolean) =>
        request<void>(`/knowledge/${id}/feedback`, {
            method: 'POST',
            body: JSON.stringify({ helpful }),
        }),
};

// ─── Intervention Extended ───────────────────────────────
export const interventionExtendedApi = {
    get: (id: string) =>
        request<any>(`/interventions/${id}`),

    respond: (id: string, response: string) =>
        request<any>(`/interventions/${id}/respond`, {
            method: 'POST',
            body: JSON.stringify({ response })
        }),

    escalate: (id: string, reason?: string) =>
        request<any>(`/interventions/${id}/escalate`, {
            method: 'POST',
            body: JSON.stringify({ reason })
        }),

    getStatistics: () =>
        request<any>('/interventions/statistics'),

    listRules: () =>
        request<any[]>('/interventions/rules'),

    registerRule: (data: any) =>
        request<any>('/interventions/rules', { method: 'POST', body: JSON.stringify(data) }),

    unregisterRule: (ruleId: string) =>
        request<void>(`/interventions/rules/${ruleId}`, { method: 'DELETE' }),

    evaluate: (action: string, context?: any) =>
        request<any>('/intervention/evaluate', {
            method: 'POST',
            body: JSON.stringify({ action, context })
        }),

    getPendingDecisions: () =>
        request<any[]>('/intervention/pending'),
};

// ─── Proactive Extended ──────────────────────────────────
export const proactiveExtendedApi = {
    getDigest: () =>
        request<any>('/proactive/digest'),

    listHabits: () =>
        request<any[]>('/proactive/habits'),

    createHabit: (data: any) =>
        request<any>('/proactive/habits', { method: 'POST', body: JSON.stringify(data) }),

    updateHabit: (id: string, data: any) =>
        request<any>(`/proactive/habits/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

    deleteHabit: (id: string) =>
        request<void>(`/proactive/habits/${id}`, { method: 'DELETE' }),

    checkinHabit: (habitId: string) =>
        request<any>(`/proactive/habits/${habitId}/checkin`, { method: 'POST' }),

    listNotifications: () =>
        request<any[]>('/proactive/notifications'),

    markNotificationRead: (id: string) =>
        request<any>(`/proactive/notifications/${id}/read`, { method: 'POST' }),

    markAllNotificationsRead: () =>
        request<any>('/proactive/notifications/read-all', { method: 'POST' }),

    listMeetingNotes: () =>
        request<any[]>('/proactive/meeting-notes'),

    getMeetingNote: (id: string) =>
        request<any>(`/proactive/meeting-notes/${id}`),

    getAnalytics: () =>
        request<any>('/proactive/analytics'),

    getSuggestions: () =>
        request<any[]>('/proactive/suggestions'),
};

// ─── Reception Extended ──────────────────────────────────
export const receptionExtendedApi = {
    getStatus: () =>
        request<any>('/reception/status'),

    chat: (message: string, context?: any) =>
        request<any>('/reception/chat', {
            method: 'POST',
            body: JSON.stringify({ message, context })
        }),

    chatStream: (message: string, context?: any) =>
        request<any>('/reception/chat/stream', {
            method: 'POST',
            body: JSON.stringify({ message, context })
        }),
};

// ─── Office Extended ─────────────────────────────────────
export const officeExtendedApi = {
    getStatus: () =>
        request<any>('/office/status'),

    listAgents: () =>
        request<any[]>('/office/agents'),

    getAgent: (id: string) =>
        request<any>(`/office/agents/${encodeURIComponent(id)}`),

    updateAgentState: (state: any) =>
        request<any>('/office/agent/state', { method: 'POST', body: JSON.stringify(state) }),

    listAreas: () =>
        request<any[]>('/office/areas'),

    getDepartmentStatus: (department: string) =>
        request<OfficeDepartmentStatusResponse>(`/office/department/${encodeURIComponent(department)}`),

    getYesterdayMemo: () =>
        request<any>('/office/yesterday-memo'),
};

// ─── Plaza ───────────────────────────────────────────────
export const plazaApi = {
    listPosts: () =>
        request<any[]>('/plaza/posts'),

    createPost: (data: any) =>
        request<any>('/plaza/posts', { method: 'POST', body: JSON.stringify(data) }),

    getStats: () =>
        request<any>('/plaza/stats'),

    likePost: (postId: string) =>
        request<any>(`/plaza/posts/${postId}/like`, { method: 'POST' }),
};

// ─── Evolution Extended ──────────────────────────────────
export const evolutionExtendedApi = {
    getResults: () =>
        request<any[]>('/admin/evolution/results'),

    getResult: (resultId: string) =>
        request<any>(`/admin/evolution/results/${resultId}`),

    extractSignals: () =>
        request<any>('/admin/evolution/extract-signals', { method: 'POST' }),

    listSkills: () =>
        request<any[]>('/admin/skills'),

    getSkill: (name: string) =>
        request<any>(`/admin/skills/${name}`),

    reloadSkills: () =>
        request<any>('/admin/skills/reload', { method: 'POST' }),

    generateSkill: (data: any) =>
        request<any>('/admin/skills/generate', { method: 'POST', body: JSON.stringify(data) }),

    installSkill: (skillName: string) =>
        request<any>(`/admin/skills/${skillName}/install`, { method: 'POST' }),

    uninstallSkill: (skillName: string) =>
        request<void>(`/admin/skills/${skillName}`, { method: 'DELETE' }),

    bindSkill: (skillName: string, neuronId: string) =>
        request<any>(`/admin/skills/${skillName}/bind/${neuronId}`, { method: 'POST' }),

    unbindSkill: (skillName: string, neuronId: string) =>
        request<any>(`/admin/skills/${skillName}/bind/${neuronId}`, { method: 'DELETE' }),

    listBindings: () =>
        request<any[]>('/admin/bindings'),

    getHotreloadStatus: () =>
        request<any>('/admin/hotreload/status'),

    triggerHotreload: () =>
        request<any>('/admin/hotreload/trigger', { method: 'POST' }),
};

// ─── VoicePrint Extended ─────────────────────────────────
export const voicePrintExtendedApi = {
    login: (audioBlob: Blob, threshold?: number) => {
        const formData = new FormData();
        formData.append('audio', audioBlob, 'recording.webm');
        if (threshold) formData.append('threshold', String(threshold));
        return request<any>('/auth/voiceprint/login', { method: 'POST', body: formData as any });
    },

    getStatus: () =>
        request<any>('/auth/voiceprint/status'),

    register: (audioBlob: Blob, speakerId: string, name?: string) => {
        const formData = new FormData();
        formData.append('audio', audioBlob, 'recording.webm');
        formData.append('speaker_id', speakerId);
        if (name) formData.append('name', name);
        return request<any>('/auth/voiceprint/register', { method: 'POST', body: formData as any });
    },

    list: () =>
        request<any[]>('/auth/voiceprint'),

    verify: (audioBlob: Blob, speakerId: string, threshold?: number) => {
        const formData = new FormData();
        formData.append('audio', audioBlob, 'recording.webm');
        formData.append('speaker_id', speakerId);
        if (threshold) formData.append('threshold', String(threshold));
        return request<any>('/auth/voiceprint/verify', { method: 'POST', body: formData as any });
    },
};

// ─── System Settings Extended ────────────────────────────
export const systemSettingsExtendedApi = {
    getByCategory: (category: string) =>
        request<any>(`/chairman/settings/${category}`),

    getSetting: (category: string, key: string) =>
        request<any>(`/chairman/settings/${category}/${key}`),

    updateSetting: (category: string, key: string, value: any) =>
        request<any>(`/chairman/settings/${category}/${key}`, {
            method: 'PUT',
            body: JSON.stringify({ value })
        }),

    batchUpdate: (data: any) =>
        request<any>('/chairman/settings/batch', { method: 'POST', body: JSON.stringify(data) }),

    getHistory: () =>
        request<any[]>('/chairman/settings/history'),

    resetSetting: (category: string, key: string) =>
        request<any>(`/chairman/settings/${category}/${key}/reset`, { method: 'POST' }),

    listCategories: () =>
        request<string[]>('/chairman/settings/categories'),
};

// ─── Tenant Extended ─────────────────────────────────────
export const tenantExtendedApi = {
    get: (tenantId: string) =>
        request<any>(`/tenants/${tenantId}`),

    update: (tenantId: string, data: any) =>
        request<any>(`/tenants/${tenantId}`, { method: 'PUT', body: JSON.stringify(data) }),

    resolveByDomain: (domain: string) =>
        request<any>(`/tenants/resolve-by-domain?domain=${encodeURIComponent(domain)}`),
};

// ─── System Extended ─────────────────────────────────────
export const systemExtendedApi = {
    getHealth: () =>
        request<any>('/health'),

    getStatus: () =>
        request<any>('/status'),

    startSession: (sessionId: string) =>
        request<any>(`/session/${sessionId}/start`, { method: 'POST' }),

    endSession: (sessionId: string) =>
        request<any>(`/session/${sessionId}/end`, { method: 'POST' }),

    getSessionStatus: (sessionId: string) =>
        request<any>(`/session/${sessionId}/status`),

    listProviders: () =>
        request<any[]>('/system/config/providers'),

    updateProvider: (providerId: string, data: any) =>
        request<any>(`/system/config/providers/${providerId}`, { method: 'PUT', body: JSON.stringify(data) }),
};

// ─── Misc ────────────────────────────────────────────────
export const miscApi = {
    getVersion: () =>
        request<any>('/version'),

    getUnreadNotifications: () =>
        request<{ unread_count: number }>('/notifications/unread-count'),
};

// ─── Vital Signs Dashboard (P0-4) ──────────────────────
export const vitalsApi = {
    getCurrent: () =>
        request<any>('/vitals'),

    getHistory: (minutes?: number) =>
        request<any[]>(`/vitals/history?minutes=${minutes || 30}`),
};

// ─── Code Review (P0-2) ────────────────────────────────
export const codeReviewApi = {
    listReviews: (status?: string) =>
        request<any[]>(`/collaborations?type=PEER_REVIEW${status ? `&status=${status}` : ''}`),

    getReview: (id: string) =>
        request<any>(`/collaborations/${id}`),

    submitCode: (id: string, data: any) =>
        request<any>(`/collaborations/${id}/submit`, { method: 'POST', body: JSON.stringify(data) }),

    approveReview: (id: string, comment?: string) =>
        request<any>(`/collaborations/${id}/approve`, { method: 'POST', body: JSON.stringify({ comment }) }),

    requestChanges: (id: string, comment: string) =>
        request<any>(`/collaborations/${id}/request-changes`, { method: 'POST', body: JSON.stringify({ comment }) }),
};

// ─── Memory Management (P0-3) ─────────────────────────────
export const memoryApi = {
    getMemories: (params?: { employeeId?: string; type?: string; limit?: number }) => {
        const qs = new URLSearchParams();
        if (params?.employeeId) qs.set('employeeId', params.employeeId);
        if (params?.type) qs.set('type', params.type);
        if (params?.limit) qs.set('limit', String(params.limit));
        const query = qs.toString();
        return request<any[]>(`/memories${query ? '?' + query : ''}`);
    },

    getMemory: (id: string) =>
        request<any>(`/memories/${id}`),

    deleteMemory: (id: string) =>
        request<void>(`/memories/${id}`, { method: 'DELETE' }),

    getMemoryStats: () =>
        request<any>('/memories/stats'),

    searchMemories: (query: string) =>
        request<any[]>(`/memories/search?q=${encodeURIComponent(query)}`),
};

