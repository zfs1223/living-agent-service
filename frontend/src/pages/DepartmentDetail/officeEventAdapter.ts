import { OfficeEvent, OfficeEventInput, OfficeZoneId, classifyOfficeEvent, inferActionFromState } from './officeMotion';

export type BackendOfficeEvent = {
  id?: string;
  agentId: string;
  agentName: string;
  fromZone?: string;
  toZone?: string;
  status?: string;
  taskState?: 'pending' | 'running' | 'complete' | 'error';
  action?: OfficeEvent['action'];
  severity?: OfficeEvent['severity'];
  category?: OfficeEvent['category'];
  occurredAt?: number;
  message?: string;
};

export type OfficeDepartmentAgentSnapshot = {
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
};

export type OfficeDepartmentSnapshot = {
  department?: string;
  departmentName?: string;
  updatedAt?: number;
  status?: string;
  agents?: OfficeDepartmentAgentSnapshot[];
  events?: BackendOfficeEvent[];
};

export function normalizeZone(zone?: string): OfficeZoneId {
  if (zone === 'workstation' || zone === 'collaboration' || zone === 'lounge') return zone;
  if (zone?.includes('work')) return 'workstation';
  if (zone?.includes('collab')) return 'collaboration';
  // alert 和 offline 区域统一映射到 lounge
  return 'lounge';
}

export function adaptBackendOfficeEvent(event: BackendOfficeEvent): OfficeEvent {
  const action = event.action ?? inferActionFromState(event.status, event.taskState);
  const classification = classifyOfficeEvent(action);
  return {
    id: event.id || `${event.agentId}-${event.occurredAt || Date.now()}-${action}`,
    agentId: event.agentId,
    agentName: event.agentName,
    fromZone: normalizeZone(event.fromZone),
    toZone: normalizeZone(event.toZone || event.status),
    action,
    at: event.occurredAt || Date.now(),
    message: event.message || `${event.agentName} ${action}`,
    severity: event.severity || classification.severity,
    category: event.category || classification.category,
  };
}

export function adaptSnapshot(snapshot: OfficeDepartmentSnapshot): OfficeEvent[] {
  const events = snapshot.events?.map(adaptBackendOfficeEvent) || [];
  const agentEvents = snapshot.agents?.map((agent) => adaptBackendOfficeEvent({
    agentId: agent.agentId,
    agentName: agent.agentName || agent.name || 'Unknown',
    status: agent.status,
    taskState: agent.taskState,
    fromZone: agent.fromZone,
    toZone: agent.toZone,
    message: agent.message,
    occurredAt: agent.occurredAt,
  })) || [];
  return [...events, ...agentEvents].sort((a, b) => b.at - a.at);
}

export function officeEventKey(event: OfficeEvent | OfficeEventInput) {
  return [event.agentId, 'action' in event ? event.action : '', 'occurredAt' in event ? event.occurredAt || '' : '', 'toZone' in event ? event.toZone || '' : ''].join('|');
}
