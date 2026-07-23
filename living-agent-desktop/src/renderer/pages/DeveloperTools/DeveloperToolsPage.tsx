/**
 * P20: Claude CLI 工具入口 - 开发者工具页面
 *
 * 提供技术部员工的开发者工具快捷入口
 */
import { useState } from 'react';
import './DeveloperTools.css';

interface DeveloperToolsPageProps {
  backendUrl: string;
  hasToken: boolean;
  currentUser: any;
}

interface ToolEntry {
  id: string;
  name: string;
  description: string;
  icon: string;
  command?: string;
  shortcut?: string;
  category: 'code' | 'debug' | 'deploy' | 'monitor';
}

const DEV_TOOLS: ToolEntry[] = [
  {
    id: 'claude-cli',
    name: 'Claude CLI',
    description: 'Claude 命令行工具，用于代码审查、重构建议',
    icon: '🤖',
    command: 'claude',
    shortcut: 'Ctrl+Shift+C',
    category: 'code'
  },
  {
    id: 'code-review',
    name: '代码审查',
    description: 'AI 驱动的代码审查，检测潜在问题和改进建议',
    icon: '🔍',
    shortcut: 'Ctrl+Shift+R',
    category: 'code'
  },
  {
    id: 'git-assistant',
    name: 'Git 助手',
    description: '智能 Git 操作，自动生成 commit message',
    icon: '📦',
    command: 'git-assist',
    category: 'code'
  },
  {
    id: 'log-viewer',
    name: '日志查看器',
    description: '实时查看服务日志，支持过滤和搜索',
    icon: '📋',
    category: 'debug'
  },
  {
    id: 'trace-analyzer',
    name: 'Trace 分析器',
    description: '分析执行 Trace，识别性能瓶颈',
    icon: '📊',
    category: 'debug'
  },
  {
    id: 'deploy-dashboard',
    name: '部署仪表盘',
    description: '查看和管理部署流水线状态',
    icon: '🚀',
    category: 'deploy'
  },
  {
    id: 'metrics-explorer',
    name: '指标浏览器',
    description: '浏览系统指标和历史数据',
    icon: '📈',
    category: 'monitor'
  },
  {
    id: 'health-check',
    name: '健康检查',
    description: '检查各服务组件健康状态',
    icon: '💊',
    category: 'monitor'
  }
];

const CATEGORY_LABELS: Record<string, string> = {
  code: '代码工具',
  debug: '调试工具',
  deploy: '部署工具',
  monitor: '监控工具'
};

export default function DeveloperToolsPage({ backendUrl, hasToken, currentUser }: DeveloperToolsPageProps) {
  const [selectedTool, setSelectedTool] = useState<ToolEntry | null>(null);
  const [filter, setFilter] = useState<string>('all');
  const [commandOutput, setCommandOutput] = useState<string>('');
  const [running, setRunning] = useState(false);

  // 检查权限：仅技术部或 FULL 权限可访问
  const canAccess = currentUser?.department === 'tech' ||
                    currentUser?.accessLevel === 'FULL' ||
                    currentUser?.identity === 'INTERNAL_ENTERPRISE';

  if (!hasToken) {
    return (
      <div className="developer-tools">
        <div className="developer-tools__login-prompt">
          <span>🔐 请先登录以使用开发者工具</span>
        </div>
      </div>
    );
  }

  if (!canAccess) {
    return (
      <div className="developer-tools">
        <div className="developer-tools__permission-denied">
          <span>⛔ 权限不足：仅技术部员工可访问开发者工具</span>
        </div>
      </div>
    );
  }

  const filteredTools = filter === 'all'
    ? DEV_TOOLS
    : DEV_TOOLS.filter(t => t.category === filter);

  const runTool = async (tool: ToolEntry) => {
    if (!tool.command) {
      setCommandOutput(`[提示] ${tool.name} 暂无命令行接口，请使用快捷键 ${tool.shortcut || 'N/A'}`);
      return;
    }

    setRunning(true);
    setCommandOutput(`[执行] ${tool.command}...\n`);

    // 模拟命令执行（实际应调用 preload 暴露的 CLI 接口）
    setTimeout(() => {
      setCommandOutput(prev => prev + `[完成] ${tool.name} 已启动\n`);
      setRunning(false);
    }, 1000);
  };

  return (
    <div className="developer-tools">
      <div className="developer-tools__header">
        <h1>🛠️ 开发者工具</h1>
        <div className="developer-tools__filter">
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="all">全部工具</option>
            <option value="code">代码工具</option>
            <option value="debug">调试工具</option>
            <option value="deploy">部署工具</option>
            <option value="monitor">监控工具</option>
          </select>
        </div>
      </div>

      <div className="developer-tools__content">
        <div className="developer-tools__list">
          {filteredTools.map(tool => (
            <div
              key={tool.id}
              className={`developer-tools__item ${selectedTool?.id === tool.id ? 'selected' : ''}`}
              onClick={() => setSelectedTool(tool)}
              onDoubleClick={() => runTool(tool)}
            >
              <span className="developer-tools__item-icon">{tool.icon}</span>
              <div className="developer-tools__item-info">
                <div className="developer-tools__item-name">{tool.name}</div>
                <div className="developer-tools__item-desc">{tool.description}</div>
              </div>
              {tool.shortcut && (
                <span className="developer-tools__item-shortcut">{tool.shortcut}</span>
              )}
            </div>
          ))}
        </div>

        {selectedTool && (
          <div className="developer-tools__detail">
            <div className="developer-tools__detail-header">
              <span className="developer-tools__detail-icon">{selectedTool.icon}</span>
              <div className="developer-tools__detail-title">{selectedTool.name}</div>
            </div>
            <div className="developer-tools__detail-category">
              {CATEGORY_LABELS[selectedTool.category]}
            </div>
            <p className="developer-tools__detail-desc">{selectedTool.description}</p>
            {selectedTool.command && (
              <div className="developer-tools__detail-command">
                <span className="command-label">命令:</span>
                <code className="command-value">{selectedTool.command}</code>
              </div>
            )}
            {selectedTool.shortcut && (
              <div className="developer-tools__detail-shortcut">
                <span className="shortcut-label">快捷键:</span>
                <kbd className="shortcut-value">{selectedTool.shortcut}</kbd>
              </div>
            )}
            <button
              className="developer-tools__run-btn"
              onClick={() => runTool(selectedTool)}
              disabled={running}
            >
              {running ? '执行中...' : '▶️ 运行'}
            </button>
          </div>
        )}
      </div>

      {commandOutput && (
        <div className="developer-tools__output">
          <div className="output-header">
            <span>输出</span>
            <button onClick={() => setCommandOutput('')}>清空</button>
          </div>
          <pre className="output-content">{commandOutput}</pre>
        </div>
      )}
    </div>
  );
}