import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { vitalsApi } from '../services/api';

interface VitalSnapshot {
  timestamp: string;
  healthStatus: string;
  healthScore: number;
  componentCount: number;
  activeConnections: number;
  degradedMode: boolean;
  freeMemoryBytes: number;
  totalMemoryBytes: number;
  maxMemoryBytes: number;
  memoryUsagePercent?: number;
}

function scoreColor(score: number): string {
  if (score >= 0.8) return 'var(--success)';
  if (score >= 0.6) return 'var(--warning)';
  return 'var(--error)';
}

function statusLabel(status: string): string {
  switch (status) {
    case 'HEALTHY': return '健康';
    case 'DEGRADED': return '降级';
    case 'UNHEALTHY': return '异常';
    default: return status;
  }
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function MiniBar({ value, max, color }: { value: number; max: number; color: string }) {
  const pct = Math.min(100, Math.max(0, (value / max) * 100));
  return (
    <div style={{ width: '100%', height: 6, background: 'var(--bg-tertiary)', borderRadius: 3, overflow: 'hidden' }}>
      <div style={{ width: `${pct}%`, height: '100%', background: color, borderRadius: 3, transition: 'width 0.3s' }} />
    </div>
  );
}

export default function VitalSignsDashboard() {
  const { data: vitals, isLoading, error } = useQuery<VitalSnapshot>({
    queryKey: ['vitals-current'],
    queryFn: () => vitalsApi.getCurrent() as Promise<VitalSnapshot>,
    refetchInterval: 15000,
  });

  if (isLoading) return <div style={{ padding: 16, color: 'var(--text-tertiary)', fontSize: 13 }}>加载系统体征...</div>;
  if (error) return <div style={{ padding: 16, color: 'var(--error)', fontSize: 13 }}>加载失败</div>;
  if (!vitals) return null;

  const memUsage = vitals.memoryUsagePercent ?? ((vitals.totalMemoryBytes - vitals.freeMemoryBytes) / vitals.maxMemoryBytes * 100);

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
      {/* 健康分数 */}
      <div style={{ padding: 12, background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)', borderRadius: 8 }}>
        <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 4 }}>健康分数</div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
          <span style={{ fontSize: 24, fontWeight: 700, color: scoreColor(vitals.healthScore) }}>
            {Math.round(vitals.healthScore * 100)}
          </span>
          <span style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>/100</span>
        </div>
        <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>{statusLabel(vitals.healthStatus)}</div>
        <MiniBar value={vitals.healthScore} max={1} color={scoreColor(vitals.healthScore)} />
      </div>

      {/* 内存使用 */}
      <div style={{ padding: 12, background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)', borderRadius: 8 }}>
        <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 4 }}>内存使用</div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
          <span style={{ fontSize: 24, fontWeight: 700, color: memUsage > 85 ? 'var(--error)' : memUsage > 70 ? 'var(--warning)' : 'var(--success)' }}>
            {memUsage.toFixed(1)}%
          </span>
        </div>
        <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
          {formatBytes(vitals.totalMemoryBytes - vitals.freeMemoryBytes)} / {formatBytes(vitals.maxMemoryBytes)}
        </div>
        <MiniBar value={memUsage} max={100} color={memUsage > 85 ? 'var(--error)' : memUsage > 70 ? 'var(--warning)' : 'var(--success)'} />
      </div>

      {/* 活跃连接 */}
      <div style={{ padding: 12, background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)', borderRadius: 8 }}>
        <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 4 }}>活跃连接</div>
        <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--accent-text)' }}>{vitals.activeConnections}</div>
      </div>

      {/* 组件状态 */}
      <div style={{ padding: 12, background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)', borderRadius: 8 }}>
        <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 4 }}>健康组件</div>
        <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--success)' }}>{vitals.componentCount}</div>
      </div>

      {/* 运行模式 */}
      <div style={{ padding: 12, background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)', borderRadius: 8 }}>
        <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginBottom: 4 }}>运行模式</div>
        <div style={{ fontSize: 16, fontWeight: 600, color: vitals.degradedMode ? 'var(--warning)' : 'var(--success)' }}>
          {vitals.degradedMode ? '降级模式' : '正常模式'}
        </div>
        {vitals.degradedMode && (
          <div style={{ fontSize: 10, color: 'var(--warning)', marginTop: 2 }}>系统以精简模式运行</div>
        )}
      </div>
    </div>
  );
}
