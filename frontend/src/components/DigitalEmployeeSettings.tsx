/**
 * 数字员工设置组件
 * 只有董事长可以设置固定数字员工的权限和参数
 */

import { useState, useEffect } from 'react';
import { agentApi } from '../services/api';
import type { Agent } from '../types';
import './DigitalEmployeeSettings.css';

interface DigitalEmployeeSettingsProps {
    agent: Agent;
    isChairman: boolean;
    onUpdate?: (updatedAgent: Agent) => void;
}

interface AgentConfig {
    maxConcurrentTasks: number;
    autoResponse: boolean;
    allowedChannels: string[];
    restrictedSkills: string[];
    workingHours: {
        start: string;
        end: string;
    };
}

export default function DigitalEmployeeSettings({ agent, isChairman, onUpdate }: DigitalEmployeeSettingsProps) {
    const [config, setConfig] = useState<AgentConfig>({
        maxConcurrentTasks: 5,
        autoResponse: true,
        allowedChannels: ['chat', 'email'],
        restrictedSkills: [],
        workingHours: { start: '09:00', end: '18:00' }
    });
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);

    // 只有董事长可以查看和修改设置
    if (!isChairman) {
        return null;
    }

    useEffect(() => {
        // 加载数字员工的当前配置
        loadAgentConfig();
    }, [agent.id]);

    const loadAgentConfig = async () => {
        setLoading(true);
        try {
            // 从后端获取数字员工的配置
            const response = await agentApi.getConfig(agent.id);
            if (response) {
                setConfig(prev => ({ ...prev, ...response }));
            }
        } catch (error) {
            console.error('加载数字员工配置失败:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            await agentApi.updateConfig(agent.id, config);
            onUpdate?.(agent);
            alert('设置已保存');
        } catch (error) {
            console.error('保存配置失败:', error);
            alert('保存失败，请重试');
        } finally {
            setSaving(false);
        }
    };

    if (loading) {
        return <div className="settings-loading">加载中...</div>;
    }

    return (
        <div className="digital-employee-settings">
            <h4 className="settings-title">数字员工设置（仅董事长）</h4>
            
            <div className="settings-section">
                <h5>基本参数</h5>
                <div className="setting-item">
                    <label>最大并发任务数</label>
                    <input
                        type="number"
                        min={1}
                        max={20}
                        value={config.maxConcurrentTasks}
                        onChange={(e) => setConfig(prev => ({
                            ...prev,
                            maxConcurrentTasks: parseInt(e.target.value) || 1
                        }))}
                    />
                </div>
                
                <div className="setting-item">
                    <label>自动响应</label>
                    <input
                        type="checkbox"
                        checked={config.autoResponse}
                        onChange={(e) => setConfig(prev => ({
                            ...prev,
                            autoResponse: e.target.checked
                        }))}
                    />
                </div>
            </div>

            <div className="settings-section">
                <h5>工作时间</h5>
                <div className="setting-item time-range">
                    <label>开始时间</label>
                    <input
                        type="time"
                        value={config.workingHours.start}
                        onChange={(e) => setConfig(prev => ({
                            ...prev,
                            workingHours: { ...prev.workingHours, start: e.target.value }
                        }))}
                    />
                    <label>结束时间</label>
                    <input
                        type="time"
                        value={config.workingHours.end}
                        onChange={(e) => setConfig(prev => ({
                            ...prev,
                            workingHours: { ...prev.workingHours, end: e.target.value }
                        }))}
                    />
                </div>
            </div>

            <div className="settings-section">
                <h5>权限控制</h5>
                <div className="setting-item">
                    <label>允许的对话通道</label>
                    <div className="checkbox-group">
                        {['chat', 'email', 'voice', 'meeting'].map(channel => (
                            <label key={channel} className="checkbox-label">
                                <input
                                    type="checkbox"
                                    checked={config.allowedChannels.includes(channel)}
                                    onChange={(e) => {
                                        const newChannels = e.target.checked
                                            ? [...config.allowedChannels, channel]
                                            : config.allowedChannels.filter(c => c !== channel);
                                        setConfig(prev => ({ ...prev, allowedChannels: newChannels }));
                                    }}
                                />
                                {channel === 'chat' && '聊天'}
                                {channel === 'email' && '邮件'}
                                {channel === 'voice' && '语音'}
                                {channel === 'meeting' && '会议'}
                            </label>
                        ))}
                    </div>
                </div>
            </div>

            <div className="settings-actions">
                <button 
                    className="btn-save"
                    onClick={handleSave}
                    disabled={saving}
                >
                    {saving ? '保存中...' : '保存设置'}
                </button>
            </div>
        </div>
    );
}
