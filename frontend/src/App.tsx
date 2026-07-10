import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from './stores';
import { Suspense, lazy, useEffect, useState, useRef } from 'react';
import { authApi, enterpriseSettingsApi } from './services/api';
import Toast from './components/Toast';

const Login = lazy(() => import('./pages/Login'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));
const ResetPassword = lazy(() => import('./pages/ResetPassword'));
const CompanySetup = lazy(() => import('./pages/CompanySetup'));
const Layout = lazy(() => import('./pages/Layout'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Plaza = lazy(() => import('./pages/Plaza'));
const AgentDetail = lazy(() => import('./pages/AgentDetail'));
const AgentCreate = lazy(() => import('./pages/AgentCreate'));
const Chat = lazy(() => import('./pages/Chat'));
const Messages = lazy(() => import('./pages/Messages'));
const EnterpriseSettings = lazy(() => import('./pages/EnterpriseSettings'));
const InvitationCodes = lazy(() => import('./pages/InvitationCodes'));
const AdminCompanies = lazy(() => import('./pages/AdminCompanies'));
const SSOEntry = lazy(() => import('./pages/SSOEntry'));
const Projects = lazy(() => import('./pages/Projects'));
const Approvals = lazy(() => import('./pages/Approvals'));
const CodeReview = lazy(() => import('./pages/CodeReview'));
const MemoryBrowser = lazy(() => import('./pages/MemoryBrowser'));
const DepartmentDetail = lazy(() => import('./pages/DepartmentDetail'));
const Neurons = lazy(() => import('./pages/Neurons'));
const Interventions = lazy(() => import('./pages/Interventions'));
const Proactive = lazy(() => import('./pages/Proactive'));
const Reception = lazy(() => import('./pages/Reception'));
const VoicePrintLogin = lazy(() => import('./pages/VoicePrintLogin'));
const Office = lazy(() => import('./pages/Office'));
const Autonomous = lazy(() => import('./pages/Autonomous'));
const FrontDesk = lazy(() => import('./pages/FrontDesk'));
const VoicePrintSettings = lazy(() => import('./pages/VoicePrintSettings'));

function getStoredTenantId() {
    return localStorage.getItem('current_tenant_id') || '';
}

function hasCompany(user: any) {
    const tenantId = user?.tenant_id || user?.tenantId || getStoredTenantId();
    return typeof tenantId === 'string' && tenantId.trim().length > 0;
}

function SetupCompanyRoute({ children }: { children: React.ReactNode }) {
    const token = useAuthStore((s) => s.token);
    const user = useAuthStore((s) => s.user);
    console.log('[SetupCompanyRoute] token:', token, 'user:', user);

    if (!token) return <Navigate to="/login" replace />;
    if (!user) return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-tertiary)' }}>加载中...</div>;
    if (hasCompany(user)) return <Navigate to="/" replace />;
    return <>{children}</>;
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
    const token = useAuthStore((s) => s.token);
    const user = useAuthStore((s) => s.user);
    console.log('[ProtectedRoute] token:', token, 'user:', user);

    if (!token) {
        console.log('[ProtectedRoute] no token, redirecting to /login');
        return <Navigate to="/login" replace />;
    }
    if (!user) {
        console.log('[ProtectedRoute] no user, showing loading');
        return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-tertiary)' }}>加载中...</div>;
    }
    if (!hasCompany(user)) {
        console.log('[ProtectedRoute] no company, redirecting to /setup-company');
        return <Navigate to="/setup-company" replace />;
    }
    console.log('[ProtectedRoute] authenticated, rendering children');
    return <>{children}</>;
}

function HomeRedirect() {
    const user = useAuthStore((s) => s.user);
    console.log('[HomeRedirect] user:', user);

    if (user?.identity === 'INTERNAL_ENTERPRISE' || user?.access_level === 'FULL') return <Navigate to="/dashboard" replace />;
    if (user?.department_code) return <Navigate to={`/departments/${encodeURIComponent(user.department_code)}/overview`} replace />;
    return <Navigate to="/plaza" replace />;
}

/* ─── Notification Bar ─── */
function NotificationBar() {
    const [config, setConfig] = useState<{ enabled: boolean; text: string } | null>(null);
    const [dismissed, setDismissed] = useState(false);
    
    const textRef = useRef<HTMLSpanElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const [isMarquee, setIsMarquee] = useState(false);

    useEffect(() => {
        enterpriseSettingsApi.getSetting('notification_bar', 'public')
            .then((d: any) => { if (d) setConfig(d.value ?? d); })
            .catch(() => { });
    }, []);

    // Check sessionStorage for dismissal (keyed by text so new messages re-show)
    useEffect(() => {
        if (config?.text) {
            const key = `notification_bar_dismissed_${btoa(encodeURIComponent(config.text))}`;
            if (sessionStorage.getItem(key)) setDismissed(true);
        }
    }, [config?.text]);

    // Manage body class: add when visible, remove when hidden or dismissed
    const isVisible = !!config?.enabled && !!config?.text && !dismissed;
    useEffect(() => {
        if (isVisible) {
            document.body.classList.add('has-notification-bar');
        } else {
            document.body.classList.remove('has-notification-bar');
        }
        return () => { document.body.classList.remove('has-notification-bar'); };
    }, [isVisible]);

    // Dynamic marquee if text is too wide
    useEffect(() => {
        if (!isVisible) return;
        const checkWidth = () => {
            if (textRef.current && containerRef.current) {
                // Determine if text is wider than its container
                setIsMarquee(textRef.current.scrollWidth > containerRef.current.clientWidth);
            }
        };
        // Small delay to ensure DOM is fully rendered
        const timer = setTimeout(checkWidth, 100);
        window.addEventListener('resize', checkWidth);
        return () => {
            clearTimeout(timer);
            window.removeEventListener('resize', checkWidth);
        };
    }, [isVisible, config?.text]);

    if (!isVisible) return null;

    const handleDismiss = () => {
        const key = `notification_bar_dismissed_${btoa(encodeURIComponent(config!.text))}`;
        sessionStorage.setItem(key, '1');
        setDismissed(true);
    };

    // Calculate dynamic duration: longer text = longer animation so speed is consistent
    const duration = config ? Math.max(20, config.text.length * 0.2) + 's' : '20s';

    return (
        <div className="notification-bar">
            <div className="notification-bar-inner" ref={containerRef}>
                <span 
                    ref={textRef} 
                    className={`notification-bar-text ${isMarquee ? 'marquee' : ''}`}
                    title={config!.text}
                    style={isMarquee ? { animationDuration: duration } : {}}
                >
                    {config!.text}
                </span>
            </div>
            <button className="notification-bar-close" onClick={handleDismiss} aria-label="Close">✕</button>
        </div>
    );
}

