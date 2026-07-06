import { useState, useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { enterpriseApi, creditApi, fetchJson, uploadFileWithProgress } from '../../services/api';
import FileBrowser from '../../components/FileBrowser';
import type { FileBrowserApi } from '../../components/FileBrowser';
import { saveAccentColor, getSavedAccentColor, resetAccentColor, PRESET_COLORS } from '../../utils/theme';
import { useToastStore } from '../../stores/toastStore';
import { request } from '../../services/apiBase';

// ─── Theme Color Picker ────────────────────────────
export function ThemeColorPicker() {
    const { t } = useTranslation();
    const [currentColor, setCurrentColor] = useState(getSavedAccentColor() || '');
    const [customHex, setCustomHex] = useState('');

    const apply = (hex: string) => {
        setCurrentColor(hex);
        saveAccentColor(hex);
    };

    const handleReset = () => {
        setCurrentColor('');
        setCustomHex('');
        resetAccentColor();
    };

    const handleCustom = () => {
        const hex = customHex.trim();
        if (/^#[0-9a-fA-F]{6}$/.test(hex)) {
            apply(hex);
        }
    };

    return (
        <div className="card" style={{ marginTop: '16px', marginBottom: '16px' }}>
            <h4 style={{ marginBottom: '12px' }}>{t('enterprise.config.themeColor')}</h4>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '12px' }}>
                {PRESET_COLORS.map(c => (
                    <div
                        key={c.hex}
                        onClick={() => apply(c.hex)}
                        title={c.name}
                        style={{
                            width: '32px', height: '32px', borderRadius: '8px',
                            background: c.hex, cursor: 'pointer',
                            border: currentColor === c.hex ? '2px solid var(--text-primary)' : '2px solid transparent',
                            outline: currentColor === c.hex ? '2px solid var(--bg-primary)' : 'none',
                            transition: 'all 120ms ease',
                        }}
                    />
                ))}
            </div>
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                <input
                    className="input"
                    value={customHex}
                    onChange={e => setCustomHex(e.target.value)}
                    placeholder="#hex"
                    style={{ width: '120px', fontSize: '13px', fontFamily: 'var(--font-mono)' }}
                    onKeyDown={e => e.key === 'Enter' && handleCustom()}
                />
                <button className="btn btn-secondary" style={{ fontSize: '12px' }} onClick={handleCustom}>Apply</button>
                {currentColor && (
                    <button className="btn btn-ghost" style={{ fontSize: '12px', color: 'var(--text-tertiary)' }} onClick={handleReset}>Reset</button>
                )}
                {currentColor && (
                    <div style={{ width: '20px', height: '20px', borderRadius: '4px', background: currentColor, border: '1px solid var(--border-default)' }} />
                )}
            </div>
        </div>
    );
}


// Preset common models per provider (used by main component)
export const PRESET_MODELS: Record<string, string[]> = {
    'openai': ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'gpt-3.5-turbo', 'o1-preview', 'o1-mini'],
    'anthropic': ['claude-3-5-sonnet-20241022', 'claude-3-5-sonnet-20240620', 'claude-3-5-haiku-20241022', 'claude-3-opus-20240229'],
    'google': ['gemini-1.5-pro', 'gemini-1.5-flash', 'gemini-2.0-flash'],
    'deepseek': ['deepseek-chat', 'deepseek-reasoner'],
    'ollama': ['llama3.1', 'llama3.2', 'qwen2.5', 'mistral', 'gemma2'],
    'azure': ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo'],
};

