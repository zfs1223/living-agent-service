import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { fixedEmployeeApi } from '../services/api';
import { IconSettings, IconTools, IconBrain, IconMessage } from '@tabler/icons-react';

interface FixedEmployeeSettingsProps {
    employeeId: string;
    employeeCode?: string;
    employeeName?: string;
}

export default function FixedEmployeeSettings({ employeeId, employeeCode, employeeName }: FixedEmployeeSettingsProps) {
    const { t } = useTranslation();
    const [definition, setDefinition] = useState<any>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [activeTab, setActiveTab] = useState<'basic' | 'capabilities' | 'tools' | 'channels'>('basic');

    useEffect(() => {
        const loadDefinition = async () => {
            if (!employeeCode) {
                setLoading(false);
                return;
            }
            
            try {
                setLoading(true);
                const definitions = await fixedEmployeeApi.getAllDefinitions();
                const matched = definitions.find((d: any) => 
                    d.name === employeeCode || d.name === employeeName
                );
                if (matched) {
                    setDefinition(matched);
                } else {
                    setError('未找到该固定数字员工的定义');
                }
            } catch (err: any) {
                setError(err?.message || '加载失败');
            } finally {
                setLoading(false);
            }
        };

        loadDefinition();
    }, [employeeCode, employeeName]);

    if (loading) {
        return (
            <div className="card" style={{ marginBottom: '12px', borderColor: 'var(--accent-primary)' }}>
                <div style={{ padding: '20px', textAlign: 'center', color: 'var(--text-secondary)' }}>
                    加载中...
                </div>
            </div>
        );
    }

    if (error || !definition) {
        return (
            <div className="card" style={{ marginBottom: '12px', borderColor: 'var(--accent-primary)' }}>
                <div style={{ padding: '20px', textAlign: 'center', color: 'var(--error)' }}>
                    {error || '未找到固定数字员工定义'}
                </div>
            </div>
        );
    }

    const tabs = [
        { key: 'basic', label: '基本信息', icon: IconSettings },
        { key: 'capabilities', label: '能力', icon: IconBrain },
        { key: 'tools', label: '工具', icon: IconTools },
        { key: 'channels', label: '通道', icon: IconMessage },
    ];

    return (
        <div className="card" style={{ marginBottom: '12px', borderColor: 'var(--accent-primary)' }}>
            <div style={{
                background: 'linear-gradient(135deg, #1a4731 0%, #1a2744 100%)',
                borderRadius: 8,
                padding: 16,
                marginBottom: 16,
                border: '1px solid rgba(34,197,94,0.2)',
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ fontSize: 20 }}>{definition.icon}</span>
                    <span style={{ fontSize: 16, fontWeight: 600, color: '#e2e8f0' }}>
                        {definition.title} ({definition.name})
                    </span>
                    <span style={{
                        fontSize: 11,
                        padding: '2px 8px',
                        borderRadius: 4,
                        background: '#22c55e',
                        color: '#fff',
                    }}>
                        编制员工
                    </span>
                </div>
                <div style={{ fontSize: 12, color: '#86efac', marginTop: 8 }}>
                    此数字员工由系统编制定义，核心配置受保护。通用设置（Token限制、触发器等）可在下方通用配置区域调整。
                </div>
            </div>
            
            {/* Tabs */}
            <div style={{ 
                display: 'flex', 
                gap: '4px', 
                marginBottom: '16px',
                borderBottom: '1px solid var(--border-subtle)',
                paddingBottom: '8px'
            }}>
                {tabs.map(tab => (
                    <button
                        key={tab.key}
                        onClick={() => setActiveTab(tab.key as any)}
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            padding: '8px 16px',
                            border: 'none',
                            background: activeTab === tab.key ? 'var(--accent-primary)' : 'transparent',
                            color: activeTab === tab.key ? 'white' : 'var(--text-secondary)',
                            borderRadius: '6px',
                            cursor: 'pointer',
                            fontSize: '13px',
                            fontWeight: 500,
                        }}
                    >
                        <tab.icon size={16} />
                        {tab.label}
                    </button>
                ))}
            </div>

            {/* Tab Content */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                {activeTab === 'basic' && (
                    <>
                        {/* Code & Department */}
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: '1fr 1fr',
                            gap: '12px',
                        }}>
                            <div style={{
                                padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px',
                                border: '1px solid var(--border-subtle)',
                            }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>编号</div>
                                <div style={{ fontWeight: 500, fontSize: '13px' }}>{definition.code}</div>
                            </div>
                            <div style={{
                                padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px',
                                border: '1px solid var(--border-subtle)',
                            }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>部门</div>
                                <div style={{ fontWeight: 500, fontSize: '13px' }}>{definition.departmentName}</div>
                            </div>
                        </div>

                        {/* Neuron ID */}
                        <div style={{
                            padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px',
                            border: '1px solid var(--border-subtle)',
                        }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '4px' }}>神经元 ID</div>
                            <div style={{ fontWeight: 500, fontSize: '13px', fontFamily: 'monospace' }}>{definition.neuronId}</div>
                        </div>

                        {/* Roles */}
                        <div style={{
                            padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px',
                            border: '1px solid var(--border-subtle)',
                        }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>角色职责</div>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                                {definition.roles?.map((role: string, idx: number) => (
                                    <span key={idx} style={{
                                        padding: '4px 10px',
                                        background: 'var(--accent-primary)',
                                        color: 'white',
                                        borderRadius: '4px',
                                        fontSize: '12px',
                                    }}>
                                        {role}
                                    </span>
                                ))}
                            </div>
                        </div>

                        {/* Personality */}
                        {definition.personality && (
                            <div style={{
                                padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px',
                                border: '1px solid var(--border-subtle)',
                            }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>个性特征</div>
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                                    {[
                                        { key: 'rigor', label: '严谨性', value: definition.personality.rigor },
                                        { key: 'creativity', label: '创造力', value: definition.personality.creativity },
                                        { key: 'riskTolerance', label: '风险承受', value: definition.personality.riskTolerance },
                                        { key: 'obedience', label: '服从性', value: definition.personality.obedience },
                                    ].map(({ label, value }) => (
                                        <div key={label} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                            <span style={{ fontSize: '12px', color: 'var(--text-secondary)', minWidth: '60px' }}>{label}</span>
                                            <div style={{ flex: 1, height: '6px', background: 'var(--border-subtle)', borderRadius: '3px', overflow: 'hidden' }}>
                                                <div style={{ width: `${(value || 0) * 100}%`, height: '100%', background: 'var(--accent-primary)', borderRadius: '3px' }} />
                                            </div>
                                            <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', minWidth: '30px', textAlign: 'right' }}>
                                                {Math.round((value || 0) * 100)}%
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </>
                )}

                {activeTab === 'capabilities' && (
                    <div style={{
                        padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px',
                        border: '1px solid var(--border-subtle)',
                    }}>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>核心能力</div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                            {definition.capabilities?.map((cap: string, idx: number) => (
                                <div key={idx} style={{
                                    display: 'flex', alignItems: 'center', gap: '8px',
                                    padding: '8px 12px', background: 'var(--bg-secondary)', borderRadius: '6px',
                                }}>
                                    <span style={{ color: 'var(--accent-primary)' }}>✓</span>
                                    <span style={{ fontSize: '13px' }}>{cap}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {activeTab === 'tools' && (
                    <div style={{
                        padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px',
                        border: '1px solid var(--border-subtle)',
                    }}>
                        <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>可用工具</div>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                            {definition.tools?.map((tool: string, idx: number) => (
                                <span key={idx} style={{
                                    padding: '6px 12px',
                                    background: 'var(--bg-secondary)',
                                    border: '1px solid var(--border-subtle)',
                                    borderRadius: '4px',
                                    fontSize: '12px',
                                    fontFamily: 'monospace',
                                }}>
                                    {tool}
                                </span>
                            ))}
                        </div>
                    </div>
                )}

                {activeTab === 'channels' && (
                    <>
                        <div style={{
                            padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px',
                            border: '1px solid var(--border-subtle)',
                        }}>
                            <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>对话通道</div>
                            <div style={{ fontWeight: 500, fontSize: '13px', fontFamily: 'monospace' }}>
                                {definition.channel}
                            </div>
                        </div>

                        {definition.requiredSkills && definition.requiredSkills.length > 0 && (
                            <div style={{
                                padding: '10px 14px', background: 'var(--bg-elevated)', borderRadius: '8px',
                                border: '1px solid var(--border-subtle)',
                            }}>
                                <div style={{ fontSize: '11px', color: 'var(--text-tertiary)', marginBottom: '8px' }}>必需技能</div>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                                    {definition.requiredSkills?.map((skill: string, idx: number) => (
                                        <span key={idx} style={{
                                            padding: '4px 10px',
                                            background: 'var(--warning)',
                                            color: 'var(--bg-primary)',
                                            borderRadius: '4px',
                                            fontSize: '12px',
                                        }}>
                                            {skill}
                                        </span>
                                    ))}
                                </div>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}