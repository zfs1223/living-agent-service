/**
 * User Management — admin page to view and manage user quotas and roles.
 */
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../stores';
import { fetchJson, chairmanApi } from '../services/api';

interface UserInfo {
    id: string;
    username: string;
    email: string;
    display_name: string;
    role: string;
    is_active: boolean;
    access_level?: number;
    feishu_open_id?: string;
    created_at?: string;
    source?: string;
    department?: string;
    department_name?: string;
    title?: string;
    position?: string;
    phone?: string;
    avatar_url?: string;
    tenant_id?: string;
    is_founder?: boolean;
    join_date?: string;
    employee_type?: string;  // HUMAN / DIGITAL
    origin?: string;         // fixed / personal / human / evolved
    identity_raw?: string;   // 原始 identity 值（如 INTERNAL_ENTERPRISE）
    is_digital?: boolean;    // 快速判断字段
}

const PAGE_SIZE = 15;

export default function UserManagement() {
    const { t, i18n } = useTranslation();
    const { user: currentUser, setUser } = useAuthStore();
    const isChinese = i18n.language === 'zh' || i18n.language?.startsWith('zh');

    const [users, setUsers] = useState<UserInfo[]>([]);
    const [loading, setLoading] = useState(true);
    const [editingUserId, setEditingUserId] = useState<string | null>(null);
    const [editForm, setEditForm] = useState({
        access_level: 0,
    });
    const [saving, setSaving] = useState(false);
    const [toast, setToast] = useState('');
    const [changingAccessUserId, setChangingAccessUserId] = useState<string | null>(null);
    const [changingRoleUserId, setChangingRoleUserId] = useState<string | null>(null);
    const [detailUser, setDetailUser] = useState<UserInfo | null>(null);

    // ── Add User state ──
    const [showAddUser, setShowAddUser] = useState(false);
    const [addForm, setAddForm] = useState({
        phone: '',
        name: '',
        email: '',
        tenantId: '',
        department: '',
        position: '',
        accessLevel: 'DEPARTMENT',
        initialPassword: '',
    });
    const [adding, setAdding] = useState(false);

    // 公司列表（从数据库获取）
    const [companies, setCompanies] = useState<any[]>([]);
    // 部门列表（从 API 获取）
    const [deptOptions, setDeptOptions] = useState<any[]>([]);

    // 部门代码 → 默认 AccessLevel
    const DEPT_DEFAULT_ACCESS: Record<string, string> = {
        tech: 'DEPARTMENT', hr: 'DEPARTMENT', finance: 'DEPARTMENT', sales: 'DEPARTMENT',
        admin: 'DEPARTMENT', cs: 'LIMITED', legal: 'DEPARTMENT', ops: 'DEPARTMENT',
        core: 'DEPARTMENT', cross_dept: 'DEPARTMENT',
    };

    // 加载公司列表
    useEffect(() => {
        fetchJson('/tenants/admin/companies')
            .then((data: any) => {
                const list = Array.isArray(data.data || data) ? (data.data || data) : [];
                setCompanies(list);
                // 默认选中第一个公司
                if (list.length > 0 && !addForm.tenantId) {
                    setAddForm(f => ({ ...f, tenantId: list[0].id || list[0].tenantId || '' }));
                }
            })
            .catch(() => setCompanies([]));
    }, []);

    // 加载部门列表（从 API 获取）
    useEffect(() => {
        fetchJson('/departments')
            .then((data: any) => {
                const list = Array.isArray(data.data || data) ? (data.data || data) : [];
                setDeptOptions(list);
            })
            .catch(() => setDeptOptions([]));
    }, []);

    // 部门选项优先从 API 获取，否则用硬编码兜底
    const DEPARTMENTS = deptOptions.length > 0
        ? deptOptions.map((d: any) => ({ value: d.id || d.code, label: d.name }))
        : [
            { value: 'tech', label: t('userMgmt.deptTech', '技术部') },
            { value: 'hr', label: t('userMgmt.deptHr', '人力资源') },
            { value: 'finance', label: t('userMgmt.deptFinance', '财务部') },
            { value: 'sales', label: t('userMgmt.deptSales', '销售部') },
            { value: 'admin', label: t('userMgmt.deptAdmin', '行政部') },
            { value: 'cs', label: t('userMgmt.deptCs', '客服部') },
            { value: 'legal', label: t('userMgmt.deptLegal', '法务部') },
            { value: 'ops', label: t('userMgmt.deptOps', '运营部') },
          ];

    // Access level definitions (matching backend: CHAT_ONLY=0, LIMITED=1, DEPARTMENT=2, FULL=3)
    const ACCESS_LEVELS = [
        { value: 0, label: t('userMgmt.accessChatOnly', '仅闲聊'), desc: t('userMgmt.accessChatOnlyDesc', '仅可访问闲聊神经元') },
        { value: 1, label: t('userMgmt.accessLimited', '受限'), desc: t('userMgmt.accessLimitedDesc', '可访问AdminBrain、CsBrain') },
        { value: 2, label: t('userMgmt.accessDepartment', '部门级'), desc: t('userMgmt.accessDepartmentDesc', '可访问本部门大脑+ToolNeuron') },
        { value: 3, label: t('userMgmt.accessFull', '完全'), desc: t('userMgmt.accessFullDesc', '可访问所有大脑+MainBrain') },
    ];

    // Search, sort & pagination
    const [searchQuery, setSearchQuery] = useState('');
    const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');
    const [page, setPage] = useState(1);

    // 数字员工过滤(默认隐藏:数字员工由主脑/部门大脑管理,不参与用户管理)
    const [showDigital, setShowDigital] = useState(false);

    const loadUsers = async () => {
        setLoading(true);
        try {
            // 使用 /api/enterprise/employees (EnterpriseApiController) 返回 identity/accessLevel
            const res: any = await fetchJson('/enterprise/employees');
            const rawData = res?.data ?? res;
            if (Array.isArray(rawData)) {
                // 映射后端字段到前端 UserInfo 格式
                // identity: INTERNAL_ENTERPRISE(董事长) / INTERNAL_ACTIVE(在职) / INTERNAL_PROBATION(试用) 等
                // accessLevel: FULL / DEPARTMENT / LIMITED / CHAT_ONLY
                const mapped: UserInfo[] = rawData.map((item: any) => {
                    // 1) 先判断员工类型:数字员工(由主脑/部门大脑管理)与人类用户分离
                    const employeeType = item.employeeType || item.employee_type || 'HUMAN';
                    const isDigital = employeeType === 'DIGITAL' || employeeType === 'FIXED';
                    const origin = item.origin || '';

                    // 2) 数字员工:统一标记 digital_employee,不参与人类用户角色推断
                    //    数字员工的"管理员"误标问题在此解决
                    if (isDigital) {
                        return {
                            id: item.employeeId || item.id || '',
                            username: item.name || '',
                            email: item.email || '',
                            display_name: item.name || '',
                            role: 'digital_employee',
                            is_active: item.active === true || item.isActive === true,
                            access_level: item.accessLevel === 'FULL' ? 3 : item.accessLevel === 'DEPARTMENT' ? 2 : item.accessLevel === 'LIMITED' ? 1 : 0,
                            feishu_open_id: undefined,
                            created_at: item.createdAt || item.created_at,
                            source: 'digital',
                            department: item.department || '',
                            department_name: item.departmentName || '',
                            title: item.title || item.position || '',
                            position: item.position || '',
                            phone: item.phone || '',
                            avatar_url: item.avatarUrl || '',
                            tenant_id: item.tenantId || '',
                            is_founder: item.isFounder || false,
                            join_date: item.joinDate || '',
                            employee_type: 'DIGITAL',
                            origin: origin,
                            identity_raw: item.identity || '',
                            is_digital: true,
                        };
                    }

                    // 3) 人类用户:根据 identity / accessLevel 推断角色
                    const identity = item.identity || 'INTERNAL_ACTIVE';
                    let roleDisplay = 'member';
                    if (identity === 'INTERNAL_ENTERPRISE') {
                        roleDisplay = 'chairman';  // 董事长
                    } else if (identity === 'INTERNAL_PROBATION') {
                        roleDisplay = 'probation'; // 试用期
                    } else if (item.accessLevel === 'FULL') {
                        roleDisplay = 'platform_admin'; // 系统管理员
                    } else if (item.accessLevel === 'DEPARTMENT') {
                        roleDisplay = 'org_admin'; // 部门管理员
                    }
                    return {
                        id: item.employeeId || item.id || '',
                        username: item.name || '',
                        email: item.email || '',
                        display_name: item.name || '',
                        role: roleDisplay,
                        is_active: item.active === true || item.isActive === true,
                        access_level: item.accessLevel === 'FULL' ? 3 : item.accessLevel === 'DEPARTMENT' ? 2 : item.accessLevel === 'LIMITED' ? 1 : 0,
                        feishu_open_id: item.feishu_open_id,
                        created_at: item.createdAt || item.created_at,
                        source: item.department ? (item.department.includes('feishu') ? 'feishu' : 'registered') : 'registered',
                        department: item.department || '',
                        department_name: item.departmentName || '',
                        title: item.title || '',
                        position: item.position || '',
                        phone: item.phone || '',
                        avatar_url: item.avatarUrl || '',
                        tenant_id: item.tenantId || '',
                        is_founder: item.isFounder || false,
                        join_date: item.joinDate || '',
                        employee_type: 'HUMAN',
                        origin: origin,
                        identity_raw: identity,
                        is_digital: false,
                    };
                });
                setUsers(mapped);
            } else {
                setUsers([]);
            }
        } catch (e) {
            console.error('Failed to load users', e);
            setUsers([]);
        }
        setLoading(false);
    };

    useEffect(() => { loadUsers(); }, []);

    const startEdit = (user: UserInfo) => {
        setEditingUserId(user.id);
        setEditForm({
            access_level: user.access_level ?? 0,
        });
    };

    const handleSave = async () => {
        if (!editingUserId) return;
        setSaving(true);
        try {
            const accessLevelMap: Record<number, string> = { 0: 'CHAT_ONLY', 1: 'LIMITED', 2: 'DEPARTMENT', 3: 'FULL' };
            const accessLevel = accessLevelMap[editForm.access_level] || 'CHAT_ONLY';
            await fetchJson(`/agents/${encodeURIComponent(editingUserId)}/access-level`, {
                method: 'PATCH',
                body: JSON.stringify({ accessLevel }),
            });
            setToast(isChinese ? '权限已更新' : 'Access level updated');
            setTimeout(() => setToast(''), 2000);
            setEditingUserId(null);
            loadUsers();
        } catch (e: any) {
            setToast(`❌ ${e.message}`);
            setTimeout(() => setToast(''), 3000);
        }
        setSaving(false);
    };

    // ── Role change handler ──
    const handleRoleChange = async (userId: string, newRole: string) => {
        setChangingRoleUserId(userId);
        try {
            await fetchJson(`/users/${userId}/role`, {
                method: 'PATCH',
                body: JSON.stringify({ role: newRole }),
            });
            setToast(t('userMgmt.quotaUpdated'));
            setTimeout(() => setToast(''), 2000);
            // If changed own role, update auth store
            if (userId === currentUser?.id) {
                setUser({ ...currentUser, role: newRole as any });
            }
            loadUsers();
        } catch (e: any) {
            const detail = (() => { try { return JSON.parse(e.message)?.detail; } catch { return e.message; } })();
            setToast(`Error: ${detail || e.message}`);
            setTimeout(() => setToast(''), 4000);
        }
        setChangingRoleUserId(null);
    };

    // ── Access level change handler ──
    const handleAccessLevelChange = async (userId: string, accessLevel: number) => {
        setChangingAccessUserId(userId);
        try {
            await chairmanApi.updateEmployeeAccess(userId, String(accessLevel));
            setToast(t('userMgmt.accessLevelUpdated', '权限级别已更新'));
            setTimeout(() => setToast(''), 2000);
            loadUsers();
            // Update detail modal if open
            if (detailUser && detailUser.id === userId) {
                setDetailUser({ ...detailUser, access_level: accessLevel });
            }
        } catch (e: any) {
            const detail = (() => { try { return JSON.parse(e.message)?.detail; } catch { return e.message; } })();
            setToast(`Error: ${detail || e.message}`);
            setTimeout(() => setToast(''), 4000);
        }
        setChangingAccessUserId(null);
    };

    // ── Add User handler ──
    // 合并邀请码功能：添加用户时自动生成邀请码，用户通过邀请码注册
    const handleAddUser = async () => {
        if (!addForm.phone.trim()) {
            setToast(t('userMgmt.phoneRequired', '请输入手机号'));
            setTimeout(() => setToast(''), 2000);
            return;
        }
        // 简单校验手机号格式（11位数字）
        if (!/^1\d{10}$/.test(addForm.phone.trim())) {
            setToast(t('userMgmt.phoneInvalid', '请输入正确的11位手机号'));
            setTimeout(() => setToast(''), 2000);
            return;
        }
        if (!addForm.name.trim()) {
            setToast('请输入姓名');
            setTimeout(() => setToast(''), 2000);
            return;
        }
        if (!addForm.tenantId) {
            setToast('请选择公司');
            setTimeout(() => setToast(''), 2000);
            return;
        }
        if (!addForm.department) {
            setToast('请选择部门');
            setTimeout(() => setToast(''), 2000);
            return;
        }
        if (!addForm.position.trim()) {
            setToast('请输入职位');
            setTimeout(() => setToast(''), 2000);
            return;
        }
        setAdding(true);
        try {
            // 方式1：通过邀请码创建用户（生成邀请码 → 自动注册）
            const dept = DEPARTMENTS.find(d => d.value === addForm.department);
            const deptName = dept?.label || addForm.department;

            // 1) 创建邀请码
            const inviteRes: any = await fetchJson('/invitation-codes/create', {
                method: 'POST',
                body: JSON.stringify({
                    tenantId: addForm.tenantId,
                    departmentCode: addForm.department,
                    departmentName: deptName,
                    phone: addForm.phone.trim(),
                    initialPassword: addForm.initialPassword || undefined,
                    accessLevel: addForm.accessLevel,
                }),
            });

            // 2) 使用邀请码注册用户
            const inviteCode = inviteRes?.data?.code || inviteRes?.code;
            if (inviteCode) {
                await fetchJson('/invitation-codes/use', {
                    method: 'POST',
                    body: JSON.stringify({
                        code: inviteCode,
                        phone: addForm.phone.trim(),
                        name: addForm.name.trim(),
                        email: addForm.email.trim() || undefined,
                        password: addForm.initialPassword || undefined,
                        position: addForm.position.trim(),
                    }),
                });
            }

            setToast(t('userMgmt.userCreated', '用户创建成功'));
            setTimeout(() => setToast(''), 2000);
            setShowAddUser(false);
            setAddForm({ phone: '', name: '', email: '', tenantId: companies[0]?.id || companies[0]?.tenantId || '', department: '', position: '', accessLevel: 'DEPARTMENT', initialPassword: '' });
            loadUsers();
        } catch (e: any) {
            const detail = (() => { try { return JSON.parse(e.message)?.detail || JSON.parse(e.message)?.errorDescription; } catch { return e.message; } })();
            setToast(`❌ ${detail || e.message}`);
            setTimeout(() => setToast(''), 4000);
        }
        setAdding(false);
    };

    // ── Delete User handler ──
    const handleDeleteUser = async (userId: string, userName: string) => {
        if (!confirm(t('userMgmt.confirmDelete', { name: userName }))) return;
        try {
            await fetchJson(`/enterprise/employees/${userId}`, { method: 'DELETE' });
            setToast(t('userMgmt.userDeleted', '用户已删除'));
            setTimeout(() => setToast(''), 2000);
            setDetailUser(null);
            loadUsers();
        } catch (e: any) {
            const detail = (() => { try { return JSON.parse(e.message)?.detail; } catch { return e.message; } })();
            setToast(`❌ ${detail || e.message}`);
            setTimeout(() => setToast(''), 4000);
        }
    };

    // Role label & styling helpers
    const roleBadge = (role: string) => {
        const styles: Record<string, { bg: string; color: string; label: string; labelZh: string }> = {
            platform_admin:    { bg: 'rgba(239,68,68,0.12)',  color: '#ef4444', label: 'Platform Admin',   labelZh: '平台管理员' },
            org_admin:         { bg: 'rgba(168,85,247,0.12)', color: '#a855f7', label: 'Admin',            labelZh: '部门管理员' },
            digital_employee:  { bg: 'rgba(34,211,238,0.12)', color: '#22d3ee', label: 'Digital Employee', labelZh: '数字员工' },
        };
        const s = styles[role];
        if (!s) return null;
        return (
            <span style={{ marginLeft: '6px', fontSize: '10px', background: s.bg, color: s.color, borderRadius: '4px', padding: '1px 6px', fontWeight: 500 }}>
                {i18n.language?.startsWith('zh') ? s.labelZh : s.label}
            </span>
        );
    };

    // 统一角色显示映射(避免硬编码判断)
    const getRoleLabel = (role: string): string => {
        const labels: Record<string, string> = {
            chairman:          t('userMgmt.chairman', '董事长'),
            platform_admin:    t('userMgmt.platformAdmin', '平台管理员'),
            org_admin:         t('userMgmt.admin', '部门管理员'),
            member:            t('userMgmt.member', '成员'),
            probation:         t('userMgmt.probation', '试用期'),
            digital_employee:  t('userMgmt.digitalEmployee', '数字员工'),
        };
        return labels[role] || t('userMgmt.member', '成员');
    };

    const formatDate = (iso?: string) => {
        if (!iso) return '-';
        const d = new Date(iso);
        return d.toLocaleString(i18n.language?.startsWith('zh') ? 'zh-CN' : 'en-US', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
    };

    // Search filter + digital employee filter
    const filtered = users
        // 1) 数字员工过滤:默认隐藏(showDigital=false)
        //    数字员工由主脑/部门大脑管理,不参与人类用户管理
        .filter(u => showDigital || !u.is_digital)
        // 2) 搜索过滤
        .filter(u => {
            if (!searchQuery.trim()) return true;
            const q = searchQuery.toLowerCase();
            return (u.username?.toLowerCase().includes(q))
                || (u.display_name?.toLowerCase().includes(q))
                || (u.email?.toLowerCase().includes(q));
        });

    // Sort
    const sorted = [...filtered].sort((a, b) => {
        const ta = a.created_at ? new Date(a.created_at).getTime() : 0;
        const tb = b.created_at ? new Date(b.created_at).getTime() : 0;
        return sortOrder === 'asc' ? ta - tb : tb - ta;
    });

    // Paginate
    const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
    const paged = sorted.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

    const toggleSort = () => {
        setSortOrder(o => o === 'asc' ? 'desc' : 'asc');
        setPage(1);
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div style={{
                borderRadius: '24px',
                padding: '22px',
                background: 'linear-gradient(135deg, rgba(16,185,129,0.12), rgba(12,18,28,0.84) 48%, rgba(5,6,10,0.96))',
                border: '1px solid rgba(255,255,255,0.08)',
                boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '18px', alignItems: 'flex-start' }}>
                    <div style={{ maxWidth: '760px' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 10px', borderRadius: '999px', background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)', fontSize: '12px', marginBottom: '14px' }}>
                            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--success)', boxShadow: '0 0 18px rgba(16,185,129,0.85)' }} />
                            管理员用户中心
                        </div>
                        <h1 style={{ fontSize: '28px', fontWeight: 700, margin: 0, letterSpacing: '-0.04em', color: 'var(--text-primary)' }}>用户管理</h1>
                        <p style={{ margin: '10px 0 0', color: 'var(--text-secondary)', fontSize: '13px', lineHeight: 1.75, maxWidth: '68ch' }}>
                            管理用户角色、配额、生命周期和访问来源。
                        </p>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '10px', minWidth: '320px' }}>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>用户</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px' }}>{users.filter(u => !u.is_digital).length}</div>
                        </div>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>管理员</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px' }}>{users.filter(u => ['platform_admin', 'org_admin'].includes(u.role)).length}</div>
                        </div>
                        <button
                            className="btn btn-primary"
                            style={{ gridColumn: 'span 2', padding: '8px 16px', fontSize: '13px', fontWeight: 600 }}
                            onClick={() => setShowAddUser(true)}
                        >
                            + {t('userMgmt.addUser', '新增用户')}
                        </button>
                    </div>
                </div>
            </div>

            {toast && (
                <div style={{
                    position: 'fixed', top: '20px', right: '20px', padding: '10px 20px',
                    borderRadius: '8px', background: toast.startsWith('✅') ? 'var(--success)' : 'var(--error)',
                    color: '#fff', fontSize: '13px', zIndex: 9999, transition: 'all 0.3s',
                }}>
                    {toast}
                </div>
            )}

            {loading ? (
                <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                    {t('common.loading')}...
                </div>
            ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    {/* Search bar + 数字员工过滤开关 */}
                    <div style={{ position: 'relative', marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
                        <input
                            className="form-input"
                            type="text"
                            placeholder={t('userMgmt.searchPlaceholder')}
                            value={searchQuery}
                            onChange={e => { setSearchQuery(e.target.value); setPage(1); }}
                            style={{
                                width: '100%', maxWidth: '360px', fontSize: '13px',
                                padding: '8px 12px 8px 12px',
                                background: 'var(--bg-elevated)', border: '1px solid var(--border-subtle)',
                                borderRadius: '8px',
                            }}
                        />
                        {/* 数字员工过滤开关(默认隐藏) */}
                        <label style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', cursor: 'pointer', fontSize: '12px', color: 'var(--text-secondary)' }}>
                            <input
                                type="checkbox"
                                checked={showDigital}
                                onChange={e => { setShowDigital(e.target.checked); setPage(1); }}
                                style={{ cursor: 'pointer' }}
                            />
                            <span>{t('userMgmt.showDigital', '显示数字员工')}</span>
                            <span style={{ fontSize: '10px', color: 'var(--text-tertiary)' }}>
                                ({users.filter(u => u.is_digital).length})
                            </span>
                        </label>
                        {searchQuery && (
                            <span style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginLeft: '4px' }}>
                                {t('userMgmt.userCount', { filtered: filtered.length, total: users.filter(u => !u.is_digital).length })}
                            </span>
                        )}
                    </div>

                    {/* Header */}
                    <div style={{
                        display: 'grid', gridTemplateColumns: '1.5fr 1.1fr 0.9fr 0.7fr 0.7fr 0.7fr 100px',
                        gap: '10px', padding: '10px 16px', fontSize: '11px', fontWeight: 600,
                        color: 'var(--text-tertiary)', textTransform: 'uppercase', letterSpacing: '0.05em',
                    }}>
                        <div>{t('userMgmt.user', '用户')}</div>
                        <div>{isChinese ? '部门' : 'Dept'}</div>
                        <div>{isChinese ? '职位' : 'Position'}</div>
                        {/* Created At with sort toggle */}
                        <div
                            style={{ cursor: 'pointer', userSelect: 'none', display: 'flex', alignItems: 'center', gap: '3px' }}
                            onClick={toggleSort}
                            title={t('userMgmt.clickToToggleSort')}
                        >
                            {t('userMgmt.joinedAt')} {sortOrder === 'asc' ? '↑' : '↓'}
                        </div>
                        <div>{isChinese ? '身份' : 'Identity'}</div>
                        <div>{isChinese ? '权限' : 'Access'}</div>
                        <div></div>
                    </div>

                    {paged.map(user => {
                        const isDigital = user.is_digital === true;
                        const identityLabel: Record<string, string> = {
                            INTERNAL_ENTERPRISE: isChinese ? '董事长' : 'Chairman',
                            INTERNAL_ACTIVE: isChinese ? '在职' : 'Active',
                            INTERNAL_PROBATION: isChinese ? '试用期' : 'Probation',
                            INTERNAL_RESIGNED: isChinese ? '离职' : 'Resigned',
                        };
                        return (
                        <div key={user.id}>
                            <div className="card" style={{
                                display: 'grid', gridTemplateColumns: '1.5fr 1.1fr 0.9fr 0.7fr 0.7fr 0.7fr 100px',
                                gap: '10px', alignItems: 'center', padding: '12px 16px',
                                background: isDigital
                                    ? 'linear-gradient(180deg, rgba(34,211,238,0.06), rgba(34,211,238,0.02))'
                                    : undefined,
                                border: isDigital
                                    ? '1px solid rgba(34,211,238,0.18)'
                                    : undefined,
                                opacity: isDigital ? 0.92 : 1,
                            }}>
                                <div>
                                    <div style={{ fontWeight: 500, fontSize: '14px', cursor: 'pointer' }} onClick={() => setDetailUser(user)}>
                                        {user.display_name || user.username}
                                        {roleBadge(user.role)}
                                    </div>
                                    <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{user.phone || user.email || `@${user.username}`}</div>
                                </div>
                                <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
                                    {user.department_name || user.department || '-'}
                                </div>
                                <div style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>{user.position || user.title || '-'}</div>
                                <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>{formatDate(user.created_at)}</div>
                                <div style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>
                                    {identityLabel[user.identity_raw || ''] || getRoleLabel(user.role)}
                                </div>
                                {/* Access Level display */}
                                <div>
                                    {ACCESS_LEVELS.find(l => l.value === (user.access_level ?? 0)) && (
                                        <span style={{ fontSize: '10px', fontWeight: 500, color: 'var(--text-secondary)' }}>
                                            {ACCESS_LEVELS.find(l => l.value === (user.access_level ?? 0))?.label}
                                        </span>
                                    )}
                                </div>
                                <div>
                                    <button
                                        className="btn btn-secondary"
                                        style={{ padding: '4px 10px', fontSize: '11px' }}
                                        onClick={() => editingUserId === user.id ? setEditingUserId(null) : startEdit(user)}
                                    >
                                        {editingUserId === user.id ? t('common.cancel') : `✏️`}
                                    </button>
                                </div>
                            </div>

                            {/* Inline edit form */}
                            {editingUserId === user.id && (
                                <div className="card" style={{
                                    marginTop: '4px', padding: '16px',
                                    background: 'var(--bg-secondary)',
                                    borderLeft: '3px solid var(--accent-color)',
                                }}>
                                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                                        <div className="form-group">
                                            <label className="form-label" style={{ fontSize: '11px' }}>
                                                {isChinese ? '权限级别' : 'Access Level'}
                                            </label>
                                            <select
                                                className="form-input"
                                                value={editForm.access_level}
                                                onChange={e => setEditForm({ ...editForm, access_level: Number(e.target.value) })}
                                            >
                                                {ACCESS_LEVELS.map(l => (
                                                    <option key={l.value} value={l.value}>{l.label}</option>
                                                ))}
                                            </select>
                                        </div>
                                    </div>
                                    <div style={{ marginTop: '12px', display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                                        <button className="btn btn-secondary" onClick={() => setEditingUserId(null)}>
                                            {t('common.cancel')}
                                        </button>
                                        <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                                            {saving ? t('common.loading') : t('common.save', 'Save')}
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                        );
                    })}

                    {users.length === 0 && (
                        <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-tertiary)' }}>
                            {t('common.noData')}
                        </div>
                    )}

                    {/* Pagination */}
                    {totalPages > 1 && (
                        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '8px', marginTop: '16px' }}>
                            <button
                                className="btn btn-secondary"
                                style={{ padding: '4px 10px', fontSize: '12px' }}
                                disabled={page <= 1}
                                onClick={() => setPage(p => p - 1)}
                            >
                                ‹ {t('userMgmt.prev')}
                            </button>
                            {Array.from({ length: totalPages }, (_, i) => i + 1).map(p => (
                                <button
                                    key={p}
                                    className={`btn ${p === page ? 'btn-primary' : 'btn-secondary'}`}
                                    style={{ padding: '4px 10px', fontSize: '12px', minWidth: '32px' }}
                                    onClick={() => setPage(p)}
                                >
                                    {p}
                                </button>
                            ))}
                            <button
                                className="btn btn-secondary"
                                style={{ padding: '4px 10px', fontSize: '12px' }}
                                disabled={page >= totalPages}
                                onClick={() => setPage(p => p + 1)}
                            >
                                {t('userMgmt.next')} ›
                            </button>
                        </div>
                    )}
                </div>
            )}
            {/* User detail modal */}
            {detailUser && (
                <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.55)', zIndex: 2000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    onClick={() => setDetailUser(null)}>
                    <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', width: '480px', maxWidth: '95vw', maxHeight: '85vh', overflow: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                            <h3 style={{ margin: 0 }}>{t('userMgmt.userDetail', '用户详情')}</h3>
                            <button onClick={() => setDetailUser(null)} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)' }}>✕</button>
                        </div>
                        {/* User info */}
                        <div style={{ display: 'flex', gap: '16px', marginBottom: '20px', alignItems: 'center' }}>
                            <div style={{ width: '48px', height: '48px', borderRadius: '50%', background: 'var(--accent-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: '20px', fontWeight: 700, flexShrink: 0 }}>
                                {(detailUser.display_name || detailUser.username || '?')[0].toUpperCase()}
                            </div>
                            <div>
                                <div style={{ fontWeight: 600, fontSize: '16px' }}>{detailUser.display_name || detailUser.username}</div>
                                <div style={{ fontSize: '12px', color: 'var(--text-tertiary)' }}>@{detailUser.username}</div>
                            </div>
                            {roleBadge(detailUser.role)}
                        </div>
                        {/* Info grid */}
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '20px' }}>
                            {[
                                { label: isChinese ? '手机号' : 'Phone', value: detailUser.phone || '-' },
                                { label: t('userMgmt.email', 'Email'), value: detailUser.email || '-' },
                                { label: isChinese ? '部门' : 'Department', value: detailUser.department_name || detailUser.department || '-' },
                                { label: isChinese ? '职位' : 'Position', value: detailUser.position || detailUser.title || '-' },
                                { label: isChinese ? '来源类型' : 'Origin', value: (() => {
                                    const m: Record<string, string> = { fixed: '固定员工', evolved: '动态员工', personal: '个人助手', human: '人类' };
                                    return m[detailUser.origin || 'human'] || detailUser.origin || '-';
                                })() },
                                { label: t('userMgmt.role', '角色'), value: getRoleLabel(detailUser.role) },
                                { label: isChinese ? '身份' : 'Identity', value: (() => {
                                    const m: Record<string, string> = { INTERNAL_ENTERPRISE: '董事长', INTERNAL_ACTIVE: '在职', INTERNAL_PROBATION: '试用期', INTERNAL_RESIGNED: '离职' };
                                    return m[detailUser.identity_raw || ''] || detailUser.identity_raw || '-';
                                })() },
                                { label: isChinese ? '创始人' : 'Founder', value: detailUser.is_founder ? '✓' : '-' },
                                { label: t('userMgmt.joinedAt', '加入时间'), value: formatDate(detailUser.created_at) },
                            ].map(item => (
                                <div key={item.label} style={{ padding: '8px 12px', borderRadius: '8px', background: 'var(--bg-tertiary)' }}>
                                    <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', marginBottom: '2px' }}>{item.label}</div>
                                    <div style={{ fontSize: '13px', fontWeight: 500 }}>{item.value}</div>
                                </div>
                            ))}
                        </div>
                        {/* Status */}
                        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '16px' }}>
                            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: detailUser.is_active ? 'var(--success)' : 'var(--error)' }} />
                            <span style={{ fontSize: '12px', color: detailUser.is_active ? 'var(--success)' : 'var(--error)' }}>
                                {detailUser.is_active ? t('userMgmt.active', '活跃') : t('userMgmt.inactive', '停用')}
                            </span>
                        </div>
                        {/* Access Level */}
                        <div style={{ marginBottom: '16px', padding: '12px', borderRadius: '8px', background: 'var(--bg-tertiary)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px', fontWeight: 600 }}>
                                {t('userMgmt.accessLevel', '权限级别')}
                            </div>
                            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                                {ACCESS_LEVELS.map(level => {
                                    const current = detailUser.access_level ?? 0;
                                    const isActive = current === level.value;
                                    return (
                                        <button key={level.value} onClick={() => {
                                            if (!isActive && confirm(t('userMgmt.confirmAccessChange', '确定将权限级别更改为 {{level}} 吗？', { level: level.label }))) {
                                                handleAccessLevelChange(detailUser.id, level.value);
                                            }
                                        }} disabled={changingAccessUserId === detailUser.id} style={{
                                            padding: '6px 12px', borderRadius: '8px', fontSize: '11px', fontWeight: 500,
                                            cursor: isActive ? 'default' : 'pointer', border: 'none',
                                            background: isActive ? 'var(--accent-primary)' : 'var(--bg-secondary)',
                                            color: isActive ? '#fff' : 'var(--text-secondary)',
                                            transition: 'all 0.15s',
                                            position: 'relative',
                                        }} title={level.desc}>
                                            {level.label}
                                        </button>
                                    );
                                })}
                            </div>
                            <div style={{ fontSize: '10px', color: 'var(--text-tertiary)', marginTop: '6px' }}>
                                {ACCESS_LEVELS.find(l => l.value === (detailUser.access_level ?? 0))?.desc}
                            </div>
                        </div>
                        {/* Actions */}
                        <div style={{ display: 'flex', gap: '8px', justifyContent: 'space-between', borderTop: '1px solid var(--border-subtle)', paddingTop: '16px' }}>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                {detailUser.is_active ? (
                                    <button className="btn btn-secondary" style={{ color: 'var(--error)' }} onClick={async () => {
                                        if (!confirm(t('userMgmt.confirmDeactivate', '确定要停用此用户吗？停用后用户将无法登录。'))) return;
                                        try {
                                            await fetchJson(`/enterprise/employees/${detailUser.id}/deactivate`, { method: 'POST', body: JSON.stringify({}) });
                                            setDetailUser({ ...detailUser, is_active: false });
                                            loadUsers();
                                            setToast(t('userMgmt.deactivated', '用户已停用'));
                                            setTimeout(() => setToast(''), 2000);
                                        } catch (e: any) {
                                            setToast(`Error: ${e.message}`);
                                            setTimeout(() => setToast(''), 4000);
                                        }
                                    }}>
                                        {t('userMgmt.deactivate', '停用用户')}
                                    </button>
                                ) : (
                                    <button className="btn btn-primary" onClick={async () => {
                                        try {
                                            await fetchJson(`/enterprise/employees/${detailUser.id}/activate`, { method: 'POST', body: JSON.stringify({}) });
                                            setDetailUser({ ...detailUser, is_active: true });
                                            loadUsers();
                                            setToast(t('userMgmt.activated', '用户已激活'));
                                            setTimeout(() => setToast(''), 2000);
                                        } catch (e: any) {
                                            setToast(`Error: ${e.message}`);
                                            setTimeout(() => setToast(''), 4000);
                                        }
                                    }}>
                                        {t('userMgmt.activate', '激活用户')}
                                    </button>
                                )}
                            </div>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <button className="btn btn-secondary" style={{ color: 'var(--error)' }} onClick={() => handleDeleteUser(detailUser.id, detailUser.display_name || detailUser.username)}>
                                    🗑️ {t('userMgmt.deleteUser', '删除')}
                                </button>
                                <button className="btn btn-secondary" onClick={() => { startEdit(detailUser); setDetailUser(null); }}>
                                    ✏️ {t('common.edit', '编辑配额')}
                                </button>
                                <button className="btn btn-secondary" onClick={() => setDetailUser(null)}>
                                    {t('common.cancel')}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
            {/* Add User Modal */}
            {showAddUser && (
                <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.55)', zIndex: 2000, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    onClick={() => setShowAddUser(false)}>
                    <div onClick={e => e.stopPropagation()} style={{ background: 'var(--bg-primary)', borderRadius: '12px', padding: '24px', width: '480px', maxWidth: '95vw', maxHeight: '85vh', overflow: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.4)' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                            <h3 style={{ margin: 0 }}>{t('userMgmt.addUserTitle', '新增用户')}</h3>
                            <button onClick={() => setShowAddUser(false)} style={{ background: 'none', border: 'none', fontSize: '18px', cursor: 'pointer', color: 'var(--text-secondary)' }}>✕</button>
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                            <div style={{ padding: '10px 12px', borderRadius: '8px', background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.25)', fontSize: '12px', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                                💡 添加用户将自动生成邀请码并注册。用户可通过手机号+密码登录。
                            </div>
                            <div className="form-group">
                                <label className="form-label" style={{ fontSize: '12px' }}>{t('userMgmt.company', '公司')} *</label>
                                <select
                                    className="form-input"
                                    value={addForm.tenantId}
                                    onChange={e => setAddForm({ ...addForm, tenantId: e.target.value })}
                                >
                                    <option value="">{isChinese ? '选择公司' : 'Select company'}</option>
                                    {companies.map((c: any) => (
                                        <option key={c.id || c.tenantId} value={c.id || c.tenantId}>{c.name}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="form-group">
                                <label className="form-label" style={{ fontSize: '12px' }}>{t('userMgmt.phone', '手机号')} *</label>
                                <input
                                    className="form-input"
                                    type="tel"
                                    maxLength={11}
                                    value={addForm.phone}
                                    onChange={e => setAddForm({ ...addForm, phone: e.target.value.replace(/\D/g, '').slice(0, 11) })}
                                    placeholder={t('userMgmt.phonePlaceholder', '输入11位手机号（用于登录）')}
                                    autoFocus
                                />
                            </div>
                            <div className="form-group">
                                <label className="form-label" style={{ fontSize: '12px' }}>{isChinese ? '姓名' : 'Name'} *</label>
                                <input
                                    className="form-input"
                                    type="text"
                                    value={addForm.name}
                                    onChange={e => setAddForm({ ...addForm, name: e.target.value })}
                                    placeholder={isChinese ? '输入姓名' : 'Enter name'}
                                />
                            </div>
                            <div className="form-group">
                                <label className="form-label" style={{ fontSize: '12px' }}>{t('userMgmt.email', '邮箱')}</label>
                                <input
                                    className="form-input"
                                    type="email"
                                    value={addForm.email}
                                    onChange={e => setAddForm({ ...addForm, email: e.target.value })}
                                    placeholder={t('userMgmt.emailPlaceholder', '输入邮箱（可选）')}
                                />
                            </div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                                <div className="form-group">
                                    <label className="form-label" style={{ fontSize: '12px' }}>{t('userMgmt.department', '部门')} *</label>
                                    <select
                                        className="form-input"
                                        value={addForm.department}
                                        onChange={e => {
                                            const code = e.target.value;
                                            setAddForm({ ...addForm, department: code, accessLevel: DEPT_DEFAULT_ACCESS[code] || 'DEPARTMENT' });
                                        }}
                                    >
                                        <option value="">{isChinese ? '选择部门' : 'Select department'}</option>
                                        {DEPARTMENTS.map(d => (
                                            <option key={d.value} value={d.value}>{d.label}</option>
                                        ))}
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="form-label" style={{ fontSize: '12px' }}>{isChinese ? '职位' : 'Position'} *</label>
                                    <input
                                        className="form-input"
                                        type="text"
                                        value={addForm.position}
                                        onChange={e => setAddForm({ ...addForm, position: e.target.value })}
                                        placeholder={isChinese ? '输入职位' : 'Enter position'}
                                    />
                                </div>
                            </div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                                <div className="form-group">
                                    <label className="form-label" style={{ fontSize: '12px' }}>
                                        {isChinese ? '权限级别' : 'Access Level'}
                                        {addForm.department && <span style={{ marginLeft: 4, fontSize: 10, color: 'var(--accent)' }}>(按部门默认)</span>}
                                    </label>
                                    <select
                                        className="form-input"
                                        value={addForm.accessLevel}
                                        onChange={e => setAddForm({ ...addForm, accessLevel: e.target.value })}
                                    >
                                        <option value="CHAT_ONLY">CHAT_ONLY (0)</option>
                                        <option value="LIMITED">LIMITED (1)</option>
                                        <option value="DEPARTMENT">DEPARTMENT (2)</option>
                                        <option value="FULL">FULL (3)</option>
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="form-label" style={{ fontSize: '12px' }}>{isChinese ? '初始密码' : 'Initial Password'}</label>
                                    <input
                                        className="form-input"
                                        type="password"
                                        value={addForm.initialPassword}
                                        onChange={e => setAddForm({ ...addForm, initialPassword: e.target.value })}
                                        placeholder={isChinese ? '至少6位（可选）' : 'Min 6 chars (optional)'}
                                    />
                                </div>
                            </div>
                        </div>
                        <div style={{ marginTop: '20px', display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
                            <button className="btn btn-secondary" onClick={() => setShowAddUser(false)}>
                                {t('common.cancel')}
                            </button>
                            <button className="btn btn-primary" onClick={handleAddUser} disabled={adding}>
                                {adding ? t('common.loading') : t('userMgmt.create', '创建用户')}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
