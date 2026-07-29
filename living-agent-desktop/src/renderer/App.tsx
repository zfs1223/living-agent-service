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
import SkillMarketPage from './pages/SkillMarket/SkillMarketPage';
import VitalsDashboard from './pages/Vitals/VitalsDashboard';
import ReceiptReview from './pages/ReceiptReview/ReceiptReview';
import HumanReportPage from './pages/HumanReport/HumanReportPage';
import DeveloperToolsPage from './pages/DeveloperTools/DeveloperToolsPage';
import CustomerValueDashboard from './pages/CustomerValue/CustomerValueDashboard';
import { MeetingPage } from './pages/Meeting/MeetingPage';
import { MeetingRoom } from './pages/Meeting/MeetingRoom';
import { CalendarPage } from './pages/Calendar/CalendarPage';
import { IMPage } from './pages/IM/IMPage'; // P86: IM 功能已融合到 OfficeChatPage，保留文件供参考
import type { ClientInfo, DesktopUser } from '@shared/api-types';

// P83/P84: 新增 'meeting' | 'meeting-room' | 'calendar' 视图，闭环 67 入口
type View = 'home' | 'chat' | 'chat-public' | 'chat-enterprise' | 'frontdesk' | 'tasks' | 'artifacts' | 'projects' | 'approvals' | 'agents' | 'interventions' | 'skills' | 'proactive' | 'plaza' | 'vitals' | 'receipts' | 'human-reports' | 'developer-tools' | 'customer-value' | 'meeting' | 'meeting-room' | 'calendar' | 'im' | 'local-save' | 'settings' | 'admin';

interface BackendStatus {
  status: 'online' | 'offline' | 'unknown';
  url: string;
}

/**
 * P14: 按用户身份（identity）计算需锁定的部门。
 * 判定优先级：先看 identity，再看 accessLevel，最后看 currentUser.department。
 *
 * 权限-部门锁定矩阵（按 UserIdentity.java 8 种身份完整梳理）：
 *   INTERNAL_ENTERPRISE(董事长)         → undefined（不锁定，可自由切换所有部门大脑 + MainBrain + 企业频道）
 *   INTERNAL_ACTIVE(在职员工)           → currentUser.department（锁定到本部门大脑）
 *   INTERNAL_PROBATION(试用期员工)      → currentUser.department（按分配部门锁定，功能权限按 LIMITED 限制）
 *   INTERNAL_DEPARTED(离职员工)         → undefined（无部门入口，仅 /ws/public 闲聊）
 *   EXTERNAL_VISITOR(外来访客)          → undefined（无部门入口，仅 /ws/public 闲聊）
 *   EXTERNAL_CUSTOMER(客户)             → 'cs'（仅客服部）
 *   EXTERNAL_PARTNER(合作伙伴)          → currentUser.department（合作部门）
 *   EXTERNAL_CONTRACTOR(外包人员)       → currentUser.department（外包所在部门）
 *
 * 绑定策略：仅做用户身份绑定，不做设备级部门绑定。
 * 退出登录清空所有锁定状态（仅存在于 React state，不持久化到磁盘）。
 */
