import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getToken } from '../../stores';
import { useToastStore } from '../../stores/toastStore';

const getCategoryLabels = (t: any): Record<string, string> => ({
    file: t('agent.toolCategories.file'),
    task: t('agent.toolCategories.task'),
    communication: t('agent.toolCategories.communication'),
    search: t('agent.toolCategories.search'),
    aware: t('agent.toolCategories.aware', 'Aware & Triggers'),
    social: t('agent.toolCategories.social', 'Social'),
    code: t('agent.toolCategories.code', 'Code & Execution'),
    discovery: t('agent.toolCategories.discovery', 'Discovery'),
    email: t('agent.toolCategories.email', 'Email'),
    feishu: t('agent.toolCategories.feishu', 'Feishu / Lark'),
    custom: t('agent.toolCategories.custom'),
    general: t('agent.toolCategories.general'),
    agentbay: t('agent.toolCategories.agentbay', 'AgentBay'),
});

const CATEGORY_CONFIG_SCHEMAS: Record<string, any> = {
    agentbay: {
        title: 'AgentBay Settings',
        fields: [
            { key: 'api_key', label: 'API Key (from AgentBay)', type: 'password', placeholder: 'Enter your AgentBay API key' },
            { key: 'os_type', label: 'Cloud Computer OS', type: 'select', default: 'windows', options: [{ value: 'linux', label: 'Linux' }, { value: 'windows', label: 'Windows' }] },
        ]
    },
    atlassian: {
        title: 'Atlassian Connectivity Settings',
        fields: [
            { key: 'api_key', label: 'API Key (Atlassian API Token)', type: 'password', placeholder: 'Enter your Atlassian API key' },
            { key: 'cloud_id', label: 'Cloud ID (Optional)', type: 'text', placeholder: 'e.g. bcc01-abc-123' }
        ]
    }
};

export { getCategoryLabels, CATEGORY_CONFIG_SCHEMAS };

