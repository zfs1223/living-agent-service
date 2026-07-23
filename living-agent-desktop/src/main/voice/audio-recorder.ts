/**
 * P10: 音频采集模块
 *
 * 使用 Web Audio API 采集麦克风音频
 * - 16kHz 采样率 / 单声道 / 960 帧大小（项目规则 §4.3）
 * - 支持 Opus 编码（可选）
 * - 提供音量级别回调（用于波形显示）
 */
import { EventEmitter } from 'events';

export interface AudioRecorderOptions {
  sampleRate?: number;       // 默认 16000
  channelCount?: number;     // 默认 1（单声道）
  frameSize?: number;        // 默认 960
  onVolumeLevel?: (level: number) => void; // 音量回调 0-1
}

export interface AudioChunk {
  pcm: Int16Array;           // PCM 16-bit 数据
  timestamp: number;         // 时间戳
}

/**
 * 音频采集器
 * - 渲染进程使用（通过 getUserMedia）
 * - 提供 PCM 原始数据 + 音量级别
 */
export class AudioRecorder extends EventEmitter {
  private options: Required<AudioRecorderOptions>;
  private mediaStream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private sourceNode: MediaStreamAudioSourceNode | null = null;
  private processorNode: ScriptProcessorNode | null = null;
  private analyserNode: AnalyserNode | null = null;
  private recording = false;
  private chunks: AudioChunk[] = [];

  constructor(options: AudioRecorderOptions = {}) {
    super();
    this.options = {
      sampleRate: options.sampleRate ?? 16000,
      channelCount: options.channelCount ?? 1,
      frameSize: options.frameSize ?? 960,
      onVolumeLevel: options.onVolumeLevel ?? (() => {}),
    };
  }

  get isRecording(): boolean {
    return this.recording;
  }

  /**
   * 请求麦克风权限并开始录音
   */
  async start(): Promise<void> {
    if (this.recording) return;

    try {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: this.options.channelCount,
          sampleRate: this.options.sampleRate,
          echoCancellation: true,
          noiseSuppression: true,
        }
      });

      // 创建 AudioContext（16kHz）
      this.audioContext = new AudioContext({
        sampleRate: this.options.sampleRate,
      });

      this.sourceNode = this.audioContext.createMediaStreamSource(this.mediaStream);

      // 分析器节点（用于音量检测）
      this.analyserNode = this.audioContext.createAnalyser();
      this.analyserNode.fftSize = 2048;
      this.sourceNode.connect(this.analyserNode);

      // 处理器节点（用于获取 PCM 数据）
      this.processorNode = this.audioContext.createScriptProcessor(
        this.options.frameSize,
        this.options.channelCount,
        this.options.channelCount
      );

      this.processorNode.onaudioprocess = (event) => {
        if (!this.recording) return;

        const inputData = event.inputBuffer.getChannelData(0);
        // Float32 → Int16 PCM 转换
        const pcm = new Int16Array(inputData.length);
        for (let i = 0; i < inputData.length; i++) {
          const s = Math.max(-1, Math.min(1, inputData[i]));
          pcm[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
        }

        this.chunks.push({
          pcm,
          timestamp: Date.now(),
        });

        this.emit('chunk', { pcm, timestamp: Date.now() } as AudioChunk);
      };

      this.sourceNode.connect(this.processorNode);
      this.processorNode.connect(this.audioContext.destination);

      this.recording = true;
      this.chunks = [];

      // 启动音量监测
      this.startVolumeMonitoring();

      this.emit('start');
    } catch (err: any) {
      this.emit('error', new Error(`麦克风访问失败: ${err.message}`));
      throw err;
    }
  }

  /**
   * 停止录音，返回所有 PCM 数据
   */
  stop(): AudioChunk[] {
    if (!this.recording) return [];

    this.recording = false;

    // 断开节点
    if (this.processorNode) {
      this.processorNode.disconnect();
      this.processorNode = null;
    }
    if (this.analyserNode) {
      this.analyserNode = null;
    }
    if (this.sourceNode) {
      this.sourceNode.disconnect();
      this.sourceNode = null;
    }
    if (this.audioContext) {
      this.audioContext.close();
      this.audioContext = null;
    }
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(t => t.stop());
      this.mediaStream = null;
    }

    const result = [...this.chunks];
    this.chunks = [];

    this.emit('stop', result);
    return result;
  }

  /**
   * 合并所有 PCM chunk 为单个 Int16Array
   */
  static mergeChunks(chunks: AudioChunk[]): Int16Array {
    const totalLength = chunks.reduce((sum, c) => sum + c.pcm.length, 0);
    const merged = new Int16Array(totalLength);
    let offset = 0;
    for (const chunk of chunks) {
      merged.set(chunk.pcm, offset);
      offset += chunk.pcm.length;
    }
    return merged;
  }

  /**
   * Int16 PCM → WAV Blob（用于发送到 ASR API）
   */
  static pcmToWav(pcm: Int16Array, sampleRate: number = 16000, channelCount: number = 1): Blob {
    const buffer = new ArrayBuffer(44 + pcm.length * 2);
    const view = new DataView(buffer);

    // WAV 文件头
    const writeString = (offset: number, str: string) => {
      for (let i = 0; i < str.length; i++) {
        view.setUint8(offset + i, str.charCodeAt(i));
      }
    };

    writeString(0, 'RIFF');
    view.setUint32(4, 36 + pcm.length * 2, true);
    writeString(8, 'WAVE');
    writeString(12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);                // PCM
    view.setUint16(22, channelCount, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * channelCount * 2, true);
    view.setUint16(32, channelCount * 2, true);
    view.setUint16(34, 16, true);
    writeString(36, 'data');
    view.setUint32(40, pcm.length * 2, true);

    // PCM 数据
    const dataOffset = 44;
    for (let i = 0; i < pcm.length; i++) {
      view.setInt16(dataOffset + i * 2, pcm[i], true);
    }

    return new Blob([buffer], { type: 'audio/wav' });
  }

  /**
   * 获取当前音量级别（0-1）
   */
  getVolumeLevel(): number {
    if (!this.analyserNode) return 0;
    const dataArray = new Uint8Array(this.analyserNode.frequencyBinCount);
    this.analyserNode.getByteTimeDomainData(dataArray);

    let sum = 0;
    for (let i = 0; i < dataArray.length; i++) {
      const normalized = (dataArray[i] - 128) / 128;
      sum += normalized * normalized;
    }
    return Math.sqrt(sum / dataArray.length);
  }

  private volumeMonitorInterval: ReturnType<typeof setInterval> | null = null;

  private startVolumeMonitoring(): void {
    if (this.volumeMonitorInterval) return;
    this.volumeMonitorInterval = setInterval(() => {
      if (!this.recording) {
        this.stopVolumeMonitoring();
        return;
      }
      const level = this.getVolumeLevel();
      this.options.onVolumeLevel(level);
      this.emit('volume', level);
    }, 50); // 20fps 音量更新
  }

  private stopVolumeMonitoring(): void {
    if (this.volumeMonitorInterval) {
      clearInterval(this.volumeMonitorInterval);
      this.volumeMonitorInterval = null;
    }
    this.options.onVolumeLevel(0);
    this.emit('volume', 0);
  }

  /**
   * 销毁资源
   */
  destroy(): void {
    this.stop();
    this.stopVolumeMonitoring();
    this.removeAllListeners();
  }
}
