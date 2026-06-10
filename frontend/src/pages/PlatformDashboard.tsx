import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { getToken } from '../stores';

function formatTokens(n: number | null | undefined): string {
    if (n == null) return '-';
    if (n < 1000) return String(n);
    if (n < 1_000_000) return (n / 1000).toFixed(n < 10_000 ? 1 : 0) + 'K';
    if (n < 1_000_000_000) return (n / 1_000_000).toFixed(n < 10_000_000 ? 1 : 0) + 'M';
    return (n / 1_000_000_000).toFixed(1) + 'B';
}

export default function PlatformDashboard() {
    const { t } = useTranslation();
    const [timeRange, setTimeRange] = useState<30 | 7>(30);
    const [loadingStats, setLoadingStats] = useState(false);
    const [loadingLeaders, setLoadingLeaders] = useState(false);
    
    const [timeSeriesData, setTimeSeriesData] = useState<any[]>([]);
    const [topCompanies, setTopCompanies] = useState<any[]>([]);
    const [topAgents, setTopAgents] = useState<any[]>([]);

    const fetchTimeSeries = async (days: number) => {
        setLoadingStats(true);
        try {
            const end = new Date();
            const start = new Date();
            start.setDate(start.getDate() - days);
            
            const token = getToken();
            const res = await fetch(`/api/admin/metrics/timeseries?start_date=${start.toISOString()}&end_date=${end.toISOString()}`, {
                headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
            });
            if (res.ok) {
                const data = await res.json();
                setTimeSeriesData(data);
            }
        } catch (e) {
            console.error('Failed to load metrics:', e);
        }
        setLoadingStats(false);
    };

    const fetchLeaderboards = async () => {
        setLoadingLeaders(true);
        try {
            const token = getToken();
            const res = await fetch('/api/admin/metrics/leaderboards', {
                headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
            });
            if (res.ok) {
                const data = await res.json();
                setTopCompanies(data.top_companies || []);
                setTopAgents(data.top_agents || []);
            }
        } catch (e) {
            console.error('Failed to load leaderboards:', e);
        }
        setLoadingLeaders(false);
    };

    useEffect(() => {
        fetchTimeSeries(timeRange);
    }, [timeRange]);

    useEffect(() => {
        fetchLeaderboards();
    }, []);

    const CustomTooltip = ({ active, payload, label }: any) => {
        if (active && payload && payload.length) {
            return (
                <div style={{
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: '8px',
                    padding: '12px',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
                    fontSize: '12px'
                }}>
                    <div style={{ fontWeight: 600, marginBottom: '8px', color: 'var(--text-secondary)' }}>{label}</div>
                    {payload.map((p: any, i: number) => (
                        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                            <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: p.stroke }} />
                            <span style={{ color: 'var(--text-tertiary)' }}>{p.name}:</span>
                            <span style={{ fontWeight: 500 }}>{p.dataKey.includes('tokens') ? formatTokens(p.value) : p.value}</span>
                        </div>
                    ))}
                </div>
            );
        }
        return null;
    };

    const ChartCard = ({ title, dataKeyTotal, dataKeyNew, color }: { title: string, dataKeyTotal: string, dataKeyNew: string, color: string }) => (
        <div className="card" style={{ flex: 1, minWidth: '300px', padding: '20px' }}>
            <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '20px', color: 'var(--text-secondary)' }}>{title}</div>
            <div style={{ height: '240px', width: '100%' }}>
                {loadingStats ? (
                    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-tertiary)', fontSize: '12px' }}>{t('common.loading', '加载中...')}</div>
                ) : (
                    <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={timeSeriesData} margin={{ top: 5, right: 5, left: -20, bottom: 5 }}>
                            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--border-subtle)" />
                            <XAxis dataKey="date" tick={{ fontSize: 10, fill: 'var(--text-tertiary)' }} tickLine={false} axisLine={false} tickFormatter={(val) => val.substring(5)} />
                            <YAxis yAxisId="left" tick={{ fontSize: 10, fill: 'var(--text-tertiary)' }} tickLine={false} axisLine={false} tickFormatter={formatTokens} />
                            <Tooltip content={<CustomTooltip />} />
                            <Line yAxisId="left" type="monotone" dataKey={dataKeyTotal} name={`Cumulative`} stroke={color} strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
                            <Line yAxisId="left" type="monotone" dataKey={dataKeyNew} name={`New`} stroke={color} opacity={0.3} strokeWidth={2} dot={false} strokeDasharray="4 4" />
                        </LineChart>
                    </ResponsiveContainer>
                )}
            </div>
        </div>
    );

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div style={{
                borderRadius: '24px',
                padding: '22px',
                background: 'linear-gradient(135deg, rgba(168,85,247,0.12), rgba(12,18,28,0.84) 48%, rgba(5,6,10,0.96))',
                border: '1px solid rgba(255,255,255,0.08)',
                boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '18px', alignItems: 'flex-start' }}>
                    <div style={{ maxWidth: '760px' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 10px', borderRadius: '999px', background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)', fontSize: '12px', marginBottom: '14px' }}>
                            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-primary)', boxShadow: '0 0 18px rgba(168,85,247,0.85)' }} />
                            平台总览
                        </div>
                        <h1 style={{ fontSize: '28px', fontWeight: 700, margin: 0, letterSpacing: '-0.04em', color: 'var(--text-primary)' }}>平台仪表盘</h1>
                        <p style={{ margin: '10px 0 0', color: 'var(--text-secondary)', fontSize: '13px', lineHeight: 1.75, maxWidth: '68ch' }}>
                            全局平台的公司、用户与模型消耗趋势概览。
                        </p>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '10px', minWidth: '320px' }}>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>公司数</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px' }}>{topCompanies.length}</div>
                        </div>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>员工数</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px' }}>{topAgents.length}</div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Range Toggle */}
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <div style={{ display: 'flex', background: 'var(--bg-secondary)', padding: '4px', borderRadius: '8px', border: '1px solid var(--border-subtle)' }}>
                    <button
                        onClick={() => setTimeRange(7)}
                        style={{
                            padding: '6px 16px', fontSize: '12px', fontWeight: 500, borderRadius: '6px',
                            background: timeRange === 7 ? 'var(--bg-primary)' : 'transparent',
                            color: timeRange === 7 ? 'var(--text-primary)' : 'var(--text-tertiary)',
                            boxShadow: timeRange === 7 ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                            border: 'none', cursor: 'pointer', transition: 'all 0.2s'
                        }}>
                        近 7 天
                    </button>
                    <button
                        onClick={() => setTimeRange(30)}
                        style={{
                            padding: '6px 16px', fontSize: '12px', fontWeight: 500, borderRadius: '6px',
                            background: timeRange === 30 ? 'var(--bg-primary)' : 'transparent',
                            color: timeRange === 30 ? 'var(--text-primary)' : 'var(--text-tertiary)',
                            boxShadow: timeRange === 30 ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
                            border: 'none', cursor: 'pointer', transition: 'all 0.2s'
                        }}>
                        近 30 天
                    </button>
                </div>
            </div>

            {/* Charts Row */}
            <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
                <ChartCard title="公司趋势" dataKeyTotal="total_companies" dataKeyNew="new_companies" color="#3b82f6" />
                <ChartCard title="用户趋势" dataKeyTotal="total_users" dataKeyNew="new_users" color="#10b981" />
                <ChartCard title="Token 消耗" dataKeyTotal="total_tokens" dataKeyNew="new_tokens" color="#8b5cf6" />
            </div>

            {/* Leaderboards */}
            <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
                <div className="card" style={{ flex: 1, minWidth: '300px', padding: '0', overflow: 'hidden' }}>
                    <div style={{ padding: '20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)', borderBottom: '1px solid var(--border-subtle)' }}>
                        Token 消耗 Top 20 公司
                    </div>
                    {loadingLeaders ? (
                        <div style={{ padding: '40px', textAlign: 'center', fontSize: '12px', color: 'var(--text-tertiary)' }}>加载中...</div>
                    ) : (
                        <div>
                            {topCompanies.map((c, i) => (
                                <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 20px', borderBottom: '1px solid var(--border-subtle)', fontSize: '13px' }}>
                                    <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', width: '20px' }}>#{i + 1}</span>
                                        <span style={{ fontWeight: 500 }}>{c.name}</span>
                                    </div>
                                    <div style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', color: 'var(--text-secondary)' }}>
                                        {formatTokens(c.tokens)}
                                    </div>
                                </div>
                            ))}
                            {topCompanies.length === 0 && <div style={{ padding: '20px', textAlign: 'center', fontSize: '12px', color: 'var(--text-tertiary)' }}>暂无数据</div>}
                        </div>
                    )}
                </div>

                <div className="card" style={{ flex: 1, minWidth: '300px', padding: '0', overflow: 'hidden' }}>
                    <div style={{ padding: '20px', fontSize: '13px', fontWeight: 600, color: 'var(--text-secondary)', borderBottom: '1px solid var(--border-subtle)' }}>
                        Token 消耗 Top 20 员工
                    </div>
                    {loadingLeaders ? (
                        <div style={{ padding: '40px', textAlign: 'center', fontSize: '12px', color: 'var(--text-tertiary)' }}>加载中...</div>
                    ) : (
                        <div>
                            {topAgents.map((a, i) => (
                                <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 20px', borderBottom: '1px solid var(--border-subtle)', fontSize: '13px' }}>
                                    <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                                        <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', width: '20px' }}>#{i + 1}</span>
                                        <div>
                                            <div style={{ fontWeight: 500 }}>{a.name}</div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{a.company}</div>
                                        </div>
                                    </div>
                                    <div style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', color: 'var(--text-secondary)' }}>
                                        {formatTokens(a.tokens)}
                                    </div>
                                </div>
                            ))}
                            {topAgents.length === 0 && <div style={{ padding: '20px', textAlign: 'center', fontSize: '12px', color: 'var(--text-tertiary)' }}>暂无数据</div>}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
