/**
 * 数字员工设置组件
 * 只有董事长可以设置固定数字员工的权限和参数
 */

import { useState, useEffect } from 'react';
import { agentApi } from '../services/api';
import type { Agent } from '../types';
import { useToastStore } from '../stores/toastStore';
import './DigitalEmployeeSettings.css';

interface DigitalEmployeeSettingsProps {
    agent: Agent;
    isEnterprise: boolean;
    onUpdate?: (updatedAgent: Agent) => void;
}

// 后端返回的 AgentConfig 格式
interface BackendAgentConfig {
    agentId?: string;
    name?: string;
    maxConcurrentTasks?: number;
    autoResponse?: boolean;
    workingHours?: string;  // 后端返回 "09:00-18:00" 格式
    allowedChannels?: string[];
}

// 前端使用的 AgentConfig 格式
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

export default function DigitalEmployeeSettings({ agent, isEnterprise, onUpdate }: DigitalEmployeeSettingsProps) {
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
    if (!isEnterprise) {
        return null;
    }

    useEffect(() => {
        // 加载数字员工的当前配置
        loadAgentConfig();
    }, [agent.id]);

    // 将后端 workingHours 字符串 "09:00-18:00" 转换为前端对象格式
    const parseWorkingHours = (hours: string | undefined): { start: string; end: string } => {
        if (!hours || !hours.includes('-')) {
            return { start: '09:00', end: '18:00' };
        }
        const [start, end] = hours.split('-');
        return { start: start.trim(), end: end.trim() };
    };

    // 将前端 workingHours 对象转换为后端字符串格式
    const formatWorkingHours = (hours: { start: string; end: string }): string => {
        return `${hours.start}-${hours.end}`;
    };

    const loadAgentConfig = async () => {
        setLoading(true);
        try {
            // 从后端获取数字员工的配置
            const response = await agentApi.getConfig(agent.id);
            if (response) {
                const backendConfig = response as BackendAgentConfig;
                setConfig({
                    maxConcurrentTasks: backendConfig.maxConcurrentTasks ?? 5,
                    autoResponse: backendConfig.autoResponse ?? true,
                    allowedChannels: backendConfig.allowedChannels ?? ['chat', 'email'],
                    restrictedSkills: [], // 后端暂无此字段，使用默认值
                    workingHours: parseWorkingHours(backendConfig.workingHours)
                });
            }
        } catch (error) {
            console.error('加载数字员工配置失败:', error);
            // 使用默认配置
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            // 将前端配置转换为后端格式
            const backendConfig: BackendAgentConfig = {
                agentId: agent.id,
                name: agent.name,
                maxConcurrentTasks: config.maxConcurrentTasks,
                autoResponse: config.autoResponse,
                workingHours: formatWorkingHours(config.workingHours),
                allowedChannels: config.allowedChannels
            };
            await agentApi.updateConfig(agent.id, backendConfig);
            onUpdate?.(agent);
            useToastStore.getState().showToast('设置已保存', 'success');
        } catch (error) {
            console.error('保存配置失败:', error);
            useToastStore.getState().showToast('保存失败，请重试', 'error');
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