function computeForcedDepartment(user: DesktopUser | null): string | undefined {
  if (!user) return undefined;
  switch (user.identity) {
    case 'INTERNAL_ENTERPRISE':
      return undefined;  // 董事长不锁定
    case 'INTERNAL_ACTIVE':
    case 'INTERNAL_PROBATION':
      return user.department || undefined;  // 在职/试用期：锁定到分配部门
    case 'EXTERNAL_CUSTOMER':
      return 'cs';  // 客户：客服部
    case 'EXTERNAL_PARTNER':
    case 'EXTERNAL_CONTRACTOR':
      return user.department || undefined;  // 锁定到合作/外包部门
    case 'INTERNAL_DEPARTED':
    case 'EXTERNAL_VISITOR':
    default:
      return undefined;  // CHAT_ONLY 用户不应进入部门页
  }
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

  // P83: 会议页面状态 — 当前正在加入的会议室 roomName
  const [meetingRoomName, setMeetingRoomName] = useState<string>('');

  // 登录表单状态（与 frontend 对齐：手机号 + 短信验证码）
  const [loginPhone, setLoginPhone] = useState('');
  const [loginCode, setLoginCode] = useState('');
  const [loginLoading, setLoginLoading] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [countdown, setCountdown] = useState(0);
  const [testCode, setTestCode] = useState('');

  // 登录Tab切换：phone(SMS) / password / voiceprint
  const [loginTab, setLoginTab] = useState<'phone' | 'password' | 'voiceprint'>('phone');
  // 密码登录表单状态（支持离线环境登录）
  const [loginPassword, setLoginPassword] = useState('');
  // 多公司选择状态
  const [tenantSelection, setTenantSelection] = useState<{ status: string; phone: string; tenants: Array<{ tenantId: string; departmentName: string; employeeName: string; founder: boolean }> } | null>(null);
  const [pendingLoginData, setPendingLoginData] = useState<{ phone: string; code: string; password: string } | null>(null);
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
    // 打开登录对话框
    setLoginError('');
    setLoginPhone('');
    setLoginCode('');
    setLoginPassword('');
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
      // 检查是否需要选择公司
if (res && (res as any).status === 'tenant_required') {
        setTenantSelection(res as { status: string; phone: string; tenants: { tenantId: string; departmentName: string; employeeName: string; founder: boolean }[] });
        setPendingLoginData({ phone: loginPhone, code: loginCode, password: '' });
        setLoginLoading(false);
        return;
      }
      // 登录成功：token 已在主进程保存，更新状态
      setHasToken(true);
      void refreshAuth();
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

  /** 手机号+密码提交登录（离线环境可用） */
  async function handlePasswordLogin(e: React.FormEvent) {
    e.preventDefault();
    setLoginError('');
    setLoginLoading(true);

    try {
      const res = await window.livingAgentAPI.auth.passwordLogin(loginPhone, loginPassword);
      // 检查是否需要选择公司
      if (res && (res as any).status === 'tenant_required') {
        setTenantSelection(res as { status: string; phone: string; tenants: { tenantId: string; departmentName: string; employeeName: string; founder: boolean }[] });
        setPendingLoginData({ phone: loginPhone, code: '', password: loginPassword });
        setLoginLoading(false);
        return;
      }
      setHasToken(true);
      void refreshAuth();
      setShowLoginDialog(false);
    } catch (err: any) {
      const msg = err.message || '';
      if (msg.includes('invalid') || msg.includes('incorrect') || msg.includes('password')) {
        setLoginError('手机号或密码错误');
      } else if (msg.includes('not found') || msg.includes('not_registered')) {
        setLoginError('用户不存在，请联系管理员添加');
      } else {
        setLoginError(msg || '登录失败');
      }
    } finally {
      setLoginLoading(false);
    }
  }

  /** 多公司选择后登录 */
  async function handleTenantSelect(tenant: { tenantId: string; departmentName: string; employeeName: string; founder: boolean }) {
    setLoginError('');
    setLoginLoading(true);
    try {
      let res: any;
      if (pendingLoginData?.password) {
        res = await window.livingAgentAPI.auth.loginWithTenant(pendingLoginData.phone, pendingLoginData.password, tenant.tenantId);
      } else {
        res = await window.livingAgentAPI.auth.phoneLoginWithTenant(pendingLoginData!.phone, pendingLoginData!.code, tenant.tenantId);
      }
      setHasToken(true);
      setCurrentUser(res.user);
      setShowLoginDialog(false);
      setTenantSelection(null);
      setPendingLoginData(null);
    } catch (err: any) {
      setLoginError(err.message || '登录失败');
    } finally {
      setLoginLoading(false);
    }
  }

  async function handleLogout() {
    await window.livingAgentAPI.auth.clearToken();
    setHasToken(false);
    setCurrentUser(null);  // ← currentUser=null → forcedDepartment=undefined → lockedDepartment=undefined
    setView('home');        // ← P14: 回到首页，避免停留在已锁定的部门页面
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
            {/* P14: CHAT_ONLY 身份（INTERNAL_DEPARTED/EXTERNAL_VISITOR）不显示部门聊天入口 */}
            {currentUser?.accessLevel !== 'CHAT_ONLY' && currentUser?.identity !== 'INTERNAL_DEPARTED' && currentUser?.identity !== 'EXTERNAL_VISITOR' && (
            <button
              className={view === 'chat' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('chat')}
            >
              💬 沟通
            </button>
            )}
            {/* P83 新增：会议入口（闭环 67 入口，对齐 P14 权限） */}
            {currentUser?.accessLevel !== 'CHAT_ONLY' && currentUser?.identity !== 'INTERNAL_DEPARTED' && currentUser?.identity !== 'EXTERNAL_VISITOR' && (
            <button
              className={view === 'meeting' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('meeting')}
            >
              📡 会议
            </button>
            )}
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
              className={view === 'agents' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('agents')}
            >
              🤖 个人助手
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
              className={view === 'human-reports' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('human-reports')}
            >
              📝 我的汇报
            </button>
            {/* P19: 客户价值指标（仅 cs/sales/core 显示） */}
            {(['cs', 'sales', 'core'].includes(currentUser?.department ?? '') || currentUser?.accessLevel === 'FULL' || currentUser?.identity === 'INTERNAL_ENTERPRISE') && (
              <button
              className={view === 'customer-value' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('customer-value')}
            >
              📊 客户价值
            </button>
            )}
            <button
              className={view === 'local-save' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('local-save')}
            >
              💾 本地保存
            </button>
          </div>
          )}

          {/* 管理功能并入企业设置：菜单已按权限显示，无需独立分组 */}
          {(currentUser?.role === 'org_admin' || currentUser?.role === 'platform_admin' || currentUser?.accessLevel === 'FULL') && (
            <button
              className={view === 'admin' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('admin')}
            >
              ⚙️ 企业设置
            </button>
          )}
          {currentUser?.role === 'platform_admin' && (
            <button
              className={view === 'admin' ? 'nav-item active' : 'nav-item'}
              onClick={() => handleNav('admin')}
            >
              🔧 平台设置
            </button>
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
            department={computeForcedDepartment(currentUser) || currentUser?.department || 'tech'}
            lockedDepartment={computeForcedDepartment(currentUser)}
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
          <EnterpriseChannelPage
            backendUrl={backend.url}
            hasToken={hasToken}
            currentUser={currentUser}
            onLogin={handleLogin}
          />
        )}
        {view === 'tasks' && <PublicTaskBoardPage />}
        {view === 'artifacts' && <ArtifactsPage />}
        {view === 'projects' && <ProjectsPage backendUrl={backend.url} hasToken={hasToken} />}
        {view === 'approvals' && <ApprovalsPage hasToken={hasToken} backendUrl={backend.url} />}
        {view === 'agents' && <PersonalAssistantPage hasToken={hasToken} backendUrl={backend.url} currentUser={currentUser} />}
        {view === 'interventions' && <InterventionsPage hasToken={hasToken} />}
        {view === 'skills' && <SkillMarketPage hasToken={hasToken} backendUrl={backend.url} />}
        {view === 'proactive' && <ProactivePage hasToken={hasToken} />}
        {view === 'vitals' && <VitalsDashboard backendUrl={backend.url} hasToken={hasToken} />}
        {view === 'receipts' && <ApprovalsPage hasToken={hasToken} backendUrl={backend.url} initialTab="receipt" />}
        {view === 'human-reports' && <HumanReportPage backendUrl={backend.url} hasToken={hasToken} currentUser={currentUser} />}
        {view === 'customer-value' && <CustomerValueDashboard backendUrl={backend.url} hasToken={hasToken} currentUser={currentUser} />}
        {/* P83: 会议管理页面（闭环 67 入口） */}
        {view === 'meeting' && (
          <MeetingPage
            backendUrl={backend.url}
            hasToken={hasToken}
            currentUser={currentUser}
            onNavigateToRoom={(roomName) => {
              setMeetingRoomName(roomName);
              setView('meeting-room');
            }}
            onLogin={handleLogin}
          />
        )}
        {/* P83: 会议室页面（闭环 67-B 会议执行） */}
        {view === 'meeting-room' && meetingRoomName && (
          <MeetingRoom
            backendUrl={backend.url}
            roomName={meetingRoomName}
            hasToken={hasToken}
            onLeave={() => {
              setMeetingRoomName('');
              setView('meeting');
            }}
          />
        )}
        {/* P84: 日历作为「会议」的日程子视图（闭环 67-D 预约管理 + 日历同步） */}
        {view === 'calendar' && (
          <MeetingPage
            backendUrl={backend.url}
            hasToken={hasToken}
            currentUser={currentUser}
            onNavigateToRoom={(roomName) => {
              setMeetingRoomName(roomName);
              setView('meeting-room');
            }}
            onLogin={handleLogin}
            initialMode="schedule"
          />
        )}
        {/* IM 即时通讯页面 */}
        {/* P86: IM 功能已融合到 OfficeChatPage 的会话列表面板 */}
        {view === 'im' && (
          <IMPage
            backendUrl={backend.url}
            hasToken={hasToken}
            currentUserId={currentUser?.id}
          />
        )}
        {view === 'plaza' && <PlazaPage hasToken={hasToken} />}
        {view === 'admin' && <AdminPage backendUrl={backend.url} hasToken={hasToken} currentUser={currentUser} />}
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
        <div className="login-dialog-overlay" onClick={() => { setShowLoginDialog(false); setTenantSelection(null); setPendingLoginData(null); }}>
          <div className="login-dialog" onClick={(e) => e.stopPropagation()}>
            {/* 多公司选择面板 */}
            {tenantSelection ? (
              <>
                <h2>🏢 选择公司</h2>
                <p style={{ fontSize: 13, color: '#888', margin: '0 0 16px' }}>
                  该手机号关联了多个公司，请选择要登录的公司
                </p>
                {loginError && (
                  <div className="login-error"><span>⚠</span> {loginError}</div>
                )}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {tenantSelection.tenants.map((t) => (
                    <button
                      key={t.tenantId}
                      onClick={() => handleTenantSelect(t)}
                      disabled={loginLoading}
                      style={{
                        display: 'flex', alignItems: 'center', gap: 12, padding: '14px 16px',
                        background: '#1a1a2e', border: '1px solid #333', borderRadius: 10,
                        cursor: loginLoading ? 'default' : 'pointer', textAlign: 'left', width: '100%',
                        transition: 'all 0.15s',
                      }}
                      onMouseEnter={(e) => { e.currentTarget.style.borderColor = '#6366f1'; }}
                      onMouseLeave={(e) => { e.currentTarget.style.borderColor = '#333'; }}
                    >
                      <div style={{ width: 36, height: 36, borderRadius: 8, background: 'linear-gradient(135deg, rgba(99,102,241,0.3), rgba(59,130,246,0.3))', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16, flexShrink: 0 }}>
                        🏢
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontWeight: 600, fontSize: 14, color: '#eee', marginBottom: 2 }}>{t.departmentName}</div>
                        <div style={{ fontSize: 12, color: '#888' }}>
                          {t.employeeName}{t.founder ? ' · 董事长' : ''}
                        </div>
                      </div>
                      <span style={{ color: '#888', fontSize: 16 }}>→</span>
                    </button>
                  ))}
                </div>
                <button
                  onClick={() => { setTenantSelection(null); setPendingLoginData(null); setLoginError(''); }}
                  style={{ marginTop: 12, width: '100%', padding: '8px 0', background: 'none', border: '1px solid #333', borderRadius: 6, color: '#888', cursor: 'pointer', fontSize: 12 }}
                >
                  ← 返回登录
                </button>
              </>
            ) : (
            <>
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
                📱 短信登录
              </button>
              <button
                onClick={() => { setLoginTab('password'); setLoginError(''); setVpError(''); }}
                style={{
                  flex: 1, padding: '10px 0', border: 'none', cursor: 'pointer',
                  background: loginTab === 'password' ? '#6366f1' : 'transparent',
                  color: loginTab === 'password' ? '#fff' : '#888',
                  fontWeight: 600, fontSize: 13, transition: 'all 0.2s',
                }}
              >
                🔑 密码登录
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

            {/* 密码登录 Tab */}
            {loginTab === 'password' && (
            <>
              <p className="login-hint">使用手机号 + 密码登录（离线环境可用）</p>

              {loginError && (
                <div className="login-error">
                  <span>⚠</span> {loginError}
                </div>
              )}

              <form onSubmit={handlePasswordLogin} className="login-form">
                <div className="login-field">
                  <label htmlFor="desktop-login-phone-pwd">手机号</label>
                  <input
                    id="desktop-login-phone-pwd"
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
                  <label htmlFor="desktop-login-password">密码</label>
                  <input
                    id="desktop-login-password"
                    type="password"
                    value={loginPassword}
                    onChange={(e) => setLoginPassword(e.target.value)}
                    placeholder="请输入密码"
                    required
                  />
                </div>

                <button
                  type="submit"
                  className="btn btn-primary login-submit-btn"
                  disabled={loginLoading || !loginPhone || !loginPassword}
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
              onClick={() => { setShowLoginDialog(false); setTenantSelection(null); setPendingLoginData(null); }}
              style={{ marginTop: 8, width: '100%' }}
            >
              取消
            </button>
            </>
            )}
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
        {/* AGENTS.md §5.1：未登录用户可以使用语音（ASR/TTS 在 model_daemon.py 内部加载，无需认证） */}
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
function ApprovalsPage({ hasToken, backendUrl, initialTab = 'all' }: { backendUrl?: string; hasToken: boolean; initialTab?: 'all' | 'receipt' }) {
  const [approvals, setApprovals] = useState<any[]>([]);
  // 审批中心子视图：全部审批 / 执行回执（execution_receipt 类型的审批）
  const [hubTab, setHubTab] = useState<'all' | 'receipt'>(initialTab);
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
      {/* 审批中心子视图切换：全部审批 / 执行回执（execution_receipt 类型的审批） */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 16, borderBottom: '1px solid #e8e8e8' }}>
        <button
          onClick={() => setHubTab('all')}
          style={{ padding: '6px 16px', border: 'none', cursor: 'pointer', background: hubTab === 'all' ? '#6366f1' : 'transparent', color: hubTab === 'all' ? '#fff' : '#666', fontWeight: 600, fontSize: 13, borderRadius: '6px 6px 0 0' }}
        >
          全部审批
        </button>
        <button
          onClick={() => setHubTab('receipt')}
          style={{ padding: '6px 16px', border: 'none', cursor: 'pointer', background: hubTab === 'receipt' ? '#6366f1' : 'transparent', color: hubTab === 'receipt' ? '#fff' : '#666', fontWeight: 600, fontSize: 13, borderRadius: '6px 6px 0 0' }}
        >
          📋 执行回执
        </button>
      </div>
      {hubTab === 'receipt' ? (
        <ReceiptReview backendUrl={backendUrl || ''} hasToken={hasToken} />
      ) : (
        approvals.length === 0 ? (
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
        ))}
      </div>
  );
}

