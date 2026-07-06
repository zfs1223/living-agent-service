import React, { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

// Components
import ConfirmModal from '../components/ConfirmModal';
import type { FileBrowserApi } from '../components/FileBrowser';
import FileBrowser from '../components/FileBrowser';
import PromptModal from '../components/PromptModal';
import OpenClawSettings from './OpenClawSettings';
import FixedEmployeeSettings from '../components/FixedEmployeeSettings';
import HumanEmployeeSettings from '../components/HumanEmployeeSettings';
import EvolvedEmployeeSettings from '../components/EvolvedEmployeeSettings';

// Sub-components (H-C04 refactoring)
import AgentTools from './AgentDetail/AgentTools';
import AgentRelations from './AgentDetail/AgentRelations';
import AgentTriggers from './AgentDetail/AgentTriggers';
import AgentChat from './AgentDetail/AgentChat';
import AgentSettings from './AgentDetail/AgentSettings';
import AgentApprovals from './AgentDetail/AgentApprovals';
import AgentDetailErrorBoundary from './AgentDetail/ErrorBoundary';

// Shared utilities and types
import { formatTokens } from './AgentDetail/utils';
import { TABS } from './AgentDetail/types';

// API services
import { activityApi, agentApi, enterpriseApi, fileApi, skillApi } from '../services/api';
import { request } from '../services/apiBase';

// Stores
import { useAuthStore } from '../stores';
import { useToastStore } from '../stores/toastStore';


function AgentDetailInner() {
    const { t, i18n } = useTranslation();
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const location = useLocation();
    const hashTab = location.hash?.replace('#', '');
    const [activeTab, setActiveTabRaw] = useState<string>(hashTab && TABS.includes(hashTab as any) ? hashTab : 'status');

    // Sync URL hash when tab changes
    const setActiveTab = (tab: string) => {
        setActiveTabRaw(tab);
        navigate(`#${tab}`, { replace: true });
    };

    // ── Agent data query ──
    const { data: agent, isLoading } = useQuery({
        queryKey: ['agent', id],
        queryFn: () => agentApi.get(id!),
        enabled: !!id,
    });

    // ── Mind tab data (queries kept for potential future use in sub-components) ──

    // ── Workspace data ──
    const [workspacePath, setWorkspacePath] = useState('workspace');

    // ── Activity data ──
    const { data: activityLogs = [] } = useQuery({
        queryKey: ['activity', id],
        queryFn: () => activityApi.list(id!, 100),
        enabled: !!id && (activeTab === 'activityLog' || activeTab === 'status'),
        refetchInterval: activeTab === 'activityLog' ? 10000 : false,
    });

    // ── Expiry editor modal state ──
    const [showExpiryModal, setShowExpiryModal] = useState(false);
    const [expiryValue, setExpiryValue] = useState('');
    const [expirySaving, setExpirySaving] = useState(false);

    const openExpiryModal = () => {
        const cur = (agent as any)?.expires_at;
        setExpiryValue(cur ? new Date(cur).toISOString().slice(0, 16) : '');
        setShowExpiryModal(true);
    };

    const addHours = (h: number) => {
        const base = (agent as any)?.expires_at ? new Date((agent as any).expires_at) : new Date();
        const next = new Date(base.getTime() + h * 3600_000);
        setExpiryValue(next.toISOString().slice(0, 16));
    };

    const saveExpiry = async (permanent = false) => {
        setExpirySaving(true);
        try {
            const body = permanent ? { expires_at: null } : { expires_at: expiryValue ? new Date(expiryValue).toISOString() : null };
            await request(`/agents/${encodeURIComponent(id!)}`, {
                method: 'PATCH',
                body: JSON.stringify(body),
            });
            queryClient.invalidateQueries({ queryKey: ['agent', id] });
            setShowExpiryModal(false);
        } catch (e) { useToastStore.getState().showToast('Failed: ' + e, 'error'); }
        setExpirySaving(false);
    };

    // ── Activity log state ──
    const [expandedLogId, setExpandedLogId] = useState<string | null>(null);
    const [logFilter, setLogFilter] = useState<string>('user');

    // ── Skill import state ──
    const [showImportSkillModal, setShowImportSkillModal] = useState(false);
    const [importingSkillId, setImportingSkillId] = useState<string | null>(null);
    const { data: globalSkillsForImport } = useQuery({
        queryKey: ['global-skills-for-import'],
        queryFn: () => skillApi.list(),
        enabled: showImportSkillModal,
    });
    // Agent-level import from ClawHub / URL
    const [showAgentClawhub, setShowAgentClawhub] = useState(false);
    const [agentClawhubQuery, setAgentClawhubQuery] = useState('');
    const [agentClawhubResults, setAgentClawhubResults] = useState<any[]>([]);
    const [agentClawhubSearching, setAgentClawhubSearching] = useState(false);
    const [agentClawhubInstalling, setAgentClawhubInstalling] = useState<string | null>(null);
    const [showAgentUrlImport, setShowAgentUrlImport] = useState(false);
    const [agentUrlInput, setAgentUrlInput] = useState('');
    const [agentUrlImporting, setAgentUrlImporting] = useState(false);

    // ── Metrics & LLM Models queries ──
    const { data: metrics } = useQuery({
        queryKey: ['metrics', id],
        queryFn: () => agentApi.metrics(id!).catch(() => null),
        enabled: !!id && activeTab === 'status',
        retry: false,
    });

    const { data: llmModels = [] } = useQuery({
        queryKey: ['llm-models'],
        queryFn: () => enterpriseApi.llmModels(),
        enabled: activeTab === 'settings' || activeTab === 'status' || activeTab === 'chat',
    });

    // ── File viewer state (used by PromptModal actions) ──
    const [promptModal, setPromptModal] = useState<{ title: string; placeholder: string; action: string } | null>(null);
    const [deleteConfirm, setDeleteConfirm] = useState<{ path: string; name: string; isDir: boolean } | null>(null);
    const [uploadToast, setUploadToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

    // ── Name/Role edit state ──
    const [editingRole, setEditingRole] = useState(false);
    const [roleInput, setRoleInput] = useState('');
    const [editingName, setEditingName] = useState(false);
    const [nameInput, setNameInput] = useState('');

    const showToast = (message: string, type: 'success' | 'error' = 'success') => {
        setUploadToast({ message, type });
        setTimeout(() => setUploadToast(null), 3000);
    };

    // ── Reset state when switching agent ──
    const prevIdRef = useRef(id);
    useEffect(() => {
        if (id && id !== prevIdRef.current) {
            prevIdRef.current = id;
            queryClient.invalidateQueries({ queryKey: ['agent', id] });
            window.history.replaceState(null, '', `#${activeTab}`);
        }
    }, [id]);

    // ── Compute derived values ──
    const currentUser = useAuthStore((s) => s.user);
    const isAdmin = currentUser?.role === 'platform_admin' || currentUser?.role === 'org_admin';

    if (isLoading || !agent) {
        return <div style={{ padding: '40px', color: 'var(--text-tertiary)' }}>{t('common.loading')}</div>;
    }

    const computeStatusKey = () => {
        if (agent.status === 'error') return 'error';
        if (agent.status === 'creating') return 'creating';
        if (agent.status === 'stopped') return 'stopped';
        if ((agent as any).agent_type === 'openclaw' && agent.status === 'running' && (agent as any).openclaw_last_seen) {
            const elapsed = Date.now() - new Date((agent as any).openclaw_last_seen).getTime();
            if (elapsed > 60 * 60 * 1000) return 'disconnected';
        }
        return agent.status === 'running' ? 'running' : 'idle';
    };

    const statusKey = computeStatusKey();
    const canManage = (agent as any).access_level === 'manage' || isAdmin;
    const origin = String((agent as any)?.origin || '').toLowerCase();
    const isFixedEmployee = origin === 'fixed';
    const isHumanEmployee = origin === 'human' || origin === 'personal';
    const isOpenClaw = String((agent as any)?.agent_type || '').toLowerCase() === 'openclaw';

    // Chat Tab visibility rules
    const canOpenChat = isHumanEmployee && !isFixedEmployee && !isOpenClaw;
    const canShowChatTab = canOpenChat;
    const visibleTabs = TABS.filter(tab => {
        if (!canShowChatTab && tab === 'chat') return false;
        return true;
    });
    const shouldShowChatEntry = canShowChatTab;
    const showChatSection = canShowChatTab && activeTab === 'chat';

    const supportsVision = !!agent?.primary_model_id && llmModels.some(
        (m: any) => m.id === agent.primary_model_id && m.supports_vision
    );

    return (
        <>
            <div>
                {/* Back button */}
                <button
                    onClick={() => navigate(-1)}
                    style={{
                        display: 'inline-flex', alignItems: 'center', gap: '6px',
                        padding: '6px 12px', borderRadius: '8px',
                        background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)',
                        color: 'var(--text-secondary)', cursor: 'pointer', fontSize: '13px',
                        marginBottom: '12px', transition: 'all 0.15s',
                    }}
                    onMouseEnter={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.12)'; e.currentTarget.style.color = 'var(--text-primary)'; }}
                    onMouseLeave={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.06)'; e.currentTarget.style.color = 'var(--text-secondary)'; }}
                >
                    ← {t('common.back', '返回')}
                </button>

                {/* Header */}
                <div style={{
                    borderRadius: '24px',
                    padding: '22px',
                    marginBottom: '18px',
                    background: 'linear-gradient(135deg, rgba(99,102,241,0.12), rgba(12,18,28,0.84) 48%, rgba(5,6,10,0.96))',
                    border: '1px solid rgba(255,255,255,0.08)',
                    boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
                }}>
                    <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 10px', borderRadius: '999px', background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)', fontSize: '12px', marginBottom: '14px' }}>
                        <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-primary)', boxShadow: '0 0 18px rgba(99,102,241,0.85)' }} />
                        {t('agent.detail.badge', '员工控制中心')}
                    </div>
                    {/* Fixed: removed duplicate div wrapper (was line 1218-1219) */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: '18px', alignItems: 'flex-start' }}>
                        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '16px', minWidth: 0, flex: 1 }}>
                            <div style={{ width: '56px', height: '56px', borderRadius: '16px', background: 'var(--accent-subtle)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '26px', flexShrink: 0 }}>{(Array.from(agent.name || 'A')[0] as string || 'A').toUpperCase()}</div>
                            <div style={{ flex: 1, minWidth: 0, overflow: 'hidden' }}>
                                {canManage && editingName ? (
                                    <input
                                        className="page-title"
                                        autoFocus
                                        value={nameInput}
                                        onChange={e => setNameInput(e.target.value)}
                                        onBlur={async () => {
                                            setEditingName(false);
                                            if (nameInput.trim() && nameInput !== agent.name) {
                                                await agentApi.update(id!, { name: nameInput.trim() } as any);
                                                queryClient.invalidateQueries({ queryKey: ['agent', id] });
                                            } else {
                                                setNameInput(agent.name);
                                            }
                                        }}
                                        onKeyDown={async e => {
                                            if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
                                            if (e.key === 'Escape') { setEditingName(false); setNameInput(agent.name); }
                                        }}
                                        style={{
                                            background: 'var(--bg-elevated)', border: '1px solid var(--accent-primary)',
                                            borderRadius: '6px', color: 'var(--text-primary)',
                                            padding: '4px 10px', minWidth: '320px', width: 'auto', outline: 'none',
                                            marginBottom: '0', display: 'block',
                                        }}
                                    />
                                ) : (
                                    <h1 className="page-title"
                                        title={canManage ? "点击编辑名称" : undefined}
                                        onClick={() => { if (canManage) { setNameInput(agent.name); setEditingName(true); } }}
                                        style={{ cursor: canManage ? 'text' : 'default', borderBottom: canManage ? '1px dashed transparent' : 'none', display: 'inline-block', marginBottom: '0' }}
                                        onMouseEnter={e => { if (canManage) e.currentTarget.style.borderBottomColor = 'var(--text-tertiary)'; }}
                                        onMouseLeave={e => { if (canManage) e.currentTarget.style.borderBottomColor = 'transparent'; }}
                                    >
                                        {agent.name}
                                    </h1>
                                )}
                                <p className="page-subtitle" style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                                    <span className={`status-dot ${statusKey}`} />
                                    {t(`agent.status.${statusKey}`)}
                                    {canManage && editingRole ? (
                                        <textarea
                                            autoFocus
                                            value={roleInput}
                                            onChange={e => setRoleInput(e.target.value)}
                                            onBlur={async () => {
                                                setEditingRole(false);
                                                if (roleInput !== agent.role_description) {
                                                    await agentApi.update(id!, { role_description: roleInput } as any);
                                                    queryClient.invalidateQueries({ queryKey: ['agent', id] });
                                                }
                                            }}
                                            onKeyDown={async e => {
                                                if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); (e.target as HTMLTextAreaElement).blur(); }
                                                if (e.key === 'Escape') { setEditingRole(false); setRoleInput(agent.role_description || ''); }
                                            }}
                                            rows={2}
                                            style={{
                                                background: 'var(--bg-elevated)', border: '1px solid var(--accent-primary)',
                                                borderRadius: '6px', color: 'var(--text-primary)', fontSize: '13px',
                                                padding: '6px 10px', width: 'min(500px, 50vw)', outline: 'none',
                                                resize: 'vertical', lineHeight: '1.5', fontFamily: 'inherit',
                                            }}
                                        />
                                    ) : (
                                        <span
                                            title={canManage ? (agent.role_description || '点击编辑') : (agent.role_description || '')}
                                            onClick={() => { if (canManage) { setRoleInput(agent.role_description || ''); setEditingRole(true); } }}
                                            style={{ cursor: canManage ? 'text' : 'default', borderBottom: canManage ? '1px dashed transparent' : 'none', maxWidth: '38vw', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', display: 'inline-block', verticalAlign: 'middle' }}
                                            onMouseEnter={e => { if (canManage) e.currentTarget.style.borderBottomColor = 'var(--text-tertiary)'; }}
                                            onMouseLeave={e => { if (canManage) e.currentTarget.style.borderBottomColor = 'transparent'; }}
                                        >
                                            {agent.role_description ? `· ${agent.role_description}` : (canManage ? <span style={{ color: 'var(--text-tertiary)', fontSize: '12px' }}>· {t('agent.fields.role', '点击添加描述...')}</span> : null)}
                                        </span>
                                    )}
                                    {(agent as any).is_expired && (
                                        <span style={{ background: 'var(--error)', color: '#fff', padding: '2px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 600 }}>已过期</span>
                                    )}
                                    {(agent as any).agent_type === 'openclaw' && (
                                        <span style={{
                                            fontSize: '10px', padding: '2px 6px', borderRadius: '4px',
                                            background: 'linear-gradient(135deg, #6366f1, #8b5cf6)', color: '#fff', fontWeight: 600,
                                            letterSpacing: '0.5px',
                                        }}>OpenClaw · Lab</span>
                                    )}
                                    {!(agent as any).is_expired && (agent as any).expires_at && (
                                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                            到期时间：{new Date((agent as any).expires_at).toLocaleString()}
                                        </span>
                                    )}
                                    {isAdmin && (
                                        <button
                                            onClick={openExpiryModal}
                                            title="编辑到期时间"
                                            style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '11px', color: 'var(--text-tertiary)', padding: '1px 4px', borderRadius: '4px', lineHeight: 1 }}
                                            onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg-secondary)')}
                                            onMouseLeave={e => (e.currentTarget.style.background = 'none')}
                                        >✏️ {t((agent as any).expires_at || (agent as any).is_expired ? 'agent.settings.expiry.renew' : 'agent.settings.expiry.setExpiry')}</button>
                                    )}
                                </p>
                            </div>
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '10px', minWidth: '320px' }}>
                            <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('agent.status.label', '状态')}</div>
                                <div style={{ fontSize: '15px', fontWeight: 700, marginTop: '6px' }}>{t(`agent.status.${statusKey}`)}</div>
                            </div>
                            <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('agent.actions.chat')}</div>
                                <div style={{ fontSize: '15px', fontWeight: 700, marginTop: '6px' }}>{shouldShowChatEntry ? (t('common.enabled', '已启用')) : (t('common.disabled', '已禁用'))}</div>
                            </div>
                            <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('agent.fields.role', '角色')}</div>
                                <div style={{ fontSize: '15px', fontWeight: 700, marginTop: '6px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{agent.role_description || '-'}</div>
                            </div>
                            <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('agent.actions.start', '生命周期')}</div>
                                <div style={{ fontSize: '15px', fontWeight: 700, marginTop: '6px' }}>{(agent as any)?.agent_type === 'openclaw' ? 'OpenClaw' : agent.status}</div>
                            </div>
                        </div>
                    </div>
                    <div style={{ display: 'flex', gap: '8px', marginTop: '18px', flexWrap: 'wrap' }}>
                        {shouldShowChatEntry && <button className="btn btn-primary" onClick={() => setActiveTab('chat')}>{t('agent.actions.chat')}</button>}
                        {(agent as any)?.agent_type !== 'openclaw' && (
                            <>
                                {agent.status === 'stopped' ? (
                                    <button className="btn btn-secondary" onClick={async () => { await agentApi.start(id!); queryClient.invalidateQueries({ queryKey: ['agent', id] }); }}>{t('agent.actions.start')}</button>
                                ) : agent.status === 'running' ? (
                                    <button className="btn btn-secondary" onClick={async () => { await agentApi.stop(id!); queryClient.invalidateQueries({ queryKey: ['agent', id] }); }}>{t('agent.actions.stop')}</button>
                                ) : null}
                            </>
                        )}
                    </div>
                </div>

                {/* Tabs */}
                <div className="tabs">
                    {visibleTabs.filter(tab => {
                        if ((agent as any)?.access_level === 'use') {
                            if (tab === 'settings' || tab === 'approvals') return false;
                        }
                        if ((agent as any)?.agent_type === 'openclaw') {
                            return ['status', 'relationships', 'activityLog', 'settings'].includes(tab);
                        }
                        return true;
                    }).map((tab) => (
                        <div key={tab} className={`tab ${activeTab === tab ? 'active' : ''}`} onClick={() => setActiveTab(tab)}>
                            {t(`agent.tabs.${tab}`)}
                        </div>
                    ))}
                </div>

                {/* ── Status Tab ── */}
                {activeTab === 'status' && (() => {
                    const formatDate = (d: string) => {
                        try { return new Date(d).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }); } catch { return d; }
                    };
                    const primaryModel = llmModels.find((m: any) => m.id === agent.primary_model_id);
                    const modelLabel = primaryModel ? (primaryModel.label || primaryModel.model) : '—';
                    const modelProvider = primaryModel ? primaryModel.provider : '—';

                    return (
                        <div>
                            {/* Metric cards */}
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px', marginBottom: '24px' }}>
                                <div className="card">
                                    <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>📋 {t('agent.tabs.status')}</div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                        <span className={`status-dot ${statusKey}`} />
                                        <span style={{ fontSize: '16px', fontWeight: 500 }}>{t(`agent.status.${statusKey}`)}</span>
                                    </div>
                                </div>
                                <div className="card">
                                    <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>🗓️ {t('agent.settings.today')} Token</div>
                                    <div style={{ fontSize: '22px', fontWeight: 600 }}>{formatTokens(agent.tokens_used_today)}</div>
                                    {agent.max_tokens_per_day && <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{t('agent.settings.noLimit')} {formatTokens(agent.max_tokens_per_day)}</div>}
                                </div>
                                <div className="card">
                                    <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>📅 {t('agent.settings.month')} Token</div>
                                    <div style={{ fontSize: '22px', fontWeight: 600 }}>{formatTokens(agent.tokens_used_month)}</div>
                                    {agent.max_tokens_per_month && <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{t('agent.settings.noLimit')} {formatTokens(agent.max_tokens_per_month)}</div>}
                                </div>
                                {/* Native agent metrics */}
                                {(agent as any)?.agent_type !== 'openclaw' && (<>
                                    <div className="card">
                                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>{t('agent.status.llmCallsToday')}</div>
                                        <div style={{ fontSize: '22px', fontWeight: 600 }}>{((agent as any).llm_calls_today || 0).toLocaleString()}</div>
                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{t('agent.status.max')}: {((agent as any).max_llm_calls_per_day || 100).toLocaleString()}</div>
                                    </div>
                                    <div className="card">
                                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>{t('agent.status.totalToken')}</div>
                                        <div style={{ fontSize: '22px', fontWeight: 600 }}>{formatTokens((agent as any).tokens_used_total || 0)}</div>
                                    </div>
                                    {metrics && (
                                        <>
                                            <div className="card">
                                                <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>✅ {t('agent.tasks.done')}</div>
                                                <div style={{ fontSize: '22px', fontWeight: 600 }}>{metrics.tasks?.done || 0}/{metrics.tasks?.total || 0}</div>
                                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}> {metrics.tasks?.completion_rate || 0}%</div>
                                            </div>
                                            <div className="card">
                                                <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>{t('agent.status.pending')}</div>
                                                <div style={{ fontSize: '22px', fontWeight: 600, color: metrics.approvals?.pending > 0 ? 'var(--warning)' : 'inherit' }}>{metrics.approvals?.pending || 0}</div>
                                            </div>
                                            <div className="card" style={{ position: 'relative' }}>
                                                <div className="metric-tooltip-trigger" style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '6px', cursor: 'help', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                                                    {t('agent.status.24hActions')}
                                                    <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="8" cy="8" r="6.5" /><path d="M8 7v4M8 5.5v0" /></svg>
                                                    <span className="metric-tooltip">{t('agent.status.24hActionsTooltip')}</span>
                                                </div>
                                                <div style={{ fontSize: '22px', fontWeight: 600 }}>{metrics.activity?.actions_last_24h || 0}</div>
                                            </div>
                                        </>
                                    )}
                                </>)}
                                {/* OpenClaw-specific metrics */}
                                {(agent as any)?.agent_type === 'openclaw' && (
                                    <div className="card">
                                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>
                                            {t('agent.openclaw.lastSeen')}
                                        </div>
                                        <div style={{ fontSize: '16px', fontWeight: 500 }}>
                                            {(agent as any).openclaw_last_seen
                                                ? new Date((agent as any).openclaw_last_seen).toLocaleString()
                                                : t('agent.openclaw.notConnected')}
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Agent Profile & Model Info */}
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '24px' }}>
                                <div className="card">
                                    <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px' }}>{t('agent.profile.title')}</h3>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', gap: '12px' }}>
                                            <span style={{ color: 'var(--text-tertiary)', flexShrink: 0 }}>{t('agent.fields.role')}</span>
                                            <span title={agent.role_description || ''} style={{ textAlign: 'right', overflow: 'hidden', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' as any }}>{agent.role_description || '—'}</span>
                                        </div>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                            <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.profile.created')}</span>
                                            <span>{agent.created_at ? formatDate(agent.created_at) : '—'}</span>
                                        </div>
                                        {(agent as any).creator_username && (
                                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                                <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.fields.createdBy', '创建者')}</span>
                                                <span style={{ color: 'var(--text-secondary)' }}>@{(agent as any).creator_username}</span>
                                            </div>
                                        )}
                                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                            <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.profile.lastActive')}</span>
                                            <span>{agent.last_active_at ? formatDate(agent.last_active_at) : '—'}</span>
                                        </div>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                            <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.profile.timezone')}</span>
                                            <span>{(agent as any).effective_timezone || agent.timezone || 'UTC'}</span>
                                        </div>
                                    </div>
                                </div>
                                {(agent as any)?.agent_type !== 'openclaw' ? (
                                    <div className="card">
                                        <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px' }}>{t('agent.modelConfig.title')}</h3>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                                <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.modelConfig.model')}</span>
                                                <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }}>{modelLabel}</span>
                                            </div>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                                <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.modelConfig.provider')}</span>
                                                <span style={{ textTransform: 'capitalize' }}>{modelProvider}</span>
                                            </div>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                                <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.modelConfig.contextRounds')}</span>
                                                <span>{(agent as any).context_window_size || 100}</span>
                                            </div>
                                        </div>
                                    </div>
                                ) : (
                                    <div className="card">
                                        <h3 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px' }}>
                                            {t('agent.openclaw.connection')}
                                        </h3>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                                <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.openclaw.type')}</span>
                                                <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                    <span style={{
                                                        fontSize: '10px', padding: '2px 6px', borderRadius: '4px',
                                                        background: 'linear-gradient(135deg, #6366f1, #8b5cf6)', color: '#fff', fontWeight: 600,
                                                    }}>OpenClaw</span>
                                                    Lab
                                                </span>
                                            </div>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                                <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.openclaw.lastSeen')}</span>
                                                <span>{(agent as any).openclaw_last_seen
                                                    ? new Date((agent as any).openclaw_last_seen).toLocaleString()
                                                    : t('agent.openclaw.never')}
                                                </span>
                                            </div>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                                                <span style={{ color: 'var(--text-tertiary)' }}>{t('agent.openclaw.model')}</span>
                                                <span style={{ color: 'var(--text-secondary)' }}>{t('agent.openclaw.managedBy')}</span>
                                            </div>
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Recent Activity */}
                            {activityLogs && activityLogs.length > 0 && (
                                <div className="card">
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                                        <h3 style={{ fontSize: '14px', fontWeight: 600 }}>📊 最近活动</h3>
                                        <button className="btn btn-ghost" style={{ fontSize: '12px' }} onClick={() => setActiveTab('activityLog')}>查看全部 →</button>
                                    </div>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                        {activityLogs.slice(0, 5).map((log: any, i: number) => (
                                            <div key={i} style={{ display: 'flex', gap: '12px', alignItems: 'flex-start', padding: '6px 0', borderBottom: i < 4 ? '1px solid var(--border-subtle)' : 'none' }}>
                                                <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', minWidth: '60px', flexShrink: 0 }}>
                                                    {new Date(log.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                                </span>
                                                <span style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>{log.summary || log.action_type}</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Quick Actions */}
                            <div style={{ display: 'flex', gap: '10px', marginTop: '20px' }}>
                                {shouldShowChatEntry && <button className="btn btn-secondary" onClick={() => setActiveTab('chat')}>{t('agent.actions.chat')}</button>}
                                {(agent as any)?.agent_type !== 'openclaw' && <button className="btn btn-secondary" onClick={() => setActiveTab('aware')}>{t('agent.tabs.aware')}</button>}
                                <button className="btn btn-secondary" onClick={() => setActiveTab('settings')}>{t('agent.tabs.settings')}</button>
                            </div>
                        </div>
                    );
                })()}

                {/* ── Aware Tab ── */}
                {activeTab === 'aware' && (
                    <AgentTriggers agentId={id!} activityLogs={activityLogs} />
                )}

                {/* ── Mind Tab (Soul + Memory + Heartbeat) ── */}
                {activeTab === 'mind' && (() => {
                    const adapter: FileBrowserApi = {
                        list: (p) => fileApi.list(id!, p),
                        read: (p) => fileApi.read(id!, p),
                        write: (p, c) => fileApi.write(id!, p, c),
                        delete: (p) => fileApi.delete(id!, p),
                        downloadUrl: (p) => fileApi.downloadUrl(id!, p),
                    };
                    return (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                            {/* Soul Section */}
                            <div>
                                <h3 style={{ marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    🧬 {t('agent.soul.title')}
                                </h3>
                                <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                                    {t('agent.mind.soulDesc', '核心身份、人格与行为边界。')}
                                </p>
                                <FileBrowser api={adapter} singleFile="soul.md" title="" features={{ edit: (agent as any)?.access_level !== 'use' }} />
                            </div>

                            {/* Memory Section */}
                            <div>
                                <h3 style={{ marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    🧠 {t('agent.memory.title')}
                                </h3>
                                <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                                    {t('agent.mind.memoryDesc', '通过对话和经验积累的持久记忆。')}
                                </p>
                                <FileBrowser api={adapter} rootPath="memory" readOnly features={{}} />
                            </div>

                            {/* Heartbeat Section */}
                            <div>
                                <h3 style={{ marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    💓 {t('agent.mind.heartbeatTitle', '心跳')}
                                </h3>
                                <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                                    {t('agent.mind.heartbeatDesc', '定期感知检查的指令。员工在每次心跳时读取此文件。')}
                                </p>
                                <FileBrowser api={adapter} singleFile="HEARTBEAT.md" title="" features={{ edit: (agent as any)?.access_level !== 'use' }} />
                            </div>
                        </div>
                    );
                })()}

                {/* ── Tools Tab ── */}
                {activeTab === 'tools' && (
                    <div>
                        <div style={{ marginBottom: '16px' }}>
                            <h3 style={{ marginBottom: '4px' }}>{t('agent.toolMgmt.title')}</h3>
                            <p style={{ fontSize: '13px', color: 'var(--text-tertiary)' }}>{t('agent.toolMgmt.description')}</p>
                        </div>
                        <AgentTools agentId={id!} canManage={canManage} />
                    </div>
                )}

                {/* ── Skills Tab ── */}
                {activeTab === 'skills' && (() => {
                    const adapter: FileBrowserApi = {
                        list: (p) => fileApi.list(id!, p),
                        read: (p) => fileApi.read(id!, p),
                        write: (p, c) => fileApi.write(id!, p, c),
                        delete: (p) => fileApi.delete(id!, p),
                        upload: (file, path, onProgress) => fileApi.upload(id!, file, path, onProgress),
                        downloadUrl: (p) => fileApi.downloadUrl(id!, p),
                    };
                    return (
                        <div>
                            <div style={{ marginBottom: '16px' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <div>
                                        <h3 style={{ marginBottom: '4px' }}>{t('agent.skills.title')}</h3>
                                        <p style={{ fontSize: '13px', color: 'var(--text-tertiary)' }}>{t('agent.skills.description')}</p>
                                    </div>
                                    <div style={{ display: 'flex', gap: '8px', flexShrink: 0 }}>
                                        <button
                                            className="btn btn-secondary"
                                            style={{ fontSize: '13px' }}
                                            onClick={() => { setShowAgentUrlImport(true); setAgentUrlInput(''); }}
                                        >
                                            从 URL 导入
                                        </button>
                                        <button
                                            className="btn btn-secondary"
                                            style={{ fontSize: '13px' }}
                                            onClick={() => { setShowAgentClawhub(true); setAgentClawhubQuery(''); setAgentClawhubResults([]); }}
                                        >
                                            浏览技能市场
                                        </button>
                                        <button
                                            className="btn btn-primary"
                                            style={{ display: 'flex', alignItems: 'center', gap: '6px', whiteSpace: 'nowrap' }}
                                            onClick={() => setShowImportSkillModal(true)}
                                        >
                                            从预设导入
                                        </button>
                                    </div>
                                </div>
                                <div style={{ marginTop: '8px', padding: '10px 14px', background: 'var(--bg-secondary)', borderRadius: '8px', fontSize: '12px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                                    <strong>技能格式：</strong><br />
                                    • <code>skills/my-skill/SKILL.md</code> — {t('agent.skills.folderFormat', '每个技能是一个包含 SKILL.md 文件和可选辅助文件的文件夹（scripts/, examples/）')}
                                </div>
                            </div>
                            <FileBrowser api={adapter} rootPath="skills" features={{ newFile: true, edit: true, delete: true, newFolder: true, upload: true, directoryNavigation: true }} title={t('agent.skills.skillFiles')} />

                            {/* Browse ClawHub Modal */}
                            {showAgentClawhub && (
                                <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={() => setShowAgentClawhub(false)}>
                                    <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', maxWidth: '600px', width: '90%', maxHeight: '70vh', display: 'flex', flexDirection: 'column', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                                            <h3>浏览技能市场</h3>
                                            <button onClick={() => setShowAgentClawhub(false)} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)', padding: '4px 8px' }}>x</button>
                                        </div>
                                        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', margin: '0 0 12px' }}>
                                            Search and install skills from ClawHub directly into this agent's workspace.
                                        </p>
                                        <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
                                            <input
                                                className="input"
                                                placeholder="搜索技能..."
                                                value={agentClawhubQuery}
                                                onChange={e => setAgentClawhubQuery(e.target.value)}
                                                onKeyDown={e => {
                                                    if (e.key === 'Enter' && agentClawhubQuery.trim()) {
                                                        setAgentClawhubSearching(true);
                                                        skillApi.clawhub.search(agentClawhubQuery).then(r => { setAgentClawhubResults(r); setAgentClawhubSearching(false); }).catch(() => setAgentClawhubSearching(false));
                                                    }
                                                }}
                                                style={{ flex: 1, fontSize: '13px' }}
                                            />
                                            <button
                                                className="btn btn-primary"
                                                style={{ fontSize: '13px' }}
                                                disabled={!agentClawhubQuery.trim() || agentClawhubSearching}
                                                onClick={() => {
                                                    setAgentClawhubSearching(true);
                                                    skillApi.clawhub.search(agentClawhubQuery).then(r => { setAgentClawhubResults(r); setAgentClawhubSearching(false); }).catch(() => setAgentClawhubSearching(false));
                                                }}
                                            >
                                                {agentClawhubSearching ? '搜索中...' : '搜索'}
                                            </button>
                                        </div>
                                        <div style={{ flex: 1, overflowY: 'auto' }}>
                                            {agentClawhubResults.length === 0 && !agentClawhubSearching && (
                                                <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-tertiary)', fontSize: '13px' }}>搜索技能市场查找技能</div>
                                            )}
                                            {agentClawhubResults.map((r: any) => (
                                                <div key={r.slug} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 12px', borderRadius: '8px', marginBottom: '6px', border: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)' }}>
                                                    <div style={{ flex: 1 }}>
                                                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                            <span style={{ fontWeight: 600, fontSize: '13px' }}>{r.displayName || r.slug}</span>
                                                            {r.version && <span style={{ fontSize: '10px', color: 'var(--accent-text)', background: 'var(--accent-subtle)', padding: '1px 5px', borderRadius: '4px' }}>v{r.version}</span>}
                                                        </div>
                                                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{r.summary?.substring(0, 100)}{r.summary?.length > 100 ? '...' : ''}</div>
                                                        {r.updatedAt && <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px', opacity: 0.7 }}>Updated {new Date(r.updatedAt).toLocaleDateString()}</div>}
                                                    </div>
                                                    <button
                                                        className="btn btn-secondary"
                                                        style={{ fontSize: '12px', padding: '5px 12px', marginLeft: '12px' }}
                                                        disabled={agentClawhubInstalling === r.slug}
                                                        onClick={async () => {
                                                            setAgentClawhubInstalling(r.slug);
                                                            try {
                                                                const res = await skillApi.agentImport.fromClawhub(id!, r.slug);
                                                                useToastStore.getState().showToast(`Installed "${r.displayName || r.slug}" (${res.files_written} files)`, 'success');
                                                                queryClient.invalidateQueries({ queryKey: ['files', id, 'skills'] });
                                                            } catch (err: any) {
                                                                useToastStore.getState().showToast(`Import failed: ${err?.message || err}`, 'error');
                                                            } finally {
                                                                setAgentClawhubInstalling(null);
                                                            }
                                                        }}
                                                    >
                                                        {agentClawhubInstalling === r.slug ? '安装中...' : '安装'}
                                                    </button>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Import from URL Modal */}
                            {showAgentUrlImport && (
                                <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={() => setShowAgentUrlImport(false)}>
                                    <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', maxWidth: '500px', width: '90%', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                                            <h3>从 GitHub URL 导入</h3>
                                            <button onClick={() => setShowAgentUrlImport(false)} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)', padding: '4px 8px' }}>x</button>
                                        </div>
                                        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', margin: '0 0 12px' }}>
                                            Paste a GitHub URL pointing to a skill directory (must contain SKILL.md).
                                        </p>
                                        <input
                                            className="input"
                                            placeholder="https://github.com/owner/repo/tree/main/path/to/skill"
                                            value={agentUrlInput}
                                            onChange={e => setAgentUrlInput(e.target.value)}
                                            style={{ width: '100%', fontSize: '13px', marginBottom: '12px', boxSizing: 'border-box' }}
                                        />
                                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
                                            <button className="btn btn-secondary" onClick={() => setShowAgentUrlImport(false)}>取消</button>
                                            <button
                                                className="btn btn-primary"
                                                disabled={!agentUrlInput.trim() || agentUrlImporting}
                                                onClick={async () => {
                                                    setAgentUrlImporting(true);
                                                    try {
                                                        const res = await skillApi.agentImport.fromUrl(id!, agentUrlInput.trim());
                                                        useToastStore.getState().showToast(`Imported ${res.files_written} files`, 'success');
                                                        queryClient.invalidateQueries({ queryKey: ['files', id, 'skills'] });
                                                        setShowAgentUrlImport(false);
                                                    } catch (err: any) {
                                                        useToastStore.getState().showToast(`Import failed: ${err?.message || err}`, 'error');
                                                    } finally {
                                                        setAgentUrlImporting(false);
                                                    }
                                                }}
                                            >
                                                {agentUrlImporting ? '导入中...' : '导入'}
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Import from Presets Modal */}
                            {showImportSkillModal && (
                                <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1000, display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={() => setShowImportSkillModal(false)}>
                                    <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', maxWidth: '600px', width: '90%', maxHeight: '70vh', display: 'flex', flexDirection: 'column', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                                            <h3>📦 {t('agent.skills.importPreset', '从预设导入')}</h3>
                                            <button onClick={() => setShowImportSkillModal(false)} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)', padding: '4px 8px' }}>✕</button>
                                        </div>
                                        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', margin: '0 0 16px' }}>
                                            {t('agent.skills.importDesc', '选择一个预设技能导入到此员工。所有技能文件将被复制到员工的 skills 文件夹。')}
                                        </p>
                                        <div style={{ flex: 1, overflowY: 'auto' }}>
                                            {!globalSkillsForImport ? (
                                                <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-tertiary)' }}>加载中...</div>
                                            ) : globalSkillsForImport.length === 0 ? (
                                                <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-tertiary)' }}>暂无预设技能</div>
                                            ) : (
                                                globalSkillsForImport.map((skill: any) => (
                                                    <div
                                                        key={skill.id}
                                                        style={{
                                                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                                                            padding: '12px 14px', borderRadius: '8px', marginBottom: '8px',
                                                            border: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)',
                                                            transition: 'border-color 0.15s',
                                                        }}
                                                        onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--accent-primary)')}
                                                        onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border-subtle)')}
                                                    >
                                                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1 }}>
                                                            <span style={{ fontSize: '20px' }}>{skill.icon || '📋'}</span>
                                                            <div>
                                                                <div style={{ fontWeight: 600, fontSize: '14px' }}>{skill.name}</div>
                                                                <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                                                                    {skill.description?.substring(0, 100)}{skill.description?.length > 100 ? '...' : ''}
                                                                </div>
                                                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                                                                    📁 {skill.folder_name}
                                                                    {skill.is_default && <span style={{ marginLeft: '8px', color: 'var(--accent-primary)', fontWeight: 600 }}>✓ Default</span>}
                                                                </div>
                                                            </div>
                                                        </div>
                                                        <button
                                                            className="btn btn-secondary"
                                                            style={{ whiteSpace: 'nowrap', fontSize: '12px', padding: '6px 14px' }}
                                                            disabled={importingSkillId === skill.id}
                                                            onClick={async () => {
                                                                setImportingSkillId(skill.id);
                                                                try {
                                                                    const res = await fileApi.importSkill(id!, skill.id);
                                                                    useToastStore.getState().showToast(`Imported "${skill.name}" (${res.files_written} files)`, 'success');
                                                                    queryClient.invalidateQueries({ queryKey: ['files', id, 'skills'] });
                                                                    setShowImportSkillModal(false);
                                                                } catch (err: any) {
                                                                    useToastStore.getState().showToast(`Import failed: ${err?.message || err}`, 'error');
                                                                } finally {
                                                                    setImportingSkillId(null);
                                                                }
                                                            }}
                                                        >
                                                            {importingSkillId === skill.id ? '⏳ ...' : '⬇️ Import'}
                                                        </button>
                                                    </div>
                                                ))
                                            )}
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>
                    );
                })()}

                {/* ── Relationships Tab ── */}
                {activeTab === 'relationships' && (
                    <AgentRelations agentId={id!} readOnly={(agent as any)?.access_level === 'use'} />
                )}

                {/* ── Workspace Tab ── */}
                {activeTab === 'workspace' && (() => {
                    const adapter: FileBrowserApi = {
                        list: (p) => fileApi.list(id!, p),
                        read: (p) => fileApi.read(id!, p),
                        write: (p, c) => fileApi.write(id!, p, c),
                        delete: (p) => fileApi.delete(id!, p),
                        upload: (file, path, onProgress) => fileApi.upload(id!, file, path + '/', onProgress),
                        downloadUrl: (p) => fileApi.downloadUrl(id!, p),
                    };
                    return <FileBrowser api={adapter} rootPath="workspace" features={{ upload: true, newFile: true, newFolder: true, edit: true, delete: true, directoryNavigation: true }} />;
                })()}

                {/* ── Chat Tab ── */}
                {showChatSection && (
                    <AgentChat agentId={id!} agent={agent} supportsVision={supportsVision} llmModels={llmModels} />
                )}

                {/* ── Activity Log Tab ── */}
                {activeTab === 'activityLog' && (() => {
                    const userActionTypes = ['chat_reply', 'tool_call', 'task_created', 'task_updated', 'file_written', 'error'];
                    const heartbeatTypes = ['heartbeat', 'plaza_post'];
                    const scheduleTypes = ['schedule_run'];
                    const messageTypes = ['feishu_msg_sent', 'agent_msg_sent', 'web_msg_sent'];

                    let filteredLogs = activityLogs;
                    if (logFilter === 'user') {
                        filteredLogs = activityLogs.filter((l: any) => userActionTypes.includes(l.action_type));
                    } else if (logFilter === 'backend') {
                        filteredLogs = activityLogs.filter((l: any) => !userActionTypes.includes(l.action_type));
                    } else if (logFilter === 'heartbeat') {
                        filteredLogs = activityLogs.filter((l: any) => heartbeatTypes.includes(l.action_type));
                    } else if (logFilter === 'schedule') {
                        filteredLogs = activityLogs.filter((l: any) => scheduleTypes.includes(l.action_type));
                    } else if (logFilter === 'messages') {
                        filteredLogs = activityLogs.filter((l: any) => messageTypes.includes(l.action_type));
                    }

                    const filterBtn = (key: string, label: string, indent = false) => (
                        <button
                            key={key}
                            onClick={() => setLogFilter(key)}
                            style={{
                                padding: indent ? '4px 10px 4px 20px' : '6px 14px',
                                fontSize: indent ? '11px' : '12px',
                                fontWeight: logFilter === key ? 600 : 400,
                                color: logFilter === key ? 'var(--accent-primary)' : 'var(--text-secondary)',
                                background: logFilter === key ? 'rgba(99,102,241,0.1)' : 'transparent',
                                border: logFilter === key ? '1px solid var(--accent-primary)' : '1px solid var(--border-subtle)',
                                borderRadius: '6px',
                                cursor: 'pointer',
                                transition: 'all 0.15s',
                                whiteSpace: 'nowrap' as const,
                            }}
                        >
                            {label}
                        </button>
                    );

                    return (
                        <div>
                            <h3 style={{ marginBottom: '12px' }}>{t('agent.activityLog.title')}</h3>

                            {/* Filter tabs */}
                            <div style={{ display: 'flex', gap: '6px', marginBottom: '16px', flexWrap: 'wrap', alignItems: 'center' }}>
                                {filterBtn('user', '👤 ' + t('agent.activityLog.userActions', '用户操作'))}
                                {(agent as any)?.agent_type !== 'openclaw' && (<>
                                    {filterBtn('backend', '⚙️ ' + t('agent.activityLog.backendServices', '后端服务'))}
                                    {(logFilter === 'backend' || logFilter === 'heartbeat' || logFilter === 'schedule' || logFilter === 'messages') && (
                                        <>
                                            <span style={{ color: 'var(--text-tertiary)', fontSize: '11px' }}>│</span>
                                            {filterBtn('heartbeat', '💓 ' + t('agent.mind.heartbeatTitle'))}
                                            {filterBtn('schedule', '⏰ ' + t('agent.activityLog.scheduleCron'), true)}
                                            {filterBtn('messages', '📨 ' + t('agent.activityLog.messages'), true)}
                                        </>
                                    )}
                                </>)}
                            </div>

                            {filteredLogs.length > 0 ? (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                                    {filteredLogs.map((log: any) => {
                                        const icons: Record<string, string> = {
                                            chat_reply: '💬', tool_call: '⚡', feishu_msg_sent: '📤',
                                            agent_msg_sent: '🤖', web_msg_sent: '🌐', task_created: '📋',
                                            task_updated: '✅', file_written: '📝', error: '❌',
                                            schedule_run: '⏰', heartbeat: '💓', plaza_post: '🏛️',
                                        };
                                        const time = log.created_at ? new Date(log.created_at).toLocaleString('zh-CN', {
                                            month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
                                        }) : '';
                                        const isExpanded = expandedLogId === log.id;
                                        return (
                                            <div key={log.id}
                                                onClick={() => setExpandedLogId(isExpanded ? null : log.id)}
                                                style={{
                                                    padding: '10px 14px', borderRadius: '8px', cursor: 'pointer',
                                                    background: isExpanded ? 'var(--bg-elevated)' : 'var(--bg-secondary)', fontSize: '13px',
                                                    border: isExpanded ? '1px solid var(--accent-primary)' : '1px solid transparent',
                                                    transition: 'all 0.15s ease',
                                                }}
                                            >
                                                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                                                    <span style={{ fontSize: '16px', flexShrink: 0, marginTop: '1px' }}>
                                                        {icons[log.action_type] || '·'}
                                                    </span>
                                                    <div style={{ flex: 1, minWidth: 0 }}>
                                                        <div style={{ fontWeight: 500, marginBottom: '2px' }}>{log.summary}</div>
                                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                                            {time} · {log.action_type}
                                                            {log.detail && !isExpanded && <span style={{ marginLeft: '8px', color: 'var(--accent-primary)' }}>▸ 详情</span>}
                                                        </div>
                                                    </div>
                                                </div>
                                                {isExpanded && log.detail && (
                                                    <div style={{ marginTop: '8px', padding: '10px', borderRadius: '6px', background: 'var(--bg-primary)', fontSize: '12px', fontFamily: 'monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-all', lineHeight: '1.6', color: 'var(--text-secondary)', maxHeight: '300px', overflowY: 'auto' }}>
                                                        {Object.entries(log.detail).map(([k, v]: [string, any]) => (
                                                            <div key={k} style={{ marginBottom: '6px' }}>
                                                                <span style={{ color: 'var(--accent-primary)', fontWeight: 600 }}>{k}:</span>{' '}
                                                                <span>{typeof v === 'object' ? JSON.stringify(v, null, 2) : String(v)}</span>
                                                            </div>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            ) : (
                                <div className="card" style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                                    {t('agent.activityLog.noRecords')}
                                </div>
                            )}
                        </div>
                    );
                })()}

                {/* ── Approvals Tab ── */}
                {activeTab === 'approvals' && (
                    <AgentApprovals agentId={id!} />
                )}

                {/* ── Settings Tab ── */}
                {activeTab === 'settings' && (() => {
                    if (isOpenClaw) return <OpenClawSettings agent={agent} agentId={id!} />;
                    if (isHumanEmployee) return <HumanEmployeeSettings employeeId={id!} employee={agent} />;
                    if (isFixedEmployee) return (
                        <FixedEmployeeSettings
                            employeeId={id!}
                            employeeCode={(agent as any)?.name}
                            employeeName={(agent as any)?.name}
                        />
                    );
                    if ((agent as any)?.origin === 'evolved') return <EvolvedEmployeeSettings employeeId={id!} employee={agent} />;
                    return <AgentSettings agent={agent} agentId={id!} canManage={canManage} />;
                })()}
            </div>

            {/* ── Modals ── */}

            <PromptModal
                open={!!promptModal}
                title={promptModal?.title || ''}
                placeholder={promptModal?.placeholder || ''}
                onCancel={() => setPromptModal(null)}
                onConfirm={async (value) => {
                    const action = promptModal?.action;
                    setPromptModal(null);
                    if (action === 'newFolder') {
                        await fileApi.write(id!, `${workspacePath}/${value}/.gitkeep`, '');
                        queryClient.invalidateQueries({ queryKey: ['files', id, workspacePath] });
                    } else if (action === 'newFile') {
                        await fileApi.write(id!, `${workspacePath}/${value}`, '');
                        queryClient.invalidateQueries({ queryKey: ['files', id, workspacePath] });
                    } else if (action === 'newSkill') {
                        const template = `---\nname: ${value}\ndescription: Describe what this skill does\n---\n\n# ${value}\n\n## Overview\nDescribe the purpose and when to use this skill.\n\n## Process\n1. Step one\n2. Step two\n\n## Output Format\nDescribe the expected output format.\n`;
                        await fileApi.write(id!, `skills/${value}/SKILL.md`, template);
                        queryClient.invalidateQueries({ queryKey: ['files', id, 'skills'] });
                    }
                }}
            />

            <ConfirmModal
                open={!!deleteConfirm}
                title={t('common.delete')}
                message={`${t('common.delete')}: ${deleteConfirm?.name}?`}
                confirmLabel={t('common.delete')}
                danger
                onCancel={() => setDeleteConfirm(null)}
                onConfirm={async () => {
                    const path = deleteConfirm?.path;
                    setDeleteConfirm(null);
                    if (path) {
                        try {
                            await fileApi.delete(id!, path);
                            queryClient.invalidateQueries({ queryKey: ['files', id, workspacePath] });
                            showToast(t('common.delete'));
                        } catch (err: any) {
                            showToast(t('agent.upload.failed'), 'error');
                        }
                    }
                }}
            />

            {uploadToast && (
                <div style={{
                    position: 'fixed', top: '20px', right: '20px', zIndex: 20000,
                    padding: '12px 20px', borderRadius: '8px',
                    background: uploadToast.type === 'success' ? 'rgba(34, 197, 94, 0.9)' : 'rgba(239, 68, 68, 0.9)',
                    color: '#fff', fontSize: '14px', fontWeight: 500,
                    boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
                }}>
                    {uploadToast.message}
                </div>
            )}

            {/* Expiry Editor Modal (admin only) */}
            {showExpiryModal && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.55)', zIndex: 9000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    onClick={() => setShowExpiryModal(false)}>
                    <div style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)', borderRadius: '12px', padding: '24px', width: '360px', maxWidth: '90vw' }}
                        onClick={e => e.stopPropagation()}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
                            <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 600 }}>⏰ {t('agent.settings.expiry.title')}</h3>
                            <button onClick={() => setShowExpiryModal(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-tertiary)', fontSize: '18px', lineHeight: 1 }}>×</button>
                        </div>
                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '16px' }}>
                            {(agent as any).is_expired
                                ? <span style={{ color: 'var(--error)', fontWeight: 600 }}>⏰ {t('agent.settings.expiry.expired')}</span>
                                : (agent as any).expires_at
                                    ? <>{t('agent.settings.expiry.currentExpiry')} <strong>{new Date((agent as any).expires_at).toLocaleString(i18n.language === 'zh' ? 'zh-CN' : 'en-US')}</strong></>
                                    : <span style={{ color: 'var(--success)' }}>{t('agent.settings.expiry.neverExpires')}</span>
                            }
                        </div>
                        <div style={{ marginBottom: '16px' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>{t('agent.settings.expiry.quickRenew')}</div>
                            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                                {([
                                    ['+ 24h', 24],
                                    [`+ ${t('agent.settings.expiry.days', { count: 7 })}`, 168],
                                    [`+ ${t('agent.settings.expiry.days', { count: 30 })}`, 720],
                                    [`+ ${t('agent.settings.expiry.days', { count: 90 })}`, 2160],
                                ] as [string, number][]).map(([label, h]) => (
                                    <button key={h} onClick={() => addHours(h)}
                                        style={{ padding: '4px 10px', borderRadius: '6px', border: '1px solid var(--border-subtle)', background: 'var(--bg-primary)', cursor: 'pointer', fontSize: '12px', color: 'var(--text-primary)' }}>
                                        {label}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div style={{ marginBottom: '20px' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '6px' }}>{t('agent.settings.expiry.customDeadline')}</div>
                            <input type="datetime-local" value={expiryValue} onChange={e => setExpiryValue(e.target.value)}
                                style={{ width: '100%', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--border-subtle)', background: 'var(--bg-primary)', color: 'var(--text-primary)', fontSize: '13px', boxSizing: 'border-box' }} />
                        </div>
                        <div style={{ display: 'flex', gap: '8px', justifyContent: 'space-between', alignItems: 'center' }}>
                            <button onClick={() => saveExpiry(true)} disabled={expirySaving}
                                style={{ padding: '7px 12px', borderRadius: '8px', border: '1px solid var(--border-subtle)', background: 'none', cursor: 'pointer', fontSize: '12px', color: 'var(--text-secondary)' }}>
                                🔓 {t('agent.settings.expiry.neverExpires')}
                            </button>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <button onClick={() => setShowExpiryModal(false)} disabled={expirySaving}
                                    style={{ padding: '7px 14px', borderRadius: '8px', border: '1px solid var(--border-subtle)', background: 'none', cursor: 'pointer', fontSize: '13px', color: 'var(--text-secondary)' }}>
                                    {t('common.cancel')}
                                </button>
                                <button onClick={() => saveExpiry(false)} disabled={expirySaving || !expiryValue}
                                    className="btn btn-primary"
                                    style={{ opacity: !expiryValue ? 0.5 : 1 }}>
                                    {expirySaving ? t('agent.settings.expiry.saving') : t('common.save')}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}

// Wrap with imported ErrorBoundary
export default function AgentDetailWithErrorBoundary() {
    return (
        <AgentDetailErrorBoundary>
            <AgentDetailInner />
        </AgentDetailErrorBoundary>
    );
}
