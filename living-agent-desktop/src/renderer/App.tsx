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

type View = 'home' | 'chat' | 'chat-public' | 'chat-enterprise' | 'frontdesk' | 'tasks' | 'artifacts' | 'projects' | 'approvals' | 'messages' | 'agents' | 'interventions' | 'skills' | 'proactive' | 'plaza' | 'local-save' | 'settings' | 'admin';

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

  // 登录Tab切换：phone / voiceprint
  const [loginTab, setLoginTab] = useState<'phone' | 'voiceprint'>('phone');
  const [vpRecording, setVpRecording] = useState(false);
  const [vpLoading, setVpLoading] = useState(false);
  const [vpError, setVpError] = useState('');
  const vpMediaRecorderRef = useRef<MediaRecorder | null>(null);
  const vpChunksRef = useRef<Blob[]>([]);

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

  /** 声纹登录 - 按住录音 */
  async function startVoicePrintRecording() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mr = new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus' });
      vpChunksRef.current = [];
      mr.ondataavailable = (e) => { if (e.data.size > 0) vpChunksRef.current.push(e.data); };
      mr.start();
      vpMediaRecorderRef.current = mr;
      setVpRecording(true);
      setVpError('');
    } catch (err: any) {
      setVpError('无法访问麦克风，请检查权限');
    }
  }

  /** 声纹登录 - 松开停止录音并自动匹配 */
  async function stopVoicePrintRecording() {
    const mr = vpMediaRecorderRef.current;
    if (!mr || mr.state === 'inactive') return;
    setVpRecording(false);
    setVpLoading(true);

    await new Promise<void>((resolve) => {
      mr.onstop = () => resolve();
      mr.stop();
    });

    // 停止麦克风
    mr.stream.getTracks().forEach((t) => t.stop());

    const blob = new Blob(vpChunksRef.current, { type: 'audio/webm' });
    const arrayBuffer = await blob.arrayBuffer();

    try {
      const res = await window.livingAgentAPI.auth.voicePrintLogin(arrayBuffer);
      setHasToken(true);
      setCurrentUser(res.user);
      setShowLoginDialog(false);
    } catch (err: any) {
      setVpError(err.message || '声纹匹配失败，请重试或使用手机登录');
    } finally {
      setVpLoading(false);
    }
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
          {/* 基础功能 - 始终显示 */}
          <div className="nav-section">
            <div className="nav-section-title">基础功能</div>
            <button
              className={view === 'home' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('home')}
            >
              🏠 概览
            </button>
          </div>

          {/* 内部功能 - 仅登录后显示 */}
          {hasToken && (
          <div className="nav-section">
            <div className="nav-section-title">内部功能</div>
            <button
              className={view === 'chat' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('chat')}
            >
              💬 部门聊天
            </button>
            {(currentUser?.accessLevel === 'FULL' || currentUser?.identity === 'INTERNAL_ENTERPRISE') && (
            <button
              className={view === 'chat-enterprise' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('chat-enterprise')}
            >
              🌐 企业频道
            </button>
            )}
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
              className={view === 'agents' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('agents')}
            >
              🤖 智能体
            </button>
            <button
              className={view === 'interventions' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('interventions')}
            >
              🚨 干预
            </button>
            <button
              className={view === 'skills' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('skills')}
            >
              🛠️ 技能
            </button>
            <button
              className={view === 'proactive' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('proactive')}
            >
              💡 主动服务
            </button>
            <button
              className={view === 'plaza' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('plaza')}
            >
              🏛️ 广场
            </button>
            <button
              className={view === 'local-save' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('local-save')}
            >
              💾 本地保存
            </button>
          </div>
          )}

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

          {/* 设置 - 始终显示 */}
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
        {view === 'frontdesk' && (
          <FrontDeskView backendUrl={backend.url} onLogin={handleLogin} />
        )}
        {view === 'home' && (
          <HomeView
            backend={backend}
            hasToken={hasToken}
            clientInfo={clientInfo}
            onNav={handleNav}
            onLogin={handleLogin}
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
        {view === 'chat-public' && (
          <OfficeChatPage
            backendUrl={backend.url}
            hasToken={false}
            currentUser={null}
            onLogin={handleLogin}
            forceChannel="/ws/public"
          />
        )}
        {view === 'chat-enterprise' && (
          <OfficeChatPage
            backendUrl={backend.url}
            hasToken={hasToken}
            currentUser={currentUser}
            onLogin={handleLogin}
            forceChannel="/ws/enterprise"
          />
        )}
        {view === 'tasks' && <PublicTaskBoardPage />}
        {view === 'artifacts' && <ArtifactsPage />}
        {view === 'projects' && <ProjectsPage backendUrl={backend.url} hasToken={hasToken} />}
        {view === 'approvals' && <ApprovalsPage hasToken={hasToken} />}
        {view === 'messages' && <MessagesPage hasToken={hasToken} />}
        {view === 'agents' && <AgentListPage hasToken={hasToken} />}
        {view === 'interventions' && <InterventionsPage hasToken={hasToken} />}
        {view === 'skills' && <SkillsPage hasToken={hasToken} />}
        {view === 'proactive' && <ProactivePage hasToken={hasToken} />}
        {view === 'plaza' && <PlazaPage hasToken={hasToken} />}
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

            {/* Tab 切换 */}
            <div style={{ display: 'flex', gap: 0, marginBottom: 16, borderBottom: '1px solid #333' }}>
              <button
                onClick={() => { setLoginTab('phone'); setLoginError(''); setVpError(''); }}
                style={{
                  flex: 1, padding: '10px 0', border: 'none', cursor: 'pointer',
                  background: loginTab === 'phone' ? '#6366f1' : 'transparent',
                  color: loginTab === 'phone' ? '#fff' : '#888',
                  fontWeight: 600, fontSize: 13, transition: 'all 0.2s',
                }}
              >
                📱 手机登录
              </button>
              <button
                onClick={() => { setLoginTab('voiceprint'); setLoginError(''); setVpError(''); }}
                style={{
                  flex: 1, padding: '10px 0', border: 'none', cursor: 'pointer',
                  background: loginTab === 'voiceprint' ? '#6366f1' : 'transparent',
                  color: loginTab === 'voiceprint' ? '#fff' : '#888',
                  fontWeight: 600, fontSize: 13, transition: 'all 0.2s',
                }}
              >
                🎤 声纹登录
              </button>
            </div>

            {/* 手机号登录 Tab */}
            {loginTab === 'phone' && (
            <>
              <p className="login-hint">使用手机号 + 短信验证码登录</p>

              {loginError && (
                <div className="login-error">
                  <span>⚠</span> {loginError}
                </div>
              )}

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
              </form>
            </>
            )}

            {/* 声纹登录 Tab */}
            {loginTab === 'voiceprint' && (
            <>
              <p className="login-hint">按住录音按钮说话，松开后自动声纹匹配登录</p>

              {vpError && (
                <div className="login-error">
                  <span>⚠</span> {vpError}
                </div>
              )}

              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16, padding: '24px 0' }}>
                <button
                  onMouseDown={startVoicePrintRecording}
                  onMouseUp={stopVoicePrintRecording}
                  onMouseLeave={() => { if (vpRecording) stopVoicePrintRecording(); }}
                  onTouchStart={(e) => { e.preventDefault(); startVoicePrintRecording(); }}
                  onTouchEnd={stopVoicePrintRecording}
                  disabled={vpLoading}
                  style={{
                    width: 80, height: 80, borderRadius: '50%',
                    background: vpRecording ? 'radial-gradient(circle, #ef4444, #991b1b)' : vpLoading ? '#444' : 'radial-gradient(circle, #6366f1, #4338ca)',
                    border: vpRecording ? '3px solid #fca5a5' : '3px solid rgba(99,102,241,0.3)',
                    color: '#fff', fontSize: 32, cursor: vpLoading ? 'not-allowed' : 'pointer',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    transition: 'all 0.2s', boxShadow: vpRecording ? '0 0 24px rgba(239,68,68,0.4)' : '0 0 12px rgba(99,102,241,0.2)',
                  }}
                >
                  {vpLoading ? '⏳' : vpRecording ? '⏺️' : '🎤'}
                </button>
                <div style={{ fontSize: 13, color: vpRecording ? '#ef4444' : '#888', textAlign: 'center' }}>
                  {vpLoading ? '声纹匹配中...' : vpRecording ? '正在录音，松开停止' : '按住说话'}
                </div>
              </div>
            </>
            )}

            <button
              type="button"
              className="btn btn-ghost-link"
              onClick={() => setShowLoginDialog(false)}
              style={{ marginTop: 8, width: '100%' }}
            >
              取消
            </button>
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

/** 前台闲聊视图：无需登录，直接通过 /ws/public 对话（支持文字+语音） */
function FrontDeskView({ backendUrl, onLogin }: { backendUrl: string; onLogin: () => void }) {
  const [messages, setMessages] = useState<{ role: 'user' | 'assistant' | 'system'; content: string; audioUrl?: string }[]>([]);
  const [input, setInput] = useState('');
  const [connected, setConnected] = useState(false);
  const [isWaiting, setIsWaiting] = useState(false);
  const [voiceMode, setVoiceMode] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const anonymousId = useRef(`guest_${Date.now().toString(36)}`);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const streamRef = useRef<MediaStream | null>(null);
  const recordingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const currentAudioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  useEffect(() => {
    if (!backendUrl) return;
    const protocol = backendUrl.startsWith('https') ? 'wss' : 'ws';
    const urlBase = backendUrl.replace(/^https?:\/\//, '');
    const wsUrl = `${protocol}://${urlBase}/ws/public?token=anonymous`;
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => setConnected(true);
    ws.onclose = () => setConnected(false);
    ws.onerror = () => setConnected(false);
    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'connected' || data.type === 'pong' || data.type === 'PONG') return;
        if (data.type === 'thinking') {
          setIsWaiting(true);
          setMessages(prev => [...prev, { role: 'assistant', content: '...' }]);
          return;
        }
        if (data.type === 'done') {
          setIsWaiting(false);
          setMessages(prev => {
            const filtered = prev.filter(m => !(m.role === 'assistant' && m.content === '...'));
            const newMsg: { role: 'assistant'; content: string; audioUrl?: string } = { role: 'assistant', content: data.content || '' };
            if (data.audio) {
              const audioBlob = new Blob([Uint8Array.from(atob(data.audio), c => c.charCodeAt(0))], { type: 'audio/wav' });
              newMsg.audioUrl = URL.createObjectURL(audioBlob);
            }
            return [...filtered, newMsg];
          });
          return;
        }
        if (data.type === 'chunk' || data.type === 'response') {
          setIsWaiting(false);
          setMessages(prev => {
            const filtered = prev.filter(m => !(m.role === 'assistant' && m.content === '...'));
            const last = prev[prev.length - 1];
            if (last && last.role === 'assistant' && last.content !== '...') {
              return [...prev.slice(0, -1), { ...last, content: last.content + (data.content || '') }];
            }
            return [...filtered, { role: 'assistant', content: data.content || '' }];
          });
          return;
        }
        if (data.type === 'asr_result' && data.text) {
          setMessages(prev => [...prev, { role: 'user', content: `🎤 ${data.text}` }]);
          return;
        }
        if (data.type === 'error') {
          setIsWaiting(false);
          setMessages(prev => [...prev, { role: 'system', content: data.message || 'Error' }]);
        }
      } catch { /* ignore */ }
    };

    return () => { ws.close(); };
  }, [backendUrl]);

  useEffect(() => {
    return () => { if (currentAudioRef.current) currentAudioRef.current.pause(); };
  }, []);

  const sendMessage = () => {
    if (!input.trim() || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;
    const content = input.trim();
    setMessages(prev => [...prev, { role: 'user', content }]);
    setInput('');
    setIsWaiting(true);
    wsRef.current.send(JSON.stringify({ type: 'chat', content, userId: anonymousId.current }));
  };

  // 语音录音
  const blobToBase64 = (blob: Blob): Promise<string> => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve((reader.result as string).split(',')[1]);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: { sampleRate: 16000, channelCount: 1, echoCancellation: true, noiseSuppression: true } });
      streamRef.current = stream;
      const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus'
        : MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/wav';
      const mr = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 128000 });
      mediaRecorderRef.current = mr;
      audioChunksRef.current = [];
      mr.ondataavailable = (e) => { if (e.data.size > 0) audioChunksRef.current.push(e.data); };
      mr.onstop = async () => {
        if (audioChunksRef.current.length === 0) return;
        const blob = new Blob(audioChunksRef.current, { type: mr.mimeType || 'audio/webm' });
        audioChunksRef.current = [];
        if (blob.size < 1000) { setMessages(prev => [...prev, { role: 'system', content: '录音时间太短' }]); return; }
        try {
          const b64 = await blobToBase64(blob);
          if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
            setIsWaiting(true);
            wsRef.current.send(JSON.stringify({ type: 'audio_full', audio: b64, userId: anonymousId.current }));
          }
        } catch { setMessages(prev => [...prev, { role: 'system', content: '音频处理失败' }]); }
      };
      mr.start(100);
      setIsRecording(true);
      recordingTimerRef.current = setTimeout(() => { if (mr.state === 'recording') stopRecording(); }, 60000);
    } catch (err: any) {
      const msg = err.name === 'NotAllowedError' ? '麦克风权限被拒绝' : err.name === 'NotFoundError' ? '未找到麦克风' : '无法访问麦克风';
      setMessages(prev => [...prev, { role: 'system', content: msg }]);
    }
  };

  const stopRecording = () => {
    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') mediaRecorderRef.current.stop();
    setIsRecording(false);
    if (recordingTimerRef.current) { clearTimeout(recordingTimerRef.current); recordingTimerRef.current = null; }
    if (streamRef.current) { streamRef.current.getTracks().forEach(t => t.stop()); streamRef.current = null; }
  };

  const playAudio = (url: string) => {
    if (currentAudioRef.current) currentAudioRef.current.pause();
    const a = new Audio(url); currentAudioRef.current = a; a.play().catch(() => undefined);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 16 }}>🤖 智能前台</h2>
          <span style={{ fontSize: 11, color: connected ? '#0a0' : '#a00' }}>
            {connected ? '在线 · 无需登录' : '离线'}
          </span>
        </div>
        <button onClick={onLogin} style={{ fontSize: 12, padding: '4px 12px', borderRadius: 6, background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer' }}>
          登录内部系统
        </button>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: 8, background: 'var(--bg-secondary, #1a1a2e)', borderRadius: 8, marginBottom: 8 }}>
        {messages.length === 0 && (
          <div style={{ textAlign: 'center', color: '#888', marginTop: 60 }}>
            <div style={{ fontSize: 36, marginBottom: 8 }}>💬</div>
            <p>你好！我是智能前台，有什么可以帮你的？</p>
            <p style={{ fontSize: 11, color: '#666' }}>支持文字和语音对话</p>
          </div>
        )}
        {messages.map((msg, i) => (
          <div key={i} style={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start', marginBottom: 8 }}>
            <div style={{
              maxWidth: '70%', padding: '8px 12px', borderRadius: msg.role === 'user' ? '10px 10px 2px 10px' : '10px 10px 10px 2px',
              background: msg.role === 'user' ? '#6366f1' : msg.role === 'system' ? '#4a1515' : '#2a2a3e',
              color: msg.role === 'user' ? '#fff' : msg.role === 'system' ? '#f88' : '#ddd',
              fontSize: 13, lineHeight: 1.5,
            }}>
              {msg.content}
              {msg.audioUrl && <button onClick={() => playAudio(msg.audioUrl!)} style={{ marginTop: 4, fontSize: 10, padding: '2px 6px', borderRadius: 4, border: '1px solid #444', background: '#1a1a2e', color: '#aaa', cursor: 'pointer' }}>🔊 播放</button>}
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      {voiceMode ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, padding: 8 }}>
          <button onMouseDown={startRecording} onMouseUp={stopRecording} onTouchStart={startRecording} onTouchEnd={stopRecording} disabled={isWaiting || !connected} style={{ width: 56, height: 56, borderRadius: '50%', border: isRecording ? '3px solid #f44' : '3px solid #6366f1', background: isRecording ? 'rgba(255,80,80,0.2)' : '#1a1a2e', color: isRecording ? '#f44' : '#6366f1', fontSize: 22, cursor: 'pointer' }}>
            {isRecording ? '⏹' : '🎤'}
          </button>
          <span style={{ fontSize: 10, color: '#888' }}>{isRecording ? '正在录音...松开停止' : isWaiting ? '处理中...' : '按住说话'}</span>
        </div>
      ) : (
        <div style={{ display: 'flex', gap: 8 }}>
          <input value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') sendMessage(); }} placeholder="输入消息..." disabled={isWaiting || !connected} style={{ flex: 1, padding: '8px 12px', borderRadius: 6, border: '1px solid #333', background: '#1a1a2e', color: '#ddd', fontSize: 13, outline: 'none' }} />
          <button onClick={sendMessage} disabled={!input.trim() || isWaiting || !connected} style={{ padding: '8px 16px', borderRadius: 6, border: 'none', background: input.trim() && !isWaiting && connected ? '#6366f1' : '#333', color: input.trim() && !isWaiting && connected ? '#fff' : '#666', cursor: 'pointer', fontSize: 13 }}>发送</button>
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4, fontSize: 9, color: '#555' }}>
        <span>Qwen3 闲聊神经元 · 无需登录</span>
        <button onClick={() => setVoiceMode(!voiceMode)} style={{ background: 'none', border: 'none', color: voiceMode ? '#6366f1' : '#555', fontSize: 9, cursor: 'pointer' }}>
          {voiceMode ? '⌨️ 文字模式' : '🎤 语音模式'}
        </button>
      </div>
    </div>
  );
}

