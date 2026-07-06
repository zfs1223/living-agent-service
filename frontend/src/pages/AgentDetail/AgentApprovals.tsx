import React from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchAuth } from './utils';

interface AgentApprovalsProps {
    agentId: string;
}

export default function AgentApprovals({ agentId }: AgentApprovalsProps) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();

    const { data: approvals = [], refetch: refetchApprovals } = useQuery({
        queryKey: ['agent-approvals', agentId],
        queryFn: () => fetchAuth<any[]>(`/agents/${encodeURIComponent(agentId!)}/approvals`),
        enabled: !!agentId,
        refetchInterval: 15000,
    });

    const resolveMut = useMutation({
        mutationFn: async ({ approvalId, action }: { approvalId: string; action: string }) => {
            return fetchAuth(`/agents/${encodeURIComponent(agentId!)}/approvals/${approvalId}/resolve`, {
                method: 'POST',
                body: JSON.stringify({ action }),
            });
        },
        onSuccess: () => {
            refetchApprovals();
            queryClient.invalidateQueries({ queryKey: ['notifications-unread'] });
        },
    });

    const pending = (approvals as any[]).filter((a: any) => a.status === 'pending');
    const resolved = (approvals as any[]).filter((a: any) => a.status !== 'pending');

    const statusStyle = (s: string) => ({
        padding: '2px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 600,
        background: s === 'approved' ? 'rgba(0,180,120,0.12)' : s === 'rejected' ? 'rgba(255,80,80,0.12)' : 'rgba(255,180,0,0.12)',
        color: s === 'approved' ? 'var(--success)' : s === 'rejected' ? 'var(--error)' : 'var(--warning)',
    });

    return (
        <div style={{ padding: '20px 24px' }}>
            {/* Pending */}
            {pending.length > 0 && (
                <>
                    <h4 style={{ margin: '0 0 12px', fontSize: '13px', color: 'var(--warning)' }}>
                        {t('agentDetail.pendingCount', { count: pending.length })}
                    </h4>
                    {pending.map((a: any) => (
                        <div key={a.id} style={{
                            padding: '14px 16px', marginBottom: '8px', borderRadius: '8px',
                            background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
                        }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                                <span style={statusStyle(a.status)}>{a.status}</span>
                                <span style={{ fontSize: '13px', fontWeight: 500 }}>{a.action_type}</span>
                                <span style={{ flex: 1 }} />
                                <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                    {a.created_at ? new Date(a.created_at).toLocaleString() : ''}
                                </span>
                            </div>
                            {a.details && (
                                <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '10px', lineHeight: '1.5', maxHeight: '80px', overflow: 'hidden' }}>
                                    {typeof a.details === 'string' ? a.details : JSON.stringify(a.details, null, 2)}
                                </div>
                            )}
                            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                                <button
                                    className="btn btn-primary"
                                    style={{ padding: '6px 16px', fontSize: '12px' }}
                                    onClick={() => resolveMut.mutate({ approvalId: a.id, action: 'approve' })}
                                    disabled={resolveMut.isPending}
                                >
                                    {t('agentDetail.approve')}
                                </button>
                                <button
                                    className="btn btn-danger"
                                    style={{ padding: '6px 16px', fontSize: '12px' }}
                                    onClick={() => resolveMut.mutate({ approvalId: a.id, action: 'reject' })}
                                    disabled={resolveMut.isPending}
                                >
                                    {t('agentDetail.reject')}
                                </button>
                            </div>
                        </div>
                    ))}
                    <div style={{ borderTop: '1px solid var(--border-subtle)', margin: '16px 0' }} />
                </>
            )}
            {/* History */}
            <h4 style={{ margin: '0 0 12px', fontSize: '13px', color: 'var(--text-secondary)' }}>
                {t('agentDetail.approvalHistory')}
            </h4>
            {resolved.length === 0 && pending.length === 0 && (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                    {t('agentDetail.noApprovalRecords')}
                </div>
            )}
            {resolved.map((a: any) => (
                <div key={a.id} style={{
                    padding: '12px 16px', marginBottom: '6px', borderRadius: '8px',
                    background: 'var(--bg-secondary)', border: '1px solid var(--border-subtle)',
                    opacity: 0.7,
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={statusStyle(a.status)}>{a.status}</span>
                        <span style={{ fontSize: '12px' }}>{a.action_type}</span>
                        <span style={{ flex: 1 }} />
                        <span style={{ fontSize: '10px', color: 'var(--text-tertiary)' }}>
                            {a.resolved_at ? new Date(a.resolved_at).toLocaleString() : ''}
                        </span>
                    </div>
                </div>
            ))}
        </div>
    );
}
