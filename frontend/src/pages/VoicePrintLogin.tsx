import { useState, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { voicePrintApi, voicePrintExtendedApi } from '../services/api';

/* ────── Inline SVG Icons (monochrome) ────── */

const Icons = {
    mic: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <rect x="5.5" y="1.5" width="5" height="7" rx="2.5" />
            <path d="M3 7a5 5 0 0010 0" />
            <line x1="8" y1="12" x2="8" y2="14.5" />
            <line x1="5.5" y1="14.5" x2="10.5" y2="14.5" />
        </svg>
    ),
    shield: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M8 1.5L2 4v4c0 3.5 2.5 6 6 7.5 3.5-1.5 6-4 6-7.5V4L8 1.5z" />
            <path d="M6 8l1.5 1.5L10.5 6" />
        </svg>
    ),
    user: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="8" cy="5" r="2.5" />
            <path d="M3 14v-1a3.5 3.5 0 017 0v1" />
        </svg>
    ),
    upload: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M8 10V2M4.5 5.5L8 2l3.5 3.5" />
            <path d="M2 10v2a2 2 0 002 2h8a2 2 0 002-2v-2" />
        </svg>
    ),
    key: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="5.5" cy="5.5" r="3.5" />
            <path d="M8.5 8.5L14 14" />
            <path d="M11.5 11.5l1.5 1.5" />
        </svg>
    ),
    check: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <rect x="2" y="2" width="12" height="12" rx="2" />
            <path d="M5.5 8l2 2 3.5-3.5" />
        </svg>
    ),
    status: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="8" cy="8" r="6" />
            <path d="M8 5v3l2 1.5" />
        </svg>
    ),
    fingerprint: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M4 12c0-3 1-6 4-6s4 3 4 6" />
            <path d="M2 12c0-5 2-9 6-9s6 4 6 9" />
            <path d="M8 9v4" />
        </svg>
    ),
    bot: (
        <svg width="14" height="14" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="5" width="12" height="10" rx="2" />
            <circle cx="7" cy="10" r="1" fill="currentColor" stroke="none" />
            <circle cx="11" cy="10" r="1" fill="currentColor" stroke="none" />
            <path d="M9 2v3M6 2h6" />
        </svg>
    ),
};

/* ────── Main Component ────── */