/**
 * 企业频道页面 - 董事长频道，跨部门协调
 * 与部门聊天不同：不显示办公室布局和部门选择器，专注跨部门沟通
 */
function EnterpriseChannelPage({ backendUrl, hasToken, currentUser, onLogin }: { backendUrl: string; hasToken: boolean; currentUser: any; onLogin: () => void }) {
  const [messages, setMessages] = useState<{ role: 'user' | 'assistant' | 'system'; content: string; }[]>([]);
  const [input, setInput] = useState('');
  const [connected, setConnected] = useState(false);
  const [isWaiting, setIsWaiting] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  useEffect(() => {
    if (!backendUrl || !hasToken) return;
    void (async () => {
      const token = await window.livingAgentAPI.auth.getToken();
      const protocol = backendUrl.startsWith('https') ? 'wss' : 'ws';
      const urlBase = backendUrl.replace(/^https?:\/\//, '');
      const ws = new WebSocket(`${protocol}://${urlBase}/ws/enterprise?token=${token || ''}`);
      wsRef.current = ws;

      ws.onopen = () => setConnected(true);
      ws.onclose = () => setConnected(false);
      ws.onerror = () => setConnected(false);
      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'connected' || data.type === 'pong') return;
          if (data.type === 'thinking') { setIsWaiting(true); return; }
          if (data.type === 'done' || data.type === 'chunk' || data.type === 'response') {
            setIsWaiting(false);
            setMessages(prev => [...prev.filter(m => m.content !== '...'), { role: 'assistant', content: data.content || '' }]);
          }
          if (data.type === 'error') { setIsWaiting(false); setMessages(prev => [...prev, { role: 'system', content: data.message || 'Error' }]); }
        } catch { /* ignore */ }
      };
    })();
    return () => { wsRef.current?.close(); };
  }, [backendUrl, hasToken]);

  function handleSend() {
    if (!input.trim() || !wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) return;
    setMessages(prev => [...prev, { role: 'user', content: input.trim() }]);
    wsRef.current.send(JSON.stringify({ type: 'chat', content: input.trim() }));
    setInput('');
    setIsWaiting(true);
  }

  if (!hasToken) {
    return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}><h2>请先登录</h2><button className="btn btn-primary" onClick={onLogin}>登录</button></div>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 16 }}>🌐 企业频道</h2>
          <span style={{ fontSize: 11, color: connected ? '#0a0' : '#a00' }}>{connected ? '在线 · 董事长频道' : '离线'}</span>
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: 8, background: 'var(--bg-secondary, #1a1a2e)', borderRadius: 8, marginBottom: 8 }}>
        {messages.length === 0 && (
          <div style={{ textAlign: 'center', color: '#888', marginTop: 60 }}>
            <div style={{ fontSize: 36, marginBottom: 8 }}>🌐</div>
            <p>企业频道 — 跨部门协调与战略沟通</p>
          </div>
        )}
        {messages.map((msg, i) => (
          <div key={i} style={{ display: 'flex', justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start', marginBottom: 8 }}>
            <div style={{
              maxWidth: '70%', padding: '8px 12px', borderRadius: msg.role === 'user' ? '10px 10px 2px 10px' : '10px 10px 10px 2px',
              background: msg.role === 'user' ? '#6366f1' : msg.role === 'system' ? '#4a1515' : '#2a2a3e',
              color: msg.role === 'user' ? '#fff' : msg.role === 'system' ? '#f88' : '#ddd', fontSize: 13, lineHeight: 1.5,
            }}>
              {msg.content}
            </div>
          </div>
        ))}
        {isWaiting && <div style={{ textAlign: 'center', color: '#888', fontSize: 12 }}>思考中...</div>}
        <div ref={messagesEndRef} />
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <input value={input} onChange={e => setInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') handleSend(); }} placeholder="输入消息..." disabled={isWaiting || !connected} style={{ flex: 1, padding: '8px 12px', borderRadius: 6, border: '1px solid #333', background: '#1a1a2e', color: '#ddd', fontSize: 13, outline: 'none' }} />
        <button onClick={handleSend} disabled={!input.trim() || isWaiting || !connected} style={{ padding: '8px 16px', borderRadius: 6, border: 'none', background: input.trim() && !isWaiting && connected ? '#6366f1' : '#333', color: input.trim() && !isWaiting && connected ? '#fff' : '#666', cursor: 'pointer', fontSize: 13 }}>发送</button>
      </div>
    </div>
  );
}

/**
 * 管理页面 - 企业设置/邀请码（权限控制）
 */
function AdminPage({ backendUrl, hasToken, currentUser }: { backendUrl: string; hasToken: boolean; currentUser: DesktopUser | null }) {
  const [tab, setTab] = useState<'enterprise' | 'add-user' | 'platform' | 'developer-tools'>('enterprise');
  const [settings, setSettings] = useState<any>(null);
  const [invitations, setInvitations] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState('');
  const [newCode, setNewCode] = useState('');
  const [newCodeRole, setNewCodeRole] = useState('employee');
  const [newCodeDept, setNewCodeDept] = useState('');
  const [newCodePhone, setNewCodePhone] = useState('');
  const [newCodeName, setNewCodeName] = useState('');
  const [newCodePosition, setNewCodePosition] = useState('');
  const [newCodePassword, setNewCodePassword] = useState('');
  const [newCodeAccessLevel, setNewCodeAccessLevel] = useState('DEPARTMENT');
  const [departments, setDepartments] = useState<any[]>([]);
  const [companies, setCompanies] = useState<any[]>([]);
  const [selectedCompany, setSelectedCompany] = useState('');
  const [showBatchCreate, setShowBatchCreate] = useState(false);
  const [batchCount, setBatchCount] = useState(5);

  // 部门代码 → 默认 AccessLevel
  const DEPT_DEFAULT_ACCESS: Record<string, string> = {
    tech: 'DEPARTMENT', hr: 'DEPARTMENT', finance: 'DEPARTMENT', sales: 'DEPARTMENT',
    admin: 'DEPARTMENT', cs: 'LIMITED', legal: 'DEPARTMENT', ops: 'DEPARTMENT',
    core: 'DEPARTMENT', cross_dept: 'DEPARTMENT',
  };

  useEffect(() => {
    if (!backendUrl) return;
    const isAdmin = currentUser?.role === 'org_admin' || currentUser?.role === 'platform_admin' || currentUser?.accessLevel === 'FULL';
    const isHR = currentUser?.department === '人力资源' || currentUser?.department === '人力资源部';
    if (!isAdmin && !isHR) return;
    void loadData();
    // 加载部门列表
    getAuthHeaders().then(headers => {
      fetch(`${backendUrl}/api/departments`, { headers })
        .then(r => r.json())
        .then(data => {
          const list = Array.isArray(data.data || data) ? (data.data || data) : [];
          setDepartments(list);
        })
        .catch(() => setDepartments([]));
      // 加载公司列表
      fetch(`${backendUrl}/api/tenants/admin/companies`, { headers })
        .then(r => r.json())
        .then(data => {
          const list = Array.isArray(data.data || data) ? (data.data || data) : [];
          setCompanies(list);
          if (list.length > 0 && !selectedCompany) {
            setSelectedCompany(list[0].id || list[0].tenantId || '');
          }
        })
        .catch(() => setCompanies([]));
    });
  }, [backendUrl, tab, currentUser]);

  async function getAuthHeaders() {
    const token = await window.livingAgentAPI.auth.getToken();
    return { Authorization: `Bearer ${token || ''}`, 'Content-Type': 'application/json' };
  }

  async function loadData() {
    setLoading(true);
    setMsg('');
    try {
      const headers = await getAuthHeaders();
      if (tab === 'enterprise') {
        const res = await fetch(`${backendUrl}/api/enterprise/settings`, { headers });
        if (res.ok) {
          const data = await res.json();
          setSettings(data.data || data);
        }
      } else if (tab === 'add-user') {
        const res = await fetch(`${backendUrl}/api/invitation-codes`, { headers });
        if (res.ok) {
          const data = await res.json();
          setInvitations(Array.isArray(data.data || data) ? (data.data || data) : []);
        }
      }
    } catch (e) {
      console.warn('[desktop] 加载设置失败:', e);
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveEnterprise() {
    if (!settings) return;
    setSaving(true);
    try {
      const headers = await getAuthHeaders();
      const res = await fetch(`${backendUrl}/api/enterprise/settings`, {
        method: 'PUT',
        headers,
        body: JSON.stringify(settings),
      });
      if (res.ok) {
        setMsg('保存成功');
      } else {
        setMsg('保存失败: ' + res.statusText);
      }
    } catch (e: any) {
      setMsg('保存失败: ' + e.message);
    } finally {
      setSaving(false);
    }
  }

  async function handleCreateInvitation() {
    if (!newCodePhone.trim()) { setMsg('请输入手机号'); return; }
    if (!/^1\d{10}$/.test(newCodePhone.trim())) { setMsg('请输入正确的11位手机号'); return; }
    if (!newCodeName.trim()) { setMsg('请输入姓名'); return; }
    if (!newCodeDept) { setMsg('请选择部门'); return; }
    if (!newCodePosition.trim()) { setMsg('请输入职位'); return; }
    if (!newCodePassword.trim()) { setMsg('请输入密码'); return; }
    if (newCodePassword.trim().length < 6) { setMsg('密码至少6位'); return; }
    if (!selectedCompany) { setMsg('请选择公司'); return; }
    setSaving(true);
    try {
      const headers = await getAuthHeaders();
      const deptName = departments.find((d: any) => d.code === newCodeDept)?.name || '';

      // 直接创建员工（无需邀请码两步流程，管理员已填写全部信息含密码）
      const res = await fetch(`${backendUrl}/api/invitation-codes/direct-create`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          tenantId: selectedCompany,
          phone: newCodePhone.trim(),
          name: newCodeName.trim(),
          password: newCodePassword.trim(),
          departmentCode: newCodeDept,
          departmentName: deptName,
          position: newCodePosition.trim(),
          accessLevel: newCodeAccessLevel,
        }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        setMsg('创建用户失败: ' + (data.errorDescription || data.message || res.statusText));
        return;
      }

      setNewCodePhone('');
      setNewCodeName('');
      setNewCodePosition('');
      setNewCodePassword('');
      void loadData();
      setMsg('用户创建成功');
    } catch (e: any) {
      setMsg('创建用户失败: ' + e.message);
    } finally {
      setSaving(false);
    }
  }

  async function handleBatchCreateInvitation() {
    if (!newCodeDept) { setMsg('请选择部门'); return; }
    setSaving(true);
    try {
      const headers = await getAuthHeaders();
      const res = await fetch(`${backendUrl}/api/invitation-codes/batch-create`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          count: batchCount,
          template: {
            departmentCode: newCodeDept,
            departmentName: departments.find((d: any) => d.code === newCodeDept)?.name || '',
            role: newCodeRole,
            accessLevel: newCodeAccessLevel,
          },
        }),
      });
      if (res.ok) {
        setShowBatchCreate(false);
        void loadData();
        setMsg(`成功生成 ${batchCount} 个邀请码`);
      } else {
        const data = await res.json().catch(() => ({}));
        setMsg('批量生成失败: ' + (data.errorDescription || data.message || res.statusText));
      }
    } catch (e: any) {
      setMsg('批量生成失败: ' + e.message);
    } finally {
      setSaving(false);
    }
  }

  async function handleDeleteInvitation(id: number) {
    try {
      const headers = await getAuthHeaders();
      await fetch(`${backendUrl}/api/invitation-codes/${id}`, {
        method: 'DELETE',
        headers,
      });
      void loadData();
    } catch (e: any) {
      setMsg('删除失败: ' + e.message);
    }
  }

  // 部门选择联动
  const handleDeptChange = (code: string) => {
    setNewCodeDept(code);
    setNewCodeAccessLevel(DEPT_DEFAULT_ACCESS[code] || 'DEPARTMENT');
  };

  const isAdmin = currentUser?.role === 'org_admin' || currentUser?.role === 'platform_admin' || currentUser?.accessLevel === 'FULL';
  const isHR = currentUser?.department === '人力资源' || currentUser?.department === '人力资源部';
  const isPlatformAdmin = currentUser?.role === 'platform_admin';
  const canOperate = isAdmin || isHR;

  if (!canOperate) {
    return (
      <div style={{ padding: 48, textAlign: 'center', color: '#999' }}>
        <h2>🔒 权限不足</h2>
        <p>仅董事长和人力资源部可以管理用户</p>
      </div>
    );
  }

  return (
    <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
      <header style={{ marginBottom: 16 }}>
        <h1>⚙️ 管理设置</h1>
        <p style={{ color: '#666' }}>企业配置和系统管理</p>
      </header>

      {msg && (
        <div style={{ padding: '8px 12px', borderRadius: 6, marginBottom: 12, background: msg.includes('成功') ? 'rgba(16,185,129,0.1)' : 'rgba(239,68,68,0.1)', color: msg.includes('成功') ? '#38a169' : '#e53e3e', fontSize: 13 }}>
          {msg}
        </div>
      )}

      {/* Tab 切换 */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <button className={`btn ${tab === 'enterprise' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('enterprise')}>企业设置</button>
        <button className={`btn ${tab === 'add-user' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('add-user')}>新增用户</button>
        {isPlatformAdmin && (
          <button className={`btn ${tab === 'platform' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('platform')}>平台设置</button>
        )}
        <button className={`btn ${tab === 'developer-tools' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setTab('developer-tools')}>🛠️ 开发者工具</button>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>加载中...</div>
      ) : tab === 'enterprise' ? (
        /* 企业设置 - 表单编辑 */
        <div style={{ padding: 16, border: '1px solid #e8e8e8', borderRadius: 8 }}>
          {settings ? (
            <div style={{ display: 'grid', gap: 12 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 500, marginBottom: 4, color: '#333' }}>企业名称</label>
                <input
                  value={settings.name || settings.enterprise_name || ''}
                  onChange={e => setSettings({ ...settings, name: e.target.value, enterprise_name: e.target.value })}
                  style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 500, marginBottom: 4, color: '#333' }}>企业描述</label>
                <textarea
                  value={settings.description || ''}
                  onChange={e => setSettings({ ...settings, description: e.target.value })}
                  style={{ width: '100%', minHeight: 60, padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, fontWeight: 500, marginBottom: 4, color: '#333' }}>默认语言</label>
                <select
                  value={settings.default_language || settings.language || 'zh'}
                  onChange={e => setSettings({ ...settings, default_language: e.target.value, language: e.target.value })}
                  style={{ padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13 }}
                >
                  <option value="zh">中文</option>
                  <option value="en">English</option>
                </select>
              </div>
              <button className="btn btn-primary" onClick={handleSaveEnterprise} disabled={saving} style={{ marginTop: 8 }}>
                {saving ? '保存中...' : '保存设置'}
              </button>
            </div>
          ) : (
            <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>暂无企业设置数据</div>
          )}
        </div>
      ) : tab === 'add-user' ? (
        /* 新增用户 */
        <div>
          <div style={{ padding: 16, border: '1px solid #6366f1', borderRadius: 8, marginBottom: 16, background: 'rgba(99,102,241,0.04)' }}>
            <h3 style={{ margin: '0 0 12px' }}>新增用户</h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              <div>
                <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>公司 *</label>
                <select value={selectedCompany} onChange={e => setSelectedCompany(e.target.value)} style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13 }}>
                  <option value="">选择公司</option>
                  {companies.map((c: any) => <option key={c.id || c.tenantId} value={c.id || c.tenantId}>{c.name || c.companyName || c.tenantId}</option>)}
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>姓名 *</label>
                <input value={newCodeName} onChange={e => setNewCodeName(e.target.value)} placeholder="真实姓名" style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>手机号 *</label>
                <input value={newCodePhone} onChange={e => setNewCodePhone(e.target.value)} placeholder="11位手机号" style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>部门 *</label>
                <select value={newCodeDept} onChange={e => handleDeptChange(e.target.value)} style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13 }}>
                  <option value="">选择部门</option>
                  {departments.map((d: any) => <option key={d.code} value={d.code}>{d.name}</option>)}
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>职位 *</label>
                <input value={newCodePosition} onChange={e => setNewCodePosition(e.target.value)} placeholder="如：软件工程师" style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>密码 *</label>
                <input type="password" value={newCodePassword} onChange={e => setNewCodePassword(e.target.value)} placeholder="至少6位" style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }} />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>角色</label>
                <select value={newCodeRole} onChange={e => setNewCodeRole(e.target.value)} style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13 }}>
                  <option value="employee">员工</option>
                  <option value="department_head">部门负责人</option>
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>
                  权限级别
                  {newCodeDept && <span style={{ marginLeft: 4, fontSize: 10, color: '#6366f1' }}>(按部门默认，可修改)</span>}
                </label>
                <select value={newCodeAccessLevel} onChange={e => setNewCodeAccessLevel(e.target.value)} style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13 }}>
                  <option value="CHAT_ONLY">CHAT_ONLY (0)</option>
                  <option value="LIMITED">LIMITED (1)</option>
                  <option value="DEPARTMENT">DEPARTMENT (2)</option>
                  <option value="FULL">FULL (3)</option>
                </select>
              </div>
              <div style={{ gridColumn: '1 / -1', display: 'flex', gap: 8, alignItems: 'flex-end' }}>
                <button className="btn btn-primary" onClick={handleCreateInvitation} disabled={saving} style={{ flex: 1 }}>
                  {saving ? '创建中...' : '创建用户'}
                </button>
                <button className="btn" style={{ fontSize: 12 }} onClick={() => setShowBatchCreate(!showBatchCreate)}>
                  批量生成邀请码
                </button>
              </div>
            </div>
            {showBatchCreate && (
              <div style={{ marginTop: 12, padding: 12, border: '1px dashed #6366f1', borderRadius: 6, background: 'rgba(99,102,241,0.02)' }}>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <label style={{ fontSize: 12, color: '#666' }}>数量:</label>
                  <input type="number" min={1} max={1000} value={batchCount} onChange={e => setBatchCount(Number(e.target.value))} style={{ width: 80, padding: 6, border: '1px solid #ddd', borderRadius: 6, fontSize: 13 }} />
                  <button className="btn btn-primary" onClick={handleBatchCreateInvitation} disabled={saving}>
                    {saving ? '生成中...' : `生成 ${batchCount} 个邀请码`}
                  </button>
                </div>
              </div>
            )}
          </div>
          <div style={{ display: 'grid', gap: 8 }}>
            <h4 style={{ margin: '8px 0 4px', color: '#666' }}>邀请码记录</h4>
            {invitations.length === 0 && <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>暂无邀请码记录</div>}
            {invitations.map((inv: any) => (
              <div key={inv.code || inv.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 12, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
                <div>
                  <code style={{ fontSize: 15, fontWeight: 600, color: '#6366f1' }}>{inv.code || inv.id}</code>
                  <span style={{ marginLeft: 8, fontSize: 12, color: '#666' }}>
                    {inv.companyName || ''} · {inv.departmentName || inv.department || ''}
                  </span>
                  {inv.phone && <span style={{ marginLeft: 8, fontSize: 11, color: '#888' }}>📱 {inv.phone}</span>}
                  <span style={{ marginLeft: 8, fontSize: 11, color: '#888' }}>{inv.accessLevel || inv.access_level || ''}</span>
                  {inv.status === 'USED' && <span style={{ marginLeft: 8, fontSize: 11, color: '#999' }}>已用完</span>}
                  {inv.status === 'EXPIRED' && <span style={{ marginLeft: 8, fontSize: 11, color: '#999' }}>已过期</span>}
                </div>
                <button className="btn" style={{ fontSize: 12, color: '#e53e3e' }} onClick={() => handleDeleteInvitation(inv.id)}>删除</button>
              </div>
            ))}
          </div>
        </div>
      ) : tab === 'developer-tools' ? (
        <DeveloperToolsPage backendUrl={backendUrl} hasToken={hasToken} currentUser={currentUser} />
      ) : (
        <div style={{ padding: 16, border: '1px solid #e8e8e8', borderRadius: 8, color: '#999', textAlign: 'center' }}>
          平台设置（开发中）
        </div>
      )}
    </div>
  );
}

