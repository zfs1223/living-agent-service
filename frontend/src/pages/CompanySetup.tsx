import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores';
import { tenantApi, authApi } from '../services/api';

export default function CompanySetup() {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();
    const { user, setAuth, setUser } = useAuthStore();
    const [allowCreate, setAllowCreate] = useState(true);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    // Join company form
    const [inviteCode, setInviteCode] = useState('');
    // Create company form
    const [companyName, setCompanyName] = useState('');

    useEffect(() => {
        // Check if self-creation is allowed
        tenantApi.registrationConfig().then((d: any) => {
            setAllowCreate(d.allow_self_create_company);
        }).catch(() => {});
    }, []);

    const refreshUser = async (tenantIdFallback?: string) => {
        const token = useAuthStore.getState().token;
        if (!token) return;

        const applyUser = (me: any) => {
            const tenantId = me?.tenantId || me?.tenant_id || tenantIdFallback || localStorage.getItem('current_tenant_id') || undefined;
            if (tenantId) {
                localStorage.setItem('current_tenant_id', tenantId);
            }
            const mappedUser = {
                id: me?.id || user?.id || '',
                username: me?.name || me?.username || user?.username || '',
                email: me?.email || user?.email || '',
                display_name: me?.name || me?.display_name || user?.display_name || '',
                role: 'org_admin' as const,
                tenant_id: tenantId,
                identity: me?.identity || user?.identity,
                access_level: me?.accessLevel || me?.access_level || user?.access_level,
                department_code: me?.department || user?.department_code,
                is_active: true,
                created_at: user?.created_at || new Date().toISOString(),
            };
            setAuth(mappedUser, token);
        };

        try {
            const me = await authApi.me() as any;
            applyUser(me);
        } catch {
            if (tenantIdFallback && user) {
                localStorage.setItem('current_tenant_id', tenantIdFallback);
                setUser({ ...user, tenant_id: tenantIdFallback });
            }
        }
    };

    const handleJoin = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const result = await tenantApi.join(inviteCode) as any;
            await refreshUser(result?.tenantId || result?.tenant_id);
            navigate('/');
        } catch (err: any) {
            setError(err.message || 'Failed to join company');
        } finally {
            setLoading(false);
        }
    };

    const handleCreate = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);
        try {
            const result = await tenantApi.selfCreate({ name: companyName }) as any;
            await refreshUser(result?.tenantId || result?.tenant_id);
            // Go through identity-aware home redirect
            navigate('/');
        } catch (err: any) {
            setError(err.message || 'Failed to create company');
        } finally {
            setLoading(false);
        }
    };

    const toggleLang = () => {
        i18n.changeLanguage(i18n.language === 'zh' ? 'en' : 'zh');
    };

    return (
        <div className="company-setup-page" style={{
            minHeight: '100vh',
            background: 'radial-gradient(circle at top, rgba(24,144,255,0.14), transparent 36%), linear-gradient(180deg, rgba(5,6,10,0.98), rgba(12,18,28,0.96))',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '32px 16px',
            position: 'relative',
        }}>
            {/* Language Switcher */}
            <button style={{
                position: 'absolute', top: '16px', right: '16px',
                cursor: 'pointer', fontSize: '13px', color: 'var(--text-secondary)',
                display: 'flex', alignItems: 'center', gap: '4px',
                padding: '8px 12px', borderRadius: '12px',
                background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)',
                zIndex: 101,
                boxShadow: '0 8px 24px rgba(0,0,0,0.18)',
            }} onClick={toggleLang}>
                🌐 {i18n.language === 'zh' ? 'EN' : '中文'}
            </button>

            <div className="company-setup-container" style={{
                width: 'min(1040px, 100%)',
                borderRadius: '28px',
                padding: '28px',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
                boxShadow: '0 28px 80px rgba(0,0,0,0.35)',
                backdropFilter: 'blur(18px)',
            }}>
                <div className="company-setup-header" style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    textAlign: 'center',
                    marginBottom: '20px',
                    paddingBottom: '18px',
                    borderBottom: '1px solid rgba(255,255,255,0.08)',
                }}>
                    <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 10px', borderRadius: '999px', background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)', fontSize: '12px', marginBottom: '14px' }}>
                        <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-primary)', boxShadow: '0 0 18px rgba(24,144,255,0.85)' }} />
                        {t('companySetup.badge', 'Workspace Entry')}
                    </div>
                    <div style={{ width: '56px', height: '56px', borderRadius: '18px', background: 'rgba(255,255,255,0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: '14px', border: '1px solid rgba(255,255,255,0.08)' }}>
                        <img src="/logo-black.png" alt="" style={{ width: 32, height: 32 }} />
                    </div>
                    <h1 style={{ margin: 0, fontSize: '28px', letterSpacing: '-0.04em' }}>{t('companySetup.title', 'Set Up Your Workspace')}</h1>
                    <p className="company-setup-subtitle" style={{ margin: '10px 0 0', maxWidth: '68ch', lineHeight: 1.75, color: 'var(--text-secondary)' }}>
                        {t('companySetup.subtitle', 'Join an existing company or create your own to get started.')}
                    </p>
                </div>

                {error && (
                    <div className="login-error" style={{ marginBottom: 16 }}>
                        <span>⚠</span> {error}
                    </div>
                )}

                <div className={`company-setup-panels ${!allowCreate ? 'single' : ''}`}>
                    {/* ── Join Company Panel ── */}
                    <form className="company-setup-panel" onSubmit={handleJoin}>
                        <div className="company-setup-panel-header">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
                                <polyline points="10 17 15 12 10 7" />
                                <line x1="15" y1="12" x2="3" y2="12" />
                            </svg>
                            <h3>{t('companySetup.joinTitle', 'Join a Company')}</h3>
                        </div>
                        <p className="company-setup-panel-desc">
                            {t('companySetup.joinDesc', 'Enter the invitation code provided by your company administrator.')}
                        </p>
                        <div className="login-field">
                            <label>{t('companySetup.inviteCode', 'Invitation Code')}</label>
                            <input
                                value={inviteCode}
                                onChange={(e) => setInviteCode(e.target.value)}
                                required
                                placeholder={t('companySetup.inviteCodePlaceholder', 'e.g. ABC12345')}
                                style={{ textTransform: 'uppercase', letterSpacing: '2px', fontFamily: 'monospace' }}
                            />
                        </div>
                        <button className="login-submit" type="submit" disabled={loading || !inviteCode}>
                            {loading ? <span className="login-spinner" /> : t('companySetup.joinBtn', 'Join Company')}
                        </button>
                    </form>

                    {/* ── Create Company Panel ── */}
                    {allowCreate && (
                        <>
                            <div className="company-setup-divider">
                                <span>{t('companySetup.or', 'OR')}</span>
                            </div>
                            <form className="company-setup-panel" onSubmit={handleCreate}>
                                <div className="company-setup-panel-header">
                                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                        <rect x="2" y="7" width="20" height="14" rx="2" ry="2" />
                                        <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
                                    </svg>
                                    <h3>{t('companySetup.createTitle', 'Create a Company')}</h3>
                                </div>
                                <p className="company-setup-panel-desc">
                                    {t('companySetup.createDesc', 'Start a new workspace. You can invite team members later.')}
                                </p>
                                <div className="login-field">
                                    <label>{t('companySetup.companyName', 'Company Name')}</label>
                                    <input
                                        value={companyName}
                                        onChange={(e) => setCompanyName(e.target.value)}
                                        required
                                        placeholder={t('companySetup.companyNamePlaceholder', 'e.g. Acme Inc.')}
                                    />
                                </div>
                                <button className="login-submit" type="submit" disabled={loading || !companyName}>
                                    {loading ? <span className="login-spinner" /> : t('companySetup.createBtn', 'Create Company')}
                                </button>
                            </form>
                        </>
                    )}
                </div>

                {!allowCreate && (
                    <p className="company-setup-hint">
                        {t('companySetup.contactAdmin', 'Contact your platform administrator for an invitation code.')}
                    </p>
                )}
            </div>
        </div>
    );
}
