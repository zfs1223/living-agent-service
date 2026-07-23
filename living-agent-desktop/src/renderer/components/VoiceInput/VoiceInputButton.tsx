/**
 * P10: 语音输入按钮
 *
 * 长按说话 → 录音 → 松开识别 → 文本插入输入框
 * - 录音中显示音量波形（VoiceWaveform）
 * - 识别中显示加载动画
 * - 权限控制：需登录 + 非 CHAT_ONLY 身份
 */
import { useState, useRef, useCallback, useEffect } from 'react';
import VoiceWaveform from './VoiceWaveform';
import './VoiceInputButton.css';

interface VoiceInputButtonProps {
  /** 识别完成回调，返回转写文本 */
  onTranscript: (text: string) => void;
  /** 是否有登录态 */
  hasToken: boolean;
  /** 用户权限等级 */
  accessLevel?: string;
  /** 是否禁用 */
  disabled?: boolean;
}

export default function VoiceInputButton({
  onTranscript,
  hasToken,
  accessLevel,
  disabled = false
}: VoiceInputButtonProps) {
  const [isRecording, setIsRecording] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [volumeLevel, setVolumeLevel] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [recordingDuration, setRecordingDuration] = useState(0);

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const volumeIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const durationIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const startTimeRef = useRef<number>(0);

  // 权限检查
  const canUseVoice = hasToken && accessLevel !== 'CHAT_ONLY';

  // 清理资源
  const cleanup = useCallback(() => {
    if (volumeIntervalRef.current) {
      clearInterval(volumeIntervalRef.current);
      volumeIntervalRef.current = null;
    }
    if (durationIntervalRef.current) {
      clearInterval(durationIntervalRef.current);
      durationIntervalRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(t => t.stop());
      streamRef.current = null;
    }
    if (analyserRef.current) {
      analyserRef.current = null;
    }
    if (audioContextRef.current) {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }
    mediaRecorderRef.current = null;
    chunksRef.current = [];
  }, []);

  // 组件卸载时清理
  useEffect(() => {
    return () => cleanup();
  }, [cleanup]);

  // 开始录音
  const startRecording = useCallback(async () => {
    if (!canUseVoice || disabled || isRecording || isProcessing) return;

    setError(null);

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: 16000,
          echoCancellation: true,
          noiseSuppression: true,
        }
      });

      streamRef.current = stream;

      // 创建 AudioContext 用于音量分析
      audioContextRef.current = new AudioContext({ sampleRate: 16000 });
      const source = audioContextRef.current.createMediaStreamSource(stream);
      analyserRef.current = audioContextRef.current.createAnalyser();
      analyserRef.current.fftSize = 2048;
      source.connect(analyserRef.current);

      // 创建 MediaRecorder（用于录音数据）
      const mr = new MediaRecorder(stream, {
        mimeType: 'audio/webm;codecs=opus'
      });
      mediaRecorderRef.current = mr;
      chunksRef.current = [];

      mr.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };

      mr.start(100); // 每 100ms 一个 chunk
      startTimeRef.current = Date.now();

      // 音量监测
      volumeIntervalRef.current = setInterval(() => {
        if (analyserRef.current) {
          const dataArray = new Uint8Array(analyserRef.current.frequencyBinCount);
          analyserRef.current.getByteTimeDomainData(dataArray);
          let sum = 0;
          for (let i = 0; i < dataArray.length; i++) {
            const normalized = (dataArray[i] - 128) / 128;
            sum += normalized * normalized;
          }
          setVolumeLevel(Math.sqrt(sum / dataArray.length));
        }
      }, 50);

      // 录音时长更新
      durationIntervalRef.current = setInterval(() => {
        setRecordingDuration(Date.now() - startTimeRef.current);
      }, 100);

      setIsRecording(true);
    } catch (err: any) {
      if (err.name === 'NotAllowedError') {
        setError('麦克风权限被拒绝，请在系统设置中允许');
      } else if (err.name === 'NotFoundError') {
        setError('未找到麦克风设备');
      } else {
        setError(`录音启动失败: ${err.message}`);
      }
      cleanup();
    }
  }, [canUseVoice, disabled, isRecording, isProcessing, cleanup]);

  // 停止录音并识别
  const stopRecordingAndRecognize = useCallback(async () => {
    if (!isRecording || !mediaRecorderRef.current) return;

    // 停止音量监测
    if (volumeIntervalRef.current) {
      clearInterval(volumeIntervalRef.current);
      volumeIntervalRef.current = null;
    }
    if (durationIntervalRef.current) {
      clearInterval(durationIntervalRef.current);
      durationIntervalRef.current = null;
    }

    setVolumeLevel(0);
    setIsRecording(false);
    setIsProcessing(true);

    const mr = mediaRecorderRef.current;

    await new Promise<void>((resolve) => {
      mr.onstop = () => resolve();
      mr.stop();
    });

    // 停止麦克风
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(t => t.stop());
      streamRef.current = null;
    }

    const blob = new Blob(chunksRef.current, { type: 'audio/webm' });

    // 检查录音长度
    if (blob.size < 1000) {
      setIsProcessing(false);
      setRecordingDuration(0);
      setError('录音时间太短，请长按说话');
      return;
    }

    try {
      // 调用后端 ASR API
      const text = await callASR(blob);
      if (text) {
        onTranscript(text);
      } else {
        setError('未能识别语音内容，请重试');
      }
    } catch (err: any) {
      setError(err.message || '语音识别失败');
    } finally {
      setIsProcessing(false);
      setRecordingDuration(0);
      chunksRef.current = [];
    }
  }, [isRecording, onTranscript]);

  // 取消录音
  const cancelRecording = useCallback(() => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
    cleanup();
    setIsRecording(false);
    setVolumeLevel(0);
    setRecordingDuration(0);
  }, [cleanup]);

  // 鼠标/触摸事件处理
  const handlePointerDown = useCallback((e: React.PointerEvent) => {
    e.preventDefault();
    startRecording();
  }, [startRecording]);

  const handlePointerUp = useCallback(() => {
    if (isRecording) {
      stopRecordingAndRecognize();
    }
  }, [isRecording, stopRecordingAndRecognize]);

  const handlePointerLeave = useCallback(() => {
    if (isRecording) {
      cancelRecording();
    }
  }, [isRecording, cancelRecording]);

  // 格式化时长
  const formatDuration = (ms: number) => {
    const seconds = Math.floor(ms / 1000);
    const tenths = Math.floor((ms % 1000) / 100);
    return `${seconds}.${tenths}s`;
  };

  // 无权限提示
  if (!canUseVoice) {
    return (
      <div className="voice-input-button voice-input-button--disabled" title={!hasToken ? '请先登录' : 'CHAT_ONLY 身份无语音权限'}>
        <span className="voice-input-button__icon">🎤</span>
      </div>
    );
  }

  return (
    <div className="voice-input-container">
      <button
        className={`voice-input-button ${isRecording ? 'voice-input-button--recording' : ''} ${isProcessing ? 'voice-input-button--processing' : ''} ${error ? 'voice-input-button--error' : ''}`}
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onPointerLeave={handlePointerLeave}
        disabled={disabled || isProcessing}
        title={isRecording ? '松开完成录音' : '按住说话'}
      >
        <span className="voice-input-button__icon">
          {isProcessing ? '⏳' : isRecording ? '⏺️' : '🎤'}
        </span>
      </button>

      {/* 录音中：波形 + 时长 */}
      {isRecording && (
        <div className="voice-input-overlay">
          <VoiceWaveform volumeLevel={volumeLevel} active={isRecording} />
          <div className="voice-input-duration">{formatDuration(recordingDuration)}</div>
          <div className="voice-input-hint">松开完成录音</div>
        </div>
      )}

      {/* 识别中 */}
      {isProcessing && (
        <div className="voice-input-overlay">
          <div className="voice-input-processing">
            <div className="processing-spinner" />
            <span>识别中...</span>
          </div>
        </div>
      )}

      {/* 错误提示 */}
      {error && (
        <div className="voice-input-error">
          <span>{error}</span>
          <button onClick={() => setError(null)}>✕</button>
        </div>
      )}
    </div>
  );
}

