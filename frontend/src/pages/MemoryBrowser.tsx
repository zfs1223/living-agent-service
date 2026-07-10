import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { memoryApi } from '../services/api';

export default function MemoryBrowser() {
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data: stats } = useQuery({
    queryKey: ['memory-stats'],
    queryFn: () => memoryApi.getMemoryStats(),
  });

  const { data: memories = [], isLoading, error } = useQuery({
    queryKey: ['memories', filterType],
    queryFn: () => memoryApi.getMemories({ type: filterType || undefined, limit: 100 }),
  });

  const { data: searchResults } = useQuery({
    queryKey: ['memory-search', searchQuery],
    queryFn: () => memoryApi.searchMemories(searchQuery),
    enabled: searchQuery.length >= 2,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => memoryApi.deleteMemory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['memories'] });
      queryClient.invalidateQueries({ queryKey: ['memory-stats'] });
      setSelectedId(null);
    },
  });

  const displayList = searchQuery.length >= 2 ? (searchResults ?? []) : memories;

  if (isLoading) {
    return <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>加载记忆数据...</div>;
  }

  if (error) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <p style={{ color: '#e53e3e' }}>加载失败：{(error as Error).message}</p>
        <p style={{ color: '#999', fontSize: 13 }}>记忆管理API尚未就绪，请确认后端MemoryController已实现</p>
      </div>
    );
  }

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <header style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 20, fontWeight: 600 }}>🧠 记忆管理</h1>
        <p style={{ color: '#666', fontSize: 14, marginTop: 4 }}>查看和管理AI的记忆与知识消费效果</p>
      </header>

      {/* 统计卡片 */}
      {stats && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 12, marginBottom: 20 }}>
          {[
            { label: '总记忆数', value: stats.totalCount ?? stats.total ?? '-' },
            { label: '今日新增', value: stats.todayCount ?? '-' },
            { label: '平均置信度', value: stats.avgConfidence ? `${(stats.avgConfidence * 100).toFixed(1)}%` : '-' },
            { label: '已归档', value: stats.archivedCount ?? '-' },
          ].map(s => (
            <div key={s.label} style={{
              padding: 16, borderRadius: 12, border: '1px solid var(--border-subtle)',
              background: 'rgba(255,255,255,0.02)', textAlign: 'center',
            }}>
              <div style={{ fontSize: 20, fontWeight: 600 }}>{s.value}</div>
              <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>{s.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* 搜索和筛选 */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
        <input
          placeholder="搜索记忆..."
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          style={{
            flex: 1, padding: '8px 12px', borderRadius: 8, border: '1px solid var(--border-subtle)',
            background: 'var(--bg-primary)', fontSize: 14,
          }}
        />
        <select
          value={filterType}
          onChange={e => setFilterType(e.target.value)}
          style={{
            padding: '8px 12px', borderRadius: 8, border: '1px solid var(--border-subtle)',
            background: 'var(--bg-primary)', fontSize: 14,
          }}
        >
          <option value="">全部类型</option>
          <option value="conversation">对话记忆</option>
          <option value="knowledge">知识记忆</option>
          <option value="skill">技能记忆</option>
          <option value="feedback">反馈记忆</option>
        </select>
      </div>

      {/* 记忆列表 */}
      <div style={{ display: 'grid', gap: 8 }}>
        {displayList.length === 0 && (
          <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>
            {searchQuery.length >= 2 ? '未找到匹配的记忆' : '暂无记忆数据'}
          </div>
        )}
        {displayList.map((memory: any) => (
          <div
            key={memory.id}
            onClick={() => setSelectedId(selectedId === memory.id ? null : memory.id)}
            style={{
              padding: 12, border: `1px solid ${selectedId === memory.id ? 'var(--accent)' : 'var(--border-subtle)'}`,
              borderRadius: 8, cursor: 'pointer', background: selectedId === memory.id ? 'rgba(74,158,255,0.05)' : 'transparent',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ flex: 1 }}>
                <span style={{ fontWeight: 500 }}>{memory.title || memory.type || '记忆'}</span>
                <span style={{ marginLeft: 8, fontSize: 12, color: '#999' }}>{memory.type}</span>
                {memory.confidence !== undefined && (
                  <span style={{ marginLeft: 8, fontSize: 11, color: memory.confidence > 0.7 ? '#38a169' : memory.confidence > 0.4 ? '#d69e2e' : '#e53e3e' }}>
                    {(memory.confidence * 100).toFixed(0)}%
                  </span>
                )}
              </div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {memory.referenceCount !== undefined && (
                  <span style={{ fontSize: 11, color: '#999' }}>引用 {memory.referenceCount} 次</span>
                )}
                <span style={{ fontSize: 11, color: '#999' }}>
                  {memory.createdAt ? new Date(memory.createdAt).toLocaleDateString() : ''}
                </span>
              </div>
            </div>
            {selectedId === memory.id && (
              <div style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--border-subtle)' }}>
                <p style={{ fontSize: 13, color: '#666', margin: '0 0 8px' }}>
                  {memory.content || memory.summary || '无内容'}
                </p>
                {memory.source && <p style={{ fontSize: 12, color: '#999', margin: '0 0 8px' }}>来源: {memory.source}</p>}
                <div style={{ display: 'flex', gap: 8 }}>
                  <button
                    className="btn btn-danger"
                    style={{ fontSize: 12, background: '#e53e3e', color: '#fff', border: 'none', borderRadius: 6, padding: '4px 12px' }}
                    onClick={e => { e.stopPropagation(); deleteMutation.mutate(memory.id); }}
                    disabled={deleteMutation.isPending}
                  >
                    删除
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
