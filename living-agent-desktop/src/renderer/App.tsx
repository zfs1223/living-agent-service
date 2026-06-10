/**
 * 桌面端 React 应用根组件
 *
 * 布局：顶部 header（品牌 / 状态 / Client ID）+ 左侧 sidebar（导航 + 后端状态 + 登录）+ 主区
 *
 * 与 web 端的区别：
 * - 桌面端定位为"轻量客户端"，主要承担：
 *   1. 鉴权 + 后端连接
 *   2. 公共任务栏浏览 / 接取
 *   3. 本地产物保存设置
 *   4. 系统托盘 / 悬浮窗 / 通知 / 快捷键（由主进程提供）
 * - 复杂功能（部门详情 / 虚拟办公室 / 数字员工管理）依然走 web 端
 *   桌面端提供"在浏览器中打开"入口跳转
 *
 * 启动关键：检测后端 URL 是否已配置（区别于默认占位）。未配置时强制进入 Settings。
 * 这是因为桌面端要装到不同的客户端 PC 上，每台机器可能连不同的后端（生产远程 / 内网测试）。
 */
import { useEffect, useState } from 'react';
import { LocalSaveSettings } from './pages/Settings/LocalSave';
import { Settings } from './pages/Settings/Settings';
import { PublicTaskBoardPage } from './pages/TaskBoard/PublicTaskBoardPage';
import type { ClientInfo } from '@shared/api-types';

type View = 'home' | 'tasks' | 'local-save' | 'settings';

interface BackendStatus {
  status: 'online' | 'offline' | 'unknown';
  url: string;
}

