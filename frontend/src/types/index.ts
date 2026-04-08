/** Shared TypeScript types for Living Agent Service */

// ─── 用户身份与权限 ───

export type UserIdentity = 'INTERNAL_ACTIVE' | 'INTERNAL_PROBATION' | 'INTERNAL_DEPARTED' | 'INTERNAL_CHAIRMAN' | 'EXTERNAL_VISITOR' | 'EXTERNAL_PARTNER';
export type AccessLevel = 'CHAT_ONLY' | 'LIMITED' | 'DEPARTMENT' | 'FULL';

export interface User {
    id: string;
    username: string;
    email: string;
    display_name: string;
    avatar_url?: string;
    role: 'platform_admin' | 'org_admin' | 'agent_admin' | 'member';
    tenant_id?: string;
    title?: string;
    department_id?: string;
    department_name?: string;
    feishu_open_id?: string;
    dingtalk_open_id?: string;
    wecom_open_id?: string;
    identity?: UserIdentity;
    access_level?: AccessLevel;
    is_active: boolean;
    created_at: string;
}

// ─── 部门定义 ───

export type DepartmentCode = 'tech' | 'admin' | 'sales' | 'hr' | 'finance' | 'cs' | 'legal' | 'ops';

export interface Department {
    id: string;
    code: DepartmentCode;
    name: string;
    name_en: string;
    description?: string;
    brain_id?: string;
    parent_id?: string;
    member_count?: number;
    created_at: string;
}

export const DEPARTMENTS: Record<DepartmentCode, { name: string; name_en: string; icon: string }> = {
    tech: { name: '技术部', name_en: 'Technology', icon: '💻' },
    admin: { name: '行政部', name_en: 'Administration', icon: '📋' },
    sales: { name: '销售部', name_en: 'Sales', icon: '📈' },
    hr: { name: '人力资源', name_en: 'Human Resources', icon: '👥' },
    finance: { name: '财务部', name_en: 'Finance', icon: '💰' },
    cs: { name: '客服部', name_en: 'Customer Service', icon: '🎧' },
    legal: { name: '法务部', name_en: 'Legal', icon: '⚖️' },
    ops: { name: '运营部', name_en: 'Operations', icon: '⚙️' },
};

// ─── 项目管理 ───

export type ProjectStatus = 'planning' | 'active' | 'on_hold' | 'completed' | 'cancelled';
export type ProjectPhase = 'initiation' | 'planning' | 'execution' | 'monitoring' | 'closure';

export interface Project {
    id: string;
    name: string;
    description?: string;
    status: ProjectStatus;
    phase: ProjectPhase;
    department_id: string;
    owner_id: string;
    owner_name?: string;
    start_date?: string;
    end_date?: string;
    budget?: number;
    progress?: number;
    team_members?: string[];
    created_at: string;
    updated_at: string;
}

export interface ProjectTask {
    id: string;
    project_id: string;
    title: string;
    description?: string;
    status: 'pending' | 'in_progress' | 'completed' | 'blocked';
    priority: 'low' | 'medium' | 'high' | 'urgent';
    assignee_id?: string;
    assignee_name?: string;
    due_date?: string;
    completed_at?: string;
    created_at: string;
}

// ─── 审批流程 ───

export type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'cancelled';
export type ApprovalType = 'leave' | 'expense' | 'purchase' | 'contract' | 'project' | 'other';

export interface ApprovalRecord {
    id: string;
    type: ApprovalType;
    title: string;
    description?: string;
    status: ApprovalStatus;
    applicant_id: string;
    applicant_name?: string;
    department_id?: string;
    amount?: number;
    attachments?: string[];
    current_step: number;
    total_steps: number;
    created_at: string;
    updated_at: string;
}

export interface ApprovalStep {
    id: string;
    approval_id: string;
    step_number: number;
    approver_id: string;
    approver_name?: string;
    status: 'pending' | 'approved' | 'rejected';
    comment?: string;
    acted_at?: string;
}

// ─── 神经元与数字员工 ───

export type NeuronType = 'Qwen3Neuron' | 'BitNetNeuron' | 'EyeNeuron' | 'CAMNeuron';
export type AgentStatus = 'creating' | 'running' | 'idle' | 'stopped' | 'error';

export interface Agent {
    id: string;
    name: string;
    avatar_url?: string;
    role_description: string;
    bio?: string;
    status: 'creating' | 'running' | 'idle' | 'stopped' | 'error';
    creator_id: string;
    primary_model_id?: string;
    fallback_model_id?: string;
    autonomy_policy: Record<string, string>;
    tokens_used_today: number;
    tokens_used_month: number;
    max_tokens_per_day?: number;
    max_tokens_per_month?: number;
    heartbeat_enabled: boolean;
    heartbeat_interval_minutes: number;
    heartbeat_active_hours: string;
    last_heartbeat_at?: string;
    timezone?: string;
    context_window_size?: number;
    agent_type?: 'native' | 'openclaw';
    openclaw_last_seen?: string;
    created_at: string;
    last_active_at?: string;
}

export interface Task {
    id: string;
    agent_id: string;
    title: string;
    description?: string;
    type: 'todo' | 'supervision';
    status: 'pending' | 'doing' | 'done' | 'paused';
    priority: 'low' | 'medium' | 'high' | 'urgent';
    assignee: string;
    created_by: string;
    creator_username?: string;
    due_date?: string;
    supervision_target_name?: string;
    supervision_channel?: string;
    remind_schedule?: string;
    created_at: string;
    updated_at: string;
    completed_at?: string;
}

export interface ChatMessage {
    id: string;
    agent_id: string;
    user_id: string;
    role: 'user' | 'assistant' | 'system';
    content: string;
    created_at: string;
}

export interface TokenResponse {
    access_token: string;
    token_type: string;
    user: User;
    needs_company_setup?: boolean;
}
