/**
 * P19: 客户价值指标可视化
 *
 * 为 cs/sales/core 部门展示客户价值指标
 * - VOC (Voice of Customer) 客户之声
 * - Net Value Score 净价值评分
 * - 客户满意度趋势
 * - 客户分层分布
 */
import { useState, useEffect } from 'react';
import './CustomerValueDashboard.css';

interface CustomerMetric {
  id: string;
  customerName: string;
  valueScore: number;      // 0-100
  satisfactionScore: number; // 0-100
  interactionCount: number;
  lastInteraction: string;
  tier: 'A' | 'B' | 'C' | 'D';
  trend: 'up' | 'down' | 'stable';
}

interface CustomerValueDashboardProps {
  backendUrl: string;
  hasToken: boolean;
  currentUser: any;
}

export default function CustomerValueDashboard({ backendUrl, hasToken, currentUser }: CustomerValueDashboardProps) {
  const [metrics, setMetrics] = useState<CustomerMetric[]>([]);
  const [loading, setLoading] = useState(true);
  const [timeRange, setTimeRange] = useState<'7d' | '30d' | '90d'>('30d');
  const [selectedTier, setSelectedTier] = useState<string>('all');

  // 检查权限：仅 cs/sales/core 或 FULL 权限可访问
  const canAccess = ['cs', 'sales', 'core'].includes(currentUser?.department) ||
                    currentUser?.accessLevel === 'FULL' ||
                    currentUser?.identity === 'INTERNAL_ENTERPRISE';

  useEffect(() => {
    if (canAccess) {
      loadMetrics();
    }
  }, [backendUrl, hasToken, timeRange]);

  const loadMetrics = async () => {
    setLoading(true);
    try {
      // 实际应调用 /api/customer-metrics API
      // 模拟数据
      await new Promise(resolve => setTimeout(resolve, 500));
      setMetrics(generateMockMetrics());
    } catch (err) {
      console.error('[CustomerValue] 加载失败:', err);
    } finally {
      setLoading(false);
    }
  };

  // 计算汇总统计
  const stats = {
    totalCustomers: metrics.length,
    avgValueScore: metrics.length > 0 
      ? Math.round(metrics.reduce((sum, m) => sum + m.valueScore, 0) / metrics.length)
      : 0,
    avgSatisfaction: metrics.length > 0
      ? Math.round(metrics.reduce((sum, m) => sum + m.satisfactionScore, 0) / metrics.length)
      : 0,
    tierDistribution: {
      A: metrics.filter(m => m.tier === 'A').length,
      B: metrics.filter(m => m.tier === 'B').length,
      C: metrics.filter(m => m.tier === 'C').length,
      D: metrics.filter(m => m.tier === 'D').length,
    }
  };

  // 过滤客户层级
  const filteredMetrics = selectedTier === 'all'
    ? metrics
    : metrics.filter(m => m.tier === selectedTier);

  if (!hasToken) {
    return (
      <div className="customer-value">
        <div className="customer-value__login-prompt">
          <span>🔐 请先登录以查看客户价值指标</span>
        </div>
      </div>
    );
  }

  if (!canAccess) {
    return (
      <div className="customer-value">
        <div className="customer-value__permission-denied">
          <span>⛔ 权限不足：仅客服部/销售部/核心部门可访问客户价值指标</span>
        </div>
      </div>
    );
  }

  return (
    <div className="customer-value">
      <div className="customer-value__header">
        <h1>📊 客户价值指标</h1>
        <div className="customer-value__filters">
          <select value={timeRange} onChange={(e) => setTimeRange(e.target.value as any)}>
            <option value="7d">近 7 天</option>
            <option value="30d">近 30 天</option>
            <option value="90d">近 90 天</option>
          </select>
          <select value={selectedTier} onChange={(e) => setSelectedTier(e.target.value)}>
            <option value="all">全部层级</option>
            <option value="A">A 级客户</option>
            <option value="B">B 级客户</option>
            <option value="C">C 级客户</option>
            <option value="D">D 级客户</option>
          </select>
          <button onClick={loadMetrics} disabled={loading}>🔄 刷新</button>
        </div>
      </div>

      {/* 汇总卡片 */}
      <div className="customer-value__stats">
        <div className="stat-card">
          <div className="stat-card__label">总客户数</div>
          <div className="stat-card__value">{stats.totalCustomers}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">平均价值评分</div>
          <div className="stat-card__value">{stats.avgValueScore}</div>
          <div className="stat-card__progress">
            <div className="progress-bar" style={{ width: `${stats.avgValueScore}%` }} />
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">平均满意度</div>
          <div className="stat-card__value">{stats.avgSatisfaction}%</div>
          <div className="stat-card__progress">
            <div className="progress-bar progress-bar--satisfaction" style={{ width: `${stats.avgSatisfaction}%` }} />
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">层级分布</div>
          <div className="tier-distribution">
            <div className="tier-bar tier-a" style={{ width: `${(stats.tierDistribution.A / stats.totalCustomers) * 100}%` }}>
              A: {stats.tierDistribution.A}
            </div>
            <div className="tier-bar tier-b" style={{ width: `${(stats.tierDistribution.B / stats.totalCustomers) * 100}%` }}>
              B: {stats.tierDistribution.B}
            </div>
            <div className="tier-bar tier-c" style={{ width: `${(stats.tierDistribution.C / stats.totalCustomers) * 100}%` }}>
              C: {stats.tierDistribution.C}
            </div>
            <div className="tier-bar tier-d" style={{ width: `${(stats.tierDistribution.D / stats.totalCustomers) * 100}%` }}>
              D: {stats.tierDistribution.D}
            </div>
          </div>
        </div>
      </div>

      {/* 客户列表 */}
      <div className="customer-value__content">
        {loading ? (
          <div className="customer-value__loading">加载中...</div>
        ) : (
          <div className="customer-list">
            <div className="customer-list__header">
              <span className="customer-col customer-col--name">客户名称</span>
              <span className="customer-col customer-col--tier">层级</span>
              <span className="customer-col customer-col--value">价值评分</span>
              <span className="customer-col customer-col--satisfaction">满意度</span>
              <span className="customer-col customer-col--interactions">交互次数</span>
              <span className="customer-col customer-col--trend">趋势</span>
            </div>
            {filteredMetrics.map(metric => (
              <div key={metric.id} className="customer-row">
                <span className="customer-col customer-col--name">{metric.customerName}</span>
                <span className={`customer-col customer-col--tier tier-${metric.tier.toLowerCase()}`}>
                  {metric.tier}
                </span>
                <span className="customer-col customer-col--value">
                  <span className="value-score">{metric.valueScore}</span>
                  <div className="mini-progress">
                    <div className="mini-progress-bar" style={{ width: `${metric.valueScore}%` }} />
                  </div>
                </span>
                <span className="customer-col customer-col--satisfaction">
                  {metric.satisfactionScore}%
                </span>
                <span className="customer-col customer-col--interactions">
                  {metric.interactionCount}
                </span>
                <span className={`customer-col customer-col--trend trend-${metric.trend}`}>
                  {metric.trend === 'up' ? '📈' : metric.trend === 'down' ? '📉' : '➡️'}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* VOC 摘要 */}
      <div className="customer-value__voc">
        <h2>🎤 客户之声 (VOC)</h2>
        <div className="voc-highlights">
          <div className="voc-item voc-positive">
            <h3>👍 正面反馈</h3>
            <ul>
              <li>响应速度满意 (92%)</li>
              <li>解决方案有效 (88%)</li>
              <li>服务态度好评 (95%)</li>
            </ul>
          </div>
          <div className="voc-item voc-negative">
            <h3>👎 待改进点</h3>
            <ul>
              <li>等待时间过长 (15%反馈)</li>
              <li>跨部门协作需优化 (8%反馈)</li>
              <li>首次解决率待提升 (12%反馈)</li>
            </ul>
          </div>
          <div className="voc-item voc-suggestion">
            <h3>💡 客户建议</h3>
            <ul>
              <li>增加自助服务入口</li>
              <li>提供进度实时追踪</li>
              <li>优化移动端体验</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}

// 生成模拟数据
function generateMockMetrics(): CustomerMetric[] {
  const customers = [
    '华为技术有限公司', '腾讯科技', '阿里巴巴集团', '字节跳动',
    '美团点评', '京东集团', '小米科技', '百度在线',
    '网易公司', '滴滴出行', '快手科技', '哔哩哔哩'
  ];

  return customers.map((name, i) => ({
    id: `cust-${i}`,
    customerName: name,
    valueScore: Math.floor(Math.random() * 40) + 60,
    satisfactionScore: Math.floor(Math.random() * 20) + 80,
    interactionCount: Math.floor(Math.random() * 50) + 10,
    lastInteraction: new Date(Date.now() - Math.random() * 7 * 24 * 3600000).toISOString(),
    tier: (['A', 'B', 'C', 'D'] as const)[Math.floor(Math.random() * 4)],
    trend: (['up', 'down', 'stable'] as const)[Math.floor(Math.random() * 3)]
  }));
}