export function App() {
  const [view, setView] = useState<View>('home');
  const [backend, setBackend] = useState<BackendStatus>({ status: 'unknown', url: '' });
  const [hasToken, setHasToken] = useState<boolean>(false);
  const [clientInfo, setClientInfo] = useState<ClientInfo | null>(null);
  const [configured, setConfigured] = useState<boolean | null>(null);
  const [forceSettings, setForceSettings] = useState<boolean>(false);

  useEffect(() => {
    void (async () => {
      const ok = await window.livingAgentAPI.isBackendConfigured();
      setConfigured(ok);
      // 未配置时强制进入 Settings
      if (!ok) {
        setForceSettings(true);
        setView('settings');
      }
      void refreshStatus();
      void refreshAuth();
      void refreshClientInfo();
    })();

    // 订阅主进程推送的后端状态变化
    const off = window.livingAgentAPI.onBackendStatusChanged((info) => {
      setBackend({ status: info.status, url: info.url });
    });
    return () => off();
  }, []);

  async function refreshStatus() {
    try {
      const url = await window.livingAgentAPI.getBackendUrl();
      const check = await window.livingAgentAPI.checkBackend();
      setBackend({ status: check.ok ? 'online' : 'offline', url });
    } catch (e) {
      setBackend({ status: 'offline', url: '' });
    }
  }

  async function refreshAuth() {
    const token = await window.livingAgentAPI.auth.getToken();
    setHasToken(!!token);
  }

  async function refreshClientInfo() {
    try {
      const info = await window.livingAgentAPI.app.getClientInfo();
      setClientInfo(info);
    } catch (e) {
      console.warn('[desktop] failed to load client info:', e);
    }
  }

  async function handleLogin() {
    // 桌面端鉴权流程：弹出 web 端登录页（在系统浏览器中），登录后回调
    // 简化：直接提示用户去 web 端登录并粘贴 token
    const token = window.prompt(
      '请到 Web 端登录后，复制 token 粘贴到此处。\n（生产可改为 OAuth 回调或内置登录表单）'
    );
    if (token && token.trim()) {
      await window.livingAgentAPI.auth.setToken(token.trim());
      setHasToken(true);
    }
  }

  async function handleLogout() {
    await window.livingAgentAPI.auth.clearToken();
    setHasToken(false);
  }

  async function handleOpenInBrowser() {
    const url = await window.livingAgentAPI.getBackendUrl();
    if (url) {
      window.open(url, '_blank');
    }
  }

  async function handleBackendChanged(url: string) {
    setBackend({ status: 'unknown', url });
    setConfigured(true);
    // 首次配置完成后退出强制设置模式
    setForceSettings(false);
    setView('home');
    // 立即重新检测
    void refreshStatus();
  }

  function handleNav(v: View) {
    if (forceSettings && v !== 'settings') {
      // 强制模式下只允许在 settings 内操作
      return;
    }
    setView(v);
  }

  // 加载中：避免配置状态未确认前的页面闪烁
  if (configured === null) {
    return (
      <div className="desktop-app">
        <main className="desktop-main" style={{ display: 'grid', placeItems: 'center' }}>
          <div style={{ color: '#999' }}>初始化中…</div>
        </main>
      </div>
    );
  }

  return (
    <div className="desktop-app">
      {/* ============ 顶部状态条 ============ */}
      <header className="desktop-header">
        <div className="header-brand">
          <span style={{ fontSize: 18 }}>🤖</span>
          <span>Living Agent</span>
          <span style={{ fontSize: 11, color: '#999', marginLeft: 4 }}>桌面客户端</span>
        </div>

        <div className="header-status">
          <div className="header-status-item" title={backend.url || '后端地址未配置'}>
            <span className={`status-dot ${backend.status}`} />
            <span className={`status-text ${backend.status}`}>
              {backend.status === 'online'
                ? '后端在线'
                : backend.status === 'offline'
                ? '后端离线'
                : '检查中…'}
            </span>
          </div>
          <div className="header-status-item">
            <span className={hasToken ? 'status-text online' : 'status-text offline'}>
              {hasToken ? '✅ 已登录' : '⚠️ 未登录'}
            </span>
          </div>
          {clientInfo && (
            <div className="header-status-item" title={clientInfo.clientId}>
              <span style={{ color: '#888' }}>🆔</span>
              <code className="client-id-chip">{shortId(clientInfo.clientId)}</code>
            </div>
          )}
          <div className="header-status-item">
            <span style={{ color: '#aaa', fontSize: 11 }}>v{clientInfo?.appVersion || '0.0.0'}</span>
          </div>
        </div>
      </header>

      {/* ============ 侧边栏 ============ */}
      <aside className="desktop-sidebar">
        <div className="sidebar-brand">
          <span style={{ fontSize: 20 }}>🤖</span>
          <strong>Living Agent</strong>
        </div>

        <nav className="sidebar-nav">
          <button
            className={view === 'home' ? 'nav-item active' : 'nav-item'}
            onClick={() => handleNav('home')}
          >
            🏠 概览
          </button>
          <button
            className={view === 'tasks' ? 'nav-item active' : 'nav-item'}
            onClick={() => handleNav('tasks')}
          >
            📋 公共任务栏
          </button>
          <button
            className={view === 'local-save' ? 'nav-item active' : 'nav-item'}
            onClick={() => handleNav('local-save')}
          >
            💾 本地保存
          </button>
          <button
            className={view === 'settings' ? 'nav-item active' : 'nav-item'}
            onClick={() => handleNav('settings')}
          >
            ⚙️ 设置
          </button>
        </nav>

        <div className="sidebar-footer">
          {hasToken ? (
            <button onClick={handleLogout} className="auth-btn">
              🚪 退出登录
            </button>
          ) : (
            <button onClick={handleLogin} className="auth-btn">
              🔑 登录
            </button>
          )}
          <button onClick={handleOpenInBrowser} className="auth-btn" disabled={!backend.url}>
            🌐 在浏览器中打开
          </button>
        </div>
      </aside>

      {/* ============ 主区 ============ */}
      <main className="desktop-main">
        {forceSettings && (
          <div className="force-settings-banner" role="alert">
            ⚠️ 首次使用需要先配置后端服务地址。配置完成后才能使用其它功能。
          </div>
        )}
        {view === 'home' && (
          <HomeView
            backend={backend}
            hasToken={hasToken}
            clientInfo={clientInfo}
            onNav={handleNav}
          />
        )}
        {view === 'tasks' && <PublicTaskBoardPage />}
        {view === 'local-save' && <LocalSaveSettings />}
        {view === 'settings' && (
          <Settings
            clientInfo={clientInfo}
            onBackendChanged={handleBackendChanged}
            onLogout={handleLogout}
            hasToken={hasToken}
          />
        )}
      </main>
    </div>
  );
}

