import { getToken } from '../stores';

export const API_BASE = '/api';

export async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
    const token = getToken();
    const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };

    const res = await fetch(`${API_BASE}${url}`, {
        ...options,
        headers,
        credentials: 'include', // 确保 HttpOnly Cookie 随请求自动发送
    });

    if (!res.ok) {
        const isAuthEndpoint = url.startsWith('/auth/login')
            || url.startsWith('/auth/register')
            || url.startsWith('/auth/forgot-password')
            || url.startsWith('/auth/reset-password');
        if (res.status === 401 && !isAuthEndpoint) {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            window.location.href = '/login';
            throw new Error('Session expired');
        }
        const error = await res.json().catch(() => ({ detail: 'Request failed' }));
        if (error.success === false && error.error) {
            throw new Error(error.errorDescription || error.error);
        }
        const fieldLabels: Record<string, string> = {
            name: '名称',
            role_description: '角色描述',
            agent_type: '智能体类型',
            primary_model_id: '主模型',
            max_tokens_per_day: '每日 Token 上限',
            max_tokens_per_month: '每月 Token 上限',
        };
        let message = '';
        if (Array.isArray(error.detail)) {
            message = error.detail.map((e: any) => {
                const field = e.loc?.slice(-1)[0] || '';
                const label = fieldLabels[field] || field;
                return label ? `${label}: ${e.msg}` : e.msg;
            }).join('; ');
        } else {
            message = error.detail || `HTTP ${res.status}`;
        }
        const err = new Error(message) as Error & { status?: number };
        err.status = res.status;
        throw err;
    }

    if (res.status === 204) return undefined as T;
    const json = await res.json();
    if (json && typeof json === 'object' && 'success' in json && 'data' in json) {
        return json.data as T;
    }
    return json as T;
}