/**
 * 调用 ASR API
 * 参考 AGENTS.md §5.7 语音对话完整链路
 */
async function callASR(audioBlob: Blob): Promise<string> {
  // 优先直接调用 model_daemon.py (8390)
  try {
    const backendUrl = await window.livingAgentAPI.getBackendUrl();
    const daemonUrl = backendUrl.replace(/:\d+/, ':8390');
    const formData = new FormData();
    formData.append('audio', audioBlob, 'recording.webm');

    const response = await fetch(`${daemonUrl}/asr`, {
      method: 'POST',
      body: formData,
    });

    if (response.ok) {
      const data = await response.json();
      return data.text || data.transcript || '';
    }
  } catch (e) {
    console.warn('[VoiceInput] Direct ASR failed, trying Java proxy:', e);
  }

  // 回退到 Java 后端代理
  try {
    const backendUrl = await window.livingAgentAPI.getBackendUrl();
    const token = await window.livingAgentAPI.auth.getToken();
    const formData = new FormData();
    formData.append('audio', audioBlob, 'recording.webm');

    const response = await fetch(`${backendUrl}/api/voice/asr`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    });

    if (response.ok) {
      const data = await response.json();
      return data.data?.text || data.data?.transcript || data.text || '';
    }
  } catch (e) {
    console.error('[VoiceInput] Java proxy ASR also failed:', e);
  }

  throw new Error('语音识别服务不可用');
}
