/**
 * P21: 生命体征仪表盘页面
 *
 * 显示 LAS 系统的实时健康状态、组件数量、连接数、内存使用、预警记录等
 * 后端 API: GET /api/vitals (当前快照) + GET /api/vitals/history?minutes=30 (历史记录)
 */
import { useState, useEffect, useCallback } from 'react';
import './VitalsDashboard.css';

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
}

export default function VitalsDashboard({ backendUrl, hasToken }: { backendUrl: string; hasToken: boolean }) {
  const [current, setCurrent] = useState<VitalSnapshot | null>(null);
  const [history, setHistory] = useState<VitalSnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [historyMinutes, setHistoryMinutes] = useState(30);

  const loadData = useCallback(async () => {
    if (!hasToken || !backendUrl) return;
    try {
      const token = await window.livingAgentAPI.auth.getToken();
      const headers = { Authorization: `Bearer ${token}` };

      const [vitalsRes, historyRes] = await Promise.all([
        fetch(`${backendUrl}/api/vitals`, { headers }),
        fetch(`${backendUrl}/api/vitals/history?minutes=${historyMinutes}`, { headers }),
      ]);

      if (vitalsRes.ok) {
        const data = await vitalsRes.json();
        setCurrent(data.data || data);
      }
      if (historyRes.ok) {
        const data = await historyRes.json();
        setHistory(Array.isArray(data.data) ? data.data : Array.isArray(data) ? data : []);
      }
      setError('');
    } catch (e: any) {
      setError(e.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [backendUrl, hasToken, historyMinutes]);

  useEffect(() => {
    loadData();
    if (!autoRefresh) return;
    const interval = setInterval(loadData, 10000); // 10秒刷新
    return () => clearInterval(interval);
  }, [loadData, autoRefresh]);

  if (!hasToken) return <div className="vitals__empty">请先登录</div>;
  if (loading) return <div className="vitals__empty">加载中...</div>;
  if (error) return <div className="vitals__error">{error}</div>;

  const memoryPercent = current
    ? ((current.totalMemoryBytes - current.freeMemoryBytes) / current.maxMemoryBytes * 100)
    : 0;

  const healthColor =
    current?.healthStatus === 'HEALTHY' ? '#52c41a' :
    current?.healthStatus === 'DEGRADED' ? '#faad14' :
    current?.healthStatus === 'UNHEALTHY' ? '#ff4d4f' : '#999';

  return (
    <div className="vitals-dashboard">
      <div className="vitals-dashboard__header">
        <h1>💓 生命体征仪表盘</h1>
        <div className="vitals-dashboard__controls">
          <label className="vitals-dashboard__auto-refresh">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={e => setAutoRefresh(e.target.checked)}
            />
            自动刷新
          </label>
          <select
            className="vitals-dashboard__history-range"
            value={historyMinutes}
            onChange={e => setHistoryMinutes(Number(e.target.value))}
          >
            <option value={10}>10 分钟</option>
            <option value={30}>30 分钟</option>
            <option value={60}>1 小时</option>
            <option value={180}>3 小时</option>
          </select>
        </div>
      </div>

      {/* 健康总览 */}
      {current && (
        <div className="vitals-overview">
          {/* 健康状态卡 */}
          <div className="vitals-card vitals-card--health">
            <div className="vitals-card__label">系统健康</div>
            <div className="vitals-card__status" style={{ color: healthColor }}>
              {current.degradedMode ? '⚠️ 降级' :
                current.healthStatus === 'HEALTHY' ? '✅ 正常' :
                current.healthStatus === 'DEGRADED' ? '⚠️ 降级' :
                '❌ 异常'}
            </div>
            <div className="vitals-card__score">
              健康分: <strong>{current.healthScore.toFixed(1)}</strong>/100
            </div>
          </div>

          {/* 组件数量卡 */}
          <div className="vitals-card">
            <div className="vitals-card__label">活跃组件</div>
            <div className="vitals-card__value">{current.componentCount}</div>
          </div>

          {/* 连接数卡 */}
          <div className="vitals-card">
            <div className="vitals-card__label">活跃连接</div>
            <div className="vitals-card__value">{current.activeConnections}</div>
          </div>

          {/* 内存卡 */}
          <div className="vitals-card">
            <div className="vitals-card__label">内存使用</div>
            <div className="vitals-card__value">{memoryPercent.toFixed(1)}%</div>
            <div className="vitals-progress">
              <div
                className="vitals-progress__bar"
                style={{
                  width: `${memoryPercent}%`,
                  background: memoryPercent > 80 ? '#ff4d4f' : memoryPercent > 60 ? '#faad14' : '#52c41a',
                }}
              />
            </div>
            <div className="vitals-card__detail">
              {formatBytes(current.totalMemoryBytes - current.freeMemoryBytes)} / {formatBytes(current.maxMemoryBytes)}
            </div>
          </div>
        </div>
      )}

      {/* 内存历史趋势图 */}
      {history.length > 1 && (
        <div className="vitals-section">
          <h2>内存使用趋势</h2>
          <div className="vitals-chart">
            <svg viewBox="0 0 600 120" className="vitals-chart__svg">
              {renderMemoryChart(history)}
            </svg>
          </div>
        </div>
      )}

      {/* 健康分数趋势图 */}
      {history.length > 1 && (
        <div className="vitals-section">
          <h2>健康分数趋势</h2>
          <div className="vitals-chart">
            <svg viewBox="0 0 600 120" className="vitals-chart__svg">
              {renderHealthChart(history)}
            </svg>
          </div>
        </div>
      )}

      {/* 最后更新时间 */}
      {current?.timestamp && (
        <div className="vitals-dashboard__footer">
          最后更新: {new Date(current.timestamp).toLocaleString()}
        </div>
      )}
    </div>
  );
}

/** 格式化字节数 */
function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i];
}

/** 渲染内存趋势 SVG */
function renderMemoryChart(data: VitalSnapshot[]) {
  const points = data.map(d => ({
    x: 0, // 占位，下面计算
    y: d.maxMemoryBytes > 0
      ? ((d.totalMemoryBytes - d.freeMemoryBytes) / d.maxMemoryBytes) * 100
      : 0,
  }));

  const width = 600;
  const height = 100;
  const padding = 10;

  const step = (width - 2 * padding) / Math.max(points.length - 1, 1);
  points.forEach((p, i) => {
    p.x = padding + i * step;
  });

  const pathD = points.map((p, i) =>
    `${i === 0 ? 'M' : 'L'} ${p.x} ${height - padding - (p.y / 100) * (height - 2 * padding)}`
  ).join(' ');

  return (
    <>
      {/* Y 轴刻度 */}
      <text x="4" y={padding + 4} fill="#999" fontSize="9">100%</text>
      <text x="4" y={height / 2} fill="#999" fontSize="9">50%</text>
      <text x="4" y={height - padding} fill="#999" fontSize="9">0%</text>
      {/* 网格线 */}
      <line x1={padding} y1={padding} x2={width - padding} y2={padding} stroke="#f0f0f0" />
      <line x1={padding} y1={height / 2} x2={width - padding} y2={height / 2} stroke="#f0f0f0" />
      <line x1={padding} y1={height - padding} x2={width - padding} y2={height - padding} stroke="#f0f0f0" />
      {/* 折线 */}
      <path d={pathD} fill="none" stroke="#1890ff" strokeWidth="2" />
      {/* 填充区域 */}
      <path
        d={`${pathD} L ${points[points.length - 1].x} ${height - padding} L ${points[0].x} ${height - padding} Z`}
        fill="rgba(24, 144, 255, 0.1)"
      />
    </>
  );
}

/** 渲染健康分数趋势 SVG */
function renderHealthChart(data: VitalSnapshot[]) {
  const points = data.map(d => ({
    x: 0,
    y: d.healthScore,
  }));

  const width = 600;
  const height = 100;
  const padding = 10;

  const step = (width - 2 * padding) / Math.max(points.length - 1, 1);
  points.forEach((p, i) => {
    p.x = padding + i * step;
  });

  const pathD = points.map((p, i) =>
    `${i === 0 ? 'M' : 'L'} ${p.x} ${height - padding - (p.y / 100) * (height - 2 * padding)}`
  ).join(' ');

  return (
    <>
      <text x="4" y={padding + 4} fill="#999" fontSize="9">100</text>
      <text x="4" y={height / 2} fill="#999" fontSize="9">50</text>
      <text x="4" y={height - padding} fill="#999" fontSize="9">0</text>
      <line x1={padding} y1={padding} x2={width - padding} y2={padding} stroke="#f0f0f0" />
      <line x1={padding} y1={height / 2} x2={width - padding} y2={height / 2} stroke="#f0f0f0" />
      <line x1={padding} y1={height - padding} x2={width - padding} y2={height - padding} stroke="#f0f0f0" />
      <path d={pathD} fill="none" stroke="#52c41a" strokeWidth="2" />
      <path
        d={`${pathD} L ${points[points.length - 1].x} ${height - padding} L ${points[0].x} ${height - padding} Z`}
        fill="rgba(82, 196, 26, 0.1)"
      />
    </>
  );
}