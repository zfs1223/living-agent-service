import { create } from 'zustand';

export type ConnectionQuality = 'good' | 'poor' | 'disconnected';

interface ConnectionState {
  isConnected: boolean;
  connectionId: string | null;
  lastHeartbeat: number | null;
  reconnectAttempts: number;
  connectionQuality: ConnectionQuality;
  wsUrl: string | null;
  setConnected: (id: string) => void;
  setDisconnected: () => void;
  setQuality: (quality: ConnectionQuality) => void;
  heartbeat: () => void;
  incrementReconnect: () => void;
  resetReconnect: () => void;
  setWsUrl: (url: string | null) => void;
}

export const useConnectionStore = create<ConnectionState>((set) => ({
  isConnected: false,
  connectionId: null,
  lastHeartbeat: null,
  reconnectAttempts: 0,
  connectionQuality: 'disconnected',
  wsUrl: null,

  setConnected: (id) => set({
    isConnected: true,
    connectionId: id,
    connectionQuality: 'good',
    reconnectAttempts: 0,
  }),

  setDisconnected: () => set({
    isConnected: false,
    connectionId: null,
    connectionQuality: 'disconnected',
  }),

  setQuality: (quality) => set({ connectionQuality: quality }),

  heartbeat: () => set({ lastHeartbeat: Date.now() }),

  incrementReconnect: () => set((s) => ({
    reconnectAttempts: s.reconnectAttempts + 1,
    connectionQuality: 'poor' as ConnectionQuality,
  })),

  resetReconnect: () => set({ reconnectAttempts: 0 }),

  setWsUrl: (url) => set({ wsUrl: url }),
}));
