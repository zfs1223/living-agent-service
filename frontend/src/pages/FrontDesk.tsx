import { useState, useRef, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { wsApi } from '../services/api';
import MarkdownRenderer from '../components/MarkdownRenderer';

interface Message {
    role: 'user' | 'assistant' | 'system';
    content: string;
    timestamp: string;
    audioUrl?: string;
}

function blobToBase64(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onloadend = () => {
            const result = reader.result as string;
            resolve(result.split(',')[1]);
        };
        reader.onerror = reject;
        reader.readAsDataURL(blob);
    });
}

export default function FrontDesk() {
    const { t, i18n } = useTranslation();
    const isChinese = i18n.language?.startsWith('zh');

    const [messages, setMessages] = useState<Message[]>([]);
    const [input, setInput] = useState('');
    const [connected, setConnected] = useState(false);
    const [isWaiting, setIsWaiting] = useState(false);
    const [voiceMode, setVoiceMode] = useState(false);
    const [isRecording, setIsRecording] = useState(false);
    const [voiceSupported, setVoiceSupported] = useState(true);
    const wsRef = useRef<WebSocket | null>(null);
    const messagesEndRef = useRef<HTMLDivElement | null>(null);
    const mediaRecorderRef = useRef<MediaRecorder | null>(null);
    const audioChunksRef = useRef<Blob[]>([]);
    const streamRef = useRef<MediaStream | null>(null);
    const recordingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const currentAudioRef = useRef<HTMLAudioElement | null>(null);
    const anonymousId = useRef(`guest_${Date.now().toString(36)}`);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    useEffect(() => { scrollToBottom(); }, [messages]);

    // 检测浏览器是否支持录音
    useEffect(() => {
        const supported = typeof navigator.mediaDevices !== 'undefined' && typeof MediaRecorder !== 'undefined';
        setVoiceSupported(supported);
    }, []);

    const connect = useCallback(() => {
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
                        const newMsg: Message = { role: 'assistant', content: data.content || '', timestamp: new Date().toISOString() };
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

                if (data.type === 'asr_result') {
                    // 显示语音识别文本
                    if (data.text) {
                        setMessages(prev => [...prev, { role: 'user', content: `🎤 ${data.text}`, timestamp: new Date().toISOString() }]);
                    }
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
        connect();
        return () => { wsRef.current?.close(); };
    }, [connect]);

    // 清理音频对象
    useEffect(() => {
        return () => {
            if (currentAudioRef.current) currentAudioRef.current.pause();
            messages.forEach(m => { if (m.audioUrl) URL.revokeObjectURL(m.audioUrl); });
        };
    }, []);

    const sendMessage = () => {
        if (!input.trim() || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;
        const content = input.trim();
        setMessages(prev => [...prev, { role: 'user', content, timestamp: new Date().toISOString() }]);
        setInput('');
        setIsWaiting(true);
        wsRef.current.send(JSON.stringify({ type: 'chat', content, userId: anonymousId.current }));
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
    };

    // 语音录音
    const startRecording = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                audio: { sampleRate: 16000, channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true }
            });
            streamRef.current = stream;

            const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus'
                : MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/wav';

            const mediaRecorder = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 128000 });
            mediaRecorderRef.current = mediaRecorder;
            audioChunksRef.current = [];

            mediaRecorder.ondataavailable = (e) => { if (e.data.size > 0) audioChunksRef.current.push(e.data); };
            mediaRecorder.onstop = () => handleRecordingStop();

            mediaRecorder.start(100);
            setIsRecording(true);

            // 最大60秒自动停止
            recordingTimerRef.current = setTimeout(() => { if (mediaRecorder.state === 'recording') stopRecording(); }, 60000);
        } catch (err: any) {
            const msg = err.name === 'NotAllowedError' ? (isChinese ? '麦克风权限被拒绝' : 'Microphone permission denied')
                : err.name === 'NotFoundError' ? (isChinese ? '未找到麦克风' : 'No microphone found')
                : (isChinese ? '无法访问麦克风' : 'Cannot access microphone');
            setMessages(prev => [...prev, { role: 'system', content: msg, timestamp: new Date().toISOString() }]);
        }
    };

    const stopRecording = () => {
        if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
            mediaRecorderRef.current.stop();
        }
        setIsRecording(false);
        if (recordingTimerRef.current) { clearTimeout(recordingTimerRef.current); recordingTimerRef.current = null; }
        if (streamRef.current) { streamRef.current.getTracks().forEach(t => t.stop()); streamRef.current = null; }
    };

    const handleRecordingStop = async () => {
        if (audioChunksRef.current.length === 0) return;
        const audioBlob = new Blob(audioChunksRef.current, { type: mediaRecorderRef.current?.mimeType || 'audio/webm' });
        audioChunksRef.current = [];

        if (audioBlob.size < 1000) {
            setMessages(prev => [...prev, { role: 'system', content: isChinese ? '录音时间太短' : 'Recording too short', timestamp: new Date().toISOString() }]);
            return;
        }

        try {
            const base64 = await blobToBase64(audioBlob);
            if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;
            setIsWaiting(true);
            wsRef.current.send(JSON.stringify({ type: 'audio_full', audio: base64, userId: anonymousId.current }));
        } catch {
            setMessages(prev => [...prev, { role: 'system', content: isChinese ? '音频处理失败' : 'Audio processing failed', timestamp: new Date().toISOString() }]);
        }
    };

    const playAudio = (url: string) => {
        if (currentAudioRef.current) currentAudioRef.current.pause();
        const audio = new Audio(url);
        currentAudioRef.current = audio;
        audio.play().catch(() => undefined);
    };

    const toggleLang = () => { i18n.changeLanguage(i18n.language === 'zh' ? 'en' : 'zh'); };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: 'var(--bg-primary)', color: 'var(--text-primary)' }}>
            {/* Header */}
            <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 20px', borderBottom: '1px solid var(--border-subtle)', flexShrink: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <span style={{ fontSize: 20 }}>🤖</span>
                    <h1 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>
                        {isChinese ? '智能前台' : 'Smart Front Desk'}
                    </h1>
                    <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 10, background: connected ? 'rgba(0,180,120,0.15)' : 'rgba(255,80,80,0.15)', color: connected ? 'var(--success)' : 'var(--error)' }}>
                        {connected ? (isChinese ? '在线' : 'Online') : (isChinese ? '离线' : 'Offline')}
                    </span>
                </div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <button onClick={toggleLang} style={{ background: 'none', border: '1px solid var(--border-subtle)', borderRadius: 6, padding: '4px 10px', fontSize: 12, color: 'var(--text-secondary)', cursor: 'pointer' }}>
                        {i18n.language === 'zh' ? 'EN' : '中文'}
                    </button>
                    <Link to="/login" style={{ fontSize: 12, padding: '6px 14px', borderRadius: 6, background: 'var(--accent)', color: '#fff', textDecoration: 'none', fontWeight: 500 }}>
                        {isChinese ? '登录' : 'Login'}
                    </Link>
                </div>
            </header>

            {/* Messages */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px' }}>
                {messages.length === 0 && (
                    <div style={{ textAlign: 'center', color: 'var(--text-tertiary)', marginTop: 80 }}>
                        <div style={{ fontSize: 48, marginBottom: 16 }}>💬</div>
                        <p style={{ fontSize: 14 }}>
                            {isChinese ? '你好！我是智能前台，有什么可以帮你的？' : "Hi! I'm the Smart Front Desk. How can I help you?"}
                        </p>
                        <p style={{ fontSize: 12, color: 'var(--text-quaternary)' }}>
                            {isChinese ? '支持文字和语音对话 · 无需登录' : 'Text & Voice · No login required'}
                        </p>
                    </div>
                )}
                {messages.map((msg, i) => (
                    <div key={i} style={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start', marginBottom: 12 }}>
                        <div style={{ maxWidth: '70%', padding: '10px 14px', borderRadius: msg.role === 'user' ? '12px 12px 4px 12px' : '12px 12px 12px 4px', background: msg.role === 'user' ? 'var(--accent)' : msg.role === 'system' ? 'rgba(255,80,80,0.12)' : 'var(--bg-secondary)', color: msg.role === 'user' ? '#fff' : msg.role === 'system' ? 'var(--error)' : 'var(--text-primary)', fontSize: 13, lineHeight: 1.6, wordBreak: 'break-word' }}>
                            {msg.role === 'assistant' ? <MarkdownRenderer content={msg.content} /> : msg.content}
                            {msg.audioUrl && (
                                <button onClick={() => playAudio(msg.audioUrl!)} style={{ marginTop: 6, fontSize: 11, padding: '2px 8px', borderRadius: 4, border: '1px solid var(--border-subtle)', background: 'var(--bg-tertiary)', color: 'var(--text-secondary)', cursor: 'pointer' }}>
                                    🔊 {isChinese ? '播放语音' : 'Play'}
                                </button>
                            )}
                        </div>
                    </div>
                ))}
                <div ref={messagesEndRef} />
            </div>

            {/* Input */}
            <div style={{ padding: '12px 20px', borderTop: '1px solid var(--border-subtle)', flexShrink: 0 }}>
                {voiceMode ? (
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
                        <button
                            onMouseDown={startRecording}
                            onMouseUp={stopRecording}
                            onTouchStart={startRecording}
                            onTouchEnd={stopRecording}
                            disabled={isWaiting || !connected}
                            style={{
                                width: 64, height: 64, borderRadius: '50%',
                                border: isRecording ? '3px solid var(--error)' : '3px solid var(--accent)',
                                background: isRecording ? 'rgba(255,80,80,0.2)' : 'var(--bg-secondary)',
                                color: isRecording ? 'var(--error)' : 'var(--accent)',
                                fontSize: 24, cursor: 'pointer',
                                transition: 'all 0.2s',
                            }}
                        >
                            {isRecording ? '⏹' : '🎤'}
                        </button>
                        <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                            {isRecording ? (isChinese ? '正在录音...松开停止' : 'Recording... release to stop')
                                : isWaiting ? (isChinese ? '处理中...' : 'Processing...')
                                : (isChinese ? '按住说话' : 'Hold to talk')}
                        </span>
                    </div>
                ) : (
                    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
                        <textarea
                            value={input}
                            onChange={e => setInput(e.target.value)}
                            onKeyDown={handleKeyDown}
                            placeholder={isChinese ? '输入消息...' : 'Type a message...'}
                            disabled={isWaiting || !connected}
                            rows={1}
                            style={{ flex: 1, padding: '10px 14px', borderRadius: 8, border: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)', color: 'var(--text-primary)', fontSize: 13, resize: 'none', outline: 'none', fontFamily: 'inherit' }}
                        />
                        <button
                            onClick={sendMessage}
                            disabled={!input.trim() || isWaiting || !connected}
                            style={{ padding: '10px 20px', borderRadius: 8, border: 'none', background: input.trim() && !isWaiting && connected ? 'var(--accent)' : 'var(--bg-tertiary)', color: input.trim() && !isWaiting && connected ? '#fff' : 'var(--text-tertiary)', fontSize: 13, fontWeight: 500, cursor: input.trim() && !isWaiting && connected ? 'pointer' : 'default' }}
                        >
                            {isChinese ? '发送' : 'Send'}
                        </button>
                    </div>
                )}
                <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 6, fontSize: 10, color: 'var(--text-quaternary)' }}>
                    <span>{isChinese ? '由 Qwen3 闲聊神经元提供 · 无需登录' : 'Powered by Qwen3 Chat Neuron · No login required'}</span>
                    {voiceSupported && (
                        <button onClick={() => setVoiceMode(!voiceMode)} style={{ background: 'none', border: 'none', color: voiceMode ? 'var(--accent)' : 'var(--text-quaternary)', fontSize: 10, cursor: 'pointer' }}>
                            {voiceMode ? '⌨️' : '🎤'} {voiceMode ? (isChinese ? '文字模式' : 'Text') : (isChinese ? '语音模式' : 'Voice')}
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}
