import { useState } from 'react';
import { useTranslation } from 'react-i18next';

// ─── Audit Log Tab ─────────────────────────────────
export default function AuditLogTab({ auditLogs, auditFilter, setAuditFilter, BG_ACTIONS }: { auditLogs: any[]; auditFilter: string; setAuditFilter: (f: any) => void; BG_ACTIONS: string[] }) {
    const { t } = useTranslation();
    const [searchQuery, setSearchQuery] = useState('');
    const [expandedLogId, setExpandedLogId] = useState<string | null>(null);
    const [page, setPage] = useState(1);
    const PAGE_SIZE = 50;
    const [dateFrom, setDateFrom] = useState('');
    const [dateTo, setDateTo] = useState('');

    const filteredAuditLogs = auditLogs.filter((log: any) => {
        if (auditFilter === 'background') return BG_ACTIONS.includes(log.action);
        if (auditFilter === 'actions') return !BG_ACTIONS.includes(log.action);
        return true;
    }).filter((log: any) => {
        if (!searchQuery.trim()) return true;
        const q = searchQuery.toLowerCase();
        return (log.action?.toLowerCase().includes(q) ||
            log.agent_id?.toLowerCase().includes(q) ||
            log.agent_name?.toLowerCase().includes(q) ||
            JSON.stringify(log.details || {}).toLowerCase().includes(q));
    }).filter((log: any) => {
        if (!dateFrom && !dateTo) return true;
        const logDate = log.created_at ? new Date(log.created_at) : null;
        if (!logDate) return false;
        if (dateFrom && logDate < new Date(dateFrom)) return false;
        if (dateTo && logDate > new Date(dateTo + 'T23:59:59')) return false;
        return true;
    });

    const totalPages = Math.ceil(filteredAuditLogs.length / PAGE_SIZE);
    const paginatedLogs = filteredAuditLogs.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

    const exportLogs = (format: 'csv' | 'json') => {
        const data = format === 'json'
            ? JSON.stringify(filteredAuditLogs, null, 2)
            : [
                ['id', 'created_at', 'action', 'agent_id', 'details'].join(','),
                ...filteredAuditLogs.map((l: any) =>
                    [l.id, l.created_at, l.action, l.agent_id || '', `"${(l.details ? JSON.stringify(l.details) : '').replace(/"/g, '""')}"`].join(',')
                ),
              ].join('\n');
        const blob = new Blob([data], { type: format === 'json' ? 'application/json' : 'text/csv' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `audit-logs.${format}`;
        a.click();
        URL.revokeObjectURL(url);
    };

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                <div>
                    <h3>{t('enterprise.tabs.audit', '审计日志')}</h3>
                    <p style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                        {t('enterprise.audit.description', '查看系统操作日志、合规追踪和审计记录。')}
                    </p>
                </div>
                <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }}>
                    <button className="btn btn-secondary" style={{ fontSize: '11px', padding: '4px 10px' }} onClick={() => exportLogs('csv')}>CSV</button>
                    <button className="btn btn-secondary" style={{ fontSize: '11px', padding: '4px 10px' }} onClick={() => exportLogs('json')}>JSON</button>
                </div>
            </div>

            {/* Search + filter bar */}
            <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                <input className="form-input" placeholder={t('enterprise.audit.searchPlaceholder', '搜索日志（动作、Agent ID、详情）...')}
                    value={searchQuery} onChange={e => { setSearchQuery(e.target.value); setPage(1); }}
                    style={{ flex: 1, fontSize: '13px', minWidth: '200px' }} />
                <div style={{ display: 'flex', gap: '4px', alignItems: 'center' }}>
                    <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('enterprise.audit.dateFrom', '从')}</span>
                    <input type="date" value={dateFrom} onChange={e => { setDateFrom(e.target.value); setPage(1); }}
                        style={{ fontSize: '11px', padding: '4px 6px', borderRadius: '6px', border: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)', color: 'var(--text-primary)' }} />
                    <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('enterprise.audit.dateTo', '至')}</span>
                    <input type="date" value={dateTo} onChange={e => { setDateTo(e.target.value); setPage(1); }}
                        style={{ fontSize: '11px', padding: '4px 6px', borderRadius: '6px', border: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)', color: 'var(--text-primary)' }} />
                    {(dateFrom || dateTo) && (
                        <button style={{ fontSize: '10px', padding: '2px 6px', borderRadius: '4px', border: 'none', background: 'var(--bg-tertiary)', color: 'var(--text-secondary)', cursor: 'pointer' }}
                            onClick={() => { setDateFrom(''); setDateTo(''); setPage(1); }}>✕</button>
                    )}
                </div>
                <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', whiteSpace: 'nowrap' }}>
                    {t('enterprise.audit.records', { count: filteredAuditLogs.length })}
                </span>
            </div>

            {/* Sub-filter pills */}
            <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '8px' }}>
                {([
                    ['all', t('enterprise.audit.filterAll', '全部')],
                    ['background', t('enterprise.audit.filterBackground', '后台')],
                    ['actions', t('enterprise.audit.filterActions', '操作')],
                ] as const).map(([key, label]) => (
                    <button key={key}
                        onClick={() => { setAuditFilter(key); setPage(1); }}
                        style={{
                            padding: '4px 14px', borderRadius: '12px', fontSize: '12px', fontWeight: 500,
                            border: auditFilter === key ? '1px solid var(--accent-primary)' : '1px solid var(--border-subtle)',
                            background: auditFilter === key ? 'var(--accent-primary)' : 'transparent',
                            color: auditFilter === key ? '#fff' : 'var(--text-secondary)',
                            cursor: 'pointer', transition: 'all 0.15s',
                        }}
                    >{label}</button>
                ))}
            </div>

            {/* Log entries */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
                {paginatedLogs.map((log: any) => {
                    const isBg = BG_ACTIONS.includes(log.action);
                    const details = log.details && typeof log.details === 'object' && Object.keys(log.details).length > 0 ? log.details : null;
                    const isExpanded = expandedLogId === log.id;
                    return (
                        <div key={log.id} style={{ borderBottom: '1px solid var(--border-subtle)', padding: '6px 12px', cursor: details ? 'pointer' : 'default' }}
                            onClick={() => details && setExpandedLogId(isExpanded ? null : log.id)}>
                            <div style={{ display: 'flex', gap: '12px', fontSize: '13px', alignItems: 'center' }}>
                                <span style={{ color: 'var(--text-tertiary)', whiteSpace: 'nowrap', fontFamily: 'var(--font-mono)', fontSize: '11px' }}>
                                    {new Date(log.created_at).toLocaleString()}
                                </span>
                                <span style={{
                                    padding: '1px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 500,
                                    background: isBg ? 'rgba(99,102,241,0.12)' : 'rgba(34,197,94,0.12)',
                                    color: isBg ? 'var(--accent-color)' : 'rgb(34,197,94)',
                                }}>{isBg ? '⚙️' : '👤'}</span>
                                <span style={{ flex: 1, fontWeight: 500 }}>{log.action}</span>
                                <span style={{ color: 'var(--text-tertiary)', fontSize: '11px' }}>{log.agent_id?.slice(0, 8) || '-'}</span>
                                {details && (
                                    <span style={{ fontSize: '10px', color: 'var(--text-tertiary)' }}>{isExpanded ? '▼' : '▶'}</span>
                                )}
                            </div>
                            {/* Collapsed: inline details */}
                            {details && !isExpanded && (
                                <div style={{ marginLeft: '100px', marginTop: '2px', fontSize: '11px', color: 'var(--text-tertiary)', fontFamily: 'var(--font-mono)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                    {Object.entries(details).slice(0, 3).map(([k, v]) => (
                                        <span key={k} style={{ marginRight: '12px' }}>{k}={typeof v === 'string' ? v : JSON.stringify(v)}</span>
                                    ))}
                                    {Object.keys(details).length > 3 && <span>...+{Object.keys(details).length - 3}</span>}
                                </div>
                            )}
                            {/* Expanded: JSON formatted details */}
                            {details && isExpanded && (
                                <div style={{ marginLeft: '100px', marginTop: '4px', padding: '8px 12px', borderRadius: '6px', background: 'var(--bg-tertiary)', fontSize: '11px', fontFamily: 'var(--font-mono)', color: 'var(--text-secondary)', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
                                    {JSON.stringify(details, null, 2)}
                                </div>
                            )}
                        </div>
                    );
                })}
                {paginatedLogs.length === 0 && <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>{t('common.noData')}</div>}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
                <div style={{ display: 'flex', justifyContent: 'center', gap: '8px', marginTop: '16px', alignItems: 'center' }}>
                    <button className="btn btn-secondary" style={{ fontSize: '11px', padding: '4px 10px' }} disabled={page <= 1} onClick={() => setPage(page - 1)}>
                        {t('enterprise.audit.prev', '上一页')}
                    </button>
                    <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                        {page} / {totalPages}
                    </span>
                    <button className="btn btn-secondary" style={{ fontSize: '11px', padding: '4px 10px' }} disabled={page >= totalPages} onClick={() => setPage(page + 1)}>
                        {t('enterprise.audit.next', '下一页')}
                    </button>
                </div>
            )}
        </div>
    );
}
