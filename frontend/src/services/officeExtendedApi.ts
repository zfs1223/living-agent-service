import { request } from './apiBase';

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

export interface OfficeDepartmentStatusResponse extends OfficeDepartmentSnapshot {
    department: string;
    departmentName?: string;
    updatedAt?: number;
    status?: string;
    agents: OfficeDepartmentAgentSnapshot[];
    events: any[];
}

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
