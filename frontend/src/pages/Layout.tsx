import { useState, useEffect, useRef } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores';
import { useIdleTimeout } from '../hooks/useIdleTimeout';
import { agentApi } from '../services/api';
import { request } from '../services/apiBase';
import type { User } from '../types';
import {
    IconHome,
    IconPlus,
    IconSettings,
    IconUser,
    IconSun,
    IconMoon,
    IconLogout,
    IconWorld,
    IconChevronsLeft,
    IconChevronsRight,
    IconBell,
    IconBuildingMonument,
    IconBuilding,
    IconChevronUp,
    IconCode,
    IconClipboard,
    IconTrendingUp,
    IconUsers,
    IconCoin,
    IconHeadset,
    IconScale,
    IconSettingsAutomation,
    IconFolder,
    IconChecklist
} from '@tabler/icons-react';
import { DEPARTMENTS, type DepartmentCode } from '../types';
import { useAppStore } from '../stores';
import { fetchJson } from '../services/api';
import { t } from 'i18next';

/* ────── Tabler Icons ────── */
const SidebarIcons = {
    home: <IconHome size={16} stroke={1.5} />,
    plus: <IconPlus size={16} stroke={1.5} />,
    settings: <IconSettings size={16} stroke={1.5} />,
    user: <IconUser size={16} stroke={1.5} />,
    sun: <IconSun size={16} stroke={1.5} />,
    moon: <IconMoon size={16} stroke={1.5} />,
    logout: <IconLogout size={16} stroke={1.5} />,
    globe: <IconWorld size={16} stroke={1.5} />,
    collapse: <IconChevronsLeft size={16} stroke={1.5} />,
    expand: <IconChevronsRight size={16} stroke={1.5} />,
    bell: <IconBell size={16} stroke={1.5} />,
};

/* Compute display badge status for an agent */
const getAgentBadgeStatus = (agent: any): string | null => {
    if (agent.status === 'error') return 'error';
    if (agent.status === 'creating') return 'creating';
    // OpenClaw disconnected detection: 60 min timeout
    if (agent.agent_type === 'openclaw' && agent.status === 'running' && agent.openclaw_last_seen) {
        const elapsed = Date.now() - new Date(agent.openclaw_last_seen).getTime();
        if (elapsed > 60 * 60 * 1000) return 'disconnected';
    }
    // idle / running / stopped → no badge
    return null;
};

