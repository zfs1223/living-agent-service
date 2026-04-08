import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { authApi } from '../services/api';

export default function ResetPassword() {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();
    const isChinese = i18n.language?.startsWith('zh');
    const [phone, setPhone] = useState('');
    const [code, setCode] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState(false);
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
            setError(isChinese ? '请输入手机号' : 'Please enter phone number');
            return;
        }
        
        setError('');
        setLoading(true);
        
        try {
            await authApi.sendSmsCode({ phone, type: 'reset' });
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

        if (!phone) {
            setError(isChinese ? '请输入手机号' : 'Please enter phone number');
            return;
        }
        if (!code || code.length < 4) {
            setError(isChinese ? '请输入验证码' : 'Please enter verification code');
            return;
        }

        setLoading(true);
        try {
            await authApi.phoneLogin({ phone, code });
            setSuccess(true);
            window.setTimeout(() => navigate('/'), 1200);
        } catch (err: any) {
            setError(err.message || (isChinese ? '验证码错误或已过期' : 'Invalid or expired code'));
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
                        <h2 className="login-form-title">{isChinese ? '手机验证登录' : 'Phone Verification Login'}</h2>
                        <p className="login-form-subtitle">
                            {isChinese ? '使用手机验证码快速登录系统' : 'Quick login with phone verification'}
                        </p>
                    </div>

                    {error && (
                        <div className="login-error">
                            <span>⚠</span> {error}
                        </div>
                    )}

                    {success && (
                        <div className="login-error" style={{ background: 'rgba(34,197,94,0.14)', borderColor: 'rgba(34,197,94,0.35)', color: '#dcfce7' }}>
                            <span>✓</span> {isChinese ? '登录成功，正在跳转...' : 'Login successful, redirecting...'}
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="login-form">
                        <div className="login-field">
                            <label>{isChinese ? '手机号' : 'Phone Number'}</label>
                            <input
                                value={phone}
                                onChange={(e) => setPhone(e.target.value)}
                                required
                                autoFocus
                                placeholder={isChinese ? '请输入手机号' : 'Enter phone number'}
                            />
                        </div>

                        <div className="login-field">
                            <label>{isChinese ? '验证码' : 'Verification Code'}</label>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <input
                                    value={code}
                                    onChange={(e) => setCode(e.target.value)}
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
                                    style={{ whiteSpace: 'nowrap', minWidth: '100px' }}
                                >
                                    {countdown > 0 ? `${countdown}s` : (isChinese ? '发送验证码' : 'Send Code')}
                                </button>
                            </div>
                        </div>

                        <button className="login-submit" type="submit" disabled={loading || success}>
                            {loading ? <span className="login-spinner" /> : (isChinese ? '登录' : 'Login')}
                        </button>
                    </form>

                    <div className="login-switch">
                        <Link to="/login">{isChinese ? '返回登录' : 'Back to login'}</Link>
                    </div>
                </div>
            </div>
        </div>
    );
}
