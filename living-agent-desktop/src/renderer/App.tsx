/**
 * 桌面端 React 应用根组件
 *
 * 布局：顶部 header（品牌 / 状态 / Client ID）+ 左侧 sidebar（导航 + 后端状态 + 登录）+ 主区
 *
 * 功能更新：
 * - 添加办公室房间布局（左侧）+ 聊天功能（右侧）的分栏布局
 * - 支持新建对话、历史记录、员工状态显示
 * - 复杂功能（部门详情 / 数字员工管理）依然走 web 端
 *   桌面端提供"在浏览器中打开"入口跳转
 *
 * 启动关键：检测后端 URL 是否已配置（区别于默认占位）。未配置时强制进入 Settings。
 * 这是因为桌面端要装到不同的客户端 PC 上，每台机器可能连不同的后端（生产远程 / 内网测试）。
 */
import { useEffect, useState, useRef } from 'react';
import { LocalSaveSettings } from './pages/Settings/LocalSave';
import { Settings } from './pages/Settings/Settings';
import { PublicTaskBoardPage } from './pages/TaskBoard/PublicTaskBoardPage';
import { OfficeChatPage } from './pages/OfficeChat';
import type { ClientInfo, DesktopUser } from '@shared/api-types';

type View = 'home' | 'chat' | 'tasks' | 'artifacts' | 'projects' | 'approvals' | 'messages' | 'local-save' | 'settings' | 'admin';

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
  const [showLoginDialog, setShowLoginDialog] = useState<boolean>(false);
  const [currentUser, setCurrentUser] = useState<DesktopUser | null>(null);

  // 登录表单状态（与 frontend 对齐：手机号 + 短信验证码）
  const [loginPhone, setLoginPhone] = useState('');
  const [loginCode, setLoginCode] = useState('');
  const [loginLoading, setLoginLoading] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [countdown, setCountdown] = useState(0);
  const [testCode, setTestCode] = useState('');

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

  // 验证码倒计时
  useEffect(() => {
    if (countdown > 0) {
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [countdown]);

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
    // 有 token 时自动获取用户信息
    if (token) {
      try {
        const user = await window.livingAgentAPI.auth.me();
        setCurrentUser(user);
      } catch (e) {
        // token 可能已过期
        setCurrentUser(null);
      }
    } else {
      setCurrentUser(null);
    }
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
    // 打开手机号+验证码登录对话框
    setLoginError('');
    setLoginPhone('');
    setLoginCode('');
    setTestCode('');
    setShowLoginDialog(true);
  }

  /** 发送短信验证码（与 frontend 对齐） */
  async function handleSendSms() {
    if (!loginPhone) {
      setLoginError('请输入手机号');
      return;
    }
    setLoginLoading(true);
    setLoginError('');
    try {
      const res = await window.livingAgentAPI.auth.smsSend(loginPhone, 'login');
      setCountdown(60);
      // 测试模式：后端直接返回验证码
      if (res.code) {
        setTestCode(res.code);
        setLoginCode(res.code || '');
      }
    } catch (err: any) {
      setLoginError(err.message || '发送验证码失败');
    } finally {
      setLoginLoading(false);
    }
  }

  /** 手机号+验证码提交登录（与 frontend 对齐） */
  async function handlePhoneLogin(e: React.FormEvent) {
    e.preventDefault();
    setLoginError('');
    setLoginLoading(true);

    try {
      const res = await window.livingAgentAPI.auth.phoneLogin(loginPhone, loginCode);
      // 登录成功：token 已在主进程保存，更新状态
      setHasToken(true);
      setCurrentUser(res.user);
      setShowLoginDialog(false);
    } catch (err: any) {
      const msg = err.message || '';
      if (msg.includes('invalid') || msg.includes('incorrect')) {
        setLoginError('验证码无效或已过期');
      } else if (msg.includes('not found')) {
        setLoginError('用户不存在，请先注册');
      } else {
        setLoginError(msg || '登录失败');
      }
    } finally {
      setLoginLoading(false);
    }
  }

  async function handleLogout() {
    await window.livingAgentAPI.auth.clearToken();
    setHasToken(false);
    setCurrentUser(null);
  }

  async function handleOpenInBrowser() {
    // 打开 Web 前端（不是后端 API 地址）
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
              {hasToken ? `✅ ${currentUser?.name || '已登录'}` : '⚠️ 未登录'}
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
          {/* 基础功能 */}
          <div className="nav-section">
            <div className="nav-section-title">基础功能</div>
            <button
              className={view === 'home' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('home')}
            >
              🏠 概览
            </button>
            <button
              className={view === 'chat' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('chat')}
            >
              💬 部门聊天
            </button>
            <button
              className={view === 'tasks' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('tasks')}
            >
              📋 公共任务栏
            </button>
            <button
              className={view === 'artifacts' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('artifacts')}
            >
              📦 我的产物
            </button>
            <button
              className={view === 'projects' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('projects')}
            >
              📁 项目
            </button>
            <button
              className={view === 'approvals' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('approvals')}
            >
              ✅ 审批
            </button>
            <button
              className={view === 'messages' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('messages')}
            >
              📨 消息
            </button>
            <button
              className={view === 'local-save' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('local-save')}
            >
              💾 本地保存
            </button>
          </div>

          {/* 管理功能（权限控制） */}
          {(currentUser?.role === 'org_admin' || currentUser?.role === 'platform_admin' || currentUser?.accessLevel === 'FULL') && (
            <div className="nav-section">
              <div className="nav-section-title">管理功能</div>
              <button
                className={view === 'admin' ? 'nav-item active' : 'nav-item'}
                onClick={() => handleNav('admin')}
              >
                ⚙️ 企业设置
              </button>
              {currentUser?.role === 'platform_admin' && (
                <button
                  className={view === 'admin' ? 'nav-item active' : 'nav-item'}
                  onClick={() => handleNav('admin')}
                >
                  🔧 平台设置
                </button>
              )}
            </div>
          )}

          {/* 设置 */}
          <div className="nav-section">
            <button
              className={view === 'settings' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('settings')}
            >
              ⚙️ 设置
            </button>
          </div>
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
        {view === 'chat' && (
          <OfficeChatPage
            backendUrl={backend.url}
            hasToken={hasToken}
            currentUser={currentUser}
            onLogin={handleLogin}
          />
        )}
        {view === 'tasks' && <PublicTaskBoardPage />}
        {view === 'artifacts' && <ArtifactsPage />}
        {view === 'projects' && <ProjectsPage backendUrl={backend.url} hasToken={hasToken} />}
        {view === 'approvals' && <ApprovalsPage backendUrl={backend.url} hasToken={hasToken} />}
        {view === 'messages' && <MessagesPage backendUrl={backend.url} hasToken={hasToken} />}
        {view === 'admin' && <AdminPage backendUrl={backend.url} currentUser={currentUser} />}
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

      {/* 登录对话框（手机号 + 验证码，与 frontend 对齐） */}
      {showLoginDialog && (
        <div className="login-dialog-overlay" onClick={() => setShowLoginDialog(false)}>
          <div className="login-dialog" onClick={(e) => e.stopPropagation()}>
            <h2>🔑 登录 Living Agent</h2>
            <p className="login-hint">使用手机号 + 短信验证码登录</p>

            {loginError && (
              <div className="login-error">
                <span>⚠</span> {loginError}
              </div>
            )}

            {/* 测试模式提示 */}
            {testCode && (
              <div className="test-code-hint">
                <span>🧪 测试模式：验证码已自动填入</span>
                <strong>{testCode}</strong>
              </div>
            )}

            <form onSubmit={handlePhoneLogin} className="login-form">
              <div className="login-field">
                <label htmlFor="desktop-login-phone">手机号</label>
                <input
                  id="desktop-login-phone"
                  type="tel"
                  value={loginPhone}
                  onChange={(e) => setLoginPhone(e.target.value)}
                  placeholder="请输入手机号"
                  required
                  autoFocus
                  maxLength={11}
                />
              </div>

              <div className="login-field">
                <label htmlFor="desktop-login-smscode">验证码</label>
                <div style={{ display: 'flex', gap: 8 }}>
                  <input
                    id="desktop-login-smscode"
                    value={loginCode}
                    onChange={(e) => setLoginCode(e.target.value)}
                    placeholder="请输入验证码"
                    required
                    style={{ flex: 1 }}
                    maxLength={6}
                  />
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={handleSendSms}
                    disabled={countdown > 0 || loginLoading || !loginPhone}
                    style={{ whiteSpace: 'nowrap', minWidth: 100 }}
                  >
                    {countdown > 0 ? `${countdown}s` : (loginLoading ? '发送中...' : '发送验证码')}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                className="btn btn-primary login-submit-btn"
                disabled={loginLoading || !loginPhone || !loginCode}
              >
                {loginLoading ? (
                  <span className="login-spinner" />
                ) : (
                  <>登录 →</>
                )}
              </button>

              <button
                type="button"
                className="btn btn-ghost-link"
                onClick={() => setShowLoginDialog(false)}
                style={{ marginTop: 8, width: '100%' }}
              >
                取消
              </button>
            </form>
          </div>
        </div>
      )}
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
        <button className="action-card" onClick={() => onNav('chat')}>
          <span className="icon">💬</span>
          <span className="title">部门聊天</span>
          <span className="desc">通过 WebSocket 直连后端，实时部门消息 / AI 对话</span>
          <span className="badge">核心</span>
        </button>

        <button className="action-card" onClick={() => onNav('tasks')}>
          <span className="icon">📋</span>
          <span className="title">公共任务栏</span>
          <span className="desc">浏览部门公开任务、固定数字员工无法处理时派发的任务</span>
          <span className="badge">主要功能</span>
        </button>

        <button className="action-card" onClick={() => onNav('artifacts')}>
          <span className="icon">📦</span>
          <span className="title">我的产物</span>
          <span className="desc">查看和下载已完成的任务产物</span>
          <span className="badge">推荐</span>
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

/**
 * 我的产物页面 - 展示可下载的产物列表
 */
function ArtifactsPage() {
  const [artifacts, setArtifacts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [downloading, setDownloading] = useState<string | null>(null);

  useEffect(() => {
    void loadArtifacts();
  }, []);

  async function loadArtifacts() {
    setLoading(true);
    try {
      const list = await window.livingAgentAPI.artifacts.myVisible({ page: 0, size: 50 });
      setArtifacts(Array.isArray(list) ? list : []);
    } catch (e) {
      console.warn('[desktop] 加载产物列表失败:', e);
      setArtifacts([]);
    } finally {
      setLoading(false);
    }
  }

  async function handleDownload(artifactId: string, fileName: string) {
    setDownloading(artifactId);
    try {
      const result = await window.livingAgentAPI.artifacts.save(artifactId, fileName || '产物文件');
      if (result.saved) {
        await window.livingAgentAPI.notify('下载完成', `产物已保存到 ${result.path}`);
      }
    } catch (e) {
      console.error('[desktop] 下载产物失败:', e);
      alert('下载失败：' + String(e));
    } finally {
      setDownloading(null);
    }
  }

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>
        加载产物列表...
      </div>
    );
  }

  return (
    <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
      <header style={{ marginBottom: 16 }}>
        <h1>📦 我的产物</h1>
        <p style={{ color: '#666' }}>查看和下载已完成的任务产物</p>
      </header>

      {artifacts.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}>📦</div>
          <p>暂无产物</p>
          <span>完成任务后，产物将显示在这里</span>
        </div>
      ) : (
        <div className="artifact-list">
          {artifacts.map((artifact) => (
            <div key={artifact.id || artifact.artifactId} className="artifact-card"
              style={{
                padding: 16,
                border: '1px solid #e8e8e8',
                borderRadius: 8,
                marginBottom: 12,
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center'
              }}>
              <div>
                <h4 style={{ margin: '0 0 4px' }}>{artifact.name || artifact.fileName || '未命名产物'}</h4>
                <span style={{ fontSize: 12, color: '#999' }}>
                  {artifact.createdAt ? new Date(artifact.createdAt).toLocaleString() : ''}
                  {artifact.size ? ` · ${(artifact.size / 1024).toFixed(1)} KB` : ''}
                </span>
              </div>
              <button
                className="btn btn-primary"
                onClick={() => handleDownload(artifact.id || artifact.artifactId, artifact.name || artifact.fileName)}
                disabled={downloading === (artifact.id || artifact.artifactId)}
              >
                {downloading === (artifact.id || artifact.artifactId) ? '下载中...' : '⬇ 下载'}
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * 项目页面 - 展示项目列表
 */
function ProjectsPage({ backendUrl, hasToken }: { backendUrl: string; hasToken: boolean }) {
  const [projects, setProjects] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!hasToken || !backendUrl) return;
    void loadProjects();
  }, [backendUrl, hasToken]);

  async function loadProjects() {
    setLoading(true);
    try {
      const res = await fetch(`${backendUrl}/api/projects`, {
        headers: { Authorization: `Bearer ${await window.livingAgentAPI.auth.getToken() || ''}` }
      });
      if (res.ok) {
        const data = await res.json();
        setProjects(Array.isArray(data) ? data : data.data || []);
      }
    } catch (e) {
      console.warn('[desktop] 加载项目列表失败:', e);
    } finally {
      setLoading(false);
    }
  }

  if (!hasToken) {
    return <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>请先登录</div>;
  }

  if (loading) {
    return <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>加载项目列表...</div>;
  }

  return (
    <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
      <header style={{ marginBottom: 16 }}>
        <h1>📁 项目</h1>
        <p style={{ color: '#666' }}>查看和管理项目</p>
      </header>
      {projects.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>暂无项目</div>
      ) : (
        <div>
          {projects.map((p) => (
            <div key={p.id} style={{ padding: 16, border: '1px solid #e8e8e8', borderRadius: 8, marginBottom: 12 }}>
              <h4 style={{ margin: '0 0 4px' }}>{p.name}</h4>
              <span style={{ fontSize: 12, color: '#999' }}>{p.status} · {p.phase}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * 审批页面 - 展示待审批列表
 */
function ApprovalsPage({ backendUrl, hasToken }: { backendUrl: string; hasToken: boolean }) {
  const [approvals, setApprovals] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!hasToken || !backendUrl) return;
    void loadApprovals();
  }, [backendUrl, hasToken]);

  async function loadApprovals() {
    setLoading(true);
    try {
      const res = await fetch(`${backendUrl}/api/approvals`, {
        headers: { Authorization: `Bearer ${await window.livingAgentAPI.auth.getToken() || ''}` }
      });
      if (res.ok) {
        const data = await res.json();
        setApprovals(Array.isArray(data) ? data : data.data || []);
      }
    } catch (e) {
      console.warn('[desktop] 加载审批列表失败:', e);
    } finally {
      setLoading(false);
    }
  }

  if (!hasToken) {
    return <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>请先登录</div>;
  }

  if (loading) {
    return <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>加载审批列表...</div>;
  }

  return (
    <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
      <header style={{ marginBottom: 16 }}>
        <h1>✅ 审批</h1>
        <p style={{ color: '#666' }}>查看和处理审批请求</p>
      </header>
      {approvals.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>暂无待审批项</div>
      ) : (
        <div>
          {approvals.map((a) => (
            <div key={a.id} style={{ padding: 16, border: '1px solid #e8e8e8', borderRadius: 8, marginBottom: 12, display: 'flex', justifyContent: 'space-between' }}>
              <div>
                <h4 style={{ margin: '0 0 4px' }}>{a.title || a.type}</h4>
                <span style={{ fontSize: 12, color: '#999' }}>{a.status} · {a.requester_name || ''}</span>
              </div>
              <button className="btn btn-primary" style={{ fontSize: 12 }}>处理</button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * 消息页面 - 展示消息通知
 */
function MessagesPage({ backendUrl, hasToken }: { backendUrl: string; hasToken: boolean }) {
  const [messages, setMessages] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!hasToken || !backendUrl) return;
    void loadMessages();
  }, [backendUrl, hasToken]);

  async function loadMessages() {
    setLoading(true);
    try {
      const res = await fetch(`${backendUrl}/api/messages`, {
        headers: { Authorization: `Bearer ${await window.livingAgentAPI.auth.getToken() || ''}` }
      });
      if (res.ok) {
        const data = await res.json();
        setMessages(Array.isArray(data) ? data : data.data || []);
      }
    } catch (e) {
      console.warn('[desktop] 加载消息列表失败:', e);
    } finally {
      setLoading(false);
    }
  }

  if (!hasToken) {
    return <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>请先登录</div>;
  }

  if (loading) {
    return <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>加载消息...</div>;
  }

  return (
    <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
      <header style={{ marginBottom: 16 }}>
        <h1>📨 消息</h1>
        <p style={{ color: '#666' }}>查看系统消息和通知</p>
      </header>
      {messages.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>暂无消息</div>
      ) : (
        <div>
          {messages.map((m) => (
            <div key={m.id} style={{ padding: 16, border: '1px solid #e8e8e8', borderRadius: 8, marginBottom: 12 }}>
              <h4 style={{ margin: '0 0 4px' }}>{m.title || m.subject}</h4>
              <p style={{ fontSize: 13, color: '#666', margin: '4px 0' }}>{m.content || m.body}</p>
              <span style={{ fontSize: 12, color: '#999' }}>{m.created_at ? new Date(m.created_at).toLocaleString() : ''}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * 管理页面 - 企业设置/平台设置（权限控制）
 */
function AdminPage({ backendUrl, currentUser }: { backendUrl: string; currentUser: DesktopUser | null }) {
  const [tab, setTab] = useState<'enterprise' | 'invitations' | 'platform'>('enterprise');
  const [settings, setSettings] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!backendUrl) return;
    // 先检查权限，有权限才加载
    const isAdmin = currentUser?.role === 'org_admin' || currentUser?.role === 'platform_admin' || currentUser?.accessLevel === 'FULL';
    if (!isAdmin) return;
    void loadSettings();
  }, [backendUrl, tab, currentUser]);

  async function loadSettings() {
    setLoading(true);
    try {
      if (tab === 'enterprise') {
        const res = await fetch(`${backendUrl}/api/enterprise/settings`, {
          headers: { Authorization: `Bearer ${await window.livingAgentAPI.auth.getToken() || ''}` }
        });
        if (res.ok) setSettings(await res.json());
      } else if (tab === 'invitations') {
        const res = await fetch(`${backendUrl}/api/enterprise/invitation-codes`, {
          headers: { Authorization: `Bearer ${await window.livingAgentAPI.auth.getToken() || ''}` }
        });
        if (res.ok) setSettings(await res.json());
      }
    } catch (e) {
      console.warn('[desktop] 加载设置失败:', e);
    } finally {
      setLoading(false);
    }
  }

  const isAdmin = currentUser?.role === 'org_admin' || currentUser?.role === 'platform_admin' || currentUser?.accessLevel === 'FULL';
  const isPlatformAdmin = currentUser?.role === 'platform_admin';

  if (!isAdmin) {
    return (
      <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>
        <h2>权限不足</h2>
        <p>需要管理员权限才能访问此页面</p>
      </div>
    );
  }

  return (
    <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
      <header style={{ marginBottom: 16 }}>
        <h1>⚙️ 管理设置</h1>
        <p style={{ color: '#666' }}>企业配置和系统管理</p>
      </header>

      {/* Tab 切换 */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <button className={`btn ${tab === 'enterprise' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('enterprise')}>企业设置</button>
        <button className={`btn ${tab === 'invitations' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('invitations')}>邀请码</button>
        {isPlatformAdmin && (
          <button className={`btn ${tab === 'platform' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('platform')}>平台设置</button>
        )}
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>加载中...</div>
      ) : (
        <div style={{ padding: 16, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
          <pre style={{ fontSize: 12, whiteSpace: 'pre-wrap' }}>{JSON.stringify(settings, null, 2)}</pre>
        </div>
      )}
    </div>
  );
}