// ─── Credit Overview ───────────────────────────────
export function CreditOverview() {
    const { t } = useTranslation();
    const { data: balance, isLoading: loadingBalance } = useQuery({
        queryKey: ['credit-balance'],
        queryFn: () => creditApi.getBalance(),
        retry: false,
    });
    const { data: creditStats, isLoading: loadingStats } = useQuery({
        queryKey: ['credit-stats'],
        queryFn: () => creditApi.getStats(),
        retry: false,
    });
    const { data: leaderboard, isLoading: loadingLeaderboard } = useQuery({
        queryKey: ['credit-leaderboard'],
        queryFn: () => creditApi.getLeaderboard(),
        retry: false,
    });

    if (loadingBalance && loadingStats) {
        return <div style={{ textAlign: 'center', padding: '20px', color: 'var(--text-tertiary)' }}>{t('common.loading')}</div>;
    }

    const balanceValue = (balance as any)?.balance ?? (balance as any)?.credits ?? '-';
    const totalEarned = (creditStats as any)?.total_earned ?? (creditStats as any)?.totalEarned ?? '-';
    const totalSpent = (creditStats as any)?.total_spent ?? (creditStats as any)?.totalSpent ?? '-';

    return (
        <div>
            {/* Balance cards */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: '12px', marginBottom: '20px' }}>
                {[
                    { label: t('enterprise.quotas.currentBalance', '当前余额'), value: balanceValue, bg: 'rgba(59,130,246,0.08)' },
                    { label: t('enterprise.quotas.totalEarned', '累计获得'), value: totalEarned, bg: 'rgba(34,197,94,0.08)' },
                    { label: t('enterprise.quotas.totalSpent', '累计消耗'), value: totalSpent, bg: 'rgba(245,158,11,0.08)' },
                ].map(s => (
                    <div key={s.label} className="card" style={{ padding: '12px', background: s.bg }}>
                        <div style={{ fontSize: '20px', fontWeight: 700 }}>{s.value}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>{s.label}</div>
                    </div>
                ))}
            </div>

            {/* Leaderboard */}
            {leaderboard && Array.isArray(leaderboard) && leaderboard.length > 0 && (
                <div>
                    <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '8px' }}>
                        {t('enterprise.quotas.leaderboard', '积分排行')}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        {leaderboard.slice(0, 10).map((item: any, idx: number) => (
                            <div key={item.id || idx} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 12px', borderRadius: '6px', background: 'var(--bg-tertiary)' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <span style={{ fontSize: '11px', fontWeight: 700, color: idx < 3 ? 'var(--accent-primary)' : 'var(--text-tertiary)', width: '16px' }}>#{idx + 1}</span>
                                    <span style={{ fontSize: '12px' }}>{item.name || item.username || item.id?.slice(0, 8) || '-'}</span>
                                </div>
                                <span style={{ fontSize: '12px', fontWeight: 600 }}>{item.credits ?? item.balance ?? '-'}</span>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

// ─── Enterprise KB Browser ─────────────────────────
export function EnterpriseKBBrowser({ onRefresh }: { onRefresh: () => void; refreshKey: number }) {
    const kbAdapter: FileBrowserApi = {
        list: (path) => enterpriseApi.kbFiles(path),
        read: (path) => enterpriseApi.kbRead(path),
        write: (path, content) => enterpriseApi.kbWrite(path, content),
        delete: (path) => enterpriseApi.kbDelete(path),
        upload: (file, path) => enterpriseApi.kbUpload(file, path),
    };
    return <FileBrowser api={kbAdapter} features={{ upload: true, newFolder: true, edit: true, delete: true, directoryNavigation: true }} onRefresh={onRefresh} />;
}

// ─── Windows Automation Nodes ──────────────────────
export function WindowsAutomationNodes() {
    const { t } = useTranslation();
    const [nodes, setNodes] = useState<any[]>([]);
    const [loading, setLoading] = useState(true);

    const loadNodes = async () => {
        try {
            const data = await fetchJson<any>('/windows-automation/nodes');
            setNodes(data.nodes || []);
        } catch { setNodes([]); }
        setLoading(false);
    };

    useEffect(() => { loadNodes(); }, []);

    const toggleEnabled = async (nodeId: string, enabled: boolean) => {
        await fetchJson(`/windows-automation/nodes/${nodeId}`, {
            method: 'PUT',
            body: JSON.stringify({ enabled: !enabled }),
        });
        loadNodes();
    };

    const deleteNode = async (nodeId: string) => {
        if (!confirm(t('enterprise.tools.deleteConfirm', '确定要删除此节点？'))) return;
        await fetchJson(`/windows-automation/nodes/${nodeId}`, { method: 'DELETE' });
        loadNodes();
    };

    const testConnection = async (nodeId: string) => {
        try {
            const data = await fetchJson<any>(`/windows-automation/nodes/${nodeId}/status`);
            useToastStore.getState().showToast(data.status === 'online' ? 'Online' : 'Offline', data.status === 'online' ? 'success' : 'error');
        } catch { useToastStore.getState().showToast('Connection failed', 'error'); }
    };

    const onlineCount = nodes.filter((n: any) => n.status === 'online').length;

    return (
        <div style={{ marginTop: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                <h3 style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    🖥️ {t('enterprise.windowsAutomation.title', 'Windows 自动化节点')}
                    <span style={{ fontSize: '12px', color: 'var(--text-tertiary)', fontWeight: 400 }}>
                        ({onlineCount}/{nodes.length} {t('enterprise.windowsAutomation.online', 'online')})
                    </span>
                </h3>
            </div>
            <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                {t('enterprise.windowsAutomation.hint', '运行了 windows_automation 服务的客户端计算机将自动注册到此处。在客户端 config.json 中配置 registration.server_url。')}
            </p>
            {loading ? <div style={{ textAlign: 'center', padding: '20px', color: 'var(--text-tertiary)' }}>Loading...</div> : (
                nodes.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                        {t('enterprise.windowsAutomation.noNodes', '暂无注册节点。请在客户端计算机上部署 windows_automation。')}
                    </div>
                ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                        {nodes.map((node: any) => (
                            <div key={node.node_id} className="card" style={{ padding: '12px 16px' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: node.status === 'online' ? '#22c55e' : '#ef4444', flexShrink: 0 }} />
                                            <span style={{ fontWeight: 500, fontSize: '13px' }}>{node.description || node.hostname || node.node_id}</span>
                                            {!node.enabled && <span style={{ fontSize: '10px', background: 'var(--bg-tertiary)', borderRadius: '4px', padding: '1px 5px' }}>Disabled</span>}
                                        </div>
                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px', display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                                            <span>IP: {node.ip_address}:{node.port}</span>
                                            {node.hostname && <span>Host: {node.hostname}</span>}
                                            {node.cpu_count && <span>CPU: {node.cpu_count}</span>}
                                            {node.memory_gb && <span>RAM: {node.memory_gb}GB</span>}
                                            {node.last_heartbeat && <span>Last: {new Date(node.last_heartbeat).toLocaleString()}</span>}
                                        </div>
                                    </div>
                                    <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }}>
                                        <button className="btn btn-ghost" style={{ fontSize: '11px' }} onClick={() => testConnection(node.node_id)}>🔍 Test</button>
                                        <button className="btn btn-ghost" style={{ fontSize: '11px' }} onClick={() => toggleEnabled(node.node_id, node.enabled)}>
                                            {node.enabled ? '⛔ Disable' : '✅ Enable'}
                                        </button>
                                        <button className="btn btn-ghost" style={{ fontSize: '11px', color: 'var(--error)' }} onClick={() => deleteNode(node.node_id)}>🗑️</button>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )
            )}
        </div>
    );
}

// ─── Company Logo Uploader ───────────────────────────
export function CompanyLogoUploader() {
    const { t } = useTranslation();
    const qc = useQueryClient();
    const tenantId = localStorage.getItem('current_tenant_id') || '';
    const [uploading, setUploading] = useState(false);
    const [logoUrl, setLogoUrl] = useState<string | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (!tenantId) return;
        fetchJson<any>('/system/config')
            .then(d => { if (d?.logo_url) setLogoUrl(d.logo_url); })
            .catch(() => { });
    }, [tenantId]);

    const handleUpload = async (file: File) => {
        if (file.size > 2 * 1024 * 1024) {
            useToastStore.getState().showToast(t('enterprise.logo.sizeLimit', 'Logo文件不能超过2MB'), 'error');
            return;
        }
        if (!['image/png', 'image/jpeg', 'image/svg+xml'].includes(file.type)) {
            useToastStore.getState().showToast(t('enterprise.logo.formatLimit', '仅支持 PNG/JPG/SVG 格式'), 'error');
            return;
        }
        setUploading(true);
        try {
            const { promise } = uploadFileWithProgress('/enterprise/logo', file);
            const data: any = await promise;
            setLogoUrl(data?.logo_url || data?.data?.logo_url || URL.createObjectURL(file));
            qc.invalidateQueries({ queryKey: ['system-config'] });
            useToastStore.getState().showToast(t('enterprise.logo.uploaded', 'Logo已上传'));
        } catch {
            // Fallback: if backend endpoint not ready, show preview locally
            setLogoUrl(URL.createObjectURL(file));
            useToastStore.getState().showToast(t('enterprise.logo.uploaded', 'Logo已上传（本地预览，后端端点待实现）'));
        }
        setUploading(false);
    };

    return (
        <div style={{ marginBottom: '24px' }}>
            <h3 style={{ marginBottom: '8px' }}>{t('enterprise.logo.title', '公司Logo')}</h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                <div
                    onClick={() => fileInputRef.current?.click()}
                    style={{
                        width: '72px', height: '72px', borderRadius: '12px', cursor: 'pointer',
                        border: '2px dashed var(--border-default)', display: 'flex', alignItems: 'center', justifyContent: 'center',
                        background: logoUrl ? 'var(--bg-tertiary)' : 'var(--bg-secondary)', overflow: 'hidden',
                        transition: 'border-color 0.15s',
                    }}
                    onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--accent-primary)')}
                    onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border-default)')}
                >
                    {logoUrl ? (
                        <img src={logoUrl} alt="Logo" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
                    ) : (
                        <span style={{ fontSize: '24px', color: 'var(--text-tertiary)' }}>+</span>
                    )}
                </div>
                <div>
                    <button className="btn btn-secondary" style={{ fontSize: '12px', padding: '4px 12px' }}
                        disabled={uploading} onClick={() => fileInputRef.current?.click()}>
                        {uploading ? t('common.loading') : t('enterprise.logo.upload', '上传Logo')}
                    </button>
                    <p style={{ fontSize: '11px', color: 'var(--text-tertiary)', margin: '4px 0 0' }}>
                        {t('enterprise.logo.hint', 'PNG/JPG/SVG，最大2MB')}
                    </p>
                </div>
                <input ref={fileInputRef} type="file" accept=".png,.jpg,.jpeg,.svg" style={{ display: 'none' }}
                    onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(f); e.target.value = ''; }} />
            </div>
        </div>
    );
}

