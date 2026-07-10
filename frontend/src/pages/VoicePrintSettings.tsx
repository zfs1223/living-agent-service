import { useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores';
import { voicePrintExtendedApi } from '../services/api';

export default function VoicePrintSettings() {
    const { t, i18n } = useTranslation();
    const isChinese = i18n.language?.startsWith('zh');
    const user = useAuthStore(s => s.user);

    const [status, setStatus] = useState<any>(null);
    const [voicePrints, setVoicePrints] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [msg, setMsg] = useState('');
    const [isRecording, setIsRecording] = useState(false);
    const [recordingTarget, setRecordingTarget] = useState<'register' | 'verify' | null>(null);
    const mediaRecorderRef = useRef<MediaRecorder | null>(null);
    const audioChunksRef = useRef<Blob[]>([]);
    const streamRef = useRef<MediaStream | null>(null);
    const lastRecordingRef = useRef<Blob | null>(null);

    const showMsg = (text: string) => { setMsg(text); setTimeout(() => setMsg(''), 4000); };

    const fetchStatus = async () => {
        setLoading(true);
        try {
            const s = await voicePrintExtendedApi.getStatus();
            setStatus(s);
            const list = await voicePrintExtendedApi.list();
            setVoicePrints(list || []);
        } catch (e: any) {
            showMsg(e.message || 'Failed to load status');
        }
        setLoading(false);
    };

    const startRecording = async (target: 'register' | 'verify') => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: { sampleRate: 16000, channelCount: 1 } });
            streamRef.current = stream;
            const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : 'audio/webm';
            const mr = new MediaRecorder(stream, { mimeType });
            mediaRecorderRef.current = mr;
            audioChunksRef.current = [];

            mr.ondataavailable = (e) => { if (e.data.size > 0) audioChunksRef.current.push(e.data); };
            mr.onstop = () => {
                const blob = new Blob(audioChunksRef.current, { type: mr.mimeType });
                lastRecordingRef.current = blob;
                setIsRecording(false);
                setRecordingTarget(null);
                if (streamRef.current) { streamRef.current.getTracks().forEach(t => t.stop()); streamRef.current = null; }
            };

            mr.start(100);
            setIsRecording(true);
            setRecordingTarget(target);
        } catch (err: any) {
            showMsg(err.name === 'NotAllowedError' ? (isChinese ? '麦克风权限被拒绝' : 'Microphone permission denied') : (isChinese ? '无法访问麦克风' : 'Cannot access microphone'));
        }
    };

    const stopRecording = () => {
        if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
            mediaRecorderRef.current.stop();
        }
    };

    const handleRegister = async () => {
        if (!lastRecordingRef.current || !user) return;
        setLoading(true);
        try {
            const speakerId = user.id || user.username || 'unknown';
            await voicePrintExtendedApi.register(lastRecordingRef.current, speakerId, user.display_name || user.username);
            showMsg(isChinese ? '声纹注册成功' : 'Voice print registered');
            lastRecordingRef.current = null;
            fetchStatus();
        } catch (e: any) {
            showMsg(e.message || 'Registration failed');
        }
        setLoading(false);
    };

    const handleVerify = async () => {
        if (!lastRecordingRef.current || !user) return;
        setLoading(true);
        try {
            const speakerId = user.id || user.username || 'unknown';
            const result = await voicePrintExtendedApi.verify(lastRecordingRef.current, speakerId);
            const score = result?.score ?? result?.data?.score ?? 0;
            const accepted = result?.accepted ?? result?.data?.accepted ?? score >= 0.7;
            showMsg(accepted
                ? (isChinese ? `验证通过（置信度: ${(score * 100).toFixed(1)}%）` : `Verified (score: ${(score * 100).toFixed(1)}%)`)
                : (isChinese ? `验证失败（置信度: ${(score * 100).toFixed(1)}%）` : `Verification failed (score: ${(score * 100).toFixed(1)}%)`));
            lastRecordingRef.current = null;
        } catch (e: any) {
            showMsg(e.message || 'Verification failed');
        }
        setLoading(false);
    };

    return (
        <div style={{ padding: 24, maxWidth: 600, margin: '0 auto' }}>
            <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 16 }}>
                {isChinese ? '🎤 声纹管理' : '🎤 Voice Print Settings'}
            </h2>

            {msg && (
                <div style={{ padding: '8px 12px', borderRadius: 6, marginBottom: 12, fontSize: 12, background: msg.includes('成功') || msg.includes('通过') || msg.includes('Verified') ? 'rgba(0,180,120,0.12)' : 'rgba(255,80,80,0.12)', color: msg.includes('成功') || msg.includes('通过') || msg.includes('Verified') ? 'var(--success)' : 'var(--error)' }}>
                    {msg}
                </div>
            )}

            <button onClick={fetchStatus} disabled={loading} className="btn btn-primary" style={{ marginBottom: 16, fontSize: 12 }}>
                {loading ? '...' : (isChinese ? '查询声纹状态' : 'Check Status')}
            </button>

            {status && (
                <div style={{ padding: 12, borderRadius: 8, background: 'var(--bg-secondary)', marginBottom: 16, fontSize: 12 }}>
                    <div>{isChinese ? '已注册声纹数' : 'Registered prints'}: {status.count ?? status.total ?? voicePrints.length}</div>
                    <div>{isChinese ? '服务状态' : 'Service'}: {status.available ? '✅' : '❌'}</div>
                </div>
            )}

            {/* 注册声纹 */}
            <div style={{ padding: 16, borderRadius: 8, border: '1px solid var(--border-subtle)', marginBottom: 12 }}>
                <h3 style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>{isChinese ? '注册声纹' : 'Register Voice Print'}</h3>
                <p style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 8 }}>{isChinese ? '录制3-5秒语音样本进行声纹注册' : 'Record 3-5 seconds for voice print registration'}</p>
                <div style={{ display: 'flex', gap: 8 }}>
                    <button
                        onMouseDown={() => startRecording('register')}
                        onMouseUp={stopRecording}
                        onTouchStart={() => startRecording('register')}
                        onTouchEnd={stopRecording}
                        disabled={isRecording && recordingTarget !== 'register'}
                        style={{ padding: '6px 12px', borderRadius: 6, border: isRecording && recordingTarget === 'register' ? '2px solid var(--error)' : '1px solid var(--border-subtle)', background: isRecording && recordingTarget === 'register' ? 'rgba(255,80,80,0.15)' : 'var(--bg-secondary)', color: 'var(--text-primary)', fontSize: 12, cursor: 'pointer' }}
                    >
                        {isRecording && recordingTarget === 'register' ? '⏹ 停止' : '🎤 录制'}
                    </button>
                    {lastRecordingRef.current && recordingTarget === null && (
                        <button onClick={handleRegister} disabled={loading} className="btn btn-primary" style={{ fontSize: 12 }}>
                            {isChinese ? '提交注册' : 'Submit'}
                        </button>
                    )}
                </div>
            </div>

            {/* 验证声纹 */}
            <div style={{ padding: 16, borderRadius: 8, border: '1px solid var(--border-subtle)', marginBottom: 12 }}>
                <h3 style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>{isChinese ? '验证声纹' : 'Verify Voice Print'}</h3>
                <p style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 8 }}>{isChinese ? '录制语音验证声纹是否匹配' : 'Record to verify voice print match'}</p>
                <div style={{ display: 'flex', gap: 8 }}>
                    <button
                        onMouseDown={() => startRecording('verify')}
                        onMouseUp={stopRecording}
                        onTouchStart={() => startRecording('verify')}
                        onTouchEnd={stopRecording}
                        disabled={isRecording && recordingTarget !== 'verify'}
                        style={{ padding: '6px 12px', borderRadius: 6, border: isRecording && recordingTarget === 'verify' ? '2px solid var(--error)' : '1px solid var(--border-subtle)', background: isRecording && recordingTarget === 'verify' ? 'rgba(255,80,80,0.15)' : 'var(--bg-secondary)', color: 'var(--text-primary)', fontSize: 12, cursor: 'pointer' }}
                    >
                        {isRecording && recordingTarget === 'verify' ? '⏹ 停止' : '🎤 录制'}
                    </button>
                    {lastRecordingRef.current && recordingTarget === null && (
                        <button onClick={handleVerify} disabled={loading} className="btn btn-primary" style={{ fontSize: 12 }}>
                            {isChinese ? '验证' : 'Verify'}
                        </button>
                    )}
                </div>
            </div>

            {/* 已注册声纹列表 */}
            {voicePrints.length > 0 && (
                <div style={{ padding: 12, borderRadius: 8, background: 'var(--bg-secondary)', fontSize: 12 }}>
                    <h4 style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>{isChinese ? '已注册声纹' : 'Registered Prints'}</h4>
                    {voicePrints.map((vp, i) => (
                        <div key={i} style={{ padding: '6px 0', borderBottom: '1px solid var(--border-subtle)' }}>
                            {vp.name || vp.speaker_id || `Voice Print ${i + 1}`}
                            {vp.created_at && <span style={{ color: 'var(--text-tertiary)', marginLeft: 8 }}>{new Date(vp.created_at).toLocaleDateString()}</span>}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
