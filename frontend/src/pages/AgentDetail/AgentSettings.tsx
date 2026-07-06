import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { agentApi, channelApi, enterpriseApi } from '../../services/api';
import { request as fetchAuth } from '../../services/apiBase';
import { useToastStore } from '../../stores/toastStore';
import ChannelConfig from '../../components/ChannelConfig';

// Format large token numbers with K/M suffixes
const formatTokens = (n: number) => {
    if (!n) return '0';
    if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`;
    if (n >= 1000) return `${(n / 1000).toFixed(1)}K`;
    return String(n);
};

interface AgentSettingsProps {
    agent: any;
    agentId: string;
    canManage: boolean;
}

export default function AgentSettings({ agent, agentId, canManage }: AgentSettingsProps) {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();
    const queryClient = useQueryClient();

    // Settings form local state
    const [settingsForm, setSettingsForm] = useState({
        primary_model_id: '',
        fallback_model_id: '',
        context_window_size: 100,
        max_tool_rounds: 50,
        max_tokens_per_day: '' as string | number,
        max_tokens_per_month: '' as string | number,
        max_triggers: 20,
        min_poll_interval_min: 5,
        webhook_rate_limit: 5,
    });
    const [settingsSaving, setSettingsSaving] = useState(false);
    const [settingsSaved, setSettingsSaved] = useState(false);
    const [settingsError, setSettingsError] = useState('');
    const settingsInitRef = useRef(false);

    // Welcome message editor state
    const [wmDraft, setWmDraft] = useState('');
    const [wmSaved, setWmSaved] = useState(false);
    useEffect(() => { setWmDraft(agent?.welcome_message || ''); }, [agent?.welcome_message]);

    // Sync settings form from server data on load
    useEffect(() => {
        if (agent && !settingsInitRef.current) {
            setSettingsForm({
                primary_model_id: agent.primary_model_id || '',
                fallback_model_id: agent.fallback_model_id || '',
                context_window_size: agent.context_window_size ?? 100,
                max_tool_rounds: agent.max_tool_rounds ?? 50,
                max_tokens_per_day: agent.max_tokens_per_day || '',
                max_tokens_per_month: agent.max_tokens_per_month || '',
                max_triggers: agent.max_triggers ?? 20,
                min_poll_interval_min: agent.min_poll_interval_min ?? 5,
                webhook_rate_limit: agent.webhook_rate_limit ?? 5,
            });
            settingsInitRef.current = true;
        }
    }, [agent]);

    const { data: channelConfig } = useQuery({
        queryKey: ['channel', agentId],
        queryFn: () => channelApi.get(agentId),
        enabled: !!agentId,
    });

    const { data: llmModels = [] } = useQuery({
        queryKey: ['llm-models'],
        queryFn: () => enterpriseApi.llmModels(),
    });

    const { data: permData } = useQuery({
        queryKey: ['agent-permissions', agentId],
        queryFn: () => fetchAuth<any>(`/agents/${encodeURIComponent(agentId!)}/permissions`),
        enabled: !!agentId,
    });

    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

    // Check if form has unsaved changes
    const hasChanges = (
        settingsForm.primary_model_id !== (agent?.primary_model_id || '') ||
        settingsForm.fallback_model_id !== (agent?.fallback_model_id || '') ||
        settingsForm.context_window_size !== (agent?.context_window_size ?? 100) ||
        settingsForm.max_tool_rounds !== (agent?.max_tool_rounds ?? 50) ||
        String(settingsForm.max_tokens_per_day) !== String(agent?.max_tokens_per_day || '') ||
        String(settingsForm.max_tokens_per_month) !== String(agent?.max_tokens_per_month || '') ||
        settingsForm.max_triggers !== (agent?.max_triggers ?? 20) ||
        settingsForm.min_poll_interval_min !== (agent?.min_poll_interval_min ?? 5) ||
        settingsForm.webhook_rate_limit !== (agent?.webhook_rate_limit ?? 5)
    );

    const handleSaveSettings = async () => {
        setSettingsSaving(true);
        setSettingsError('');
        try {
            const result: any = await agentApi.update(agentId, {
                primary_model_id: settingsForm.primary_model_id || null,
                fallback_model_id: settingsForm.fallback_model_id || null,
                context_window_size: settingsForm.context_window_size,
                max_tool_rounds: settingsForm.max_tool_rounds,
                max_tokens_per_day: settingsForm.max_tokens_per_day ? Number(settingsForm.max_tokens_per_day) : null,
                max_tokens_per_month: settingsForm.max_tokens_per_month ? Number(settingsForm.max_tokens_per_month) : null,
                max_triggers: settingsForm.max_triggers,
                min_poll_interval_min: settingsForm.min_poll_interval_min,
                webhook_rate_limit: settingsForm.webhook_rate_limit,
            } as any);
            queryClient.invalidateQueries({ queryKey: ['agent', agentId] });
            settingsInitRef.current = false;

            const clamped = result?._clamped_fields;
            if (clamped && clamped.length > 0) {
                const isCh = i18n.language?.startsWith('zh');
                const fieldNames: Record<string, string> = isCh
                    ? { min_poll_interval_min: 'Poll 最短间隔', webhook_rate_limit: 'Webhook 频率限制', heartbeat_interval_minutes: '心跳间隔' }
                    : { min_poll_interval_min: 'Poll 最短间隔', webhook_rate_limit: 'Webhook 频率限制', heartbeat_interval_minutes: '心跳间隔' };
                const msgs = clamped.map((c: any) => {
                    const name = fieldNames[c.field] || c.field;
                    return isCh ? `${name}: ${c.requested} -> ${c.applied} (公司策略限制)` : `${name}: ${c.requested} -> ${c.applied} (company policy)`;
                });
                setSettingsError((isCh ? '以下值已被调整：\n' : '以下值已被调整：\n') + msgs.join('\n'));
                setTimeout(() => setSettingsError(''), 5000);
            }

            setSettingsSaved(true);
            setTimeout(() => setSettingsSaved(false), 2000);
        } catch (e: any) {
            setSettingsError(e?.message || '保存失败');
        } finally {
            setSettingsSaving(false);
        }
    };

    return (
        <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px', position: 'sticky', top: 0, zIndex: 10, background: 'var(--bg-primary)', paddingTop: '4px', paddingBottom: '12px', borderBottom: '1px solid var(--border-subtle)' }}>
                <h3 style={{ margin: 0 }}>{t('agent.settings.title')}</h3>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    {settingsSaved && <span style={{ fontSize: '12px', color: 'var(--success)' }}>{t('agent.settings.saved', 'Saved')}</span>}
                    {settingsError && <span style={{ fontSize: '12px', color: settingsError.includes('adjusted') ? 'var(--warning)' : 'var(--error)', whiteSpace: 'pre-line' }}>{settingsError}</span>}
                    <button className="btn btn-primary" disabled={!hasChanges || settingsSaving} onClick={handleSaveSettings} style={{ opacity: hasChanges ? 1 : 0.5, cursor: hasChanges ? 'pointer' : 'default', padding: '6px 20px', fontSize: '13px' }}>
                        {settingsSaving ? t('agent.settings.saving', '保存中...') : t('agent.settings.save', '保存')}
                    </button>
                </div>
            </div>

            {/* Model Selection */}
            {agent?.agent_type !== 'openclaw' && (
                <div className="card" style={{ marginBottom: '12px' }}>
                    <h4 style={{ marginBottom: '12px' }}>{t('agent.settings.modelConfig')}</h4>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                        <div>
                            <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '6px' }}>{t('agent.settings.primaryModel')}</label>
                            <select className="input" value={settingsForm.primary_model_id} onChange={(e) => setSettingsForm(f => ({ ...f, primary_model_id: e.target.value }))}>
                                <option value="">--</option>
                                {llmModels.filter((m: any) => m.enabled || m.id === settingsForm.primary_model_id).map((m: any) => (
                                    <option key={m.id} value={m.id}>{m.label} ({m.provider}/{m.model}){!m.enabled ? ` [${t('enterprise.llm.disabled', 'Disabled')}]` : ''}</option>
                                ))}
                            </select>
                            {settingsForm.primary_model_id && llmModels.some((m: any) => m.id === settingsForm.primary_model_id && !m.enabled) && (
                                <div style={{ fontSize: '11px', color: 'var(--error)', marginTop: '4px' }}>{t('agent.settings.modelDisabledWarning', '该模型已被管理员禁用。')}
                            </div>
                            )}
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('agent.settings.primaryModel')}</div>
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '6px' }}>{t('agent.settings.fallbackModel')}</label>
                            <select className="input" value={settingsForm.fallback_model_id} onChange={(e) => setSettingsForm(f => ({ ...f, fallback_model_id: e.target.value }))}>
                                <option value="">--</option>
                                {llmModels.filter((m: any) => m.enabled || m.id === settingsForm.fallback_model_id).map((m: any) => (
                                    <option key={m.id} value={m.id}>{m.label} ({m.provider}/{m.model}){!m.enabled ? ` [${t('enterprise.llm.disabled', 'Disabled')}]` : ''}</option>
                                ))}
                            </select>
                            {settingsForm.fallback_model_id && llmModels.some((m: any) => m.id === settingsForm.fallback_model_id && !m.enabled) && (
                                <div style={{ fontSize: '11px', color: 'var(--error)', marginTop: '4px' }}>{t('agent.settings.modelDisabledWarning', '该模型已被管理员禁用。')}</div>
                            )}
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('agent.settings.fallbackModel')}</div>
                        </div>
                    </div>
                </div>
            )}

            {/* Context Window */}
            {agent?.agent_type !== 'openclaw' && (<>
                <div className="card" style={{ marginBottom: '12px' }}>
                    <h4 style={{ marginBottom: '12px' }}>{t('agent.settings.conversationContext')}</h4>
                    <div>
                        <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '6px' }}>{t('agent.settings.maxRounds')}</label>
                        <input className="input" type="number" min={10} max={500} value={settingsForm.context_window_size} onChange={(e) => setSettingsForm(f => ({ ...f, context_window_size: Math.max(10, Math.min(500, parseInt(e.target.value) || 100)) }))} style={{ width: '120px' }} />
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('agent.settings.roundsDesc')}</div>
                    </div>
                </div>
                <div className="card" style={{ marginBottom: '12px' }}>
                    <h4 style={{ marginBottom: '12px' }}>🔧 {t('agent.settings.maxToolRounds', 'Max Tool Call Rounds')}</h4>
                    <div>
                        <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '6px' }}>{t('agent.settings.maxToolRoundsLabel', 'Maximum rounds per message')}</label>
                        <input className="input" type="number" min={5} max={200} value={settingsForm.max_tool_rounds} onChange={(e) => setSettingsForm(f => ({ ...f, max_tool_rounds: Math.max(5, Math.min(200, parseInt(e.target.value) || 50)) }))} style={{ width: '120px' }} />
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('agent.settings.maxToolRoundsDesc', 'How many tool-calling rounds per message. Default: 50')}</div>
                    </div>
                </div>
            </>)}

            {/* Token Limits */}
            <div className="card" style={{ marginBottom: '12px' }}>
                <h4 style={{ marginBottom: '12px' }}>{t('agent.settings.tokenLimits')}</h4>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                    <div>
                        <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '6px' }}>{t('agent.settings.dailyLimit')}</label>
                        <input className="input" type="number" value={settingsForm.max_tokens_per_day} onChange={(e) => setSettingsForm(f => ({ ...f, max_tokens_per_day: e.target.value }))} placeholder={t("agent.settings.noLimit")} />
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('agent.settings.today')}: {formatTokens(agent?.tokens_used_today || 0)}</div>
                    </div>
                    <div>
                        <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '6px' }}>{t('agent.settings.monthlyLimit')}</label>
                        <input className="input" type="number" value={settingsForm.max_tokens_per_month} onChange={(e) => setSettingsForm(f => ({ ...f, max_tokens_per_month: e.target.value }))} placeholder={t("agent.settings.noLimit")} />
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('agent.settings.month')}: {formatTokens(agent?.tokens_used_month || 0)}</div>
                    </div>
                </div>
            </div>

            {/* Trigger Limits */}
            {agent?.agent_type !== 'openclaw' && (
                <div className="card" style={{ marginBottom: '12px' }}>
                    <h4 style={{ marginBottom: '4px' }}>{t('agentDetail.triggerLimits')}</h4>
                    <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                        {t('agentDetail.triggerLimitsDesc')}
                    </p>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px' }}>
                        <div>
                            <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '6px' }}>{t('agentDetail.maxTriggers')}</label>
                            <input className="input" type="number" min={1} max={100} value={settingsForm.max_triggers} onChange={(e) => setSettingsForm(f => ({ ...f, max_triggers: Math.max(1, Math.min(100, parseInt(e.target.value) || 20)) }))} style={{ width: '100%' }} />
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('agentDetail.maxTriggersDesc')}</div>
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '6px' }}>{t('agentDetail.minPollInterval')}</label>
                            <input className="input" type="number" min={1} max={60} value={settingsForm.min_poll_interval_min} onChange={(e) => setSettingsForm(f => ({ ...f, min_poll_interval_min: Math.max(1, Math.min(60, parseInt(e.target.value) || 5)) }))} style={{ width: '100%' }} />
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('agentDetail.minPollIntervalDesc')}</div>
                        </div>
                        <div>
                            <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '6px' }}>{t('agentDetail.webhookRateLimit')}</label>
                            <input className="input" type="number" min={1} max={60} value={settingsForm.webhook_rate_limit} onChange={(e) => setSettingsForm(f => ({ ...f, webhook_rate_limit: Math.max(1, Math.min(60, parseInt(e.target.value) || 5)) }))} style={{ width: '100%' }} />
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('agentDetail.webhookRateLimitDesc')}</div>
                        </div>
                    </div>
                </div>
            )}

            {/* Welcome Message */}
            {(() => {
                const saveWm = async () => {
                    try {
                        await agentApi.update(agentId, { welcome_message: wmDraft } as any);
                        queryClient.invalidateQueries({ queryKey: ['agent', agentId] });
                        setWmSaved(true);
                        setTimeout(() => setWmSaved(false), 2000);
                    } catch { }
                };
                return (
                    <div className="card" style={{ marginBottom: '12px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
                            <h4 style={{ margin: 0 }}>{t('agentDetail.welcomeMessage')}</h4>
                            {wmSaved && <span style={{ fontSize: '12px', color: 'var(--success)' }}>✓ {t('agentDetail.saved')}</span>}
                        </div>
                        <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                            {t('agentDetail.welcomeMessageDesc')}
                        </p>
                        <textarea className="input" rows={4} value={wmDraft} onChange={e => setWmDraft(e.target.value)} onBlur={saveWm} placeholder={t('agentDetail.welcomePlaceholder')} style={{ width: '100%', minHeight: '80px', resize: 'vertical', fontFamily: 'inherit', fontSize: '13px' }} />
                    </div>
                );
            })()}

            {/* Autonomy Policy */}
            {agent?.agent_type !== 'openclaw' && <div className="card" style={{ marginBottom: '12px' }}>
                <h4 style={{ marginBottom: '4px' }}>{t('agent.settings.autonomy.title')}</h4>
                <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '16px' }}>{t('agent.settings.autonomy.description')}</p>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {[
                        { key: 'read_files', label: t('agent.settings.autonomy.readFiles'), desc: t('agent.settings.autonomy.readFilesDesc') },
                        { key: 'write_workspace_files', label: t('agent.settings.autonomy.writeFiles'), desc: t('agent.settings.autonomy.writeFilesDesc') },
                        { key: 'delete_files', label: t('agent.settings.autonomy.deleteFiles'), desc: t('agent.settings.autonomy.deleteFilesDesc') },
                        { key: 'send_feishu_message', label: t('agent.settings.autonomy.sendFeishu'), desc: t('agent.settings.autonomy.sendFeishuDesc') },
                        { key: 'web_search', label: t('agent.settings.autonomy.webSearch'), desc: t('agent.settings.autonomy.webSearchDesc') },
                        { key: 'manage_tasks', label: t('agent.settings.autonomy.manageTasks'), desc: t('agent.settings.autonomy.manageTasksDesc') },
                    ].map((action) => {
                        const currentLevel = (agent?.autonomy_policy as any)?.[action.key] || 'L1';
                        return (
                            <div key={action.key} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                                <div style={{ flex: 1 }}>
                                    <div style={{ fontWeight: 500, fontSize: '13px' }}>{action.label}</div>
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{action.desc}</div>
                                </div>
                                <select className="input" value={currentLevel} onChange={async (e) => { const newPolicy = { ...(agent?.autonomy_policy as any || {}), [action.key]: e.target.value }; await agentApi.update(agentId, { autonomy_policy: newPolicy } as any); queryClient.invalidateQueries({ queryKey: ['agent', agentId] }); }} style={{ width: '140px', fontSize: '12px', color: currentLevel === 'L1' ? 'var(--success)' : currentLevel === 'L2' ? 'var(--warning)' : 'var(--error)', fontWeight: 600 }}>
                                    <option value="L1">{t('agent.settings.autonomy.l1Auto')}</option>
                                    <option value="L2">{t('agent.settings.autonomy.l2Notify')}</option>
                                    <option value="L3">{t('agent.settings.autonomy.l3Approve')}</option>
                                </select>
                            </div>
                        );
                    })}
                </div>
            </div>}

            {/* Permission Management */}
            {(() => {
                const scopeLabels: Record<string, string> = {
                    company: '🏢 ' + t('agent.settings.perm.companyWide', '全公司'),
                    user: '👤 ' + t('agent.settings.perm.onlyMe', '仅自己'),
                };
                const handleScopeChange = async (newScope: string) => {
                    try {
                        await fetchAuth(`/agents/${encodeURIComponent(agentId!)}/permissions`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ scope_type: newScope, scope_ids: [], access_level: permData?.access_level || 'use' }) });
                        queryClient.invalidateQueries({ queryKey: ['agent-permissions', agentId] });
                        queryClient.invalidateQueries({ queryKey: ['agent', agentId] });
                    } catch (e) { console.error('Failed to update permissions', e); }
                };
                const handleAccessLevelChange = async (newLevel: string) => {
                    try {
                        await fetchAuth(`/agents/${encodeURIComponent(agentId!)}/permissions`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ scope_type: permData?.scope_type || 'company', scope_ids: permData?.scope_ids || [], access_level: newLevel }) });
                        queryClient.invalidateQueries({ queryKey: ['agent-permissions', agentId] });
                        queryClient.invalidateQueries({ queryKey: ['agent', agentId] });
                    } catch (e) { console.error('Failed to update access level', e); }
                };
                const isOwner = permData?.is_owner ?? false;
                const currentScope = permData?.scope_type || 'company';
                const currentAccessLevel = permData?.access_level || 'use';
                return (
                    <div className="card" style={{ marginBottom: '12px' }}>
                        <h4 style={{ marginBottom: '12px' }}>🔒 {t('agent.settings.perm.title', '访问权限')}</h4>
                        <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '16px' }}>{t('agent.settings.perm.description', '控制谁可以查看和与此员工交互。')}</p>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '16px' }}>
                            {(['company', 'user'] as const).map((scope) => (
                                <label key={scope} style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '12px 14px', borderRadius: '8px', cursor: isOwner ? 'pointer' : 'default', border: currentScope === scope ? '1px solid var(--accent-primary)' : '1px solid var(--border-subtle)', background: currentScope === scope ? 'rgba(99,102,241,0.06)' : 'transparent', opacity: isOwner ? 1 : 0.7, transition: 'all 0.15s' }}>
                                    <input type="radio" name="perm_scope" checked={currentScope === scope} disabled={!isOwner} onChange={() => handleScopeChange(scope)} style={{ accentColor: 'var(--accent-primary)' }} />
                                    <div>
                                        <div style={{ fontWeight: 500, fontSize: '13px' }}>{scopeLabels[scope]}</div>
                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                                            {scope === 'company' && t('agent.settings.perm.companyWideDesc', '所有用户均可使用此员工')}
                                            {scope === 'user' && t('agent.settings.perm.onlyMeDesc', '仅创建者可使用此员工')}
                                        </div>
                                    </div>
                                </label>
                            ))}
                        </div>
                        {currentScope === 'company' && isOwner && (
                            <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: '12px' }}>
                                <label style={{ display: 'block', fontSize: '13px', fontWeight: 500, marginBottom: '8px' }}>{t('agent.settings.perm.defaultAccess', '默认访问级别')}</label>
                                <div style={{ display: 'flex', gap: '8px' }}>
                                    {[{ val: 'use', label: '👁️ ' + t('agent.settings.perm.useAccess', '使用'), desc: t('agent.settings.perm.useAccessDesc', '任务、聊天、工具、技能、工作空间') }, { val: 'manage', label: '⚙️ ' + t('agent.settings.perm.manageAccess', '管理'), desc: t('agent.settings.perm.manageAccessDesc', '完全访问，包括设置') }].map(opt => (
                                        <label key={opt.val} style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', cursor: 'pointer', border: currentAccessLevel === opt.val ? '1px solid var(--accent-primary)' : '1px solid var(--border-subtle)', background: currentAccessLevel === opt.val ? 'rgba(99,102,241,0.06)' : 'transparent', transition: 'all 0.15s' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                <input type="radio" name="access_level" checked={currentAccessLevel === opt.val} onChange={() => handleAccessLevelChange(opt.val)} style={{ accentColor: 'var(--accent-primary)' }} />
                                                <span style={{ fontWeight: 500, fontSize: '13px' }}>{opt.label}</span>
                                            </div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px', marginLeft: '20px' }}>{opt.desc}</div>
                                        </label>
                                    ))}
                                </div>
                            </div>
                        )}
                        {!isOwner && <div style={{ marginTop: '12px', fontSize: '11px', color: 'var(--text-tertiary)', fontStyle: 'italic' }}>{t('agent.settings.perm.readOnly', '仅创建者或管理员可更改权限')}</div>}
                    </div>
                );
            })()}

            {/* Timezone */}
            <div className="card" style={{ marginBottom: '12px' }}>
                <h4 style={{ marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '8px' }}>{t('agent.settings.timezone.title', '🌐 时区')}</h4>
                <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '16px' }}>{t('agent.settings.timezone.description', '用于调度和时间感知的时区。')}</p>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                    <div>
                        <div style={{ fontWeight: 500, fontSize: '13px' }}>{t('agent.settings.timezone.current', '员工时区')}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{agent?.timezone ? t('agent.settings.timezone.override', '自定义时区') : t('agent.settings.timezone.inherited', '使用公司默认')}</div>
                    </div>
                    <select className="input" disabled={!canManage} value={agent?.timezone || ''} onChange={async (e) => { if (!canManage) return; const val = e.target.value || null; await agentApi.update(agentId, { timezone: val } as any); queryClient.invalidateQueries({ queryKey: ['agent', agentId] }); }} style={{ width: '200px', fontSize: '12px', opacity: canManage ? 1 : 0.6 }}>
                        <option value="">{t('agent.settings.timezone.default', '↩ 使用公司默认')}</option>
                        {[
    { value: 'UTC', label: 'UTC (协调世界时)' },
    { value: 'Asia/Shanghai', label: 'Asia/Shanghai (UTC+8) 中国标准时间' },
    { value: 'Asia/Tokyo', label: 'Asia/Tokyo (UTC+9) 日本标准时间' },
    { value: 'Asia/Seoul', label: 'Asia/Seoul (UTC+9) 韩国标准时间' },
    { value: 'Asia/Singapore', label: 'Asia/Singapore (UTC+8) 新加坡时间' },
    { value: 'Asia/Kolkata', label: 'Asia/Kolkata (UTC+5:30) 印度标准时间' },
    { value: 'Asia/Dubai', label: 'Asia/Dubai (UTC+4) 阿联酋时间' },
    { value: 'Europe/London', label: 'Europe/London (UTC+0/+1) 格林威治时间' },
    { value: 'Europe/Paris', label: 'Europe/Paris (UTC+1/+2) 欧洲中部时间' },
    { value: 'Europe/Berlin', label: 'Europe/Berlin (UTC+1/+2) 德国时间' },
    { value: 'Europe/Moscow', label: 'Europe/Moscow (UTC+3) 莫斯科时间' },
    { value: 'America/New_York', label: 'America/New_York (UTC-5/-4) 美东时间' },
    { value: 'America/Chicago', label: 'America/Chicago (UTC-6/-5) 美中时间' },
    { value: 'America/Denver', label: 'America/Denver (UTC-7/-6) 美山时间' },
    { value: 'America/Los_Angeles', label: 'America/Los_Angeles (UTC-8/-7) 美西时间' },
    { value: 'America/Sao_Paulo', label: 'America/Sao_Paulo (UTC-3) 巴西时间' },
    { value: 'Australia/Sydney', label: 'Australia/Sydney (UTC+10/+11) 悉尼时间' },
    { value: 'Pacific/Auckland', label: 'Pacific/Auckland (UTC+12/+13) 奥克兰时间' },
].map(tz => (<option key={tz.value} value={tz.value}>{tz.label}</option>))}
                    </select>
                </div>
            </div>

            {/* Heartbeat */}
            <div className="card" style={{ marginBottom: '12px' }}>
                <h4 style={{ marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '8px' }}>{t('agent.settings.heartbeat.title', '心跳检测')}</h4>
                <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '16px' }}>{t('agent.settings.heartbeat.description', '定期状态感知检查。')}</p>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                        <div>
                            <div style={{ fontWeight: 500, fontSize: '13px' }}>{t('agent.settings.heartbeat.enabled', '启用心跳')}</div>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('agent.settings.heartbeat.enabledDesc', '员工将定期检查自身状态')}</div>
                        </div>
                        <label style={{ position: 'relative', display: 'inline-block', width: '44px', height: '24px', cursor: canManage ? 'pointer' : 'default' }}>
                            <input type="checkbox" checked={agent?.heartbeat_enabled ?? true} disabled={!canManage} onChange={async (e) => { if (!canManage) return; await agentApi.update(agentId, { heartbeat_enabled: e.target.checked } as any); queryClient.invalidateQueries({ queryKey: ['agent', agentId] }); }} style={{ opacity: 0, width: 0, height: 0 }} />
                            <span style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, background: (agent?.heartbeat_enabled ?? true) ? 'var(--accent-primary)' : 'var(--bg-tertiary)', borderRadius: '12px', transition: 'background 0.2s', opacity: canManage ? 1 : 0.6 }}>
                                <span style={{ position: 'absolute', top: '3px', left: (agent?.heartbeat_enabled ?? true) ? '23px' : '3px', width: '18px', height: '18px', background: 'white', borderRadius: '50%', transition: 'left 0.2s' }} />
                            </span>
                        </label>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                        <div>
                            <div style={{ fontWeight: 500, fontSize: '13px' }}>{t('agent.settings.heartbeat.interval', '检查间隔')}</div>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('agent.settings.heartbeat.intervalDesc', '员工检查状态的频率')}</div>
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <input type="number" className="input" disabled={!canManage} min={1} defaultValue={agent?.heartbeat_interval_minutes ?? 120} key={agent?.heartbeat_interval_minutes} onBlur={async (e) => { if (!canManage) return; const val = Math.max(1, Number(e.target.value) || 120); e.target.value = String(val); await agentApi.update(agentId, { heartbeat_interval_minutes: val } as any); queryClient.invalidateQueries({ queryKey: ['agent', agentId] }); }} style={{ width: '80px', fontSize: '12px', opacity: canManage ? 1 : 0.6 }} />
                            <span style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>{t('common.minutes', 'min')}</span>
                        </div>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                        <div>
                            <div style={{ fontWeight: 500, fontSize: '13px' }}>{t('agent.settings.heartbeat.activeHours', '活跃时段')}</div>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('agent.settings.heartbeat.activeHoursDesc', '仅在此时间段内触发')}</div>
                        </div>
                        <input className="input" disabled={!canManage} value={agent?.heartbeat_active_hours ?? '09:00-18:00'} onChange={async (e) => { if (!canManage) return; await agentApi.update(agentId, { heartbeat_active_hours: e.target.value } as any); queryClient.invalidateQueries({ queryKey: ['agent', agentId] }); }} style={{ width: '140px', fontSize: '12px', textAlign: 'center', opacity: canManage ? 1 : 0.6 }} placeholder="09:00-18:00" />
                    </div>
                    {agent?.last_heartbeat_at && (
                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', paddingLeft: '4px' }}>{t('agent.settings.heartbeat.lastRun', '上次心跳')}: {new Date(agent.last_heartbeat_at).toLocaleString()}</div>
                    )}
                </div>
            </div>

            {/* Channel Config */}
            <div style={{ marginBottom: "12px" }}>
                <ChannelConfig mode="edit" agentId={agentId} />
            </div>

            {/* Danger Zone */}
            <div className="card" style={{ borderColor: 'var(--error)' }}>
                <h4 style={{ color: 'var(--error)', marginBottom: '12px' }}>{t('agent.settings.danger.title')}</h4>
                <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '12px' }}>{t('agent.settings.danger.deleteWarning')}</p>
                {!showDeleteConfirm ? (
                    <button className="btn btn-danger" onClick={() => setShowDeleteConfirm(true)}>× {t('agent.settings.danger.deleteAgent')}</button>
                ) : (
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                        <span style={{ fontSize: '13px', color: 'var(--error)', fontWeight: 600 }}>{t('agent.settings.danger.deleteWarning')}</span>
                        <button className="btn btn-danger" onClick={async () => {
                            try { await agentApi.delete(agentId); queryClient.invalidateQueries({ queryKey: ['agents'] }); navigate('/'); } catch (err: any) { useToastStore.getState().showToast(err?.message || '删除员工失败', 'error'); }
                        }}>{t('agent.settings.danger.confirmDelete')}</button>
                        <button className="btn btn-secondary" onClick={() => setShowDeleteConfirm(false)}>{t('common.cancel')}</button>
                    </div>
                )}
            </div>
        </div>
    );
}
