import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores';
import { authApi, systemApi, fetchJson } from '../services/api';
import type { User, UserIdentity, AccessLevel } from '../types';

export default function Login() {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();
    const setAuth = useAuthStore((s) => s.setAuth);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [systemStatus, setSystemStatus] = useState<{ hasFounder: boolean; isFirstUser: boolean } | null>(null);
    const [countdown, setCountdown] = useState(0);
    const [testCode, setTestCode] = useState('');
    const [isTestMode, setIsTestMode] = useState(true);

    const [form, setForm] = useState({
        phone: '',
        code: '',
    });

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', 'dark');
        
        systemApi.status()
            .then(status => setSystemStatus(status))
            .catch(() => setSystemStatus({ hasFounder: false, isFirstUser: true }));
    }, []);

    useEffect(() => {
        if (countdown > 0) {
            const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
            return () => clearTimeout(timer);
        }
    }, [countdown]);

    const toggleLang = () => {
        i18n.changeLanguage(i18n.language === 'zh' ? 'en' : 'zh');
    };

    const handleSendCode = async () => {
        if (!form.phone) {
            setError(t('phoneLogin.enterPhone'));
            return;
        }
        
        setError('');
        setLoading(true);
        
        try {
            const res = await authApi.sendSmsCode({ phone: form.phone, type: 'login' });
            setCountdown(60);
            if (res.code) {
                setTestCode(res.code);
                setForm(prev => ({ ...prev, code: res.code || '' }));
            }
        } catch (err: any) {
            setError(err.message || t('phoneLogin.failedToSendCode'));
        } finally {
            setLoading(false);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            const res = await authApi.phoneLogin({
                phone: form.phone,
                code: form.code,
            });
            console.log('[Login] phoneLogin response:', res);
            console.log('[Login] accessToken:', res.accessToken);
            console.log('[Login] user:', res.user);
            const user: User = {
                id: res.user.id,
                username: res.user.name,
                email: res.user.email || '',
                display_name: res.user.name,
                role: 'org_admin',
                tenant_id: res.user.tenantId,
                department_code: res.user.department || undefined,
                identity: res.user.identity as UserIdentity,
                access_level: res.user.accessLevel as AccessLevel,
                is_active: true,
                created_at: new Date().toISOString(),
            };
            console.log('[Login] constructed user:', user);
            setAuth(user, res.accessToken);
            console.log('[Login] setAuth called, navigating to /');
            navigate('/');
        } catch (err: any) {
            const msg = err.message || '';
            if (msg.includes('invalid') || msg.includes('incorrect')) {
                setError(t('phoneLogin.invalidOrExpiredCode'));
            } else if (msg.includes('not found')) {
                setError(t('phoneLogin.userNotFoundRegister'));
            } else {
                setError(msg || t('phoneLogin.loginFailed'));
            }
        } finally {
            setLoading(false);
        }
    };

    const isChinese = i18n.language?.startsWith('zh');

    if (systemStatus && !systemStatus.hasFounder) {
        return <RegisterPage isChinese={isChinese} toggleLang={toggleLang} />;
    }

    return (
        <div className="login-page" style={{ position: 'relative' }}>
            <div className="login-hero">
                <div className="login-hero-bg" />
                <div className="login-hero-content">
                    <div className="login-hero-badge">
                        <span className="login-hero-badge-dot" />
                        {t('login.hero.badge')}
                    </div>
                    <h1 className="login-hero-title">
                        {t('login.hero.title')}<br />
                        <span style={{ fontSize: '0.65em', fontWeight: 600, opacity: 0.85 }}>{t('login.hero.subtitle')}</span>
                    </h1>
                    <p className="login-hero-desc" dangerouslySetInnerHTML={{ __html: t('login.hero.description') }} />
                    <div className="login-hero-features">
                        <div className="login-hero-feature">
                            <span className="login-hero-feature-icon">🧠</span>
                            <div>
                                <div className="login-hero-feature-title">{t('login.hero.features.multiAgent.title')}</div>
                                <div className="login-hero-feature-desc">{t('login.hero.features.multiAgent.description')}</div>
                            </div>
                        </div>
                        <div className="login-hero-feature">
                            <span className="login-hero-feature-icon">⚡</span>
                            <div>
                                <div className="login-hero-feature-title">{t('login.hero.features.persistentMemory.title')}</div>
                                <div className="login-hero-feature-desc">{t('login.hero.features.persistentMemory.description')}</div>
                            </div>
                        </div>
                        <div className="login-hero-feature">
                            <span className="login-hero-feature-icon">📚</span>
                            <div>
                                <div className="login-hero-feature-title">{t('login.hero.features.agentPlaza.title')}</div>
                                <div className="login-hero-feature-desc">{t('login.hero.features.agentPlaza.description')}</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div className="login-form-panel" style={{ position: 'relative' }}>
                <div style={{
                    position: 'absolute', top: '16px', right: '16px',
                    cursor: 'pointer', fontSize: '13px', color: 'var(--text-secondary)',
                    display: 'flex', alignItems: 'center', gap: '4px',
                    padding: '6px 12px', borderRadius: '999px',
                    background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-subtle)',
                    zIndex: 101,
                    boxShadow: '0 8px 20px rgba(0,0,0,0.08)',
                }} onClick={toggleLang}>
                    <span style={{ fontSize: '14px' }}>🌐</span>
                    <span>{t('phoneLogin.switchLang')}</span>
                </div>

                <div className="login-form-wrapper">
                    <div className="login-form-header">
                        <div className="login-form-logo">
                            <img src="/logo-black.png" className="login-logo-img" alt="" style={{ width: 28, height: 28, marginRight: 8, verticalAlign: 'middle' }} />
                            Living Agent
                        </div>
                        <h2 className="login-form-title">{t('phoneLogin.title')}</h2>
                        <p className="login-form-subtitle">{t('phoneLogin.subtitle')}</p>
                    </div>

                    {error && (
                        <div className="login-error">
                            <span>⚠</span> {error}
                        </div>
                    )}

                    {isTestMode && testCode && (
                        <div style={{
                            background: 'rgba(76, 175, 80, 0.15)',
                            border: '1px solid rgba(76, 175, 80, 0.4)',
                            borderRadius: '8px',
                            padding: '10px 14px',
                            marginBottom: '16px',
                            fontSize: '13px',
                            color: '#4caf50',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px'
                        }}>
                            <span>🧪</span>
                            <span>{t('phoneLogin.testModeAutoFill')}</span>
                            <strong style={{ letterSpacing: '4px', fontSize: '16px' }}>{testCode}</strong>
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="login-form">
                        <div className="login-field">
                            <label>{t('phoneLogin.phoneNumber')}</label>
                            <input
                                value={form.phone}
                                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                                required
                                autoFocus
                                placeholder={t('phoneLogin.enterPhoneNumber')}
                            />
                        </div>

                        <div className="login-field">
                            <label>{t('phoneLogin.verificationCode')}</label>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <input
                                    value={form.code}
                                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                                    required
                                    placeholder={t('phoneLogin.enterCode')}
                                    style={{ flex: 1 }}
                                    maxLength={6}
                                />
                                <button
                                    type="button"
                                    className="btn btn-ghost"
                                    onClick={handleSendCode}
                                    disabled={countdown > 0 || loading}
                                    style={{ 
                                        whiteSpace: 'nowrap', 
                                        minWidth: '100px',
                                        opacity: countdown > 0 ? 0.7 : 1 
                                    }}
                                >
                                    {countdown > 0 
                                        ? `${countdown}s` 
                                        : t('phoneLogin.sendCode')}
                                </button>
                            </div>
                        </div>

                        <button className="login-submit" type="submit" disabled={loading}>
                            {loading ? (
                                <span className="login-spinner" />
                            ) : (
                                <>
                                    {t('phoneLogin.login')}
                                    <span style={{ marginLeft: '6px' }}>→</span>
                                </>
                            )}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}

function RegisterPage({ isChinese, toggleLang }: { isChinese: boolean; toggleLang: () => void }) {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const setAuth = useAuthStore((s) => s.setAuth);
    const [form, setForm] = useState({
        name: '',
        email: '',
        phone: '',
        companyName: '',
    });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            const res = await systemApi.register(form);
            const user: User = {
                id: res.employeeId,
                username: res.name,
                email: form.email,
                display_name: res.name,
                role: 'org_admin',
                tenant_id: res.tenantId,
                identity: res.identity as UserIdentity,
                access_level: res.accessLevel as AccessLevel,
                is_active: true,
                created_at: new Date().toISOString(),
            };
            setAuth(user, res.sessionId);

            navigate('/');
        } catch (err: any) {
            setError(err.message || t('registerFounder.registrationFailed'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-page">
            <div className="login-hero">
                <div className="login-hero-bg" />
                <div className="login-hero-content">
                    <h1 className="login-hero-title">
                        {t('registerFounder.systemInit')}<br />
                        <span style={{ fontSize: '0.65em', fontWeight: 600, opacity: 0.85 }}>
                            {t('registerFounder.registerFounderAccount')}
                        </span>
                    </h1>
                    <p className="login-hero-desc">
                        {t('registerFounder.welcomeDesc')}
                    </p>
                </div>
            </div>

            <div className="login-form-panel" style={{ position: 'relative' }}>
                <div style={{
                    position: 'absolute', top: '16px', right: '16px',
                    cursor: 'pointer', fontSize: '13px', color: 'var(--text-secondary)',
                    display: 'flex', alignItems: 'center', gap: '4px',
                    padding: '6px 12px', borderRadius: '999px',
                    background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-subtle)',
                    zIndex: 101,
                    boxShadow: '0 8px 20px rgba(0,0,0,0.08)',
                }} onClick={toggleLang}>
                    <span style={{ fontSize: '14px' }}>🌐</span>
                    <span>{t('phoneLogin.switchLang')}</span>
                </div>

                <div className="login-form-wrapper">
                    <div className="login-form-header">
                        <div className="login-form-logo">
                            <img src="/logo-black.png" className="login-logo-img" alt="" style={{ width: 28, height: 28, marginRight: 8, verticalAlign: 'middle' }} />
                            Living Agent
                        </div>
                        <h2 className="login-form-title">{t('registerFounder.title')}</h2>
                        <p className="login-form-subtitle">{t('registerFounder.subtitle')}</p>
                    </div>

                    {error && (
                        <div className="login-error">
                            <span>⚠</span> {error}
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="login-form">
                        <div className="login-field">
                            <label>{t('registerFounder.name')}</label>
                            <input
                                value={form.name}
                                onChange={(e) => setForm({ ...form, name: e.target.value })}
                                required
                                autoFocus
                                placeholder={t('registerFounder.enterName')}
                            />
                        </div>

                        <div className="login-field">
                            <label>{t('registerFounder.email')}</label>
                            <input
                                type="email"
                                value={form.email}
                                onChange={(e) => setForm({ ...form, email: e.target.value })}
                                required
                                placeholder={t('registerFounder.enterEmail')}
                            />
                        </div>

                        <div className="login-field">
                            <label>{t('registerFounder.phone')}</label>
                            <input
                                value={form.phone}
                                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                                required
                                placeholder={t('phoneLogin.enterPhoneNumber')}
                            />
                        </div>

                        <div className="login-field">
                            <label>{t('registerFounder.companyName')} ({t('registerFounder.optional')})</label>
                            <input
                                value={form.companyName}
                                onChange={(e) => setForm({ ...form, companyName: e.target.value })}
                                placeholder={t('registerFounder.enterCompanyName')}
                            />
                        </div>

                        <button className="login-submit" type="submit" disabled={loading}>
                            {loading ? (
                                <span className="login-spinner" />
                            ) : (
                                <>
                                    {t('registerFounder.register')}
                                    <span style={{ marginLeft: '6px' }}>→</span>
                                </>
                            )}
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}
