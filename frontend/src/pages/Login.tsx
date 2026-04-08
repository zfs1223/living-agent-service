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
            setError(isChinese ? '请输入手机号' : 'Please enter phone number');
            return;
        }
        
        setError('');
        setLoading(true);
        
        try {
            await authApi.sendSmsCode({ phone: form.phone, type: 'login' });
            setCountdown(60);
        } catch (err: any) {
            setError(err.message || (isChinese ? '发送验证码失败' : 'Failed to send code'));
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
            const user: User = {
                id: res.user.id,
                username: res.user.name,
                email: res.user.email || '',
                display_name: res.user.name,
                role: 'org_admin',
                tenant_id: res.user.tenantId,
                identity: res.user.identity as UserIdentity,
                access_level: res.user.accessLevel as AccessLevel,
                is_active: true,
                created_at: new Date().toISOString(),
            };
            setAuth(user, res.accessToken);
            navigate('/');
        } catch (err: any) {
            const msg = err.message || '';
            if (msg.includes('invalid') || msg.includes('incorrect')) {
                setError(isChinese ? '验证码错误或已过期' : 'Invalid or expired code');
            } else if (msg.includes('not found')) {
                setError(isChinese ? '用户不存在，请先注册' : 'User not found, please register first');
            } else {
                setError(msg || (isChinese ? '登录失败' : 'Login failed'));
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
        <div className="login-page">
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

            <div className="login-form-panel">
                <div style={{
                    position: 'absolute', top: '16px', right: '16px',
                    cursor: 'pointer', fontSize: '13px', color: 'var(--text-secondary)',
                    display: 'flex', alignItems: 'center', gap: '4px',
                    padding: '6px 12px', borderRadius: '8px',
                    background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
                    zIndex: 101,
                }} onClick={toggleLang}>
                    🌐
                </div>

                <div className="login-form-wrapper">
                    <div className="login-form-header">
                        <div className="login-form-logo">
                            <img src="/logo-black.png" className="login-logo-img" alt="" style={{ width: 28, height: 28, marginRight: 8, verticalAlign: 'middle' }} />
                            Living Agent
                        </div>
                        <h2 className="login-form-title">{isChinese ? '手机验证码登录' : 'Phone Login'}</h2>
                        <p className="login-form-subtitle">{isChinese ? '使用手机验证码快速登录' : 'Quick login with phone verification'}</p>
                    </div>

                    {error && (
                        <div className="login-error">
                            <span>⚠</span> {error}
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="login-form">
                        <div className="login-field">
                            <label>{isChinese ? '手机号' : 'Phone Number'}</label>
                            <input
                                value={form.phone}
                                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                                required
                                autoFocus
                                placeholder={isChinese ? '请输入手机号' : 'Enter phone number'}
                            />
                        </div>

                        <div className="login-field">
                            <label>{isChinese ? '验证码' : 'Verification Code'}</label>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <input
                                    value={form.code}
                                    onChange={(e) => setForm({ ...form, code: e.target.value })}
                                    required
                                    placeholder={isChinese ? '请输入验证码' : 'Enter code'}
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
                                        : (isChinese ? '发送验证码' : 'Send Code')}
                                </button>
                            </div>
                        </div>

                        <button className="login-submit" type="submit" disabled={loading}>
                            {loading ? (
                                <span className="login-spinner" />
                            ) : (
                                <>
                                    {isChinese ? '登录' : 'Login'}
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
                identity: res.identity as UserIdentity,
                access_level: res.accessLevel as AccessLevel,
                is_active: true,
                created_at: new Date().toISOString(),
            };
            setAuth(user, res.sessionId);
            navigate('/');
        } catch (err: any) {
            setError(err.message || (isChinese ? '注册失败' : 'Registration failed'));
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
                        {isChinese ? '系统初始化' : 'System Initialization'}<br />
                        <span style={{ fontSize: '0.65em', fontWeight: 600, opacity: 0.85 }}>
                            {isChinese ? '注册创始人账号' : 'Register Founder Account'}
                        </span>
                    </h1>
                    <p className="login-hero-desc">
                        {isChinese 
                            ? '欢迎使用 Living Agent 系统。请先注册创始人（董事长）账号。'
                            : 'Welcome to Living Agent. Please register the founder account first.'}
                    </p>
                </div>
            </div>

            <div className="login-form-panel">
                <div style={{
                    position: 'absolute', top: '16px', right: '16px',
                    cursor: 'pointer', fontSize: '13px', color: 'var(--text-secondary)',
                    display: 'flex', alignItems: 'center', gap: '4px',
                    padding: '6px 12px', borderRadius: '8px',
                    background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
                    zIndex: 101,
                }} onClick={toggleLang}>
                    🌐
                </div>

                <div className="login-form-wrapper">
                    <div className="login-form-header">
                        <div className="login-form-logo">
                            <img src="/logo-black.png" className="login-logo-img" alt="" style={{ width: 28, height: 28, marginRight: 8, verticalAlign: 'middle' }} />
                            Living Agent
                        </div>
                        <h2 className="login-form-title">{isChinese ? '注册创始人' : 'Register Founder'}</h2>
                        <p className="login-form-subtitle">{isChinese ? '创建系统管理员账号' : 'Create system admin account'}</p>
                    </div>

                    {error && (
                        <div className="login-error">
                            <span>⚠</span> {error}
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="login-form">
                        <div className="login-field">
                            <label>{isChinese ? '姓名' : 'Name'}</label>
                            <input
                                value={form.name}
                                onChange={(e) => setForm({ ...form, name: e.target.value })}
                                required
                                autoFocus
                                placeholder={isChinese ? '请输入姓名' : 'Enter name'}
                            />
                        </div>

                        <div className="login-field">
                            <label>{isChinese ? '邮箱' : 'Email'}</label>
                            <input
                                type="email"
                                value={form.email}
                                onChange={(e) => setForm({ ...form, email: e.target.value })}
                                required
                                placeholder={isChinese ? '请输入邮箱' : 'Enter email'}
                            />
                        </div>

                        <div className="login-field">
                            <label>{isChinese ? '手机号' : 'Phone'}</label>
                            <input
                                value={form.phone}
                                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                                required
                                placeholder={isChinese ? '请输入手机号' : 'Enter phone number'}
                            />
                        </div>

                        <div className="login-field">
                            <label>{isChinese ? '公司名称' : 'Company Name'} ({isChinese ? '可选' : 'Optional'})</label>
                            <input
                                value={form.companyName}
                                onChange={(e) => setForm({ ...form, companyName: e.target.value })}
                                placeholder={isChinese ? '请输入公司名称' : 'Enter company name'}
                            />
                        </div>

                        <button className="login-submit" type="submit" disabled={loading}>
                            {loading ? (
                                <span className="login-spinner" />
                            ) : (
                                <>
                                    {isChinese ? '注册' : 'Register'}
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