// ─── Company Name Editor ───────────────────────────
export function CompanyNameEditor() {
    const { t } = useTranslation();
    const qc = useQueryClient();
    const tenantId = localStorage.getItem('current_tenant_id') || '';
    const [name, setName] = useState('');
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    useEffect(() => {
        if (!tenantId) return;
        fetchJson<any>(`/tenants/${tenantId}`)
            .then(d => { if (d?.name) setName(d.name); })
            .catch(() => { });
    }, [tenantId]);

    const handleSave = async () => {
        if (!tenantId || !name.trim()) return;
        setSaving(true);
        try {
            await fetchJson(`/tenants/${tenantId}`, {
                method: 'PUT', body: JSON.stringify({ name: name.trim() }),
            });
            qc.invalidateQueries({ queryKey: ['tenants'] });
            setSaved(true);
            setTimeout(() => setSaved(false), 2000);
        } catch (e) { }
        setSaving(false);
    };

    return (
        <div className="card" style={{ padding: '16px', marginBottom: '24px' }}>
            <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                <input
                    className="form-input"
                    value={name}
                    onChange={e => setName(e.target.value)}
                    placeholder={t('enterprise.companyName.placeholder', '输入公司名称')}
                    style={{ flex: 1, fontSize: '14px' }}
                    onKeyDown={e => e.key === 'Enter' && handleSave()}
                />
                <button className="btn btn-primary" onClick={handleSave} disabled={saving || !name.trim()}>
                    {saving ? t('common.loading') : t('common.save', '保存')}
                </button>
                {saved && <span style={{ color: 'var(--success)', fontSize: '12px' }}>✅</span>}
            </div>
        </div>
    );
}