function HomeView({
  backend,
  hasToken,
  clientInfo,
  onNav,
  onLogin
}: {
  backend: BackendStatus;
  hasToken: boolean;
  clientInfo: ClientInfo | null;
  onNav: (v: View) => void;
  onLogin: () => void;
}) {
  // ── Integrated FrontDesk chat (auto-connect) ──
  const [messages, setMessages] = useState<{ role: 'user' | 'assistant' | 'system'; content: string; audioUrl?: string }[]>([]);
  const [input, setInput] = useState('');
  const [connected, setConnected] = useState(false);
  const [isWaiting, setIsWaiting] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const anonymousId = useRef(`guest_${Date.now().toString(36)}`);
  const currentAudioRef = useRef<HTMLAudioElement | null>(null);
  const chatContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  useEffect(() => {
    if (!backend.url) return;
    const protocol = backend.url.startsWith('https') ? 'wss' : 'ws';
    const urlBase = backend.url.replace(/^https?:\/\//, '');
    const wsUrl = `${protocol}://${urlBase}/ws/public?token=anonymous`;
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;
    ws.onopen = () => setConnected(true);
    ws.onclose = () => setConnected(false);
    ws.onerror = () => setConnected(false);
    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'connected' || data.type === 'pong' || data.type === 'PONG') return;
        if (data.type === 'thinking') { setIsWaiting(true); setMessages(prev => [...prev, { role: 'assistant', content: '...' }]); return; }
        if (data.type === 'done') {
          setIsWaiting(false);
          setMessages(prev => {
            const filtered = prev.filter(m => !(m.role === 'assistant' && m.content === '...'));
            const newMsg: { role: 'assistant'; content: string; audioUrl?: string } = { role: 'assistant', content: data.content || '' };
            if (data.audio) {
              const audioBlob = new Blob([Uint8Array.from(atob(data.audio), c => c.charCodeAt(0))], { type: 'audio/wav' });
              newMsg.audioUrl = URL.createObjectURL(audioBlob);
            }
            return [...filtered, newMsg];
          });
          return;
        }
        if (data.type === 'chunk' || data.type === 'response') {
          setIsWaiting(false);
          setMessages(prev => {
            const filtered = prev.filter(m => !(m.role === 'assistant' && m.content === '...'));
            const last = prev[prev.length - 1];
            if (last && last.role === 'assistant' && last.content !== '...') {
              return [...prev.slice(0, -1), { ...last, content: last.content + (data.content || '') }];
            }
            return [...filtered, { role: 'assistant', content: data.content || '' }];
          });
          return;
        }
        if (data.type === 'asr_result' && data.text) { setMessages(prev => [...prev, { role: 'user', content: `🎤 ${data.text}` }]); return; }
        if (data.type === 'error') { setIsWaiting(false); setMessages(prev => [...prev, { role: 'system', content: data.message || 'Error' }]); }
      } catch { /* ignore */ }
    };
    return () => { ws.close(); };
  }, [backend.url]);

  useEffect(() => {
    return () => { if (currentAudioRef.current) currentAudioRef.current.pause(); };
  }, []);

  useEffect(() => {
    if (chatContainerRef.current) chatContainerRef.current.scrollTop = chatContainerRef.current.scrollHeight;
  }, [messages]);

  const sendMessage = () => {
    if (!input.trim() || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;
    const content = input.trim();
    setMessages(prev => [...prev, { role: 'user', content }]);
    setInput('');
    setIsWaiting(true);
    wsRef.current.send(JSON.stringify({ type: 'chat', content, userId: anonymousId.current }));
  };

  const playAudio = (url: string) => {
    if (currentAudioRef.current) currentAudioRef.current.pause();
    const a = new Audio(url); currentAudioRef.current = a; a.play().catch(() => undefined);
  };

  return (
    <div className="home-view">
      <div className="home-hero">
        <h1>欢迎使用 Living Agent 桌面端</h1>
        <p>
          桌面端是<span style={{ color: '#fff', background: 'rgba(255,255,255,0.2)', padding: '0 6px', borderRadius: 3 }}>独立安装</span>的客户端应用，
          与服务端 Web 端通过 HTTP API 通信，不共享代码。
        </p>
        {!hasToken && (
          <button onClick={onLogin} style={{ marginTop: 12, padding: '8px 24px', borderRadius: 8, background: '#6366f1', color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 600, fontSize: 13 }}>
            🔑 登录内部系统
          </button>
        )}
        {hasToken && (
          <span style={{ marginTop: 12, display: 'inline-block', padding: '6px 16px', borderRadius: 8, background: 'rgba(47,229,141,0.12)', color: '#2fe58d', fontSize: 13 }}>
            ✓ 已登录内部系统
          </span>
        )}
      </div>

      {/* ── Integrated Chat Panel ── */}
      <div style={{ margin: '0 0 16px', borderRadius: 12, background: 'var(--bg-secondary, #1a1a2e)', border: '1px solid var(--border-subtle, #2a2a3e)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 16px', borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>💬</span>
            <strong style={{ fontSize: 14 }}>智能前台</strong>
          </div>
          <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 999, background: connected ? 'rgba(47,229,141,0.12)' : 'rgba(255,80,80,0.12)', color: connected ? '#2fe58d' : '#f44' }}>
            {connected ? '在线' : '离线'}
          </span>
        </div>

        <div ref={chatContainerRef} style={{ flex: 1, overflowY: 'auto', padding: '12px 16px', minHeight: 160, maxHeight: 280 }}>
          {messages.length === 0 && (
            <div style={{ textAlign: 'center', color: '#888', padding: '32px 0' }}>
              <div style={{ fontSize: 32, marginBottom: 8 }}>💬</div>
              <p style={{ margin: 0, fontSize: 13 }}>你好！我是智能前台，有什么可以帮你的？</p>
              <p style={{ fontSize: 11, color: '#555', margin: '4px 0 0' }}>支持文字和语音对话 · 无需登录</p>
            </div>
          )}
          {messages.map((msg, i) => (
            <div key={i} style={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start', marginBottom: 8 }}>
              <div style={{
                maxWidth: '75%', padding: '8px 12px', borderRadius: msg.role === 'user' ? '10px 10px 2px 10px' : '10px 10px 10px 2px',
                background: msg.role === 'user' ? '#6366f1' : msg.role === 'system' ? '#4a1515' : '#2a2a3e',
                color: msg.role === 'user' ? '#fff' : msg.role === 'system' ? '#f88' : '#ddd',
                fontSize: 13, lineHeight: 1.5, wordBreak: 'break-word',
              }}>
                {msg.content}
                {msg.audioUrl && <button onClick={() => playAudio(msg.audioUrl!)} style={{ marginTop: 4, fontSize: 10, padding: '2px 6px', borderRadius: 4, border: '1px solid #444', background: '#1a1a2e', color: '#aaa', cursor: 'pointer' }}>🔊 播放</button>}
              </div>
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>

        <div style={{ display: 'flex', gap: 8, padding: '10px 16px', borderTop: '1px solid rgba(255,255,255,0.06)' }}>
          <input
            value={input} onChange={e => setInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') sendMessage(); }}
            placeholder="输入消息..." disabled={isWaiting || !connected}
            style={{ flex: 1, padding: '8px 12px', borderRadius: 6, border: '1px solid #333', background: '#1a1a2e', color: '#ddd', fontSize: 13, outline: 'none' }}
          />
          <button onClick={sendMessage} disabled={!input.trim() || isWaiting || !connected}
            style={{ padding: '8px 16px', borderRadius: 6, border: 'none', background: input.trim() && !isWaiting && connected ? '#6366f1' : '#333', color: input.trim() && !isWaiting && connected ? '#fff' : '#666', cursor: 'pointer', fontSize: 13 }}>
            发送
          </button>
        </div>
        <div style={{ padding: '0 16px 6px', fontSize: 9, color: '#555' }}>Qwen3 闲聊神经元 · 无需登录</div>
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
        {/* 内部系统入口 - 仅未登录时显示 */}
        {!hasToken && (
        <button className="action-card" onClick={onLogin}>
          <span className="icon">🏢</span>
          <span className="title">内部系统</span>
          <span className="desc">登录后可访问部门聊天、任务、项目、审批等内部功能</span>
          <span className="badge">需登录</span>
        </button>
        )}
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
function ApprovalsPage({ hasToken }: { backendUrl?: string; hasToken: boolean }) {
  const [approvals, setApprovals] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<any>(null);
  const [comment, setComment] = useState('');
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    if (!hasToken) return;
    void loadApprovals();
  }, [hasToken]);

  async function loadApprovals() {
    setLoading(true);
    try {
      const data = await window.livingAgentAPI.approval.list();
      setApprovals(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('[desktop] 加载审批列表失败:', e);
    } finally {
      setLoading(false);
    }
  }

  async function handleSelect(id: string) {
    if (selectedId === id) { setSelectedId(null); setDetail(null); return; }
    setSelectedId(id);
    try {
      const d = await window.livingAgentAPI.approval.detail(id);
      setDetail(d);
    } catch (e) {
      console.warn('[desktop] 加载审批详情失败:', e);
    }
  }

  async function handleAction(action: 'approve' | 'reject' | 'cancel', stepId?: string) {
    if (!selectedId) return;
    setActionLoading(true);
    try {
      if (action === 'approve' && stepId) {
        await window.livingAgentAPI.approval.approve(selectedId, stepId, comment || undefined);
      } else if (action === 'reject' && stepId) {
        await window.livingAgentAPI.approval.reject(selectedId, stepId, comment || undefined);
      } else if (action === 'cancel') {
        await window.livingAgentAPI.approval.cancel(selectedId);
      }
      setComment('');
      setSelectedId(null);
      setDetail(null);
      await loadApprovals();
    } catch (e: any) {
      console.warn('[desktop] 审批操作失败:', e);
    } finally {
      setActionLoading(false);
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
            <div key={a.id}>
              <div style={{ padding: 16, border: '1px solid #e8e8e8', borderRadius: 8, marginBottom: 4, display: 'flex', justifyContent: 'space-between', cursor: 'pointer' }} onClick={() => handleSelect(a.id)}>
                <div>
                  <h4 style={{ margin: '0 0 4px' }}>{a.title || a.type}</h4>
                  <span style={{ fontSize: 12, color: '#999' }}>{a.status} · {a.requester_name || ''}</span>
                </div>
                <button className="btn btn-primary" style={{ fontSize: 12 }} onClick={(e) => { e.stopPropagation(); handleSelect(a.id); }}>处理</button>
              </div>
              {selectedId === a.id && detail && (
                <div style={{ padding: 16, border: '1px solid #d0d0d0', borderRadius: 8, marginBottom: 12, background: '#fafafa' }}>
                  <h4 style={{ margin: '0 0 8px' }}>审批详情</h4>
                  {detail.steps && detail.steps.length > 0 && (
                    <div style={{ marginBottom: 8 }}>
                      {detail.steps.map((s: any, i: number) => (
                        <div key={i} style={{ padding: '4px 0', fontSize: 13, display: 'flex', gap: 8 }}>
                          <span style={{ color: s.status === 'APPROVED' ? 'green' : s.status === 'REJECTED' ? 'red' : '#666' }}>{s.status}</span>
                          <span>{s.approver_name || s.approver_id || `步骤 ${i + 1}`}</span>
                          {s.comment && <span style={{ color: '#999' }}>- {s.comment}</span>}
                        </div>
                      ))}
                    </div>
                  )}
                  <div style={{ marginBottom: 8 }}>
                    <textarea
                      placeholder="审批意见（可选）"
                      value={comment}
                      onChange={(e) => setComment(e.target.value)}
                      style={{ width: '100%', minHeight: 48, padding: 8, borderRadius: 6, border: '1px solid #ddd', fontSize: 13, boxSizing: 'border-box' }}
                    />
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    {detail.current_step_id && (
                      <>
                        <button className="btn btn-primary" style={{ fontSize: 12 }} disabled={actionLoading} onClick={() => handleAction('approve', detail.current_step_id)}>通过</button>
                        <button className="btn btn-danger" style={{ fontSize: 12, background: '#e74c3c', color: '#fff', border: 'none', borderRadius: 6, padding: '4px 12px' }} disabled={actionLoading} onClick={() => handleAction('reject', detail.current_step_id)}>驳回</button>
                      </>
                    )}
                    <button className="btn" style={{ fontSize: 12, background: '#eee', border: 'none', borderRadius: 6, padding: '4px 12px' }} disabled={actionLoading} onClick={() => handleAction('cancel')}>取消审批</button>
                  </div>
                </div>
              )}
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
function MessagesPage({ hasToken }: { backendUrl?: string; hasToken: boolean }) {
  const [messages, setMessages] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    if (!hasToken) return;
    void loadMessages();
    void loadUnread();
  }, [hasToken]);

  async function loadMessages() {
    setLoading(true);
    try {
      const data = await window.livingAgentAPI.message.list(50);
      setMessages(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('[desktop] 加载消息列表失败:', e);
    } finally {
      setLoading(false);
    }
  }

  async function loadUnread() {
    try {
      const count = await window.livingAgentAPI.message.unreadCount();
      setUnreadCount(count);
    } catch { /* ignore */ }
  }

  async function handleMarkRead(id: string) {
    try {
      await window.livingAgentAPI.message.markRead(id);
      setMessages(prev => prev.map(m => m.id === id ? { ...m, read: true } : m));
      await loadUnread();
    } catch (e) {
      console.warn('[desktop] 标记已读失败:', e);
    }
  }

  async function handleMarkAllRead() {
    try {
      await window.livingAgentAPI.message.markAllRead();
      setMessages(prev => prev.map(m => ({ ...m, read: true })));
      setUnreadCount(0);
    } catch (e) {
      console.warn('[desktop] 全部已读失败:', e);
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
      <header style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1>📨 消息 {unreadCount > 0 && <span style={{ fontSize: 14, background: '#e74c3c', color: '#fff', borderRadius: 10, padding: '2px 8px' }}>{unreadCount}</span>}</h1>
          <p style={{ color: '#666' }}>查看系统消息和通知</p>
        </div>
        {unreadCount > 0 && (
          <button className="btn" style={{ fontSize: 12, background: '#eee', border: 'none', borderRadius: 6, padding: '6px 12px' }} onClick={handleMarkAllRead}>全部已读</button>
        )}
      </header>
      {messages.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>暂无消息</div>
      ) : (
        <div>
          {messages.map((m) => (
            <div key={m.id} style={{ padding: 16, border: '1px solid #e8e8e8', borderRadius: 8, marginBottom: 12, background: m.read ? 'transparent' : '#f0f7ff' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1 }}>
                  <h4 style={{ margin: '0 0 4px' }}>{m.title || m.subject}</h4>
                  <p style={{ fontSize: 13, color: '#666', margin: '4px 0' }}>{m.content || m.body}</p>
                  <span style={{ fontSize: 12, color: '#999' }}>{m.created_at ? new Date(m.created_at).toLocaleString() : ''}</span>
                </div>
                {!m.read && (
                  <button className="btn" style={{ fontSize: 11, background: '#eee', border: 'none', borderRadius: 6, padding: '4px 8px', marginLeft: 8, whiteSpace: 'nowrap' }} onClick={() => handleMarkRead(m.id)}>已读</button>
                )}
              </div>
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

/** Agent 管理页 (P1-1) */
function AgentListPage({ hasToken }: { hasToken: boolean }) {
  const [agents, setAgents] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!hasToken) { setLoading(false); return; }
    window.livingAgentAPI.agent.list()
      .then(setAgents)
      .catch(e => setError(e.message || '加载失败'))
      .finally(() => setLoading(false));
  }, [hasToken]);

  async function handleStart(id: string) {
    try { await window.livingAgentAPI.agent.start(id); setAgents(prev => prev.map(a => a.id === id ? { ...a, status: 'running' } : a)); } catch (e: any) { alert(e.message); }
  }

  async function handleStop(id: string) {
    try { await window.livingAgentAPI.agent.stop(id); setAgents(prev => prev.map(a => a.id === id ? { ...a, status: 'stopped' } : a)); } catch (e: any) { alert(e.message); }
  }

  if (!hasToken) {
    return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>请先登录</div>;
  }
  if (loading) return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>加载中...</div>;
  if (error) return <div style={{ padding: 32, textAlign: 'center', color: '#e53e3e' }}>{error}</div>;

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <h1>🤖 智能体管理</h1>
      <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
        {agents.length === 0 && <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>暂无智能体</div>}
        {agents.map(agent => (
          <div key={agent.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 12, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
            <div>
              <strong>{agent.name || agent.id}</strong>
              <span style={{ marginLeft: 8, fontSize: 12, color: agent.status === 'running' ? '#38a169' : '#999' }}>
                {agent.status === 'running' ? '● 运行中' : agent.status === 'idle' ? '● 空闲' : '○ 已停止'}
              </span>
              {agent.role_description && <div style={{ fontSize: 12, color: '#666', marginTop: 2 }}>{agent.role_description}</div>}
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              {agent.status !== 'running' && <button className="btn" onClick={() => handleStart(agent.id)}>▶ 启动</button>}
              {agent.status === 'running' && <button className="btn" onClick={() => handleStop(agent.id)}>⏹ 停止</button>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/** 干预决策页 (P1-2) */
function InterventionsPage({ hasToken }: { hasToken: boolean }) {
  const [interventions, setInterventions] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [comment, setComment] = useState<Record<string, string>>({});

  function loadList() {
    if (!hasToken) { setLoading(false); return; }
    setLoading(true);
    window.livingAgentAPI.intervention.list()
      .then(setInterventions)
      .catch(e => setError(e.message || '加载失败'))
      .finally(() => setLoading(false));
  }

  useEffect(loadList, [hasToken]);

  async function handleRespond(id: string, action: string) {
    try {
      await window.livingAgentAPI.intervention.respond(id, action, comment[id] || '');
      loadList();
    } catch (e: any) { alert(e.message); }
  }

  async function handleEscalate(id: string) {
    try {
      await window.livingAgentAPI.intervention.escalate(id, comment[id] || '需要升级处理');
      loadList();
    } catch (e: any) { alert(e.message); }
  }

  if (!hasToken) {
    return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>请先登录</div>;
  }
  if (loading) return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>加载中...</div>;
  if (error) return <div style={{ padding: 32, textAlign: 'center', color: '#e53e3e' }}>{error}</div>;

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <h1>🚨 人工干预</h1>
      <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
        {interventions.length === 0 && <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>暂无待干预事项</div>}
        {interventions.map(iv => (
          <div key={iv.id} style={{ padding: 12, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <strong>{iv.title || iv.type || iv.id}</strong>
                <span style={{ marginLeft: 8, fontSize: 12, color: iv.status === 'pending' ? '#d69e2e' : '#38a169' }}>
                  {iv.status === 'pending' ? '⏳ 待处理' : '✓ 已处理'}
                </span>
                {iv.description && <div style={{ fontSize: 12, color: '#666', marginTop: 4 }}>{iv.description}</div>}
              </div>
            </div>
            {iv.status === 'pending' && (
              <div style={{ marginTop: 8, display: 'flex', gap: 8, alignItems: 'center' }}>
                <input
                  placeholder="评论..."
                  value={comment[iv.id] || ''}
                  onChange={e => setComment(prev => ({ ...prev, [iv.id]: e.target.value }))}
                  style={{ flex: 1, padding: '4px 8px', border: '1px solid #ddd', borderRadius: 4, fontSize: 13 }}
                />
                <button className="btn" onClick={() => handleRespond(iv.id, 'approve')}>✅ 批准</button>
                <button className="btn" onClick={() => handleRespond(iv.id, 'reject')}>❌ 拒绝</button>
                <button className="btn" onClick={() => handleEscalate(iv.id)}>⬆ 升级</button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

/** 技能管理页 (P1-3) */
function SkillsPage({ hasToken }: { hasToken: boolean }) {
  const [skills, setSkills] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!hasToken) { setLoading(false); return; }
    window.livingAgentAPI.skill.list()
      .then(setSkills)
      .catch(e => setError(e.message || '加载失败'))
      .finally(() => setLoading(false));
  }, [hasToken]);

  if (!hasToken) return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>请先登录</div>;
  if (loading) return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>加载中...</div>;
  if (error) return <div style={{ padding: 32, textAlign: 'center', color: '#e53e3e' }}>{error}</div>;

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <h1>🛠️ 技能</h1>
      <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
        {skills.length === 0 && <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>暂无技能</div>}
        {skills.map(skill => (
          <div key={skill.id || skill.name} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 12, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
            <div>
              <strong>{skill.name || skill.id}</strong>
              {skill.description && <div style={{ fontSize: 12, color: '#666', marginTop: 2 }}>{skill.description}</div>}
              {skill.category && <span style={{ fontSize: 11, color: '#999', marginLeft: 8 }}>📁 {skill.category}</span>}
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <span style={{ fontSize: 12, color: skill.enabled !== false ? '#38a169' : '#999' }}>
                {skill.enabled !== false ? '● 已启用' : '○ 未启用'}
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/** 主动服务页 (P1-4) */
function ProactivePage({ hasToken }: { hasToken: boolean }) {
  const [digest, setDigest] = useState<any>(null);
  const [habits, setHabits] = useState<any[]>([]);
  const [notifications, setNotifications] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<'digest' | 'habits' | 'notifications'>('digest');

  useEffect(() => {
    if (!hasToken) { setLoading(false); return; }
    Promise.all([
      window.livingAgentAPI.proactive.digest().catch(() => null),
      window.livingAgentAPI.proactive.habits().catch(() => []),
      window.livingAgentAPI.proactive.notifications().catch(() => []),
    ]).then(([d, h, n]) => {
      setDigest(d);
      setHabits(Array.isArray(h) ? h : []);
      setNotifications(Array.isArray(n) ? n : []);
    }).catch(e => setError(e.message || '加载失败'))
      .finally(() => setLoading(false));
  }, [hasToken]);

  if (!hasToken) return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>请先登录</div>;
  if (loading) return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>加载中...</div>;
  if (error) return <div style={{ padding: 32, textAlign: 'center', color: '#e53e3e' }}>{error}</div>;

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <h1>💡 主动服务</h1>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16, marginTop: 8 }}>
        {(['digest', 'habits', 'notifications'] as const).map(t => (
          <button key={t} className={`btn ${tab === t ? 'btn-primary' : ''}`} onClick={() => setTab(t)}>
            {t === 'digest' ? '📋 摘要' : t === 'habits' ? '🔄 习惯' : '🔔 通知'}
          </button>
        ))}
      </div>
      {tab === 'digest' && (
        <div style={{ padding: 16, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
          {digest ? <pre style={{ fontSize: 12, whiteSpace: 'pre-wrap' }}>{JSON.stringify(digest, null, 2)}</pre> : <div style={{ color: '#999' }}>暂无摘要</div>}
        </div>
      )}
      {tab === 'habits' && (
        <div style={{ display: 'grid', gap: 8 }}>
          {habits.length === 0 && <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>暂无习惯数据</div>}
          {habits.map((h, i) => (
            <div key={i} style={{ padding: 12, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
              <strong>{h.name || h.type || `习惯 ${i + 1}`}</strong>
              {h.description && <div style={{ fontSize: 12, color: '#666', marginTop: 2 }}>{h.description}</div>}
            </div>
          ))}
        </div>
      )}
      {tab === 'notifications' && (
        <div style={{ display: 'grid', gap: 8 }}>
          {notifications.length === 0 && <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>暂无通知</div>}
          {notifications.map((n, i) => (
            <div key={i} style={{ padding: 12, border: '1px solid #e8e8e8', borderRadius: 8, background: n.read ? '#fafafa' : '#f0f7ff' }}>
              <strong>{n.title || n.type || `通知 ${i + 1}`}</strong>
              {n.content && <div style={{ fontSize: 12, color: '#666', marginTop: 2 }}>{n.content}</div>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** 广场页 (P1-5) */
function PlazaPage({ hasToken }: { hasToken: boolean }) {
  const [posts, setPosts] = useState<any[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newContent, setNewContent] = useState('');

  function loadPosts() {
    if (!hasToken) { setLoading(false); return; }
    setLoading(true);
    Promise.all([
      window.livingAgentAPI.plaza.posts(),
      window.livingAgentAPI.plaza.stats().catch(() => null),
    ]).then(([p, s]) => {
      setPosts(Array.isArray(p) ? p : []);
      setStats(s);
    }).catch(e => setError(e.message || '加载失败'))
      .finally(() => setLoading(false));
  }

  useEffect(loadPosts, [hasToken]);

  async function handleLike(postId: string) {
    try { await window.livingAgentAPI.plaza.like(postId); loadPosts(); } catch (e: any) { alert(e.message); }
  }

  async function handleCreate() {
    if (!newTitle || !newContent) return;
    try {
      await window.livingAgentAPI.plaza.create({ title: newTitle, content: newContent });
      setNewTitle('');
      setNewContent('');
      setShowCreate(false);
      loadPosts();
    } catch (e: any) { alert(e.message); }
  }

  if (!hasToken) return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>请先登录</div>;
  if (loading) return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>加载中...</div>;
  if (error) return <div style={{ padding: 32, textAlign: 'center', color: '#e53e3e' }}>{error}</div>;

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>🏛️ 广场</h1>
        <button className="btn btn-primary" onClick={() => setShowCreate(!showCreate)}>
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
          <button className="btn btn-primary" onClick={handleCreate} disabled={!newTitle || !newContent}>发布</button>
        </div>
      )}
      <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
        {posts.length === 0 && <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>暂无帖子</div>}
        {posts.map(post => (
          <div key={post.id} style={{ padding: 12, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div style={{ flex: 1 }}>
                <strong>{post.title}</strong>
                {post.author_name && <span style={{ fontSize: 12, color: '#999', marginLeft: 8 }}>by {post.author_name}</span>}
                {post.content && <div style={{ fontSize: 13, color: '#666', marginTop: 4 }}>{post.content.length > 200 ? post.content.slice(0, 200) + '...' : post.content}</div>}
              </div>
              <button className="btn" style={{ fontSize: 12, whiteSpace: 'nowrap' }} onClick={() => handleLike(post.id)}>
                👍 {post.like_count ?? post.likes ?? 0}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
