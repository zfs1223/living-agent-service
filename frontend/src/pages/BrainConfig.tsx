import { useState, useRef, useEffect, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { brainModelApi } from '@/services/brainModelApi';
import { modelPoolApi } from '@/services/modelPoolApi';
import type { LlmModel } from '@/types/modelPool';

const BRAIN_ID_MAP: Record<string, string> = {
  main: 'neuron://core/main-brain/001',
  tech: 'neuron://tech/tech-brain/001',
  admin: 'neuron://admin/admin-brain/001',
  hr: 'neuron://hr/hr-brain/001',
  finance: 'neuron://finance/finance-brain/001',
  sales: 'neuron://sales/sales-brain/001',
  cs: 'neuron://cs/cs-brain/001',
  ops: 'neuron://ops/ops-brain/001',
  legal: 'neuron://legal/legal-brain/001',
};

const BRAIN_LABELS: Record<string, string> = {
  main: '主大脑',
  tech: '技术大脑',
  admin: '行政大脑',
  hr: '人力大脑',
  finance: '财务大脑',
  sales: '销售大脑',
  cs: '客服大脑',
  ops: '运营大脑',
  legal: '法务大脑',
};

const BRAIN_DESCRIPTIONS: Record<string, string> = {
  main: '复杂推理、跨部门协调、战略决策',
  tech: '代码审查、CI/CD、架构设计',
  admin: '文档处理、文案创作、行政事务',
  hr: '招聘管理、考勤、绩效',
  finance: '报销审批、发票、预算',
  sales: '销售支持、市场营销',
  cs: '工单处理、问题解答',
  ops: '数据分析、运营策略',
  legal: '合同审查、合规检查',
};

const BRAIN_COLORS: Record<string, string> = {
  main: '#6366f1',
  tech: '#3b82f6',
  admin: '#10b981',
  hr: '#f59e0b',
  finance: '#ef4444',
  sales: '#ec4899',
  cs: '#8b5cf6',
  ops: '#14b8a6',
  legal: '#f97316',
};

interface BrainAssignmentItem {
  brainId: string;
  brainName: string;
  modelId: string | null;
  displayName: string | null;
}

export default function BrainConfig() {
  const queryClient = useQueryClient();
  const [expandedBrain, setExpandedBrain] = useState<string | null>(null);
  const [assignError, setAssignError] = useState<string | null>(null);
  const [lastAction, setLastAction] = useState<{ type: 'assign' | 'clear'; brainKey: string; time: number; isAuto?: boolean } | null>(null);
  const [autoEvolutionEnabled, setAutoEvolutionEnabled] = useState(() => {
    return localStorage.getItem('brain-auto-evolution') === 'true';
  });
  const [dropdownOpen, setDropdownOpen] = useState<string | null>(null);
  const [dropdownPos, setDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const triggerRef = useRef<Record<string, HTMLElement | null>>({});

  useEffect(() => {
    if (!dropdownOpen) return;
    const handler = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (target.closest('[data-dropdown-trigger]') || target.closest('[data-dropdown-panel]')) return;
      setDropdownOpen(null);
      setDropdownPos(null);
    };
    setTimeout(() => document.addEventListener('click', handler), 0);
    return () => document.removeEventListener('click', handler);
  }, [dropdownOpen]);

  const { data: assignments, isLoading: assignmentsLoading } = useQuery({
    queryKey: ['brain-models', 'assignments'],
    queryFn: () => brainModelApi.list(),
  });

  const { data: availableModels, isLoading: modelsLoading } = useQuery({
    queryKey: ['brain-models', 'available-models'],
    queryFn: () => brainModelApi.available(),
  });

  const { data: providers, isLoading: providersLoading } = useQuery({
    queryKey: ['model-pool', 'providers'],
    queryFn: () => modelPoolApi.providers.list(),
  });

  const [mutatingBrainId, setMutatingBrainId] = useState<string | null>(null);
  const [pendingAssignment, setPendingAssignment] = useState<Record<string, BrainAssignmentItem>>({});

  const assignMutation = useMutation({
    mutationFn: ({ brainId, modelId }: { brainId: string; modelId: string }) => {
      setMutatingBrainId(brainId);
      return brainModelApi.assign(brainId, { modelId });
    },
    onSuccess: (data, { brainId }) => {
      setAssignError(null);
      const brainKey = Object.entries(BRAIN_ID_MAP).find(([, id]) => id === brainId)?.[0];
      if (brainKey) setLastAction({ type: 'assign', brainKey, time: Date.now() });
      queryClient.setQueryData(['brain-models', 'assignments'], (old: any[] | undefined) => {
        return [...(old || []).filter((a: any) => a.brainId !== brainId), data];
      });
      queryClient.invalidateQueries({ queryKey: ['brain-models', 'assignments'] });
      queryClient.invalidateQueries({ queryKey: ['brain-models', 'available-models'] });
    },
    onSettled: () => {
      setMutatingBrainId(null);
      setDropdownOpen(null);
      setDropdownPos(null);
    },
    onError: (err: any, { brainId }) => {
      setPendingAssignment(prev => {
        const next = { ...prev };
        delete next[brainId];
        return next;
      });
      setAssignError(err.message || '绑定失败，请重试');
    },
  });

  const clearMutation = useMutation({
    mutationFn: (brainId: string) => {
      setMutatingBrainId(brainId);
      return brainModelApi.clear(brainId);
    },
    onSuccess: (_, brainId) => {
      setAssignError(null);
      const brainKey = Object.entries(BRAIN_ID_MAP).find(([, id]) => id === brainId)?.[0];
      if (brainKey) setLastAction({ type: 'clear', brainKey, time: Date.now() });
      queryClient.setQueryData(['brain-models', 'assignments'], (old: any[] | undefined) => {
        return (old || []).filter((a: any) => a.brainId !== brainId);
      });
      queryClient.invalidateQueries({ queryKey: ['brain-models', 'assignments'] });
      queryClient.invalidateQueries({ queryKey: ['brain-models', 'available-models'] });
    },
    onSettled: () => {
      setMutatingBrainId(null);
      setDropdownOpen(null);
      setDropdownPos(null);
    },
    onError: (err: any) => {
      setAssignError(err.message || '清除失败，请重试');
    },
  });

  const getAssignmentForBrain = (brainKey: string): BrainAssignmentItem | null => {
    const brainId = BRAIN_ID_MAP[brainKey];
    if (pendingAssignment[brainId]) return pendingAssignment[brainId];
    const found = (assignments || []).find((a: any) => a.brainId === brainId);
    if (!found) return null;
    return {
      brainId,
      brainName: found.brainName || BRAIN_LABELS[brainKey],
      modelId: found.modelId,
      displayName: found.displayName || null,
    };
  };

  const toggleAutoEvolution = (enabled: boolean) => {
    setAutoEvolutionEnabled(enabled);
    localStorage.setItem('brain-auto-evolution', enabled ? 'true' : 'false');
  };

  const handleManualRollback = (brainKey: string) => {
    if (lastAction?.type === 'assign' && lastAction.isAuto) {
      const brainId = BRAIN_ID_MAP[brainKey];
      clearMutation.mutate(brainId);
    }
  };

  const getProviderName = (providerId: string) => {
    const p = (providers || []).find((pr: any) => pr.id === providerId);
    return p ? p.displayName : providerId;
  };

  const getCurrentModel = (brainKey: string) => {
    const assignment = getAssignmentForBrain(brainKey);
    if (!assignment?.modelId) return null;
    return (availableModels || []).find((m: LlmModel) => m.id === assignment.modelId);
  };

  const handleModelSelect = useCallback((brainId: string, modelId: string, displayName: string, _providerId: string) => {
    setPendingAssignment(prev => ({
      ...prev,
      [brainId]: { brainId, brainName: displayName, modelId, displayName },
    }));
    setDropdownOpen(null);
    setDropdownPos(null);
    assignMutation.mutate({ brainId, modelId });
  }, [assignMutation]);

  const handleClearModel = useCallback((brainId: string) => {
    setPendingAssignment(prev => {
      const next = { ...prev };
      delete next[brainId];
      return next;
    });
    setDropdownOpen(null);
    setDropdownPos(null);
    clearMutation.mutate(brainId);
  }, [clearMutation]);

  const isLoading = assignmentsLoading || modelsLoading || providersLoading;

  if (isLoading) {
    return (
      <div style={{ padding: 'var(--space-8)', textAlign: 'center', color: 'var(--text-tertiary)' }}>
        加载中...
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
      {assignError && (
        <div style={{
          padding: '10px 14px',
          background: 'var(--error-subtle)',
          color: 'var(--error)',
          borderRadius: 'var(--radius-lg)',
          fontSize: 'var(--text-sm)',
          border: '1px solid rgba(239, 68, 68, 0.2)',
        }}>
          ❌ {assignError}
        </div>
      )}

      {lastAction && (
        <div style={{
          padding: '10px 14px',
          background: 'var(--success-subtle)',
          color: 'var(--success)',
          borderRadius: 'var(--radius-lg)',
          fontSize: 'var(--text-sm)',
          border: '1px solid rgba(34, 197, 94, 0.2)',
        }}>
          ✅ {lastAction.type === 'assign' ? '已绑定' : '已清除'} {BRAIN_LABELS[lastAction.brainKey] || lastAction.brainKey} 的模型配置
        </div>
      )}

      <div style={{ marginBottom: 'var(--space-2)' }}>
        <h3 style={{ fontSize: 'var(--text-lg)', fontWeight: '600', marginBottom: 'var(--space-1)', color: 'var(--text-primary)' }}>
          大脑模型配置
        </h3>
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--text-tertiary)', margin: 0 }}>
          为每个业务大脑分配专用的 LLM 模型。未配置时将使用默认模型。
        </p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
        {Object.entries(BRAIN_LABELS).map(([brainKey, brainLabel]) => {
          const brainId = BRAIN_ID_MAP[brainKey];
          const assignment = getAssignmentForBrain(brainKey);
          const isExpanded = expandedBrain === brainKey;
          const color = BRAIN_COLORS[brainKey] || '#666';
          const description = BRAIN_DESCRIPTIONS[brainKey] || '';
          const currentModel = getCurrentModel(brainKey);
          const isDropdownOpen = dropdownOpen === brainKey;

          return (
            <div
              key={brainKey}
              style={{
                border: `1px solid ${isExpanded ? color : 'var(--border-subtle)'}`,
                borderRadius: 'var(--radius-xl)',
                overflow: 'hidden',
                background: isExpanded ? `${color}08` : 'var(--bg-secondary)',
                transition: 'all 0.2s',
              }}
            >
              <div
                onClick={() => setExpandedBrain(isExpanded ? null : brainKey)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  padding: 'var(--space-4)',
                  cursor: 'pointer',
                  gap: 'var(--space-3)',
                }}
              >
                <div
                  style={{
                    width: '36px',
                    height: '36px',
                    borderRadius: 'var(--radius-md)',
                    background: color,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#fff',
                    fontSize: '14px',
                    fontWeight: '700',
                    flexShrink: 0,
                  }}
                >
                  {brainLabel.charAt(0)}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 'var(--text-base)', fontWeight: '600', color: 'var(--text-primary)' }}>
                    {brainLabel}
                  </div>
                  <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-tertiary)', marginTop: '2px' }}>
                    {description}
                  </div>
                </div>
                <div style={{ flexShrink: 0, textAlign: 'right' }}>
                  {assignment ? (
                    <div>
                      <div style={{ fontSize: 'var(--text-sm)', fontWeight: '500', color: mutatingBrainId === brainId ? 'var(--accent-primary)' : color }}>
                        {assignment.displayName || '已配置'}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                        {mutatingBrainId === brainId ? '保存中...' : '已分配'}
                      </div>
                    </div>
                  ) : (
                    <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-tertiary)' }}>
                      {mutatingBrainId === brainId ? '保存中...' : '使用默认模型'}
                    </div>
                  )}
                </div>
                <div style={{ fontSize: '12px', color: 'var(--text-tertiary)', flexShrink: 0 }}>
                  {isExpanded ? '▼' : '▶'}
                </div>
              </div>

              {isExpanded && (
                <div style={{ padding: '0 var(--space-4) var(--space-4)', paddingLeft: '68px' }}>
                  <div style={{ position: 'relative' }}>
                    <div
                      ref={el => { triggerRef.current[brainKey] = el; }}
                      data-dropdown-trigger
                      onClick={(e) => {
                        e.stopPropagation();
                        if (isDropdownOpen) {
                          setDropdownOpen(null);
                          setDropdownPos(null);
                        } else {
                          const rect = e.currentTarget.getBoundingClientRect();
                          setDropdownPos({ top: rect.bottom + 4, left: rect.left, width: rect.width });
                          setDropdownOpen(brainKey);
                        }
                      }}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '8px 12px',
                        border: '1px solid var(--border-default)',
                        borderRadius: 'var(--radius-md)',
                        background: 'var(--bg-elevated)',
                        cursor: 'pointer',
                        minHeight: '36px',
                        transition: 'border-color 0.15s',
                      }}
                    >
                      <span style={{
                        fontSize: 'var(--text-sm)',
                        color: currentModel ? 'var(--text-primary)' : 'var(--text-tertiary)',
                      }}>
                        {currentModel ? `${currentModel.displayName} (${getProviderName(currentModel.providerId)})` : '-- 使用默认模型 --'}
                      </span>
                      <span style={{ color: 'var(--text-tertiary)', fontSize: '12px' }}>
                        {isDropdownOpen ? '▲' : '▼'}
                      </span>
                    </div>

                    {isDropdownOpen && dropdownPos && (
                      <div
                        data-dropdown-panel
                        style={{
                          position: 'fixed',
                          top: dropdownPos.top,
                          left: dropdownPos.left,
                          width: dropdownPos.width,
                          background: 'var(--bg-elevated)',
                          border: '1px solid var(--border-default)',
                          borderRadius: 'var(--radius-md)',
                          maxHeight: '200px',
                          overflowY: 'auto',
                          zIndex: 1000,
                          boxShadow: 'var(--shadow-md)',
                        }}
                      >
                        <div
                          onMouseDown={(e) => {
                            e.preventDefault();
                            handleClearModel(brainId);
                          }}
                          style={{
                            padding: '8px 12px',
                            fontSize: 'var(--text-sm)',
                            color: 'var(--text-tertiary)',
                            cursor: 'pointer',
                            borderBottom: '1px solid var(--border-subtle)',
                          }}
                          onMouseEnter={e => (e.currentTarget.style.background = 'var(--bg-hover)')}
                          onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                        >
                          -- 使用默认模型 --
                        </div>
                        {(availableModels || []).map((m: LlmModel) => (
                          <div
                            key={m.id}
                            onMouseDown={(e) => {
                              e.preventDefault();
                              handleModelSelect(brainId, m.id, m.displayName, m.providerId);
                            }}
                            style={{
                              padding: '8px 12px',
                              fontSize: 'var(--text-sm)',
                              color: assignment?.modelId === m.id ? color : 'var(--text-primary)',
                              cursor: 'pointer',
                              background: assignment?.modelId === m.id ? `${color}12` : 'transparent',
                            }}
                            onMouseEnter={e => {
                              if (assignment?.modelId !== m.id) e.currentTarget.style.background = 'var(--bg-hover)';
                            }}
                            onMouseLeave={e => {
                              if (assignment?.modelId !== m.id) e.currentTarget.style.background = 'transparent';
                            }}
                          >
                            {m.displayName} <span style={{ color: 'var(--text-tertiary)', fontSize: '12px' }}>({getProviderName(m.providerId)})</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div style={{
        padding: 'var(--space-3) var(--space-4)',
        background: 'rgba(59, 130, 246, 0.08)',
        borderRadius: 'var(--radius-lg)',
        border: '1px solid rgba(59, 130, 246, 0.2)',
      }}>
        <p style={{ fontSize: 'var(--text-xs)', color: 'var(--info)', margin: 0 }}>
          💡 提示：未分配的脑将使用系统默认模型（qwen3.5-27b）。在"模型池"标签页中可以添加更多模型。
        </p>
      </div>

      <div style={{
        padding: 'var(--space-4)',
        background: 'var(--bg-secondary)',
        borderRadius: 'var(--radius-xl)',
        border: '1px solid var(--border-subtle)',
      }}>
        <h4 style={{ fontSize: 'var(--text-base)', fontWeight: '600', margin: '0 0 var(--space-3)', color: 'var(--text-primary)' }}>
          自动进化配置
        </h4>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 'var(--space-3)' }}>
          <div>
            <div style={{ fontSize: 'var(--text-sm)', fontWeight: '500', color: 'var(--text-secondary)' }}>启用自动进化</div>
            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-tertiary)', marginTop: '2px' }}>
              系统将根据用户反馈自动调整大脑模型绑定
            </div>
          </div>
          <button
            onClick={() => toggleAutoEvolution(!autoEvolutionEnabled)}
            style={{
              width: '44px',
              height: '24px',
              borderRadius: '12px',
              border: 'none',
              background: autoEvolutionEnabled ? 'var(--success)' : 'var(--text-tertiary)',
              cursor: 'pointer',
              position: 'relative',
              transition: 'background 0.2s',
            }}
          >
            <div
              style={{
                width: '20px',
                height: '20px',
                borderRadius: '50%',
                background: '#fff',
                position: 'absolute',
                top: '2px',
                left: autoEvolutionEnabled ? '22px' : '2px',
                transition: 'left 0.2s',
                boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
              }}
            />
          </button>
        </div>

        {lastAction && (
          <div style={{
            padding: '10px 12px',
            background: lastAction.isAuto ? 'rgba(245, 158, 11, 0.12)' : 'var(--success-subtle)',
            borderRadius: 'var(--radius-lg)',
            border: `1px solid ${lastAction.isAuto ? 'rgba(245, 158, 11, 0.28)' : 'rgba(34, 197, 94, 0.2)'}`,
          }}>
            <div style={{
              fontSize: 'var(--text-sm)',
              fontWeight: '500',
              color: lastAction.isAuto ? 'var(--warning)' : 'var(--success)',
            }}>
              🤖 自动调整：
              {lastAction.type === 'assign' ? '绑定' : '清除'} {BRAIN_LABELS[lastAction.brainKey] || lastAction.brainKey}
              <span style={{ marginLeft: 'var(--space-2)', color: 'var(--text-tertiary)' }}>
                ({new Date(lastAction.time).toLocaleTimeString()})
              </span>
            </div>
            {lastAction.isAuto && (
              <button
                onClick={() => handleManualRollback(lastAction.brainKey)}
                style={{
                  marginTop: 'var(--space-2)',
                  padding: '4px 10px',
                  fontSize: 'var(--text-xs)',
                  background: 'var(--bg-elevated)',
                  border: '1px solid rgba(245, 158, 11, 0.28)',
                  borderRadius: 'var(--radius-sm)',
                  cursor: 'pointer',
                  color: 'var(--warning)',
                }}
              >
                ↩ 回滚到手动配置
              </button>
            )}
          </div>
        )}

        {!lastAction && (
          <div style={{ fontSize: 'var(--text-xs)', color: 'var(--text-tertiary)', textAlign: 'center', padding: 'var(--space-2)' }}>
            暂无调整记录
          </div>
        )}
      </div>
    </div>
  );
}
