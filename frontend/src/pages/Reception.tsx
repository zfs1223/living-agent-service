import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { receptionApi, receptionExtendedApi } from '../services/api';

/* ────── Inline SVG Icons (monochrome) ────── */

const Icons = {
    visitor: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="8" cy="5" r="2.5" />
            <path d="M3 14v-1a3.5 3.5 0 017 0v1" />
        </svg>
    ),
    checkIn: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <rect x="2" y="2" width="12" height="12" rx="2" />
            <path d="M5.5 8l2 2 3.5-3.5" />
        </svg>
    ),
    chat: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M2 4a2 2 0 012-2h8a2 2 0 012 2v5a2 2 0 01-2 2H8l-3 3V11H4a2 2 0 01-2-2V4z" />
        </svg>
    ),
    send: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M14.5 1.5l-6 13-2.5-5.5L.5 6.5l14-5z" />
            <path d="M14.5 1.5L6 9" />
        </svg>
    ),
    status: (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="8" cy="8" r="6" />
            <path d="M8 5v3l2 1.5" />
        </svg>
    ),
    bot: (
        <svg width="14" height="14" viewBox="0 0 18 18" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round">
            <rect x="3" y="5" width="12" height="10" rx="2" />
            <circle cx="7" cy="10" r="1" fill="currentColor" stroke="none" />
            <circle cx="11" cy="10" r="1" fill="currentColor" stroke="none" />
            <path d="M9 2v3M6 2h6" />
        </svg>
    ),
};

/* ────── Helpers ────── */

const timeAgo = (dateStr: string | undefined, t: any) => {
    if (!dateStr) return '-';
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return t('reception.justNow', '刚刚');
    if (mins < 60) return t('reception.minutesAgo', { count: mins, defaultValue: `${mins}分钟前` });
    const hours = Math.floor(mins / 60);
    if (hours < 24) return t('reception.hoursAgo', { count: hours, defaultValue: `${hours}小时前` });
    return t('reception.daysAgo', { count: Math.floor(hours / 24), defaultValue: `${Math.floor(hours / 24)}天前` });
};

/* ────── Main Component ────── */

