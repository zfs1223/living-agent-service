import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { proactiveApi, proactiveExtendedApi } from '../services/api';

type SubTab = 'digest' | 'habits' | 'notifications' | 'meetingNotes' | 'suggestions';

const SUB_TAB_ITEMS: { key: SubTab; labelKey: string; fallbackLabel: string }[] = [
    { key: 'digest', labelKey: 'proactive.digest', fallbackLabel: '概要' },
    { key: 'habits', labelKey: 'proactive.habits', fallbackLabel: '习惯' },
    { key: 'notifications', labelKey: 'proactive.notifications', fallbackLabel: '通知' },
    { key: 'meetingNotes', labelKey: 'proactive.meetingNotes', fallbackLabel: '会议记录' },
    { key: 'suggestions', labelKey: 'proactive.suggestions', fallbackLabel: '建议' },
];

export default function Proactive() {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const [subTab, setSubTab] = useState<SubTab>('digest');

    // --- Habits state ---
    const [showCreateHabit, setShowCreateHabit] = useState(false);
    const [newHabit, setNewHabit] = useState({ name: '', description: '', frequency: 'daily' });
    const [editingHabit, setEditingHabit] = useState<string | null>(null);
    const [editHabitData, setEditHabitData] = useState({ name: '', description: '', frequency: '' });

    // --- Digest & Analytics ---
    const { data: digest, isLoading: loadingDigest } = useQuery({
        queryKey: ['proactive-digest'],
        queryFn: () => proactiveExtendedApi.getDigest(),
        enabled: subTab === 'digest',
    });

    const { data: analytics } = useQuery({
        queryKey: ['proactive-analytics'],
        queryFn: () => proactiveExtendedApi.getAnalytics(),
        enabled: subTab === 'digest',
    });

    // --- Habits ---
    const { data: habits = [], isLoading: loadingHabits } = useQuery({
        queryKey: ['proactive-habits'],
        queryFn: () => proactiveExtendedApi.listHabits(),
        enabled: subTab === 'habits',
    });

    const createHabitMutation = useMutation({
        mutationFn: (data: any) => proactiveExtendedApi.createHabit(data),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['proactive-habits'] });
            setShowCreateHabit(false);
            setNewHabit({ name: '', description: '', frequency: 'daily' });
        },
    });

    const updateHabitMutation = useMutation({
        mutationFn: ({ id, data }: { id: string; data: any }) => proactiveExtendedApi.updateHabit(id, data),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['proactive-habits'] });
            setEditingHabit(null);
        },
    });

    const deleteHabitMutation = useMutation({
        mutationFn: (id: string) => proactiveExtendedApi.deleteHabit(id),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['proactive-habits'] });
        },
    });

    const checkinHabitMutation = useMutation({
        mutationFn: (habitId: string) => proactiveExtendedApi.checkinHabit(habitId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['proactive-habits'] });
        },
    });

    // --- Notifications ---
    const { data: notifications = [], isLoading: loadingNotifications } = useQuery({
        queryKey: ['proactive-notifications'],
        queryFn: () => proactiveExtendedApi.listNotifications(),
        enabled: subTab === 'notifications',
    });

    const markReadMutation = useMutation({
        mutationFn: (id: string) => proactiveExtendedApi.markNotificationRead(id),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['proactive-notifications'] });
        },
    });

    const markAllReadMutation = useMutation({
        mutationFn: () => proactiveExtendedApi.markAllNotificationsRead(),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['proactive-notifications'] });
        },
    });

    // --- Meeting Notes ---
    const { data: meetingNotes = [], isLoading: loadingMeetingNotes } = useQuery({
        queryKey: ['proactive-meeting-notes'],
        queryFn: () => proactiveExtendedApi.listMeetingNotes(),
        enabled: subTab === 'meetingNotes',
    });

    // --- Suggestions ---
    const { data: suggestions = [], isLoading: loadingSuggestions } = useQuery({
        queryKey: ['proactive-suggestions'],
        queryFn: () => proactiveExtendedApi.getSuggestions(),
        enabled: subTab === 'suggestions',
    });

    const unreadNotifications = (notifications as any[]).filter((n: any) => !n.read && !n.read_at).length;

    const renderKeyValueBlock = (data: any) => {
        if (!data || typeof data !== 'object') return null;
        return Object.entries(data as Record<string, any>).map(([key, val]) => (
            <div key={key} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                <span style={{ color: 'var(--text-tertiary)', fontSize: '13px' }}>{key}</span>
                <span style={{ color: 'var(--text-secondary)', fontSize: '13px', textAlign: 'right', maxWidth: '60%' }}>
                    {typeof val === 'object' ? JSON.stringify(val) : String(val ?? '-')}
                </span>
            </div>
        ));
    };

    return (
        <div style={{ maxWidth: '800px', margin: '0 auto', padding: '24px' }}>
            <h1 style={{ fontSize: '20px', fontWeight: 600, margin: 0, marginBottom: '20px' }}>
                {t('proactive.title', '主动服务')}
            </h1>

            {/* Sub Tab 切换 */}
            <div style={{
                display: 'flex', gap: '2px', marginBottom: '20px',
                background: 'var(--bg-secondary)', borderRadius: '8px', padding: '3px',
                overflowX: 'auto',
            }}>
                {SUB_TAB_ITEMS.map((tab) => {
                    const isActive = subTab === tab.key;
                    const badge = tab.key === 'notifications' && unreadNotifications > 0 ? unreadNotifications : undefined;
                    return (
                        <button
                            key={tab.key}
                            onClick={() => setSubTab(tab.key)}
                            style={{
                                flex: 1, padding: '8px 10px', borderRadius: '6px',
                                border: 'none', cursor: 'pointer',
                                background: isActive ? 'var(--accent)' : 'transparent',
                                color: isActive ? '#fff' : 'var(--text-secondary)',
                                fontSize: '13px', fontWeight: isActive ? 600 : 400,
                                transition: 'all 0.15s',
                                whiteSpace: 'nowrap',
                                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px',
                            }}
                        >
                            {t(tab.labelKey, tab.fallbackLabel)}
                            {badge !== undefined && (
                                <span style={{
                                    fontSize: '11px', padding: '1px 6px', borderRadius: '999px',
                                    background: isActive ? 'rgba(255,255,255,0.25)' : '#f59e0b',
                                    color: isActive ? '#fff' : '#000',
                                }}>
                                    {badge}
                                </span>
                            )}
                        </button>
                    );
                })}
            </div>

            {/* ========== Digest Tab ========== */}
            {subTab === 'digest' && (
                <div>
                    {loadingDigest && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    )}
                    {!loadingDigest && !digest && (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('proactive.noDigest', '暂无概要数据')}
                        </div>
                    )}

                    {/* Digest 数据 */}
                    {digest && (
                        <div style={{
                            padding: '16px', borderRadius: '8px',
                            background: 'var(--bg-secondary)', marginBottom: '16px',
                        }}>
                            <div style={{ fontSize: '14px', fontWeight: 600, marginBottom: '10px', color: 'var(--text-secondary)' }}>
                                {t('proactive.dailyDigest', '每日概要')}
                            </div>
                            {renderKeyValueBlock(digest)}
                        </div>
                    )}

                    {/* Analytics 数据 */}
                    {analytics && (
                        <div style={{
                            padding: '16px', borderRadius: '8px',
                            background: 'var(--bg-secondary)',
                        }}>
                            <div style={{ fontSize: '14px', fontWeight: 600, marginBottom: '10px', color: 'var(--text-secondary)' }}>
                                {t('proactive.analytics', '分析数据')}
                            </div>
                            {renderKeyValueBlock(analytics)}
                        </div>
                    )}
                </div>
            )}

            {/* ========== Habits Tab ========== */}
            {subTab === 'habits' && (
                <div>
                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '12px' }}>
                        <button
                            onClick={() => setShowCreateHabit(!showCreateHabit)}
                            style={{
                                padding: '6px 14px', borderRadius: '6px', border: 'none',
                                background: 'var(--accent)', color: '#fff', fontSize: '13px', cursor: 'pointer',
                            }}
                        >
                            {showCreateHabit ? t('proactive.cancel', '取消') : t('proactive.createHabit', '创建习惯')}
                        </button>
                    </div>

                    {showCreateHabit && (
                        <div style={{
                            padding: '14px 16px', borderRadius: '8px',
                            background: 'var(--bg-secondary)', marginBottom: '12px',
                        }}>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                <input
                                    type="text"
                                    placeholder={t('proactive.habitName', '习惯名称')}
                                    value={newHabit.name}
                                    onChange={(e) => setNewHabit({ ...newHabit, name: e.target.value })}
                                    style={{
                                        padding: '8px 12px', borderRadius: '8px',
                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                        fontSize: '13px', outline: 'none',
                                    }}
                                />
                                <input
                                    type="text"
                                    placeholder={t('proactive.habitDescription', '习惯描述')}
                                    value={newHabit.description}
                                    onChange={(e) => setNewHabit({ ...newHabit, description: e.target.value })}
                                    style={{
                                        padding: '8px 12px', borderRadius: '8px',
                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                        fontSize: '13px', outline: 'none',
                                    }}
                                />
                                <select
                                    value={newHabit.frequency}
                                    onChange={(e) => setNewHabit({ ...newHabit, frequency: e.target.value })}
                                    style={{
                                        padding: '8px 12px', borderRadius: '8px',
                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                        fontSize: '13px', outline: 'none',
                                    }}
                                >
                                    <option value="daily">{t('proactive.daily', '每日')}</option>
                                    <option value="weekly">{t('proactive.weekly', '每周')}</option>
                                    <option value="monthly">{t('proactive.monthly', '每月')}</option>
                                </select>
                                <button
                                    onClick={() => createHabitMutation.mutate(newHabit)}
                                    disabled={!newHabit.name.trim() || createHabitMutation.isPending}
                                    style={{
                                        padding: '8px 16px', borderRadius: '6px', border: 'none',
                                        background: 'var(--accent)', color: '#fff', fontSize: '13px',
                                        cursor: newHabit.name.trim() ? 'pointer' : 'not-allowed',
                                        opacity: newHabit.name.trim() ? 1 : 0.5,
                                        alignSelf: 'flex-start',
                                    }}
                                >
                                    {createHabitMutation.isPending ? '...' : t('proactive.submit', '提交')}
                                </button>
                            </div>
                        </div>
                    )}

                    {loadingHabits && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    )}
                    {!loadingHabits && (habits as any[]).length === 0 && (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('proactive.noHabits', '暂无习惯数据')}
                        </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        {(habits as any[]).map((habit: any) => {
                            const hid = habit.id || habit.habitId || '';
                            const isEditing = editingHabit === hid;
                            return (
                                <div key={hid} style={{
                                    padding: '14px 16px', borderRadius: '8px',
                                    background: 'var(--bg-secondary)',
                                }}>
                                    {isEditing ? (
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                            <input
                                                type="text"
                                                value={editHabitData.name}
                                                onChange={(e) => setEditHabitData({ ...editHabitData, name: e.target.value })}
                                                style={{
                                                    padding: '8px 12px', borderRadius: '8px',
                                                    border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                                    background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                                    fontSize: '13px', outline: 'none',
                                                }}
                                            />
                                            <input
                                                type="text"
                                                value={editHabitData.description}
                                                onChange={(e) => setEditHabitData({ ...editHabitData, description: e.target.value })}
                                                style={{
                                                    padding: '8px 12px', borderRadius: '8px',
                                                    border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                                    background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                                    fontSize: '13px', outline: 'none',
                                                }}
                                            />
                                            <select
                                                value={editHabitData.frequency}
                                                onChange={(e) => setEditHabitData({ ...editHabitData, frequency: e.target.value })}
                                                style={{
                                                    padding: '8px 12px', borderRadius: '8px',
                                                    border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                                    background: 'var(--bg-secondary)', color: 'var(--text-primary, #fff)',
                                                    fontSize: '13px', outline: 'none',
                                                }}
                                            >
                                                <option value="daily">{t('proactive.daily', '每日')}</option>
                                                <option value="weekly">{t('proactive.weekly', '每周')}</option>
                                                <option value="monthly">{t('proactive.monthly', '每月')}</option>
                                            </select>
                                            <div style={{ display: 'flex', gap: '8px' }}>
                                                <button
                                                    onClick={() => updateHabitMutation.mutate({ id: hid, data: editHabitData })}
                                                    disabled={updateHabitMutation.isPending}
                                                    style={{
                                                        padding: '6px 14px', borderRadius: '6px', border: 'none',
                                                        background: 'var(--accent)', color: '#fff', fontSize: '13px', cursor: 'pointer',
                                                    }}
                                                >
                                                    {updateHabitMutation.isPending ? '...' : t('proactive.save', '保存')}
                                                </button>
                                                <button
                                                    onClick={() => setEditingHabit(null)}
                                                    style={{
                                                        padding: '6px 14px', borderRadius: '6px',
                                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                                        background: 'transparent', color: 'var(--text-secondary)',
                                                        fontSize: '13px', cursor: 'pointer',
                                                    }}
                                                >
                                                    {t('proactive.cancel', '取消')}
                                                </button>
                                            </div>
                                        </div>
                                    ) : (
                                        <>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                                                <span style={{ fontWeight: 600, fontSize: '14px' }}>
                                                    {habit.name || hid}
                                                </span>
                                                <span style={{
                                                    fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                                    background: 'rgba(255,255,255,0.06)', color: 'var(--text-tertiary)',
                                                }}>
                                                    {habit.frequency || '-'}
                                                </span>
                                                {habit.streak !== undefined && (
                                                    <span style={{
                                                        fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                                        background: 'rgba(34,197,94,0.12)', color: '#22c55e',
                                                    }}>
                                                        {habit.streak} {t('proactive.dayStreak', '天连续')}
                                                    </span>
                                                )}
                                            </div>
                                            {habit.description && (
                                                <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5, marginBottom: '8px' }}>
                                                    {habit.description}
                                                </div>
                                            )}
                                            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                                                <button
                                                    onClick={() => checkinHabitMutation.mutate(hid)}
                                                    disabled={checkinHabitMutation.isPending}
                                                    style={{
                                                        padding: '4px 12px', borderRadius: '6px', border: 'none',
                                                        background: '#22c55e', color: '#fff', fontSize: '12px', cursor: 'pointer',
                                                    }}
                                                >
                                                    {t('proactive.checkin', '签到')}
                                                </button>
                                                <button
                                                    onClick={() => {
                                                        setEditingHabit(hid);
                                                        setEditHabitData({
                                                            name: habit.name || '',
                                                            description: habit.description || '',
                                                            frequency: habit.frequency || 'daily',
                                                        });
                                                    }}
                                                    style={{
                                                        padding: '4px 12px', borderRadius: '6px',
                                                        border: '1px solid var(--border-subtle, rgba(255,255,255,0.1))',
                                                        background: 'transparent', color: 'var(--accent)',
                                                        fontSize: '12px', cursor: 'pointer',
                                                    }}
                                                >
                                                    {t('proactive.edit', '编辑')}
                                                </button>
                                                <button
                                                    onClick={() => { if (confirm(t('proactive.confirmDelete', '确认删除该习惯？'))) deleteHabitMutation.mutate(hid); }}
                                                    disabled={deleteHabitMutation.isPending}
                                                    style={{
                                                        padding: '4px 10px', borderRadius: '6px',
                                                        border: '1px solid rgba(239,68,68,0.3)',
                                                        background: 'rgba(239,68,68,0.1)', color: '#ef4444',
                                                        fontSize: '12px', cursor: 'pointer',
                                                    }}
                                                >
                                                    {t('proactive.delete', '删除')}
                                                </button>
                                            </div>
                                        </>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* ========== Notifications Tab ========== */}
            {subTab === 'notifications' && (
                <div>
                    {unreadNotifications > 0 && (
                        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '12px' }}>
                            <button
                                onClick={() => markAllReadMutation.mutate()}
                                style={{
                                    fontSize: '13px', color: 'var(--accent)', background: 'none',
                                    border: 'none', cursor: 'pointer',
                                }}
                            >
                                {t('proactive.markAllRead', '全部标记已读')} ({unreadNotifications})
                            </button>
                        </div>
                    )}
                    {loadingNotifications && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    )}
                    {!loadingNotifications && (notifications as any[]).length === 0 && (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('proactive.noNotifications', '暂无通知')}
                        </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        {(notifications as any[]).map((notif: any) => {
                            const nid = notif.id || '';
                            const isUnread = !notif.read && !notif.read_at;
                            return (
                                <div
                                    key={nid}
                                    onClick={() => isUnread && markReadMutation.mutate(nid)}
                                    style={{
                                        padding: '14px 16px', borderRadius: '8px',
                                        background: isUnread ? 'rgba(224,238,238,0.06)' : 'var(--bg-secondary)',
                                        cursor: isUnread ? 'pointer' : 'default',
                                        borderLeft: isUnread ? '3px solid var(--accent)' : '3px solid transparent',
                                        transition: 'background 0.15s',
                                    }}
                                >
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                                        <span style={{ fontWeight: 600, fontSize: '14px' }}>
                                            {notif.title || notif.type || nid}
                                        </span>
                                        {isUnread && (
                                            <span style={{
                                                width: '8px', height: '8px', borderRadius: '50%',
                                                background: 'var(--accent)', flexShrink: 0,
                                            }} />
                                        )}
                                        {notif.created_at && (
                                            <span style={{ marginLeft: 'auto', fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                                {new Date(notif.created_at).toLocaleString()}
                                            </span>
                                        )}
                                    </div>
                                    {notif.content && (
                                        <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                                            {notif.content}
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* ========== Meeting Notes Tab ========== */}
            {subTab === 'meetingNotes' && (
                <div>
                    {loadingMeetingNotes && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    )}
                    {!loadingMeetingNotes && (meetingNotes as any[]).length === 0 && (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('proactive.noMeetingNotes', '暂无会议记录')}
                        </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        {(meetingNotes as any[]).map((note: any) => {
                            const mid = note.id || '';
                            return (
                                <div key={mid} style={{
                                    padding: '14px 16px', borderRadius: '8px',
                                    background: 'var(--bg-secondary)',
                                }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                                        <span style={{ fontWeight: 600, fontSize: '14px' }}>
                                            {note.title || note.topic || mid}
                                        </span>
                                        {note.date && (
                                            <span style={{ marginLeft: 'auto', fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                                {note.date}
                                            </span>
                                        )}
                                        {note.created_at && !note.date && (
                                            <span style={{ marginLeft: 'auto', fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                                {new Date(note.created_at).toLocaleString()}
                                            </span>
                                        )}
                                    </div>
                                    {note.summary && (
                                        <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                                            {note.summary}
                                        </div>
                                    )}
                                    {note.participants && (
                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '6px' }}>
                                            {t('proactive.participants', '参与人')}: {Array.isArray(note.participants) ? note.participants.join(', ') : note.participants}
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* ========== Suggestions Tab ========== */}
            {subTab === 'suggestions' && (
                <div>
                    {loadingSuggestions && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.loading', '加载中...')}
                        </div>
                    )}
                    {!loadingSuggestions && (suggestions as any[]).length === 0 && (
                        <div style={{
                            textAlign: 'center', padding: '60px 20px', color: 'var(--text-tertiary)',
                            background: 'var(--bg-secondary)', borderRadius: '12px',
                        }}>
                            {t('proactive.noSuggestions', '暂无建议')}
                        </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                        {(suggestions as any[]).map((suggestion: any, index: number) => {
                            const sid = suggestion.id || `suggestion-${index}`;
                            const priorityColor = suggestion.priority === 'high'
                                ? '#ef4444'
                                : suggestion.priority === 'medium'
                                    ? '#f59e0b'
                                    : '#22c55e';
                            return (
                                <div key={sid} style={{
                                    padding: '14px 16px', borderRadius: '8px',
                                    background: 'var(--bg-secondary)',
                                }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
                                        <span style={{ fontWeight: 600, fontSize: '14px' }}>
                                            {suggestion.title || suggestion.type || sid}
                                        </span>
                                        {suggestion.priority && (
                                            <span style={{
                                                fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                                color: priorityColor, background: `${priorityColor}18`,
                                            }}>
                                                {suggestion.priority}
                                            </span>
                                        )}
                                        {suggestion.category && (
                                            <span style={{
                                                fontSize: '11px', padding: '2px 8px', borderRadius: '999px',
                                                background: 'rgba(255,255,255,0.06)', color: 'var(--text-tertiary)',
                                            }}>
                                                {suggestion.category}
                                            </span>
                                        )}
                                    </div>
                                    {suggestion.description && (
                                        <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                                            {suggestion.description}
                                        </div>
                                    )}
                                    {suggestion.reason && (
                                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '6px' }}>
                                            {suggestion.reason}
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
}
