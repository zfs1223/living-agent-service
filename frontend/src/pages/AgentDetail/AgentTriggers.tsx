import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { triggerApi, fileApi } from '../../services/api';
import { request } from '../../services/apiBase';

/** Convert rich schedule JSON to cron expression */
export function schedToCron(sched: { freq: string; interval: number; time: string; weekdays?: number[] }): string {
    const [h, m] = (sched.time || '09:00').split(':').map(Number);
    if (sched.freq === 'weekly') {
        const days = (sched.weekdays || [1, 2, 3, 4, 5]).join(',');
        return sched.interval > 1 ? `${m} ${h} * * ${days}` : `${m} ${h} * * ${days}`;
    }
    // daily
    if (sched.interval === 1) return `${m} ${h} * * *`;
    return `${m} ${h} */${sched.interval} * *`;
}

interface AgentTriggersProps {
    agentId: string;
    activityLogs: any[];
}

export default function AgentTriggers({ agentId, activityLogs }: AgentTriggersProps) {
    const { t, i18n } = useTranslation();

    // ── Aware tab data: triggers ──
    const { data: awareTriggers = [], refetch: refetchTriggers } = useQuery({
        queryKey: ['triggers', agentId],
        queryFn: () => triggerApi.list(agentId),
        enabled: !!agentId,
        refetchInterval: 5000,
    });

    // ── Aware tab data: focus.md ──
    const { data: focusFile } = useQuery({
        queryKey: ['file', agentId, 'focus.md'],
        queryFn: () => fileApi.read(agentId, 'focus.md').catch(() => null),
        enabled: !!agentId,
    });

    // ── Aware tab data: reflection sessions (trigger monologues) ──
    const { data: reflectionSessions = [] } = useQuery({
        queryKey: ['reflection-sessions', agentId],
        queryFn: async () => {
            try {
                const all = await request<any[]>(`/agents/${encodeURIComponent(agentId!)}/sessions?scope=all`);
                return (all || []).filter((s: any) => s.source_channel === 'trigger');
            } catch {
                return [];
            }
        },
        enabled: !!agentId,
        refetchInterval: 10000,
    });

    // ── Aware tab state ──
    const [expandedFocus, setExpandedFocus] = useState<string | null>(null);
    const [expandedReflection, setExpandedReflection] = useState<string | null>(null);
    const [reflectionMessages, setReflectionMessages] = useState<Record<string, any[]>>({});
    const [showAllFocus, setShowAllFocus] = useState(false);
    const [showCompletedFocus, setShowCompletedFocus] = useState(false);
    const [showAllTriggers, setShowAllTriggers] = useState(false);
    const REFLECTIONS_PAGE_SIZE = 10;
    const SECTION_PAGE_SIZE = 5;
    const [reflectionPage, setReflectionPage] = useState(0);

    // Parse focus.md into focus items with multi-line descriptions
    const raw = focusFile?.content || '';
    const lines = raw.split('\n');
    const focusItems: { id: string; name: string; description: string; done: boolean; inProgress: boolean }[] = [];
    let currentItem: any = null;
    for (const line of lines) {
        const match = line.match(/^\s*-\s*\[([ x/])\]\s*(.+)/i);
        if (match) {
            if (currentItem) focusItems.push(currentItem);
            const marker = match[1];
            const fullText = match[2].trim();
            const colonIdx = fullText.indexOf(':');
            const itemName = colonIdx > 0 ? fullText.substring(0, colonIdx).trim() : fullText;
            const itemDesc = colonIdx > 0 ? fullText.substring(colonIdx + 1).trim() : '';
            currentItem = {
                id: itemName,
                name: itemName,
                description: itemDesc,
                done: marker.toLowerCase() === 'x',
                inProgress: marker === '/',
            };
        } else if (currentItem && line.trim() && /^\s{2,}/.test(line)) {
            currentItem.description = currentItem.description
                ? currentItem.description + ' ' + line.trim()
                : line.trim();
        }
    }
    if (currentItem) focusItems.push(currentItem);

    // Helper: convert trigger config to natural language
    const triggerToHuman = (trig: any): string => {
        if (trig.type === 'cron' && trig.config?.expr) {
            const expr = trig.config.expr;
            const parts = expr.split(' ');
            if (parts.length >= 5) {
                const [min, hour, , , dow] = parts;
                const timeStr = `${hour.padStart(2, '0')}:${min.padStart(2, '0')}`;
                if (dow === '*' && min !== '*' && hour !== '*') return `Every day at ${timeStr}`;
                if (dow === '1-5' && min !== '*' && hour !== '*') return `Weekdays at ${timeStr}`;
                if (dow === '0' || dow === '7') return `Sundays at ${timeStr}`;
                if (hour === '*' && min === '0') {
                    if (dow === '1-5') return 'Every hour on weekdays';
                    return 'Every hour';
                }
                if (hour === '*' && min !== '*') return `Every hour at :${min.padStart(2, '0')}`;
            }
            return `Cron: ${expr}`;
        }
        if (trig.type === 'once' && trig.config?.at) {
            try {
                return `Once at ${new Date(trig.config.at).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}`;
            } catch { return `Once at ${trig.config.at}`; }
        }
        if (trig.type === 'interval' && trig.config?.minutes) {
            const m = trig.config.minutes;
            return m >= 60 ? `Every ${m / 60}h` : `Every ${m} min`;
        }
        if (trig.type === 'poll') return `Poll: ${trig.config?.url?.substring(0, 40) || 'URL'}`;
        if (trig.type === 'on_message') {
            return `On message from ${trig.config?.from_agent_name || trig.config?.from_user_name || 'unknown'}`;
        }
        if (trig.type === 'webhook') {
            return `Webhook${trig.config?.token ? ` (${trig.config.token.substring(0, 6)}...)` : ''}`;
        }
        return trig.type;
    };

    // Group triggers by focus_ref
    const triggersByFocus: Record<string, any[]> = {};
    const standaloneTriggers: any[] = [];
    for (const trig of awareTriggers) {
        if (trig.focus_ref && focusItems.some(f => f.name === trig.focus_ref)) {
            if (!triggersByFocus[trig.focus_ref]) triggersByFocus[trig.focus_ref] = [];
            triggersByFocus[trig.focus_ref].push(trig);
        } else {
            standaloneTriggers.push(trig);
        }
    }

    // Group activity logs by trigger name -> focus_ref
    const triggerLogsByFocus: Record<string, any[]> = {};
    const triggerNameToFocus: Record<string, string> = {};
    for (const trig of awareTriggers) {
        if (trig.focus_ref) triggerNameToFocus[trig.name] = trig.focus_ref;
    }
    const triggerRelatedLogs = activityLogs.filter((log: any) =>
        log.action_type === 'trigger_fired' || log.action_type === 'trigger_created' ||
        log.action_type === 'trigger_updated' || log.action_type === 'trigger_cancelled' ||
        log.summary?.includes('trigger')
    );
    for (const log of triggerRelatedLogs) {
        let matched = false;
        for (const [trigName, focusName] of Object.entries(triggerNameToFocus)) {
            if (log.summary?.includes(trigName) || log.detail?.tool === trigName) {
                if (!triggerLogsByFocus[focusName]) triggerLogsByFocus[focusName] = [];
                triggerLogsByFocus[focusName].push(log);
                matched = true;
                break;
            }
        }
        if (!matched) {
            if (!triggerLogsByFocus['__unmatched__']) triggerLogsByFocus['__unmatched__'] = [];
            triggerLogsByFocus['__unmatched__'].push(log);
        }
    }

    const hasFocusItems = focusItems.length > 0;
    const hasStandalone = standaloneTriggers.length > 0;
    const activeFocusItems = focusItems.filter(f => !f.done);
    const completedFocusItems = focusItems.filter(f => f.done);
    const visibleActiveFocus = showAllFocus ? activeFocusItems : activeFocusItems.slice(0, SECTION_PAGE_SIZE);
    const hiddenActiveCount = activeFocusItems.length - visibleActiveFocus.length;

    // Render a focus item row
    const renderFocusItem = (item: typeof focusItems[0]) => {
        const isExpanded = expandedFocus === item.id;
        const itemTriggers = triggersByFocus[item.name] || [];
        const itemLogs = triggerLogsByFocus[item.name] || [];
        const displayTitle = item.description || item.name;
        const displaySubtitle = item.description ? item.name : null;

        return (
            <div key={item.id} style={{
                borderRadius: '8px',
                border: '1px solid var(--border-subtle)',
                overflow: 'hidden',
                marginBottom: '6px',
                background: 'var(--bg-primary)',
            }}>
                <div
                    onClick={() => setExpandedFocus(isExpanded ? null : item.id)}
                    style={{ padding: '12px 16px', display: 'flex', alignItems: 'flex-start', gap: '12px', cursor: 'pointer', transition: 'background 0.15s' }}
                    onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg-secondary)')}
                    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                >
                    <div style={{ width: '8px', height: '8px', borderRadius: '50%', marginTop: '5px', flexShrink: 0, background: item.done ? 'var(--success, #10b981)' : item.inProgress ? 'var(--accent-primary)' : 'var(--border-subtle)' }} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: '13px', fontWeight: 500, lineHeight: '20px', textDecoration: item.done ? 'line-through' : 'none', color: item.done ? 'var(--text-tertiary)' : 'var(--text-primary)' }}>{displayTitle}</div>
                        {displaySubtitle && <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', fontFamily: 'monospace', marginTop: '2px' }}>{displaySubtitle}</div>}
                    </div>
                    {itemTriggers.length > 0 && (
                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', padding: '2px 8px', borderRadius: '10px', background: 'var(--bg-secondary)', whiteSpace: 'nowrap' }}>
                            {itemTriggers.length} trigger{itemTriggers.length > 1 ? 's' : ''}
                        </span>
                    )}
                    <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)', transition: 'transform 0.15s', marginTop: '4px' }}>&#9654;</span>
                </div>
                {isExpanded && (
                    <div style={{ padding: '0 16px 12px 36px', borderTop: '1px solid var(--border-subtle)' }}>
                        {itemTriggers.length > 0 && (
                            <div style={{ marginTop: '12px' }}>
                                {itemTriggers.map((trig: any) => (
                                    <div key={trig.id} style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '8px 12px', marginBottom: '4px', borderRadius: '6px', background: 'var(--bg-secondary)', opacity: trig.is_enabled ? 1 : 0.5 }}>
                                        <div style={{ flex: 1 }}>
                                            <div style={{ fontSize: '12px', fontWeight: 500, color: 'var(--text-primary)' }}>{triggerToHuman(trig)}</div>
                                            {trig.reason && <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{trig.reason}</div>}
                                            <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', marginTop: '2px', fontFamily: 'monospace' }}>{trig.type === 'cron' ? trig.config?.expr : ''}{' '}</div>
                                        </div>
                                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', whiteSpace: 'nowrap' }}>{t('agent.aware.fired', { count: trig.fire_count })}</span>
                                        {!trig.is_enabled && <span style={{ fontSize: '10px', color: 'var(--text-tertiary)' }}>{t('agent.aware.disabled')}</span>}
                                        <div style={{ display: 'flex', gap: '4px' }}>
                                            <button className="btn btn-ghost" style={{ padding: '2px 6px', fontSize: '11px' }} onClick={async (e) => { e.stopPropagation(); await triggerApi.update(agentId, trig.id, { is_enabled: !trig.is_enabled }); refetchTriggers(); }}>
                                                {trig.is_enabled ? t('agent.aware.disable') : t('agent.aware.enable')}
                                            </button>
                                            <button className="btn btn-ghost" style={{ padding: '2px 6px', fontSize: '11px', color: 'var(--error)' }} onClick={async (e) => { e.stopPropagation(); if (confirm(t('agent.aware.deleteTriggerConfirm', { name: trig.name }))) { await triggerApi.delete(agentId, trig.id); refetchTriggers(); } }}>
                                                {t('common.delete', 'Delete')}
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        {itemLogs.length > 0 && (
                            <div style={{ marginTop: '12px' }}>
                                <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-tertiary)', marginBottom: '6px' }}>{t('agent.aware.reflections')}</div>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                                    {itemLogs.slice(0, 10).map((log: any) => (
                                        <div key={log.id} style={{ padding: '6px 12px', borderRadius: '6px', background: 'var(--bg-secondary)', borderLeft: '2px solid var(--border-subtle)' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '2px' }}>
                                                <span style={{ fontSize: '10px', padding: '1px 5px', borderRadius: '3px', background: log.action_type === 'trigger_fired' ? 'rgba(var(--accent-primary-rgb, 99,102,241), 0.1)' : 'var(--bg-tertiary, #e5e7eb)', color: log.action_type === 'trigger_fired' ? 'var(--accent-primary)' : 'var(--text-tertiary)', fontWeight: 500 }}>{log.action_type?.replace('trigger_', '')}</span>
                                                <span style={{ fontSize: '10px', color: 'var(--text-tertiary)' }}>{new Date(log.created_at).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
                                            </div>
                                            <div style={{ fontSize: '12px', color: 'var(--text-secondary)', whiteSpace: 'pre-wrap' }}>{log.summary}</div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                        {itemTriggers.length === 0 && itemLogs.length === 0 && (
                            <div style={{ padding: '12px 0', fontSize: '12px', color: 'var(--text-tertiary)' }}>{t('agent.aware.noTriggers')}</div>
                        )}
                    </div>
                )}
            </div>
        );
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
            {/* ── Focus Section ── */}
            <div className="card" style={{ marginBottom: '16px', padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                    <div>
                        <h4 style={{ margin: 0, fontSize: '14px', fontWeight: 600 }}>{t('agent.aware.focus')}</h4>
                        <span style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>{t('agent.aware.focusDesc')}</span>
                    </div>
                    {hasFocusItems && (
                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                            {activeFocusItems.length} active{completedFocusItems.length > 0 ? ` · ${completedFocusItems.length} done` : ''}
                        </span>
                    )}
                </div>
                {visibleActiveFocus.map(renderFocusItem)}
                {hiddenActiveCount > 0 && (
                    <button onClick={() => setShowAllFocus(true)} className="btn btn-ghost" style={{ width: '100%', fontSize: '12px', color: 'var(--text-tertiary)', padding: '8px', marginTop: '4px' }}>
                        {t('agent.aware.showMore', { count: hiddenActiveCount })}
                    </button>
                )}
                {showAllFocus && activeFocusItems.length > SECTION_PAGE_SIZE && (
                    <button onClick={(e) => { setShowAllFocus(false); e.currentTarget.closest('.card')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }} className="btn btn-ghost" style={{ width: '100%', fontSize: '12px', color: 'var(--text-tertiary)', padding: '8px', marginTop: '4px' }}>
                        {t('agent.aware.showLess')}
                    </button>
                )}
                {completedFocusItems.length > 0 && (
                    <>
                        <button onClick={() => setShowCompletedFocus(!showCompletedFocus)} className="btn btn-ghost" style={{ width: '100%', fontSize: '12px', color: 'var(--text-tertiary)', padding: '8px', marginTop: '8px', borderTop: '1px solid var(--border-subtle)', borderRadius: 0 }}>
                            {showCompletedFocus ? t('agent.aware.hideCompleted') : t('agent.aware.showCompleted', { count: completedFocusItems.length })}
                        </button>
                        {showCompletedFocus && completedFocusItems.map(renderFocusItem)}
                    </>
                )}
                {!hasFocusItems && (
                    <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-tertiary)', border: '1px dashed var(--border-subtle)', borderRadius: '8px' }}>{t('agent.aware.focusEmpty')}</div>
                )}
            </div>

            {/* ── Standalone Triggers Card ── */}
            {hasStandalone && (
                <div className="card" style={{ marginBottom: '16px', padding: '16px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                        <div><h4 style={{ margin: 0, fontSize: '14px', fontWeight: 600 }}>{t('agent.aware.standaloneTriggers')}</h4></div>
                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{standaloneTriggers.length} trigger{standaloneTriggers.length > 1 ? 's' : ''}</span>
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        {[...standaloneTriggers].sort((a: any, b: any) => (b.is_enabled ? 1 : 0) - (a.is_enabled ? 1 : 0)).slice(0, showAllTriggers ? undefined : SECTION_PAGE_SIZE).map((trig: any) => (
                            <div key={trig.id} style={{ padding: '10px 14px', borderRadius: '8px', border: '1px solid var(--border-subtle)', display: 'flex', alignItems: 'center', gap: '10px', opacity: trig.is_enabled ? 1 : 0.5, background: 'var(--bg-primary)' }}>
                                <div style={{ flex: 1 }}>
                                    <div style={{ fontSize: '13px', fontWeight: 500 }}>{triggerToHuman(trig)}</div>
                                    {trig.reason && <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{trig.reason}</div>}
                                    <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', fontFamily: 'monospace', marginTop: '2px' }}>{trig.name}{trig.type === 'cron' ? ` · ${trig.config?.expr}` : ''}</div>
                                </div>
                                <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', whiteSpace: 'nowrap' }}>{t('agent.aware.fired', { count: trig.fire_count })}</span>
                                {!trig.is_enabled && <span style={{ fontSize: '10px', color: 'var(--text-tertiary)' }}>{t('agent.aware.disabled')}</span>}
                                <div style={{ display: 'flex', gap: '4px' }}>
                                    <button className="btn btn-ghost" style={{ padding: '2px 6px', fontSize: '11px' }} onClick={async () => { await triggerApi.update(agentId, trig.id, { is_enabled: !trig.is_enabled }); refetchTriggers(); }}>
                                        {trig.is_enabled ? t('agent.aware.disable') : t('agent.aware.enable')}
                                    </button>
                                    <button className="btn btn-ghost" style={{ padding: '2px 6px', fontSize: '11px', color: 'var(--error)' }} onClick={async () => { if (confirm(t('agent.aware.deleteTriggerConfirm', { name: trig.name }))) { await triggerApi.delete(agentId, trig.id); refetchTriggers(); } }}>
                                        {t('common.delete', 'Delete')}
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                    {standaloneTriggers.length > SECTION_PAGE_SIZE && (
                        <button onClick={(e) => { const collapse = showAllTriggers; setShowAllTriggers(!showAllTriggers); if (collapse) e.currentTarget.closest('.card')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }} className="btn btn-ghost" style={{ width: '100%', fontSize: '12px', color: 'var(--text-tertiary)', padding: '8px', marginTop: '4px' }}>
                            {showAllTriggers ? (i18n.language?.startsWith('zh') ? '收起' : 'Show less') : (i18n.language?.startsWith('zh') ? `显示更多 ${standaloneTriggers.length - SECTION_PAGE_SIZE} 项...` : `Show ${standaloneTriggers.length - SECTION_PAGE_SIZE} more...`)}
                        </button>
                    )}
                </div>
            )}

            {/* Raw markdown toggle */}
            {raw && (
                <details style={{ marginTop: '4px', marginBottom: '16px' }}>
                    <summary style={{ fontSize: '11px', color: 'var(--text-tertiary)', cursor: 'pointer' }}>{t('agent.aware.viewRawMarkdown')}</summary>
                    <pre style={{ fontSize: '11px', marginTop: '8px', padding: '12px', background: 'var(--bg-secondary)', borderRadius: '6px', whiteSpace: 'pre-wrap', maxHeight: '300px', overflow: 'auto' }}>{raw}</pre>
                </details>
            )}

            {/* Reflections */}
            {reflectionSessions.length > 0 && (() => {
                const totalPages = Math.ceil(reflectionSessions.length / REFLECTIONS_PAGE_SIZE);
                const pageStart = reflectionPage * REFLECTIONS_PAGE_SIZE;
                const visibleSessions = reflectionSessions.slice(pageStart, pageStart + REFLECTIONS_PAGE_SIZE);
                return (
                    <div className="card" style={{ padding: '16px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                            <div>
                                <h4 style={{ margin: 0, fontSize: '14px', fontWeight: 600 }}>{t('agent.aware.reflections')}</h4>
                                <span style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>{t('agent.aware.reflectionsDesc')}</span>
                            </div>
                            <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{reflectionSessions.length} session{reflectionSessions.length > 1 ? 's' : ''}</span>
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                            {visibleSessions.map((session: any) => {
                                const isExpanded = expandedReflection === session.id;
                                const msgs = reflectionMessages[session.id] || [];
                                return (
                                    <div key={session.id} style={{ borderRadius: '8px', border: '1px solid var(--border-subtle)', overflow: 'hidden', background: 'var(--bg-primary)' }}>
                                        <div onClick={async () => {
                                            if (isExpanded) { setExpandedReflection(null); return; }
                                            setExpandedReflection(session.id);
                                            if (!reflectionMessages[session.id]) {
                                                try {
                                                    const data = await request<any[]>(`/agents/${encodeURIComponent(agentId!)}/sessions/${session.id}/messages`);
                                                    setReflectionMessages(prev => ({ ...prev, [session.id]: data || [] }));
                                                } catch { /* ignore */ }
                                            }
                                        }} style={{ padding: '10px 16px', display: 'flex', alignItems: 'center', gap: '10px', cursor: 'pointer', transition: 'background 0.15s' }}
                                            onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg-secondary)')}
                                            onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                                        >
                                            <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: 'var(--accent-primary)', flexShrink: 0 }} />
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div style={{ fontSize: '12px', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{(session.title || 'Trigger execution').replace(/^🤖\s*/, '')}</div>
                                                <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', marginTop: '1px' }}>{new Date(session.created_at).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}{session.message_count > 0 && ` · ${session.message_count} msg`}</div>
                                            </div>
                                            <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)', transition: 'transform 0.15s' }}>&#9654;</span>
                                        </div>
                                        {isExpanded && (
                                            <div style={{ padding: '0 16px 12px', borderTop: '1px solid var(--border-subtle)' }}>
                                                {msgs.length === 0 ? (
                                                    <div style={{ padding: '12px 0', fontSize: '12px', color: 'var(--text-tertiary)' }}>Loading...</div>
                                                ) : (
                                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                                                        {msgs.map((msg: any, mi: number) => {
                                                            if (msg.role === 'tool_call') {
                                                                const tName = msg.toolName || (() => { try { return JSON.parse(msg.content || '{}').name; } catch { return ''; } })() || 'tool';
                                                                const tArgs = msg.toolArgs || (() => { try { return JSON.parse(msg.content || '{}').args; } catch { return {}; } })();
                                                                const tResult = msg.toolResult || '';
                                                                const argsStr = typeof tArgs === 'string' ? tArgs : JSON.stringify(tArgs || {}, null, 2);
                                                                const resultStr = typeof tResult === 'string' ? tResult : JSON.stringify(tResult, null, 2);
                                                                const hasDetail = argsStr.length > 60 || resultStr;
                                                                const Tag = hasDetail ? 'details' : 'div';
                                                                const HeaderTag = hasDetail ? 'summary' : 'div';
                                                                return (
                                                                    <Tag key={mi} style={{ borderRadius: '6px', background: 'var(--bg-secondary)', overflow: 'hidden' }}>
                                                                        <HeaderTag style={{ padding: '5px 10px', fontSize: '11px', cursor: hasDetail ? 'pointer' : 'default', display: 'flex', alignItems: 'center', gap: '8px', listStyle: 'none', WebkitAppearance: 'none' } as any}>
                                                                            {hasDetail && <span style={{ fontSize: '8px', color: 'var(--text-tertiary)', flexShrink: 0 }}>&#9654;</span>}
                                                                            <span style={{ fontWeight: 600, fontSize: '10px', color: 'var(--text-primary)', padding: '1px 6px', borderRadius: '3px', background: 'var(--bg-tertiary, rgba(0,0,0,0.06))', flexShrink: 0, fontFamily: 'monospace' }}>{tName}</span>
                                                                            <span style={{ color: 'var(--text-tertiary)', fontFamily: 'monospace', fontSize: '10px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{argsStr.replace(/\n/g, ' ').substring(0, 60)}{argsStr.length > 60 ? '...' : ''}</span>
                                                                        </HeaderTag>
                                                                        {hasDetail && (
                                                                            <div style={{ padding: '8px 10px', borderTop: '1px solid var(--border-subtle)', fontFamily: 'monospace', fontSize: '10px', lineHeight: 1.5, whiteSpace: 'pre-wrap', maxHeight: '200px', overflow: 'auto', color: 'var(--text-secondary)' }}>
                                                                                {argsStr}
                                                                                {resultStr && (<><div style={{ borderTop: '1px dashed var(--border-subtle)', margin: '6px 0', opacity: 0.5 }} /><span style={{ color: 'var(--text-tertiary)' }}>→ </span>{resultStr.substring(0, 500)}</>)}
                                                                            </div>
                                                                        )}
                                                                    </Tag>
                                                                );
                                                            }
                                                            if (msg.role === 'tool_result') {
                                                                const resultStr = typeof msg.content === 'string' ? msg.content : JSON.stringify(msg.content, null, 2);
                                                                return (
                                                                    <details key={mi} style={{ borderRadius: '6px', background: 'var(--bg-secondary)', overflow: 'hidden' }}>
                                                                        <summary style={{ padding: '5px 10px', fontSize: '11px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px', listStyle: 'none', WebkitAppearance: 'none' } as any}>
                                                                            <span style={{ fontSize: '8px', color: 'var(--text-tertiary)', flexShrink: 0 }}>&#9654;</span>
                                                                            <span style={{ fontWeight: 600, fontSize: '10px', color: 'var(--text-primary)', padding: '1px 6px', borderRadius: '3px', background: 'rgba(34,197,94,0.1)', flexShrink: 0, fontFamily: 'monospace' }}>result</span>
                                                                            <span style={{ color: 'var(--text-tertiary)', fontFamily: 'monospace', fontSize: '10px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{resultStr.substring(0, 60)}{resultStr.length > 60 ? '...' : ''}</span>
                                                                        </summary>
                                                                        <div style={{ padding: '8px 10px', borderTop: '1px solid var(--border-subtle)', fontFamily: 'monospace', fontSize: '10px', lineHeight: 1.5, whiteSpace: 'pre-wrap', maxHeight: '200px', overflow: 'auto', color: 'var(--text-secondary)' }}>
                                                                            {resultStr.substring(0, 500)}
                                                                        </div>
                                                                    </details>
                                                                );
                                                            }
                                                            if (msg.role === 'assistant') {
                                                                return <div key={mi} style={{ padding: '8px 10px', borderRadius: '6px', background: 'var(--bg-secondary)', fontSize: '12px', color: 'var(--text-primary)', whiteSpace: 'pre-wrap', lineHeight: '1.5', maxHeight: '200px', overflow: 'auto' }}>{msg.content}</div>;
                                                            }
                                                            if (msg.role === 'user') {
                                                                return <div key={mi} style={{ padding: '6px 10px', borderRadius: '6px', background: 'var(--bg-secondary)', borderLeft: '2px solid var(--border-subtle)', fontSize: '11px', color: 'var(--text-secondary)', whiteSpace: 'pre-wrap', maxHeight: '100px', overflow: 'auto' }}>{(msg.content || '').substring(0, 300)}</div>;
                                                            }
                                                            return null;
                                                        })}
                                                    </div>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                        {totalPages > 1 && (
                            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px', marginTop: '12px', paddingTop: '8px', borderTop: '1px solid var(--border-subtle)' }}>
                                <button onClick={() => { setReflectionPage(p => Math.max(0, p - 1)); setExpandedReflection(null); }} disabled={reflectionPage === 0} className="btn btn-ghost" style={{ fontSize: '12px', padding: '4px 10px', opacity: reflectionPage === 0 ? 0.3 : 1 }}>{i18n.language?.startsWith('zh') ? '上一页' : 'Prev'}</button>
                                <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', fontVariantNumeric: 'tabular-nums' }}>{reflectionPage + 1} / {totalPages}</span>
                                <button onClick={() => { setReflectionPage(p => Math.min(totalPages - 1, p + 1)); setExpandedReflection(null); }} disabled={reflectionPage >= totalPages - 1} className="btn btn-ghost" style={{ fontSize: '12px', padding: '4px 10px', opacity: reflectionPage >= totalPages - 1 ? 0.3 : 1 }}>{i18n.language?.startsWith('zh') ? '下一页' : 'Next'}</button>
                            </div>
                        )}
                    </div>
                );
            })()}
        </div>
    );
}