export default function Reception() {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const chatEndRef = useRef<HTMLDivElement>(null);

    // ─── State ───
    const [checkInForm, setCheckInForm] = useState({ name: '', purpose: '', contact: '' });
    const [chatInput, setChatInput] = useState('');
    const [chatHistory, setChatHistory] = useState<{ role: 'user' | 'assistant'; content: string }[]>([]);

    // ─── Queries ───
    const { data: status, isLoading: statusLoading } = useQuery({
        queryKey: ['reception-status'],
        queryFn: () => receptionExtendedApi.getStatus(),
        refetchInterval: 30000,
    });

    const { data: visitors = [], isLoading: visitorsLoading } = useQuery({
        queryKey: ['reception-visitors'],
        queryFn: () => receptionApi.getVisitors(),
        refetchInterval: 15000,
    });

    // ─── Mutations ───
    const checkInMutation = useMutation({
        mutationFn: (data: any) => receptionApi.checkIn(data),
        onSuccess: () => {
            setCheckInForm({ name: '', purpose: '', contact: '' });
            queryClient.invalidateQueries({ queryKey: ['reception-visitors'] });
            queryClient.invalidateQueries({ queryKey: ['reception-status'] });
        },
    });

    const chatMutation = useMutation({
        mutationFn: (message: string) => receptionExtendedApi.chat(message),
        onSuccess: (response: any) => {
            setChatHistory(prev => [...prev, { role: 'assistant', content: response?.reply || response?.message || response?.content || JSON.stringify(response) }]);
        },
        onError: () => {
            setChatHistory(prev => [...prev, { role: 'assistant', content: t('reception.chatError', '抱歉，回复出现错误，请稍后再试。') }]);
        },
    });

    // ─── Effects ───
    useEffect(() => {
        chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [chatHistory]);

    // ─── Handlers ───
    const handleCheckIn = () => {
        if (!checkInForm.name.trim()) return;
        checkInMutation.mutate({
            name: checkInForm.name,
            purpose: checkInForm.purpose,
            contact: checkInForm.contact,
        });
    };

    const handleSendChat = () => {
        if (!chatInput.trim() || chatMutation.isPending) return;
        const msg = chatInput.trim();
        setChatHistory(prev => [...prev, { role: 'user', content: msg }]);
        setChatInput('');
        chatMutation.mutate(msg);
    };

    const handleChatKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSendChat();
        }
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            {/* ─── Header / Status Banner ─── */}
            <div style={{
                borderRadius: '24px',
                padding: '22px',
                background: 'linear-gradient(135deg, rgba(16,185,129,0.12), rgba(12,18,28,0.84) 48%, rgba(5,6,10,0.96))',
                border: '1px solid rgba(255,255,255,0.08)',
                boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '18px', alignItems: 'flex-start' }}>
                    <div style={{ maxWidth: '760px' }}>
                        <div style={{
                            display: 'inline-flex', alignItems: 'center', gap: '8px',
                            padding: '6px 10px', borderRadius: '999px',
                            background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)',
                            fontSize: '12px', marginBottom: '14px',
                        }}>
                            <span style={{
                                width: '8px', height: '8px', borderRadius: '50%',
                                background: status?.online ? 'var(--success)' : 'var(--text-tertiary)',
                                boxShadow: status?.online ? '0 0 18px rgba(16,185,129,0.85)' : 'none',
                            }} />
                            {t('reception.badge', '智能前台')}
                        </div>
                        <h1 style={{ fontSize: '28px', fontWeight: 700, margin: 0, letterSpacing: '-0.04em', color: 'var(--text-primary)' }}>
                            {t('reception.title', '接待前台')}
                        </h1>
                        <p style={{ fontSize: '13px', color: 'var(--text-secondary)', margin: '10px 0 0', lineHeight: 1.75, maxWidth: '68ch' }}>
                            {t('reception.subtitle', '访客签到、前台咨询与智能接待服务')}
                        </p>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '10px', minWidth: '260px' }}>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('reception.todayVisitors', '今日访客')}</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px' }}>{visitors.length}</div>
                        </div>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('reception.serviceStatus', '服务状态')}</div>
                            <div style={{ fontSize: '15px', fontWeight: 600, marginTop: '6px', color: status?.online ? 'var(--success)' : 'var(--text-tertiary)' }}>
                                {statusLoading ? '...' : (status?.online ? t('reception.online', '在线') : t('reception.offline', '离线'))}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* ─── Two-Column Layout ─── */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '18px', alignItems: 'flex-start' }}>
                {/* ─── Left: Visitor List + Check-In Form ─── */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
                    {/* Visitor List */}
                    <div style={{
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-lg)', overflow: 'hidden',
                    }}>
                        <div style={{
                            padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)',
                            display: 'flex', alignItems: 'center', gap: '6px',
                            background: 'rgba(255,255,255,0.03)',
                        }}>
                            <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.visitor}</span>
                            <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                                {t('reception.visitorList', '访客列表')}
                            </h3>
                            <span style={{ marginLeft: 'auto', fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                {visitors.length} {t('reception.people', '人')}
                            </span>
                        </div>
                        <div style={{ maxHeight: '320px', overflowY: 'auto' }}>
                            {visitorsLoading ? (
                                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                                    {t('common.loading')}
                                </div>
                            ) : visitors.length === 0 ? (
                                <div style={{
                                    textAlign: 'center', padding: '40px 20px',
                                    color: 'var(--text-tertiary)', fontSize: '13px',
                                }}>
                                    {t('reception.noVisitors', '暂无访客记录')}
                                </div>
                            ) : (
                                visitors.map((v: any, i: number) => (
                                    <div key={v.id || i} style={{
                                        display: 'flex', alignItems: 'center', gap: '10px',
                                        padding: '10px 16px',
                                        borderBottom: i < visitors.length - 1 ? '1px solid var(--border-subtle)' : 'none',
                                        transition: 'background 0.15s',
                                    }}
                                        onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'var(--bg-hover)'; }}
                                        onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'transparent'; }}
                                    >
                                        <div style={{
                                            width: '32px', height: '32px', borderRadius: 'var(--radius-md)',
                                            background: 'var(--bg-tertiary)', border: '1px solid var(--border-subtle)',
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            color: 'var(--text-tertiary)', flexShrink: 0,
                                            fontSize: '13px', fontWeight: 600,
                                        }}>
                                            {(v.name || '?')[0].toUpperCase()}
                                        </div>
                                        <div style={{ flex: 1, minWidth: 0 }}>
                                            <div style={{ fontSize: '13px', fontWeight: 500, color: 'var(--text-primary)' }}>
                                                {v.name}
                                            </div>
                                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                {v.purpose || '-'}
                                            </div>
                                        </div>
                                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', fontFamily: 'var(--font-mono)', flexShrink: 0 }}>
                                            {timeAgo(v.check_in_time || v.created_at, t)}
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>

                    {/* Check-In Form */}
                    <div style={{
                        border: '1px solid var(--border-subtle)',
                        borderRadius: 'var(--radius-lg)', overflow: 'hidden',
                    }}>
                        <div style={{
                            padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)',
                            display: 'flex', alignItems: 'center', gap: '6px',
                            background: 'rgba(255,255,255,0.03)',
                        }}>
                            <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.checkIn}</span>
                            <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                                {t('reception.checkIn', '访客签到')}
                            </h3>
                        </div>
                        <div style={{ padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                            <div>
                                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>
                                    {t('reception.name', '姓名')} *
                                </label>
                                <input
                                    type="text"
                                    value={checkInForm.name}
                                    onChange={e => setCheckInForm(prev => ({ ...prev, name: e.target.value }))}
                                    placeholder={t('reception.namePlaceholder', '请输入您的姓名')}
                                    style={{
                                        width: '100%', boxSizing: 'border-box',
                                        padding: '8px 12px', fontSize: '13px',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary)',
                                        border: '1px solid var(--border-default)', borderRadius: 'var(--radius-md)',
                                        outline: 'none', transition: 'border-color 0.15s',
                                    }}
                                    onFocus={e => { e.currentTarget.style.borderColor = 'var(--accent-primary)'; e.currentTarget.style.boxShadow = '0 0 0 2px var(--accent-subtle)'; }}
                                    onBlur={e => { e.currentTarget.style.borderColor = 'var(--border-default)'; e.currentTarget.style.boxShadow = 'none'; }}
                                />
                            </div>
                            <div>
                                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>
                                    {t('reception.purpose', '来访目的')}
                                </label>
                                <input
                                    type="text"
                                    value={checkInForm.purpose}
                                    onChange={e => setCheckInForm(prev => ({ ...prev, purpose: e.target.value }))}
                                    placeholder={t('reception.purposePlaceholder', '例如：商务洽谈、面试、参观等')}
                                    style={{
                                        width: '100%', boxSizing: 'border-box',
                                        padding: '8px 12px', fontSize: '13px',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary)',
                                        border: '1px solid var(--border-default)', borderRadius: 'var(--radius-md)',
                                        outline: 'none', transition: 'border-color 0.15s',
                                    }}
                                    onFocus={e => { e.currentTarget.style.borderColor = 'var(--accent-primary)'; e.currentTarget.style.boxShadow = '0 0 0 2px var(--accent-subtle)'; }}
                                    onBlur={e => { e.currentTarget.style.borderColor = 'var(--border-default)'; e.currentTarget.style.boxShadow = 'none'; }}
                                />
                            </div>
                            <div>
                                <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>
                                    {t('reception.contact', '联系方式')}
                                </label>
                                <input
                                    type="text"
                                    value={checkInForm.contact}
                                    onChange={e => setCheckInForm(prev => ({ ...prev, contact: e.target.value }))}
                                    placeholder={t('reception.contactPlaceholder', '手机号或邮箱')}
                                    style={{
                                        width: '100%', boxSizing: 'border-box',
                                        padding: '8px 12px', fontSize: '13px',
                                        background: 'var(--bg-secondary)', color: 'var(--text-primary)',
                                        border: '1px solid var(--border-default)', borderRadius: 'var(--radius-md)',
                                        outline: 'none', transition: 'border-color 0.15s',
                                    }}
                                    onFocus={e => { e.currentTarget.style.borderColor = 'var(--accent-primary)'; e.currentTarget.style.boxShadow = '0 0 0 2px var(--accent-subtle)'; }}
                                    onBlur={e => { e.currentTarget.style.borderColor = 'var(--border-default)'; e.currentTarget.style.boxShadow = 'none'; }}
                                />
                            </div>
                            <button
                                className={`btn ${checkInForm.name.trim() ? 'btn-primary' : 'btn-secondary'}`}
                                onClick={handleCheckIn}
                                disabled={!checkInForm.name.trim() || checkInMutation.isPending}
                                style={{ marginTop: '4px' }}
                            >
                                {checkInMutation.isPending ? t('reception.checkingIn', '签到中...') : t('reception.checkInButton', '签到')}
                            </button>
                            {checkInMutation.isError && (
                                <div style={{ fontSize: '12px', color: 'var(--error)' }}>
                                    {t('reception.checkInError', '签到失败，请重试')}
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* ─── Right: Chat Interface ─── */}
                <div style={{
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-lg)', overflow: 'hidden',
                    display: 'flex', flexDirection: 'column', minHeight: '500px',
                }}>
                    <div style={{
                        padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)',
                        display: 'flex', alignItems: 'center', gap: '6px',
                        background: 'rgba(255,255,255,0.03)',
                    }}>
                        <span style={{ display: 'flex', opacity: 0.6 }}>{Icons.chat}</span>
                        <h3 style={{ margin: 0, fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                            {t('reception.chatTitle', '前台咨询')}
                        </h3>
                        <span style={{
                            display: 'flex', alignItems: 'center', gap: '4px',
                            marginLeft: 'auto', fontSize: '11px',
                            color: status?.online ? 'var(--success)' : 'var(--text-tertiary)',
                        }}>
                            <span style={{
                                width: '6px', height: '6px', borderRadius: '50%',
                                background: status?.online ? 'var(--success)' : 'var(--text-tertiary)',
                                display: 'inline-block',
                            }} />
                            {status?.online ? t('reception.online', '在线') : t('reception.offline', '离线')}
                        </span>
                    </div>

                    {/* Chat Messages */}
                    <div style={{
                        flex: 1, padding: '14px 16px',
                        overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '10px',
                    }}>
                        {chatHistory.length === 0 && (
                            <div style={{
                                textAlign: 'center', padding: '40px 20px',
                                color: 'var(--text-tertiary)', fontSize: '13px',
                                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px',
                            }}>
                                <span style={{ display: 'flex', opacity: 0.4 }}>{Icons.chat}</span>
                                {t('reception.chatPlaceholder', '有什么可以帮助您的？请输入消息与前台对话。')}
                            </div>
                        )}
                        {chatHistory.map((msg, i) => (
                            <div key={i} style={{
                                display: 'flex', gap: '8px',
                                justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
                            }}>
                                {msg.role === 'assistant' && (
                                    <div style={{
                                        width: '28px', height: '28px', borderRadius: 'var(--radius-md)',
                                        background: 'var(--bg-tertiary)', border: '1px solid var(--border-subtle)',
                                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                                        color: 'var(--text-tertiary)', flexShrink: 0,
                                    }}>
                                        {Icons.bot}
                                    </div>
                                )}
                                <div style={{
                                    maxWidth: '75%', padding: '8px 12px',
                                    borderRadius: msg.role === 'user' ? 'var(--radius-md) 2px var(--radius-md) var(--radius-md)' : '2px var(--radius-md) var(--radius-md) var(--radius-md)',
                                    background: msg.role === 'user' ? 'var(--accent-primary)' : 'var(--bg-secondary)',
                                    color: msg.role === 'user' ? '#fff' : 'var(--text-primary)',
                                    fontSize: '13px', lineHeight: 1.6,
                                    whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                                }}>
                                    {msg.content}
                                </div>
                            </div>
                        ))}
                        {chatMutation.isPending && (
                            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                                <div style={{
                                    width: '28px', height: '28px', borderRadius: 'var(--radius-md)',
                                    background: 'var(--bg-tertiary)', border: '1px solid var(--border-subtle)',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    color: 'var(--text-tertiary)', flexShrink: 0,
                                }}>
                                    {Icons.bot}
                                </div>
                                <div style={{
                                    padding: '8px 12px', borderRadius: '2px var(--radius-md) var(--radius-md) var(--radius-md)',
                                    background: 'var(--bg-secondary)', fontSize: '13px',
                                    color: 'var(--text-tertiary)',
                                }}>
                                    {t('reception.typing', '正在输入...')}
                                </div>
                            </div>
                        )}
                        <div ref={chatEndRef} />
                    </div>

                    {/* Chat Input */}
                    <div style={{
                        padding: '12px 16px', borderTop: '1px solid var(--border-subtle)',
                        display: 'flex', gap: '8px', alignItems: 'flex-end',
                    }}>
                        <textarea
                            value={chatInput}
                            onChange={e => setChatInput(e.target.value)}
                            onKeyDown={handleChatKeyDown}
                            placeholder={t('reception.chatInputPlaceholder', '输入消息...')}
                            rows={1}
                            style={{
                                flex: 1, boxSizing: 'border-box',
                                padding: '8px 12px', fontSize: '13px', lineHeight: 1.5,
                                background: 'var(--bg-secondary)', color: 'var(--text-primary)',
                                border: '1px solid var(--border-default)', borderRadius: 'var(--radius-md)',
                                outline: 'none', resize: 'none', minHeight: '36px', maxHeight: '100px',
                                fontFamily: 'var(--font-family)',
                                transition: 'border-color 0.15s',
                            }}
                            onFocus={e => { e.currentTarget.style.borderColor = 'var(--accent-primary)'; e.currentTarget.style.boxShadow = '0 0 0 2px var(--accent-subtle)'; }}
                            onBlur={e => { e.currentTarget.style.borderColor = 'var(--border-default)'; e.currentTarget.style.boxShadow = 'none'; }}
                        />
                        <button
                            className={`btn ${chatInput.trim() ? 'btn-primary' : 'btn-secondary'}`}
                            onClick={handleSendChat}
                            disabled={!chatInput.trim() || chatMutation.isPending}
                            style={{ height: '36px', padding: '0 14px', display: 'flex', alignItems: 'center', gap: '4px' }}
                        >
                            <span style={{ display: 'flex' }}>{Icons.send}</span>
                            {t('reception.send', '发送')}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