export default function AgentTools({ agentId, canManage = false }: { agentId: string; canManage?: boolean }) {
    const { t } = useTranslation();
    const [tools, setTools] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);
    const [configTool, setConfigTool] = useState<any | null>(null);
    const [configData, setConfigData] = useState<Record<string, any>>({});
    const [configJson, setConfigJson] = useState('');
    const [configSaving, setConfigSaving] = useState(false);
    const [toolTab, setToolTab] = useState<'company' | 'installed'>('company');
    const [deletingToolId, setDeletingToolId] = useState<string | null>(null);
    const [configCategory, setConfigCategory] = useState<string | null>(null);
    const [configGlobalData, setConfigGlobalData] = useState<Record<string, any>>({});

    const SENSITIVE_KEYS_BASE = new Set(['api_key', 'private_key', 'auth_code', 'password', 'secret']);

    const getSensitiveKeys = (schema: any): Set<string> => {
        const keys = new Set(SENSITIVE_KEYS_BASE);
        if (schema?.fields) {
            for (const field of schema.fields) {
                if (field.type === 'password') keys.add(field.key);
            }
        }
        return keys;
    };

    const loadTools = async () => {
        try {
            const token = getToken();
            const res = await fetch(`/api/tools/agents/${encodeURIComponent(agentId!)}/with-config`, {
                headers: { Authorization: `Bearer ${token}` },
            });
            if (res.ok) setTools(await res.json());
            else {
                const res2 = await fetch(`/api/tools/agents/${encodeURIComponent(agentId!)}`, { headers: { Authorization: `Bearer ${token}` } });
                if (res2.ok) setTools(await res2.json());
            }
        } catch (e) { console.error(e); }
        setLoading(false);
    };

    useEffect(() => { loadTools(); }, [agentId]);

    const toggleTool = async (toolId: string, enabled: boolean) => {
        setTools(prev => prev.map(t => t.id === toolId ? { ...t, enabled } : t));
        try {
            const token = getToken();
            await fetch(`/api/tools/agents/${encodeURIComponent(agentId!)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                body: JSON.stringify([{ tool_id: toolId, enabled }]),
            });
        } catch (e) { console.error(e); }
    };

    const openConfig = (tool: any) => {
        setConfigTool(tool);
        const sensitiveKeys = getSensitiveKeys(tool.config_schema);
        const globalCfg = tool.global_config || {};
        const agentCfg = tool.agent_config || {};
        const merged: Record<string, any> = {};
        for (const [k, v] of Object.entries(globalCfg)) {
            if (!sensitiveKeys.has(k)) merged[k] = v;
        }
        Object.assign(merged, agentCfg);
        setConfigData(merged);
        setConfigJson(JSON.stringify(agentCfg, null, 2));
    };

    const openCategoryConfig = async (category: string) => {
        setConfigCategory(category);
        setConfigData({});
        setConfigGlobalData({});
        setConfigSaving(true);
        try {
            const token = getToken();
            const res = await fetch(`/api/tools/agents/${encodeURIComponent(agentId!)}/category-config/${category}`, {
                headers: { Authorization: `Bearer ${token}` },
            });
            if (res.ok) {
                const data = await res.json();
                const globalCfg = data.global_config || {};
                const agentCfg = data.agent_config || {};
                setConfigGlobalData(globalCfg);
                const catSchema = CATEGORY_CONFIG_SCHEMAS[category];
                const sensitiveKeys = getSensitiveKeys(catSchema);
                const merged: Record<string, any> = {};
                for (const [k, v] of Object.entries(globalCfg)) {
                    if (!sensitiveKeys.has(k)) merged[k] = v;
                }
                Object.assign(merged, agentCfg);
                setConfigData(merged);
            }
        } catch (e) { console.error(e); }
        setConfigSaving(false);
    };

    const saveConfig = async () => {
        if (!configTool && !configCategory) return;
        setConfigSaving(true);
        try {
            const token = getToken();

            if (configCategory) {
                const raw = configData;
                const catSchema = CATEGORY_CONFIG_SCHEMAS[configCategory!];
                const sensitiveKeys = getSensitiveKeys(catSchema);
                const payload: Record<string, any> = {};
                for (const [k, v] of Object.entries(raw)) {
                    if (sensitiveKeys.has(k) && (v === '' || v === undefined || v === null)) continue;
                    payload[k] = v;
                }
                await fetch(`/api/tools/agents/${encodeURIComponent(agentId!)}/category-config/${configCategory}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                    body: JSON.stringify({ config: payload }),
                });
                setConfigCategory(null);
            } else {
                const hasSchema = configTool.config_schema?.fields?.length > 0;
                const raw = hasSchema ? configData : JSON.parse(configJson || '{}');
                const sensitiveKeys = getSensitiveKeys(configTool.config_schema);
                const payload: Record<string, any> = {};
                for (const [k, v] of Object.entries(raw)) {
                    if (sensitiveKeys.has(k) && (v === '' || v === undefined || v === null)) continue;
                    payload[k] = v;
                }
                await fetch(`/api/tools/agents/${encodeURIComponent(agentId!)}/tool-config/${configTool.id}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                    body: JSON.stringify({ config: payload }),
                });
                setConfigTool(null);
            }
            loadTools();
        } catch (e) { useToastStore.getState().showToast('保存失败: ' + e, 'error'); }
        setConfigSaving(false);
    };

    if (loading) return <div style={{ color: 'var(--text-tertiary)', padding: '20px' }}>{t('common.loading')}</div>;

    const companyTools = tools.filter(t => t.source === 'builtin' || t.source === 'admin');
    const agentInstalledTools = tools.filter(t => t.source === 'agent');

    const groupByCategory = (toolList: any[]) =>
        toolList.reduce((acc: Record<string, any[]>, t) => {
            const cat = t.category || 'general';
            (acc[cat] = acc[cat] || []).push(t);
            return acc;
        }, {});

    const renderToolGroup = (groupedTools: Record<string, any[]>) =>
        Object.entries(groupedTools).map(([category, catTools]) => (
            <div key={category}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 14px', marginBottom: '8px' }}>
                    <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                        {getCategoryLabels(t)[category] || category}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        {CATEGORY_CONFIG_SCHEMAS[category] && canManage && (
                            <button
                                onClick={() => openCategoryConfig(category)}
                                style={{ background: 'none', border: '1px solid var(--border-subtle)', borderRadius: '6px', padding: '3px 8px', fontSize: '11px', cursor: 'pointer', color: 'var(--text-secondary)' }}
                                title={`配置 ${getCategoryLabels(t)[category] || category}`}
                            >⚙️ Config</button>
                        )}
                        {canManage && (
                            <label style={{ position: 'relative', display: 'inline-block', width: '40px', height: '22px', cursor: 'pointer', flexShrink: 0 }} title={`启用/禁用所有 ${getCategoryLabels(t)[category] || category} 工具`}>
                                <input type="checkbox"
                                    checked={(catTools as any[]).every(t => t.enabled)}
                                    onChange={async (e) => {
                                        const targetEnabled = e.target.checked;
                                        const catToolIds = new Set((catTools as any[]).map(t => t.id));
                                        setTools(prev => prev.map(t => catToolIds.has(t.id) ? { ...t, enabled: targetEnabled } : t));
                                        try {
                                            const token = getToken();
                                            const payload = Array.from(catToolIds).map(id => ({ tool_id: id, enabled: targetEnabled }));
                                            await fetch(`/api/tools/agents/${encodeURIComponent(agentId!)}`, {
                                                method: 'PUT',
                                                headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                                                body: JSON.stringify(payload),
                                            });
                                        } catch (err: any) {
                                            console.error('Bulk update failed', err);
                                            loadTools();
                                        }
                                    }}
                                    style={{ opacity: 0, width: 0, height: 0 }} />
                                <span style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, borderRadius: '22px', background: (catTools as any[]).every(t => t.enabled) ? 'var(--accent-primary)' : 'var(--bg-tertiary)', transition: '0.3s', boxShadow: 'inset 0 1px 3px rgba(0,0,0,0.1)' }}>
                                    <span style={{ position: 'absolute', left: (catTools as any[]).every(t => t.enabled) ? '20px' : '2px', top: '2px', width: '18px', height: '18px', borderRadius: '50%', background: '#fff', transition: '0.3s', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' }} />
                                </span>
                            </label>
                        )}
                    </div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    {(catTools as any[]).map((tool: any) => {
                        const hasConfig = tool.config_schema?.fields?.length > 0 || tool.type === 'mcp';
                        const hasAgentOverride = tool.agent_config && Object.keys(tool.agent_config).length > 0;
                        const isGlobalCategoryConfig = category === 'agentbay' && tool.name === 'agentbay_browser_navigate';
                        return (
                            <div key={tool.id} className="card" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flex: 1, minWidth: 0 }}>
                                    <span style={{ fontSize: '18px' }}>{tool.icon}</span>
                                    <div style={{ minWidth: 0 }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                            <span style={{ fontWeight: 500, fontSize: '13px' }}>{tool.display_name}</span>
                                            {tool.type === 'mcp' && (
                                                <span style={{ fontSize: '10px', background: 'var(--primary)', color: '#fff', borderRadius: '4px', padding: '1px 5px' }}>MCP</span>
                                            )}
                                            {tool.type === 'builtin' && (
                                                <span style={{ fontSize: '10px', background: 'var(--bg-tertiary)', color: 'var(--text-secondary)', borderRadius: '4px', padding: '1px 5px' }}>Built-in</span>
                                            )}
                                            {hasAgentOverride && (
                                                <span style={{ fontSize: '10px', background: 'rgba(99,102,241,0.15)', color: 'var(--accent-color)', borderRadius: '4px', padding: '1px 5px' }}>Configured</span>
                                            )}
                                        </div>
                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            {tool.description}
                                            {tool.mcp_server_name && <span> · {tool.mcp_server_name}</span>}
                                        </div>
                                    </div>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>
                                    {canManage && hasConfig && !isGlobalCategoryConfig && (
                                        <button
                                            onClick={() => openConfig(tool)}
                                            style={{ background: 'none', border: '1px solid var(--border-subtle)', borderRadius: '6px', padding: '3px 8px', fontSize: '11px', cursor: 'pointer', color: 'var(--text-secondary)' }}
                                            title={t('agent.tools.configurePerAgent', '配置单个员工设置')}
                                        >⚙️ Config</button>
                                    )}
                                    {canManage && tool.source === 'agent' && tool.agent_tool_id && (
                                        <button
                                            onClick={async () => {
                                                if (!confirm(t('agent.tools.confirmDelete', `Remove "${tool.display_name}" from this agent?`))) return;
                                                setDeletingToolId(tool.id);
                                                try {
                                                    const token = getToken();
                                                    const res = await fetch(`/api/tools/agent-tool/${tool.agent_tool_id}`, {
                                                        method: 'DELETE',
                                                        headers: { Authorization: `Bearer ${token}` },
                                                    });
                                                    if (res.ok) await loadTools();
                                                    else useToastStore.getState().showToast('删除失败', 'error');
                                                } catch (e) { useToastStore.getState().showToast('删除失败: ' + e, 'error'); }
                                                setDeletingToolId(null);
                                            }}
                                            disabled={deletingToolId === tool.id}
                                            style={{ background: 'none', border: '1px solid var(--border-subtle)', borderRadius: '6px', padding: '3px 8px', fontSize: '11px', cursor: 'pointer', color: 'var(--text-tertiary)', opacity: deletingToolId === tool.id ? 0.5 : 1 }}
                                            title={t('agent.tools.removeTool', 'Remove from agent')}
                                        >{deletingToolId === tool.id ? '...' : '✕'}</button>
                                    )}
                                    {canManage ? (
                                        <label style={{ position: 'relative', display: 'inline-block', width: '40px', height: '22px', cursor: 'pointer', flexShrink: 0 }}>
                                            <input
                                                type="checkbox"
                                                checked={tool.enabled}
                                                onChange={e => toggleTool(tool.id, e.target.checked)}
                                                style={{ opacity: 0, width: 0, height: 0 }}
                                            />
                                            <span style={{
                                                position: 'absolute', inset: 0,
                                                background: tool.enabled ? 'var(--accent-primary)' : 'var(--bg-tertiary)',
                                                borderRadius: '11px', transition: 'background 0.2s',
                                            }}>
                                                <span style={{
                                                    position: 'absolute', left: tool.enabled ? '20px' : '2px', top: '2px',
                                                    width: '18px', height: '18px', background: '#fff',
                                                    borderRadius: '50%', transition: 'left 0.2s',
                                                }} />
                                            </span>
                                        </label>
                                    ) : (
                                        <span style={{ fontSize: '11px', color: tool.enabled ? '#22c55e' : 'var(--text-tertiary)', fontWeight: 500 }}>
                                            {tool.enabled ? t('common.enabled', 'On') : t('common.disabled', 'Off')}
                                        </span>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>
        ));

    const activeTools = toolTab === 'company' ? companyTools : agentInstalledTools;

    return (
        <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                <div style={{ display: 'flex', gap: '4px', padding: '4px', background: 'var(--bg-secondary)', borderRadius: '8px', marginBottom: '12px' }}>
                    <button
                        onClick={() => setToolTab('company')}
                        style={{
                            flex: 1, padding: '7px 12px', border: 'none', borderRadius: '6px', cursor: 'pointer',
                            fontSize: '12px', fontWeight: 600, transition: 'all 0.2s',
                            background: toolTab === 'company' ? 'var(--bg-primary)' : 'transparent',
                            color: toolTab === 'company' ? 'var(--text-primary)' : 'var(--text-tertiary)',
                            boxShadow: toolTab === 'company' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                        }}
                    >
                        {t('agent.tools.companyTools', 'Company Tools')} ({companyTools.length})
                    </button>
                    <button
                        onClick={() => setToolTab('installed')}
                        style={{
                            flex: 1, padding: '7px 12px', border: 'none', borderRadius: '6px', cursor: 'pointer',
                            fontSize: '12px', fontWeight: 600, transition: 'all 0.2s',
                            background: toolTab === 'installed' ? 'var(--bg-primary)' : 'transparent',
                            color: toolTab === 'installed' ? 'var(--text-primary)' : 'var(--text-tertiary)',
                            boxShadow: toolTab === 'installed' ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                        }}
                    >
                        {t('agent.tools.agentInstalled', 'Agent Self-Installed Tools')} ({agentInstalledTools.length})
                    </button>
                </div>

                {activeTools.length > 0 ? (
                    renderToolGroup(groupByCategory(activeTools))
                ) : (
                    <div className="card" style={{ textAlign: 'center', padding: '30px', color: 'var(--text-tertiary)' }}>
                        {toolTab === 'installed' ? t('agent.tools.noInstalled', 'No agent-installed tools yet') : t('agent.tools.noCompany', 'No company-configured tools')}
                    </div>
                )}
            </div>
            {tools.length === 0 && (
                <div className="card" style={{ textAlign: 'center', padding: '30px', color: 'var(--text-tertiary)' }}>
                    {t('common.noData')}
                </div>
            )}

            {/* Tool Config Modal */}
            {(configTool || configCategory) && (() => {
                const target = configTool || CATEGORY_CONFIG_SCHEMAS[configCategory!];
                const fields = configTool ? (configTool.config_schema?.fields || []) : (target.fields || []);
                const title = configTool ? configTool.display_name : target.title;
                const isCat = !!configCategory;
                return (
                    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.55)', zIndex: 2000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                        onClick={() => { setConfigTool(null); setConfigCategory(null); }}>
                        <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', width: '480px', maxWidth: '95vw', maxHeight: '80vh', overflow: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                                <div>
                                    <h3 style={{ margin: 0 }}>⚙️ {title}</h3>
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{isCat ? '共享分类配置（影响该分类下所有工具）' : '员工级配置（覆盖全局默认值）'}</div>
                                </div>
                                <button onClick={() => { setConfigTool(null); setConfigCategory(null); }} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)' }}>✕</button>
                            </div>

                            {fields.length > 0 ? (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                    {fields
                                        .filter((field: any) => {
                                            if (!field.depends_on) return true;
                                            return Object.entries(field.depends_on).every(([depKey, depVals]: [string, any]) =>
                                                (depVals as string[]).includes(configData[depKey] ?? '')
                                            );
                                        })
                                        .map((field: any) => {
                                            const userFromStore = require('../../stores').useAuthStore.getState().user;
                                            const currentUserRole = userFromStore?.role;
                                            const isReadOnly = field.read_only_for_roles?.includes(currentUserRole);
                                            return (
                                                <div key={field.key}>
                                                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 500, marginBottom: '4px' }}>
                                                        {field.label}
                                                        {isReadOnly && <span style={{ fontWeight: 400, color: 'var(--text-tertiary)', marginLeft: '4px' }}>(Admin only)</span>}
                                                        {(() => {
                                                            const globalVal = configTool?.global_config?.[field.key] ?? configGlobalData?.[field.key];
                                                            if (!globalVal) return null;
                                                            return (
                                                                <span style={{ fontWeight: 400, color: 'var(--accent-primary)', marginLeft: '4px', fontSize: '11px' }}>
                                                                    (company: {String(globalVal).slice(0, 20)}{String(globalVal).length > 20 ? '\u2026' : ''})
                                                                </span>
                                                            );
                                                        })()}
                                                    </label>
                                                    {field.type === 'checkbox' ? (
                                                        <label style={{ position: 'relative', display: 'inline-block', width: '40px', height: '22px', cursor: isReadOnly ? 'not-allowed' : 'pointer' }}>
                                                            <input
                                                                type="checkbox"
                                                                checked={configData[field.key] ?? field.default ?? false}
                                                                disabled={isReadOnly}
                                                                onChange={e => setConfigData(p => ({ ...p, [field.key]: e.target.checked }))}
                                                                style={{ opacity: 0, width: 0, height: 0 }}
                                                            />
                                                            <span style={{
                                                                position: 'absolute', inset: 0,
                                                                background: (configData[field.key] ?? field.default) ? '#22c55e' : 'var(--bg-tertiary)',
                                                                borderRadius: '11px', transition: 'background 0.2s', opacity: isReadOnly ? 0.6 : 1,
                                                            }}>
                                                                <span style={{
                                                                    position: 'absolute', left: (configData[field.key] ?? field.default) ? '20px' : '2px', top: '2px',
                                                                    width: '18px', height: '18px', background: '#fff',
                                                                    borderRadius: '50%', transition: 'left 0.2s',
                                                                }} />
                                                            </span>
                                                        </label>
                                                    ) : field.type === 'password' ? (
                                                        <input type="password" autoComplete="new-password" className="form-input"
                                                            value={configData[field.key] ?? ''}
                                                            placeholder={(() => {
                                                                const globalVal = configTool?.global_config?.[field.key] ?? configGlobalData?.[field.key];
                                                                return globalVal ? `Using company key (${globalVal})` : (field.placeholder || t('admin.leaveBlankDefault', 'Leave blank to use global default'));
                                                            })()}
                                                            onChange={e => setConfigData(p => ({ ...p, [field.key]: e.target.value }))} />
                                                    ) : field.type === 'select' ? (
                                                        <select className="form-input" value={configData[field.key] ?? field.default ?? ''}
                                                            onChange={e => setConfigData(p => ({ ...p, [field.key]: e.target.value }))}>
                                                            {(field.options || []).map((o: any) => <option key={o.value} value={o.value}>{o.label}</option>)}
                                                        </select>
                                                    ) : field.type === 'number' ? (
                                                        <input type="number" className="form-input" value={configData[field.key] ?? field.default ?? ''} placeholder={field.placeholder || ''} min={field.min} max={field.max} onChange={e => setConfigData(p => ({ ...p, [field.key]: e.target.value ? Number(e.target.value) : '' }))} />
                                                    ) : (
                                                        <input type="text" className="form-input" value={configData[field.key] ?? ''} placeholder={field.placeholder || 'Leave blank to use global default'} onChange={e => setConfigData(p => ({ ...p, [field.key]: e.target.value }))} />
                                                    )}
                                                </div>
                                            );
                                        })}
                                </div>
                            ) : (
                                <div>
                                    <label style={{ display: 'block', fontSize: '12px', fontWeight: 500, marginBottom: '4px' }}>配置 JSON (员工覆盖)</label>
                                    <textarea
                                        className="form-input"
                                        value={configJson}
                                        onChange={e => setConfigJson(e.target.value)}
                                        style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', minHeight: '120px', resize: 'vertical' }}
                                        placeholder='{}'
                                    />
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                        Global default: <code style={{ fontSize: '10px' }}>{JSON.stringify(configTool?.global_config || {}).slice(0, 80)}</code>
                                    </div>
                                </div>
                            )}

                            <div style={{ display: 'flex', gap: '8px', marginTop: '16px', justifyContent: 'flex-end' }}>
                                {configTool && configTool.agent_config && Object.keys(configTool.agent_config || {}).length > 0 && (
                                    <button className="btn btn-ghost" style={{ color: 'var(--error)', marginRight: 'auto' }} onClick={async () => {
                                        const token = getToken();
                                        await fetch(`/api/tools/agents/${encodeURIComponent(agentId!)}/tool-config/${configTool.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify({ config: {} }) });
                                        setConfigTool(null); loadTools();
                                    }}>重置为全局默认</button>
                                )}
                                <button className="btn btn-secondary" onClick={() => { setConfigTool(null); setConfigCategory(null); }}>取消</button>
                                <button className="btn btn-primary" onClick={saveConfig} disabled={configSaving}>{configSaving ? t('common.saving', 'Saving…') : t('common.save', 'Save')}</button>
                            </div>
                        </div>
                    </div>
                );
            })()}
        </>
    );
}
