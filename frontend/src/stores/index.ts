/** Global state management with Zustand */

import { create } from 'zustand';
import type { User, Agent, Department, DepartmentCode, AccessLevel, UserIdentity } from '../types';

interface AuthStore {
    user: User | null;
    token: string | null;
    lastActivity: number;
    setAuth: (user: User, token: string) => void;
    setUser: (user: User) => void;
    logout: () => void;
    isAuthenticated: () => boolean;
    updateLastActivity: () => void;
    // New fields for living-agent-service
    currentDepartment: Department | null;
    setCurrentDepartment: (dept: Department | null) => void;
}

const DEFAULT_IDLE_TIMEOUT = 30 * 60 * 1000;

function getIdleTimeout(): number {
    const stored = localStorage.getItem('idle_timeout_minutes');
    if (stored) {
        const minutes = parseInt(stored, 10);
        if (!isNaN(minutes) && minutes > 0) {
            return minutes * 60 * 1000;
        }
    }
    return DEFAULT_IDLE_TIMEOUT;
}

export const useAuthStore = create<AuthStore>((set, get) => ({
    user: null,
    token: localStorage.getItem('token'),
    lastActivity: Date.now(),
    currentDepartment: null,

    setAuth: (user, token) => {
        // accessToken 保留在内存中（Zustand store），用于 API 调用
        // refreshToken 已通过 HttpOnly Cookie 自动管理，无需手动存储
        localStorage.setItem('token', token);
        localStorage.setItem('last_activity', Date.now().toString());
        if (user.tenant_id) {
            localStorage.setItem('current_tenant_id', user.tenant_id);
        }
        set({ user, token, lastActivity: Date.now() });
    },

    setUser: (user) => {
        set({ user });
    },

    logout: () => {
        // 清除本地存储的 accessToken
        // refreshToken 的 HttpOnly Cookie 由后端 /api/auth/logout 接口清除
        localStorage.removeItem('token');
        localStorage.removeItem('current_tenant_id');
        localStorage.removeItem('last_activity');
        set({ user: null, token: null, currentDepartment: null, lastActivity: 0 });
    },

    isAuthenticated: () => !!get().token,

    updateLastActivity: () => {
        const now = Date.now();
        localStorage.setItem('last_activity', now.toString());
        set({ lastActivity: now });
    },

    setCurrentDepartment: (dept) => {
        set({ currentDepartment: dept });
    },
}));

/** Get the current auth token from the store (safe to call outside React) */
export function getToken(): string | null {
    return useAuthStore.getState().token;
}

interface AppStore {
    sidebarCollapsed: boolean;
    toggleSidebar: () => void;
    selectedAgentId: string | null;
    setSelectedAgent: (id: string | null) => void;
    // New fields for living-agent-service
    currentDepartmentCode: DepartmentCode | null;
    setCurrentDepartmentCode: (code: DepartmentCode | null) => void;
}

export const useAppStore = create<AppStore>((set) => ({
    sidebarCollapsed: localStorage.getItem('sidebar_collapsed') === 'true',
    toggleSidebar: () => set((s) => {
        const newState = !s.sidebarCollapsed;
        localStorage.setItem('sidebar_collapsed', String(newState));
        return { sidebarCollapsed: newState };
    }),
    selectedAgentId: null,
    setSelectedAgent: (id) => set({ selectedAgentId: id }),
    currentDepartmentCode: null,
    setCurrentDepartmentCode: (code) => set({ currentDepartmentCode: code }),
}));