export default function App() {
    const { token, setAuth, user } = useAuthStore();
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        console.log('[App] useEffect running, token:', token, 'user:', user);
        // Initialize theme on app mount (ensures login page gets correct theme)
        const savedTheme = localStorage.getItem('theme') || 'dark';
        document.documentElement.setAttribute('data-theme', savedTheme);

        if (token && user) {
            // Token 和 user 都存在，说明会话有效，直接显示当前页面
            console.log('[App] token && user both exist, showing page');
            setLoading(false);
            return;
        }

        if (token && !user) {
            // 有 token 但无 user 信息（页面刷新场景），调用 /api/auth/me 恢复
            console.log('[App] token exists but no user, calling authApi.me()');
            authApi.me()
                .then((u: any) => {
                    console.log('[App] authApi.me() success:', u);
                    const tenantId = u.tenantId || u.tenant_id || getStoredTenantId();
                    if (tenantId) {
                        localStorage.setItem('current_tenant_id', tenantId);
                    }
                    const mappedUser = {
                        id: u.id,
                        username: u.name || u.username,
                        email: u.email || '',
                        display_name: u.name || u.display_name,
                        role: 'org_admin' as const,
                        tenant_id: tenantId,
                        identity: u.identity,
                        access_level: u.accessLevel || u.access_level,
                        department_code: u.department || undefined,
                        is_active: true,
                        created_at: new Date().toISOString(),
                    };
                    setAuth(mappedUser, token);
                })
                .catch((err) => {
                    console.log('[App] authApi.me() failed:', err);
                    useAuthStore.getState().logout();
                })
                .finally(() => setLoading(false));
        } else {
            // 没有 token，直接显示登录页
            console.log('[App] no token, showing login page');
            setLoading(false);
        }
    }, []);


    if (loading) {
        return (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-tertiary)' }}>
                加载中...
            </div>
        );
    }

    return (
        <>
            <NotificationBar />
            <Toast />
            <Suspense fallback={<div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100vh', color: 'var(--text-tertiary)' }}>加载中...</div>}>
                <Routes>
                    <Route path="/frontdesk" element={<FrontDesk />} />
                    <Route path="/login" element={<Login />} />
                    <Route path="/forgot-password" element={<ForgotPassword />} />
                    <Route path="/reset-password" element={<ResetPassword />} />
                    <Route path="/sso/entry" element={<SSOEntry />} />
                    <Route path="/setup-company" element={<SetupCompanyRoute><CompanySetup /></SetupCompanyRoute>} />
                    <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
                        <Route index element={<HomeRedirect />} />
                        <Route path="dashboard" element={<Dashboard />} />
                        <Route path="plaza" element={<Plaza />} />
                        <Route path="agents/new" element={<AgentCreate />} />
                        <Route path="agents/create" element={<AgentCreate />} />
                        <Route path="agents/:id" element={<AgentDetail />} />
                        <Route path="agents/:id/chat" element={<Chat />} />
                        <Route path="chat" element={<Chat />} />
                        <Route path="messages" element={<Messages />} />
                        <Route path="enterprise" element={<EnterpriseSettings />} />
                        <Route path="documents" element={<Navigate to="/enterprise" replace />} />
                        <Route path="invitations" element={<InvitationCodes />} />
                        <Route path="admin/platform-settings" element={<AdminCompanies />} />
                        {/* Projects and Approvals */}
                        <Route path="projects" element={<Projects />} />
                        <Route path="approvals" element={<Approvals />} />
                        <Route path="code-reviews" element={<CodeReview />} />
                        <Route path="memories" element={<MemoryBrowser />} />
                        {/* Neuron & Intervention */}
                            <Route path="neurons" element={<Neurons />} />
                            <Route path="interventions" element={<Interventions />} />
                            <Route path="proactive" element={<Proactive />} />
                            {/* Reception */}
                            <Route path="reception" element={<Reception />} />
                            {/* Voice & Office */}
                            <Route path="voiceprint" element={<VoicePrintLogin />} />
                            <Route path="voiceprint-settings" element={<VoicePrintSettings />} />
                            <Route path="office" element={<Office />} />
                            {/* Autonomous */}
                            <Route path="autonomous" element={<Autonomous />} />
                            {/* Department routes */}
                            <Route path="departments/:code" element={<Navigate to="overview" replace />} />
                            <Route path="departments/:code/overview" element={<DepartmentDetail />} />
                            <Route path="departments/:code/tasks" element={<DepartmentDetail />} />
                    </Route>
                </Routes>
            </Suspense>
        </>
    );
}
