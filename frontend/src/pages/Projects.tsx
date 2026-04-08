import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores';
import { projectApi, departmentApi } from '../services/api';
import { DEPARTMENTS, type Project, type ProjectTask, type ProjectStatus } from '../types';
import {
    IconPlus,
    IconFolder,
    IconCalendar,
    IconUsers,
    IconChevronRight,
    IconCheck,
    IconClock,
    IconAlertTriangle,
    IconTrash,
    IconEdit,
    IconEye,
} from '@tabler/icons-react';

const statusColors: Record<ProjectStatus, string> = {
    planning: 'var(--warning)',
    active: 'var(--success)',
    on_hold: 'var(--text-tertiary)',
    completed: 'var(--accent-primary)',
    cancelled: 'var(--error)',
};

const statusLabels: Record<ProjectStatus, string> = {
    planning: '规划中',
    active: '进行中',
    on_hold: '暂停',
    completed: '已完成',
    cancelled: '已取消',
};

export default function Projects() {
    const { t, i18n } = useTranslation();
    const isChinese = i18n.language?.startsWith('zh');
    const queryClient = useQueryClient();
    const user = useAuthStore((s) => s.user);
    
    const [selectedDepartment, setSelectedDepartment] = useState<string>('');
    const [selectedStatus, setSelectedStatus] = useState<string>('');
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [selectedProject, setSelectedProject] = useState<Project | null>(null);
    
    const { data: projects = [], isLoading } = useQuery({
        queryKey: ['projects', selectedDepartment, selectedStatus],
        queryFn: () => projectApi.list(selectedDepartment || undefined, selectedStatus || undefined),
    });
    
    const { data: departments = [] } = useQuery({
        queryKey: ['departments'],
        queryFn: () => departmentApi.list(),
    });
    
    const createMutation = useMutation({
        mutationFn: (data: any) => projectApi.create(data),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['projects'] });
            setShowCreateModal(false);
        },
    });
    
    const deleteMutation = useMutation({
        mutationFn: (id: string) => projectApi.delete(id),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['projects'] });
            setSelectedProject(null);
        },
    });

    return (
        <div className="page-container">
            <div className="page-header">
                <div>
                    <h1 className="page-title">{isChinese ? '项目管理' : 'Projects'}</h1>
                    <p className="page-subtitle">{isChinese ? '管理企业项目和任务' : 'Manage enterprise projects and tasks'}</p>
                </div>
                <button className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
                    <IconPlus size={16} stroke={1.5} />
                    <span>{isChinese ? '新建项目' : 'New Project'}</span>
                </button>
            </div>
            
            <div className="page-filters">
                <select
                    className="form-select"
                    value={selectedDepartment}
                    onChange={(e) => setSelectedDepartment(e.target.value)}
                >
                    <option value="">{isChinese ? '所有部门' : 'All Departments'}</option>
                    {Object.entries(DEPARTMENTS).map(([code, info]) => (
                        <option key={code} value={code}>{info.icon} {isChinese ? info.name : info.name_en}</option>
                    ))}
                </select>
                <select
                    className="form-select"
                    value={selectedStatus}
                    onChange={(e) => setSelectedStatus(e.target.value)}
                >
                    <option value="">{isChinese ? '所有状态' : 'All Status'}</option>
                    {Object.entries(statusLabels).map(([status, label]) => (
                        <option key={status} value={status}>{label}</option>
                    ))}
                </select>
            </div>
            
            {isLoading ? (
                <div className="loading-state">{isChinese ? '加载中...' : 'Loading...'}</div>
            ) : projects.length === 0 ? (
                <div className="empty-state">
                    <IconFolder size={48} stroke={1} />
                    <p>{isChinese ? '暂无项目' : 'No projects yet'}</p>
                </div>
            ) : (
                <div className="projects-grid">
                    {(projects as Project[]).map((project) => (
                        <div
                            key={project.id}
                            className="project-card"
                            onClick={() => setSelectedProject(project)}
                        >
                            <div className="project-card-header">
                                <h3 className="project-card-title">{project.name}</h3>
                                <span
                                    className="project-status-badge"
                                    style={{ background: statusColors[project.status] }}
                                >
                                    {statusLabels[project.status]}
                                </span>
                            </div>
                            <p className="project-card-desc">{project.description || (isChinese ? '暂无描述' : 'No description')}</p>
                            <div className="project-card-meta">
                                {project.department_id && (
                                    <span className="project-meta-item">
                                        {DEPARTMENTS[project.department_id as keyof typeof DEPARTMENTS]?.icon || '🏢'}
                                        {' '}
                                        {DEPARTMENTS[project.department_id as keyof typeof DEPARTMENTS]?.name || project.department_id}
                                    </span>
                                )}
                                {project.progress !== undefined && (
                                    <span className="project-meta-item">
                                        <div className="progress-bar">
                                            <div className="progress-fill" style={{ width: `${project.progress}%` }} />
                                        </div>
                                        <span>{project.progress}%</span>
                                    </span>
                                )}
                            </div>
                            <div className="project-card-footer">
                                <span className="project-date">
                                    <IconCalendar size={14} />
                                    {project.start_date ? new Date(project.start_date).toLocaleDateString() : '-'}
                                </span>
                                {project.team_members && project.team_members.length > 0 && (
                                    <span className="project-team">
                                        <IconUsers size={14} />
                                        {project.team_members.length}
                                    </span>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}
            
            {showCreateModal && (
                <CreateProjectModal
                    onClose={() => setShowCreateModal(false)}
                    onSubmit={(data) => createMutation.mutate(data)}
                    loading={createMutation.isPending}
                    isChinese={isChinese}
                />
            )}
            
            {selectedProject && (
                <ProjectDetailModal
                    project={selectedProject}
                    onClose={() => setSelectedProject(null)}
                    onDelete={() => deleteMutation.mutate(selectedProject.id)}
                    isChinese={isChinese}
                />
            )}
        </div>
    );
}

function CreateProjectModal({ onClose, onSubmit, loading, isChinese }: {
    onClose: () => void;
    onSubmit: (data: any) => void;
    loading: boolean;
    isChinese: boolean;
}) {
    const [form, setForm] = useState({
        name: '',
        description: '',
        department_id: '',
        start_date: '',
        end_date: '',
        budget: '',
    });
    
    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        onSubmit({
            ...form,
            budget: form.budget ? parseFloat(form.budget) : undefined,
        });
    };
    
    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isChinese ? '新建项目' : 'New Project'}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>
                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="form-group">
                            <label>{isChinese ? '项目名称' : 'Project Name'}</label>
                            <input
                                className="form-input"
                                value={form.name}
                                onChange={(e) => setForm({ ...form, name: e.target.value })}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>{isChinese ? '描述' : 'Description'}</label>
                            <textarea
                                className="form-textarea"
                                value={form.description}
                                onChange={(e) => setForm({ ...form, description: e.target.value })}
                                rows={3}
                            />
                        </div>
                        <div className="form-group">
                            <label>{isChinese ? '所属部门' : 'Department'}</label>
                            <select
                                className="form-select"
                                value={form.department_id}
                                onChange={(e) => setForm({ ...form, department_id: e.target.value })}
                            >
                                <option value="">{isChinese ? '选择部门' : 'Select Department'}</option>
                                {Object.entries(DEPARTMENTS).map(([code, info]) => (
                                    <option key={code} value={code}>{info.icon} {isChinese ? info.name : info.name_en}</option>
                                ))}
                            </select>
                        </div>
                        <div className="form-row">
                            <div className="form-group">
                                <label>{isChinese ? '开始日期' : 'Start Date'}</label>
                                <input
                                    type="date"
                                    className="form-input"
                                    value={form.start_date}
                                    onChange={(e) => setForm({ ...form, start_date: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>{isChinese ? '结束日期' : 'End Date'}</label>
                                <input
                                    type="date"
                                    className="form-input"
                                    value={form.end_date}
                                    onChange={(e) => setForm({ ...form, end_date: e.target.value })}
                                />
                            </div>
                        </div>
                        <div className="form-group">
                            <label>{isChinese ? '预算' : 'Budget'}</label>
                            <input
                                type="number"
                                className="form-input"
                                value={form.budget}
                                onChange={(e) => setForm({ ...form, budget: e.target.value })}
                                placeholder="0.00"
                            />
                        </div>
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn btn-ghost" onClick={onClose}>
                            {isChinese ? '取消' : 'Cancel'}
                        </button>
                        <button type="submit" className="btn btn-primary" disabled={loading}>
                            {loading ? '...' : (isChinese ? '创建' : 'Create')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

function ProjectDetailModal({ project, onClose, onDelete, isChinese }: {
    project: Project;
    onClose: () => void;
    onDelete: () => void;
    isChinese: boolean;
}) {
    const { data: tasks = [] } = useQuery({
        queryKey: ['project-tasks', project.id],
        queryFn: () => projectApi.getTasks(project.id),
    });
    
    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content modal-lg" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <div>
                        <h2>{project.name}</h2>
                        <span
                            className="project-status-badge"
                            style={{ background: statusColors[project.status] }}
                        >
                            {statusLabels[project.status]}
                        </span>
                    </div>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>
                <div className="modal-body">
                    <div className="project-detail-section">
                        <h3>{isChinese ? '项目信息' : 'Project Info'}</h3>
                        <div className="project-detail-grid">
                            <div className="detail-item">
                                <span className="detail-label">{isChinese ? '描述' : 'Description'}</span>
                                <span className="detail-value">{project.description || '-'}</span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{isChinese ? '部门' : 'Department'}</span>
                                <span className="detail-value">
                                    {project.department_id && DEPARTMENTS[project.department_id as keyof typeof DEPARTMENTS]?.name}
                                </span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{isChinese ? '进度' : 'Progress'}</span>
                                <span className="detail-value">{project.progress || 0}%</span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{isChinese ? '预算' : 'Budget'}</span>
                                <span className="detail-value">{project.budget ? `¥${project.budget.toLocaleString()}` : '-'}</span>
                            </div>
                        </div>
                    </div>
                    
                    <div className="project-detail-section">
                        <h3>{isChinese ? '任务列表' : 'Tasks'}</h3>
                        {(tasks as ProjectTask[]).length === 0 ? (
                            <div className="empty-state-small">{isChinese ? '暂无任务' : 'No tasks'}</div>
                        ) : (
                            <div className="task-list">
                                {(tasks as ProjectTask[]).map((task) => (
                                    <div key={task.id} className="task-item">
                                        <div className="task-status-icon">
                                            {task.status === 'completed' ? (
                                                <IconCheck size={16} />
                                            ) : task.status === 'blocked' ? (
                                                <IconAlertTriangle size={16} />
                                            ) : (
                                                <IconClock size={16} />
                                            )}
                                        </div>
                                        <div className="task-content">
                                            <span className="task-title">{task.title}</span>
                                            <span className="task-assignee">{task.assignee_name || '-'}</span>
                                        </div>
                                        <span className={`task-priority task-priority-${task.priority}`}>
                                            {task.priority}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
                <div className="modal-footer">
                    <button className="btn btn-danger" onClick={onDelete}>
                        <IconTrash size={16} />
                        {isChinese ? '删除项目' : 'Delete'}
                    </button>
                    <button className="btn btn-ghost" onClick={onClose}>
                        {isChinese ? '关闭' : 'Close'}
                    </button>
                </div>
            </div>
        </div>
    );
}
