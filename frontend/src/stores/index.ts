/** Global state management with Zustand */

import { create } from 'zustand';
import type { User, Agent, Department, DepartmentCode, AccessLevel, UserIdentity } from '../types';

interface AuthStore {
    user: User | null;
    token: string | null;
    setAuth: (user: User, token: string) => void;
    setUser: (user: User) => void;
    logout: () => void;
    isAuthenticated: () => boolean;
    // New fields for living-agent-service
    currentDepartment: Department | null;
    setCurrentDepartment: (dept: Department | null) => void;
}

export const useAuthStore = create<AuthStore>((set, get) => ({
    user: null,
    token: localStorage.getItem('token'),
    currentDepartment: null,

    setAuth: (user, token) => {
        localStorage.setItem('token', token);
        set({ user, token });
    },

    setUser: (user) => {
        set({ user });
    },

    logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('current_tenant_id');
        set({ user: null, token: null, currentDepartment: null });
    },

    isAuthenticated: () => !!get().token,

    setCurrentDepartment: (dept) => {
        set({ currentDepartment: dept });
    },
}));

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