/* ────── Account Settings Modal ────── */
function AccountSettingsModal({ user, onClose, isChinese }: { user: any; onClose: () => void; isChinese: boolean }) {
    const { setUser } = useAuthStore();
    const [username, setUsername] = useState(user?.username || '');
    const [email, setEmail] = useState(user?.email || '');
    const [displayName, setDisplayName] = useState(user?.display_name || '');
    const [oldPassword, setOldPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [saving, setSaving] = useState(false);
    const [msg, setMsg] = useState('');
    const [msgType, setMsgType] = useState<'success' | 'error'>('success');

    const showMsg = (text: string, type: 'success' | 'error' = 'success') => {
        setMsg(text); setMsgType(type); setTimeout(() => setMsg(''), 3000);
    };

    const handleSaveProfile = async () => {
        setSaving(true);
        try {
            const body: any = {};
            if (username !== user?.username) body.username = username;
            if (email !== user?.email) body.email = email;
            if (displayName !== user?.display_name) body.display_name = displayName;
            if (Object.keys(body).length === 0) { showMsg(t('layout.noChanges'), 'error'); setSaving(false); return; }
            const updated = await request<User>('/auth/me', {
                method: 'PATCH',
                body: JSON.stringify(body),
            });
            setUser(updated);
            showMsg(t('layout.profileUpdated'));
        } catch (e: any) { showMsg(e.message || 'Failed', 'error'); }
        setSaving(false);
    };

    const handleChangePassword = async () => {
        if (!oldPassword || !newPassword) { showMsg(t('layout.fillAllPasswordFields'), 'error'); return; }
        if (newPassword.length < 6) { showMsg(t('layout.min6Characters'), 'error'); return; }
        if (newPassword !== confirmPassword) { showMsg(t('layout.passwordsDoNotMatch'), 'error'); return; }
        setSaving(true);
        try {
            await request('/auth/me/password', {
                method: 'PUT',
                body: JSON.stringify({ old_password: oldPassword, new_password: newPassword }),
            });
            showMsg(t('layout.passwordChanged'));
            setOldPassword(''); setNewPassword(''); setConfirmPassword('');
        } catch (e: any) { showMsg(e.message || 'Failed', 'error'); }
        setSaving(false);
    };

    const inputStyle = { width: '100%', fontSize: '13px' };
    const labelStyle = { display: 'block' as const, fontSize: '12px', fontWeight: 500, marginBottom: '4px', color: 'var(--text-secondary)' };

    return (
        <div style={{ position: 'fixed', inset: 0, zIndex: 10000, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={onClose}>
            <div style={{ background: 'var(--bg-primary)', borderRadius: '12px', border: '1px solid var(--border-subtle)', width: '420px', maxHeight: '90vh', overflow: 'auto', padding: '24px', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }} onClick={e => e.stopPropagation()}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                    <h3 style={{ margin: 0 }}>{t('layout.accountSettings')}</h3>
                    <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-tertiary)', fontSize: '18px', cursor: 'pointer', padding: '4px 8px' }}>×</button>
                </div>
                {msg && <div style={{ padding: '8px 12px', borderRadius: '6px', fontSize: '12px', marginBottom: '16px', background: msgType === 'success' ? 'rgba(0,180,120,0.12)' : 'rgba(255,80,80,0.12)', color: msgType === 'success' ? 'var(--success)' : 'var(--error)' }}>{msg}</div>}
                {/* Profile */}
                <h4 style={{ margin: '0 0 12px', fontSize: '13px', color: 'var(--text-secondary)' }}>{t('layout.profile')}</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '20px' }}>
                    <div><label style={labelStyle}>{t('layout.username')}</label><input className="form-input" value={username} onChange={e => setUsername(e.target.value)} style={inputStyle} /></div>
                    <div><label style={labelStyle}>{t('layout.email')}</label><input className="form-input" type="email" value={email} onChange={e => setEmail(e.target.value)} style={inputStyle} /></div>
                    <div><label style={labelStyle}>{t('layout.displayName')}</label><input className="form-input" value={displayName} onChange={e => setDisplayName(e.target.value)} style={inputStyle} /></div>
                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}><button className="btn btn-primary" onClick={handleSaveProfile} disabled={saving} style={{ padding: '6px 16px', fontSize: '12px' }}>{saving ? '...' : t('layout.save')}</button></div>
                </div>
                <div style={{ borderTop: '1px solid var(--border-subtle)', marginBottom: '20px' }} />
                {/* Password */}
                <h4 style={{ margin: '0 0 12px', fontSize: '13px', color: 'var(--text-secondary)' }}>{t('layout.changePassword')}</h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    <div><label style={labelStyle}>{t('layout.currentPassword')}</label><input className="form-input" type="password" value={oldPassword} onChange={e => setOldPassword(e.target.value)} style={inputStyle} /></div>
                    <div><label style={labelStyle}>{t('layout.newPassword')}</label><input className="form-input" type="password" value={newPassword} onChange={e => setNewPassword(e.target.value)} placeholder={t('layout.newPasswordPlaceholder')} style={inputStyle} /></div>
                    <div><label style={labelStyle}>{t('layout.confirmNewPassword')}</label><input className="form-input" type="password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} style={inputStyle} /></div>
                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}><button className="btn btn-primary" onClick={handleChangePassword} disabled={saving} style={{ padding: '6px 16px', fontSize: '12px' }}>{saving ? '...' : t('layout.changePassword')}</button></div>
                </div>
            </div>
        </div>
    );
}

