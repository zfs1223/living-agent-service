import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '../stores';
import { projectApi, projectActionApi, departmentApi } from '../services/api';
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
    planning: 'Planning',
    active: 'Active',
    on_hold: 'On Hold',
    completed: 'Completed',
    cancelled: 'Cancelled',
};

export default function Projects() {
    const { t, i18n } = useTranslation();
    const queryClient = useQueryClient();
    const user = useAuthStore((s) => s.user);

    const tStatusLabels: Record<ProjectStatus, string> = {
        planning: t('projects.statusPlanning'),
        active: t('projects.statusActive'),
        on_hold: t('projects.statusOnHold'),
        completed: t('projects.statusCompleted'),
        cancelled: t('projects.statusCancelled'),
    };
    
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

    const { data: statistics } = useQuery({
        queryKey: ['project-statistics'],
        queryFn: () => projectActionApi.getStatistics(),
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
        <div className="page-container" style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
            <div style={{
                borderRadius: '24px',
                padding: '22px',
                background: 'linear-gradient(135deg, rgba(96,165,250,0.12), rgba(12,18,28,0.84) 50%, rgba(5,6,10,0.96))',
                border: '1px solid rgba(255,255,255,0.08)',
                boxShadow: '0 24px 60px rgba(0,0,0,0.18)',
            }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '18px', alignItems: 'flex-start' }}>
                    <div style={{ maxWidth: '760px' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '8px', padding: '6px 10px', borderRadius: '999px', background: 'rgba(255,255,255,0.08)', color: 'var(--text-secondary)', fontSize: '12px', marginBottom: '14px' }}>
                            <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: 'var(--accent-primary)', boxShadow: '0 0 18px rgba(96,165,250,0.85)' }} />
                            {t('projects.commandCenter')}
                        </div>
                        <h1 className="page-title" style={{ margin: 0, fontSize: '28px', lineHeight: 1.1 }}>{t('projects.title')}</h1>
                        <p className="page-subtitle" style={{ marginTop: '10px', maxWidth: '68ch', lineHeight: 1.75 }}>
                            {t('projects.subtitle')}
                        </p>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: '10px', minWidth: '320px' }}>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('projects.projectCount')}</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px' }}>{projects.length}</div>
                        </div>
                        <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('projects.activeCount', '进行中')}</div>
                            <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', color: 'var(--success)' }}>
                                {(statistics as Record<string, any>)?.active ?? (statistics as Record<string, any>)?.activeCount ?? projects.filter((p: Project) => p.status === 'active').length}
                            </div>
                        </div>
                        {(statistics as Record<string, any>)?.completed !== undefined && (
                            <div style={{ padding: '12px 14px', borderRadius: '16px', background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.08)' }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('projects.completedCount', '已完成')}</div>
                                <div style={{ fontSize: '22px', fontWeight: 700, marginTop: '6px', color: 'var(--accent-primary)' }}>
                                    {(statistics as Record<string, any>)?.completed}
                                </div>
                            </div>
                        )}
                        <button className="btn btn-primary" onClick={() => setShowCreateModal(true)} style={{ height: '100%', justifyContent: 'center' }}>
                            <IconPlus size={16} stroke={1.5} />
                            <span>{t('projects.newProject')}</span>
                        </button>
                    </div>
                </div>
            </div>
            
            <div className="page-filters" style={{ padding: '12px 14px', borderRadius: '18px', background: 'rgba(255,255,255,0.03)', border: '1px solid var(--border-subtle)' }}>
                <select
                    className="form-select"
                    value={selectedDepartment}
                    onChange={(e) => setSelectedDepartment(e.target.value)}
                >
                    <option value="">{t('projects.allDepartments')}</option>
                    {Object.entries(DEPARTMENTS).map(([code, info]) => (
                        <option key={code} value={code}>{info.icon} {i18n.language?.startsWith('zh') ? info.name : info.name_en}</option>
                    ))}
                </select>
                <select
                    className="form-select"
                    value={selectedStatus}
                    onChange={(e) => setSelectedStatus(e.target.value)}
                >
                    <option value="">{t('projects.allStatus')}</option>
                    {Object.entries(tStatusLabels).map(([status, label]) => (
                        <option key={status} value={status}>{label}</option>
                    ))}
                </select>
            </div>
            
            {isLoading ? (
                <div className="loading-state">{t('projects.loading')}</div>
            ) : projects.length === 0 ? (
                <div className="empty-state">
                    <IconFolder size={48} stroke={1} />
                    <p>{t('projects.noProjects')}</p>
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
                                    {tStatusLabels[project.status]}
                                </span>
                            </div>
                            <p className="project-card-desc">{project.description || t('projects.noDescription')}</p>
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
                />
            )}
            
            {selectedProject && (
                <ProjectDetailModal
                    project={selectedProject}
                    onClose={() => setSelectedProject(null)}
                    onDelete={() => deleteMutation.mutate(selectedProject.id)}
                />
            )}
        </div>
    );
}