/** 缩短 UUID 展示（前 8 位） */
function shortId(uuid: string): string {
  if (!uuid) return '';
  if (uuid.length <= 13) return uuid;
  return uuid.slice(0, 8) + '…';
}

function HomeView({
  backend,
  hasToken,
  clientInfo,
  onNav
}: {
  backend: BackendStatus;
  hasToken: boolean;
  clientInfo: ClientInfo | null;
  onNav: (v: View) => void;
}) {
  return (
    <div className="home-view">
      <div className="home-hero">
        <h1>欢迎使用 Living Agent 桌面端</h1>
        <p>
          桌面端是<span style={{ color: '#fff', background: 'rgba(255,255,255,0.2)', padding: '0 6px', borderRadius: 3 }}>独立安装</span>的客户端应用，
          与服务端 Web 端通过 HTTP API 通信，不共享代码。
        </p>
      </div>

      {clientInfo && (
        <section className="home-status" style={{ marginBottom: 16 }}>
          <h3>当前客户端</h3>
          <div className="home-status-grid">
            <div className="status-cell">
              <span className="label">Client ID</span>
              <span className="value mono" title={clientInfo.clientId}>
                {shortId(clientInfo.clientId)}
              </span>
            </div>
            <div className="status-cell">
              <span className="label">主机</span>
              <span className="value">{clientInfo.hostname}</span>
            </div>
            <div className="status-cell">
              <span className="label">系统</span>
              <span className="value">{clientInfo.platform}</span>
            </div>
            <div className="status-cell">
              <span className="label">用户</span>
              <span className="value">{clientInfo.osUser}</span>
            </div>
            <div className="status-cell">
              <span className="label">注册时间</span>
              <span className="value">{clientInfo.createdAt.slice(0, 10)}</span>
            </div>
          </div>
        </section>
      )}

      <div className="home-grid">
        <button className="action-card" onClick={() => onNav('tasks')}>
          <span className="icon">📋</span>
          <span className="title">公共任务栏</span>
          <span className="desc">浏览部门公开任务、固定数字员工无法处理时派发的任务</span>
          <span className="badge">主要功能</span>
        </button>

        <button className="action-card" onClick={() => onNav('local-save')}>
          <span className="icon">💾</span>
          <span className="title">本地产物保存</span>
          <span className="desc">将服务器上的产物按权限自动备份到本机文件夹</span>
          <span className="badge">推荐</span>
        </button>

        <button className="action-card" onClick={() => onNav('settings')}>
          <span className="icon">⚙️</span>
          <span className="title">设置</span>
          <span className="desc">后端地址、客户端标识、登录态</span>
          {backend.url ? (
            <span className="badge">{backend.url}</span>
          ) : (
            <span className="badge warn">未配置后端</span>
          )}
        </button>

        <button
          className="action-card"
          onClick={() => {
            if (backend.url) window.open(backend.url, '_blank');
          }}
          disabled={!backend.url}
        >
          <span className="icon">🌐</span>
          <span className="title">Web 端（功能完整）</span>
          <span className="desc">部门详情 / 虚拟办公室 / 数字员工配置 / 模型配置</span>
          <span className="badge warn">在浏览器中</span>
        </button>
      </div>

      <section className="home-status">
        <h3>系统状态</h3>
        <div className="home-status-grid">
          <div className="status-cell">
            <span className="label">后端地址</span>
            <span className="value mono" style={{ wordBreak: 'break-all' }}>
              {backend.url || '未配置'}
            </span>
          </div>
          <div className="status-cell">
            <span className="label">连接状态</span>
            <span className={`value status-text ${backend.status}`}>
              {backend.status === 'online'
                ? '在线'
                : backend.status === 'offline'
                ? '离线'
                : '检查中…'}
            </span>
          </div>
          <div className="status-cell">
            <span className="label">登录态</span>
            <span className={`value status-text ${hasToken ? 'online' : 'offline'}`}>
              {hasToken ? '已登录' : '未登录'}
            </span>
          </div>
        </div>
      </section>

      {!hasToken && (
        <div className="notice">
          ⚠️ 尚未登录，部分功能（接取任务 / 同步产物）不可用。
        </div>
      )}
    </div>
  );
}
