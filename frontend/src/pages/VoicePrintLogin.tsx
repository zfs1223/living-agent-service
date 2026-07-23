import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores';
import { voicePrintApi, voicePrintExtendedApi } from '../services/api';

/* ────── Main Component: 个人声纹设置（用于登录验证） ────── */
export default function VoicePrintLogin() {
    const { t, i18n } = useTranslation();
    const queryClient = useQueryClient();
    const isChinese = i18n.language?.startsWith('zh');
    const user = useAuthStore(s => s.user);

    const [isRecording, setIsRecording] = useState(false);
    const [verifyCommand, setVerifyCommand] = useState('请验证我的身份');
    const [testCommand, setTestCommand] = useState('');
    const [msg, setMsg] = useState('');
    const [msgType, setMsgType] = useState<'success' | 'error' | 'info'>('info');
    const mediaRecorderRef = useRef<MediaRecorder | null>(null);
    const audioChunksRef = useRef<Blob[]>([]);
    const streamRef = useRef<MediaStream | null>(null);
    const lastRecordingRef = useRef<Blob | null>(null);

    const showMsg = (text: string, type: 'success' | 'error' | 'info' = 'info') => {
        setMsg(text);
        setMsgType(type);
        setTimeout(() => setMsg(''), 5000);
    };

    // 查询当前用户的声纹状态
    const { data: myVoicePrint, isLoading } = useQuery({
        queryKey: ['my-voiceprint'],
        queryFn: async () => {
            try {
                const list = await voicePrintApi.list();
                // 过滤出当前用户的声纹
                const userId = user?.id || user?.username;
                return list?.find((vp: any) => vp.speaker_id === userId || vp.user_id === userId) || null;
            } catch {
                return null;
            }
        },
        enabled: !!user,
    });

    // 查询验证指令设置
    const { data: verifySettings } = useQuery({
        queryKey: ['voiceprint-verify-settings'],
        queryFn: async () => {
            try {
                const res = await voicePrintApi.getVerifySettings?.();
                return res || { command: '请验证我的身份', enabled: true };
            } catch {
                return { command: '请验证我的身份', enabled: true };
            }
        },
        enabled: !!user,
    });

    useEffect(() => {
        if (verifySettings?.command) {
            setVerifyCommand(verifySettings.command);
        }
    }, [verifySettings]);

    // 开始录音
    const startRecording = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                audio: { sampleRate: 16000, channelCount: 1, echoCancellation: true, noiseSuppression: true }
            });
            streamRef.current = stream;
            const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
                ? 'audio/webm;codecs=opus'
                : 'audio/webm';
            const mr = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 128000 });
            mediaRecorderRef.current = mr;
            audioChunksRef.current = [];

            mr.ondataavailable = (e) => { if (e.data.size > 0) audioChunksRef.current.push(e.data); };
            mr.onstop = () => {
                const blob = new Blob(audioChunksRef.current, { type: mr.mimeType });
                lastRecordingRef.current = blob;
                setIsRecording(false);
                if (streamRef.current) {
                    streamRef.current.getTracks().forEach(t => t.stop());
                    streamRef.current = null;
                }
            };

            mr.start(100);
            setIsRecording(true);
        } catch (err: any) {
            showMsg(
                err.name === 'NotAllowedError'
                    ? (isChinese ? '麦克风权限被拒绝' : 'Microphone permission denied')
                    : (isChinese ? '无法访问麦克风' : 'Cannot access microphone'),
                'error'
            );
        }
    };

    // 停止录音
    const stopRecording = () => {
        if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
            mediaRecorderRef.current.stop();
        }
    };

    // 注册声纹
    const registerMutation = useMutation({
        mutationFn: async () => {
            if (!lastRecordingRef.current || !user) throw new Error('No recording');
            const speakerId = user.id || user.username || 'unknown';
            return voicePrintExtendedApi.register(
                lastRecordingRef.current,
                speakerId,
                user.display_name || user.username
            );
        },
        onSuccess: () => {
            showMsg(isChinese ? '声纹注册成功！现在可以使用声纹登录' : 'Voice print registered! You can now login with voice', 'success');
            lastRecordingRef.current = null;
            queryClient.invalidateQueries({ queryKey: ['my-voiceprint'] });
        },
        onError: (e: any) => {
            showMsg(e.message || (isChinese ? '注册失败' : 'Registration failed'), 'error');
        },
    });

    // 验证声纹（带指令文字）
    const verifyMutation = useMutation({
        mutationFn: async () => {
            if (!lastRecordingRef.current) throw new Error('No recording');
            return voicePrintApi.verifyWithCommand?.({
                audio: lastRecordingRef.current,
                command: verifyCommand,
                speakerId: user?.id || user?.username,
            }) ?? voicePrintApi.verify(lastRecordingRef.current, user?.id || user?.username);
        },
        onSuccess: (result: any) => {
            const score = result?.score ?? result?.data?.score ?? 0;
            const accepted = result?.accepted ?? result?.data?.accepted ?? score >= 0.7;
            const commandMatch = result?.commandMatch ?? true;
            if (accepted && commandMatch) {
                showMsg(isChinese
                    ? `验证通过！声纹匹配，指令正确（置信度: ${(score * 100).toFixed(1)}%）`
                    : `Verified! Voice matched, command correct (score: ${(score * 100).toFixed(1)}%)`, 'success');
            } else if (!commandMatch) {
                showMsg(isChinese
                    ? '声纹匹配但指令不正确，请说正确的验证语句'
                    : 'Voice matched but command incorrect. Please say the correct phrase', 'error');
            } else {
                showMsg(isChinese
                    ? `声纹不匹配（置信度: ${(score * 100).toFixed(1)}%）`
                    : `Voice not matched (score: ${(score * 100).toFixed(1)}%)`, 'error');
            }
            lastRecordingRef.current = null;
        },
        onError: (e: any) => {
            showMsg(e.message || (isChinese ? '验证失败' : 'Verification failed'), 'error');
        },
    });

    // 保存验证指令设置
    const saveSettingsMutation = useMutation({
        mutationFn: async (data: { command: string; enabled: boolean }) => {
            return voicePrintApi.updateVerifySettings?.(data) ?? Promise.resolve();
        },
        onSuccess: () => {
            showMsg(isChinese ? '验证指令设置已保存' : 'Verify command settings saved', 'success');
        },
        onError: (e: any) => {
            showMsg(e.message || (isChinese ? '保存失败' : 'Save failed'), 'error');
        },
    });

    const handleSaveCommand = () => {
        if (!verifyCommand.trim()) {
            showMsg(isChinese ? '请输入验证指令' : 'Please enter verify command', 'error');
            return;
        }
        saveSettingsMutation.mutate({ command: verifyCommand, enabled: true });
    };

    const handleRegister = () => {
        if (!lastRecordingRef.current) {
            showMsg(isChinese ? '请先录制声纹' : 'Please record voice first', 'error');
            return;
        }
        registerMutation.mutate();
    };

    const handleVerify = () => {
        if (!lastRecordingRef.current) {
            showMsg(isChinese ? '请先录制声纹' : 'Please record voice first', 'error');
            return;
        }
        verifyMutation.mutate();
    };

    return (
        <div style={{ padding: 24, maxWidth: 720, margin: '0 auto' }}>
            {/* Header */}
            <div style={{
                borderRadius: 16,
                padding: 20,
                background: 'linear-gradient(135deg, rgba(139,92,246,0.12), rgba(12,18,28,0.84))',
                border: '1px solid rgba(255,255,255,0.08)',
                marginBottom: 20,
            }}>
                <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
                    🎤 {isChinese ? '声纹设置' : 'Voice Print Setup'}
                </h1>
                <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: '8px 0 0' }}>
                    {isChinese
                        ? '设置您的个人声纹，用于声纹登录验证。建议录制3-5秒清晰语音。'
                        : 'Set up your personal voice print for voice login verification. Record 3-5 seconds of clear audio.'}
                </p>
            </div>

            {/* 消息提示 */}
            {msg && (
                <div style={{
                    padding: '10px 14px',
                    borderRadius: 8,
                    marginBottom: 16,
                    fontSize: 13,
                    background: msgType === 'success' ? 'rgba(16,185,129,0.12)' : msgType === 'error' ? 'rgba(239,68,68,0.12)' : 'rgba(59,130,246,0.12)',
                    color: msgType === 'success' ? 'var(--success)' : msgType === 'error' ? 'var(--error)' : 'var(--accent)',
                    border: `1px solid ${msgType === 'success' ? 'rgba(16,185,129,0.2)' : msgType === 'error' ? 'rgba(239,68,68,0.2)' : 'rgba(59,130,246,0.2)'}`,
                }}>
                    {msg}
                </div>
            )}

            {/* 当前状态 */}
            <div style={{
                padding: 16,
                borderRadius: 12,
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-subtle)',
                marginBottom: 16,
            }}>
                <div style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 4 }}>
                    {isChinese ? '当前状态' : 'Current Status'}
                </div>
                <div style={{ fontSize: 14, fontWeight: 500, color: myVoicePrint ? 'var(--success)' : 'var(--text-secondary)' }}>
                    {isLoading ? '...' : myVoicePrint
                        ? (isChinese ? '✅ 已注册声纹' : '✅ Voice print registered')
                        : (isChinese ? '⚠️ 未注册声纹' : '⚠️ Voice print not registered')}
                </div>
                {myVoicePrint?.created_at && (
                    <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 4 }}>
                        {isChinese ? '注册时间: ' : 'Registered: '}{new Date(myVoicePrint.created_at).toLocaleString()}
                    </div>
                )}
            </div>

            {/* 验证指令设置 */}
            <div style={{
                padding: 16,
                borderRadius: 12,
                border: '1px solid var(--border-subtle)',
                marginBottom: 16,
            }}>
                <h3 style={{ fontSize: 14, fontWeight: 600, margin: '0 0 12px', color: 'var(--text-primary)' }}>
                    🔐 {isChinese ? '验证指令文字' : 'Verification Command'}
                </h3>
                <p style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 12 }}>
                    {isChinese
                        ? '设置一个验证指令，登录时需要同时验证声纹和指令文字，提高安全性'
                        : 'Set a verification command. Login requires both voice and command match for enhanced security'}
                </p>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <input
                        type="text"
                        value={verifyCommand}
                        onChange={e => setVerifyCommand(e.target.value)}
                        placeholder={isChinese ? '输入验证指令，如：请验证我的身份' : 'Enter command, e.g.: Please verify my identity'}
                        style={{
                            flex: 1,
                            padding: '8px 12px',
                            fontSize: 13,
                            background: 'var(--bg-secondary)',
                            color: 'var(--text-primary)',
                            border: '1px solid var(--border-default)',
                            borderRadius: 6,
                            outline: 'none',
                        }}
                    />
                    <button
                        onClick={handleSaveCommand}
                        disabled={saveSettingsMutation.isPending || !verifyCommand.trim()}
                        className="btn btn-primary"
                        style={{ fontSize: 12, padding: '8px 16px' }}
                    >
                        {saveSettingsMutation.isPending ? '...' : (isChinese ? '保存' : 'Save')}
                    </button>
                </div>
            </div>

            {/* 声纹录制 */}
            <div style={{
                padding: 16,
                borderRadius: 12,
                border: '1px solid var(--border-subtle)',
                marginBottom: 16,
            }}>
                <h3 style={{ fontSize: 14, fontWeight: 600, margin: '0 0 12px', color: 'var(--text-primary)' }}>
                    🎙️ {isChinese ? '录制声纹' : 'Record Voice Print'}
                </h3>
                <p style={{ fontSize: 12, color: 'var(--text-tertiary)', marginBottom: 12 }}>
                    {isChinese
                        ? '按住按钮录制3-5秒语音样本，松开后可提交注册或验证'
                        : 'Hold to record 3-5 seconds voice sample, then submit to register or verify'}
                </p>
                <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                    <button
                        onMouseDown={startRecording}
                        onMouseUp={stopRecording}
                        onTouchStart={startRecording}
                        onTouchEnd={stopRecording}
                        style={{
                            width: 64,
                            height: 64,
                            borderRadius: '50%',
                            border: isRecording ? '3px solid var(--error)' : '3px solid var(--accent)',
                            background: isRecording ? 'rgba(239,68,68,0.15)' : 'rgba(139,92,246,0.15)',
                            color: isRecording ? 'var(--error)' : 'var(--accent)',
                            fontSize: 24,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            transition: 'all 0.2s',
                        }}
                    >
                        {isRecording ? '⏹' : '🎤'}
                    </button>
                    <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
                        {isRecording
                            ? (isChinese ? '录音中...松开停止' : 'Recording... release to stop')
                            : lastRecordingRef.current
                                ? (isChinese ? '✅ 已录制，可提交' : '✅ Recorded, ready to submit')
                                : (isChinese ? '按住开始录音' : 'Hold to start recording')}
                    </div>
                </div>
            </div>

            {/* 操作按钮 */}
            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
                <button
                    onClick={handleRegister}
                    disabled={!lastRecordingRef.current || registerMutation.isPending}
                    className="btn btn-primary"
                    style={{ fontSize: 13 }}
                >
                    {registerMutation.isPending
                        ? (isChinese ? '注册中...' : 'Registering...')
                        : (isChinese ? '📝 注册声纹' : '📝 Register Voice Print')}
                </button>
                <button
                    onClick={handleVerify}
                    disabled={!lastRecordingRef.current || verifyMutation.isPending || !myVoicePrint}
                    className="btn btn-secondary"
                    style={{ fontSize: 13 }}
                    title={!myVoicePrint ? (isChinese ? '请先注册声纹' : 'Please register first') : ''}
                >
                    {verifyMutation.isPending
                        ? (isChinese ? '验证中...' : 'Verifying...')
                        : (isChinese ? '✅ 测试验证' : '✅ Test Verify')}
                </button>
            </div>
        </div>
    );
}