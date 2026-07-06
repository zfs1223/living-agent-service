import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { fetchJson } from '../../services/api';
import { copyToClipboard } from '../../utils/clipboard';
import { useToastStore } from '../../stores/toastStore';

const FEISHU_SYNC_PERM_JSON = `{
  "scopes": {
    "tenant": [
      "contact:contact.base:readonly",
      "contact:department.base:readonly",
      "contact:user.base:readonly",
      "contact:user.employee_id:readonly"
    ],
    "user": []
  }
}`;


// ─── Department Tree ───────────────────────────────
function DeptTree({ departments, parentId, selectedDept, onSelect, level }: {
    departments: any[]; parentId: string | null; selectedDept: string | null;
    onSelect: (id: string | null) => void; level: number;
}) {
    const qc = useQueryClient();
    const children = departments.filter((d: any) =>
        parentId === null ? !d.parent_id : d.parent_id === parentId
    );
    if (children.length === 0) return null;
    return (
        <>
            {children.map((d: any) => (
                <div key={d.id}>
                    <div
                        style={{
                            padding: '5px 8px',
                            paddingLeft: `${8 + level * 16}px`,
                            borderRadius: '4px',
                            cursor: 'pointer', fontSize: '13px', marginBottom: '1px',
                            background: selectedDept === d.id ? 'rgba(224,238,238,0.12)' : 'transparent',
                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                        }}
                        onClick={() => onSelect(d.id)}
                    >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '2px' }}>
                            <span style={{ color: 'var(--text-tertiary)', marginRight: '4px', fontSize: '11px' }}>
                                {departments.some((c: any) => c.parent_id === d.id) ? '▾' : '·'}
                            </span>
                            {d.name}
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '2px' }}>
                            {d.member_count !== undefined && (
                                <span style={{ fontSize: '10px', color: 'var(--text-tertiary)' }}>
                                    {d.member_count}
                                </span>
                            )}
                            <button style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '10px', color: 'var(--text-tertiary)', padding: '0 2px', opacity: 0.5 }} title="Edit"
                                onClick={e => { e.stopPropagation(); const newName = prompt('New name:', d.name); if (newName?.trim() && newName.trim() !== d.name) { fetchJson(`/departments/${d.id}`, { method: 'PUT', body: JSON.stringify({ name: newName.trim() }) }).then(() => qc.invalidateQueries({ queryKey: ['departments'] })).catch(() => {}); } }}>✏</button>
                            <button style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '10px', color: 'var(--error)', padding: '0 2px', opacity: 0.5 }} title="Delete"
                                onClick={e => { e.stopPropagation(); if (confirm(`Delete department "${d.name}"?`)) { fetchJson(`/departments/${d.id}`, { method: 'DELETE' }).then(() => qc.invalidateQueries({ queryKey: ['departments'] })).catch(() => {}); } }}>✕</button>
                        </div>
                    </div>
                    <DeptTree departments={departments} parentId={d.id} selectedDept={selectedDept} onSelect={onSelect} level={level + 1} />
                </div>
            ))}
        </>
    );
}