// ─── Company Timezone Editor ───────────────────────
const COMMON_TIMEZONES = [
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
];

export function CompanyTimezoneEditor() {
    const { t } = useTranslation();
    const tenantId = localStorage.getItem('current_tenant_id') || '';
    const [timezone, setTimezone] = useState('UTC');
    const [saving, setSaving] = useState(false);
    const [saved, setSaved] = useState(false);

    useEffect(() => {
        if (!tenantId) return;
        fetchJson<any>(`/tenants/${tenantId}`)
            .then(d => { if (d?.timezone) setTimezone(d.timezone); })
            .catch(() => { });
    }, [tenantId]);

    const handleSave = async (tz: string) => {
        if (!tenantId) return;
        setTimezone(tz);
        setSaving(true);
        try {
            await fetchJson(`/tenants/${tenantId}`, {
                method: 'PUT', body: JSON.stringify({ timezone: tz }),
            });
            setSaved(true);
            setTimeout(() => setSaved(false), 2000);
        } catch (e) { }
        setSaving(false);
    };

    return (
        <div className="card" style={{ padding: '16px', marginBottom: '24px' }}>
            <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 500, fontSize: '13px', marginBottom: '4px' }}>🌐 {t('enterprise.timezone.title', '公司时区')}</div>
                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                        {t('enterprise.timezone.description', '默认时区，适用于所有数字员工。员工可单独覆盖。')}
                    </div>
                </div>
                <select
                    className="form-input"
                    value={timezone}
                    onChange={e => handleSave(e.target.value)}
                    style={{ width: '220px', fontSize: '13px' }}
                    disabled={saving}
                >
                    {COMMON_TIMEZONES.map(tz => (
                        <option key={tz.value} value={tz.value}>{tz.label}</option>
                    ))}
                </select>
                {saved && <span style={{ color: 'var(--success)', fontSize: '12px' }}>✅</span>}
            </div>
        </div>
    );
}


