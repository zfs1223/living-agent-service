import { AgentLike } from './types';
import { getZoneByStatus, normalizeStatus } from './status';

export type OfficeZoneId = 'workstation' | 'collaboration' | 'lounge' | 'alert' | 'offline';
export type OfficeMotionKind = 'arrive' | 'walk' | 'sit' | 'standby' | 'alert' | 'return';
export type OfficeSeatPosition = 'front' | 'middle' | 'back' | 'corner' | 'aisle';
export type OfficeOutfit = 'support' | 'tech' | 'finance' | 'legal' | 'ops' | 'hr' | 'sales' | 'admin' | 'default';

export type OfficeMotionProfile = {
  zone: OfficeZoneId;
  lane: 0 | 1 | 2 | 3;
  seat: OfficeSeatPosition;
  pace: 'slow' | 'normal' | 'fast';
  mood: 'calm' | 'focused' | 'resting' | 'urgent';
  holdMs: number;
  jitterMs: number;
  motionKind: OfficeMotionKind;
  outfit: OfficeOutfit;
  pose: 'stand' | 'walk' | 'sit' | 'alert';
};

export type OfficeTransition = {
  zone: OfficeZoneId;
  prevZone: OfficeZoneId;
  at: number;
  holdMs: number;
  phase: 'entering' | 'moving' | 'settling' | 'idle' | 'returning';
  motionKind: OfficeMotionKind;
  direction: 'in' | 'out' | 'stationary';
  progress: number;
};

export type OfficeEvent = {
  id: string;
  agentId: string;
  agentName: string;
  fromZone: OfficeZoneId;
  toZone: OfficeZoneId;
  action: 'arrive' | 'walk' | 'sit' | 'return' | 'alert' | 'offline' | 'task-start' | 'task-complete';
  at: number;
  message: string;
  severity: 'info' | 'success' | 'warning' | 'error';
  category: 'movement' | 'status' | 'task' | 'alert';
};

export type OfficeEventInput = Partial<Pick<OfficeEvent, 'action' | 'fromZone' | 'toZone' | 'severity' | 'category'>> & {
  agentId: string;
  agentName: string;
  status?: string;
  taskState?: 'pending' | 'running' | 'complete' | 'error';
  occurredAt?: number;
};

const MOTION_BY_STATUS: Record<string, OfficeMotionProfile> = {
  // 工作状态
  online: { zone: 'workstation', lane: 0, seat: 'front', pace: 'normal', mood: 'focused', holdMs: 8000, jitterMs: 180, motionKind: 'walk', outfit: 'tech', pose: 'walk' },
  running: { zone: 'workstation', lane: 0, seat: 'front', pace: 'fast', mood: 'focused', holdMs: 8000, jitterMs: 180, motionKind: 'walk', outfit: 'tech', pose: 'walk' },
  active: { zone: 'workstation', lane: 1, seat: 'middle', pace: 'fast', mood: 'focused', holdMs: 8000, jitterMs: 180, motionKind: 'walk', outfit: 'ops', pose: 'walk' },
  busy: { zone: 'collaboration', lane: 1, seat: 'aisle', pace: 'normal', mood: 'urgent', holdMs: 10000, jitterMs: 240, motionKind: 'walk', outfit: 'support', pose: 'walk' },
  learning: { zone: 'workstation', lane: 0, seat: 'front', pace: 'normal', mood: 'focused', holdMs: 12000, jitterMs: 200, motionKind: 'walk', outfit: 'tech', pose: 'walk' },
  evolving: { zone: 'workstation', lane: 0, seat: 'front', pace: 'fast', mood: 'urgent', holdMs: 10000, jitterMs: 220, motionKind: 'walk', outfit: 'tech', pose: 'walk' },

  // 离开/休息状态
  away: { zone: 'lounge', lane: 2, seat: 'corner', pace: 'slow', mood: 'resting', holdMs: 15000, jitterMs: 320, motionKind: 'sit', outfit: 'default', pose: 'sit' },
  idle: { zone: 'lounge', lane: 2, seat: 'corner', pace: 'slow', mood: 'resting', holdMs: 15000, jitterMs: 320, motionKind: 'sit', outfit: 'default', pose: 'sit' },

  // 离线/休眠状态
  offline: { zone: 'offline', lane: 3, seat: 'corner', pace: 'slow', mood: 'calm', holdMs: 30000, jitterMs: 0, motionKind: 'standby', outfit: 'default', pose: 'stand' },
  stopped: { zone: 'offline', lane: 3, seat: 'corner', pace: 'slow', mood: 'calm', holdMs: 30000, jitterMs: 0, motionKind: 'standby', outfit: 'default', pose: 'stand' },
  inactive: { zone: 'offline', lane: 3, seat: 'corner', pace: 'slow', mood: 'calm', holdMs: 30000, jitterMs: 0, motionKind: 'standby', outfit: 'default', pose: 'stand' },
  dormant: { zone: 'offline', lane: 3, seat: 'back', pace: 'slow', mood: 'calm', holdMs: 30000, jitterMs: 0, motionKind: 'standby', outfit: 'default', pose: 'stand' },
  archived: { zone: 'offline', lane: 3, seat: 'back', pace: 'slow', mood: 'calm', holdMs: 30000, jitterMs: 0, motionKind: 'standby', outfit: 'default', pose: 'stand' },
  terminated: { zone: 'offline', lane: 3, seat: 'corner', pace: 'slow', mood: 'calm', holdMs: 30000, jitterMs: 0, motionKind: 'standby', outfit: 'default', pose: 'stand' },

  // 异常/禁用状态
  disabled: { zone: 'alert', lane: 0, seat: 'front', pace: 'slow', mood: 'urgent', holdMs: 5000, jitterMs: 260, motionKind: 'alert', outfit: 'legal', pose: 'alert' },
  error: { zone: 'alert', lane: 0, seat: 'front', pace: 'fast', mood: 'urgent', holdMs: 5000, jitterMs: 260, motionKind: 'alert', outfit: 'legal', pose: 'alert' },
};

