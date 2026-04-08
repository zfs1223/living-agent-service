import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { departmentApi, agentApi } from '../services/api';
import { DEPARTMENTS, type DepartmentCode } from '../types';
import { useAuthStore } from '../stores';
import DigitalEmployeeSettings from '../components/DigitalEmployeeSettings';
import {
    IconUsers,
    IconBrain,
    IconRobot,
    IconMessage,
    IconArrowLeft,
    IconPlayerPlay,
    IconSettings,
    IconPlus,
} from '@tabler/icons-react';

export default function DepartmentDetail() {
    const { code } = useParams<{ code: string }>();
    const navigate = useNavigate();
    const { t, i18n } = useTranslation();
    const isChinese = i18n.language?.startsWith('zh');
    const user = useAuthStore((s) => s.user);
    
    // 判断用户权限
    const isChairman = user?.identity === 'INTERNAL_CHAIRMAN' || user?.access_level === 'FULL';
    const isDepartmentHead = user?.department_id === code && user?.title?.includes('负责人');
    const canChatWithFixedAgents = isChairman || isDepartmentHead;
    
    const deptCode = code as DepartmentCode;
    const deptInfo = DEPARTMENTS[deptCode];
    
    const { data: department, isLoading: loadingDept } = useQuery({
        queryKey: ['department', code],
        queryFn: () => departmentApi.getByCode(code!),
        enabled: !!code,
    });
    
    const { data: brain, isLoading: loadingBrain } = useQuery({
        queryKey: ['department-brain', department?.id],
        queryFn: () => departmentApi.getBrain(department!.id),
        enabled: !!department?.id,
    });
    
    const { data: agents = [], isLoading: loadingAgents } = useQuery({
        queryKey: ['department-agents', department?.id],
        queryFn: () => departmentApi.getAgents(department!.id),
        enabled: !!department?.id,
    });
    
    const { data: members = [], isLoading: loadingMembers } = useQuery({
        queryKey: ['department-members', department?.id],
        queryFn: () => departmentApi.getMembers(department!.id),
        enabled: !!department?.id,
    });
    
    const isLoading = loadingDept || loadingBrain || loadingAgents || loadingMembers;

    const handleStartChat = (agentId: string) => {
        // 所有对话都使用闲聊神经元(Qwen3Neuron)，通过权限区分通道
        navigate(`/chat?id=${encodeURIComponent(agentId)}`);
    };

    const handleBrainChat = () => {
        // 部门大脑对话入口 - 只有董事长和部门负责人可以使用
        if (brain && (brain as any).id) {
            navigate(`/chat?brain=${code}&dept=${encodeURIComponent(department?.name || '')}`);
        }
    };

    const handleAgentClick = (agentId: string) => {
        navigate(`/agents/${encodeURIComponent(agentId)}`);
    };

    const handleCreateAgent = () => {
        // 普通员工创建新的数字人进行协助对话
        navigate('/agents/create?type=personal');
    };

    if (!deptInfo) {
        return (
            <div className="page-container">
                <div className="error-state">
                    <h2>{isChinese ? '部门不存在' : 'Department Not Found'}</h2>
                    <p>{isChinese ? '请检查 URL 是否正确' : 'Please check the URL'}</p>
                </div>
            </div>
        );
    }

    return (
        <div className="page-container">
            <button 
                onClick={() => navigate('/chairman')} 
                style={{ 
                    display: 'flex', 
                    alignItems: 'center', 
                    gap: '6px', 
                    marginBottom: '16px',
                    background: 'none',
                    border: 'none',
                    color: 'var(--text-secondary)',
                    cursor: 'pointer',
                    fontSize: '13px',
                    padding: '4px 0',
                }}
            >
                <IconArrowLeft size={16} />
                {isChinese ? '返回董事长视图' : 'Back to Chairman View'}
            </button>

            <div className="dept-header">
                <div className="dept-icon">{deptInfo.icon}</div>
                <div className="dept-info">
                    <h1 className="dept-title">{isChinese ? deptInfo.name : deptInfo.name_en}</h1>
                    <p className="dept-desc">{department?.description || (isChinese ? '暂无描述' : 'No description')}</p>
                </div>
                <div className="dept-stats">
                    <div className="dept-stat">
                        <IconUsers size={20} />
                        <span className="stat-value">{(members as any[])?.length || 0}</span>
                        <span className="stat-label">{isChinese ? '成员' : 'Members'}</span>
                    </div>
                    <div className="dept-stat">
                        <IconRobot size={20} />
                        <span className="stat-value">{(agents as any[])?.length || 0}</span>
                        <span className="stat-label">{isChinese ? '数字员工' : 'Agents'}</span>
                    </div>
                </div>
            </div>
            
            {isLoading ? (
                <div className="loading-state">{isChinese ? '加载中...' : 'Loading...'}</div>
            ) : (
                <div className="dept-content">
                    <div className="dept-section">
                        <div className="dept-section-header">
                            <IconBrain size={18} />
                            <h2>{isChinese ? '部门大脑' : 'Department Brain'}</h2>
                        </div>
                        {brain ? (
                            <div className="brain-card">
                                <div className="brain-avatar">
                                    {deptInfo.icon}
                                </div>
                                <div className="brain-info">
                                    <h3>{(brain as any).name || `${deptInfo.name} Brain`}</h3>
                                    <p>{(brain as any).description || (isChinese ? '智能部门助手' : 'Intelligent department assistant')}</p>
                                    <div className="brain-status">
                                        <span className={`status-dot ${(brain as any).status === 'running' ? 'active' : ''}`} />
                                        <span>{(brain as any).status === 'running' ? (isChinese ? '运行中' : 'Running') : (isChinese ? '已停止' : 'Stopped')}</span>
                                    </div>
                                </div>
                                {/* 只有董事长和部门负责人可以与部门大脑对话 */}
                                {canChatWithFixedAgents && (
                                    <button
                                        className="btn btn-primary"
                                        onClick={handleBrainChat}
                                        style={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '6px',
                                        }}
                                    >
                                        <IconMessage size={16} />
                                        {isChinese ? '对话' : 'Chat'}
                                    </button>
                                )}
                            </div>
                        ) : (
                            <div className="empty-state-small">
                                {isChinese ? '暂无部门大脑' : 'No brain configured'}
                            </div>
                        )}
                    </div>
                    
                    <div className="dept-section">
                        <div className="dept-section-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <IconRobot size={18} />
                                <h2>{isChinese ? '数字员工' : 'Digital Employees'}</h2>
                            </div>
                            {/* 普通员工可以创建新的数字人进行协助对话 */}
                            {!isChairman && (
                                <button
                                    className="btn btn-secondary"
                                    onClick={handleCreateAgent}
                                    style={{
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '6px',
                                        fontSize: '13px',
                                        padding: '6px 12px',
                                    }}
                                >
                                    <IconPlus size={14} />
                                    {isChinese ? '新建数字人' : 'Create Agent'}
                                </button>
                            )}
                        </div>
                        {(agents as any[])?.length > 0 ? (
                            <div className="agent-grid">
                                {(agents as any[]).map((agent: any) => (
                                    <div
                                        key={agent.id}
                                        className="agent-card"
                                        style={{ position: 'relative' }}
                                    >
                                        <div
                                            className="agent-card-main"
                                            onClick={() => handleAgentClick(agent.id)}
                                            style={{ flex: 1, cursor: 'pointer' }}
                                        >
                                            <div className="agent-avatar">
                                                {agent.avatar || agent.name?.charAt(0)?.toUpperCase() || '?'}
                                            </div>
                                            <div className="agent-info">
                                                <h4>{agent.name}</h4>
                                                <p>{agent.title || (isChinese ? '数字员工' : 'Digital Employee')}</p>
                                            </div>
                                            <span className={`agent-status status-${agent.status}`}>
                                                {agent.status}
                                            </span>
                                        </div>
                                        {/* 固定数字员工的设置按钮 - 仅董事长可见 */}
                                        {isChairman && agent.id?.startsWith('employee://digital/') && (
                                            <DigitalEmployeeSettings
                                                agent={agent}
                                                isChairman={isChairman}
                                                onUpdate={(updatedAgent) => {
                                                    // 刷新代理列表
                                                    window.location.reload();
                                                }}
                                            />
                                        )}
                                        {/* 普通固定数字员工不需要直接对话按钮，通过部门大脑协调 */}
                                        {/* 只有董事长可以与任意固定数字员工直接对话 */}
                                        {isChairman && (
                                            <button
                                                className="btn btn-primary btn-sm"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    handleStartChat(agent.id);
                                                }}
                                                style={{
                                                    marginTop: '8px',
                                                    width: '100%',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'center',
                                                    gap: '6px',
                                                }}
                                            >
                                                <IconMessage size={14} />
                                                {isChinese ? '对话' : 'Chat'}
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="empty-state-small">
                                {isChinese ? '暂无数字员工' : 'No agents'}
                            </div>
                        )}
                    </div>
                    
                    <div className="dept-section">
                        <div className="dept-section-header">
                            <IconUsers size={18} />
                            <h2>{isChinese ? '部门成员' : 'Team Members'}</h2>
                        </div>
                        {(members as any[])?.length > 0 ? (
                            <div className="member-list">
                                {(members as any[]).map((member: any) => (
                                    <div key={member.id} className="member-item">
                                        <div className="member-avatar">
                                            {member.display_name?.charAt(0)?.toUpperCase() || member.username?.charAt(0)?.toUpperCase() || member.name?.charAt(0)?.toUpperCase() || '?'}
                                        </div>
                                        <div className="member-info">
                                            <span className="member-name">{member.display_name || member.username || member.name}</span>
                                            <span className="member-title">{member.title || (isChinese ? '成员' : 'Member')}</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="empty-state-small">
                                {isChinese ? '暂无成员' : 'No members'}
                            </div>
                        )}
                    </div>
                    
                    <div className="dept-section">
                        <div className="dept-section-header">
                            <IconPlayerPlay size={18} />
                            <h2>{isChinese ? '部门动态' : 'Recent Activity'}</h2>
                        </div>
                        <div className="activity-list">
                            <div className="activity-item">
                                <div className="activity-icon">📋</div>
                                <div className="activity-content">
                                    <span className="activity-text">{isChinese ? '暂无最新动态' : 'No recent activity'}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
