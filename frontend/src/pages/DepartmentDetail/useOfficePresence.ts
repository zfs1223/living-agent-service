import { useEffect, useMemo, useRef, useState } from 'react';
import { AgentLike } from './types';
import { OfficeEvent, OfficeEventInput, OfficeZoneId, OfficeTransition, classifyOfficeEvent, getDirection, getOfficeMotion, getPhase, getProgress, getMotionDelay, inferActionFromState } from './officeMotion';
import { adaptBackendOfficeEvent, adaptSnapshot, officeEventKey, type BackendOfficeEvent, type OfficeDepartmentSnapshot } from './officeEventAdapter';
import { getZoneByStatus } from './status';

export type OfficePresence = {
  zones: Record<OfficeZoneId, AgentLike[]>;
  motionById: Record<string, ReturnType<typeof getOfficeMotion>>;
  transitionById: Record<string, OfficeTransition>;
  timeline: OfficeEvent[];
  emitEvent: (event: OfficeEventInput) => void;
  ingestEvents: (events: BackendOfficeEvent[]) => void;
  ingestSnapshot: (snapshot: OfficeDepartmentSnapshot) => void;
};

const COOLDOWN_MS = 1600;
const RETURN_MS = 2400;
const TIMELINE_LIMIT = 48;

function buildZoneMap(agents: AgentLike[]) {
  const map: Record<OfficeZoneId, AgentLike[]> = {
    workstation: [],
    collaboration: [],
    lounge: [],
    alert: [],
    offline: [],
  };
  agents.forEach((agent) => {
    map[getZoneByStatus(agent.status) as OfficeZoneId].push(agent);
  });
  return map;
}

function toEvent(agent: AgentLike, fromZone: OfficeZoneId, toZone: OfficeZoneId, action: OfficeEvent['action'], at: number, message: string): OfficeEvent {
  return {
    id: `${agent.id}-${at}-${action}`,
    agentId: agent.id,
    agentName: agent.name,
    fromZone,
    toZone,
    action,
    at,
    message,
    ...classifyOfficeEvent(action),
  };
}

function getTimelineMessage(agent: AgentLike, fromZone: OfficeZoneId, toZone: OfficeZoneId, action: OfficeEvent['action'], isChinese: boolean) {
  const name = agent.name;
  const zh: Record<OfficeEvent['action'], string> = {
    arrive: `${name} 抵达 ${toZone}。`,
    walk: `${name} 正在从 ${fromZone} 移动到 ${toZone}。`,
    sit: `${name} 在休息室待命。`,
    return: `${name} 完成任务后回到工位。`,
    alert: `${name} 进入告警状态。`,
    offline: `${name} 已离线。`,
    'task-start': `${name} 开始执行任务。`,
    'task-complete': `${name} 完成当前任务。`,
  };
  const en: Record<OfficeEvent['action'], string> = {
    arrive: `${name} arrived at ${toZone}.`,
    walk: `${name} is moving from ${fromZone} to ${toZone}.`,
    sit: `${name} is waiting in the lounge.`,
    return: `${name} returned to the workstation after finishing the task.`,
    alert: `${name} entered alert state.`,
    offline: `${name} is offline.`,
    'task-start': `${name} started a task.`,
    'task-complete': `${name} completed the current task.`,
  };
  return isChinese ? zh[action] : en[action];
}

function actionFromTransition(transition: OfficeTransition, agentStatus: string): OfficeEvent['action'] {
  if (transition.zone === 'alert') return 'alert';
  if (transition.zone === 'offline') return 'offline';
  if (transition.direction === 'out') return 'sit';
  if (transition.prevZone === 'lounge' && transition.zone === 'workstation') return 'return';
  if (agentStatus === 'idle') return 'sit';
  return transition.phase === 'entering' ? 'arrive' : 'walk';
}