// ── Broadcast Section ──────────────────────────
export function BroadcastSection() {
    const { t } = useTranslation();
    const [title, setTitle] = useState('');
    const [body, setBody] = useState('');
    const [sendEmail, setSendEmail] = useState(false);
    const [sending, setSending] = useState(false);
    const [result, setResult] = useState<{ users: number; agents: number; emails: number } | null>(null);

    const handleSend = async () => {
        if (!title.trim()) return;
        setSending(true);
        setResult(null);
        try {
            const data = await request<any>('/notifications/broadcast', {
                method: 'POST',
                body: JSON.stringify({ title: title.trim(), body: body.trim(), send_email: sendEmail }),
            });
            setResult({
                users: data.users_notified,
                agents: data.agents_notified,
                emails: data.emails_sent || 0,
            });
            setTitle('');
            setBody('');
            setSendEmail(false);
        } catch (e: any) {
            useToastStore.getState().showToast(e.message || 'Failed', 'error');
        }
        setSending(false);
    };

    return (
        <div style={{ marginTop: '24px', marginBottom: '24px' }}>
            <h3 style={{ marginBottom: '4px' }}>{t('enterprise.broadcast.title', '广播通知')}</h3>
            <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                {t('enterprise.broadcast.description', '向公司所有用户和员工发送通知。')}
            </p>
            <div className="card" style={{ padding: '16px' }}>
                <input
                    className="form-input"
                    placeholder={t('enterprise.broadcast.titlePlaceholder', '通知标题')}
                    value={title}
                    onChange={e => setTitle(e.target.value)}
                    maxLength={200}
                    style={{ marginBottom: '8px', fontSize: '13px' }}
                />
                <textarea
                    className="form-input"
                    placeholder={t('enterprise.broadcast.bodyPlaceholder', '可选详情...')}
                    value={body}
                    onChange={e => setBody(e.target.value)}
                    maxLength={1000}
                    rows={3}
                    style={{ resize: 'vertical', fontSize: '13px', marginBottom: '12px' }}
                />
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px', fontSize: '13px' }}>
                    <input
                        type="checkbox"
                        checked={sendEmail}
                        onChange={e => setSendEmail(e.target.checked)}
                    />
                    <span>{t('enterprise.broadcast.sendEmail', '同时发送邮件给已配置邮箱的用户')}</span>
                </label>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <button className="btn btn-primary" onClick={handleSend} disabled={sending || !title.trim()}>
                        {sending ? t('common.loading') : t('enterprise.broadcast.send', '发送广播')}
                    </button>
                    {result && (
                        <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                            {t(
                                'enterprise.broadcast.sentWithEmail',
                                `Sent to ${result.users} users, ${result.agents} agents, and ${result.emails} email recipients`,
                                { users: result.users, agents: result.agents, emails: result.emails },
                            )}
                        </span>
                    )}
                </div>
            </div>
        </div>
    );
}
