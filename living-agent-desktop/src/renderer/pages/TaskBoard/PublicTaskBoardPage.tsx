/**
 * 桌面端公共任务中心页面
 * 桌面端是独立安装包，所有 UI 组件都在 src/renderer/components 下，
 * 不复用 web 端 frontend/src/components。组件通过 window.livingAgentAPI IPC
 * 与主进程通信，由主进程转发到后端服务（与 web 端共享同一后端 API）。
 */
import React, { useState, useEffect } from 'react';
import PublicTaskBoard from '../../components/PublicTaskBoard';

type TabType = 'tasks' | 'plaza';

export function PublicTaskBoardPage() {
  const [tab, setTab] = useState<TabType>('tasks');
  
  // 广场状态
  const [posts, setPosts] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newContent, setNewContent] = useState('');

  useEffect(() => {
    if (tab === 'plaza') {
      loadPlazaData();
    }
  }, [tab]);

  async function loadPlazaData() {
    setLoading(true);
    try {
      const [p, s] = await Promise.all([
        window.livingAgentAPI.plaza.posts(),
        window.livingAgentAPI.plaza.stats().catch(() => null),
      ]);
      setPosts(Array.isArray(p) ? p : []);
      setStats(s);
    } catch (e: any) {
      console.error('加载广场失败:', e);
    } finally {
      setLoading(false);
    }
  }

  async function handleLike(postId: string) {
    try {
      await window.livingAgentAPI.plaza.like(postId);
      loadPlazaData();
    } catch (e: any) {
      alert(e.message);
    }
  }

  async function handleCreate() {
    if (!newTitle || !newContent) return;
    try {
      await window.livingAgentAPI.plaza.create({ title: newTitle, content: newContent });
      setNewTitle('');
      setNewContent('');
      setShowCreate(false);
      loadPlazaData();
    } catch (e: any) {
      alert(e.message);
    }
  }

  return (
    <div className="desktop-task-board-page" style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <header style={{ marginBottom: 16 }}>
        <h1>📋 公共任务栏</h1>
        <p style={{ color: '#666' }}>
          固定数字员工无法处理的任务，可接取并完成以获得积分奖励
        </p>
      </header>

      {/* 标签页切换 */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16, borderBottom: '1px solid #e8e8e8', paddingBottom: 8 }}>
        <button
          onClick={() => setTab('tasks')}
          style={{
            padding: '8px 16px',
            border: 'none',
            background: tab === 'tasks' ? '#1890ff' : '#f5f5f5',
            color: tab === 'tasks' ? '#fff' : '#333',
            borderRadius: 4,
            cursor: 'pointer',
          }}
        >
          📋 任务
        </button>
        <button
          onClick={() => setTab('plaza')}
          style={{
            padding: '8px 16px',
            border: 'none',
            background: tab === 'plaza' ? '#1890ff' : '#f5f5f5',
            color: tab === 'plaza' ? '#fff' : '#333',
            borderRadius: 4,
            cursor: 'pointer',
          }}
        >
          🏛️ 广场
        </button>
      </div>

      {/* 任务列表 */}
      {tab === 'tasks' && <PublicTaskBoard />}

      {/* 广场内容 */}
      {tab === 'plaza' && (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <h2 style={{ margin: 0 }}>🏛️ 广场</h2>
            <button
              onClick={() => setShowCreate(!showCreate)}
              style={{
                padding: '6px 12px',
                background: '#1890ff',
                color: '#fff',
                border: 'none',
                borderRadius: 4,
                cursor: 'pointer',
              }}
            >
              {showCreate ? '取消' : '✏️ 发帖'}
            </button>
          </div>
          
          {stats && (
            <div style={{ margin: '8px 0', fontSize: 12, color: '#666' }}>
              📊 帖子: {stats.totalPosts ?? '-'} · 点赞: {stats.totalLikes ?? '-'} · 作者: {stats.totalAuthors ?? '-'}
            </div>
          )}

          {showCreate && (
            <div style={{ padding: 16, border: '1px solid #4a9eff', borderRadius: 8, marginBottom: 16, background: '#f8fbff' }}>
              <input
                placeholder="标题"
                value={newTitle}
                onChange={e => setNewTitle(e.target.value)}
                style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, marginBottom: 8, boxSizing: 'border-box' }}
              />
              <textarea
                placeholder="内容"
                value={newContent}
                onChange={e => setNewContent(e.target.value)}
                style={{ width: '100%', minHeight: 80, padding: 8, border: '1px solid #ddd', borderRadius: 6, marginBottom: 8, boxSizing: 'border-box' }}
              />
              <button
                onClick={handleCreate}
                disabled={!newTitle || !newContent}
                style={{
                  padding: '6px 16px',
                  background: !newTitle || !newContent ? '#ccc' : '#1890ff',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 4,
                  cursor: !newTitle || !newContent ? 'not-allowed' : 'pointer',
                }}
              >
                发布
              </button>
            </div>
          )}

          {loading ? (
            <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>加载中...</div>
          ) : (
            <div style={{ display: 'grid', gap: 8 }}>
              {posts.length === 0 && (
                <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>暂无帖子</div>
              )}
              {posts.map(post => (
                <div key={post.id} style={{ padding: 12, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 500, marginBottom: 4 }}>{post.title}</div>
                      <div style={{ fontSize: 13, color: '#666', whiteSpace: 'pre-wrap' }}>{post.content}</div>
                      <div style={{ fontSize: 11, color: '#999', marginTop: 8 }}>
                        {post.author_name || '匿名'} · {post.created_at ? new Date(post.created_at).toLocaleString() : '-'}
                      </div>
                    </div>
                    <button
                      onClick={() => handleLike(post.id)}
                      style={{
                        background: 'none',
                        border: '1px solid #ddd',
                        borderRadius: 4,
                        padding: '4px 8px',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 4,
                      }}
                    >
                      👍 {post.likes || 0}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
