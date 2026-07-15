import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores';
import { authApi, systemApi, voicePrintExtendedApi, wsApi } from '../services/api';
import MarkdownRenderer from '../components/MarkdownRenderer';
import type { User, UserIdentity, AccessLevel } from '../types';

type LoginTab = 'phone' | 'voiceprint';

interface ChatMessage {
    role: 'user' | 'assistant' | 'system';
    content: string;
    timestamp: string;
    audioUrl?: string;
}

function blobToBase64(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => resolve((reader.result as string).split(',')[1]);
        reader.onerror = reject;
        reader.readAsDataURL(blob);
    });
}

function buildUser(res: any): User {
    return {
        id: res.user.id, username: res.user.name, email: res.user.email || '',
        display_name: res.user.name, role: 'org_admin', tenant_id: res.user.tenantId,
        department_code: res.user.department || undefined, identity: res.user.identity as UserIdentity,
        access_level: res.user.accessLevel as AccessLevel, is_active: true, created_at: new Date().toISOString(),
    };
}

export default function Login() {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();
    const setAuth = useAuthStore((s) => s.setAuth);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [systemStatus, setSystemStatus] = useState<{ hasFounder: boolean; isFirstUser: boolean } | null>(null);
    const [countdown, setCountdown] = useState(0);
    const [testCode, setTestCode] = useState('');
    const [isTestMode] = useState(true);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [activeTab, setActiveTab] = useState<LoginTab>('phone');
    const isChinese = i18n.language?.startsWith('zh');

    const [form, setForm] = useState({ phone: '', code: '' });

    // Voiceprint login — press-to-talk
    const [vpRecording, setVpRecording] = useState(false);
    const [vpLoading, setVpLoading] = useState(false);
    const [vpError, setVpError] = useState('');
    const vpMediaRef = useRef<MediaRecorder | null>(null);
    const vpChunksRef = useRef<Blob[]>([]);
    const vpStreamRef = useRef<MediaStream | null>(null);

    // ── FrontDesk chat state (auto-connect on mount) ──
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [chatInput, setChatInput] = useState('');
    const [connected, setConnected] = useState(false);
    const [isWaiting, setIsWaiting] = useState(false);
    const [voiceMode, setVoiceMode] = useState(false);
    const [isRecording, setIsRecording] = useState(false);
    const [voiceSupported, setVoiceSupported] = useState(true);
    const wsRef = useRef<WebSocket | null>(null);
    const mediaRecorderRef = useRef<MediaRecorder | null>(null);
    const audioChunksRef = useRef<Blob[]>([]);
    const streamRef = useRef<MediaStream | null>(null);
    const recordingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const currentAudioRef = useRef<HTMLAudioElement | null>(null);
    const anonymousId = useRef(`guest_${Date.now().toString(36)}`);
    const chatContainerRef = useRef<HTMLDivElement | null>(null);

    // ── Init ──
    useEffect(() => {
        document.documentElement.setAttribute('data-theme', 'dark');
        systemApi.status()
            .then(status => setSystemStatus(status))
            .catch(() => setSystemStatus({ hasFounder: false, isFirstUser: true }));
        setVoiceSupported(typeof navigator.mediaDevices !== 'undefined' && typeof MediaRecorder !== 'undefined');
    }, []);

    // ── Auto-connect WebSocket for public chat ──
    const connectWs = useCallback(() => {
        // 如果已有连接且正在连接/已连接，不重复创建
        if (wsRef.current && wsRef.current.readyState <= WebSocket.OPEN) {
            return;
        }
        const wsUrl = wsApi.publicUrl('anonymous');
        const ws = new WebSocket(wsUrl);
        wsRef.current = ws;
        ws.onopen = () => setConnected(true);
        ws.onclose = () => setConnected(false);
        ws.onerror = () => setConnected(false);
        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'connected' || data.type === 'pong' || data.type === 'PONG') return;
                if (data.type === 'thinking') {
                    setIsWaiting(true);
                    setMessages(prev => [...prev, { role: 'assistant', content: '...', timestamp: new Date().toISOString() }]);
                    return;
                }
                if (data.type === 'done') {
                    setIsWaiting(false);
                    setMessages(prev => {
                        const filtered = prev.filter(m => !(m.role === 'assistant' && m.content === '...'));
                        const newMsg: ChatMessage = { role: 'assistant', content: data.content || '', timestamp: new Date().toISOString() };
                        if (data.audio) {
                            const audioBlob = new Blob([Uint8Array.from(atob(data.audio), c => c.charCodeAt(0))], { type: 'audio/wav' });
                            newMsg.audioUrl = URL.createObjectURL(audioBlob);
                        }
                        return [...filtered, newMsg];
                    });
                    return;
                }
                if (data.type === 'chunk' || data.type === 'response') {
                    setIsWaiting(false);
                    setMessages(prev => {
                        const filtered = prev.filter(m => !(m.role === 'assistant' && m.content === '...'));
                        const last = prev[prev.length - 1];
                        if (last && last.role === 'assistant' && last.content !== '...') {
                            return [...prev.slice(0, -1), { ...last, content: last.content + (data.content || '') }];
                        }
                        return [...filtered, { role: 'assistant', content: data.content || '', timestamp: new Date().toISOString() }];
                    });
                    return;
                }
                if (data.type === 'asr_result' && data.text) {
                    setMessages(prev => [...prev, { role: 'user', content: `🎤 ${data.text}`, timestamp: new Date().toISOString() }]);
                    return;
                }
                if (data.type === 'error') {
                    setIsWaiting(false);
                    setMessages(prev => [...prev, { role: 'system', content: data.message || 'Error', timestamp: new Date().toISOString() }]);
                }
            } catch { /* ignore */ }
        };
    }, []);

    useEffect(() => {
        connectWs();
    }, [connectWs]);
    useEffect(() => {
        return () => {
            if (currentAudioRef.current) currentAudioRef.current.pause();
            messages.forEach(m => { if (m.audioUrl) URL.revokeObjectURL(m.audioUrl); });
        };
    }, []);
    useEffect(() => { if (chatContainerRef.current) chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight; }, [messages]);

    // ── Chat actions ──
    const sendMessage = () => {
        if (!chatInput.trim() || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;
        const content = chatInput.trim();
        setMessages(prev => [...prev, { role: 'user', content, timestamp: new Date().toISOString() }]);
        setChatInput(''); setIsWaiting(true);
        wsRef.current.send(JSON.stringify({ type: 'chat', content, userId: anonymousId.current }));
    };
    const handleChatKeyDown = (e: React.KeyboardEvent) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); } };

    const startRecording = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: { sampleRate: 16000, channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true } });
            streamRef.current = stream;
            const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/wav';
            const mr = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 128000 });
            mediaRecorderRef.current = mr; audioChunksRef.current = [];
            mr.ondataavailable = (e) => { if (e.data.size > 0) audioChunksRef.current.push(e.data); };
            mr.onstop = async () => {
                if (audioChunksRef.current.length === 0) return;
                const blob = new Blob(audioChunksRef.current, { type: mr.mimeType || 'audio/webm' }); audioChunksRef.current = [];
                if (blob.size < 1000) { setMessages(prev => [...prev, { role: 'system', content: isChinese ? '录音时间太短' : 'Recording too short', timestamp: new Date().toISOString() }]); return; }
                try {
                    const base64 = await blobToBase64(blob);
                    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) { setIsWaiting(true); wsRef.current.send(JSON.stringify({ type: 'audio_full', audio: base64, userId: anonymousId.current })); }
                } catch { setMessages(prev => [...prev, { role: 'system', content: isChinese ? '音频处理失败' : 'Audio processing failed', timestamp: new Date().toISOString() }]); }
            };
            mr.start(100); setIsRecording(true);
            recordingTimerRef.current = setTimeout(() => { if (mr.state === 'recording') stopRecording(); }, 60000);
        } catch (err: any) {
            const msg = err.name === 'NotAllowedError' ? (isChinese ? '麦克风权限被拒绝' : 'Microphone permission denied') : err.name === 'NotFoundError' ? (isChinese ? '未找到麦克风' : 'No microphone found') : (isChinese ? '无法访问麦克风' : 'Cannot access microphone');
            setMessages(prev => [...prev, { role: 'system', content: msg, timestamp: new Date().toISOString() }]);
        }
    };
    const stopRecording = () => {
        if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') mediaRecorderRef.current.stop();
        setIsRecording(false);
        if (recordingTimerRef.current) { clearTimeout(recordingTimerRef.current); recordingTimerRef.current = null; }
        if (streamRef.current) { streamRef.current.getTracks().forEach(t => t.stop()); streamRef.current = null; }
    };
    const playAudio = (url: string) => { if (currentAudioRef.current) currentAudioRef.current.pause(); const audio = new Audio(url); currentAudioRef.current = audio; audio.play().catch(() => undefined); };

    // ── Countdown ──
    useEffect(() => { if (countdown > 0) { const timer = setTimeout(() => setCountdown(countdown - 1), 1000); return () => clearTimeout(timer); } }, [countdown]);

    const toggleLang = () => i18n.changeLanguage(i18n.language === 'zh' ? 'en' : 'zh');

    // ── Login handlers ──
    const handleSendCode = async () => {
        if (!form.phone) { setError(t('phoneLogin.enterPhone')); return; }
        setError(''); setLoading(true);
        try { const res = await authApi.sendSmsCode({ phone: form.phone, type: 'login' }); setCountdown(60); if (res.code) { setTestCode(res.code); setForm(prev => ({ ...prev, code: res.code || '' })); } }
        catch (err: any) { setError(err.message || t('phoneLogin.failedToSendCode')); }
        finally { setLoading(false); }
    };
    const handlePhoneSubmit = async (e: React.FormEvent) => {
        e.preventDefault(); setError(''); setLoading(true);
        try { const res = await authApi.phoneLogin({ phone: form.phone, code: form.code }); setAuth(buildUser(res), res.accessToken); navigate('/'); }
        catch (err: any) { const msg = err.message || ''; if (msg.includes('invalid') || msg.includes('incorrect')) setError(t('phoneLogin.invalidOrExpiredCode')); else if (msg.includes('not found')) setError(t('phoneLogin.userNotFoundRegister')); else setError(msg || t('phoneLogin.loginFailed')); }
        finally { setLoading(false); }
    };

    // ── Voiceprint: press-to-talk instant login ──
    const vpStartRecording = async () => {
        setVpError('');
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: { sampleRate: 16000, channelCount: 1, echoCancellation: true, noiseSuppression: true } });
            vpStreamRef.current = stream;
            const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/wav';
            const mr = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 128000 });
            vpMediaRef.current = mr; vpChunksRef.current = [];
            mr.ondataavailable = (e) => { if (e.data.size > 0) vpChunksRef.current.push(e.data); };
            mr.onstop = async () => {
                if (vpChunksRef.current.length === 0) return;
                const blob = new Blob(vpChunksRef.current, { type: mr.mimeType || 'audio/webm' }); vpChunksRef.current = [];
                if (blob.size < 1000) { setVpError(isChinese ? '录音时间太短，请重试' : 'Recording too short, please try again'); return; }
                setVpLoading(true);
                try {
                    const res = await voicePrintExtendedApi.login(blob);
                    if (res.success !== false && res.accessToken) { setAuth(buildUser(res), res.accessToken); navigate('/'); }
                    else { setVpError(isChinese ? '声纹未匹配，请重试或使用手机登录' : 'Voiceprint not matched, please retry or use phone login'); }
                } catch (err: any) { setVpError(err.message || (isChinese ? '声纹登录失败' : 'Voiceprint login failed')); }
                finally { setVpLoading(false); }
            };
            mr.start(100); setVpRecording(true);
        } catch (err: any) {
            setVpError(err.name === 'NotAllowedError' ? (isChinese ? '麦克风权限被拒绝' : 'Microphone permission denied') : (isChinese ? '无法访问麦克风' : 'Cannot access microphone'));
        }
    };
    const vpStopRecording = () => {
        if (vpMediaRef.current && vpMediaRef.current.state === 'recording') vpMediaRef.current.stop();
        setVpRecording(false);
        if (vpStreamRef.current) { vpStreamRef.current.getTracks().forEach(t => t.stop()); vpStreamRef.current = null; }
    };

    if (systemStatus && !systemStatus.hasFounder) return <RegisterPage isChinese={isChinese} toggleLang={toggleLang} />;

    const langLabel = isChinese ? '中文' : 'EN';

    return (
        <div className="login-page" style={{ position: 'relative' }}>
            <div className="login-hero" style={{ flex: 1, width: '100%' }}>
                <div className="login-hero-bg" />
                <div className="login-hero-content">
                    {/* ── Left column: hero text + features ── */}
                    <div className="login-hero-left">
                        <div className="login-hero-topbar">
                            <div className="login-hero-lang" onClick={toggleLang}>
                                <span>🌐</span><span>{langLabel}</span>
                            </div>
                            <button className="login-drawer-trigger" onClick={() => setDrawerOpen(true)}>
                                {t('phoneLogin.openDrawer')}
                            </button>
                        </div>
                        <div className="login-hero-badge">
                            <span className="login-hero-badge-dot" />{t('login.hero.badge')}
                        </div>
                        <h1 className="login-hero-title">
                            {t('login.hero.title')}<br />
                            <span style={{ fontSize: '0.65em', fontWeight: 600, opacity: 0.85 }}>{t('login.hero.subtitle')}</span>
                        </h1>
                        <p className="login-hero-desc" dangerouslySetInnerHTML={{ __html: t('login.hero.description') }} />
                        <div className="login-hero-features">
                            <div className="login-hero-feature">
                                <span className="login-hero-feature-icon">🧠</span>
                                <div><div className="login-hero-feature-title">{t('login.hero.features.multiAgent.title')}</div><div className="login-hero-feature-desc">{t('login.hero.features.multiAgent.description')}</div></div>
                            </div>
                            <div className="login-hero-feature">
                                <span className="login-hero-feature-icon">⚡</span>
                                <div><div className="login-hero-feature-title">{t('login.hero.features.persistentMemory.title')}</div><div className="login-hero-feature-desc">{t('login.hero.features.persistentMemory.description')}</div></div>
                            </div>
                            <div className="login-hero-feature">
                                <span className="login-hero-feature-icon">📚</span>
                                <div><div className="login-hero-feature-title">{t('login.hero.features.agentPlaza.title')}</div><div className="login-hero-feature-desc">{t('login.hero.features.agentPlaza.description')}</div></div>
                            </div>
                        </div>
                    </div>

                    {/* ── Right column: chat panel ── */}
                    <div className="login-hero-chat">
                        <div className="login-hero-chat-header">
                            <div className="login-hero-chat-brand"><span>💬</span><span>{isChinese ? '智能前台' : 'Smart Front Desk'}</span></div>
                            <span className={`login-hero-chat-status ${connected ? 'online' : 'offline'}`}>{connected ? (isChinese ? '在线' : 'Online') : (isChinese ? '离线' : 'Offline')}</span>
                        </div>
                        <div className="login-hero-chat-messages" ref={chatContainerRef}>
                            {messages.length === 0 && (
                                <div className="login-hero-chat-empty">
                                    <div style={{ fontSize: 32, marginBottom: 8 }}>💬</div>
                                    <p>{isChinese ? '你好！我是智能前台，有什么可以帮你的？' : "Hi! I'm the Smart Front Desk. How can I help?"}</p>
                                    <p className="login-hero-chat-hint">{isChinese ? '文字 / 语音 · 无需登录' : 'Text & Voice · No login required'}</p>
                                </div>
                            )}
                            {messages.map((msg, i) => (
                                <div key={i} className={`login-hero-chat-msg ${msg.role}`}>
                                    <div className="login-hero-chat-bubble" style={{ background: msg.role === 'user' ? 'var(--accent)' : msg.role === 'system' ? 'rgba(255,80,80,0.12)' : 'rgba(255,255,255,0.06)', color: msg.role === 'user' ? '#fff' : msg.role === 'system' ? 'var(--error)' : 'var(--text-primary)' }}>
                                        {msg.role === 'assistant' ? <MarkdownRenderer content={msg.content} /> : msg.content}
                                        {msg.audioUrl && <button onClick={() => playAudio(msg.audioUrl!)} className="login-hero-chat-play">🔊 {isChinese ? '播放' : 'Play'}</button>}
                                    </div>
                                </div>
                            ))}
                        </div>
                        <div className="login-hero-chat-input">
                            {voiceMode ? (
                                <div className="login-hero-chat-voice">
                                    <button onMouseDown={startRecording} onMouseUp={stopRecording} onTouchStart={startRecording} onTouchEnd={stopRecording} disabled={isWaiting || !connected} className="login-hero-chat-voice-btn" style={{ border: isRecording ? '3px solid var(--error)' : '3px solid var(--accent)', background: isRecording ? 'rgba(255,80,80,0.2)' : 'rgba(255,255,255,0.06)', color: isRecording ? 'var(--error)' : 'var(--accent)' }}>
                                        {isRecording ? '⏹' : '🎤'}
                                    </button>
                                    <span className="login-hero-chat-voice-hint">{isRecording ? (isChinese ? '录音中…松开停止' : 'Recording… release to stop') : isWaiting ? (isChinese ? '处理中…' : 'Processing…') : (isChinese ? '按住说话' : 'Hold to talk')}</span>
                                </div>
                            ) : (
                                <div className="login-hero-chat-text-row">
                                    <textarea value={chatInput} onChange={e => setChatInput(e.target.value)} onKeyDown={handleChatKeyDown} placeholder={isChinese ? '输入消息…' : 'Type a message…'} disabled={isWaiting || !connected} rows={1} className="login-hero-chat-textarea" />
                                    <button onClick={sendMessage} disabled={!chatInput.trim() || isWaiting || !connected} className="login-hero-chat-send" style={{ background: chatInput.trim() && !isWaiting && connected ? 'var(--accent)' : 'rgba(255,255,255,0.06)', color: chatInput.trim() && !isWaiting && connected ? '#fff' : 'var(--text-tertiary)' }}>
                                        {isChinese ? '发送' : 'Send'}
                                    </button>
                                </div>
                            )}
                            <div className="login-hero-chat-footer">
                                <span>{isChinese ? 'Qwen3 闲聊神经元 · 无需登录' : 'Powered by Qwen3 · No login required'}</span>
                                {voiceSupported && (
                                    <button onClick={() => setVoiceMode(!voiceMode)} className="login-hero-chat-mode-btn" style={{ color: voiceMode ? 'var(--accent)' : 'var(--text-quaternary)' }}>
                                        {voiceMode ? '⌨️' : '🎤'} {voiceMode ? (isChinese ? '文字' : 'Text') : (isChinese ? '语音' : 'Voice')}
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Drawer overlay */}
            {drawerOpen && <div className="login-drawer-overlay" onClick={() => setDrawerOpen(false)} />}

            {/* Drawer panel */}
            <div className={`login-drawer ${drawerOpen ? 'login-drawer--open' : ''}`}>
                <button className="login-drawer-close" onClick={() => setDrawerOpen(false)}>✕</button>
                <div className="login-drawer-inner">
                    <div className="login-form-header">
                        <div className="login-form-logo">
                            <img src="/logo-black.png" className="login-logo-img" alt="" style={{ width: 28, height: 28, marginRight: 8, verticalAlign: 'middle' }} />
                            Living Agent
                        </div>
                        <h2 className="login-form-title">{t('phoneLogin.drawerTitle')}</h2>
                        <p className="login-form-subtitle">{t('phoneLogin.drawerSubtitle')}</p>
                    </div>

                    <div className="login-drawer-tabs">
                        <button className={`login-drawer-tab ${activeTab === 'phone' ? 'login-drawer-tab--active' : ''}`} onClick={() => { setActiveTab('phone'); setError(''); setVpError(''); }}>
                            <span className="login-drawer-tab-icon">📱</span>{t('phoneLogin.tabPhone')}
                        </button>
                        <button className={`login-drawer-tab ${activeTab === 'voiceprint' ? 'login-drawer-tab--active' : ''}`} onClick={() => { setActiveTab('voiceprint'); setError(''); setVpError(''); }}>
                            <span className="login-drawer-tab-icon">🎙️</span>{t('phoneLogin.tabVoiceprint')}
                        </button>
                    </div>

                    {/* Phone login */}
                    {activeTab === 'phone' && (
                        <div className="login-drawer-form" style={{ animation: 'drawerTabFadeIn 0.25s ease' }}>
                            {error && <div className="login-error"><span>⚠</span> {error}</div>}
                            {isTestMode && testCode && (
                                <div style={{ background: 'rgba(76,175,80,0.15)', border: '1px solid rgba(76,175,80,0.4)', borderRadius: 8, padding: '10px 14px', marginBottom: 16, fontSize: 13, color: '#4caf50', display: 'flex', alignItems: 'center', gap: 8 }}>
                                    <span>🧪</span><span>{t('phoneLogin.testModeAutoFill')}</span><strong style={{ letterSpacing: 4, fontSize: 16 }}>{testCode}</strong>
                                </div>
                            )}
                            <form onSubmit={handlePhoneSubmit} className="login-form">
                                <div className="login-field"><label>{t('phoneLogin.phoneNumber')}</label><input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} required autoFocus placeholder={t('phoneLogin.enterPhoneNumber')} /></div>
                                <div className="login-field">
                                    <label>{t('phoneLogin.verificationCode')}</label>
                                    <div style={{ display: 'flex', gap: 8 }}>
                                        <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required placeholder={t('phoneLogin.enterCode')} style={{ flex: 1 }} maxLength={6} />
                                        <button type="button" className="btn btn-ghost" onClick={handleSendCode} disabled={countdown > 0 || loading} style={{ whiteSpace: 'nowrap', minWidth: 100, opacity: countdown > 0 ? 0.7 : 1 }}>{countdown > 0 ? `${countdown}s` : t('phoneLogin.sendCode')}</button>
                                    </div>
                                </div>
                                <button className="login-submit" type="submit" disabled={loading}>{loading ? <span className="login-spinner" /> : <>{t('phoneLogin.login')}<span style={{ marginLeft: 6 }}>→</span></>}</button>
                            </form>
                        </div>
                    )}

                    {/* Voiceprint login — press-to-talk instant match */}
                    {activeTab === 'voiceprint' && (
                        <div className="login-drawer-form" style={{ animation: 'drawerTabFadeIn 0.25s ease' }}>
                            {vpError && <div className="login-error"><span>⚠</span> {vpError}</div>}

                            <div style={{ textAlign: 'center', padding: '28px 0 20px' }}>
                                <div style={{ width: 88, height: 88, borderRadius: 22, background: vpRecording ? 'rgba(139,92,246,0.25)' : 'linear-gradient(135deg, rgba(139,92,246,0.18), rgba(255,255,255,0.05))', border: vpRecording ? '2px solid rgba(139,92,246,0.6)' : '1px solid rgba(255,255,255,0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px', fontSize: 36, transition: 'all 0.2s ease', boxShadow: vpRecording ? '0 0 24px rgba(139,92,246,0.3)' : 'none' }}>
                                    {vpLoading ? <span className="login-spinner" /> : <span>🎙️</span>}
                                </div>
                                <p style={{ fontSize: 14, color: 'var(--text-primary)', fontWeight: 500, margin: '0 0 6px' }}>
                                    {isChinese ? '声纹登录' : 'Voice Login'}
                                </p>
                                <p style={{ fontSize: 12, color: 'var(--text-tertiary)', lineHeight: 1.6, margin: 0 }}>
                                    {vpRecording ? (isChinese ? '正在录音…松开停止' : 'Recording… release to stop')
                                        : vpLoading ? (isChinese ? '正在匹配…' : 'Matching…')
                                        : (isChinese ? '按住麦克风说话，匹配成功自动登录' : 'Hold to speak, auto-login on match')}
                                </p>
                            </div>

                            <button
                                onMouseDown={vpStartRecording}
                                onMouseUp={vpStopRecording}
                                onTouchStart={vpStartRecording}
                                onTouchEnd={vpStopRecording}
                                disabled={vpLoading}
                                className="login-submit"
                                style={{
                                    background: vpRecording ? 'rgba(139,92,246,0.6)' : 'var(--accent)',
                                    cursor: vpLoading ? 'default' : 'pointer',
                                    userSelect: 'none',
                                }}
                            >
                                {vpRecording ? (isChinese ? '● 录音中' : '● Recording') : vpLoading ? (isChinese ? '匹配中…' : 'Matching…') : (isChinese ? '按住说话' : 'Hold to Speak')}
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

function RegisterPage({ isChinese, toggleLang }: { isChinese: boolean; toggleLang: () => void }) {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const setAuth = useAuthStore((s) => s.setAuth);
    const [form, setForm] = useState({ name: '', email: '', phone: '', companyName: '' });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const langLabel = isChinese ? '中文' : 'EN';

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault(); setError(''); setLoading(true);
        try { const res = await systemApi.register(form); const user: User = { id: res.employeeId, username: res.name, email: form.email, display_name: res.name, role: 'org_admin', tenant_id: res.tenantId, identity: res.identity as UserIdentity, access_level: res.accessLevel as AccessLevel, is_active: true, created_at: new Date().toISOString() }; setAuth(user, res.sessionId); navigate('/'); }
        catch (err: any) { setError(err.message || t('registerFounder.registrationFailed')); }
        finally { setLoading(false); }
    };

    return (
        <div className="login-page">
            <div className="login-hero"><div className="login-hero-bg" /><div className="login-hero-content">
                <h1 className="login-hero-title">{t('registerFounder.systemInit')}<br /><span style={{ fontSize: '0.65em', fontWeight: 600, opacity: 0.85 }}>{t('registerFounder.registerFounderAccount')}</span></h1>
                <p className="login-hero-desc">{t('registerFounder.welcomeDesc')}</p>
            </div></div>
            <div className="login-form-panel" style={{ position: 'relative' }}>
                <div style={{ position: 'absolute', top: 16, right: 16, cursor: 'pointer', fontSize: 13, color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: 4, padding: '6px 12px', borderRadius: 999, background: 'rgba(255,255,255,0.06)', border: '1px solid var(--border-subtle)', zIndex: 101, boxShadow: '0 8px 20px rgba(0,0,0,0.08)' }} onClick={toggleLang}><span style={{ fontSize: 14 }}>🌐</span><span>{langLabel}</span></div>
                <div className="login-form-wrapper">
                    <div className="login-form-header"><div className="login-form-logo"><img src="/logo-black.png" className="login-logo-img" alt="" style={{ width: 28, height: 28, marginRight: 8, verticalAlign: 'middle' }} />Living Agent</div><h2 className="login-form-title">{t('registerFounder.title')}</h2><p className="login-form-subtitle">{t('registerFounder.subtitle')}</p></div>
                    {error && <div className="login-error"><span>⚠</span> {error}</div>}
                    <form onSubmit={handleSubmit} className="login-form">
                        <div className="login-field"><label>{t('registerFounder.name')}</label><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required autoFocus placeholder={t('registerFounder.enterName')} /></div>
                        <div className="login-field"><label>{t('registerFounder.email')}</label><input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required placeholder={t('registerFounder.enterEmail')} /></div>
                        <div className="login-field"><label>{t('registerFounder.phone')}</label><input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} required placeholder={t('phoneLogin.enterPhoneNumber')} /></div>
                        <div className="login-field"><label>{t('registerFounder.companyName')} ({t('registerFounder.optional')})</label><input value={form.companyName} onChange={(e) => setForm({ ...form, companyName: e.target.value })} placeholder={t('registerFounder.enterCompanyName')} /></div>
                        <button className="login-submit" type="submit" disabled={loading}>{loading ? <span className="login-spinner" /> : <>{t('registerFounder.register')}<span style={{ marginLeft: 6 }}>→</span></>}</button>
                    </form>
                </div>
            </div>
        </div>
    );
}
