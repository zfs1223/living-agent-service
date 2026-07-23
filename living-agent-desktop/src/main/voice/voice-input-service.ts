/**
 * P10: 语音输入服务
 *
 * 调用 model_daemon.py 的 ASR 接口
 * - HTTP API 方式调用 Sherpa-ONNX ASR
 * - 支持 WAV 格式音频输入
 * - 返回识别文本
 *
 * 参考：AGENTS.md §5.7 语音对话完整链路
 * - model_daemon.py 在 8390 端口提供 ASR 服务
 * - ASR 模型：Sherpa-ONNX
 */
import { getBackendUrl } from '../api-client';
import { AudioRecorder, AudioChunk } from './audio-recorder';

export interface ASRResult {
  text: string;
  confidence: number;
  isFinal: boolean;
  language?: string;
}

export interface VoiceInputState {
  status: 'idle' | 'recording' | 'processing' | 'error';
  duration: number;        // 录音时长(ms)
  volumeLevel: number;     // 当前音量 0-1
  error?: string;
}

/**
 * 语音输入服务
 * - 管理录音生命周期
 * - 调用 ASR API 进行识别
 * - 提供状态变更回调
 */
export class VoiceInputService {
  private recorder: AudioRecorder | null = null;
  private startTime = 0;
  private state: VoiceInputState = {
    status: 'idle',
    duration: 0,
    volumeLevel: 0,
  };
  private stateListeners: Set<(state: VoiceInputState) => void> = new Set();

  /**
   * 订阅状态变更
   */
  onStateChange(listener: (state: VoiceInputState) => void): () => void {
    this.stateListeners.add(listener);
    return () => this.stateListeners.delete(listener);
  }

  /**
   * 获取当前状态
   */
  getState(): VoiceInputState {
    return { ...this.state };
  }

  private updateState(partial: Partial<VoiceInputState>): void {
    this.state = { ...this.state, ...partial };
    for (const listener of this.stateListeners) {
      listener({ ...this.state });
    }
  }

  /**
   * 开始录音
   */
  async startRecording(): Promise<void> {
    if (this.state.status === 'recording') return;

    this.recorder = new AudioRecorder({
      sampleRate: 16000,
      channelCount: 1,
      frameSize: 960,
      onVolumeLevel: (level) => {
        this.updateState({ volumeLevel: level });
      },
    });

    try {
      await this.recorder.start();
      this.startTime = Date.now();
      this.updateState({
        status: 'recording',
        duration: 0,
        volumeLevel: 0,
        error: undefined,
      });

      // 持续更新录音时长
      this.durationTimer = setInterval(() => {
        if (this.state.status === 'recording') {
          this.updateState({ duration: Date.now() - this.startTime });
        }
      }, 100);
    } catch (err: any) {
      this.updateState({
        status: 'error',
        error: err.message || '录音启动失败',
      });
      throw err;
    }
  }

  private durationTimer: ReturnType<typeof setInterval> | null = null;

  /**
   * 停止录音并识别
   */
  async stopRecordingAndRecognize(): Promise<ASRResult> {
    if (this.state.status !== 'recording' || !this.recorder) {
      return { text: '', confidence: 0, isFinal: true };
    }

    // 停止计时
    if (this.durationTimer) {
      clearInterval(this.durationTimer);
      this.durationTimer = null;
    }

    const chunks = this.recorder.stop();
    this.updateState({ status: 'processing', volumeLevel: 0 });

    try {
      // 合并 PCM 数据
      const pcm = AudioRecorder.mergeChunks(chunks);

      // 检查录音长度（至少 0.3s）
      const durationMs = chunks.length > 0
        ? chunks[chunks.length - 1].timestamp - chunks[0].timestamp
        : 0;

      if (durationMs < 300 || pcm.length < 4800) {
        this.updateState({ status: 'idle', duration: 0 });
        return { text: '', confidence: 0, isFinal: true };
      }

      // 转换为 WAV 格式
      const wavBlob = AudioRecorder.pcmToWav(pcm, 16000, 1);

      // 调用 ASR API
      const result = await this.callASRApi(wavBlob);

      this.updateState({ status: 'idle', duration: 0 });
      return result;
    } catch (err: any) {
      this.updateState({
        status: 'error',
        error: err.message || '语音识别失败',
      });
      return { text: '', confidence: 0, isFinal: true };
    } finally {
      this.recorder = null;
    }
  }

  /**
   * 取消录音
   */
  cancelRecording(): void {
    if (this.durationTimer) {
      clearInterval(this.durationTimer);
      this.durationTimer = null;
    }
    if (this.recorder) {
      this.recorder.stop();
      this.recorder = null;
    }
    this.updateState({ status: 'idle', duration: 0, volumeLevel: 0 });
  }

  /**
   * 调用 ASR API
   *
   * 参考 model_daemon.py 的 ASR 接口
   * 路径：POST /asr（由 model_daemon.py 在 8390 端口提供）
   */
  private async callASRApi(wavBlob: Blob): Promise<ASRResult> {
    const backendUrl = getBackendUrl();

    // 方式1：直接调用 model_daemon.py 的 ASR 端口（8390）
    // 方式2：通过 Java 后端代理（/api/voice/asr）
    // 优先使用直接调用，减少延迟

    // 尝试直接调用 model_daemon.py
    try {
      const daemonUrl = backendUrl.replace(/:\d+/, ':8390');
      const formData = new FormData();
      formData.append('audio', wavBlob, 'recording.wav');

      const response = await fetch(`${daemonUrl}/asr`, {
        method: 'POST',
        body: formData,
      });

      if (response.ok) {
        const data = await response.json();
        return {
          text: data.text || data.transcript || '',
          confidence: data.confidence ?? 0.9,
          isFinal: true,
          language: data.language,
        };
      }
    } catch (e) {
      // 直接调用失败，回退到 Java 后端
      console.warn('[VoiceInput] Direct daemon ASR failed, falling back to Java proxy:', e);
    }

    // 通过 Java 后端代理调用
    try {
      const formData = new FormData();
      formData.append('audio', wavBlob, 'recording.wav');

      const response = await fetch(`${backendUrl}/api/voice/asr`, {
        method: 'POST',
        body: formData,
      });

      if (response.ok) {
        const data = await response.json();
        return {
          text: data.data?.text || data.data?.transcript || data.text || '',
          confidence: data.data?.confidence ?? data.confidence ?? 0.9,
          isFinal: true,
          language: data.data?.language || data.language,
        };
      }
    } catch (e) {
      console.error('[VoiceInput] Java proxy ASR also failed:', e);
    }

    throw new Error('语音识别服务不可用');
  }

  /**
   * 销毁
   */
  destroy(): void {
    this.cancelRecording();
    this.stateListeners.clear();
  }
}