/** 个人助手创建页 - 仅显示个人助手(origin=personal)，支持新建 */
function PersonalAssistantPage({ hasToken, backendUrl, currentUser }: { hasToken: boolean; backendUrl: string; currentUser: DesktopUser | null }) {
  const [agents, setAgents] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [creating, setCreating] = useState(false);
  const [models, setModels] = useState<any[]>([]);
  const [skills, setSkills] = useState<any[]>([]);
  const [selectedModel, setSelectedModel] = useState('');
  const [selectedSkills, setSelectedSkills] = useState<string[]>([]);

  useEffect(() => {
    if (!hasToken) { setLoading(false); return; }
    window.livingAgentAPI.agent.list()
      .then(list => {
        // 只显示个人助手 (origin=personal)，不显示固定员工和内部员工
        const personal = (list || []).filter((a: any) =>
          a.origin === 'personal' || a.agent_type === 'personal' || a.type === 'personal'
        );
        setAgents(personal);
      })
      .catch(e => setError(e.message || '加载失败'))
      .finally(() => setLoading(false));

    // 加载模型列表
    window.livingAgentAPI.model.list()
      .then(list => setModels(list || []))
      .catch(() => {});

    // 加载技能列表（个人助手只可选择 personalSafe=true 的技能）
    window.livingAgentAPI.skill.list(true)
      .then(list => setSkills(list || []))
      .catch(() => {});
  }, [hasToken]);

  async function handleCreate() {
    if (!newName.trim()) return;
    setCreating(true);
    try {
      await window.livingAgentAPI.agent.create({
        name: newName.trim(),
        role_description: newDesc.trim(),
        agent_type: 'personal',
        department: currentUser?.department || 'default',
        primary_model_id: selectedModel || undefined,
        skill_ids: selectedSkills.length > 0 ? selectedSkills : undefined,
      });
      setNewName('');
      setNewDesc('');
      setSelectedModel('');
      setSelectedSkills([]);
      setShowCreate(false);
      // 重新加载列表
      const list = await window.livingAgentAPI.agent.list();
      const personal = (list || []).filter((a: any) =>
        a.origin === 'personal' || a.agent_type === 'personal' || a.type === 'personal'
      );
      setAgents(personal);
    } catch (e: any) {
      alert(e.message || '创建失败');
    } finally {
      setCreating(false);
    }
  }

  async function handleStart(id: string) {
    try { await window.livingAgentAPI.agent.start(id); setAgents(prev => prev.map(a => a.id === id ? { ...a, status: 'running' } : a)); } catch (e: any) { alert(e.message); }
  }

  async function handleStop(id: string) {
    try { await window.livingAgentAPI.agent.stop(id); setAgents(prev => prev.map(a => a.id === id ? { ...a, status: 'stopped' } : a)); } catch (e: any) { alert(e.message); }
  }

  async function handleDelete(id: string) {
    try {
      await window.livingAgentAPI.agent.delete(id);
      setAgents(prev => prev.filter(a => a.id !== id));
    } catch (e: any) { alert(e.message || '删除失败'); }
  }

  if (!hasToken) {
    return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>请先登录</div>;
  }
  if (loading) return <div style={{ padding: 32, textAlign: 'center', color: '#999' }}>加载中...</div>;
  if (error) return <div style={{ padding: 32, textAlign: 'center', color: '#e53e3e' }}>{error}</div>;

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1>🤖 个人助手</h1>
          <p style={{ color: '#666', fontSize: 13 }}>创建和管理您的个人助手</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreate(!showCreate)}>
          {showCreate ? '取消' : '➕ 新建助手'}
        </button>
      </div>

      {showCreate && (
        <div style={{ padding: 16, border: '1px solid #6366f1', borderRadius: 8, marginBottom: 16, marginTop: 16, background: 'rgba(99,102,241,0.04)' }}>
          <h3 style={{ margin: '0 0 12px' }}>新建个人助手</h3>
          <input
            placeholder="助手名称"
            value={newName}
            onChange={e => setNewName(e.target.value)}
            style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, marginBottom: 8, boxSizing: 'border-box', fontSize: 13 }}
          />
          <textarea
            placeholder="助手描述（可选）"
            value={newDesc}
            onChange={e => setNewDesc(e.target.value)}
            style={{ width: '100%', minHeight: 60, padding: 8, border: '1px solid #ddd', borderRadius: 6, marginBottom: 8, boxSizing: 'border-box', fontSize: 13 }}
          />
          {/* 模型选择 */}
          <div style={{ marginBottom: 8 }}>
            <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>选择模型（可选）</label>
            <select
              value={selectedModel}
              onChange={e => setSelectedModel(e.target.value)}
              style={{ width: '100%', padding: 8, border: '1px solid #ddd', borderRadius: 6, fontSize: 13, boxSizing: 'border-box' }}
            >
              <option value="">默认模型</option>
              {models.map((m: any) => (
                <option key={m.id} value={m.id}>
                  {m.providerId} {m.modelName || m.name || m.id}
                </option>
              ))}
            </select>
          </div>
          {/* 技能选择 */}
          <div style={{ marginBottom: 8 }}>
            <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>选择技能（可多选）</label>
            <div style={{ maxHeight: 120, overflowY: 'auto', border: '1px solid #ddd', borderRadius: 6, padding: 4 }}>
              {skills.length === 0 ? (
                <div style={{ color: '#999', fontSize: 12, padding: 4 }}>暂无可用技能</div>
              ) : (
                skills.map((s: any) => {
                  const skillId = s.id || s.name || s.key;
                  const skillName = s.displayName || s.name || s.title || s.id || s.key;
                  const checked = selectedSkills.includes(skillId);
                  const isPersonalSafe = s.personalSafe === true;
                  return (
                    <label key={skillId} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '3px 4px', fontSize: 12, cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => {
                          setSelectedSkills(prev =>
                            checked ? prev.filter(id => id !== skillId) : [...prev, skillId]
                          );
                        }}
                      />
                      {skillName}
                      {isPersonalSafe && <span style={{ fontSize: 9, color: '#38a169', background: '#f0fff4', border: '1px solid #c6f6d5', padding: '0px 3px', borderRadius: 3, marginLeft: 2 }}>安全</span>}
                    </label>
                  );
                })
              )}
            </div>
          </div>
          <button className="btn btn-primary" onClick={handleCreate} disabled={!newName.trim() || creating}>
            {creating ? '创建中...' : '创建'}
          </button>
        </div>
      )}

      <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
        {agents.length === 0 && !showCreate && (
          <div style={{ color: '#999', textAlign: 'center', padding: 32 }}>
            <div style={{ fontSize: 36, marginBottom: 8 }}>🤖</div>
            <p>暂无个人助手</p>
            <p style={{ fontSize: 11 }}>点击"新建助手"创建您的专属AI助手</p>
          </div>
        )}
        {agents.map(agent => (
          <div key={agent.id} style={{ padding: 12, border: '1px solid #e8e8e8', borderRadius: 8, background: '#fafafa' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ flex: 1 }}>
                <div>
                  <strong>{agent.name || agent.id}</strong>
                  <span style={{ marginLeft: 8, fontSize: 12, color: agent.status === 'running' ? '#38a169' : '#999' }}>
                    {agent.status === 'running' ? '● 运行中' : agent.status === 'idle' ? '● 空闲' : '○ 已停止'}
                  </span>
                  {agent.origin && <span style={{ marginLeft: 8, fontSize: 10, color: '#6366f1', background: '#eef2ff', padding: '1px 6px', borderRadius: 4 }}>个人</span>}
                </div>
                {agent.description && <div style={{ fontSize: 12, color: '#666', marginTop: 2 }}>{agent.description}</div>}
                {/* 模型信息 */}
                {agent.primary_model_id && (
                  <div style={{ fontSize: 11, color: '#888', marginTop: 2 }}>模型: {agent.primary_model_id}</div>
                )}
                {/* 技能标签 */}
                {agent.skills && agent.skills.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 4 }}>
                    {agent.skills.map((skill: string) => (
                      <span key={skill} style={{ fontSize: 10, color: '#38a169', background: '#f0fff4', border: '1px solid #c6f6d5', padding: '1px 6px', borderRadius: 4 }}>{skill}</span>
                    ))}
                  </div>
                )}
              </div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {agent.status !== 'running' && <button className="btn" onClick={() => handleStart(agent.id)}>▶ 启动</button>}
                {agent.status === 'running' && <button className="btn" onClick={() => handleStop(agent.id)}>⏹ 停止</button>}
                <button className="btn" style={{ color: '#e53e3e', fontSize: 11 }} onClick={() => { if (confirm('确定删除此助手？')) handleDelete(agent.id); }}>删除</button>
              </div>
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
