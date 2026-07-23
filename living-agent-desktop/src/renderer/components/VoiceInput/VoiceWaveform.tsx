/**
 * P10: 音量波形显示
 *
 * 使用 Canvas 实时绘制录音音量波形
 * - 20fps 更新
 * - 显示最近 2s 的音量数据
 */
import { useRef, useEffect } from 'react';

interface VoiceWaveformProps {
  /** 音量级别 0-1 */
  volumeLevel: number;
  /** 是否活跃（录音中） */
  active: boolean;
  /** 波形颜色 */
  color?: string;
  /** 高度 */
  height?: number;
}

const MAX_BARS = 40;
const BAR_WIDTH = 3;
const BAR_GAP = 2;
const BAR_MIN_HEIGHT = 2;

export default function VoiceWaveform({
  volumeLevel,
  active,
  color = '#6366f1',
  height = 60
}: VoiceWaveformProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const barsRef = useRef<number[]>(new Array(MAX_BARS).fill(0));
  const animFrameRef = useRef<number>(0);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const draw = () => {
      const { width, height: h } = canvas;

      // 更新柱状数据：左移 + 添加新值
      const bars = barsRef.current;
      bars.shift();
      bars.push(active ? volumeLevel : 0);

      // 清空画布
      ctx.clearRect(0, 0, width, h);

      // 绘制柱状波形
      const totalWidth = MAX_BARS * (BAR_WIDTH + BAR_GAP) - BAR_GAP;
      const startX = (width - totalWidth) / 2;

      for (let i = 0; i < MAX_BARS; i++) {
        const barHeight = Math.max(BAR_MIN_HEIGHT, bars[i] * h * 0.8);
        const x = startX + i * (BAR_WIDTH + BAR_GAP);
        const y = (h - barHeight) / 2;

        // 颜色渐变：低音量=暗色，高音量=亮色
        const alpha = 0.3 + bars[i] * 0.7;
        ctx.fillStyle = color;
        ctx.globalAlpha = alpha;
        ctx.beginPath();
        ctx.roundRect(x, y, BAR_WIDTH, barHeight, 1.5);
        ctx.fill();
      }

      ctx.globalAlpha = 1;

      if (active) {
        animFrameRef.current = requestAnimationFrame(draw);
      }
    };

    if (active) {
      animFrameRef.current = requestAnimationFrame(draw);
    } else {
      // 非活跃时绘制静默波形
      const bars = barsRef.current;
      bars.fill(0);
      const { width } = canvas;
      const totalWidth = MAX_BARS * (BAR_WIDTH + BAR_GAP) - BAR_GAP;
      const startX = (width - totalWidth) / 2;
      ctx.clearRect(0, 0, canvas.width, h);
      for (let i = 0; i < MAX_BARS; i++) {
        const x = startX + i * (BAR_WIDTH + BAR_GAP);
        const y = (h - BAR_MIN_HEIGHT) / 2;
        ctx.fillStyle = color;
        ctx.globalAlpha = 0.2;
        ctx.beginPath();
        ctx.roundRect(x, y, BAR_WIDTH, BAR_MIN_HEIGHT, 1.5);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
    }

    return () => {
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current);
      }
    };
  }, [volumeLevel, active, color, height]);

  return (
    <canvas
      ref={canvasRef}
      width={200}
      height={height}
      className="voice-waveform"
    />
  );
}
