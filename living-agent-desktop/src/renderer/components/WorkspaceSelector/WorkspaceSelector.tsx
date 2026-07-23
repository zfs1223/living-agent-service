/**
 * P31: 工作空间选择器
 *
 * 允许用户选择本地目录授权给 LAS 读取文件
 * - 用户主动选择目录 + 明示授权范围 + 可随时撤销
 * - 沙箱路径校验（防 `../../` 越权）+ 50MB 单文件限制
 * - 不写入文件、不持久化内容到云端、不跨用户共享
 */
import { useState, useEffect } from 'react';
import './WorkspaceSelector.css';

interface Workspace {
  id: string;
  path: string;
  name: string;
  authorizedAt: string;
  scope: 'read' | 'read-write';
  fileCount?: number;
  totalSize?: number;
}

interface WorkspaceSelectorProps {
  onWorkspaceChange?: (workspace: Workspace | null) => void;
  currentWorkspace?: Workspace | null;
}

export default function WorkspaceSelector({ onWorkspaceChange, currentWorkspace }: WorkspaceSelectorProps) {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [showDialog, setShowDialog] = useState(false);
  const [selectedPath, setSelectedPath] = useState('');
  const [selectedScope, setSelectedScope] = useState<'read' | 'read-write'>('read');
  const [authorizing, setAuthorizing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 加载已授权的工作空间列表
  useEffect(() => {
    loadWorkspaces();
  }, []);

  const loadWorkspaces = async () => {
    // 从 preload 加载（实际应调用 window.livingAgentAPI.workspace.list()）
    // 模拟数据
    setWorkspaces([]);
  };

  // 选择目录（调用 Electron dialog）
  const selectDirectory = async () => {
    try {
      // 实际应调用 window.livingAgentAPI.dialog.showOpenDialog({ properties: ['openDirectory'] })
      // 模拟选择
      setSelectedPath('C:\\Users\\User\\Projects\\my-project');
    } catch (err) {
      setError('无法选择目录');
    }
  };

  // 授权工作空间
  const authorizeWorkspace = async () => {
    if (!selectedPath) {
      setError('请先选择目录');
      return;
    }

    // 路径安全校验
    if (selectedPath.includes('..') || selectedPath.includes('~')) {
      setError('路径不安全，请选择合法目录');
      return;
    }

    setAuthorizing(true);
    setError(null);

    try {
      // 实际应调用 window.livingAgentAPI.workspace.authorize()
      await new Promise(resolve => setTimeout(resolve, 1000));

      const newWorkspace: Workspace = {
        id: `ws-${Date.now()}`,
        path: selectedPath,
        name: selectedPath.split(/[\\\/]/).pop() || 'workspace',
        authorizedAt: new Date().toISOString(),
        scope: selectedScope,
        fileCount: 0,
        totalSize: 0
      };

      setWorkspaces(prev => [...prev, newWorkspace]);
      setShowDialog(false);
      setSelectedPath('');
      onWorkspaceChange?.(newWorkspace);
    } catch (err: any) {
      setError(err.message || '授权失败');
    } finally {
      setAuthorizing(false);
    }
  };

  // 撤销授权
  const revokeWorkspace = async (id: string) => {
    try {
      // 实际应调用 window.livingAgentAPI.workspace.revoke(id)
      setWorkspaces(prev => prev.filter(w => w.id !== id));
      if (currentWorkspace?.id === id) {
        onWorkspaceChange?.(null);
      }
    } catch (err) {
      console.error('撤销失败:', err);
    }
  };

  return (
    <div className="workspace-selector">
      <div className="workspace-selector__header">
        <span className="workspace-selector__title">📁 工作空间</span>
        <button
          className="workspace-selector__add"
          onClick={() => setShowDialog(true)}
          title="添加工作空间"
        >
          +
        </button>
      </div>

      {/* 当前工作空间 */}
      {currentWorkspace && (
        <div className="workspace-selector__current">
          <div className="current-workspace">
            <span className="current-workspace__icon">📂</span>
            <div className="current-workspace__info">
              <div className="current-workspace__name">{currentWorkspace.name}</div>
              <div className="current-workspace__path">{currentWorkspace.path}</div>
            </div>
            <button
              className="current-workspace__revoke"
              onClick={() => revokeWorkspace(currentWorkspace.id)}
              title="撤销授权"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {/* 工作空间列表 */}
      {workspaces.length > 0 && !currentWorkspace && (
        <div className="workspace-selector__list">
          {workspaces.map(ws => (
            <div
              key={ws.id}
              className="workspace-item"
              onClick={() => onWorkspaceChange?.(ws)}
            >
              <span className="workspace-item__icon">📁</span>
              <div className="workspace-item__info">
                <div className="workspace-item__name">{ws.name}</div>
                <div className="workspace-item__path">{ws.path}</div>
              </div>
              <button
                className="workspace-item__revoke"
                onClick={(e) => { e.stopPropagation(); revokeWorkspace(ws.id); }}
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}

      {/* 空状态 */}
      {workspaces.length === 0 && !currentWorkspace && (
        <div className="workspace-selector__empty">
          <span>尚未授权任何工作空间</span>
          <button onClick={() => setShowDialog(true)}>添加工作空间</button>
        </div>
      )}

      {/* 添加对话框 */}
      {showDialog && (
        <div className="workspace-dialog-overlay" onClick={() => setShowDialog(false)}>
          <div className="workspace-dialog" onClick={e => e.stopPropagation()}>
            <div className="workspace-dialog__header">
              <h3>📁 添加工作空间</h3>
              <button onClick={() => setShowDialog(false)}>✕</button>
            </div>

            <div className="workspace-dialog__content">
              <div className="workspace-dialog__field">
                <label>选择目录</label>
                <div className="path-input">
                  <input
                    type="text"
                    value={selectedPath}
                    onChange={(e) => setSelectedPath(e.target.value)}
                    placeholder="点击右侧按钮选择目录..."
                    readOnly
                  />
                  <button onClick={selectDirectory}>浏览...</button>
                </div>
              </div>

              <div className="workspace-dialog__field">
                <label>授权范围</label>
                <div className="scope-options">
                  <label className={`scope-option ${selectedScope === 'read' ? 'selected' : ''}`}>
                    <input
                      type="radio"
                      name="scope"
                      value="read"
                      checked={selectedScope === 'read'}
                      onChange={() => setSelectedScope('read')}
                    />
                    <span className="scope-option__label">只读</span>
                    <span className="scope-option__desc">仅读取文件内容</span>
                  </label>
                  <label className={`scope-option ${selectedScope === 'read-write' ? 'selected' : ''}`}>
                    <input
                      type="radio"
                      name="scope"
                      value="read-write"
                      checked={selectedScope === 'read-write'}
                      onChange={() => setSelectedScope('read-write')}
                    />
                    <span className="scope-option__label">读写</span>
                    <span className="scope-option__desc">读取和修改文件</span>
                  </label>
                </div>
              </div>

              {error && (
                <div className="workspace-dialog__error">{error}</div>
              )}

              <div className="workspace-dialog__security-note">
                <span className="security-icon">🔒</span>
                <span>LAS 仅按需读取文件，不会将内容上传到云端，不会跨用户共享</span>
              </div>
            </div>

            <div className="workspace-dialog__footer">
              <button
                className="btn btn-secondary"
                onClick={() => setShowDialog(false)}
              >
                取消
              </button>
              <button
                className="btn btn-primary"
                onClick={authorizeWorkspace}
                disabled={!selectedPath || authorizing}
              >
                {authorizing ? '授权中...' : '授权'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}