/* ────── Version Display (runtime) ────── */
function VersionDisplay() {
    const [info, setInfo] = useState<{ version?: string; commit?: string }>({});
    useEffect(() => {
        request<{ version?: string; commit?: string }>('/version').then(setInfo).catch(() => {});
    }, []);
    if (!info.version) return null;
    return (
        <div style={{ textAlign: 'center', fontSize: '10px', color: 'var(--text-quaternary)', marginTop: '8px', letterSpacing: '0.3px' }}>
            v{info.version}
            {info.commit && <span style={{ opacity: 0.6 }}> ({info.commit})</span>}
        </div>
    );
}

export default function Layout() {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();
    const { user, logout } = useAuthStore();
    const queryClient = useQueryClient();
    const [showAccountSettings, setShowAccountSettings] = useState(false);
    const [showAccountMenu, setShowAccountMenu] = useState(false);
    const accountMenuRef = useRef<HTMLDivElement>(null);
    const [showNotifications, setShowNotifications] = useState(false);
    const [notifCategory, setNotifCategory] = useState<string>('all');
    const [selectedNotification, setSelectedNotification] = useState<any | null>(null);

    useIdleTimeout();

    // Notification polling
    const { data: unreadCount = 0 } = useQuery({
        queryKey: ['notifications-unread'],
        queryFn: async () => {
            const res = await fetchJson<{ success: boolean; data: { unread_count: number } }>('/messages/unread-count');
            return res?.data?.unread_count || 0;
        },
        refetchInterval: 30000,
        enabled: !!user,
    });
    const { data: notifications = [], refetch: refetchNotifications } = useQuery({
        queryKey: ['notifications', notifCategory],
        queryFn: () => fetchJson<any[]>(`/messages/inbox?limit=50${notifCategory !== 'all' ? `&category=${notifCategory}` : ''}`),
        enabled: !!user && showNotifications,
    });
    const markAllRead = async () => {
        await request('/messages/read-all', { method: 'PUT' });
        queryClient.invalidateQueries({ queryKey: ['notifications-unread'] });
        queryClient.invalidateQueries({ queryKey: ['notifications'] });
    };
    const markOneRead = async (id: string) => {
        await request(`/messages/${id}/read`, { method: 'PUT' });
        queryClient.invalidateQueries({ queryKey: ['notifications-unread'] });
        queryClient.invalidateQueries({ queryKey: ['notifications'] });
    };

    // Theme
    const [theme, setTheme] = useState<'dark' | 'light'>(() => {
        return (localStorage.getItem('theme') as 'dark' | 'light') || 'dark';
    });

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem('theme', theme);
    }, [theme]);

    const toggleTheme = () => setTheme(prev => prev === 'dark' ? 'light' : 'dark');

    // Sidebar collapse state
    const isSidebarCollapsed = useAppStore(s => s.sidebarCollapsed);
    const toggleSidebar = useAppStore(s => s.toggleSidebar);

    // Use user's own tenant_id directly (no switching)
    const currentTenant = user?.tenant_id || '';

    // Keep tenant in localStorage for other components that read it
    useEffect(() => {
        if (currentTenant) {
            localStorage.setItem('current_tenant_id', currentTenant);
        }
    }, [currentTenant]);

    const { data: agents = [] } = useQuery({
        queryKey: ['agents', currentTenant],
        queryFn: () => agentApi.list(currentTenant || undefined),
        refetchInterval: 30000,
        enabled: !!user,  // 只有用户已认证时才调用
    });

    const handleLogout = () => {
        logout();
        navigate('/login');
    };

    const toggleLang = () => {
        i18n.changeLanguage(i18n.language === 'zh' ? 'en' : 'zh');
    };

    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (accountMenuRef.current && !accountMenuRef.current.contains(e.target as Node)) {
                setShowAccountMenu(false);
            }
        };
        if (showAccountMenu) document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [showAccountMenu]);

    return (
        <div className={`app-layout ${isSidebarCollapsed ? 'sidebar-collapsed' : ''}`}>
            <nav className={`sidebar ${isSidebarCollapsed ? 'collapsed' : ''}`}>
                {/* 固定顶部：Logo 和折叠按钮 */}
                <div className="sidebar-top">
                    <div className="sidebar-logo" style={{
                        padding: '14px 14px 12px',
                        marginBottom: '8px',
                        borderBottom: '1px solid var(--border-subtle)',
                        position: 'relative',
                    }}>
                        <div style={{ position: 'absolute', inset: '8px 10px auto auto', fontSize: '10px', color: 'var(--text-tertiary)', background: 'rgba(255,255,255,0.05)', border: '1px solid var(--border-subtle)', borderRadius: '999px', padding: '2px 8px' }}>
                            {t('layout.workspace')}
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0 }}>
                            <div style={{
                                width: '30px', height: '30px', borderRadius: '10px',
                                background: theme === 'dark' ? 'linear-gradient(135deg, rgba(24,144,255,0.9), rgba(82,196,255,0.45))' : 'linear-gradient(135deg, rgba(24,144,255,0.18), rgba(24,144,255,0.06))',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                boxShadow: '0 10px 20px rgba(24,144,255,0.18)',
                                flexShrink: 0,
                            }}>
                                <img src={theme === 'dark' ? '/logo-white.png' : '/logo-black.png'} alt="" style={{ width: 18, height: 18 }} />
                            </div>
                            <div style={{ minWidth: 0 }}>
                                <div className="sidebar-logo-text" style={{ lineHeight: 1.1 }}>Living Agent</div>
                                <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', marginTop: '2px' }}>{t('dashboard.enterprise.focusValue', 'Company Intelligence')}</div>
                            </div>
                        </div>
                        <button className="btn btn-ghost sidebar-collapse-btn" onClick={toggleSidebar} style={{
                            padding: '5px', display: 'flex', alignItems: 'center', justifyContent: 'center',
                            marginLeft: 'auto', color: 'var(--text-tertiary)', borderRadius: '10px',
                            background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-subtle)',
                        }} title={isSidebarCollapsed ? t('layout.expandSidebar', '展开侧边栏') : t('layout.collapseSidebar', '收起侧边栏')}>
                            {isSidebarCollapsed ? SidebarIcons.expand : SidebarIcons.collapse}
                        </button>
                    </div>
                </div>

                {/* 可滚动导航区域 */}
                <div className="sidebar-scrollable">
                    <div className="sidebar-section" style={{ paddingTop: '8px' }}>
                        <NavLink to="/dashboard" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                {SidebarIcons.home}
                            </span>
                            <span className="sidebar-item-text">{t('nav.dashboard')}</span>
                        </NavLink>
                        <NavLink to="/plaza" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconBuildingMonument size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.plaza', '广场')}</span>
                        </NavLink>
                        <NavLink to="/projects" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconFolder size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.projects', '项目')}</span>
                        </NavLink>
                        <NavLink to="/approvals" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconChecklist size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.approvals', '审批')}</span>
                        </NavLink>
                        <NavLink to="/autonomous" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconCoin size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.autonomous', '经济自治')}</span>
                        </NavLink>
                    </div>

                    <div className="sidebar-section">
                        <div className="sidebar-section-title">{t('layout.departments')}</div>
                        {Object.entries(DEPARTMENTS).map(([code, info]) => (
                            <NavLink
                                key={code}
                                to={`/departments/${code}/overview`}
                                className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}
                            >
                                <span className="sidebar-item-icon">{info.icon}</span>
                                <span className="sidebar-item-text">{i18n.language?.startsWith('zh') ? info.name : info.name_en}</span>
                            </NavLink>
                        ))}
                    </div>

                    <div className="sidebar-section">
                        <div className="sidebar-section-title">{t('nav.system', '系统')}</div>
                        <NavLink to="/neurons" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconCode size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.neurons', '神经元')}</span>
                        </NavLink>
                        <NavLink to="/interventions" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconClipboard size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.interventions', '干预')}</span>
                        </NavLink>
                        <NavLink to="/proactive" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconTrendingUp size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.proactive', '主动服务')}</span>
                        </NavLink>
                        <NavLink to="/reception" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconHeadset size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.reception', '接待')}</span>
                        </NavLink>
                        <NavLink to="/voiceprint" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconScale size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.voiceprint', '声纹')}</span>
                        </NavLink>
                        <NavLink to="/office" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`}>
                            <span className="sidebar-item-icon" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                                <IconSettingsAutomation size={14} stroke={1.5} />
                            </span>
                            <span className="sidebar-item-text">{t('nav.office', '办公室')}</span>
                        </NavLink>
                    </div>
                </div>
                {/* 可滚动区域结束 */}

                {/* 固定底部：用户信息和设置 */}
                <div className="sidebar-bottom" style={{ paddingTop: '8px' }}>
                    <div className="sidebar-section" style={{ borderBottom: '1px solid var(--border-subtle)', paddingBottom: '8px', marginBottom: 0 }}>
                        {user && (
                            <NavLink to="/agents/new" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`} title={t('nav.newAgent')}>
                                <span className="sidebar-item-icon" style={{ display: 'flex' }}>{SidebarIcons.plus}</span>
                                <span className="sidebar-item-text">{t('nav.newAgent')}</span>
                            </NavLink>
                        )}
                        {user && ['platform_admin', 'org_admin'].includes(user.role) && (
                            <NavLink to="/documents" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`} title={t('nav.documents', '文档中心')}>
                                <span className="sidebar-item-icon" style={{ display: 'flex' }}><IconBuilding size={16} stroke={1.5} /></span>
                                <span className="sidebar-item-text">{t('nav.documents', '文档中心')}</span>
                            </NavLink>
                        )}
                        {user && user.role === 'platform_admin' && (
                            <NavLink to="/admin/platform-settings" className={({ isActive }) => `sidebar-item ${isActive ? 'active' : ''}`} title={t('nav.platformSettings', 'Platform Settings')}>
                                <span className="sidebar-item-icon" style={{ display: 'flex' }}>
                                    <IconSettings size={16} stroke={1.5} />
                                </span>
                                <span className="sidebar-item-text">{t('nav.platformSettings', 'Platform Settings')}</span>
                            </NavLink>
                        )}
                    </div>

                    <div className="sidebar-footer">
                        <div className="sidebar-footer-controls" style={{
                            display: 'flex', alignItems: 'center', gap: '4px', marginBottom: '8px',
                        }}>
                            <button className="btn btn-ghost" onClick={toggleTheme} style={{
                                padding: '4px 8px', display: 'flex', alignItems: 'center', justifyContent: 'center',
                            }} title={theme === 'dark' ? t('layout.lightMode', '浅色模式') : t('layout.darkMode', '深色模式')}>
                                {theme === 'dark' ? SidebarIcons.sun : SidebarIcons.moon}
                            </button>
                            <button className="btn btn-ghost" onClick={() => { setShowNotifications(v => !v); if (!showNotifications) refetchNotifications(); }} style={{
                                padding: '4px 8px', display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative',
                            }} title={t('layout.notifications')}>
                                {SidebarIcons.bell}
                                {(unreadCount as number) > 0 && (
                                    <span style={{
                                        position: 'absolute', top: '-2px', right: '-4px',
                                        minWidth: '16px', height: '16px', borderRadius: '8px',
                                        padding: '0 4px', boxSizing: 'border-box',
                                        background: 'var(--error)', color: '#fff',
                                        fontSize: '10px', fontWeight: 600,
                                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                                        lineHeight: 1,
                                    }}>{(unreadCount as number) > 99 ? '99+' : unreadCount}</span>
                                )}
                            </button>
                        </div>
                        <div ref={accountMenuRef} style={{ position: 'relative' }}>
                            {showAccountMenu && (
                                <div className="account-dropdown">
                                    <button className="account-dropdown-item" onClick={() => { toggleLang(); setShowAccountMenu(false); }}>
                                        <IconWorld size={15} stroke={1.5} />
                                        <span>{i18n.language === 'zh' ? 'English' : '中文'}</span>
                                    </button>
                                    <button className="account-dropdown-item" onClick={() => { setShowAccountSettings(true); setShowAccountMenu(false); }}>
                                        <IconUser size={15} stroke={1.5} />
                                        <span>{t('layout.accountSettings')}</span>
                                    </button>
                                    <div style={{ height: '1px', background: 'var(--border-subtle)', margin: '4px 0' }} />
                                    <button className="account-dropdown-item account-dropdown-danger" onClick={() => { handleLogout(); setShowAccountMenu(false); }}>
                                        <IconLogout size={15} stroke={1.5} />
                                        <span>{t('layout.logout', 'Logout')}</span>
                                    </button>
                                </div>
                            )}
                            <div
                                className="sidebar-account-row"
                                onClick={() => setShowAccountMenu(v => !v)}
                            >
                                <div style={{
                                    width: '28px', height: '28px', borderRadius: 'var(--radius-md)',
                                    background: 'var(--bg-tertiary)', border: '1px solid var(--border-subtle)',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    color: 'var(--text-tertiary)', flexShrink: 0,
                                }}>
                                    {SidebarIcons.user}
                                </div>
                                <div className="sidebar-footer-user-info" style={{ flex: 1, minWidth: 0 }}>
                                    <div style={{ fontSize: '13px', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {user?.display_name}
                                    </div>
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                        {user?.role === 'platform_admin' ? t('roles.platformAdmin') :
                                            user?.role === 'org_admin' ? t('roles.orgAdmin') :
                                                user?.role === 'agent_admin' ? t('roles.agentAdmin') : t('roles.member')}
                                    </div>
                                </div>
                                <IconChevronUp size={14} stroke={1.5} style={{
                                    color: 'var(--text-tertiary)', flexShrink: 0,
                                    transform: showAccountMenu ? 'rotate(0deg)' : 'rotate(180deg)',
                                    transition: 'transform 0.2s ease',
                                }} />
                            </div>
                        </div>
                        <VersionDisplay />
                    </div>
                </div>
            </nav>

            {/* Notification Modal */}
            {showNotifications && (
                <>
                    <div style={{ position: 'fixed', inset: 0, zIndex: 9998, background: 'rgba(0,0,0,0.5)' }} onClick={() => setShowNotifications(false)} />
                    <div style={{
                        position: 'fixed', top: '50%', left: '50%', transform: 'translate(-50%, -50%)',
                        width: 'calc(100vw - 80px)', maxWidth: '800px',
                        height: '80vh', maxHeight: '800px',
                        background: 'var(--bg-primary)', border: '1px solid var(--border-subtle)',
                        borderRadius: '12px', boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
                        zIndex: 9999, display: 'flex', flexDirection: 'column', overflow: 'hidden',
                    }}>
                        <div style={{ borderBottom: '1px solid var(--border-subtle)', flexShrink: 0 }}>
                            <div style={{ padding: '16px 24px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 600, flex: 1 }}>{t('layout.notifications')}</h3>
                                {(unreadCount as number) > 0 && (
                                    <button className="btn btn-ghost" onClick={markAllRead} style={{ fontSize: '12px', padding: '4px 10px' }}>
                                        {t('layout.markAllRead')}
                                    </button>
                                )}
                                <button className="btn btn-ghost" onClick={() => setShowNotifications(false)} style={{ padding: '4px 8px', fontSize: '18px', lineHeight: 1 }}>×</button>
                            </div>
                            <div style={{ display: 'flex', gap: '0', padding: '0 24px', marginTop: '12px' }}>
                                {[
                                    { key: 'all', zh: '全部', en: 'All' },
                                    { key: 'tool', zh: '工具执行', en: 'Tool' },
                                    { key: 'approval', zh: '审批', en: 'Approval' },
                                    { key: 'social', zh: '社交', en: 'Social' },
                                ].map(tab => (
                                    <button
                                        key={tab.key}
                                        onClick={() => { setNotifCategory(tab.key); }}
                                        style={{
                                            background: 'none', border: 'none', cursor: 'pointer',
                                            padding: '8px 14px', fontSize: '13px', fontWeight: 500,
                                            color: notifCategory === tab.key ? 'var(--text-primary)' : 'var(--text-tertiary)',
                                            borderBottom: notifCategory === tab.key ? '2px solid var(--accent-primary)' : '2px solid transparent',
                                            marginBottom: '-1px', transition: 'all 0.15s',
                                        }}
                                    >
                                        {i18n.language?.startsWith('zh') ? tab.zh : tab.en}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div style={{ flex: 1, overflowY: 'auto', padding: '8px 0' }}>
                            {(notifications as any[]).length === 0 && (
                                <div style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                                    {t('layout.noNotifications')}
                                </div>
                            )}
                            {(notifications as any[]).map((n: any) => (
                                <div
                                    key={n.id}
                                    onClick={() => {
                                        if (!n.is_read) markOneRead(n.id);
                                        if (n.type === 'broadcast' || !n.link) {
                                            setSelectedNotification(n);
                                        } else if (n.link) {
                                            navigate(n.link); setShowNotifications(false);
                                        }
                                    }}
                                    style={{
                                        padding: '14px 24px', cursor: 'pointer',
                                        borderBottom: '1px solid var(--border-subtle)',
                                        background: n.is_read ? 'transparent' : 'var(--bg-secondary)',
                                        transition: 'background 0.15s',
                                    }}
                                    onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg-tertiary)')}
                                    onMouseLeave={e => (e.currentTarget.style.background = n.is_read ? 'transparent' : 'var(--bg-secondary)')}
                                >
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                                        {!n.is_read && <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: 'var(--accent-primary)', flexShrink: 0 }} />}
                                        <span style={{ fontSize: '13px', fontWeight: 500, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                            {n.title}
                                        </span>
                                    </div>
                                    {n.body && <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', lineHeight: '1.4', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{n.body}</div>}
                                    <div style={{ fontSize: '11px', color: 'var(--text-quaternary)', marginTop: '4px' }}>
                                        {n.created_at ? new Date(n.created_at).toLocaleString() : ''}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </>
            )}
            
            {/* Notification Detail Modal */}
            {selectedNotification && (
                <div style={{ position: 'fixed', inset: 0, zIndex: 10000, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={() => setSelectedNotification(null)}>
                    <div style={{ background: 'var(--bg-primary)', borderRadius: '12px', border: '1px solid var(--border-subtle)', width: '480px', maxHeight: '90vh', display: 'flex', flexDirection: 'column', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }} onClick={e => e.stopPropagation()}>
                        <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 600 }}>{selectedNotification.title}</h3>
                            <button onClick={() => setSelectedNotification(null)} style={{ background: 'none', border: 'none', color: 'var(--text-tertiary)', fontSize: '20px', cursor: 'pointer', padding: '0' }}>×</button>
                        </div>
                        <div style={{ padding: '20px 24px', overflowY: 'auto', fontSize: '14px', lineHeight: '1.6', color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }}>
                            {selectedNotification.body || t('layout.noDetailsProvided')}
                        </div>
                        <div style={{ padding: '16px 24px', borderTop: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', color: 'var(--text-tertiary)', fontSize: '12px' }}>
                            <span>{selectedNotification.sender_name ? t('layout.from', { name: selectedNotification.sender_name }) : ''}</span>
                            <span>{selectedNotification.created_at ? new Date(selectedNotification.created_at).toLocaleString() : ''}</span>
                        </div>
                    </div>
                </div>
            )}

            <main className="main-content">
                <Outlet />
            </main>

            {showAccountSettings && (
                <AccountSettingsModal
                    user={user}
                    onClose={() => setShowAccountSettings(false)}
                    isChinese={i18n.language?.startsWith('zh')}
                />
            )}
        </div>
    );
}