function CreateProjectModal({ onClose, onSubmit, loading }: {
    onClose: () => void;
    onSubmit: (data: any) => void;
    loading: boolean;
}) {
    const { t, i18n } = useTranslation();
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
                    <h2>{t('projects.createProject')}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>
                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="form-group">
                            <label>{t('projects.projectName')}</label>
                            <input
                                className="form-input"
                                value={form.name}
                                onChange={(e) => setForm({ ...form, name: e.target.value })}
                                required
                            />
                        </div>
                        <div className="form-group">
                            <label>{t('projects.description')}</label>
                            <textarea
                                className="form-textarea"
                                value={form.description}
                                onChange={(e) => setForm({ ...form, description: e.target.value })}
                                rows={3}
                            />
                        </div>
                        <div className="form-group">
                            <label>{t('projects.department')}</label>
                            <select
                                className="form-select"
                                value={form.department_id}
                                onChange={(e) => setForm({ ...form, department_id: e.target.value })}
                            >
                                <option value="">{t('projects.selectDepartment')}</option>
                                {Object.entries(DEPARTMENTS).map(([code, info]) => (
                                    <option key={code} value={code}>{info.icon} {i18n.language?.startsWith('zh') ? info.name : info.name_en}</option>
                                ))}
                            </select>
                        </div>
                        <div className="form-row">
                            <div className="form-group">
                                <label>{t('projects.startDate')}</label>
                                <input
                                    type="date"
                                    className="form-input"
                                    value={form.start_date}
                                    onChange={(e) => setForm({ ...form, start_date: e.target.value })}
                                />
                            </div>
                            <div className="form-group">
                                <label>{t('projects.endDate')}</label>
                                <input
                                    type="date"
                                    className="form-input"
                                    value={form.end_date}
                                    onChange={(e) => setForm({ ...form, end_date: e.target.value })}
                                />
                            </div>
                        </div>
                        <div className="form-group">
                            <label>{t('projects.budget')}</label>
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
                            {t('projects.cancel')}
                        </button>
                        <button type="submit" className="btn btn-primary" disabled={loading}>
                            {loading ? '...' : t('projects.create')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

function ProjectDetailModal({ project, onClose, onDelete }: {
    project: Project;
    onClose: () => void;
    onDelete: () => void;
}) {
    const { t } = useTranslation();
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
                        <h3>{t('projects.projectInfo')}</h3>
                        <div className="project-detail-grid">
                            <div className="detail-item">
                                <span className="detail-label">{t('projects.description')}</span>
                                <span className="detail-value">{project.description || '-'}</span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{t('projects.department')}</span>
                                <span className="detail-value">
                                    {project.department_id && DEPARTMENTS[project.department_id as keyof typeof DEPARTMENTS]?.name}
                                </span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{t('projects.progress')}</span>
                                <span className="detail-value">{project.progress || 0}%</span>
                            </div>
                            <div className="detail-item">
                                <span className="detail-label">{t('projects.budget')}</span>
                                <span className="detail-value">{project.budget ? `¥${project.budget.toLocaleString()}` : '-'}</span>
                            </div>
                        </div>
                    </div>
                    
                    <div className="project-detail-section">
                        <h3>{t('projects.tasks')}</h3>
                        {(tasks as ProjectTask[]).length === 0 ? (
                            <div className="empty-state-small">{t('projects.noTasks')}</div>
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
                        {t('projects.deleteProject')}
                    </button>
                    <button className="btn btn-ghost" onClick={onClose}>
                        {t('projects.close')}
                    </button>
                </div>
            </div>
        </div>
    );
}