function useStableTransitions(agents: AgentLike[], isChinese: boolean) {
  const [transitionState, setTransitionState] = useState<Record<string, OfficeTransition>>({});
  const [timeline, setTimeline] = useState<OfficeEvent[]>([]);
  const seenEventKeys = useRef(new Set<string>());
  const latestSnapshotRef = useRef<OfficeDepartmentSnapshot | null>(null);

  useEffect(() => {
    const now = Date.now();
    setTransitionState((prev) => {
      const next: Record<string, OfficeTransition> = {};
      for (const agent of agents) {
        const motion = getOfficeMotion(agent);
        const previous = prev[agent.id];
        const prevZone = previous?.zone ?? motion.zone;
        const currentZone = motion.zone;
        const changed = !previous || previous.zone !== currentZone;
        const elapsed = previous ? now - previous.at : Number.POSITIVE_INFINITY;
        const cooldownPassed = elapsed >= COOLDOWN_MS;
        const returnReady = previous?.zone === 'lounge' && currentZone === 'workstation' && elapsed >= RETURN_MS;
        const phase = getPhase(now, previous?.at ?? now, previous?.holdMs ?? motion.holdMs);
        const direction = getDirection(prevZone, currentZone);
        const progress = getProgress(now, previous?.at ?? now, previous?.holdMs ?? motion.holdMs);

        if (previous && !changed && !returnReady) {
          next[agent.id] = {
            ...previous,
            phase,
            motionKind: motion.motionKind,
            direction,
            progress,
          };
          continue;
        }

        const nextTransition: OfficeTransition = {
          zone: currentZone,
          prevZone,
          at: now,
          holdMs: motion.holdMs,
          phase: changed ? 'entering' : phase,
          motionKind: motion.motionKind,
          direction,
          progress,
        };

        if (changed && cooldownPassed) {
          const action = actionFromTransition(nextTransition, agent.status || '');
          const event = toEvent(agent, prevZone, currentZone, action, now, getTimelineMessage(agent, prevZone, currentZone, action, isChinese));
          const key = officeEventKey(event);
          if (!seenEventKeys.current.has(key)) {
            seenEventKeys.current.add(key);
            setTimeline((prevTimeline) => [event, ...prevTimeline].slice(0, TIMELINE_LIMIT));
          }
        }

        if (previous?.zone === 'lounge' && currentZone === 'workstation' && returnReady) {
          nextTransition.phase = 'returning';
        }

        if ((agent.status === 'stopped' || agent.status === 'inactive') && currentZone === 'offline') {
          nextTransition.phase = 'idle';
        }

        next[agent.id] = nextTransition;
      }

      return next;
    });
  }, [agents, isChinese]);

  const pushEvent = (event: OfficeEvent) => {
    const key = officeEventKey(event);
    if (seenEventKeys.current.has(key)) return;
    seenEventKeys.current.add(key);
    setTimeline((prev) => [event, ...prev].slice(0, TIMELINE_LIMIT));
  };

  const emitEvent = (event: OfficeEventInput) => {
    const now = event.occurredAt ?? Date.now();
    const action = event.action ?? inferActionFromState(event.status, event.taskState);
    const fromZone = (event.fromZone ?? 'lounge') as OfficeZoneId;
    const toZone = (event.toZone ?? getZoneByStatus(event.status) as OfficeZoneId);
    const agent: AgentLike = { id: event.agentId, name: event.agentName, status: event.status };
    const message = event.taskState === 'complete'
      ? `${event.agentName} completed a task.`
      : getTimelineMessage(agent, fromZone, toZone, action, false);
    pushEvent({ ...toEvent(agent, fromZone, toZone, action, now, message), ...classifyOfficeEvent(action) });
  };

  const ingestEvents = (events: BackendOfficeEvent[]) => {
    events.forEach((event) => pushEvent(adaptBackendOfficeEvent(event)));
  };

  const ingestSnapshot = (snapshot: OfficeDepartmentSnapshot) => {
    latestSnapshotRef.current = snapshot;
    const snapshotEvents = adaptSnapshot(snapshot);
    setTimeline(snapshotEvents.slice(0, TIMELINE_LIMIT));
    seenEventKeys.current = new Set(snapshotEvents.map(officeEventKey));
  };

  return { transitionState, timeline, emitEvent, ingestEvents, ingestSnapshot, latestSnapshotRef };
}

export default function useOfficePresence(agents: AgentLike[], isChinese = false) {
  const zones = useMemo(() => buildZoneMap(agents), [agents]);
  const stable = useStableTransitions(agents, isChinese);

  const motionById = useMemo(() => {
    return agents.reduce<Record<string, ReturnType<typeof getOfficeMotion>>>((acc, agent) => {
      acc[agent.id] = getOfficeMotion(agent);
      return acc;
    }, {});
  }, [agents]);

  const delayedZones = useMemo(() => {
    const result: Record<OfficeZoneId, AgentLike[]> = {
      workstation: [],
      collaboration: [],
      lounge: [],
      alert: [],
      offline: [],
    };

    Object.entries(zones).forEach(([zone, list]) => {
      list
        .slice()
        .sort((a, b) => getMotionDelay(a.id, 0) - getMotionDelay(b.id, 0))
        .forEach((agent) => {
          result[zone as OfficeZoneId].push(agent);
        });
    });

    return result;
  }, [zones]);

  const timeline = useMemo(() => {
    const entries = agents.map((agent) => {
      const motion = motionById[agent.id];
      const transition = stable.transitionState[agent.id];
      const fromZone = transition?.prevZone ?? motion.zone;
      const toZone = transition?.zone ?? motion.zone;
      const action = transition ? actionFromTransition(transition, agent.status || '') : inferActionFromState(agent.status, undefined);
      const at = transition?.at || Date.now();
      return toEvent(agent, fromZone, toZone, action, at, getTimelineMessage(agent, fromZone, toZone, action, isChinese));
    });
    return [...stable.timeline, ...entries].slice(0, TIMELINE_LIMIT);
  }, [agents, isChinese, motionById, stable.timeline, stable.transitionState]);

  return {
    zones: delayedZones,
    motionById,
    transitionById: stable.transitionState,
    timeline,
    emitEvent: stable.emitEvent,
    ingestEvents: stable.ingestEvents,
    ingestSnapshot: stable.ingestSnapshot,
  } as OfficePresence;
}
