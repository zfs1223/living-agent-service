import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { knowledgeApi, knowledgeExtendedApi } from '../../services/api';
import { EnterpriseKBBrowser } from './InfoTabComponents';

// ─── Knowledge Tab ─────────────────────────────────
export default function KnowledgeTab() {
    const { t } = useTranslation();
    const qc = useQueryClient();
    const [subTab, setSubTab] = useState<'entries' | 'files' | 'governance'>('entries');
    const [searchQuery, setSearchQuery] = useState('');
    const [searching, setSearching] = useState(false);
    const [searchResults, setSearchResults] = useState<any[]>([]);

    // Highlight matching keywords in search results
    const highlightText = (text: string, query: string) => {
        if (!query.trim() || !text) return text;
        const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const parts = text.split(new RegExp(`(${escaped})`, 'gi'));
        return parts.map((part, i) =>
            part.toLowerCase() === query.toLowerCase()
                ? <mark key={i} style={{ background: 'rgba(250,204,21,0.3)', color: 'inherit', padding: '0 1px', borderRadius: '2px' }}>{part}</mark>
                : part
        );
    };
    const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
    const [editingEntry, setEditingEntry] = useState<any | null>(null);
    const [showCreateForm, setShowCreateForm] = useState(false);
    const [form, setForm] = useState({
        title: '', content: '', category: '', scope: 'L3_SHARED' as string,
        type: 'RULE' as string, importance: 'MEDIUM' as string,
    });
    const [saving, setSaving] = useState(false);
    const [toast, setToast] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

    const showToast = (message: string, type: 'success' | 'error' = 'success') => {
        setToast({ message, type });
        setTimeout(() => setToast(null), 3000);
    };

    // Knowledge entries list
    const { data: entries = [], isLoading: entriesLoading } = useQuery({
        queryKey: ['knowledge-entries', selectedCategory],
        queryFn: () => selectedCategory
            ? knowledgeExtendedApi.getByCategory(selectedCategory)
            : knowledgeApi.list(),
    });

    // Categories
    const { data: categories = [] } = useQuery({
        queryKey: ['knowledge-categories'],
        queryFn: () => knowledgeExtendedApi.getCategories(),
    });

    // Stats
    const { data: stats } = useQuery({
        queryKey: ['knowledge-stats'],
        queryFn: () => knowledgeExtendedApi.getStats(),
    });

    // Favorites
    const { data: favorites = [] } = useQuery({
        queryKey: ['knowledge-favorites'],
        queryFn: () => knowledgeExtendedApi.getFavorites(),
    });

    // Search
    const handleSearch = async () => {
        if (!searchQuery.trim()) return;
        setSearching(true);
        try {
            const results = await knowledgeExtendedApi.search(searchQuery);
            setSearchResults(results);
        } catch (e: any) {
            showToast(e.message || 'Search failed', 'error');
        }
        setSearching(false);
    };

    // Create/Update entry
    const handleSave = async () => {
        setSaving(true);
        try {
            if (editingEntry?.id) {
                await knowledgeApi.update(editingEntry.id, form);
                showToast(t('enterprise.knowledge.updated', '知识条目已更新'));
            } else {
                await knowledgeApi.create(form);
                showToast(t('enterprise.knowledge.created', '知识条目已创建'));
            }
            setEditingEntry(null);
            setShowCreateForm(false);
            setForm({ title: '', content: '', category: '', scope: 'L3_SHARED', type: 'RULE', importance: 'MEDIUM' });
            qc.invalidateQueries({ queryKey: ['knowledge-entries'] });
            qc.invalidateQueries({ queryKey: ['knowledge-stats'] });
        } catch (e: any) {
            showToast(e.message || 'Save failed', 'error');
        }
        setSaving(false);
    };

    // Delete entry
    const handleDelete = async (id: string) => {
        if (!confirm(t('enterprise.knowledge.deleteConfirm', '确定要删除此知识条目吗？'))) return;
        try {
            await knowledgeApi.delete(id);
            qc.invalidateQueries({ queryKey: ['knowledge-entries'] });
            qc.invalidateQueries({ queryKey: ['knowledge-stats'] });
            showToast(t('enterprise.knowledge.deleted', '知识条目已删除'));
        } catch (e: any) {
            showToast(e.message || 'Delete failed', 'error');
        }
    };

    // Toggle favorite
    const toggleFavorite = async (id: string, isFav: boolean) => {
        try {
            if (isFav) {
                await knowledgeExtendedApi.removeFavorite(id);
            } else {
                await knowledgeExtendedApi.addFavorite(id);
            }
            qc.invalidateQueries({ queryKey: ['knowledge-favorites'] });
        } catch { /* ignore */ }
    };

    const favoriteIds = new Set(favorites.map((f: any) => f.id));

    const SCOPE_LABELS: Record<string, { label: string; color: string }> = {
        L1_PRIVATE: { label: t('enterprise.knowledge.scopePrivate', '私有'), color: 'rgba(139,92,246,0.12)' },
        L2_DEPARTMENT: { label: t('enterprise.knowledge.scopeDepartment', '部门'), color: 'rgba(59,130,246,0.12)' },
        L3_SHARED: { label: t('enterprise.knowledge.scopeShared', '共享'), color: 'rgba(34,197,94,0.12)' },
    };
    const TYPE_LABELS: Record<string, string> = {
        RULE: t('enterprise.knowledge.typeRule', '规则'),
        BEST_PRACTICE: t('enterprise.knowledge.typeBestPractice', '最佳实践'),
        EXPERIENCE: t('enterprise.knowledge.typeExperience', '经验'),
        PROCEDURE: t('enterprise.knowledge.typeProcedure', '流程'),
    };
    const STATUS_LABELS: Record<string, { label: string; color: string }> = {
        DRAFT: { label: t('enterprise.knowledge.statusDraft', '草稿'), color: 'var(--bg-tertiary)' },
        ACTIVE: { label: t('enterprise.knowledge.statusActive', '生效'), color: 'rgba(34,197,94,0.12)' },
        DEPRECATED: { label: t('enterprise.knowledge.statusDeprecated', '废弃'), color: 'rgba(245,158,11,0.12)' },
        ARCHIVED: { label: t('enterprise.knowledge.statusArchived', '归档'), color: 'rgba(107,114,128,0.12)' },
    };

    return (
        <div>
            {toast && (
                <div style={{
                    position: 'fixed', top: '20px', right: '20px', zIndex: 20000,
                    padding: '12px 20px', borderRadius: '8px',
                    background: toast.type === 'success' ? 'rgba(34,197,94,0.9)' : 'rgba(239,68,68,0.9)',
                    color: '#fff', fontSize: '14px', fontWeight: 500,
                    boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
                }}>{toast.message}</div>
            )}

            {/* Header with stats */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '16px' }}>
                <div>
                    <h3>{t('enterprise.tabs.knowledge', '知识库')}</h3>
                    <p style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                        {t('enterprise.knowledge.description', '管理企业知识条目、文件和知识治理流程。')}
                    </p>
                </div>
                {stats && (
                    <div style={{ display: 'flex', gap: '12px', flexShrink: 0 }}>
                        <span className="badge badge-info">{t('enterprise.knowledge.totalEntries', '{{count}} 条', { count: stats.total_count ?? stats.total ?? 0 })}</span>
                        <span className="badge badge-success">{t('enterprise.knowledge.activeEntries', '{{count}} 生效', { count: stats.active_count ?? 0 })}</span>
                        <span className="badge" style={{ background: 'rgba(59,130,246,0.12)' }}>{t('enterprise.knowledge.categoryCount', '{{count}} 分类', { count: stats.category_count ?? categories.length ?? 0 })}</span>
                    </div>
                )}
            </div>

            {/* Sub-tabs */}
            <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', borderBottom: '1px solid var(--border-subtle)', paddingBottom: '8px' }}>
                {([['entries', t('enterprise.knowledge.subEntries', '知识条目')], ['files', t('enterprise.knowledge.subFiles', '企业文件')], ['governance', t('enterprise.knowledge.subGovernance', '知识治理')]] as const).map(([key, label]) => (
                    <button key={key} onClick={() => setSubTab(key as any)} style={{
                        padding: '4px 14px', borderRadius: '12px', fontSize: '12px', fontWeight: 500, cursor: 'pointer', border: 'none',
                        background: subTab === key ? 'var(--accent-primary)' : 'var(--bg-tertiary)',
                        color: subTab === key ? '#fff' : 'var(--text-secondary)', transition: 'all 0.15s',
                    }}>{label}</button>
                ))}
            </div>

            {/* ── Knowledge Entries ── */}
            {subTab === 'entries' && (
                <div>
                    {/* Search bar */}
                    <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
                        <input className="form-input" placeholder={t('enterprise.knowledge.searchPlaceholder', '搜索知识...')} value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleSearch()}
                            style={{ flex: 1, fontSize: '13px' }} />
                        <button className="btn btn-secondary" onClick={handleSearch} disabled={searching}>
                            {searching ? t('common.loading') : t('common.search', '搜索')}
                        </button>
                        <button className="btn btn-primary" onClick={() => { setShowCreateForm(true); setEditingEntry(null); setForm({ title: '', content: '', category: '', scope: 'L3_SHARED', type: 'RULE', importance: 'MEDIUM' }); }}>
                            + {t('enterprise.knowledge.create', '新建')}
                        </button>
                    </div>

                    {/* Category filter pills */}
                    {categories.length > 0 && (
                        <div style={{ display: 'flex', gap: '6px', marginBottom: '16px', flexWrap: 'wrap' }}>
                            <button onClick={() => setSelectedCategory(null)} style={{
                                padding: '3px 10px', borderRadius: '12px', fontSize: '11px', cursor: 'pointer', border: 'none',
                                background: !selectedCategory ? 'var(--accent-primary)' : 'var(--bg-tertiary)',
                                color: !selectedCategory ? '#fff' : 'var(--text-secondary)',
                            }}>{t('enterprise.knowledge.allCategories', '全部')}</button>
                            {categories.map((cat: string) => (
                                <button key={cat} onClick={() => setSelectedCategory(cat)} style={{
                                    padding: '3px 10px', borderRadius: '12px', fontSize: '11px', cursor: 'pointer', border: 'none',
                                    background: selectedCategory === cat ? 'var(--accent-primary)' : 'var(--bg-tertiary)',
                                    color: selectedCategory === cat ? '#fff' : 'var(--text-secondary)',
                                }}>{cat}</button>
                            ))}
                        </div>
                    )}

                    {/* Search results or entries list */}
                    {(searchResults.length > 0 ? searchResults : entries).length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {entriesLoading ? t('common.loading') : t('common.noData')}
                        </div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                            {(searchResults.length > 0 ? searchResults : entries).map((entry: any) => {
                                const scopeInfo = SCOPE_LABELS[entry.scope] || SCOPE_LABELS.L3_SHARED;
                                const statusInfo = STATUS_LABELS[entry.status] || STATUS_LABELS.DRAFT;
                                const isFav = favoriteIds.has(entry.id);
                                return (
                                    <div key={entry.id} className="card" style={{ padding: '12px 16px' }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                                                    <span style={{ fontWeight: 500, fontSize: '14px' }}>{searchResults.length > 0 ? highlightText(entry.title, searchQuery) : entry.title}</span>
                                                    <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: scopeInfo.color, color: 'var(--text-primary)' }}>{scopeInfo.label}</span>
                                                    <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: statusInfo.color, color: 'var(--text-primary)' }}>{statusInfo.label}</span>
                                                    {entry.type && TYPE_LABELS[entry.type] && (
                                                        <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: 'var(--bg-tertiary)', color: 'var(--text-secondary)' }}>{TYPE_LABELS[entry.type]}</span>
                                                    )}
                                                </div>
                                                <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '600px' }}>
                                                    {searchResults.length > 0 ? highlightText(entry.content?.slice(0, 200) || '', searchQuery) : <>{entry.content?.slice(0, 120)}{entry.content?.length > 120 ? '...' : ''}</>}
                                                </div>
                                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '4px', display: 'flex', gap: '12px' }}>
                                                    {entry.category && <span>{t('enterprise.knowledge.category', '分类')}: {entry.category}</span>}
                                                    {entry.metadata?.usage_count !== undefined && <span>{t('enterprise.knowledge.usageCount', '使用')}: {entry.metadata.usage_count}</span>}
                                                    {entry.metadata?.confidence !== undefined && <span>{t('enterprise.knowledge.confidence', '置信度')}: {(entry.metadata.confidence * 100).toFixed(0)}%</span>}
                                                    {entry.importance && <span>{t('enterprise.knowledge.importance', '重要性')}: {entry.importance}</span>}
                                                </div>
                                            </div>
                                            <div style={{ display: 'flex', gap: '4px', flexShrink: 0, alignItems: 'center' }}>
                                                {/* P2-2: Knowledge effect feedback */}
                                                <button className="btn btn-ghost" style={{ fontSize: '11px', padding: '2px 6px' }} onClick={() => knowledgeExtendedApi.submitFeedback(entry.id, true).catch(() => {})} title={t('enterprise.knowledge.helpful', '有用')}>👍</button>
                                                <button className="btn btn-ghost" style={{ fontSize: '11px', padding: '2px 6px' }} onClick={() => knowledgeExtendedApi.submitFeedback(entry.id, false).catch(() => {})} title={t('enterprise.knowledge.notHelpful', '无用')}>👎</button>
                                                <button className="btn btn-ghost" style={{ fontSize: '11px', padding: '2px 6px' }} onClick={() => toggleFavorite(entry.id, isFav)}>
                                                    {isFav ? '★' : '☆'}
                                                </button>
                                                <button className="btn btn-ghost" style={{ fontSize: '11px', padding: '2px 6px' }} onClick={() => {
                                                    setEditingEntry(entry);
                                                    setShowCreateForm(true);
                                                    setForm({
                                                        title: entry.title || '', content: entry.content || '',
                                                        category: entry.category || '', scope: entry.scope || 'L3_SHARED',
                                                        type: entry.type || 'RULE', importance: entry.importance || 'MEDIUM',
                                                    });
                                                }}>✏️</button>
                                                {/* Status transition buttons */}
                                                {entry.status === 'DRAFT' && (
                                                    <button className="btn btn-ghost" style={{ fontSize: '10px', padding: '2px 6px', color: 'rgb(34,197,94)' }} onClick={async () => { await knowledgeApi.update(entry.id, { ...entry, status: 'ACTIVE' }); qc.invalidateQueries({ queryKey: ['knowledge-entries'] }); qc.invalidateQueries({ queryKey: ['knowledge-stats'] }); }} title={t('enterprise.knowledge.activate', '激活')}>▶</button>
                                                )}
                                                {entry.status === 'ACTIVE' && (
                                                    <button className="btn btn-ghost" style={{ fontSize: '10px', padding: '2px 6px', color: 'rgb(245,158,11)' }} onClick={async () => { await knowledgeApi.update(entry.id, { ...entry, status: 'DEPRECATED' }); qc.invalidateQueries({ queryKey: ['knowledge-entries'] }); qc.invalidateQueries({ queryKey: ['knowledge-stats'] }); }} title={t('enterprise.knowledge.deprecate', '废弃')}>⚠</button>
                                                )}
                                                {(entry.status === 'ACTIVE' || entry.status === 'DEPRECATED') && (
                                                    <button className="btn btn-ghost" style={{ fontSize: '10px', padding: '2px 6px', color: 'rgb(107,114,128)' }} onClick={async () => { await knowledgeApi.update(entry.id, { ...entry, status: 'ARCHIVED' }); qc.invalidateQueries({ queryKey: ['knowledge-entries'] }); qc.invalidateQueries({ queryKey: ['knowledge-stats'] }); }} title={t('enterprise.knowledge.archive', '归档')}>📦</button>
                                                )}
                                                <button className="btn btn-ghost" style={{ fontSize: '11px', padding: '2px 6px', color: 'var(--error)' }} onClick={() => handleDelete(entry.id)}>🗑️</button>
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}

                    {/* Create/Edit form modal */}
                    {showCreateForm && (
                        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.55)', zIndex: 2000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                            onClick={() => { setShowCreateForm(false); setEditingEntry(null); }}>
                            <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', width: '600px', maxWidth: '95vw', maxHeight: '85vh', overflow: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                                    <h3 style={{ margin: 0 }}>{editingEntry ? t('enterprise.knowledge.editEntry', '编辑知识条目') : t('enterprise.knowledge.createEntry', '新建知识条目')}</h3>
                                    <button onClick={() => { setShowCreateForm(false); setEditingEntry(null); }} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)' }}>✕</button>
                                </div>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                    <div className="form-group">
                                        <label className="form-label">{t('enterprise.knowledge.titleLabel', '标题')}</label>
                                        <input className="form-input" value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} />
                                    </div>
                                    <div className="form-group">
                                        <label className="form-label">{t('enterprise.knowledge.contentLabel', '内容')}</label>
                                        <textarea className="form-input" value={form.content} onChange={e => setForm({ ...form, content: e.target.value })} style={{ minHeight: '150px', resize: 'vertical' }} />
                                    </div>
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                                        <div className="form-group">
                                            <label className="form-label">{t('enterprise.knowledge.categoryLabel', '分类')}</label>
                                            <input className="form-input" value={form.category} onChange={e => setForm({ ...form, category: e.target.value })} placeholder={t('enterprise.knowledge.categoryPlaceholder', 'e.g. product, policy')} />
                                        </div>
                                        <div className="form-group">
                                            <label className="form-label">{t('enterprise.knowledge.scopeLabel', '范围')}</label>
                                            <select className="form-input" value={form.scope} onChange={e => setForm({ ...form, scope: e.target.value })}>
                                                <option value="L1_PRIVATE">{t('enterprise.knowledge.scopePrivate', '私有')}</option>
                                                <option value="L2_DEPARTMENT">{t('enterprise.knowledge.scopeDepartment', '部门')}</option>
                                                <option value="L3_SHARED">{t('enterprise.knowledge.scopeShared', '共享')}</option>
                                            </select>
                                        </div>
                                        <div className="form-group">
                                            <label className="form-label">{t('enterprise.knowledge.typeLabel', '类型')}</label>
                                            <select className="form-input" value={form.type} onChange={e => setForm({ ...form, type: e.target.value })}>
                                                <option value="RULE">{t('enterprise.knowledge.typeRule', '规则')}</option>
                                                <option value="BEST_PRACTICE">{t('enterprise.knowledge.typeBestPractice', '最佳实践')}</option>
                                                <option value="EXPERIENCE">{t('enterprise.knowledge.typeExperience', '经验')}</option>
                                                <option value="PROCEDURE">{t('enterprise.knowledge.typeProcedure', '流程')}</option>
                                            </select>
                                        </div>
                                        <div className="form-group">
                                            <label className="form-label">{t('enterprise.knowledge.importanceLabel', '重要性')}</label>
                                            <select className="form-input" value={form.importance} onChange={e => setForm({ ...form, importance: e.target.value })}>
                                                <option value="LOW">{t('enterprise.knowledge.importanceLow', '低')}</option>
                                                <option value="MEDIUM">{t('enterprise.knowledge.importanceMedium', '中')}</option>
                                                <option value="HIGH">{t('enterprise.knowledge.importanceHigh', '高')}</option>
                                                <option value="CRITICAL">{t('enterprise.knowledge.importanceCritical', '关键')}</option>
                                            </select>
                                        </div>
                                    </div>
                                    <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '8px', borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
                                        <button className="btn btn-secondary" onClick={() => { setShowCreateForm(false); setEditingEntry(null); }}>{t('common.cancel')}</button>
                                        <button className="btn btn-primary" onClick={handleSave} disabled={saving || !form.title.trim()}>
                                            {saving ? t('common.loading') : t('common.save', '保存')}
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            )}

            {/* ── Enterprise Files ── */}
            {subTab === 'files' && (
                <div>
                    <p style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                        {t('enterprise.kb.description', '所有员工均可通过 enterprise_info/ 目录访问的共享文件。')}
                    </p>
                    <div className="card" style={{ padding: '16px' }}>
                        <EnterpriseKBBrowser onRefresh={() => {}} refreshKey={0} />
                    </div>
                </div>
            )}

            {/* ── Knowledge Governance ── */}
            {subTab === 'governance' && (
                <div>
                    <p style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginBottom: '16px' }}>
                        {t('enterprise.knowledge.governanceDesc', '管理知识晋升审核、有效性标记和知识生命周期。')}
                    </p>

                    {/* Stats overview */}
                    {stats && (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '12px', marginBottom: '20px' }}>
                            {[
                                { label: t('enterprise.knowledge.totalEntries', '总条目'), value: stats.total_count ?? stats.total ?? 0, bg: 'rgba(59,130,246,0.08)' },
                                { label: t('enterprise.knowledge.statusActive', '生效'), value: stats.active_count ?? 0, bg: 'rgba(34,197,94,0.08)' },
                                { label: t('enterprise.knowledge.statusDraft', '草稿'), value: stats.draft_count ?? 0, bg: 'rgba(245,158,11,0.08)' },
                                { label: t('enterprise.knowledge.statusDeprecated', '废弃'), value: stats.deprecated_count ?? 0, bg: 'rgba(239,68,68,0.08)' },
                            ].map(s => (
                                <div key={s.label} className="card" style={{ padding: '12px', background: s.bg }}>
                                    <div style={{ fontSize: '20px', fontWeight: 700 }}>{s.value}</div>
                                    <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>{s.label}</div>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Favorites section */}
                    {favorites.length > 0 && (
                        <div style={{ marginBottom: '20px' }}>
                            <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '8px' }}>
                                {t('enterprise.knowledge.favorites', '收藏')} ({favorites.length})
                            </div>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                                {favorites.map((f: any) => (
                                    <div key={f.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 12px', borderRadius: '6px', background: 'var(--bg-tertiary)' }}>
                                        <span style={{ fontSize: '13px' }}>★ {f.title}</span>
                                        <button className="btn btn-ghost" style={{ fontSize: '11px', padding: '2px 6px' }} onClick={() => toggleFavorite(f.id, true)}>✕</button>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Scope distribution */}
                    <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '8px' }}>
                        {t('enterprise.knowledge.scopeDistribution', '知识范围分布')}
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px', marginBottom: '20px' }}>
                        {Object.entries(SCOPE_LABELS).map(([key, info]) => {
                            const count = entries.filter((e: any) => e.scope === key).length;
                            return (
                                <div key={key} className="card" style={{ padding: '12px', background: info.color }}>
                                    <div style={{ fontSize: '20px', fontWeight: 700 }}>{count}</div>
                                    <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>{info.label}</div>
                                </div>
                            );
                        })}
                    </div>

                    {/* Knowledge promotion review */}
                    <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '8px' }}>
                        {t('enterprise.knowledge.promotionReview', '知识晋升审核')}
                    </div>
                    <p style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>
                        {t('enterprise.knowledge.promotionDesc', '审核知识从私有(L1)晋升到部门(L2)或共享(L3)的请求。')}
                    </p>
                    {entries.filter((e: any) => e.status === 'ACTIVE' && e.scope !== 'L3_SHARED').length === 0 ? (
                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', padding: '12px', textAlign: 'center', background: 'var(--bg-tertiary)', borderRadius: '8px' }}>
                            {t('enterprise.knowledge.noPromotionCandidates', '暂无可晋升的知识条目')}
                        </div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginBottom: '20px' }}>
                            {entries.filter((e: any) => e.status === 'ACTIVE' && e.scope !== 'L3_SHARED').map((entry: any) => {
                                const currentScope = SCOPE_LABELS[entry.scope] || SCOPE_LABELS.L1_PRIVATE;
                                const nextScope = entry.scope === 'L1_PRIVATE' ? 'L2_DEPARTMENT' : 'L3_SHARED';
                                const nextScopeInfo = SCOPE_LABELS[nextScope];
                                return (
                                    <div key={entry.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 12px', borderRadius: '8px', background: 'var(--bg-tertiary)' }}>
                                        <div>
                                            <span style={{ fontSize: '13px', fontWeight: 500 }}>{entry.title}</span>
                                            <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: currentScope.color, marginLeft: '6px' }}>{currentScope.label}</span>
                                            <span style={{ fontSize: '10px', color: 'var(--text-tertiary)', margin: '0 4px' }}>→</span>
                                            <span style={{ fontSize: '10px', padding: '1px 6px', borderRadius: '4px', background: nextScopeInfo.color }}>{nextScopeInfo.label}</span>
                                        </div>
                                        <button className="btn btn-primary" style={{ fontSize: '10px', padding: '2px 8px' }} onClick={async () => {
                                            await knowledgeApi.update(entry.id, { ...entry, scope: nextScope });
                                            qc.invalidateQueries({ queryKey: ['knowledge-entries'] });
                                            qc.invalidateQueries({ queryKey: ['knowledge-stats'] });
                                        }}>{t('enterprise.knowledge.promote', '晋升')}</button>
                                    </div>
                                );
                            })}
                        </div>
                    )}

                    {/* Validity marking */}
                    <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '8px' }}>
                        {t('enterprise.knowledge.validityMarking', '有效性标记')}
                    </div>
                    <p style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>
                        {t('enterprise.knowledge.validityDesc', '标记知识条目的验证状态，确保知识库内容质量。')}
                    </p>
                    {entries.filter((e: any) => e.status === 'ACTIVE').length === 0 ? (
                        <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', padding: '12px', textAlign: 'center', background: 'var(--bg-tertiary)', borderRadius: '8px' }}>
                            {t('enterprise.knowledge.noActiveEntries', '暂无生效的知识条目')}
                        </div>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                            {entries.filter((e: any) => e.status === 'ACTIVE').slice(0, 20).map((entry: any) => {
                                const validity = entry.validity || entry.metadata?.validity || 'UNVERIFIED';
                                const VALIDITY_STYLES: Record<string, { label: string; bg: string; color: string }> = {
                                    UNVERIFIED: { label: t('enterprise.knowledge.validityUnverified', '未验证'), bg: 'rgba(107,114,128,0.12)', color: 'rgb(107,114,128)' },
                                    VERIFIED: { label: t('enterprise.knowledge.validityVerified', '已验证'), bg: 'rgba(34,197,94,0.12)', color: 'rgb(34,197,94)' },
                                    OUTDATED: { label: t('enterprise.knowledge.validityOutdated', '已过时'), bg: 'rgba(245,158,11,0.12)', color: 'rgb(245,158,11)' },
                                    INVALID: { label: t('enterprise.knowledge.validityInvalid', '无效'), bg: 'rgba(239,68,68,0.12)', color: 'rgb(239,68,68)' },
                                };
                                const vStyle = VALIDITY_STYLES[validity] || VALIDITY_STYLES.UNVERIFIED;
                                return (
                                    <div key={entry.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 12px', borderRadius: '6px', background: 'var(--bg-tertiary)' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                            <span style={{ fontSize: '12px' }}>{entry.title}</span>
                                            <span style={{ fontSize: '9px', padding: '1px 5px', borderRadius: '4px', background: vStyle.bg, color: vStyle.color }}>{vStyle.label}</span>
                                        </div>
                                        <select value={validity} onChange={async (e) => {
                                            await knowledgeApi.update(entry.id, { ...entry, validity: e.target.value });
                                            qc.invalidateQueries({ queryKey: ['knowledge-entries'] });
                                        }} style={{ fontSize: '10px', padding: '1px 4px', borderRadius: '4px', border: '1px solid var(--border-subtle)', background: 'var(--bg-secondary)', color: 'var(--text-primary)' }}>
                                            <option value="UNVERIFIED">{t('enterprise.knowledge.validityUnverified', '未验证')}</option>
                                            <option value="VERIFIED">{t('enterprise.knowledge.validityVerified', '已验证')}</option>
                                            <option value="OUTDATED">{t('enterprise.knowledge.validityOutdated', '已过时')}</option>
                                            <option value="INVALID">{t('enterprise.knowledge.validityInvalid', '无效')}</option>
                                        </select>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