export default function VoicePrintLogin() {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const registerFileRef = useRef<HTMLInputElement>(null);
    const loginFileRef = useRef<HTMLInputElement>(null);
    const verifyFileRef = useRef<HTMLInputElement>(null);

    // ─── State ───
    const [registerForm, setRegisterForm] = useState({ name: '', description: '' });
    const [registerFile, setRegisterFile] = useState<File | null>(null);
    const [loginFile, setLoginFile] = useState<File | null>(null);
    const [verifyFile, setVerifyFile] = useState<File | null>(null);
    const [loginResult, setLoginResult] = useState<any>(null);
    const [verifyResult, setVerifyResult] = useState<any>(null);

    // ─── Queries ───
    const { data: serviceStatus, isLoading: statusLoading } = useQuery({
        queryKey: ['voiceprint-status'],
        queryFn: () => voicePrintExtendedApi.getStatus(),
        refetchInterval: 30000,
    });

    const { data: voicePrints = [], isLoading: printsLoading } = useQuery({
        queryKey: ['voiceprint-list'],
        queryFn: () => voicePrintApi.list(),
        refetchInterval: 15000,
    });

    // ─── Mutations ───
    const registerMutation = useMutation({
        mutationFn: (data: any) => voicePrintApi.register(data),
        onSuccess: () => {
            setRegisterForm({ name: '', description: '' });
            setRegisterFile(null);
            queryClient.invalidateQueries({ queryKey: ['voiceprint-list'] });
        },
    });

    const loginMutation = useMutation({
        mutationFn: (data: any) => voicePrintExtendedApi.login(data),
        onSuccess: (response: any) => {
            setLoginResult(response);
        },
    });

    const verifyMutation = useMutation({
        mutationFn: (data: any) => voicePrintApi.verify(data),
        onSuccess: (response: any) => {
            setVerifyResult(response);
        },
    });

    // ─── Handlers ───
    const handleRegister = () => {
        if (!registerForm.name.trim()) return;
        registerMutation.mutate({
            name: registerForm.name,
            description: registerForm.description,
            ...(registerFile ? { file_name: registerFile.name } : {}),
        });
    };

    const handleLogin = () => {
        if (!loginFile) return;
        loginMutation.mutate({
            file_name: loginFile.name,
        });
        setLoginResult(null);
    };

    const handleVerify = () => {
        if (!verifyFile) return;
        verifyMutation.mutate({
            file_name: verifyFile.name,
        });
        setVerifyResult(null);
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            {/* ─── Header / Status Banner ─── */}
            <div style={{
                borderRadius: '24px',
                padding: '22px',
                background: 'linear-gradient(135deg, rgba(139,92,246,0.12), rgba(12,18,28,0.84) 48%, rgba(5,6,10,0.96))',
                border: '1px solid rgba(255,255,255,0.08)',
                boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '18px', alignItems: 'flex-start' }}>
                    <div style={{ maxWidth: '760px' }}>
                        <div style={{
                            display: 'inline-flex', alignItems: 'center', gap: '8px',
                            padding: '6px 10px', borderRadius: '999px',
                            background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)',
                            fontSize: '12px', marginBottom: '14px',
                        }}>
                            <span style={{
                                width: '8px', height: '8px', borderRadius: '50%',
                                background: serviceStatus?.available ? 'var(--success)' : 'var(--text-tertiary)',
                                boxShadow: serviceStatus?.available ? '0 0 18px rgba(139,92,246,0.85)' : 'none',
                            }} />
                            {t('voiceprint.badge', '声纹认证')}
                        </div>
                        <h1 style={{ fontSize: '28px', fontWeight: 700, margin: 0, letterSpacing: '-0.04em', color: 'var(--text-primary)' }}>
                            {t('voiceprint.title', '声纹登录')}
                        </h1>
                        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', margin: '10px 0 0', lineHeight: 1.75, maxWidth: '68ch' }}>
                            {t('voiceprint.subtitle', '通过声纹识别进行安全登录与身份验证')}
                        </p>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '10px', minWidth: '260px' }}>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('voiceprint.registered', '已注册')}</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px' }}>{voicePrints.length}</div>
                        </div>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('voiceprint.serviceStatus', '服务状态')}</div>
                            <div style={{ fontSize: '15px', fontWeight: 600, marginTop: '6px', color: serviceStatus?.available ? 'var(--success)' : 'var(--text-tertiary)' }}>
                                {statusLoading ? '...' : (serviceStatus?.available ? t('voiceprint.available', '可用') : t('voiceprint.unavailable', '不可用'))}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* ─── Two-Column Layout ─── */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '18px', alignItems: 'flex-start' }}>
                {/* ─── Left: Voice Print List + Register Form ─── */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                    {/* Registered Voice Prints */}
                    <div style={{
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-lg)', overflow: 'hidden',
                    }}>
                        <div style={{
                            padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)',
                            display: 'flex', alignItems: 'center', gap: '6px',
                            background: 'rgba(255,255,255,0.03)',
                        }}>
                            <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.fingerprint}</span>
                            <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                                {t('voiceprint.registeredList', '已注册声纹')}
                            </h3>
                            <span style={{ marginLeft: 'auto', fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                {voicePrints.length} {t('voiceprint.records', '条')}
                            </span>
                        </div>
                        <div style={{ maxHeight: '320px', overflowY: 'auto' }}>
                            {printsLoading ? (
                                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                                    {t('common.loading')}
                                </div>
                            ) : voicePrints.length === 0 ? (
                                <div style={{
                                    textAlign: 'center', padding: '40px 20px',
                                    color: 'var(--text-tertiary)', fontSize: '13px',
                                }}>
                                    {t('voiceprint.noRecords', '暂无注册声纹')}
                                </div>
                            ) : (
                                voicePrints.map((vp: any, i: number) => (
                                    <div key={vp.id || i} style={{
                                        display: 'flex', alignItems: 'center', gap: '10px',
                                        padding: '10px 16px',
                                        borderBottom: i < voicePrints.length - 1 ? '1px solid var(--border-subtle)' : 'none',
                                        transition: 'background 0.15s',
                                    }}
                                        onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'var(--bg-hover)'; }}
                                        onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'transparent'; }}
                                    >
                                        <div style={{
                                            width: '32px', height: '32px', borderRadius: 'var(--radius-md)',
                                            background: 'var(--bg-tertiary)', border: '1px solid var(--border-subtle)',
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            color: 'var(--text-tertiary)', flexShrink: 0,
                                        }}>
                                            {Icons.fingerprint}
                                        </div>
                                        <div style={{ flex: 1, minWidth: 0 }}>
                                            <div style={{ fontSize: '13px', fontWeight: 500, color: 'var(--text-primary)' }}>
                                                {vp.name || vp.id}
                                            </div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                {vp.description || vp.status || '-'}
                                            </div>
                                        </div>
                                        <span style={{
                                            fontSize: '11px', padding: '2px 8px',
                                            borderRadius: 'var(--radius-sm)',
                                            background: vp.status === 'active' ? 'rgba(16,185,129,0.12)' : 'var(--bg-tertiary)',
                                            color: vp.status === 'active' ? 'var(--success)' : 'var(--text-tertiary)',
                                        }}>
                                            {vp.status === 'active' ? t('voiceprint.active', '活跃') : (vp.status || '-')}
                                        </span>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>

                    {/* Register Form */}
                    <div style={{
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-lg)', overflow: 'hidden',
                    }}>
                        <div style={{
                            padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)',
                            display: 'flex', alignItems: 'center', gap: '6px',
                            background: 'rgba(255,255,255,0.03)',
                        }}>
                            <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.mic}</span>
                            <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                                {t('voiceprint.register', '注册声纹')}
                            </h3>
                        </div>
                        <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                            <div>
                                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>
                                    {t('voiceprint.name', '名称')} *
                                </label>
                                <input
                                    type="text"
                                    value={registerForm.name}
                                    onChange={e => setRegisterForm(prev => ({ ...prev, name: e.target.value }))}
                                    placeholder={t('voiceprint.namePlaceholder', '为声纹起一个名称')}
                                    style={{
                                        width: '100%', boxSizing: 'border-box',
                                        padding: '8px 12px', fontSize: '13px',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary)',
                                        border: '1px solid var(--border-default)', borderRadius: 'var(--radius-md)',
                                        outline: 'none', transition: 'border-color 0.15s',
                                    }}
                                    onFocus={e => { e.currentTarget.style.borderColor = 'var(--accent-primary)'; e.currentTarget.style.boxShadow = '0 0 0 2px var(--accent-subtle)'; }}
                                    onBlur={e => { e.currentTarget.style.borderColor = 'var(--border-default)'; e.currentTarget.style.boxShadow = 'none'; }}
                                />
                            </div>
                            <div>
                                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>
                                    {t('voiceprint.description', '描述')}
                                </label>
                                <input
                                    type="text"
                                    value={registerForm.description}
                                    onChange={e => setRegisterForm(prev => ({ ...prev, description: e.target.value }))}
                                    placeholder={t('voiceprint.descriptionPlaceholder', '例如：主管理员声纹')}
                                    style={{
                                        width: '100%', boxSizing: 'border-box',
                                        padding: '8px 12px', fontSize: '13px',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary)',
                                        border: '1px solid var(--border-default)', borderRadius: 'var(--radius-md)',
                                        outline: 'none', transition: 'border-color 0.15s',
                                    }}
                                    onFocus={e => { e.currentTarget.style.borderColor = 'var(--accent-primary)'; e.currentTarget.style.boxShadow = '0 0 0 2px var(--accent-subtle)'; }}
                                    onBlur={e => { e.currentTarget.style.borderColor = 'var(--border-default)'; e.currentTarget.style.boxShadow = 'none'; }}
                                />
                            </div>
                            {/* Audio File Upload */}
                            <div>
                                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>
                                    {t('voiceprint.audioFile', '音频文件')}
                                </label>
                                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                                    <input
                                        ref={registerFileRef}
                                        type="file"
                                        accept="audio/*"
                                        onChange={e => setRegisterFile(e.target.files?.[0] || null)}
                                        style={{ display: 'none' }}
                                    />
                                    <button
                                        className="btn btn-secondary"
                                        onClick={() => registerFileRef.current?.click()}
                                        style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                                    >
                                        <span style={{ display: 'flex' }}>{Icons.upload}</span>
                                        {registerFile ? registerFile.name : t('voiceprint.selectFile', '选择文件')}
                                    </button>
                                    {/* Recording placeholder button */}
                                    <button
                                        className="btn btn-secondary"
                                        style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                                        title={t('voiceprint.record', '录音（开发中）')}
                                    >
                                        <span style={{ display: 'flex' }}>{Icons.mic}</span>
                                        {t('voiceprint.record', '录音')}
                                    </button>
                                </div>
                                {registerFile && (
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                        {(registerFile.size / 1024).toFixed(1)} KB
                                    </div>
                                )}
                            </div>
                            <button
                                className={`btn ${registerForm.name.trim() ? 'btn-primary' : 'btn-secondary'}`}
                                onClick={handleRegister}
                                disabled={!registerForm.name.trim() || registerMutation.isPending}
                                style={{ marginTop: '4px' }}
                            >
                                {registerMutation.isPending ? t('voiceprint.registering', '注册中...') : t('voiceprint.registerButton', '注册')}
                            </button>
                            {registerMutation.isError && (
                                <div style={{ fontSize: '12px', color: 'var(--error)' }}>
                                    {t('voiceprint.registerError', '注册失败，请重试')}
                                </div>
                            )}
                            {registerMutation.isSuccess && (
                                <div style={{ fontSize: '12px', color: 'var(--success)' }}>
                                    {t('voiceprint.registerSuccess', '声纹注册成功')}
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* ─── Right: Login + Verify ─── */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                    {/* Voice Print Login */}
                    <div style={{
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-lg)', overflow: 'hidden',
                    }}>
                        <div style={{
                            padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)',
                            display: 'flex', alignItems: 'center', gap: '6px',
                            background: 'rgba(255,255,255,0.03)',
                        }}>
                            <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.key}</span>
                            <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                                {t('voiceprint.login', '声纹登录')}
                            </h3>
                        </div>
                        <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                            <div>
                                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>
                                    {t('voiceprint.loginAudio', '登录音频')}
                                </label>
                                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                                    <input
                                        ref={loginFileRef}
                                        type="file"
                                        accept="audio/*"
                                        onChange={e => { setLoginFile(e.target.files?.[0] || null); setLoginResult(null); }}
                                        style={{ display: 'none' }}
                                    />
                                    <button
                                        className="btn btn-secondary"
                                        onClick={() => loginFileRef.current?.click()}
                                        style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                                    >
                                        <span style={{ display: 'flex' }}>{Icons.upload}</span>
                                        {loginFile ? loginFile.name : t('voiceprint.selectFile', '选择文件')}
                                    </button>
                                    <button
                                        className="btn btn-secondary"
                                        style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                                        title={t('voiceprint.record', '录音（开发中）')}
                                    >
                                        <span style={{ display: 'flex' }}>{Icons.mic}</span>
                                        {t('voiceprint.record', '录音')}
                                    </button>
                                </div>
                                {loginFile && (
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                        {(loginFile.size / 1024).toFixed(1)} KB
                                    </div>
                                )}
                            </div>
                            <button
                                className={`btn ${loginFile ? 'btn-primary' : 'btn-secondary'}`}
                                onClick={handleLogin}
                                disabled={!loginFile || loginMutation.isPending}
                            >
                                {loginMutation.isPending ? t('voiceprint.loggingIn', '登录中...') : t('voiceprint.loginButton', '声纹登录')}
                            </button>
                            {loginMutation.isError && (
                                <div style={{ fontSize: '12px', color: 'var(--error)' }}>
                                    {t('voiceprint.loginError', '登录失败，请确认声纹已注册')}
                                </div>
                            )}
                            {loginResult && (
                                <div style={{
                                    padding: '10px 12px', borderRadius: 'var(--radius-md)',
                                    background: loginResult.success !== false ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)',
                                    border: `1px solid ${loginResult.success !== false ? 'rgba(16,185,129,0.2)' : 'rgba(239,68,68,0.2)'}`,
                                }}>
                                    <div style={{ fontSize: '13px', fontWeight: 500, color: loginResult.success !== false ? 'var(--success)' : 'var(--error)', marginBottom: '4px' }}>
                                        {loginResult.success !== false ? t('voiceprint.loginSuccess', '登录成功') : t('voiceprint.loginFailed', '登录失败')}
                                    </div>
                                    {loginResult.user && (
                                        <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                                            {loginResult.user.name || loginResult.user.email || JSON.stringify(loginResult.user)}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Voice Print Verify */}
                    <div style={{
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-lg)', overflow: 'hidden',
                    }}>
                        <div style={{
                            padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)',
                            display: 'flex', alignItems: 'center', gap: '6px',
                            background: 'rgba(255,255,255,0.03)',
                        }}>
                            <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.shield}</span>
                            <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                                {t('voiceprint.verify', '声纹验证')}
                            </h3>
                        </div>
                        <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                            <div>
                                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>
                                    {t('voiceprint.verifyAudio', '验证音频')}
                                </label>
                                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                                    <input
                                        ref={verifyFileRef}
                                        type="file"
                                        accept="audio/*"
                                        onChange={e => { setVerifyFile(e.target.files?.[0] || null); setVerifyResult(null); }}
                                        style={{ display: 'none' }}
                                    />
                                    <button
                                        className="btn btn-secondary"
                                        onClick={() => verifyFileRef.current?.click()}
                                        style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                                    >
                                        <span style={{ display: 'flex' }}>{Icons.upload}</span>
                                        {verifyFile ? verifyFile.name : t('voiceprint.selectFile', '选择文件')}
                                    </button>
                                    <button
                                        className="btn btn-secondary"
                                        style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                                        title={t('voiceprint.record', '录音（开发中）')}
                                    >
                                        <span style={{ display: 'flex' }}>{Icons.mic}</span>
                                        {t('voiceprint.record', '录音')}
                                    </button>
                                </div>
                                {verifyFile && (
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                                        {(verifyFile.size / 1024).toFixed(1)} KB
                                    </div>
                                )}
                            </div>
                            <button
                                className={`btn ${verifyFile ? 'btn-primary' : 'btn-secondary'}`}
                                onClick={handleVerify}
                                disabled={!verifyFile || verifyMutation.isPending}
                            >
                                {verifyMutation.isPending ? t('voiceprint.verifying', '验证中...') : t('voiceprint.verifyButton', '验证')}
                            </button>
                            {verifyMutation.isError && (
                                <div style={{ fontSize: '12px', color: 'var(--error)' }}>
                                    {t('voiceprint.verifyError', '验证失败')}
                                </div>
                            )}
                            {verifyResult && (
                                <div style={{
                                    padding: '10px 12px', borderRadius: 'var(--radius-md)',
                                    background: verifyResult.match !== false ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)',
                                    border: `1px solid ${verifyResult.match !== false ? 'rgba(16,185,129,0.2)' : 'rgba(239,68,68,0.2)'}`,
                                }}>
                                    <div style={{ fontSize: '13px', fontWeight: 500, color: verifyResult.match !== false ? 'var(--success)' : 'var(--error)' }}>
                                        {verifyResult.match !== false
                                            ? t('voiceprint.verifyMatch', '声纹匹配')
                                            : t('voiceprint.verifyNoMatch', '声纹不匹配')}
                                    </div>
                                    {verifyResult.confidence && (
                                        <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '4px' }}>
                                            {t('voiceprint.confidence', '置信度')}: {(verifyResult.confidence * 100).toFixed(1)}%
                                        </div>
                                    )}
                                    {verifyResult.matched_name && (
                                        <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '2px' }}>
                                            {t('voiceprint.matchedName', '匹配')}: {verifyResult.matched_name}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
