import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchJson, toolApi } from '../../services/api';
import PromptModal from '../../components/PromptModal';
import ModelPoolProviders from '../ModelPoolProviders';
import BrainConfig from '../BrainConfig';
import UserManagement from '../UserManagement';
import InvitationCodes from '../InvitationCodes';
import { useToastStore } from '../../stores/toastStore';
import { request } from '../../services/apiBase';

// Sub-components extracted into separate files
import OrgTab from './OrgTab';
import KnowledgeTab from './KnowledgeTab';
import AuditLogTab from './AuditLogTab';
import ApprovalsTab from './ApprovalsTab';
import SkillsTab from './SkillsTab';
import { ThemeColorPicker, CreditOverview, CompanyLogoUploader, CompanyNameEditor, CompanyTimezoneEditor, BroadcastSection, WindowsAutomationNodes, PRESET_MODELS } from './InfoTabComponents';

// ─── Identity Providers Tab ──────────────────────────

export default function EnterpriseSettings() {
    const { t } = useTranslation();
    const qc = useQueryClient();
    const [activeTab, setActiveTab] = useState<'info' | 'llm' | 'brain' | 'knowledge' | 'tools' | 'skills' | 'users' | 'org' | 'invites' | 'quotas' | 'approvals' | 'audit'>('info');

    // Track selected tenant as state so page refreshes on company switch
    const [selectedTenantId, setSelectedTenantId] = useState(localStorage.getItem('current_tenant_id') || '');
    useEffect(() => {
        const handler = (e: StorageEvent) => {
            if (e.key === 'current_tenant_id') {
                setSelectedTenantId(e.newValue || '');
            }
        };
        window.addEventListener('storage', handler);
        return () => window.removeEventListener('storage', handler);
    }, []);

    // Tenant quota defaults
    const [quotaForm, setQuotaForm] = useState({
        default_message_limit: 50, default_message_period: 'permanent',
        default_max_agents: 2, default_agent_ttl_hours: 48,
        default_max_llm_calls_per_day: 100, min_heartbeat_interval_minutes: 120,
        default_max_triggers: 20, min_poll_interval_floor: 5, max_webhook_rate_ceiling: 5,
    });
    const [quotaSaving, setQuotaSaving] = useState(false);
    const [quotaSaved, setQuotaSaved] = useState(false);
    useEffect(() => {
        if (activeTab === 'quotas') {
            fetchJson<any>('/enterprise/tenant-quotas').then(d => {
                if (d && Object.keys(d).length) setQuotaForm(f => ({ ...f, ...d }));
            }).catch(() => { });
        }
    }, [activeTab]);
    const saveQuotas = async () => {
        setQuotaSaving(true);
        try {
            await fetchJson('/enterprise/tenant-quotas', { method: 'PATCH', body: JSON.stringify(quotaForm) });
            setQuotaSaved(true); setTimeout(() => setQuotaSaved(false), 2000);
        } catch (e) { useToastStore.getState().showToast('Failed to save', 'error'); }
        setQuotaSaving(false);
    };
    const [companyIntro, setCompanyIntro] = useState('');
    const [companyIntroSaving, setCompanyIntroSaving] = useState(false);
    const [companyIntroSaved, setCompanyIntroSaved] = useState(false);
    const [companyIntroMode, setCompanyIntroMode] = useState<'edit' | 'preview'>('edit');

    // Company intro key: always per-tenant scoped
    const companyIntroKey = selectedTenantId ? `company_intro_${selectedTenantId}` : 'company_intro';

    // Load Company Intro (tenant-scoped only, no fallback to global)
    useEffect(() => {
        setCompanyIntro('');
        if (!selectedTenantId) return;
        fetchJson<any>(`/enterprise/system-settings/company_intro/${selectedTenantId}`)
            .then(d => {
                if (d?.value?.content) {
                    setCompanyIntro(d.value.content);
                }
                // No fallback — each company starts empty with placeholder watermark
            })
            .catch(() => { });
    }, [selectedTenantId]);

    const saveCompanyIntro = async () => {
        setCompanyIntroSaving(true);
        try {
            await fetchJson(`/enterprise/system-settings/company_intro/${selectedTenantId}`, {
                method: 'PUT', body: JSON.stringify({ value: { content: companyIntro } }),
            });
            setCompanyIntroSaved(true);
            setTimeout(() => setCompanyIntroSaved(false), 2000);
        } catch (e) { }
        setCompanyIntroSaving(false);
    };
    const [auditFilter, setAuditFilter] = useState<'all' | 'background' | 'actions'>('all');
    const [kbToast, setKbToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);
    const showKbToast = (message: string, type: 'success' | 'error' = 'success') => {
        setKbToast({ message, type });
        setTimeout(() => setKbToast(null), 3000);
    };

    const [allTools, setAllTools] = useState<any[]>([]);
    const [showAddMCP, setShowAddMCP] = useState(false);
    const [mcpForm, setMcpForm] = useState({ server_url: '', server_name: '' });
    const [mcpRawInput, setMcpRawInput] = useState('');
    const [mcpTestResult, setMcpTestResult] = useState<any>(null);
    const [mcpTesting, setMcpTesting] = useState(false);
    const [editingToolId, setEditingToolId] = useState<string | null>(null);
    const [editingConfig, setEditingConfig] = useState<Record<string, any>>({});
    const [configCategory, setConfigCategory] = useState<string | null>(null);

    // Category-level config schemas: tools sharing the same key have config on category header
    const GLOBAL_CATEGORY_CONFIG_SCHEMAS: Record<string, { title: string; fields: any[] }> = {
        agentbay: {
            title: 'AgentBay Settings',
            fields: [
                { key: 'api_key', label: 'API Key (from AgentBay)', type: 'password', placeholder: 'Enter your AgentBay API key' },
                { key: 'os_type', label: 'Cloud Computer OS', type: 'select', default: 'windows', options: [{ value: 'linux', label: 'Linux' }, { value: 'windows', label: 'Windows' }] },
            ],
        },
    };

    // Labels for tool categories (mirrors AgentDetail getCategoryLabels)
    const categoryLabels: Record<string, string> = {
        file: t('agent.toolCategories.file'),
        task: t('agent.toolCategories.task'),
        communication: t('agent.toolCategories.communication'),
        search: t('agent.toolCategories.search'),
        aware: t('agent.toolCategories.aware', '感知与触发'),
        social: t('agent.toolCategories.social', '社交'),
        code: t('agent.toolCategories.code', '代码与执行'),
        discovery: t('agent.toolCategories.discovery', '发现'),
        email: t('agent.toolCategories.email', '邮件'),
        feishu: t('agent.toolCategories.feishu', '飞书 / Lark'),
        custom: t('agent.toolCategories.custom'),
        general: t('agent.toolCategories.general'),
        agentbay: t('agent.toolCategories.agentbay', 'AgentBay'),
    };
    const [toolsView, setToolsView] = useState<'global' | 'agent-installed'>('global');
    const [toolDeptFilter, setToolDeptFilter] = useState<string>('all');
    const [deptTools, setDeptTools] = useState<any[] | null>(null);
    const [loadingDeptTools, setLoadingDeptTools] = useState(false);
    const [agentInstalledTools, setAgentInstalledTools] = useState<any[]>([]);
    const loadAllTools = async () => {
        const tid = selectedTenantId;
        const data = await fetchJson<any[]>(`/tools${tid ? `?tenant_id=${tid}` : ''}`);
        setAllTools(data);
    };
    const loadAgentInstalledTools = async () => {
        try {
            const tid = selectedTenantId;
            const data = await fetchJson<any[]>(`/tools/agent-installed${tid ? `?tenant_id=${tid}` : ''}`);
            setAgentInstalledTools(data);
        } catch { }
    };
    useEffect(() => { if (activeTab === 'tools') { loadAllTools(); loadAgentInstalledTools(); } }, [activeTab, selectedTenantId]);

    // ─── Jina API Key
    const [jinaKey, setJinaKey] = useState('');
    const [jinaKeySaved, setJinaKeySaved] = useState(false);
    const [jinaKeySaving, setJinaKeySaving] = useState(false);
    const [jinaKeyMasked, setJinaKeyMasked] = useState('');  // stored key from DB
    useEffect(() => {
        if (activeTab !== 'tools') return;
        request<any>('/enterprise/system-settings/jina_api_key')
            .then(d => { if (d.value?.api_key) setJinaKeyMasked(d.value.api_key.slice(0, 8) + '••••••••'); })
            .catch(() => { });
    }, [activeTab]);
    const saveJinaKey = async () => {
        setJinaKeySaving(true);
        await request('/enterprise/system-settings/jina_api_key', {
            method: 'PUT',
            body: JSON.stringify({ value: { api_key: jinaKey } }),
        });
        setJinaKeyMasked(jinaKey.slice(0, 8) + '••••••••');
        setJinaKey('');
        setJinaKeySaving(false);
        setJinaKeySaved(true);
        setTimeout(() => setJinaKeySaved(false), 2000);
    };
    const clearJinaKey = async () => {
        await request('/enterprise/system-settings/jina_api_key', {
            method: 'PUT',
            body: JSON.stringify({ value: {} }),
        });
        setJinaKeyMasked('');
        setJinaKey('');
    };


    const { data: currentTenant } = useQuery({
        queryKey: ['tenant', selectedTenantId],
        queryFn: () => fetchJson<any>(`/tenants/${selectedTenantId}`),
        enabled: !!selectedTenantId,
    });

    // ─── Stats (scoped to selected tenant)
    const { data: stats } = useQuery({
        queryKey: ['chairman-dashboard', selectedTenantId],
        queryFn: () => fetchJson<any>(`/chairman/dashboard`),
    });

    // ─── Approvals
    const { data: approvals = [] } = useQuery({
        queryKey: ['approvals', selectedTenantId],
        queryFn: () => fetchJson<any[]>(`/enterprise/approvals${selectedTenantId ? `?tenant_id=${selectedTenantId}` : ''}`),
        enabled: activeTab === 'approvals',
    });
    const resolveApproval = useMutation({
        mutationFn: ({ id, action }: { id: string; action: string }) =>
            fetchJson(`/enterprise/approvals/${id}/resolve`, { method: 'POST', body: JSON.stringify({ action }) }),
        onSuccess: () => qc.invalidateQueries({ queryKey: ['approvals', selectedTenantId] }),
    });

    // ─── Audit Logs
    const BG_ACTIONS = ['supervision_tick', 'supervision_fire', 'supervision_error', 'schedule_tick', 'schedule_fire', 'schedule_error', 'heartbeat_tick', 'heartbeat_fire', 'heartbeat_error', 'server_startup'];
    const { data: auditLogs = [] } = useQuery({
        queryKey: ['audit-logs', selectedTenantId],
        queryFn: () => fetchJson<any[]>(`/enterprise/audit-logs?limit=200${selectedTenantId ? `&tenant_id=${selectedTenantId}` : ''}`),
        enabled: activeTab === 'audit',
    });
    const filteredAuditLogs = auditLogs.filter((log: any) => {
        if (auditFilter === 'background') return BG_ACTIONS.includes(log.action);
        if (auditFilter === 'actions') return !BG_ACTIONS.includes(log.action);
        return true;
    });

    return (
        <>
            <div>
                <div className="page-header">
                    <div>
                        <h1 className="page-title">{t('nav.enterprise')}</h1>
                        {stats && (
                            <div style={{ display: 'flex', gap: '24px', marginTop: '8px' }}>
                                <span className="badge badge-info">{t('enterprise.stats.users', { count: stats.total_users })}</span>
                                <span className="badge badge-success">{t('enterprise.stats.runningAgents', { running: stats.running_agents, total: stats.total_agents })}</span>
                                {stats.pending_approvals > 0 && <span className="badge badge-warning">{stats.pending_approvals} {t('enterprise.tabs.approvals')}</span>}
                            </div>
                        )}
                    </div>
                </div>

                <div className="tabs">
                    {(['info', 'llm', 'brain', 'knowledge', 'tools', 'skills', 'users', 'org', 'invites', 'quotas', 'approvals', 'audit'] as const).map(tab => (
                        <div key={tab} className={`tab ${activeTab === tab ? 'active' : ''}`} onClick={() => setActiveTab(tab)}>
                            {tab === 'quotas' ? t('enterprise.tabs.quotas', '配额') : tab === 'users' ? t('enterprise.tabs.users', '用户') : tab === 'invites' ? t('enterprise.tabs.invites', '邀请') : tab === 'brain' ? '大脑配置' : tab === 'knowledge' ? t('enterprise.tabs.knowledge', '知识库') : t(`enterprise.tabs.${tab}`)}
                        </div>
                    ))}
                </div>

                {/* ── LLM Model Pool ── */}
                {/*  LLM Model Pool  */}
                {activeTab === 'llm' && <ModelPoolProviders />}

                {/* ── Brain Config ── */}
                {activeTab === 'brain' && <BrainConfig />}

                {/* ── Knowledge Base ── */}
                {activeTab === 'knowledge' && <KnowledgeTab />}

                {/* ── Org Structure ── */}
                {activeTab === 'org' && (
                    <OrgTab tenant={currentTenant} />
                )}

                {/* ── Approvals ── */}
                {activeTab === 'approvals' && (
                    <ApprovalsTab approvals={approvals} resolveApproval={resolveApproval} />
                )}

                {/* ── Audit Logs ── */}
                {activeTab === 'audit' && (
                    <AuditLogTab auditLogs={auditLogs} auditFilter={auditFilter} setAuditFilter={setAuditFilter} BG_ACTIONS={BG_ACTIONS} />
                )}

                {/* ── Company Management ── */}
                {activeTab === 'info' && (
                    <div>

                        {/* ── Logo Upload ── */}
                        <CompanyLogoUploader key={`logo-${selectedTenantId}`} />

                        {/* ── 0. Company Name ── */}
                        <h3 style={{ marginBottom: '8px' }}>{t('enterprise.companyName.title', '公司名称')}</h3>
                        <CompanyNameEditor key={`name-${selectedTenantId}`} />

                        {/* ── 0.5. Company Timezone ── */}
                        <CompanyTimezoneEditor key={`tz-${selectedTenantId}`} />

                        {/* ── 1. Company Intro ── */}
                        <h3 style={{ marginBottom: '8px' }}>{t('enterprise.companyIntro.title', '公司简介')}</h3>
                        <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                            {t('enterprise.companyIntro.description', '描述公司的使命、产品和文化。此内容将作为上下文包含在每个员工的对话中。')}
                        </p>
                        <div className="card" style={{ padding: '16px', marginBottom: '24px' }}>
                            {/* Edit/Preview toggle */}
                            <div style={{ display: 'flex', gap: '4px', marginBottom: '10px' }}>
                                {(['edit', 'preview'] as const).map(mode => (
                                    <button key={mode} onClick={() => setCompanyIntroMode(mode)} style={{
                                        padding: '3px 12px', borderRadius: '6px', fontSize: '11px', fontWeight: 500, cursor: 'pointer', border: 'none',
                                        background: companyIntroMode === mode ? 'var(--accent-primary)' : 'var(--bg-tertiary)',
                                        color: companyIntroMode === mode ? '#fff' : 'var(--text-secondary)', transition: 'all 0.15s',
                                    }}>{mode === 'edit' ? t('enterprise.companyIntro.edit', '编辑') : t('enterprise.companyIntro.preview', '预览')}</button>
                                ))}
                            </div>
                            {companyIntroMode === 'edit' ? (
                                <textarea
                                    className="form-input"
                                    value={companyIntro}
                                    onChange={e => setCompanyIntro(e.target.value)}
                                    placeholder="# Company Name&#10;生命智能体自治系统"
                                    style={{
                                        minHeight: '200px', resize: 'vertical',
                                        fontFamily: 'var(--font-mono)', fontSize: '13px',
                                        lineHeight: '1.6', whiteSpace: 'pre-wrap',
                                    }}
                                />
                            ) : (
                                <div style={{
                                    minHeight: '200px', padding: '12px', borderRadius: '8px',
                                    background: 'var(--bg-tertiary)', fontSize: '13px', lineHeight: '1.7',
                                    whiteSpace: 'pre-wrap', overflow: 'auto',
                                }} dangerouslySetInnerHTML={{
                                    __html: (companyIntro || `*${t('enterprise.companyIntro.empty', '暂无简介内容')}*`)
                                        .replace(/^### (.+)$/gm, '<h4 style="margin:12px 0 4px;font-size:14px;font-weight:600">$1</h4>')
                                        .replace(/^## (.+)$/gm, '<h3 style="margin:14px 0 6px;font-size:15px;font-weight:600">$1</h3>')
                                        .replace(/^# (.+)$/gm, '<h2 style="margin:16px 0 8px;font-size:17px;font-weight:700">$1</h2>')
                                        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
                                        .replace(/\*(.+?)\*/g, '<em>$1</em>')
                                        .replace(/`(.+?)`/g, '<code style="padding:1px 4px;border-radius:3px;background:var(--bg-secondary);font-size:12px">$1</code>')
                                        .replace(/^- (.+)$/gm, '<li style="margin-left:16px">$1</li>')
                                        .replace(/\n/g, '<br/>')
                                }} />
                            )}
                            <div style={{ marginTop: '12px', display: 'flex', gap: '8px', alignItems: 'center' }}>
                                <button className="btn btn-primary" onClick={saveCompanyIntro} disabled={companyIntroSaving}>
                                    {companyIntroSaving ? t('common.loading') : t('common.save', '保存')}
                                </button>
                                {companyIntroSaved && <span style={{ color: 'var(--success)', fontSize: '12px' }}>✅ {t('enterprise.config.saved', '已保存')}</span>}
                                <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginLeft: 'auto' }}>
                                    💡 {t('enterprise.companyIntro.hint', '此内容将出现在每个员工的系统提示中')}
                                </span>
                            </div>
                        </div>

                        {/* ── Theme Color ── */}
                        <ThemeColorPicker />

                        {/* ── Broadcast ── */}
                        <BroadcastSection />

                        {/* ── Danger Zone: Delete Company ── */}
                        <div style={{ marginTop: '32px', padding: '16px', border: '1px solid var(--status-error, #e53e3e)', borderRadius: '8px' }}>
                            <h3 style={{ marginBottom: '4px', color: 'var(--status-error, #e53e3e)' }}>{t('enterprise.dangerZone', '危险区域')}</h3>
                            <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                                {t('enterprise.deleteCompanyDesc', '永久删除此公司及其所有数据，包括员工、模型、工具和技能。此操作不可撤销。')}
                            </p>
                            <button
                                className="btn"
                                onClick={async () => {
                                    const name = document.querySelector<HTMLInputElement>('.company-name-input')?.value || selectedTenantId;
                                    if (!confirm(t('enterprise.deleteCompanyConfirm', '确定要删除此公司及其所有数据吗？此操作不可撤销。'))) return;
                                    try {
                                        const res = await fetchJson<any>(`/tenants/${selectedTenantId}`, { method: 'DELETE' });
                                        // Switch to fallback tenant
                                        const fallbackId = res.fallback_tenant_id;
                                        localStorage.setItem('current_tenant_id', fallbackId);
                                        setSelectedTenantId(fallbackId);
                                        window.dispatchEvent(new StorageEvent('storage', { key: 'current_tenant_id', newValue: fallbackId }));
                                        qc.invalidateQueries({ queryKey: ['tenants'] });
                                    } catch (e: any) {
                                        useToastStore.getState().showToast(e.message || 'Delete failed', 'error');
                                    }
                                }}
                                style={{
                                    background: 'transparent', color: 'var(--status-error, #e53e3e)',
                                    border: '1px solid var(--status-error, #e53e3e)', borderRadius: '6px',
                                    padding: '6px 16px', fontSize: '13px', cursor: 'pointer',
                                }}
                            >
                                {t('enterprise.deleteCompany', '删除此公司')}
                            </button>
                        </div>
                    </div>
                )}

                {/* ── Quotas Tab ── */}
                {activeTab === 'quotas' && (
                    <div>
                        <h3 style={{ marginBottom: '4px' }}>{t('enterprise.quotas.defaultUserQuotas')}</h3>
                        <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '16px' }}>
                            {t('enterprise.quotas.defaultsApply')}
                        </p>
                        <div className="card" style={{ padding: '16px' }}>
                            {/* ── Conversation Limits ── */}
                            <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '10px' }}>{t('enterprise.quotas.conversationLimits')}</div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '20px' }}>
                                <div className="form-group">
                                    <label className="form-label">{t('enterprise.quotas.messageLimit')}</label>
                                    <input className="form-input" type="number" min={0} value={quotaForm.default_message_limit}
                                        onChange={e => setQuotaForm({ ...quotaForm, default_message_limit: Number(e.target.value) })} />
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('enterprise.quotas.maxMessagesPerPeriod')}</div>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('enterprise.quotas.messagePeriod')}</label>
                                    <select className="form-input" value={quotaForm.default_message_period}
                                        onChange={e => setQuotaForm({ ...quotaForm, default_message_period: e.target.value })}>
                                        <option value="permanent">{t('enterprise.quotas.permanent')}</option>
                                        <option value="daily">{t('enterprise.quotas.daily')}</option>
                                        <option value="weekly">{t('enterprise.quotas.weekly')}</option>
                                        <option value="monthly">{t('enterprise.quotas.monthly')}</option>
                                    </select>
                                </div>
                            </div>

                            {/* ── Agent Limits ── */}
                            <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '10px' }}>{t('enterprise.quotas.agentLimits')}</div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '16px', marginBottom: '20px' }}>
                                <div className="form-group">
                                    <label className="form-label">{t('enterprise.quotas.maxAgents')}</label>
                                    <input className="form-input" type="number" min={0} value={quotaForm.default_max_agents}
                                        onChange={e => setQuotaForm({ ...quotaForm, default_max_agents: Number(e.target.value) })} />
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('enterprise.quotas.agentsUserCanCreate')}</div>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('enterprise.quotas.agentTTL')}</label>
                                    <input className="form-input" type="number" min={1} value={quotaForm.default_agent_ttl_hours}
                                        onChange={e => setQuotaForm({ ...quotaForm, default_agent_ttl_hours: Number(e.target.value) })} />
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('enterprise.quotas.agentAutoExpiry')}</div>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('enterprise.quotas.dailyLLMCalls')}</label>
                                    <input className="form-input" type="number" min={0} value={quotaForm.default_max_llm_calls_per_day}
                                        onChange={e => setQuotaForm({ ...quotaForm, default_max_llm_calls_per_day: Number(e.target.value) })} />
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('enterprise.quotas.maxLLMCallsPerDay')}</div>
                                </div>
                            </div>

                            {/* ── System Limits ── */}
                            <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '10px' }}>{t('enterprise.quotas.system')}</div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '16px' }}>
                                <div className="form-group">
                                    <label className="form-label">{t('enterprise.quotas.minHeartbeatInterval')}</label>
                                    <input className="form-input" type="number" min={1} value={quotaForm.min_heartbeat_interval_minutes}
                                        onChange={e => setQuotaForm({ ...quotaForm, min_heartbeat_interval_minutes: Number(e.target.value) })} />
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>{t('enterprise.quotas.minHeartbeatDesc')}</div>
                                </div>
                            </div>

                            {/* ── Trigger Limits ── */}
                            <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '10px', marginTop: '20px' }}>{t('enterprise.quotas.triggerLimits')}</div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '16px', marginBottom: '20px' }}>
                                <div className="form-group">
                                    <label className="form-label">{t('enterprise.quotas.defaultMaxTriggers', '默认最大触发次数')}</label>
                                    <input className="form-input" type="number" min={1} max={100} value={quotaForm.default_max_triggers}
                                        onChange={e => setQuotaForm({ ...quotaForm, default_max_triggers: Number(e.target.value) })} />
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                        {t('enterprise.quotas.defaultMaxTriggersDesc', '新员工的默认触发次数限制')}
                                    </div>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('enterprise.quotas.minPollInterval', '最小轮询间隔（分钟）')}</label>
                                    <input className="form-input" type="number" min={1} max={60} value={quotaForm.min_poll_interval_floor}
                                        onChange={e => setQuotaForm({ ...quotaForm, min_poll_interval_floor: Number(e.target.value) })} />
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                        {t('enterprise.quotas.minPollIntervalDesc', '公司级下限：员工轮询频率不得低于此值')}
                                    </div>
                                </div>
                                <div className="form-group">
                                    <label className="form-label">{t('enterprise.quotas.maxWebhookRate', '最大 Webhook 频率（次/分钟）')}</label>
                                    <input className="form-input" type="number" min={1} max={60} value={quotaForm.max_webhook_rate_ceiling}
                                        onChange={e => setQuotaForm({ ...quotaForm, max_webhook_rate_ceiling: Number(e.target.value) })} />
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                        {t('enterprise.quotas.maxWebhookRateDesc', '公司级上限：每个员工每分钟最大 Webhook 调用次数')}
                                    </div>
                                </div>
                            </div>
                            <div style={{ marginTop: '16px', display: 'flex', gap: '8px', alignItems: 'center' }}>
                                <button className="btn btn-primary" onClick={saveQuotas} disabled={quotaSaving}>
                                    {quotaSaving ? t('common.loading') : t('common.save', 'Save')}
                                </button>
                                {quotaSaved && <span style={{ color: 'var(--success)', fontSize: '12px' }}>✅ Saved</span>}
                            </div>
                        </div>

                        {/* ── Credit & Resource Overview ── */}
                        <h3 style={{ marginTop: '24px', marginBottom: '4px' }}>{t('enterprise.quotas.creditOverview', '积分与资源概览')}</h3>
                        <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '16px' }}>
                            {t('enterprise.quotas.creditOverviewDesc', '查看企业积分余额和资源使用情况。')}
                        </p>
                        <CreditOverview />
                    </div>
                )}

                {/* ── Users Tab ── */}
                {activeTab === 'users' && (
                    <UserManagement key={selectedTenantId} />
                )}


                {/* ── Tools Tab ── */}
                {activeTab === 'tools' && (
                    <div>
                        {/* Search bar + dept filter */}
                        <div style={{ marginBottom: '12px', display: 'flex', gap: '8px', alignItems: 'center' }}>
                            <input className="form-input" placeholder={t('enterprise.tools.searchPlaceholder', '搜索工具...')} id="tool-search-input"
                                style={{ fontSize: '13px', flex: 1, maxWidth: '400px' }} onInput={(e) => {
                                    const q = (e.target as HTMLInputElement).value.toLowerCase();
                                    document.querySelectorAll('[data-tool-name]').forEach((el) => {
                                        const name = el.getAttribute('data-tool-name')?.toLowerCase() || '';
                                        (el as HTMLElement).style.display = !q || name.includes(q) ? '' : 'none';
                                    });
                                }} />
                            <select value={toolDeptFilter} onChange={async e => {
                                const dept = e.target.value;
                                setToolDeptFilter(dept);
                                if (dept !== 'all') {
                                    setLoadingDeptTools(true);
                                    try {
                                        const data = await toolApi.getByDepartment(dept);
                                        setDeptTools(data);
                                    } catch { setDeptTools([]); }
                                    setLoadingDeptTools(false);
                                } else {
                                    setDeptTools(null);
                                }
                            }} style={{
                                fontSize: '11px', padding: '4px 8px', borderRadius: '8px',
                                border: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)', color: 'var(--text-primary)',
                            }}>
                                <option value="all">{t('enterprise.tools.allDepartments', '所有部门')}</option>
                                {['tech', 'hr', 'finance', 'sales', 'cs', 'admin', 'legal', 'ops'].map(dept => (
                                    <option key={dept} value={dept}>{t(`enterprise.departments.${dept}`, dept)}</option>
                                ))}
                            </select>
                        </div>
                        {/* Sub-tab pills */}
                        <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '8px' }}>
                            {([['global', t('enterprise.tools.globalTools')], ['agent-installed', t('enterprise.tools.agentInstalled')]] as const).map(([key, label]) => (
                                <button key={key} onClick={() => { setToolsView(key as any); if (key === 'agent-installed') loadAgentInstalledTools(); }} style={{
                                    padding: '4px 14px', borderRadius: '12px', fontSize: '12px', fontWeight: 500, cursor: 'pointer', border: 'none',
                                    background: toolsView === key ? 'var(--accent-primary)' : 'var(--bg-tertiary)',
                                    color: toolsView === key ? '#fff' : 'var(--text-secondary)', transition: 'all 0.15s',
                                }}>{label}</button>
                            ))}
                        </div>

                        {/* Agent-Installed Tools */}
                        {toolsView === 'agent-installed' && (
                            <div>
                                <p style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>{t('enterprise.tools.agentInstalledHint')}</p>
                                {agentInstalledTools.length === 0 ? (
                                    <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>{t('enterprise.tools.noAgentInstalledTools')}</div>
                                ) : (
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                                        {agentInstalledTools.map((row: any) => (
                                            <div key={row.agent_tool_id} className="card" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 16px' }}>
                                                <div style={{ flex: 1, minWidth: 0 }}>
                                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                        <span style={{ fontWeight: 500, fontSize: '13px' }}>🔌 {row.tool_display_name}</span>
                                                        {row.mcp_server_name && <span style={{ fontSize: '10px', background: 'var(--primary)', color: '#fff', borderRadius: '4px', padding: '1px 5px' }}>MCP</span>}
                                                    </div>
                                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                                                        🤖 {row.installed_by_agent_name || 'Unknown Agent'}
                                                        {row.installed_at && <span> · {new Date(row.installed_at).toLocaleString()}</span>}
                                                    </div>
                                                </div>
                                                <button className="btn btn-ghost" style={{ color: 'var(--error)', fontSize: '12px' }} onClick={async () => {
                                                    if (!confirm(t('enterprise.tools.removeFromAgent', { name: row.tool_display_name }))) return;
                                                    try {
                                                        await fetchJson(`/tools/agent-tool/${row.agent_tool_id}`, { method: 'DELETE' });
                                                    } catch {
                                                        // Already deleted (e.g. removed via Global Tools) — just refresh
                                                    }
                                                    loadAgentInstalledTools();
                                                }}>🗑️ {t('enterprise.tools.delete')}</button>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        )}

                        {toolsView === 'global' && <>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                                <h3>{t('enterprise.tools.title')}</h3>
                                <button className="btn btn-primary" onClick={() => setShowAddMCP(true)}>+ {t('enterprise.tools.addMcpServer')}</button>
                            </div>

                            {showAddMCP && (
                                <div className="card" style={{ padding: '16px', marginBottom: '16px' }}>
                                    <h4 style={{ marginBottom: '12px' }}>{t('enterprise.tools.mcpServer')}</h4>
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                                        <div>
                                            <label style={{ display: 'block', fontSize: '12px', marginBottom: '4px' }}>{t('enterprise.tools.jsonConfig')}</label>
                                            <textarea className="form-input" value={mcpRawInput} onChange={e => {
                                                const val = e.target.value;
                                                setMcpRawInput(val);
                                                // Auto-parse JSON config format
                                                try {
                                                    const parsed = JSON.parse(val);
                                                    const servers = parsed.mcpServers || parsed;
                                                    const names = Object.keys(servers);
                                                    if (names.length > 0) {
                                                        const name = names[0];
                                                        const cfg = servers[name];
                                                        const url = cfg.url || cfg.uri || '';
                                                        setMcpForm({ server_name: name, server_url: url });
                                                    }
                                                } catch {
                                                    // Not JSON — treat as plain URL
                                                    setMcpForm(p => ({ ...p, server_url: val }));
                                                }
                                            }} placeholder={'{\n  "mcpServers": {\n    "server-name": {\n      "type": "sse",\n      "url": "https://mcp.example.com/sse"\n    }\n  }\n}\n\nor paste a URL directly'} style={{ minHeight: '120px', fontFamily: 'var(--font-mono)', fontSize: '12px', resize: 'vertical' }} />
                                        </div>
                                        {mcpForm.server_name && (
                                            <div style={{ display: 'flex', gap: '12px', fontSize: '12px', color: 'var(--text-secondary)', padding: '8px 12px', background: 'var(--bg-tertiary)', borderRadius: '6px' }}>
                                                <span>Name: <strong>{mcpForm.server_name}</strong></span>
                                                <span>URL: <strong>{mcpForm.server_url}</strong></span>
                                            </div>
                                        )}
                                        {!mcpForm.server_name && (
                                            <div>
                                                <label style={{ display: 'block', fontSize: '12px', marginBottom: '4px' }}>{t('enterprise.tools.mcpServerName')}</label>
                                                <input className="form-input" value={mcpForm.server_name} onChange={e => setMcpForm(p => ({ ...p, server_name: e.target.value }))} placeholder="My MCP Server" />
                                            </div>
                                        )}
                                        <div style={{ display: 'flex', gap: '8px' }}>
                                            <button className="btn btn-secondary" disabled={mcpTesting || !mcpForm.server_url} onClick={async () => {
                                                setMcpTesting(true); setMcpTestResult(null);
                                                try {
                                                    const r = await fetchJson<any>('/tools/test-mcp', { method: 'POST', body: JSON.stringify({ server_url: mcpForm.server_url }) });
                                                    setMcpTestResult(r);
                                                } catch (e: any) { setMcpTestResult({ ok: false, error: e.message }); }
                                                setMcpTesting(false);
                                            }}>{mcpTesting ? t('enterprise.tools.testing') : t('enterprise.tools.testConnection')}</button>
                                            <button className="btn btn-secondary" onClick={() => { setShowAddMCP(false); setMcpTestResult(null); setMcpForm({ server_url: '', server_name: '' }); setMcpRawInput(''); }}>{t('common.cancel')}</button>
                                        </div>
                                        {mcpTestResult && (
                                            <div className="card" style={{ padding: '12px', background: mcpTestResult.ok ? 'rgba(0,200,100,0.1)' : 'rgba(255,0,0,0.1)' }}>
                                                {mcpTestResult.ok ? (
                                                    <div>
                                                        <div style={{ color: 'var(--success)', fontWeight: 600, marginBottom: '8px' }}>{t('enterprise.tools.connectionSuccess', { count: mcpTestResult.tools?.length || 0 })}</div>
                                                        {(mcpTestResult.tools || []).map((tool: any, i: number) => (
                                                            <div key={i} data-tool-name={tool.name} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderBottom: '1px solid var(--border-color)' }}>
                                                                <div>
                                                                    <span style={{ fontWeight: 500, fontSize: '13px' }}>{tool.name}</span>
                                                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{tool.description?.slice(0, 80)}</div>
                                                                </div>
                                                                <button className="btn btn-secondary" style={{ padding: '4px 10px', fontSize: '11px' }} onClick={async () => {
                                                                    try {
                                                                        await fetchJson('/tools', {
                                                                            method: 'POST', body: JSON.stringify({
                                                                                name: `mcp_${tool.name}`,
                                                                                display_name: tool.name,
                                                                                description: tool.description || '',
                                                                                type: 'mcp',
                                                                                category: 'custom',
                                                                                icon: '·',
                                                                                mcp_server_url: mcpForm.server_url,
                                                                                mcp_server_name: mcpForm.server_name || mcpForm.server_url,
                                                                                mcp_tool_name: tool.name,
                                                                                parameters_schema: tool.inputSchema || {},
                                                                                is_default: false,
                                                                            })
                                                                        });
                                                                        loadAllTools();
                                                                    } catch (e: any) {
                                                                        useToastStore.getState().showToast(`${t('enterprise.tools.importFailed') || 'Import failed'}: ${e.message}`, 'error');
                                                                    }
                                                                }}>{t('enterprise.tools.import') || 'Import'}</button>
                                                            </div>
                                                        ))}
                                                        <div style={{ marginTop: '10px', display: 'flex', justifyContent: 'flex-end' }}>
                                                            <button className="btn btn-primary" style={{ padding: '6px 14px', fontSize: '12px' }} onClick={async () => {
                                                                const tools = mcpTestResult.tools || [];
                                                                let successCount = 0;
                                                                const errors: string[] = [];
                                                                for (const tool of tools) {
                                                                    try {
                                                                        await fetchJson('/tools', {
                                                                            method: 'POST', body: JSON.stringify({
                                                                                name: `mcp_${tool.name}`,
                                                                                display_name: tool.name,
                                                                                description: tool.description || '',
                                                                                type: 'mcp',
                                                                                category: 'custom',
                                                                                icon: '·',
                                                                                mcp_server_url: mcpForm.server_url,
                                                                                mcp_server_name: mcpForm.server_name || mcpForm.server_url,
                                                                                mcp_tool_name: tool.name,
                                                                                parameters_schema: tool.inputSchema || {},
                                                                                is_default: false,
                                                                            })
                                                                        });
                                                                        successCount++;
                                                                    } catch (e: any) {
                                                                        errors.push(`${tool.name}: ${e.message}`);
                                                                    }
                                                                }
                                                                loadAllTools();
                                                                setShowAddMCP(false); setMcpTestResult(null); setMcpForm({ server_url: '', server_name: '' }); setMcpRawInput('');
                                                                if (errors.length > 0) {
                                                                    useToastStore.getState().showToast(`Imported ${successCount}/${tools.length} tools. Failed: ${errors.join('; ')}`, errors.length > 0 ? 'error' : 'success');
                                                                }
                                                            }}>{t('enterprise.tools.importAll')}</button>
                                                        </div>
                                                    </div>
                                                ) : (
                                                    <div style={{ color: 'var(--danger)' }}>{t('enterprise.tools.connectionFailed')}: {mcpTestResult.error}</div>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            )}

                            {/* ─── Category-grouped tool list ─── */}
                            {(() => {
                                // Use department-filtered tools if a department is selected
                                const displayTools = toolDeptFilter !== 'all' && deptTools ? deptTools : allTools;
                                // Group tools by category (same pattern as AgentDetail.tsx)
                                const grouped = displayTools.reduce((acc: Record<string, any[]>, tool: any) => {
                                    const cat = tool.category || 'general';
                                    (acc[cat] = acc[cat] || []).push(tool);
                                    return acc;
                                }, {} as Record<string, any[]>);

                                if (loadingDeptTools) return <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>{t('common.loading')}</div>;
                                if (displayTools.length === 0) {
                                    return <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>{t('enterprise.tools.emptyState')}</div>;
                                }

                                return (
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                                        {Object.entries(grouped).map(([category, catTools]) => {
                                            const hasCategoryConfig = !!GLOBAL_CATEGORY_CONFIG_SCHEMAS[category];

                                            return (
                                                <div key={category}>
                                                    {/* Category header */}
                                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 14px', marginBottom: '8px' }}>
                                                        <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                                                            {categoryLabels[category] || category}
                                                        </div>
                                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                            {hasCategoryConfig && (
                                                                <button
                                                                    onClick={() => {
                                                                        setConfigCategory(category);
                                                                        setEditingConfig({});
                                                                        // Load existing global config from the first tool in this category that has a non-empty config.
                                                                        // Do NOT require config_schema — some categories (e.g. AgentBay)
                                                                        // define their schema only in frontend CATEGORY_CONFIG_SCHEMAS.
                                                                        const firstToolWithConfig = (catTools as any[]).find((tl: any) => tl.config && Object.keys(tl.config).length > 0);
                                                                        if (firstToolWithConfig?.config) {
                                                                            setEditingConfig({ ...firstToolWithConfig.config });
                                                                        }
                                                                    }}
                                                                    style={{ background: 'none', border: '1px solid var(--border-subtle)', borderRadius: '6px', padding: '3px 8px', fontSize: '11px', cursor: 'pointer', color: 'var(--text-secondary)' }}
                                                                    title={`Configure ${category}`}
                                                                >Configure</button>
                                                            )}
                                                            {/* Category Bulk Toggle */}
                                                            <label style={{ position: 'relative', display: 'inline-block', width: '40px', height: '22px', cursor: 'pointer', flexShrink: 0 }} title={`Enable/Disable all ${categoryLabels[category] || category} tools`}>
                                                                <input type="checkbox"
                                                                    checked={(catTools as any[]).every(t => t.enabled)}
                                                                    onChange={async (e) => {
                                                                        const targetEnabled = e.target.checked;
                                                                        try {
                                                                            const payload = (catTools as any[]).map(t => ({ tool_id: t.id, enabled: targetEnabled }));
                                                                            await fetchJson('/tools/bulk', { method: 'PUT', body: JSON.stringify(payload) });
                                                                            loadAllTools();
                                                                        } catch (err: any) {
                                                                            useToastStore.getState().showToast('Bulk update failed: ' + err.message, 'error');
                                                                        }
                                                                    }}
                                                                    style={{ opacity: 0, width: 0, height: 0 }} />
                                                                <span style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, borderRadius: '22px', background: (catTools as any[]).every(t => t.enabled) ? 'var(--accent-primary)' : 'var(--bg-tertiary)', transition: '0.3s', boxShadow: 'inset 0 1px 3px rgba(0,0,0,0.1)' }}>
                                                                    <span style={{ position: 'absolute', left: (catTools as any[]).every(t => t.enabled) ? '20px' : '2px', top: '2px', width: '18px', height: '18px', borderRadius: '50%', background: '#fff', transition: '0.3s', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }} />
                                                                </span>
                                                            </label>
                                                        </div>
                                                    </div>

                                                    {/* Tools in this category */}
                                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                                                        {(catTools as any[]).map((tool: any) => {
                                                            // If this category has shared config, individual tool config buttons are hidden
                                                            const hasOwnConfig = tool.config_schema?.fields?.length > 0 && !hasCategoryConfig;
                                                            const isEditing = editingToolId === tool.id;

                                                            return (
                                                                <div key={tool.id} data-tool-name={tool.name || tool.id} className="card" style={{ padding: '0', overflow: 'hidden' }}>
                                                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px' }}>
                                                                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, minWidth: 0 }}>
                                                                            <span style={{ fontSize: '18px' }}>{tool.icon}</span>
                                                                            <div style={{ minWidth: 0 }}>
                                                                                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                                                                    <span style={{ fontWeight: 500, fontSize: '13px' }}>{tool.display_name}</span>
                                                                                    <span style={{ fontSize: '10px', background: tool.type === 'mcp' ? 'var(--primary)' : 'var(--bg-tertiary)', color: tool.type === 'mcp' ? '#fff' : 'var(--text-secondary)', borderRadius: '4px', padding: '1px 5px' }}>
                                                                                        {tool.type === 'mcp' ? 'MCP' : 'Built-in'}
                                                                                    </span>
                                                                                    {tool.is_default && <span style={{ fontSize: '10px', background: 'rgba(0,200,100,0.15)', color: 'var(--success)', borderRadius: '4px', padding: '1px 5px' }}>Default</span>}
                                                                                </div>
                                                                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                                                    {tool.description?.slice(0, 80)}
                                                                                    {tool.mcp_server_name && <span> · {tool.mcp_server_name}</span>}
                                                                                </div>
                                                                            </div>
                                                                        </div>

                                                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>
                                                                            {/* Per-tool config button: only if the tool has its own schema AND is NOT part of a category config */}
                                                                            {hasOwnConfig && (
                                                                                <button className="btn btn-secondary" style={{ padding: '4px 8px', fontSize: '11px' }} onClick={async () => {
                                                                                    if (isEditing) {
                                                                                        setEditingToolId(null);
                                                                                    } else {
                                                                                        setEditingToolId(tool.id);
                                                                                        const cfg = { ...tool.config };
                                                                                        // Pre-load jina api_key from system_settings
                                                                                        if (tool.name === 'jina_search' || tool.name === 'jina_read') {
                                                                                            try {
                                                                                                const d = await request<any>('/enterprise/system-settings/jina_api_key');
                                                                                                if (d.value?.api_key) cfg.api_key = d.value.api_key;
                                                                                            } catch { }
                                                                                        }
                                                                                        setEditingConfig(cfg);
                                                                                    }
                                                                                }}>{isEditing ? t('enterprise.tools.collapse') : t('enterprise.tools.configure')}</button>
                                                                            )}

                                                                            {/* Delete (non-builtin only) */}
                                                                            {tool.type !== 'builtin' && (
                                                                                <button className="btn btn-danger" style={{ padding: '4px 8px', fontSize: '11px' }} onClick={async () => {
                                                                                    if (!confirm(`${t('common.delete')} ${tool.display_name}?`)) return;
                                                                                    await fetchJson(`/tools/${tool.id}`, { method: 'DELETE' });
                                                                                    loadAllTools();
                                                                                    loadAgentInstalledTools();
                                                                                }}>{t('common.delete')}</button>
                                                                            )}

                                                                            {/* Enable toggle */}
                                                                            <label style={{ position: 'relative', display: 'inline-block', width: '40px', height: '22px', cursor: 'pointer', flexShrink: 0 }}>
                                                                                <input type="checkbox" checked={tool.enabled} onChange={async (e) => {
                                                                                    await fetchJson(`/tools/${tool.id}`, { method: 'PUT', body: JSON.stringify({ enabled: e.target.checked }) });
                                                                                    loadAllTools();
                                                                                }} style={{ opacity: 0, width: 0, height: 0 }} />
                                                                                <span style={{ position: 'absolute', inset: 0, background: tool.enabled ? 'var(--accent-primary)' : 'var(--bg-tertiary)', borderRadius: '11px', transition: 'background 0.2s' }}>
                                                                                    <span style={{ position: 'absolute', left: tool.enabled ? '20px' : '2px', top: '2px', width: '18px', height: '18px', background: '#fff', borderRadius: '50%', transition: 'left 0.2s' }} />
                                                                                </span>
                                                                            </label>
                                                                        </div>
                                                                    </div>

                                                                    {/* Inline config editing form (per-tool only) */}
                                                                    {/* Inline config editing form replaced by global modal */}
                                                                </div>
                                                            );
                                                        })}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                );
                            })()}

                            {/* Per-Tool Config Modal */}
                            {editingToolId && (() => {
                                const tool = allTools.find(t => t.id === editingToolId);
                                if (!tool) return null;
                                return (
                                    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.55)', zIndex: 2000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                        onClick={() => setEditingToolId(null)}>
                                        <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', width: '480px', maxWidth: '95vw', maxHeight: '80vh', overflow: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                                                <div>
                                                    <h3 style={{ margin: 0 }}>⚙️ {tool.display_name}</h3>
                                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>Global configuration used by all agents</div>
                                                </div>
                                                <button onClick={() => setEditingToolId(null)} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)' }}>✕</button>
                                            </div>
                                            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                                {(tool.config_schema.fields || []).map((field: any) => {
                                                    // Check depends_on
                                                    if (field.depends_on) {
                                                        const visible = Object.entries(field.depends_on).every(([k, vals]: [string, any]) =>
                                                            vals.includes(editingConfig[k])
                                                        );
                                                        if (!visible) return null;
                                                    }
                                                    return (
                                                        <div key={field.key}>
                                                            <label style={{ display: 'block', fontSize: '12px', fontWeight: 500, marginBottom: '4px' }}>{field.label}</label>
                                                            {field.type === 'checkbox' ? (
                                                                <label style={{ position: 'relative', display: 'inline-block', width: '40px', height: '22px', cursor: 'pointer' }}>
                                                                    <input
                                                                        type="checkbox"
                                                                        checked={editingConfig[field.key] ?? field.default ?? false}
                                                                        onChange={e => setEditingConfig(p => ({ ...p, [field.key]: e.target.checked }))}
                                                                        style={{ opacity: 0, width: 0, height: 0 }}
                                                                    />
                                                                    <span style={{
                                                                        position: 'absolute', inset: 0,
                                                                        background: (editingConfig[field.key] ?? field.default) ? 'var(--accent-primary)' : 'var(--bg-tertiary)',
                                                                        borderRadius: '11px', transition: 'background 0.2s',
                                                                    }}>
                                                                        <span style={{
                                                                            position: 'absolute', left: (editingConfig[field.key] ?? field.default) ? '20px' : '2px', top: '2px',
                                                                            width: '18px', height: '18px', background: '#fff',
                                                                            borderRadius: '50%', transition: 'left 0.2s',
                                                                        }} />
                                                                    </span>
                                                                </label>
                                                            ) : field.type === 'select' ? (
                                                                <select className="form-input" value={editingConfig[field.key] ?? field.default ?? ''} onChange={e => setEditingConfig(p => ({ ...p, [field.key]: e.target.value }))}>
                                                                    {(field.options || []).map((opt: any) => (
                                                                        <option key={opt.value} value={opt.value}>{opt.label}</option>
                                                                    ))}
                                                                </select>
                                                            ) : field.type === 'number' ? (
                                                                <input type="number" className="form-input" value={editingConfig[field.key] ?? field.default ?? ''} min={field.min} max={field.max}
                                                                    onChange={e => setEditingConfig(p => ({ ...p, [field.key]: Number(e.target.value) }))} />
                                                            ) : field.type === 'password' ? (
                                                                <input type="password" autoComplete="new-password" className="form-input" value={editingConfig[field.key] ?? ''} placeholder={field.placeholder || ''}
                                                                    onChange={e => setEditingConfig(p => ({ ...p, [field.key]: e.target.value }))} />
                                                            ) : (
                                                                <input type="text" className="form-input" value={editingConfig[field.key] ?? field.default ?? ''} placeholder={field.placeholder || ''}
                                                                    onChange={e => setEditingConfig(p => ({ ...p, [field.key]: e.target.value }))} />
                                                            )}
                                                        </div>
                                                    );
                                                })}
                                                <div style={{ display: 'flex', gap: '8px', marginTop: '12px', justifyContent: 'flex-end', borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
                                                    <button className="btn btn-secondary" onClick={() => setEditingToolId(null)}>{t('common.cancel')}</button>
                                                    <button className="btn btn-primary" onClick={async () => {
                                                        if (tool.name === 'jina_search' || tool.name === 'jina_read') {
                                                            if (editingConfig.api_key) {
                                                                await request('/enterprise/system-settings/jina_api_key', {
                                                                    method: 'PUT',
                                                                    body: JSON.stringify({ value: { api_key: editingConfig.api_key } }),
                                                                });
                                                            }
                                                        } else {
                                                            await fetchJson(`/tools/${tool.id}`, { method: 'PUT', body: JSON.stringify({ config: editingConfig }) });
                                                        }
                                                        setEditingToolId(null);
                                                        loadAllTools();
                                                    }}>{t('enterprise.tools.saveConfig')}</button>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                );
                            })()}

                            {/* Category-level config modal */}
                            {configCategory && GLOBAL_CATEGORY_CONFIG_SCHEMAS[configCategory] && (
                                <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.55)', zIndex: 2000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                    onClick={() => setConfigCategory(null)}>
                                    <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', width: '480px', maxWidth: '95vw', maxHeight: '80vh', overflow: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                                            <div>
                                                <h3 style={{ margin: 0 }}>{GLOBAL_CATEGORY_CONFIG_SCHEMAS[configCategory].title}</h3>
                                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>Global configuration shared by all tools in this category</div>
                                            </div>
                                            <button onClick={() => setConfigCategory(null)} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)' }}>x</button>
                                        </div>
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                            {GLOBAL_CATEGORY_CONFIG_SCHEMAS[configCategory].fields.map((field: any) => (
                                                <div key={field.key}>
                                                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 500, marginBottom: '4px' }}>{field.label}</label>
                                                    {field.type === 'password' ? (
                                                        <input type="password" autoComplete="new-password" className="form-input" value={editingConfig[field.key] ?? ''} placeholder={field.placeholder || ''}
                                                            onChange={e => setEditingConfig(p => ({ ...p, [field.key]: e.target.value }))} />
                                                    ) : field.type === 'select' ? (
                                                        <select className="form-input" value={editingConfig[field.key] ?? field.default ?? ''} onChange={e => setEditingConfig(p => ({ ...p, [field.key]: e.target.value }))}>
                                                            {(field.options || []).map((o: any) => <option key={o.value} value={o.value}>{o.label}</option>)}
                                                        </select>
                                                    ) : (
                                                        <input type="text" className="form-input" value={editingConfig[field.key] ?? ''} placeholder={field.placeholder || ''}
                                                            onChange={e => setEditingConfig(p => ({ ...p, [field.key]: e.target.value }))} />
                                                    )}
                                                </div>
                                            ))}
                                            <div style={{ display: 'flex', gap: '8px', marginTop: '8px', justifyContent: 'flex-end' }}>
                                                <button className="btn btn-secondary" onClick={() => setConfigCategory(null)}>{t('common.cancel')}</button>
                                                <button className="btn btn-primary" onClick={async () => {
                                                    // Save config to the first tool in this category.
                                                    // We write to one representative tool per category;
                                                    // get_category_config endpoint reads it back.
                                                    const catTools = allTools.filter((tl: any) => (tl.category || 'general') === configCategory);
                                                    if (catTools.length > 0) {
                                                        await fetchJson(`/tools/${catTools[0].id}`, { method: 'PUT', body: JSON.stringify({ config: editingConfig }) });
                                                    }
                                                    setConfigCategory(null);
                                                    loadAllTools();
                                                }}>{t('common.save', '保存')}</button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* ── Windows Automation Nodes ── */}
                            <WindowsAutomationNodes />
                        </>}
                    </div>
                )}

                {/* ── Skills Tab ── */}
                {activeTab === 'skills' && <SkillsTab />}

                {/* ── Invitation Codes Tab ── */}
                {activeTab === 'invites' && <InvitationCodes />}
            </div>

            {
                kbToast && (
                    <div style={{
                        position: 'fixed', top: '20px', right: '20px', zIndex: 20000,
                        padding: '12px 20px', borderRadius: '8px',
                        background: kbToast.type === 'success' ? 'rgba(34, 197, 94, 0.9)' : 'rgba(239, 68, 68, 0.9)',
                        color: '#fff', fontSize: '14px', fontWeight: 500,
                        boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
                    }}>
                        {''}{kbToast.message}
                    </div>
                )
            }
        </>
    );
}
