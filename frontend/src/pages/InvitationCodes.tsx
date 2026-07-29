import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../stores';
import { getToken } from '../stores';
import { request } from '../services/apiBase';
import { invitationCodeApi, departmentApi, adminApi } from '../services/api';

/** 邀请码状态显示映射 */
const STATUS_MAP: Record<string, { label: string; color: string }> = {
    PENDING: { label: '待使用', color: 'var(--success)' },
    USED: { label: '已用完', color: 'var(--warning)' },
    EXPIRED: { label: '已过期', color: 'var(--text-tertiary)' },
    DISABLED: { label: '已禁用', color: 'var(--text-tertiary)' },
};

/** 部门代码 → 默认 AccessLevel 映射 */
const DEPT_DEFAULT_ACCESS: Record<string, string> = {
    tech: 'DEPARTMENT',
    hr: 'DEPARTMENT',
    finance: 'DEPARTMENT',
    sales: 'DEPARTMENT',
    admin: 'DEPARTMENT',
    cs: 'LIMITED',       // 客服部默认 LIMITED
    legal: 'DEPARTMENT',
    ops: 'DEPARTMENT',
    core: 'DEPARTMENT',
    cross_dept: 'DEPARTMENT',
};

export default function InvitationCodes() {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();
    const isChinese = i18n.language === 'zh' || i18n.language?.startsWith('zh');
    const user = useAuthStore((s) => s.user);

    // 权限检查：仅董事长(founder/FULL) 和 HR 部门可操作
    const isFounder = user?.role === 'org_admin' || user?.access_level === 'FULL';
    const isHR = (user as any)?.department === '人力资源' || (user as any)?.department === '人力资源部';
    const canOperate = isFounder || isHR;

    const [codes, setCodes] = useState<any[]>([]);
    const [total, setTotal] = useState(0);
    const [search, setSearch] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [toast, setToast] = useState('');
    const [toastType, setToastType] = useState<'success' | 'error'>('success');

    // 单个创建表单
    const [showCreate, setShowCreate] = useState(false);
    const [createForm, setCreateForm] = useState({
        departmentCode: '',
        departmentName: '',
        phone: '',
        initialPassword: '',
        role: 'employee',
        accessLevel: 'DEPARTMENT',
        maxUses: 1,
        note: '',
    });
    const [creating, setCreating] = useState(false);

    // 批量生成
    const [showBatch, setShowBatch] = useState(false);
    const [batchForm, setBatchForm] = useState({
        count: 5,
        maxUses: 1,
        departmentCode: '',
        departmentName: '',
        role: 'employee',
        accessLevel: 'DEPARTMENT',
    });
    const [batchCreating, setBatchCreating] = useState(false);

    // 部门列表（从 API 获取）
    const [departments, setDepartments] = useState<any[]>([]);

    // 使用详情弹窗
    const [usageDetail, setUsageDetail] = useState<{ code: string; usages: any[] } | null>(null);

    // 加载邀请码列表
    const loadCodes = useCallback(async () => {
        try {
            const data = await invitationCodeApi.list({
                tenantId: undefined,
                status: statusFilter || undefined,
            });
            setCodes(Array.isArray(data) ? data : []);
            setTotal(Array.isArray(data) ? data.length : 0);
        } catch {
            try {
                const data = await request<any>('/enterprise/invitation-codes');
                setCodes(data.items || data || []);
                setTotal(data.total || (Array.isArray(data) ? data.length : 0));
            } catch {
                setCodes([]);
                setTotal(0);
            }
        }
    }, [statusFilter]);

    // 加载部门列表
    useEffect(() => {
        departmentApi.list().then((data: any) => {
            // /api/departments 返回 { code, name, nameEn, ... } 格式
            const list = Array.isArray(data) ? data : [];
            setDepartments(list);
        }).catch(() => setDepartments([]));
    }, []);

    useEffect(() => { loadCodes(); }, [loadCodes]);

    // 无权限提示
    if (!canOperate) {
        return (
            <div className="content-area" style={{ maxWidth: '600px', margin: '0 auto', padding: '60px 24px', textAlign: 'center' }}>
                <div style={{ fontSize: '48px', marginBottom: '16px' }}>🔒</div>
                <h2 style={{ fontSize: '18px', fontWeight: 600, marginBottom: '8px' }}>
                    {isChinese ? '无权访问' : 'Access Denied'}
                </h2>
                <p style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginBottom: '24px' }}>
                    {isChinese ? '仅董事长和人力资源部可以管理邀请码' : 'Only founders and HR can manage invitation codes'}
                </p>
                <button className="btn btn-primary" onClick={() => navigate('/')}>
                    {isChinese ? '返回首页' : 'Go Home'}
                </button>
            </div>
        );
    }

    // Toast 辅助
    const showToast = (msg: string, type: 'success' | 'error' = 'success') => {
        setToast(msg);
        setToastType(type);
        setTimeout(() => setToast(''), 3000);
    };

    // 部门选择联动 — 自动设置默认权限级别
    const handleDepartmentSelect = (code: string, target: 'create' | 'batch') => {
        const dept = departments.find((d: any) => d.code === code);
        const deptName = dept?.name || dept?.departmentName || '';
        const defaultAccess = DEPT_DEFAULT_ACCESS[code] || 'DEPARTMENT';

        if (target === 'create') {
            setCreateForm(f => ({ ...f, departmentCode: code, departmentName: deptName, accessLevel: defaultAccess }));
        } else {
            setBatchForm(f => ({ ...f, departmentCode: code, departmentName: deptName, accessLevel: defaultAccess }));
        }
    };

    // 单个创建
    const handleCreateSingle = async () => {
        if (!createForm.departmentCode) {
            showToast(isChinese ? '请选择部门' : 'Please select department', 'error');
            return;
        }
        setCreating(true);
        try {
            await invitationCodeApi.create({
                departmentCode: createForm.departmentCode,
                departmentName: createForm.departmentName || undefined,
                phone: createForm.phone || undefined,
                initialPassword: createForm.initialPassword || undefined,
                role: createForm.role,
                accessLevel: createForm.accessLevel,
                maxUses: createForm.maxUses,
                note: createForm.note || undefined,
            });
            showToast(isChinese ? '邀请码创建成功' : 'Invitation code created');
            setShowCreate(false);
            setCreateForm({ departmentCode: '', departmentName: '', phone: '', initialPassword: '', role: 'employee', accessLevel: 'DEPARTMENT', maxUses: 1, note: '' });
            await loadCodes();
        } catch (err: any) {
            showToast(err.message || (isChinese ? '创建失败' : 'Create failed'), 'error');
        }
        setCreating(false);
    };

    // 批量生成
    const handleBatchCreate = async () => {
        if (!batchForm.departmentCode) {
            showToast(isChinese ? '请选择部门' : 'Please select department', 'error');
            return;
        }
        setBatchCreating(true);
        try {
            await invitationCodeApi.batchCreate({
                count: batchForm.count,
                template: {
                    departmentCode: batchForm.departmentCode,
                    departmentName: batchForm.departmentName || undefined,
                    role: batchForm.role,
                    accessLevel: batchForm.accessLevel,
                    maxUses: batchForm.maxUses,
                },
            });
            showToast(isChinese ? `成功生成 ${batchForm.count} 个邀请码` : `Generated ${batchForm.count} codes`);
            setShowBatch(false);
            await loadCodes();
        } catch (err: any) {
            showToast(err.message || (isChinese ? '批量生成失败' : 'Batch create failed'), 'error');
        }
        setBatchCreating(false);
    };

    // 禁用
    const handleDisable = async (id: number) => {
        try {
            await invitationCodeApi.disable(id);
            showToast(isChinese ? '已禁用' : 'Disabled');
            await loadCodes();
        } catch {
            showToast(isChinese ? '操作失败' : 'Failed', 'error');
        }
    };

    // 删除
    const handleDelete = async (id: number) => {
        if (!confirm(isChinese ? '确定删除此邀请码？' : 'Delete this code?')) return;
        try {
            await invitationCodeApi.delete(id);
            showToast(isChinese ? '已删除' : 'Deleted');
            await loadCodes();
        } catch {
            showToast(isChinese ? '删除失败' : 'Delete failed', 'error');
        }
    };

    // 清理过期
    const handleCleanup = async () => {
        try {
            const result = await invitationCodeApi.cleanup();
            showToast(isChinese ? `已清理 ${result.cleaned_count} 个过期邀请码` : `Cleaned ${result.cleaned_count} expired codes`);
            await loadCodes();
        } catch {
            showToast(isChinese ? '清理失败' : 'Cleanup failed', 'error');
        }
    };

    // 导出 CSV
    const exportCsv = () => {
        const token = getToken();
        const a = document.createElement('a');
        fetch('/api/enterprise/invitation-codes/export', {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
        })
            .then(r => r.blob())
            .then(blob => {
                a.href = URL.createObjectURL(blob);
                a.download = 'invitation_codes.csv';
                a.click();
                URL.revokeObjectURL(a.href);
            });
    };

    // 渲染状态 Badge
    const renderStatus = (status: string) => {
        const s = STATUS_MAP[status] || STATUS_MAP.PENDING;
        return (
            <span className="badge" style={{ background: s.color, color: '#fff', fontSize: '10px' }}>
                {s.label}
            </span>
        );
    };

    // 表单样式
    const inputStyle = { width: '100%' };
    const labelStyle: React.CSSProperties = { display: 'block', fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '4px' };
    const fieldStyle: React.CSSProperties = { marginBottom: '10px' };

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
                {t('enterprise.invites.pageTitle', '邀请码管理')}
            </h2>
            <p style={{ fontSize: '13px', color: 'var(--text-tertiary)', marginBottom: '24px' }}>
                {isChinese ? '管理邀请码，选择部门和手机号，快速生成。公司自动填充当前公司。' : 'Manage invitation codes. Company is auto-filled from your account.'}
            </p>

            {/* 操作按钮栏 */}
            <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', flexWrap: 'wrap' }}>
                <button className="btn btn-primary" onClick={() => { setShowCreate(true); setShowBatch(false); }}>
                    {isChinese ? '+ 创建邀请码' : '+ Create Code'}
                </button>
                <button className="btn btn-secondary" onClick={() => { setShowBatch(true); setShowCreate(false); }}>
                    {isChinese ? '批量生成' : 'Batch Create'}
                </button>
                <button className="btn btn-secondary" onClick={handleCleanup}>
                    {isChinese ? '清理过期' : 'Cleanup Expired'}
                </button>
                <button className="btn btn-secondary" onClick={exportCsv}>
                    {t('enterprise.invites.exportCsv')}
                </button>
            </div>

            {/* 单个创建表单 */}
            {showCreate && (
                <div className="card" style={{ padding: '20px', marginBottom: '16px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                        <div style={{ fontSize: '14px', fontWeight: 600 }}>
                            {isChinese ? '创建单个邀请码' : 'Create Single Code'}
                        </div>
                        <button className="btn btn-ghost" onClick={() => setShowCreate(false)} style={{ padding: '4px 8px' }}>x</button>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>{isChinese ? '部门 *' : 'Department *'}</label>
                            <select className="form-input" style={inputStyle}
                                value={createForm.departmentCode}
                                onChange={e => handleDepartmentSelect(e.target.value, 'create')}>
                                <option value="">{isChinese ? '选择部门' : 'Select department'}</option>
                                {departments.map((d: any) => <option key={d.code} value={d.code}>{d.name}</option>)}
                            </select>
                        </div>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>{isChinese ? '手机号（预绑定）' : 'Phone (bind)'}</label>
                            <input className="form-input" style={inputStyle} placeholder={isChinese ? '注册时需匹配此手机号' : 'Must match on registration'}
                                value={createForm.phone} onChange={e => setCreateForm(f => ({ ...f, phone: e.target.value }))} />
                        </div>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>{isChinese ? '初始密码' : 'Initial Password'}</label>
                            <input className="form-input" type="password" style={inputStyle} placeholder={isChinese ? '至少 6 位（可选）' : 'Min 6 chars (optional)'}
                                value={createForm.initialPassword} onChange={e => setCreateForm(f => ({ ...f, initialPassword: e.target.value }))} />
                        </div>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>{isChinese ? '角色' : 'Role'}</label>
                            <select className="form-input" style={inputStyle}
                                value={createForm.role} onChange={e => setCreateForm(f => ({ ...f, role: e.target.value }))}>
                                <option value="employee">{isChinese ? '员工' : 'Employee'}</option>
                                <option value="department_head">{isChinese ? '部门负责人' : 'Dept Head'}</option>
                            </select>
                        </div>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>
                                {isChinese ? '权限级别' : 'Access Level'}
                                {createForm.departmentCode && (
                                    <span style={{ marginLeft: 6, fontSize: '10px', color: 'var(--accent)' }}>
                                        ({isChinese ? '按部门默认，可修改' : 'Dept default, adjustable'})
                                    </span>
                                )}
                            </label>
                            <select className="form-input" style={inputStyle}
                                value={createForm.accessLevel} onChange={e => setCreateForm(f => ({ ...f, accessLevel: e.target.value }))}>
                                <option value="CHAT_ONLY">CHAT_ONLY (0)</option>
                                <option value="LIMITED">LIMITED (1)</option>
                                <option value="DEPARTMENT">DEPARTMENT (2)</option>
                                <option value="FULL">FULL (3)</option>
                            </select>
                        </div>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>{isChinese ? '最大使用次数' : 'Max Uses'}</label>
                            <input className="form-input" type="number" min={1} style={inputStyle}
                                value={createForm.maxUses} onChange={e => setCreateForm(f => ({ ...f, maxUses: Number(e.target.value) }))} />
                        </div>
                        <div style={{ ...fieldStyle, gridColumn: '1 / -1' }}>
                            <label style={labelStyle}>{isChinese ? '备注' : 'Note'}</label>
                            <input className="form-input" style={inputStyle} placeholder={isChinese ? '可选' : 'Optional'}
                                value={createForm.note} onChange={e => setCreateForm(f => ({ ...f, note: e.target.value }))} />
                        </div>
                    </div>
                    <button className="btn btn-primary" onClick={handleCreateSingle} disabled={creating}
                        style={{ marginTop: '8px' }}>
                        {creating ? (isChinese ? '创建中...' : 'Creating...') : (isChinese ? '确认创建' : 'Create')}
                    </button>
                </div>
            )}

            {/* 批量生成表单 */}
            {showBatch && (
                <div className="card" style={{ padding: '20px', marginBottom: '16px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                        <div style={{ fontSize: '14px', fontWeight: 600 }}>
                            {isChinese ? '批量生成邀请码' : 'Batch Create Codes'}
                        </div>
                        <button className="btn btn-ghost" onClick={() => setShowBatch(false)} style={{ padding: '4px 8px' }}>x</button>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>{isChinese ? '生成数量' : 'Count'}</label>
                            <input className="form-input" type="number" min={1} max={1000} style={inputStyle}
                                value={batchForm.count} onChange={e => setBatchForm(f => ({ ...f, count: Number(e.target.value) }))} />
                        </div>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>{isChinese ? '每码最大使用次数' : 'Max Uses / Code'}</label>
                            <input className="form-input" type="number" min={1} style={inputStyle}
                                value={batchForm.maxUses} onChange={e => setBatchForm(f => ({ ...f, maxUses: Number(e.target.value) }))} />
                        </div>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>{isChinese ? '部门 *' : 'Department *'}</label>
                            <select className="form-input" style={inputStyle}
                                value={batchForm.departmentCode}
                                onChange={e => handleDepartmentSelect(e.target.value, 'batch')}>
                                <option value="">{isChinese ? '选择部门' : 'Select department'}</option>
                                {departments.map((d: any) => <option key={d.code} value={d.code}>{d.name}</option>)}
                            </select>
                        </div>
                        <div style={fieldStyle}>
                            <label style={labelStyle}>{isChinese ? '角色' : 'Role'}</label>
                            <select className="form-input" style={inputStyle}
                                value={batchForm.role} onChange={e => setBatchForm(f => ({ ...f, role: e.target.value }))}>
                                <option value="employee">{isChinese ? '员工' : 'Employee'}</option>
                                <option value="department_head">{isChinese ? '部门负责人' : 'Dept Head'}</option>
                            </select>
                        </div>
                        <div style={{ ...fieldStyle, gridColumn: '1 / -1' }}>
                            <label style={labelStyle}>
                                {isChinese ? '权限级别' : 'Access Level'}
                                {batchForm.departmentCode && (
                                    <span style={{ marginLeft: 6, fontSize: '10px', color: 'var(--accent)' }}>
                                        ({isChinese ? '按部门默认，可修改' : 'Dept default, adjustable'})
                                    </span>
                                )}
                            </label>
                            <select className="form-input" style={inputStyle}
                                value={batchForm.accessLevel} onChange={e => setBatchForm(f => ({ ...f, accessLevel: e.target.value }))}>
                                <option value="CHAT_ONLY">CHAT_ONLY (0)</option>
                                <option value="LIMITED">LIMITED (1)</option>
                                <option value="DEPARTMENT">DEPARTMENT (2)</option>
                                <option value="FULL">FULL (3)</option>
                            </select>
                        </div>
                    </div>
                    <button className="btn btn-primary" onClick={handleBatchCreate} disabled={batchCreating}
                        style={{ marginTop: '8px' }}>
                        {batchCreating ? (isChinese ? '生成中...' : 'Generating...') : (isChinese ? `生成 ${batchForm.count} 个邀请码` : `Generate ${batchForm.count} Codes`)}
                    </button>
                </div>
            )}

            {/* 状态过滤 */}
            <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', flexWrap: 'wrap' }}>
                {[{ key: '', label: isChinese ? '全部' : 'All' }, { key: 'PENDING', label: '待使用' }, { key: 'USED', label: '已用完' }, { key: 'EXPIRED', label: '已过期' }, { key: 'DISABLED', label: '已禁用' }].map(s => (
                    <button key={s.key} className={`btn ${statusFilter === s.key ? 'btn-primary' : 'btn-secondary'}`}
                        style={{ padding: '4px 12px', fontSize: '11px' }}
                        onClick={() => setStatusFilter(s.key)}>
                        {s.label}
                    </button>
                ))}
            </div>

            {/* 列表 */}
            <div className="card" style={{ padding: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                    <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)' }}>
                        {isChinese ? '所有邀请码' : 'All Invitation Codes'} ({total})
                    </div>
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                        <input className="form-input" placeholder={t('common.search', 'Search') + '...'}
                            value={search} onChange={e => setSearch(e.target.value)}
                            style={{ width: '200px', height: '30px', fontSize: '12px' }} />
                    </div>
                </div>

                {/* 表头 */}
                <div style={{
                    display: 'grid', gridTemplateColumns: '1.2fr 0.8fr 0.8fr 0.6fr 0.6fr 0.6fr 80px',
                    gap: '8px', padding: '8px 12px', fontSize: '11px', fontWeight: 600,
                    color: 'var(--text-tertiary)', textTransform: 'uppercase', letterSpacing: '0.05em',
                    borderBottom: '1px solid var(--border-subtle)',
                }}>
                    <div>{isChinese ? '邀请码' : 'Code'}</div>
                    <div>{isChinese ? '公司/部门' : 'Company/Dept'}</div>
                    <div>{isChinese ? '手机号' : 'Phone'}</div>
                    <div>{isChinese ? '使用' : 'Usage'}</div>
                    <div>{isChinese ? '状态' : 'Status'}</div>
                    <div>{isChinese ? '创建时间' : 'Created'}</div>
                    <div></div>
                </div>

                {codes.length === 0 && (
                    <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                        {t('common.noData')}
                    </div>
                )}

                {codes.filter((c: any) => !search || c.code?.toLowerCase().includes(search.toLowerCase())).map((c: any) => (
                    <div key={c.id} style={{
                        display: 'grid', gridTemplateColumns: '1.2fr 0.8fr 0.8fr 0.6fr 0.6fr 0.6fr 80px',
                        gap: '8px', padding: '10px 12px', alignItems: 'center',
                        borderBottom: '1px solid var(--border-subtle)', fontSize: '13px',
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            <span style={{ fontFamily: 'monospace', fontWeight: 500, letterSpacing: '0.05em', fontSize: '12px' }}>{c.code}</span>
                            <button onClick={() => { navigator.clipboard.writeText(c.code); showToast(isChinese ? '已复制' : 'Copied'); }}
                                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '11px', color: 'var(--text-tertiary)', padding: '0 2px' }}
                                title={isChinese ? '复制' : 'Copy'}>📋</button>
                        </div>
                        <div style={{ fontSize: '12px' }}>
                            <div style={{ fontWeight: 500 }}>{c.companyName || '-'}</div>
                            <div style={{ color: 'var(--text-tertiary)', fontSize: '11px' }}>{c.departmentName || '-'}</div>
                        </div>
                        <div style={{ fontSize: '12px', color: c.phone ? 'var(--text-secondary)' : 'var(--text-tertiary)' }}>
                            {c.phone || (isChinese ? '不限' : 'Any')}
                        </div>
                        <div style={{ fontSize: '12px' }}>
                            <span style={{ fontWeight: 500 }}>{c.usedCount ?? c.used_count ?? 0}</span>
                            <span style={{ color: 'var(--text-tertiary)' }}> / {c.maxUses ?? c.max_uses ?? 1}</span>
                        </div>
                        <div>{renderStatus(c.status || (c.is_active ? 'PENDING' : 'DISABLED'))}</div>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                            {c.createdAt ? new Date(c.createdAt).toLocaleDateString() : c.created_at ? new Date(c.created_at).toLocaleDateString() : '-'}
                        </div>
                        <div style={{ display: 'flex', gap: '4px' }}>
                            {(c.status === 'PENDING' || c.is_active) && (c.usedCount ?? c.used_count ?? 0) < (c.maxUses ?? c.max_uses ?? 1) && (
                                <button className="btn btn-secondary" style={{ padding: '2px 6px', fontSize: '10px' }}
                                    onClick={() => handleDisable(c.id)}>禁用</button>
                            )}
                            <button className="btn btn-secondary" style={{ padding: '2px 6px', fontSize: '10px', color: 'var(--error)' }}
                                onClick={() => handleDelete(c.id)}>删</button>
                        </div>
                    </div>
                ))}
            </div>

            {/* 使用详情弹窗 */}
            {usageDetail && (
                <div style={{ position: 'fixed', inset: 0, zIndex: 9999, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    onClick={() => setUsageDetail(null)}>
                    <div style={{ background: 'var(--bg-primary)', borderRadius: '12px', width: '480px', border: '1px solid var(--border-default)', boxShadow: '0 16px 48px rgba(0,0,0,0.2)' }}
                        onClick={e => e.stopPropagation()}>
                        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <h3 style={{ margin: 0, fontSize: '15px' }}>{isChinese ? '邀请码使用详情' : 'Usage Detail'}</h3>
                            <button className="btn btn-ghost" onClick={() => setUsageDetail(null)} style={{ padding: '4px 8px', fontSize: '16px' }}>x</button>
                        </div>
                        <div style={{ padding: '16px 20px' }}>
                            <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginBottom: '12px' }}>
                                {isChinese ? '邀请码' : 'Code'}: <code style={{ fontFamily: 'monospace', fontWeight: 500 }}>{usageDetail.code}</code>
                            </div>
                            {usageDetail.usages.length === 0 ? (
                                <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
                                    {isChinese ? '暂无使用记录' : 'No usage records'}
                                </div>
                            ) : (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                                    {usageDetail.usages.map((u: any, i: number) => (
                                        <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 12px', borderRadius: '6px', background: 'var(--bg-tertiary)', fontSize: '12px' }}>
                                            <span style={{ fontWeight: 500 }}>{u.username || u.user_id || '-'}</span>
                                            <span style={{ color: 'var(--text-tertiary)' }}>{u.used_at ? new Date(u.used_at).toLocaleString() : '-'}</span>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
