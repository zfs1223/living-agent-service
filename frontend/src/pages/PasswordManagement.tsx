import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { request } from '../services/apiBase';

interface Employee {
    employeeId: string;
    name: string;
    phone?: string;
    email?: string;
    departmentName?: string;
    position?: string;
    active: boolean;
    identity?: string;
    accessLevel?: string;
    passwordHash?: string;
    passwordChangedAt?: string;
}

/**
 * 密码管理页面（INVITATION_CODE_IMPROVEMENT_PLAN.md §3.3）
 * 董事长/FULL 权限用户可管理所有用户的密码
 */
export default function PasswordManagement() {
    const { t, i18n } = useTranslation();
    const isChinese = i18n.language === 'zh' || i18n.language?.startsWith('zh');

    const [employees, setEmployees] = useState<Employee[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');
    const [toast, setToast] = useState('');
    const [toastType, setToastType] = useState<'success' | 'error'>('success');

    // 重置密码弹窗
    const [resetTarget, setResetTarget] = useState<Employee | null>(null);
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [resetting, setResetting] = useState(false);

    // 加载员工列表
    const loadEmployees = useCallback(async () => {
        setLoading(true);
        try {
            const data = await request<Employee[]>('/enterprise/employees');
            setEmployees(Array.isArray(data) ? data : []);
        } catch {
            try {
                const data = await request<any>('/admin/users');
                setEmployees(Array.isArray(data) ? data : []);
            } catch {
                setEmployees([]);
            }
        }
        setLoading(false);
    }, []);

    useEffect(() => { loadEmployees(); }, [loadEmployees]);

    const showToast = (msg: string, type: 'success' | 'error' = 'success') => {
        setToast(msg);
        setToastType(type);
        setTimeout(() => setToast(''), 3000);
    };

    // 管理员重置密码
    const handleResetPassword = async () => {
        if (!resetTarget) return;
        if (!newPassword || newPassword.length < 6) {
            showToast(isChinese ? '密码长度至少 6 位' : 'Password must be at least 6 characters', 'error');
            return;
        }
        if (newPassword !== confirmPassword) {
            showToast(isChinese ? '两次密码不一致' : 'Passwords do not match', 'error');
            return;
        }

        setResetting(true);
        try {
            // 调用管理员重置密码端点
            await request('/auth/admin/reset-password', {
                method: 'POST',
                body: JSON.stringify({
                    employeeId: resetTarget.employeeId,
                    newPassword,
                }),
            });
            showToast(isChinese ? `已重置 ${resetTarget.name} 的密码` : `Password reset for ${resetTarget.name}`);
            setResetTarget(null);
            setNewPassword('');
            setConfirmPassword('');
            await loadEmployees();
        } catch (err: any) {
            showToast(err.message || (isChinese ? '重置失败' : 'Reset failed'), 'error');
        }
        setResetting(false);
    };

    // 过滤
    const filtered = employees.filter(e => {
        if (!search) return true;
        const q = search.toLowerCase();
        return (
            (e.name?.toLowerCase().includes(q)) ||
            (e.phone?.includes(q)) ||
            (e.email?.toLowerCase().includes(q)) ||
            (e.departmentName?.toLowerCase().includes(q)) ||
            (e.employeeId?.toLowerCase().includes(q))
        );
    });

    // 统计
    const totalEmployees = employees.length;
    const withPassword = employees.filter(e => e.passwordHash).length;
    const withoutPassword = totalEmployees - withPassword;

    const labelStyle: React.CSSProperties = { display: 'block', fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '4px' };
    const fieldStyle: React.CSSProperties = { marginBottom: '12px' };

    return (
        <div className="content-area" style={{ maxWidth: '960px', margin: '0 auto', padding: '32px 24px' }}>
            {/* Toast */}
            {toast && (
                <div style={{
                    position: 'fixed', top: '20px', right: '20px', padding: '10px 20px',
                    borderRadius: '8px', background: toastType === 'error' ? 'var(--error)' : 'var(--success)', color: '#fff',
                    fontSize: '13px', zIndex: 9999,
                }}>{toast}</div>
            )}

            <h2 style={{ fontSize: '20px', fontWeight: 600, marginBottom: '4px' }}>
                {isChinese ? '密码管理' : 'Password Management'}
            </h2>
            <p style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginBottom: '24px' }}>
                {isChinese ? '管理所有用户的登录密码，支持重置和初始设置' : 'Manage login passwords for all users'}
            </p>

            {/* 统计卡片 */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px', marginBottom: '24px' }}>
                <div className="card" style={{ padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--text-primary)' }}>{totalEmployees}</div>
                    <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                        {isChinese ? '总用户数' : 'Total Users'}
                    </div>
                </div>
                <div className="card" style={{ padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--success)' }}>{withPassword}</div>
                    <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                        {isChinese ? '已设密码' : 'With Password'}
                    </div>
                </div>
                <div className="card" style={{ padding: '16px', textAlign: 'center' }}>
                    <div style={{ fontSize: '24px', fontWeight: 700, color: 'var(--warning)' }}>{withoutPassword}</div>
                    <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                        {isChinese ? '未设密码' : 'No Password'}
                    </div>
                </div>
            </div>

            {/* 搜索 */}
            <div style={{ marginBottom: '16px' }}>
                <input className="form-input" placeholder={isChinese ? '搜索姓名、手机号、部门...' : 'Search name, phone, dept...'}
                    value={search} onChange={e => setSearch(e.target.value)}
                    style={{ width: '100%', maxWidth: '400px' }} />
            </div>

            {/* 员工列表 */}
            <div className="card" style={{ padding: '0', overflow: 'hidden' }}>
                {/* 表头 */}
                <div style={{
                    display: 'grid', gridTemplateColumns: '1.5fr 1fr 1fr 0.8fr 0.8fr 80px',
                    gap: '8px', padding: '12px 16px', fontSize: '11px', fontWeight: 600,
                    color: 'var(--text-tertiary)', textTransform: 'uppercase', letterSpacing: '0.05em',
                    borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-tertiary)',
                }}>
                    <div>{isChinese ? '员工' : 'Employee'}</div>
                    <div>{isChinese ? '部门' : 'Department'}</div>
                    <div>{isChinese ? '手机号' : 'Phone'}</div>
                    <div>{isChinese ? '密码状态' : 'Password'}</div>
                    <div>{isChinese ? '上次修改' : 'Last Changed'}</div>
                    <div></div>
                </div>

                {loading && (
                    <div style={{ textAlign: 'center', padding: '32px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                        {isChinese ? '加载中...' : 'Loading...'}
                    </div>
                )}

                {!loading && filtered.length === 0 && (
                    <div style={{ textAlign: 'center', padding: '32px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                        {isChinese ? '无匹配用户' : 'No matching users'}
                    </div>
                )}

                {!loading && filtered.map(emp => (
                    <div key={emp.employeeId} style={{
                        display: 'grid', gridTemplateColumns: '1.5fr 1fr 1fr 0.8fr 0.8fr 80px',
                        gap: '8px', padding: '12px 16px', alignItems: 'center',
                        borderBottom: '1px solid var(--border-subtle)', fontSize: '13px',
                    }}>
                        <div>
                            <div style={{ fontWeight: 500 }}>{emp.name}</div>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{emp.position || emp.employeeId}</div>
                        </div>
                        <div style={{ fontSize: '12px' }}>{emp.departmentName || '-'}</div>
                        <div style={{ fontSize: '12px', fontFamily: 'monospace' }}>{emp.phone || '-'}</div>
                        <div>
                            {emp.passwordHash ? (
                                <span className="badge badge-success" style={{ fontSize: '10px' }}>
                                    {isChinese ? '已设置' : 'Set'}
                                </span>
                            ) : (
                                <span className="badge" style={{ background: 'var(--warning)', color: '#fff', fontSize: '10px' }}>
                                    {isChinese ? '未设置' : 'Not Set'}
                                </span>
                            )}
                        </div>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                            {emp.passwordChangedAt ? new Date(emp.passwordChangedAt).toLocaleDateString() : '-'}
                        </div>
                        <div>
                            <button className="btn btn-secondary" style={{ padding: '2px 8px', fontSize: '10px' }}
                                onClick={() => {
                                    setResetTarget(emp);
                                    setNewPassword('');
                                    setConfirmPassword('');
                                }}>
                                {isChinese ? '重置' : 'Reset'}
                            </button>
                        </div>
                    </div>
                ))}
            </div>

            {/* 重置密码弹窗 */}
            {resetTarget && (
                <div style={{ position: 'fixed', inset: 0, zIndex: 9999, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    onClick={() => setResetTarget(null)}>
                    <div style={{ background: 'var(--bg-primary)', borderRadius: '12px', width: '420px', border: '1px solid var(--border-default)', boxShadow: '0 16px 48px rgba(0,0,0,0.2)' }}
                        onClick={e => e.stopPropagation()}>
                        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <h3 style={{ margin: 0, fontSize: '15px' }}>
                                {isChinese ? '重置密码' : 'Reset Password'}
                            </h3>
                            <button className="btn btn-ghost" onClick={() => setResetTarget(null)} style={{ padding: '4px 8px', fontSize: '16px' }}>x</button>
                        </div>
                        <div style={{ padding: '20px' }}>
                            <div style={{ marginBottom: '16px', padding: '12px', borderRadius: '8px', background: 'var(--bg-tertiary)' }}>
                                <div style={{ fontWeight: 500, fontSize: '14px' }}>{resetTarget.name}</div>
                                <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                                    {resetTarget.phone || resetTarget.email || resetTarget.employeeId}
                                </div>
                                {resetTarget.departmentName && (
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                                        {resetTarget.departmentName} · {resetTarget.position || ''}
                                    </div>
                                )}
                            </div>

                            <div style={fieldStyle}>
                                <label style={labelStyle}>{isChinese ? '新密码' : 'New Password'}</label>
                                <input className="form-input" type="password" style={{ width: '100%' }}
                                    placeholder={isChinese ? '至少 6 位' : 'At least 6 characters'}
                                    value={newPassword} onChange={e => setNewPassword(e.target.value)} minLength={6} />
                            </div>
                            <div style={fieldStyle}>
                                <label style={labelStyle}>{isChinese ? '确认密码' : 'Confirm Password'}</label>
                                <input className="form-input" type="password" style={{ width: '100%' }}
                                    placeholder={isChinese ? '再次输入新密码' : 'Enter new password again'}
                                    value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} />
                            </div>

                            <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '16px' }}>
                                <button className="btn btn-secondary" onClick={() => setResetTarget(null)}>
                                    {isChinese ? '取消' : 'Cancel'}
                                </button>
                                <button className="btn btn-primary" onClick={handleResetPassword} disabled={resetting}>
                                    {resetting ? (isChinese ? '重置中...' : 'Resetting...') : (isChinese ? '确认重置' : 'Reset')}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
