import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores';
import { voicePrintApi, voicePrintExtendedApi } from '../services/api';

/* ────── Main Component: 声纹管理（管理所有已注册声纹） ────── */
export default function VoicePrintSettings() {
    const { t, i18n } = useTranslation();
    const queryClient = useQueryClient();
    const isChinese = i18n.language?.startsWith('zh');
    const user = useAuthStore(s => s.user);

    const [msg, setMsg] = useState('');
    const [msgType, setMsgType] = useState<'success' | 'error' | 'info'>('info');
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedVp, setSelectedVp] = useState<any>(null);

    const showMsg = (text: string, type: 'success' | 'error' | 'info' = 'info') => {
        setMsg(text);
        setMsgType(type);
        setTimeout(() => setMsg(''), 5000);
    };

    // 查询所有声纹列表
    const { data: voicePrints = [], isLoading, refetch } = useQuery({
        queryKey: ['all-voiceprints'],
        queryFn: async () => {
            try {
                const list = await voicePrintApi.list();
                return list || [];
            } catch {
                return [];
            }
        },
        refetchInterval: 30000,
    });

    // 查询服务状态
    const { data: serviceStatus } = useQuery({
        queryKey: ['voiceprint-service-status'],
        queryFn: async () => {
            try {
                return await voicePrintExtendedApi.getStatus();
            } catch {
                return { available: false };
            }
        },
        refetchInterval: 60000,
    });

    // 判断是否是管理员（可以管理所有声纹）
    const isAdmin = user?.role === 'org_admin' || user?.identity === ('CHAIRMAN' as any) || user?.access_level === 'FULL';

    // 过滤声纹列表
    const filteredPrints = voicePrints.filter((vp: any) => {
        if (!searchTerm) return true;
        const name = vp.name || vp.speaker_id || '';
        return name.toLowerCase().includes(searchTerm.toLowerCase());
    });

    // 根据权限过滤：非管理员只能看自己的
    const visiblePrints = isAdmin
        ? filteredPrints
        : filteredPrints.filter((vp: any) =>
            vp.speaker_id === user?.id || vp.speaker_id === user?.username || vp.user_id === user?.id
        );

    // 删除声纹
    const deleteMutation = useMutation({
        mutationFn: async (id: string) => {
            return voicePrintApi.delete(id);
        },
        onSuccess: () => {
            showMsg(isChinese ? '声纹已删除' : 'Voice print deleted', 'success');
            setSelectedVp(null);
            queryClient.invalidateQueries({ queryKey: ['all-voiceprints'] });
        },
        onError: (e: any) => {
            showMsg(e.message || (isChinese ? '删除失败' : 'Delete failed'), 'error');
        },
    });

    // 更新声纹状态
    const updateMutation = useMutation({
        mutationFn: async ({ id, data }: { id: string; data: any }) => {
            return voicePrintApi.update(id, data);
        },
        onSuccess: () => {
            showMsg(isChinese ? '声纹状态已更新' : 'Voice print updated', 'success');
            queryClient.invalidateQueries({ queryKey: ['all-voiceprints'] });
        },
        onError: (e: any) => {
            showMsg(e.message || (isChinese ? '更新失败' : 'Update failed'), 'error');
        },
    });

    const handleDelete = (vp: any) => {
        if (!confirm(isChinese ? `确定删除声纹 "${vp.name || vp.speaker_id}"？` : `Delete voice print "${vp.name || vp.speaker_id}"?`)) {
            return;
        }
        deleteMutation.mutate(vp.id || vp.speaker_id);
    };

    const handleToggleActive = (vp: any) => {
        const newStatus = vp.status === 'active' ? 'inactive' : 'active';
        updateMutation.mutate({
            id: vp.id || vp.speaker_id,
            data: { status: newStatus },
        });
    };

    return (
        <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
            {/* Header */}
            <div style={{
                borderRadius: 16,
                padding: 20,
                background: 'linear-gradient(135deg, rgba(59,130,246,0.12), rgba(12,18,28,0.84))',
                border: '1px solid rgba(255,255,255,0.08)',
                marginBottom: 20,
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div>
                        <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
                            🎤 {isChinese ? '声纹管理' : 'Voice Print Management'}
                        </h1>
                        <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: '8px 0 0' }}>
                            {isAdmin
                                ? (isChinese ? '管理所有已注册的声纹记录' : 'Manage all registered voice prints')
                                : (isChinese ? '管理您的个人声纹记录' : 'Manage your personal voice prints')}
                        </p>
                    </div>
                    <div style={{
                        padding: '8px 14px',
                        borderRadius: 8,
                        background: serviceStatus?.available ? 'rgba(16,185,129,0.12)' : 'rgba(239,68,68,0.12)',
                        color: serviceStatus?.available ? 'var(--success)' : 'var(--error)',
                        fontSize: 12,
                        fontWeight: 500,
                    }}>
                        {serviceStatus?.available
                            ? (isChinese ? '✅ 服务正常' : '✅ Service OK')
                            : (isChinese ? '❌ 服务异常' : '❌ Service Down')}
                    </div>
                </div>
            </div>

            {/* 消息提示 */}
            {msg && (
                <div style={{
                    padding: '10px 14px',
                    borderRadius: 8,
                    marginBottom: 16,
                    fontSize: 13,
                    background: msgType === 'success' ? 'rgba(16,185,129,0.12)' : msgType === 'error' ? 'rgba(239,68,68,0.12)' : 'rgba(59,130,246,0.12)',
                    color: msgType === 'success' ? 'var(--success)' : msgType === 'error' ? 'var(--error)' : 'var(--accent)',
                    border: `1px solid ${msgType === 'success' ? 'rgba(16,185,129,0.2)' : msgType === 'error' ? 'rgba(239,68,68,0.2)' : 'rgba(59,130,246,0.2)'}`,
                }}>
                    {msg}
                </div>
            )}

            {/* 统计信息 */}
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(3, 1fr)',
                gap: 12,
                marginBottom: 20,
            }}>
                <div style={{
                    padding: 14,
                    borderRadius: 10,
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-subtle)',
                }}>
                    <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                        {isChinese ? '总声纹数' : 'Total Voice Prints'}
                    </div>
                    <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--text-primary)', marginTop: 4 }}>
                        {visiblePrints.length}
                    </div>
                </div>
                <div style={{
                    padding: 14,
                    borderRadius: 10,
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-subtle)',
                }}>
                    <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                        {isChinese ? '活跃声纹' : 'Active Prints'}
                    </div>
                    <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--success)', marginTop: 4 }}>
                        {visiblePrints.filter((vp: any) => vp.status === 'active').length}
                    </div>
                </div>
                <div style={{
                    padding: 14,
                    borderRadius: 10,
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-subtle)',
                }}>
                    <div style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>
                        {isChinese ? '服务状态' : 'Service Status'}
                    </div>
                    <div style={{ fontSize: 18, fontWeight: 600, color: serviceStatus?.available ? 'var(--success)' : 'var(--error)', marginTop: 6 }}>
                        {serviceStatus?.available ? (isChinese ? '正常' : 'OK') : (isChinese ? '异常' : 'Error')}
                    </div>
                </div>
            </div>

            {/* 搜索栏 */}
            <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
                <input
                    type="text"
                    value={searchTerm}
                    onChange={e => setSearchTerm(e.target.value)}
                    placeholder={isChinese ? '搜索声纹名称...' : 'Search voice prints...'}
                    style={{
                        flex: 1,
                        padding: '10px 14px',
                        fontSize: 13,
                        background: 'var(--bg-secondary)',
                        color: 'var(--text-primary)',
                        border: '1px solid var(--border-default)',
                        borderRadius: 8,
                        outline: 'none',
                    }}
                />
                <button
                    onClick={() => refetch()}
                    className="btn btn-secondary"
                    style={{ fontSize: 13 }}
                >
                    {isChinese ? '刷新' : 'Refresh'}
                </button>
            </div>

            {/* 声纹列表 */}
            <div style={{
                borderRadius: 12,
                border: '1px solid var(--border-subtle)',
                overflow: 'hidden',
            }}>
                <div style={{
                    padding: '12px 16px',
                    background: 'rgba(255,255,255,0.03)',
                    borderBottom: '1px solid var(--border-subtle)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}>
                    <h3 style={{ margin: 0, fontSize: 14, fontWeight: 500, color: 'var(--text-secondary)' }}>
                        {isChinese ? '声纹列表' : 'Voice Print List'}
                    </h3>
                    <span style={{ fontSize: 12, color: 'var(--text-tertiary)' }}>
                        {visiblePrints.length} {isChinese ? '条记录' : 'records'}
                    </span>
                </div>

                {isLoading ? (
                    <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-tertiary)', fontSize: 13 }}>
                        {isChinese ? '加载中...' : 'Loading...'}
                    </div>
                ) : visiblePrints.length === 0 ? (
                    <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-tertiary)', fontSize: 13 }}>
                        {isChinese ? '暂无声纹记录' : 'No voice prints found'}
                    </div>
                ) : (
                    <div style={{ maxHeight: 400, overflowY: 'auto' }}>
                        {visiblePrints.map((vp: any, i: number) => (
                            <div
                                key={vp.id || i}
                                style={{
                                    padding: '12px 16px',
                                    borderBottom: i < visiblePrints.length - 1 ? '1px solid var(--border-subtle)' : 'none',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: 12,
                                    transition: 'background 0.15s',
                                }}
                                onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'var(--bg-hover)'; }}
                                onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'transparent'; }}
                            >
                                <div style={{
                                    width: 40,
                                    height: 40,
                                    borderRadius: 10,
                                    background: 'var(--bg-tertiary)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    fontSize: 18,
                                    color: 'var(--text-tertiary)',
                                }}>
                                    🎤
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                    <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--text-primary)' }}>
                                        {vp.name || vp.speaker_id || `Voice Print ${i + 1}`}
                                    </div>
                                    <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 2 }}>
                                        {vp.description || vp.speaker_id || '-'}
                                        {vp.created_at && (
                                            <span style={{ marginLeft: 8 }}>
                                                {isChinese ? '注册: ' : 'Created: '}{new Date(vp.created_at).toLocaleDateString()}
                                            </span>
                                        )}
                                    </div>
                                </div>
                                <span style={{
                                    padding: '4px 10px',
                                    borderRadius: 6,
                                    fontSize: 11,
                                    fontWeight: 500,
                                    background: vp.status === 'active' ? 'rgba(16,185,129,0.12)' : 'rgba(107,114,128,0.12)',
                                    color: vp.status === 'active' ? 'var(--success)' : 'var(--text-tertiary)',
                                }}>
                                    {vp.status === 'active' ? (isChinese ? '活跃' : 'Active') : (isChinese ? '停用' : 'Inactive')}
                                </span>
                                <button
                                    onClick={() => handleToggleActive(vp)}
                                    className="btn btn-ghost"
                                    style={{ fontSize: 11, padding: '4px 8px' }}
                                    title={vp.status === 'active' ? (isChinese ? '停用' : 'Disable') : (isChinese ? '启用' : 'Enable')}
                                >
                                    {vp.status === 'active' ? '⏸' : '▶️'}
                                </button>
                                <button
                                    onClick={() => handleDelete(vp)}
                                    className="btn btn-ghost"
                                    style={{ fontSize: 11, padding: '4px 8px', color: 'var(--error)' }}
                                    title={isChinese ? '删除' : 'Delete'}
                                >
                                    🗑️
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* 权限说明 */}
            <div style={{
                marginTop: 16,
                padding: 12,
                borderRadius: 8,
                background: 'rgba(59,130,246,0.06)',
                border: '1px solid rgba(59,130,246,0.15)',
                fontSize: 12,
                color: 'var(--text-secondary)',
            }}>
                {isAdmin
                    ? (isChinese ? '💡 您是管理员，可以管理所有声纹记录' : '💡 You are admin and can manage all voice prints')
                    : (isChinese ? '💡 您只能管理自己的声纹记录' : '💡 You can only manage your own voice prints')}
            </div>
        </div>
    );
}