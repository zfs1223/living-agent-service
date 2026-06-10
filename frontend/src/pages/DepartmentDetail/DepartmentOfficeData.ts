import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { departmentApi } from '../../services/api';
import { officeExtendedApi, type OfficeDepartmentStatusResponse } from '../../services/officeExtendedApi';
import { AgentLike } from './types';
import { BackendOfficeEvent, OfficeDepartmentAgentSnapshot, OfficeDepartmentSnapshot } from './officeEventAdapter';

export type DepartmentOfficeSnapshotState = {
  department: any | null;
  brain: any | null;
  agents: AgentLike[];
  members: any[];
};

export type DepartmentOfficeSnapshotBundle = {
  snapshot: OfficeDepartmentSnapshot | null;
  state: DepartmentOfficeSnapshotState;
  events: BackendOfficeEvent[];
};

export type DepartmentOfficeData = DepartmentOfficeSnapshotBundle & {
  isLoading: boolean;
  isRefreshing: boolean;
  refresh: () => Promise<void>;
  snapshotKey: string;
};

function normalizeAgent(agent: any): AgentLike {
  return {
    id: agent.id || agent.agentId,
    name: agent.name || agent.agentName || agent.display_name || agent.username || 'Unknown',
    avatar: agent.avatar,
    title: agent.title,
    status: agent.status,
    current_task: agent.current_task || agent.currentTask,
    updated_at: agent.updated_at || agent.updatedAt,
    last_active_at: agent.last_active_at || agent.lastActiveAt,
  };
}

function normalizeEvents(events: BackendOfficeEvent[]) {
  return [...events].sort((a, b) => (b.occurredAt || 0) - (a.occurredAt || 0));
}

function normalizeOfficeSnapshot(snapshot: OfficeDepartmentStatusResponse | null): OfficeDepartmentSnapshot | null {
  if (!snapshot) return null;
  const agents: OfficeDepartmentAgentSnapshot[] | undefined = Array.isArray(snapshot.agents)
    ? snapshot.agents.map((agent) => ({
        ...agent,
        agentId: agent.agentId,
        agentName: agent.agentName || agent.name || 'Unknown',
      }))
    : undefined;
  return {
    department: snapshot.department,
    departmentName: snapshot.departmentName,
    updatedAt: snapshot.updatedAt,
    status: snapshot.status,
    agents,
    events: snapshot.events,
  };
}

function extractAgentsFromSnapshot(snapshot: OfficeDepartmentSnapshot | null): AgentLike[] | null {
  if (!snapshot?.agents?.length) return null;
  return snapshot.agents.map((agent) => normalizeAgent({
    id: agent.id || agent.agentId,
    agentId: agent.agentId,
    name: agent.agentName || agent.name,
    status: agent.status,
    current_task: agent.current_task || agent.currentTask,
    updated_at: agent.updated_at,
    last_active_at: agent.last_active_at,
    avatar: undefined,
    title: undefined,
  }));
}

function snapshotToState(snapshot: OfficeDepartmentSnapshot | null, fallbackState: DepartmentOfficeSnapshotState): DepartmentOfficeSnapshotState {
  if (!snapshot) return fallbackState;
  return {
    department: fallbackState.department,
    brain: fallbackState.brain,
    agents: fallbackState.agents,
    members: fallbackState.members,
  };
}

export default function useDepartmentOfficeData(code?: string): DepartmentOfficeData {
  const [snapshot, setSnapshot] = useState<OfficeDepartmentSnapshot | null>(null);
  const [state, setState] = useState<DepartmentOfficeSnapshotState>({ department: null, brain: null, agents: [], members: [] });
  const [events, setEvents] = useState<BackendOfficeEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const lastSyncRef = useRef<number>(0);

  const refresh = useCallback(async () => {
    if (!code) return;
    const now = Date.now();
    if (now - lastSyncRef.current < 1500 && lastSyncRef.current !== 0) return;
    lastSyncRef.current = now;

    setIsRefreshing(true);
    if (!state.department) setIsLoading(true);

    try {
      const dept = await departmentApi.getByCode(code);
      const [brainRes, agentRes, memberRes, officeRes] = await Promise.all([
        dept?.id ? departmentApi.getBrain(dept.id).catch(() => null) : Promise.resolve(null),
        dept?.id ? departmentApi.getAgents(dept.id).catch(() => []) : Promise.resolve([]),
        dept?.id ? departmentApi.getMembers(dept.id).catch(() => []) : Promise.resolve([]),
        officeExtendedApi.getDepartmentStatus(code).catch(() => null),
      ]);

      const normalizedSnapshot = normalizeOfficeSnapshot(officeRes);
      const snapshotAgents = extractAgentsFromSnapshot(normalizedSnapshot);
      const snapshotEvents = normalizedSnapshot?.events || [];

      const nextState: DepartmentOfficeSnapshotState = {
        department: dept,
        brain: brainRes,
        agents: snapshotAgents || (agentRes || []).map(normalizeAgent),
        members: memberRes || [],
      };

      setSnapshot(normalizedSnapshot);
      setState(snapshotToState(normalizedSnapshot, nextState));
      setEvents((prev) => normalizeEvents([...snapshotEvents, ...prev]).slice(0, 48));
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [code, state.department]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const snapshotKey = useMemo(() => `${snapshot?.updatedAt || ''}:${snapshot?.events?.[0]?.occurredAt || ''}`, [snapshot]);

  return {
    snapshot,
    state,
    events,
    isLoading,
    isRefreshing,
    refresh,
    snapshotKey,
  };
}
