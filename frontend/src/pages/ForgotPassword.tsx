import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authApi } from '../services/api';

export default function ForgotPassword() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const [phone, setPhone] = useState('');
    const [code, setCode] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [countdown, setCountdown] = useState(0);

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', 'dark');
    }, []);

    useEffect(() => {
        if (countdown > 0) {
            const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
            return () => clearTimeout(timer);
        }
    }, [countdown]);

    const handleSendCode = async () => {
        if (!phone) {
            setError(t('phoneLogin.enterPhone'));
            return;
        }
        
        setError('');
        setLoading(true);
        
        try {
            await authApi.sendSmsCode({ phone, type: 'reset' });
            setCountdown(60);
            setMessage(t('phoneLogin.codeSent'));
        } catch (err: any) {
            setError(err.message || t('phoneLogin.failedToSendCode'));
        } finally {
            setLoading(false);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');
        setMessage('');
        setLoading(true);

        try {
            await authApi.phoneLogin({ phone, code });
            setMessage(t('phoneLogin.loginSuccess'));
            setTimeout(() => navigate('/'), 1000);
        } catch (err: any) {
            setError(err.message || t('phoneLogin.invalidOrExpiredCode'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-page">
            <div className="login-form-panel" style={{ width: '100%', display: 'flex', justifyContent: 'center' }}>
                <div className="login-form-wrapper" style={{ maxWidth: '460px' }}>
                    <div className="login-form-header">
                        <div className="login-form-logo">
                            <img src="/logo-black.png" className="login-logo-img" alt="" style={{ width: 28, height: 28, marginRight: 8, verticalAlign: 'middle' }} />
                            Living Agent
                        </div>
                        <h2 className="login-form-title">{t('phoneLogin.title')}</h2>
                        <p className="login-form-subtitle">
                            {t('phoneLogin.subtitle')}
                        </p>
                    </div>

                    {error && (
                        <div className="login-error">
                            <span>⚠</span> {error}
                        </div>
                    )}

                    {message && (
                        <div className="login-error" style={{ background: 'rgba(34,197,94,0.14)', borderColor: 'rgba(34,197,94,0.35)', color: '#dcfce7' }}>
                            <span>✓</span> {message}
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="login-form">
                        <div className="login-field">
                            <label>{t('phoneLogin.phoneNumber')}</label>
                            <input
                                value={phone}
                                onChange={(e) => setPhone(e.target.value)}
                                required
                                autoFocus
                                placeholder={t('phoneLogin.enterPhoneNumber')}
                            />
                        </div>

                        <div className="login-field">
                            <label>{t('phoneLogin.verificationCode')}</label>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <input
                                    value={code}
                                    onChange={(e) => setCode(e.target.value)}
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
                                    style={{ whiteSpace: 'nowrap', minWidth: '100px' }}
                                >
                                    {countdown > 0 ? `${countdown}s` : t('phoneLogin.sendCode')}
                                </button>
                            </div>
                        </div>

                        <button className="login-submit" type="submit" disabled={loading}>
                            {loading ? <span className="login-spinner" /> : t('phoneLogin.login')}
                        </button>
                    </form>

                    <div className="login-switch">
                        <Link to="/login">{t('phoneLogin.backToLogin')}</Link>
                    </div>
                </div>
            </div>
        </div>
    );
}
