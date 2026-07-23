/**
 * P12: Memory 面板
 *
 * 查看和管理已记忆的内容
 */
import { useState, useEffect } from 'react';
import './MemoryControls.css';

interface MemoryEntry {
  id: string;
  content: string;
  createdAt: string;
  source?: string;
  tags?: string[];
  /** P18: 是否已转化为知识 */
  convertedToKnowledge?: boolean;
  /** P18: 关联的知识条目 ID */
  knowledgeId?: string;
}

interface MemoryPanelProps {
  onClose?: () => void;
  onDelete?: (id: string) => Promise<void>;
  /** P18: 编辑记忆 */
  onEdit?: (id: string, newContent: string) => Promise<void>;
  /** P18: 转化为知识 */
  onConvertToKnowledge?: (id: string) => Promise<void>;
  backendUrl?: string;
  token?: string;
}

export default function MemoryPanel({
  onClose,
  onDelete,
  onEdit,
  onConvertToKnowledge,
  backendUrl,
  token
}: MemoryPanelProps) {
  const [memories, setMemories] = useState<MemoryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [deletingIds, setDeletingIds] = useState<Set<string>>(new Set());

  // P18: 编辑状态
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editContent, setEditContent] = useState('');

  // P18: 转化状态
  const [convertingIds, setConvertingIds] = useState<Set<string>>(new Set());
  const [showKnowledgePanel, setShowKnowledgePanel] = useState(false);

  // 加载记忆列表
  useEffect(() => {
    loadMemories();
  }, [backendUrl, token]);

  const loadMemories = async () => {
    if (!backendUrl || !token) {
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${backendUrl}/api/memory/entries`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setMemories(data.data || data || []);
      } else {
        setError('加载失败');
      }
    } catch (err) {
      console.error('[MemoryPanel] 加载失败:', err);
      setError('加载失败');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!onDelete) return;

    setDeletingIds(prev => new Set(prev).add(id));
    try {
      await onDelete(id);
      setMemories(prev => prev.filter(m => m.id !== id));
    } catch (err) {
      console.error('[MemoryPanel] 删除失败:', err);
    } finally {
      setDeletingIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  // P18: 开始编辑
  const startEdit = (memory: MemoryEntry) => {
    setEditingId(memory.id);
    setEditContent(memory.content);
  };

  // P18: 保存编辑
  const saveEdit = async () => {
    if (!editingId || !onEdit) return;
    try {
      await onEdit(editingId, editContent);
      setMemories(prev => prev.map(m =>
        m.id === editingId ? { ...m, content: editContent } : m
      ));
      setEditingId(null);
      setEditContent('');
    } catch (err) {
      console.error('[MemoryPanel] 编辑失败:', err);
    }
  };

  // P18: 取消编辑
  const cancelEdit = () => {
    setEditingId(null);
    setEditContent('');
  };

  // P18: 转化为知识
  const handleConvertToKnowledge = async (id: string) => {
    if (!onConvertToKnowledge) return;
    setConvertingIds(prev => new Set(prev).add(id));
    try {
      await onConvertToKnowledge(id);
      setMemories(prev => prev.map(m =>
        m.id === id ? { ...m, convertedToKnowledge: true } : m
      ));
    } catch (err) {
      console.error('[MemoryPanel] 转化失败:', err);
    } finally {
      setConvertingIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  // 搜索过滤
  const filteredMemories = searchQuery
    ? memories.filter(m =>
        m.content.toLowerCase().includes(searchQuery.toLowerCase()) ||
        m.tags?.some(t => t.toLowerCase().includes(searchQuery.toLowerCase()))
      )
    : memories;

  return (
    <div className="memory-panel">
      <div className="memory-panel__header">
        <div className="memory-panel__title">
          <span className="memory-panel__icon">🧠</span>
          <span>记忆库</span>
          <span className="memory-panel__count">{memories.length}</span>
          {/* P18: 已转化为知识计数 */}
          {memories.filter(m => m.convertedToKnowledge).length > 0 && (
            <span className="memory-panel__knowledge-count" title="已转化为知识">
              → {memories.filter(m => m.convertedToKnowledge).length} 知识
            </span>
          )}
        </div>
        {/* P18: 知识库面板切换 */}
        <button
          className={`memory-panel__toggle-knowledge ${showKnowledgePanel ? 'active' : ''}`}
          onClick={() => setShowKnowledgePanel(!showKnowledgePanel)}
          title="查看知识库"
        >
          📚
        </button>
        {onClose && (
          <button className="memory-panel__close" onClick={onClose}>
            ✕
          </button>
        )}
      </div>

      <div className="memory-panel__search">
        <input
          type="text"
          className="memory-panel__search-input"
          placeholder="搜索记忆..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
      </div>

      <div className="memory-panel__content">
        {loading && (
          <div className="memory-panel__loading">加载中...</div>
        )}

        {error && (
          <div className="memory-panel__error">{error}</div>
        )}

        {!loading && !error && filteredMemories.length === 0 && (
          <div className="memory-panel__empty">
            {searchQuery ? '未找到匹配的记忆' : '暂无记忆'}
          </div>
        )}

        {!loading && !error && filteredMemories.length > 0 && (
          <div className="memory-panel__list">
            {filteredMemories.map(memory => (
              <div key={memory.id} className={`memory-entry ${memory.convertedToKnowledge ? 'memory-entry--converted' : ''}`}>
                {/* P18: 编辑模式 */}
                {editingId === memory.id ? (
                  <div className="memory-entry__edit">
                    <textarea
                      className="memory-entry__edit-textarea"
                      value={editContent}
                      onChange={(e) => setEditContent(e.target.value)}
                      rows={3}
                    />
                    <div className="memory-entry__edit-actions">
                      <button className="memory-entry__save" onClick={saveEdit}>✅ 保存</button>
                      <button className="memory-entry__cancel" onClick={cancelEdit}>取消</button>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="memory-entry__content">
                      {memory.content}
                      {/* P18: 已转化标识 */}
                      {memory.convertedToKnowledge && (
                        <span className="memory-entry__converted-badge" title="已转化为知识">📚</span>
                      )}
                    </div>
                    <div className="memory-entry__meta">
                      <span className="memory-entry__time">
                        {new Date(memory.createdAt).toLocaleString()}
                      </span>
                      {memory.tags && memory.tags.length > 0 && (
                        <div className="memory-entry__tags">
                          {memory.tags.map(tag => (
                            <span key={tag} className="memory-entry__tag">{tag}</span>
                          ))}
                        </div>
                      )}
                    </div>
                    {/* P18: 操作按钮组 */}
                    <div className="memory-entry__actions">
                      {/* 编辑按钮 */}
                      <button
                        className="memory-entry__action"
                        onClick={() => startEdit(memory)}
                        title="编辑"
                      >
                        ✏️
                      </button>
                      {/* 转化为知识按钮 */}
                      {onConvertToKnowledge && !memory.convertedToKnowledge && (
                        <button
                          className="memory-entry__action memory-entry__convert"
                          onClick={() => handleConvertToKnowledge(memory.id)}
                          disabled={convertingIds.has(memory.id)}
                          title="转化为知识"
                        >
                          {convertingIds.has(memory.id) ? '...' : '📚'}
                        </button>
                      )}
                      {/* 删除按钮 */}
                      <button
                        className="memory-entry__action memory-entry__delete"
                        onClick={() => handleDelete(memory.id)}
                        disabled={deletingIds.has(memory.id)}
                        title="删除"
                      >
                        {deletingIds.has(memory.id) ? '...' : '🗑️'}
                      </button>
                    </div>
                  </>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="memory-panel__footer">
        <button className="memory-panel__refresh" onClick={loadMemories}>
          刷新
        </button>
      </div>
    </div>
  );
}