function getOutfitByAgent(agent?: AgentLike): OfficeOutfit {
  const dept = (agent?.department || agent?.code || '').toLowerCase();
  const name = `${agent?.name || ''}`.toLowerCase();
  const title = `${agent?.title || ''}`.toLowerCase();
  const combined = `${dept} ${name} ${title}`;
  if (combined.includes('法务') || combined.includes('legal') || dept.includes('legal')) return 'legal';
  if (combined.includes('财务') || combined.includes('finance') || dept.includes('finance')) return 'finance';
  if (combined.includes('运营') || combined.includes('ops') || dept.includes('ops')) return 'ops';
  if (combined.includes('客服') || combined.includes('support') || dept.includes('cs')) return 'support';
  if (combined.includes('技术') || combined.includes('tech') || dept.includes('tech')) return 'tech';
  if (combined.includes('人事') || combined.includes('hr') || dept.includes('hr')) return 'hr';
  if (combined.includes('销售') || combined.includes('sales') || dept.includes('sales')) return 'sales';
  if (combined.includes('行政') || combined.includes('admin') || dept.includes('admin')) return 'admin';
  return 'default';
}

export function getOfficeMotion(agent?: AgentLike): OfficeMotionProfile {
  const status = normalizeStatus(agent?.status);
  const base = MOTION_BY_STATUS[status] ?? MOTION_BY_STATUS.idle;
  return {
    ...base,
    zone: getZoneByStatus(agent?.status) as OfficeZoneId,
    outfit: getOutfitByAgent(agent) || base.outfit,
  };
}

export function getMotionDelay(agentId: string, lane: number) {
  const seed = agentId.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
  return (seed % 11) * 110 + lane * 170;
}

export function getDirection(prevZone: OfficeZoneId | undefined, zone: OfficeZoneId): OfficeTransition['direction'] {
  if (!prevZone || prevZone === zone) return 'stationary';
  if (prevZone === 'lounge' && zone === 'workstation') return 'in';
  if (prevZone === 'workstation' && zone === 'lounge') return 'out';
  return 'in';
}

export function getPhase(now: number, at: number, holdMs: number): OfficeTransition['phase'] {
  const elapsed = now - at;
  if (elapsed < 260) return 'entering';
  if (elapsed < 620) return 'moving';
  if (elapsed < holdMs) return 'settling';
  return 'idle';
}

export function getProgress(now: number, at: number, holdMs: number) {
  if (holdMs <= 0) return 1;
  return Math.max(0, Math.min(1, (now - at) / holdMs));
}

export function classifyOfficeEvent(action: OfficeEvent['action']) {
  if (action === 'task-complete') return { severity: 'success' as const, category: 'task' as const };
  if (action === 'task-start') return { severity: 'info' as const, category: 'task' as const };
  if (action === 'alert' || action === 'offline') return { severity: 'error' as const, category: 'alert' as const };
  if (action === 'sit' || action === 'return') return { severity: 'info' as const, category: 'movement' as const };
  return { severity: 'info' as const, category: 'movement' as const };
}

export function inferActionFromState(status?: string, taskState?: OfficeEventInput['taskState']): OfficeEvent['action'] {
  const normalized = normalizeStatus(status);
  if (taskState === 'complete') return 'task-complete';
  if (taskState === 'running') return 'task-start';
  if (normalized === 'error') return 'alert';
  if (normalized === 'stopped' || normalized === 'inactive') return 'offline';
  if (normalized === 'idle') return 'sit';
  return 'walk';
}