// ─── Org & Identity Tab ─────────────────────────────
export default function OrgTab({ tenant }: { tenant: any }) {
    const { t } = useTranslation();
    const qc = useQueryClient();

    const SsoStatus = () => {
        const [isExpanded, setIsExpanded] = useState(!!tenant?.sso_enabled);
        const [ssoEnabled, setSsoEnabled] = useState(!!tenant?.sso_enabled);
        const [ssoDomain, setSsoDomain] = useState(tenant?.sso_domain || '');
        const [saving, setSaving] = useState(false);
        const [error, setError] = useState('');

        useEffect(() => {
            setSsoEnabled(!!tenant?.sso_enabled);
            setSsoDomain(tenant?.sso_domain || '');
            setIsExpanded(!!tenant?.sso_enabled);
        }, [tenant]);

        const handleSave = async (forceEnabled?: boolean) => {
            if (!tenant?.id) return;
            const targetEnabled = forceEnabled !== undefined ? forceEnabled : ssoEnabled;
            setSaving(true);
            setError('');
            try {
                await fetchJson(`/tenants/${tenant.id}`, {
                    method: 'PUT',
                    body: JSON.stringify({
                        sso_enabled: targetEnabled,
                        sso_domain: targetEnabled ? (ssoDomain.trim() || null) : null,
                    }),
                });
                qc.invalidateQueries({ queryKey: ['tenant', tenant.id] });
            } catch (e: any) {
                setError(e.message || 'Failed to update SSO configuration');
            }
            setSaving(false);
        };

        const handleToggle = (e: React.ChangeEvent<HTMLInputElement>) => {
            const checked = e.target.checked;
            setSsoEnabled(checked);
            setIsExpanded(checked);
            if (!checked) {
                // auto-save when disabling
                handleSave(false);
            }
        };

        return (
            <div className="card" style={{ marginBottom: '24px', overflow: 'hidden' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px' }}>
                    <div>
                        <div style={{ fontWeight: 600, fontSize: '14px', marginBottom: '4px' }}>
                            {t('enterprise.identity.ssoTitle', '企业单点登录')}
                        </div>
                        <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                            {t('enterprise.identity.ssoDisabledHint', '通过单点登录实现企业无缝登录。')}
                        </div>
                    </div>
                    <div>
                        <label style={{ position: 'relative', display: 'inline-block', width: '36px', height: '20px' }}>
                            <input 
                                type="checkbox" 
                                checked={ssoEnabled} 
                                onChange={handleToggle}
                                style={{ opacity: 0, width: 0, height: 0 }} 
                            />
                            <span style={{
                                position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
                                borderRadius: '20px', cursor: 'pointer',
                                background: ssoEnabled ? 'var(--accent-primary)' : 'var(--border-subtle)',
                                transition: '0.2s'
                            }}>
                                <span style={{
                                    position: 'absolute', left: ssoEnabled ? '18px' : '2px', top: '2px',
                                    width: '16px', height: '16px', borderRadius: '50%',
                                    background: '#fff', transition: '0.2s',
                                    boxShadow: '0 1px 2px rgba(0,0,0,0.1)'
                                }} />
                            </span>
                        </label>
                    </div>
                </div>

                {isExpanded && (
                    <div style={{ padding: '0 16px 16px', borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
                        <div style={{ marginBottom: '16px' }}>
                            <label className="form-label" style={{ fontSize: '12px', marginBottom: '8px' }}>
                                {t('enterprise.identity.ssoDomain', '自定义访问域名')}
                            </label>
                            <input
                                className="form-input"
                                value={ssoDomain}
                                onChange={e => setSsoDomain(e.target.value)}
                                placeholder={t('enterprise.identity.ssoDomainPlaceholder', 'e.g. acme.living-agent.com')}
                                style={{ fontSize: '13px', width: '100%', maxWidth: '400px' }}
                            />
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '6px' }}>
                                {t('enterprise.identity.ssoDomainDesc', '用户通过 SSO 登录时使用的自定义域名。')}
                            </div>
                        </div>

                        {error && <div style={{ color: 'var(--error)', fontSize: '12px', marginBottom: '12px' }}>{error}</div>}

                        <div style={{ display: 'flex', gap: '8px' }}>
                            <button className="btn btn-primary btn-sm" onClick={() => handleSave()} disabled={saving || !ssoDomain.trim()}>
                                {saving ? t('common.loading') : t('common.save', '保存配置')}
                            </button>
                        </div>
                    </div>
                )}
            </div>
        );
    };

    const [syncing, setSyncing] = useState<string | null>(null);
    const [syncResult, setSyncResult] = useState<any>(null);
    const [memberSearch, setMemberSearch] = useState('');
    const [selectedDept, setSelectedDept] = useState<string | null>(null);
    const [expandedType, setExpandedType] = useState<string | null>(null);
    const [savingProvider, setSavingProvider] = useState(false);
    const [saveProviderOk, setSaveProviderOk] = useState(false);

    // Identity Providers state
    const [editingId, setEditingId] = useState<string | null>(null);
    const [useOAuth2Form, setUseOAuth2Form] = useState(false);
    const [form, setForm] = useState({
        provider_type: 'feishu',
        name: '',
        config: {} as any,
        app_id: '',
        app_secret: '',
        authorize_url: '',
        token_url: '',
        user_info_url: '',
        scope: 'openid profile email'
    });

    const currentTenantId = localStorage.getItem('current_tenant_id') || '';

    // Queries
    const { data: providers = [] } = useQuery({
        queryKey: ['identity-providers', currentTenantId],
        queryFn: () => fetchJson<any[]>(`/enterprise/identity-providers${currentTenantId ? `?tenant_id=${currentTenantId}` : ''}`),
    });

    const { data: departmentsData = { items: [], total_member: 0 } } = useQuery({
        queryKey: ['org-departments', currentTenantId, editingId],
        queryFn: () => {
            const params = new URLSearchParams();
            if (currentTenantId) params.set('tenant_id', currentTenantId);
            if (editingId) params.set('provider_id', editingId);
            return fetchJson<{ items: any[]; total_member: number }>(`/enterprise/org/departments?${params}`);
        },
        enabled: !!editingId,
    });

    const { data: members = [] } = useQuery({
        queryKey: ['org-members', selectedDept, memberSearch, currentTenantId, editingId],
        queryFn: () => {
            const params = new URLSearchParams();
            if (selectedDept) params.set('department_id', selectedDept);
            if (memberSearch) params.set('search', memberSearch);
            if (currentTenantId) params.set('tenant_id', currentTenantId);
            if (editingId) params.set('provider_id', editingId);
            return fetchJson<any[]>(`/enterprise/org/members?${params}`);
        },
        enabled: !!editingId,
    });

    // Mutations
    const addProvider = useMutation({
        mutationFn: (data: any) => {
            const payload = { ...data, tenant_id: currentTenantId, is_active: true };
            if (data.provider_type === 'oauth2' && useOAuth2Form) {
                return fetchJson('/enterprise/identity-providers/oauth2', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
            }
            return fetchJson('/enterprise/identity-providers', { method: 'POST', body: JSON.stringify(payload) });
        },
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['identity-providers'] });
            setUseOAuth2Form(false);
            setSavingProvider(false);
            setSaveProviderOk(true);
            setTimeout(() => setSaveProviderOk(false), 2500);
        },
        onError: () => setSavingProvider(false),
    });

    const updateProvider = useMutation({
        mutationFn: ({ id, data }: { id: string; data: any }) => {
            if (data.provider_type === 'oauth2' && useOAuth2Form) {
                return fetchJson(`/enterprise/identity-providers/${id}/oauth2`, {
                    method: 'PATCH',
                    body: JSON.stringify(data)
                });
            }
            return fetchJson(`/enterprise/identity-providers/${id}`, { method: 'PUT', body: JSON.stringify(data) });
        },
        onSuccess: () => {
            qc.invalidateQueries({ queryKey: ['identity-providers'] });
            setUseOAuth2Form(false);
            setSavingProvider(false);
            setSaveProviderOk(true);
            setTimeout(() => setSaveProviderOk(false), 2500);
        },
        onError: () => setSavingProvider(false),
    });

    const deleteProvider = useMutation({
        mutationFn: (id: string) => fetchJson(`/enterprise/identity-providers/${id}`, { method: 'DELETE' }),
        onSuccess: () => qc.invalidateQueries({ queryKey: ['identity-providers'] }),
    });

    const triggerSync = async (providerId: string) => {
        setSyncing(providerId);
        setSyncResult(null);
        try {
            const result = await fetchJson<any>(`/enterprise/org/sync?provider_id=${providerId}`, { method: 'POST' });
            setSyncResult({ ...result, providerId });
            qc.invalidateQueries({ queryKey: ['org-departments'] });
            qc.invalidateQueries({ queryKey: ['org-members'] });
            qc.invalidateQueries({ queryKey: ['identity-providers'] });
        } catch (e: any) {
            setSyncResult({ error: e.message, providerId });
        }
        setSyncing(null);
    };

    const initOAuth2FromConfig = (config: any) => ({
        app_id: config?.app_id || config?.client_id || '',
        app_secret: config?.app_secret || config?.client_secret || '',
        authorize_url: config?.authorize_url || '',
        token_url: config?.token_url || '',
        user_info_url: config?.user_info_url || '',
        scope: config?.scope || 'openid profile email'
    });

    const save = () => {
        setSavingProvider(true);
        setSaveProviderOk(false);
        if (editingId) {
            updateProvider.mutate({ id: editingId, data: form });
        } else {
            addProvider.mutate(form);
        }
    };

    const IDP_TYPES = [
        { type: 'feishu', name: 'Feishu', desc: 'Feishu / Lark Integration', icon: <img src="/feishu.png" width="20" height="20" alt="Feishu"/> },
        { type: 'wecom', name: 'WeCom', desc: 'WeChat Work Integration', icon: <img src="/wecom.png" width="20" height="20" style={{ borderRadius: '4px' }} alt="WeCom"/> },
        { type: 'dingtalk', name: 'DingTalk', desc: 'DingTalk App Integration', icon: <img src="/dingtalk.png" width="20" height="20" style={{ borderRadius: '4px' }} alt="DingTalk"/> },
        { type: 'oauth2', name: 'OAuth2', desc: 'Generic OIDC Provider', icon: <div style={{width: 20, height: 20, background: 'var(--accent-primary)', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 10, fontWeight: 700}}>O</div> }
    ];

    const handleExpand = (type: string, existingProvider?: any) => {
        if (expandedType === type) {
            setExpandedType(null);
            return;
        }
        setExpandedType(type);
        setEditingId(existingProvider ? existingProvider.id : null);
        setUseOAuth2Form(type === 'oauth2');
        
        if (existingProvider) {
            setForm({ ...existingProvider, ...(type === 'oauth2' ? initOAuth2FromConfig(existingProvider.config) : {}) });
        } else {
            const defaults: any = {
                feishu: { app_id: '', app_secret: '', corp_id: '' },
                dingtalk: { app_key: '', app_secret: '', corp_id: '' },
                wecom: { corp_id: '', secret: '', agent_id: '', bot_id: '', bot_secret: '' },
            };
            const nameMap: Record<string, string> = { feishu: 'Feishu', wecom: 'WeCom', dingtalk: 'DingTalk', oauth2: 'OAuth2' };
            setForm({
                provider_type: type,
                name: nameMap[type] || type,
                config: defaults[type] || {},
                app_id: '', app_secret: '', authorize_url: '', token_url: '', user_info_url: '',
                scope: 'openid profile email'
            });
        }
        setSelectedDept(null);
        setMemberSearch('');
    };

    const renderForm = (type: string, existingProvider?: any) => {
        return (
            <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px solid var(--border-subtle)' }}>
                {/* Setup Guide moved to the top */}
                {['feishu', 'dingtalk', 'wecom'].includes(type) && (
                    <div style={{ background: 'var(--bg-primary)', padding: '16px', borderRadius: '8px', border: '1px solid var(--border-subtle)', marginBottom: '20px', fontSize: '12px' }}>
                        <div style={{ fontWeight: 600, fontSize: '13px', marginBottom: '8px', color: 'var(--text-primary)' }}>
                            👉 {t('enterprise.org.syncSetupGuide', '设置指南与所需权限')}
                        </div>
                        <div style={{ color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                            {type === 'feishu' && (
                                <>
                                    {Array.from({ length: 7 }).map((_, i) => (
                                        <div key={i} style={{ marginBottom: '6px' }}>
                                            {i + 1}. {t(`enterprise.org.syncGuide.feishu.step${i + 1}`)}
                                        </div>
                                    ))}
                                    <div style={{ marginTop: '16px', marginBottom: '8px' }}>
                                        {t('enterprise.org.feishuGuideText', '权限 JSON（批量导入）')}
                                    </div>
                                    <div style={{ position: 'relative', background: '#282c34', borderRadius: '6px', padding: '12px', paddingRight: '40px', color: '#abb2bf', fontFamily: 'monospace', fontSize: '11px', whiteSpace: 'pre-wrap', overflowX: 'auto' }}>
                                        <button 
                                            className="btn btn-ghost" 
                                            style={{ position: 'absolute', top: '8px', right: '8px', fontSize: '10px', color: '#abb2bf', padding: '4px 8px', background: 'rgba(255,255,255,0.1)', cursor: 'pointer', border: 'none', borderRadius: '4px' }}
                                            onClick={(e) => { e.preventDefault(); copyToClipboard(FEISHU_SYNC_PERM_JSON); e.currentTarget.textContent = 'Copied✓'; setTimeout(() => { e.currentTarget.textContent = 'Copy'; }, 2000); }}
                                        >
                                            Copy
                                        </button>
                                        {FEISHU_SYNC_PERM_JSON}
                                    </div>
                                    <div style={{ marginTop: '8px', color: 'var(--text-secondary)' }}>
                                        {t('enterprise.org.feishuGuideWarning', '注意：每次添加新权限后需要重新发布应用。')}
                                    </div>
                                </>
                            )}
                            {type === 'dingtalk' && (
                                <>
                                    {Array.from({ length: 6 }).map((_, i) => (
                                        <div key={i} style={{ marginBottom: '6px' }}>
                                            {i + 1}. {t(`enterprise.org.syncGuide.dingtalk.step${i + 1}`)}
                                        </div>
                                    ))}
                                </>
                            )}
                            {type === 'wecom' && (
                                <>
                                    {Array.from({ length: 5 }).map((_, i) => (
                                        <div key={i} style={{ marginBottom: '6px' }}>
                                            {i + 1}. {t(`enterprise.org.syncGuide.wecom.step${i + 1}`)}
                                        </div>
                                    ))}
                                </>
                            )}
                        </div>
                    </div>
                )}

                {/* Name field only for oauth2 */}
                {type === 'oauth2' && (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '16px' }}>
                        <div className="form-group">
                            <label className="form-label">{t('enterprise.identity.name')}</label>
                            <input className="form-input" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
                        </div>
                    </div>
                )}

                {type === 'oauth2' ? (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div className="form-group">
                            <label className="form-label">Client ID</label>
                            <input className="form-input" value={form.app_id} onChange={e => setForm({ ...form, app_id: e.target.value })} />
                        </div>
                        <div className="form-group">
                            <label className="form-label">Client Secret</label>
                            <input className="form-input" type="password" value={form.app_secret} onChange={e => setForm({ ...form, app_secret: e.target.value })} />
                        </div>
                        <div className="form-group" style={{ gridColumn: '1 / -1' }}>
                            <label className="form-label">Authorize URL</label>
                            <input className="form-input" value={form.authorize_url} onChange={e => setForm({ ...form, authorize_url: e.target.value })} />
                        </div>
                    </div>
                ) : type === 'wecom' ? (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div className="form-group" style={{ gridColumn: '1 / -1' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>
                                {t('enterprise.identity.providerHints.wecom')}
                            </div>
                        </div>
                        <div className="form-group">
                            <label className="form-label">Corp ID</label>
                            <input className="form-input" value={form.config.corp_id || ''} onChange={e => setForm({ ...form, config: { ...form.config, corp_id: e.target.value } })} placeholder="wwxxxxxxxxxxxx" />
                        </div>
                        <div className="form-group">
                            <label className="form-label">Secret</label>
                            <input className="form-input" type="password" value={form.config.secret || ''} onChange={e => setForm({ ...form, config: { ...form.config, secret: e.target.value } })} />
                        </div>
                        <div className="form-group">
                            <label className="form-label">Agent ID (Optional)</label>
                            <input className="form-input" value={form.config.agent_id || ''} onChange={e => setForm({ ...form, config: { ...form.config, agent_id: e.target.value } })} />
                        </div>
                        <div style={{ gridColumn: '1 / -1', height: '1px', background: 'var(--border-subtle)', margin: '8px 0' }} />
                        <div className="form-group">
                            <label className="form-label">Bot ID (Intelligent Robot)</label>
                            <input className="form-input" value={form.config.bot_id || ''} onChange={e => setForm({ ...form, config: { ...form.config, bot_id: e.target.value } })} placeholder="aibXXXXXXXXXXXX" />
                        </div>
                        <div className="form-group">
                            <label className="form-label">Bot Secret</label>
                            <input className="form-input" type="password" value={form.config.bot_secret || ''} onChange={e => setForm({ ...form, config: { ...form.config, bot_secret: e.target.value } })} />
                        </div>
                    </div>
                ) : type === 'dingtalk' ? (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div className="form-group" style={{ gridColumn: '1 / -1' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('enterprise.identity.providerHints.dingtalk')}</div>
                        </div>
                        <div className="form-group">
                            <label className="form-label">App Key</label>
                            <input className="form-input" value={form.config.app_key || ''} onChange={e => setForm({ ...form, config: { ...form.config, app_key: e.target.value } })} placeholder="dingxxxxxxxxxxxx" />
                        </div>
                        <div className="form-group">
                            <label className="form-label">App Secret</label>
                            <input className="form-input" type="password" value={form.config.app_secret || ''} onChange={e => setForm({ ...form, config: { ...form.config, app_secret: e.target.value } })} />
                        </div>
                    </div>
                ) : type === 'feishu' ? (
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div className="form-group" style={{ gridColumn: '1 / -1' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('enterprise.identity.providerHints.feishu')}</div>
                        </div>
                        <div className="form-group">
                            <label className="form-label">App ID</label>
                            <input className="form-input" value={form.config.app_id || ''} onChange={e => setForm({ ...form, config: { ...form.config, app_id: e.target.value } })} placeholder="cli_xxxxxxxxxxxx" />
                        </div>
                        <div className="form-group">
                            <label className="form-label">App Secret</label>
                            <input className="form-input" type="password" value={form.config.app_secret || ''} onChange={e => setForm({ ...form, config: { ...form.config, app_secret: e.target.value } })} />
                        </div>
                    </div>
                ) : null}

                <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginTop: '16px' }}>
                    <button className="btn btn-primary btn-sm" onClick={save} disabled={savingProvider}>
                        {savingProvider ? t('common.loading') : t('common.save', '保存')}
                    </button>
                    {saveProviderOk && (
                        <span style={{ fontSize: '12px', color: 'var(--success)' }}>Saved</span>
                    )}
                    {existingProvider && (
                        <button className="btn btn-ghost btn-sm" style={{ color: 'var(--error)' }} onClick={() => confirm('Are you sure you want to delete this configuration?') && deleteProvider.mutate(existingProvider.id)}>
                            {t('common.delete', '删除')}
                        </button>
                    )}
                </div>
            </div>
        );
    };

    const renderOrgBrowser = (p: any) => {
        return (
            <div style={{ marginTop: '24px', paddingTop: '24px', borderTop: '1px dashed var(--border-subtle)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                    <div style={{ fontWeight: 500, fontSize: '14px' }}>{t('enterprise.org.orgBrowser', '组织目录')}</div>
                    
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '8px' }}>
                        {['feishu', 'dingtalk', 'wecom'].includes(p.provider_type) && (
                            <button className="btn btn-secondary btn-sm" style={{ fontSize: '12px' }} onClick={() => triggerSync(p.id)} disabled={!!syncing}>
                                {syncing === p.id ? 'Syncing...' : 'Sync Directory'}
                            </button>
                        )}
                        {syncResult && (
                            <div style={{ padding: '6px 10px', borderRadius: '4px', fontSize: '11px', background: syncResult.error ? 'rgba(255,0,0,0.1)' : 'rgba(0,200,0,0.1)' }}>
                                {syncResult.error ? `Error: ${syncResult.error}` : `Sync complete: ${syncResult.users_created || 0} users created, ${syncResult.profiles_synced || 0} profiles synced.`}
                            </div>
                        )}
                    </div>
                </div>


                <div style={{ display: 'flex', gap: '16px' }}>
                    <div style={{ width: '260px', borderRight: '1px solid var(--border-subtle)', paddingRight: '16px', maxHeight: '500px', overflowY: 'auto' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                            <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)' }}>{t('enterprise.org.departments', '部门')}</span>
                            <button className="btn btn-secondary" style={{ fontSize: '10px', padding: '2px 8px' }} onClick={() => {
                                const name = prompt(t('enterprise.org.newDeptName', '请输入部门名称'));
                                if (name?.trim()) {
                                    fetchJson('/departments', { method: 'POST', body: JSON.stringify({ name: name.trim(), code: name.trim().toLowerCase().replace(/\s+/g, '-'), parent_id: selectedDept || undefined }) })
                                        .then(() => qc.invalidateQueries({ queryKey: ['departments'] }))
                                        .catch(() => { });
                                }
                            }}>+ {t('enterprise.org.addDept', '新增')}</button>
                        </div>
                        <div style={{ padding: '6px 8px', borderRadius: '4px', cursor: 'pointer', fontSize: '13px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: !selectedDept ? 'rgba(224,238,238,0.1)' : 'transparent' }} onClick={() => setSelectedDept(null)}>
                            {t('common.all')}
                            {departmentsData.total_member > 0 && <span style={{ fontSize: '10px', color: 'var(--text-tertiary)' }}>({departmentsData.total_member})</span>}
                        </div>
                        <DeptTree departments={departmentsData.items} parentId={null} selectedDept={selectedDept} onSelect={setSelectedDept} level={0} />
                    </div>

                    <div style={{ flex: 1 }}>
                        <input className="form-input" placeholder={t("enterprise.org.searchMembers")} value={memberSearch} onChange={e => setMemberSearch(e.target.value)} style={{ marginBottom: '12px' }} />
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', maxHeight: '400px', overflowY: 'auto' }}>
                            {members.map((m: any) => (
                                <div key={m.id} style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '8px', borderRadius: '6px', border: '1px solid var(--border-subtle)' }}>
                                    <div style={{ width: '32px', height: '32px', borderRadius: '50%', background: 'var(--bg-tertiary)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '14px', fontWeight: 600 }}>{m.name?.[0]}</div>
                                    <div>
                                        <div style={{ fontWeight: 500, fontSize: '13px' }}>{m.name}</div>
                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                            {m.provider_type && <span style={{ marginRight: '4px', padding: '1px 4px', borderRadius: '3px', background: 'var(--bg-secondary)', fontSize: '10px' }}>{m.provider_type}</span>}
                                            {m.title || '-'} · {m.department_path || m.department_id || '-'}
                                        </div>
                                    </div>
                                </div>
                            ))}
                            {members.length === 0 && <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-tertiary)' }}>{t('enterprise.org.noMembers')}</div>}
                        </div>
                    </div>
                </div>
            </div>
        );
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {/* SSO status is now derived from per-channel toggles — no global switch */}

            {/* 1. Identity Providers Section */}
            <div className="card" style={{ padding: '0', overflow: 'hidden' }}>
                <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)' }}>
                    <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 600 }}>
                        {t('enterprise.identity.title', '组织与目录同步')}
                    </h3>
                    <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '4px' }}>
                        Configure enterprise directory synchronization and Identity Provider settings.
                    </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {IDP_TYPES.map((idp, index) => {
                        const existingProvider = providers.find((p: any) => p.provider_type === idp.type);
                        const isExpanded = expandedType === idp.type;
                        
                        return (
                            <div key={idp.type} style={{ borderBottom: index < IDP_TYPES.length - 1 ? '1px solid var(--border-subtle)' : 'none' }}>
                                <div 
                                    style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', cursor: 'pointer', background: isExpanded ? 'var(--bg-secondary)' : 'transparent', transition: 'background 0.2s' }}
                                    onClick={() => handleExpand(idp.type, existingProvider)}
                                >
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                        {idp.icon}
                                        <div>
                                            <div style={{ fontWeight: 500, fontSize: '14px' }}>{idp.name}</div>
                                            <div style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>{idp.desc}</div>
                                        </div>
                                    </div>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                                        {existingProvider ? (
                                            <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', gap: '8px' }}>
                                                <span className="badge badge-success" style={{ fontSize: '10px' }}>Active</span>
                                                {existingProvider.last_synced_at && (
                                                    <span style={{ fontSize: '10px', color: 'var(--text-tertiary)' }}>
                                                        Synced: {new Date(existingProvider.last_synced_at).toLocaleDateString()}
                                                    </span>
                                                )}
                                            </div>
                                        ) : (
                                            <span className="badge badge-secondary" style={{ fontSize: '10px' }}>Not configured</span>
                                        )}
                                        <div style={{ color: 'var(--text-tertiary)', transform: isExpanded ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s', fontSize: '12px' }}>
                                            ▼
                                        </div>
                                    </div>
                                </div>

                                {isExpanded && (
                                    <div style={{ padding: '0 20px 20px', background: 'var(--bg-secondary)' }}>
                                        {renderForm(idp.type, existingProvider)}

                                        {/* Per-channel SSO Login URLs & Toggle */}
                                        {['feishu', 'dingtalk', 'wecom', 'oauth2'].includes(idp.type) && (() => {
                                            const ssoEnabled = existingProvider ? !!existingProvider.sso_login_enabled : false;
                                            const slug = tenant?.slug || '';
                                            const domain = tenant?.sso_domain || (slug ? `${slug}.living-agent.ai` : '');
                                            const callbackUrl = domain ? `https://${domain}/api/auth/${idp.type}/callback` : '';

                                            const handleSsoToggle = async () => {
                                                if (!existingProvider) {
                                                    useToastStore.getState().showToast(t('enterprise.identity.saveFirst', '请先保存配置以启用 SSO。'), 'info');
                                                    return;
                                                }
                                                const newVal = !ssoEnabled;
                                                try {
                                                    await fetchJson(`/enterprise/identity-providers/${existingProvider.id}`, {
                                                        method: 'PUT',
                                                        body: JSON.stringify({ sso_login_enabled: newVal }),
                                                    });
                                                    qc.invalidateQueries({ queryKey: ['identity-providers'] });
                                                    // Refresh tenant data so sso_domain updates in UI
                                                    if (tenant?.id) qc.invalidateQueries({ queryKey: ['tenant', tenant.id] });
                                                } catch (e) {
                                                    console.error('Failed to toggle SSO:', e);
                                                }
                                            };

                                            return (
                                                <div style={{ marginTop: '20px', paddingTop: '20px', borderTop: '1px dashed var(--border-subtle)' }}>
                                                    {/* SSO Toggle */}
                                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
                                                        <div>
                                                            <div style={{ fontWeight: 500, fontSize: '13px' }}>{t('enterprise.identity.ssoLoginToggle', 'SSO 登录')}</div>
                                                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                                                                {t('enterprise.identity.ssoLoginToggleHint', '允许用户通过此身份提供者登录。')}
                                                            </div>
                                                        </div>
                                                        <label style={{ position: 'relative', display: 'inline-block', width: '36px', height: '20px', flexShrink: 0 }}>
                                                            <input
                                                                type="checkbox"
                                                                checked={ssoEnabled}
                                                                onChange={handleSsoToggle}
                                                                style={{ opacity: 0, width: 0, height: 0 }}
                                                            />
                                                            <span style={{
                                                                position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
                                                                borderRadius: '20px', cursor: 'pointer',
                                                                background: ssoEnabled ? 'var(--accent-primary)' : 'var(--border-subtle)',
                                                                transition: '0.2s',
                                                                opacity: existingProvider ? 1 : 0.5
                                                            }}>
                                                                <span style={{
                                                                    position: 'absolute', left: ssoEnabled ? '18px' : '2px', top: '2px',
                                                                    width: '16px', height: '16px', borderRadius: '50%',
                                                                    background: '#fff', transition: '0.2s',
                                                                    boxShadow: '0 1px 2px rgba(0,0,0,0.1)'
                                                                }} />
                                                            </span>
                                                        </label>
                                                    </div>

                                                    {/* Callback URL & domain info — always shown so users can configure it before saving */}
                                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                                            {/* Company subdomain */}
                                                            <div>
                                                                <label className="form-label" style={{ fontSize: '11px', marginBottom: '4px', color: 'var(--text-secondary)' }}>
                                                                    {t('enterprise.identity.ssoSubdomain', 'SSO 登录 URL')}
                                                                </label>
                                                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                                    <input
                                                                        className="form-input"
                                                                        readOnly
                                                                        value={domain ? `https://${domain}` : 'Generating...'}
                                                                        style={{ fontSize: '12px', flex: 1, maxWidth: '400px', background: 'var(--bg-primary)', cursor: 'default' }}
                                                                    />
                                                                    <button
                                                                        className="btn btn-ghost btn-sm"
                                                                        style={{ fontSize: '11px' }}
                                                                        onClick={(e) => { 
                                                                            e.preventDefault();
                                                                            copyToClipboard(`https://${domain}`);
                                                                            const el = e.currentTarget;
                                                                            const old = el.textContent;
                                                                            el.textContent = 'Copied✓';
                                                                            setTimeout(() => { el.textContent = old; }, 2000);
                                                                        }}
                                                                    >
                                                                        {t('common.copy', '复制')}
                                                                    </button>
                                                                </div>
                                                                <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                                                    {t('enterprise.identity.ssoSubdomainHint', '将此 URL 分享给您的团队。访问此地址时会显示 SSO 登录按钮。')}
                                                                </div>
                                                            </div>

                                                            {/* Callback URL */}
                                                            <div>
                                                                <label className="form-label" style={{ fontSize: '11px', marginBottom: '4px', color: 'var(--text-secondary)' }}>
                                                                    {t('enterprise.identity.callbackUrl', '回调 URL（粘贴到应用设置中）')}
                                                                </label>
                                                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                                    <input
                                                                        className="form-input"
                                                                        readOnly
                                                                        value={callbackUrl}
                                                                        style={{ fontSize: '12px', flex: 1, maxWidth: '400px', background: 'var(--bg-primary)', cursor: 'default' }}
                                                                    />
                                                                    <button
                                                                        className="btn btn-ghost btn-sm"
                                                                        style={{ fontSize: '11px' }}
                                                                        onClick={(e) => { 
                                                                            e.preventDefault();
                                                                            copyToClipboard(callbackUrl);
                                                                            const el = e.currentTarget;
                                                                            const old = el.textContent;
                                                                            el.textContent = 'Copied✓';
                                                                            setTimeout(() => { el.textContent = old; }, 2000);
                                                                        }}
                                                                    >
                                                                        {t('common.copy', '复制')}
                                                                    </button>
                                                                </div>
                                                                <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                                                    {t('enterprise.identity.callbackUrlHint', '将此 URL 添加为身份提供者应用配置中的 OAuth 回调地址。')}
                                                                </div>
                                                            </div>
                                                        </div>
                                                </div>
                                            );
                                        })()}

                                        {existingProvider && renderOrgBrowser(existingProvider)}
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}
