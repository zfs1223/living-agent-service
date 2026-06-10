import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { modelPoolApi, type ProviderEntry } from '@/services/modelPoolApi';
import type { ProviderConfig, LlmModel, ProviderRequest, ModelRequest, ProviderTestResult } from '@/types/modelPool';
import { useToastStore } from '../stores/toastStore';

const FALLBACK_PROVIDERS: ProviderEntry[] = [
  { id: 'anthropic', displayName: 'Anthropic', protocol: 'ANTHROPIC', defaultBaseUrl: 'https://api.anthropic.com', supportsToolChoice: false, defaultMaxTokens: 8192 },
  { id: 'openai', displayName: 'OpenAI', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://api.openai.com/v1', supportsToolChoice: true, defaultMaxTokens: 16384 },
  { id: 'openai-response', displayName: 'OpenAI Responses', protocol: 'OPENAI_RESPONSES', defaultBaseUrl: 'https://api.openai.com/v1', supportsToolChoice: true, defaultMaxTokens: 16384 },
  { id: 'azure', displayName: 'Azure OpenAI', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: '', supportsToolChoice: true, defaultMaxTokens: 16384 },
  { id: 'deepseek', displayName: 'DeepSeek', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://api.deepseek.com/v1', supportsToolChoice: true, defaultMaxTokens: 8192 },
  { id: 'qwen', displayName: 'Qwen (DashScope)', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', supportsToolChoice: true, defaultMaxTokens: 8192 },
  { id: 'minimax', displayName: 'MiniMax', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://api.minimaxi.com/v1', supportsToolChoice: true, defaultMaxTokens: 16384 },
  { id: 'openrouter', displayName: 'OpenRouter', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://openrouter.ai/api/v1', supportsToolChoice: true, defaultMaxTokens: 4096 },
  { id: 'zhipu', displayName: '智谱 (Zhipu)', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://open.bigmodel.cn/api/paas/v4', supportsToolChoice: true, defaultMaxTokens: 8192 },
  { id: 'baidu', displayName: '百度 (千帆)', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://qianfan.baidubce.com/v2', supportsToolChoice: false, defaultMaxTokens: 4096 },
  { id: 'gemini', displayName: 'Gemini', protocol: 'GEMINI', defaultBaseUrl: 'https://generativelanguage.googleapis.com/v1beta', supportsToolChoice: true, defaultMaxTokens: 8192 },
  { id: 'kimi', displayName: 'Kimi (月之暗面)', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://api.moonshot.cn/v1', supportsToolChoice: true, defaultMaxTokens: 8192 },
  { id: 'vllm', displayName: 'vLLM', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'http://localhost:8000/v1', supportsToolChoice: true, defaultMaxTokens: 4096 },
  { id: 'ollama', displayName: 'Ollama', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'http://localhost:11434/v1', supportsToolChoice: true, defaultMaxTokens: 4096 },
  { id: 'sglang', displayName: 'SGLang', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'http://localhost:30000/v1', supportsToolChoice: true, defaultMaxTokens: 4096 },
  { id: 'siliconflow', displayName: '硅基流动 (SiliconFlow)', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://api.siliconflow.cn/v1', supportsToolChoice: true, defaultMaxTokens: 8192 },
  { id: 'modelscope', displayName: 'ModelScope', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: 'https://api-inference.modelscope.cn/v1', supportsToolChoice: true, defaultMaxTokens: 8192 },
  { id: 'custom', displayName: '自定义', protocol: 'OPENAI_COMPATIBLE', defaultBaseUrl: '', supportsToolChoice: true, defaultMaxTokens: 4096 },
];

export default function ModelPoolProviders() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingModel, setEditingModel] = useState<LlmModel | null>(null);
  const [testResult, setTestResult] = useState<ProviderTestResult | null>(null);
  const [testingProvider, setTestingProvider] = useState<string | null>(null);
  const [discoveredModels, setDiscoveredModels] = useState<string[]>([]);
  const [discovering, setDiscovering] = useState(false);
  const [showDiscovered, setShowDiscovered] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  
  const [form, setForm] = useState({
    providerId: 'anthropic',
    modelName: '',
    displayName: '',
    baseUrl: '',
    apiKey: '',
    contextWindow: 128000,
    maxOutputTokens: 8192,
    supportsVision: false,
    supportsReasoning: false,
    temperature: '' as string,
    enabled: true,
  });

  const { data: providers = [] } = useQuery({
    queryKey: ['model-pool', 'providers'],
    queryFn: () => modelPoolApi.providers.list(),
  });

  const { data: models = [] } = useQuery({
    queryKey: ['model-pool', 'models'],
    queryFn: () => modelPoolApi.models.list(),
  });

  const { data: presetProviders = [] } = useQuery<ProviderEntry[]>({
    queryKey: ['model-pool', 'provider-manifest'],
    queryFn: () => modelPoolApi.providers.manifest(),
  });

  const providerOptions = presetProviders.length > 0 ? presetProviders : FALLBACK_PROVIDERS;

  const getProviderDisplayName = (providerId: string) => {
    const preset = providerOptions.find(p => p.id === providerId);
    return preset?.displayName || providerId;
  };

  const getProviderBaseUrl = (providerId: string) => {
    const preset = providerOptions.find(p => p.id === providerId);
    return preset?.defaultBaseUrl || '';
  };

  const handleProviderChange = (providerId: string) => {
    const preset = providerOptions.find(p => p.id === providerId);
    setForm(f => ({
      ...f,
      providerId,
      baseUrl: preset?.defaultBaseUrl || '',
      maxOutputTokens: preset?.defaultMaxTokens || 4096,
    }));
  };

  const saveProviderMutation = useMutation({
    mutationFn: (data: ProviderRequest) => modelPoolApi.providers.add(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['model-pool', 'providers'] });
    },
  });

  const saveModelMutation = useMutation({
    mutationFn: (data: ModelRequest) => {
      if (editingModel) {
        return modelPoolApi.models.update(editingModel.id, data);
      }
      return modelPoolApi.models.add(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['model-pool', 'models'] });
      queryClient.invalidateQueries({ queryKey: ['model-pool', 'providers'] });
      setShowForm(false);
      setEditingModel(null);
      setTestResult(null);
    },
    onError: (error: any) => {
      const message = error?.message || '保存失败';
      setTestResult({
        success: false,
        message: message.includes('已存在') ? '模型已存在，请更换名称或编辑现有模型' : message,
        latencyMs: 0,
        response: '',
        error: message,
      });
    },
  });

  const deleteModelMutation = useMutation({
    mutationFn: (id: string) => modelPoolApi.models.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['model-pool', 'models'] });
    },
  });

  const batchDeleteMutation = useMutation({
    mutationFn: (ids: string[]) => modelPoolApi.models.batchDelete(ids),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['model-pool', 'models'] });
      queryClient.invalidateQueries({ queryKey: ['model-pool', 'providers'] });
      setSelectedIds(new Set());
      useToastStore.getState().showToast(`已删除 ${result.deleted} 个模型`, 'success');
    },
  });

  const testMutation = useMutation({
    mutationFn: async ({ providerId, model, baseUrl, apiKey }: { providerId: string; model: string; baseUrl: string; apiKey: string }) => {
      setTestingProvider(providerId);
      const preset = providerOptions.find(p => p.id === providerId);
      const updateReq: ProviderRequest = {
        id: providerId,
        displayName: preset?.displayName || providerId,
        protocol: (preset?.protocol === 'openai_compatible' ? 'OPENAI_COMPATIBLE' : preset?.protocol?.toUpperCase()) as any || 'OPENAI_COMPATIBLE',
        baseUrl: baseUrl || preset?.defaultBaseUrl || '',
        apiKeyEncrypted: apiKey,
        enabled: true,
        supportsToolChoice: preset?.supportsToolChoice ?? true,
        defaultMaxTokens: preset?.defaultMaxTokens || 4096,
        autoDiscoverModels: false,
      };
      await saveProviderMutation.mutateAsync(updateReq);
      
      return modelPoolApi.providers.test(providerId, model, baseUrl, apiKey);
    },
    onSettled: () => setTestingProvider(null),
    onSuccess: (result) => setTestResult(result),
  });

  const discoverModelsMutation = useMutation({
    mutationFn: async ({ providerId, baseUrl, apiKey }: { providerId: string; baseUrl: string; apiKey: string }) => {
      setDiscovering(true);
      setDiscoveredModels([]);
      setShowDiscovered(false);
      
      const preset = providerOptions.find(p => p.id === providerId);
      const updateReq: ProviderRequest = {
        id: providerId,
        displayName: preset?.displayName || providerId,
        protocol: (preset?.protocol === 'openai_compatible' ? 'OPENAI_COMPATIBLE' : preset?.protocol?.toUpperCase()) as any || 'OPENAI_COMPATIBLE',
        baseUrl: baseUrl || preset?.defaultBaseUrl || '',
        apiKeyEncrypted: apiKey,
        enabled: true,
        supportsToolChoice: preset?.supportsToolChoice ?? true,
        defaultMaxTokens: preset?.defaultMaxTokens || 4096,
        autoDiscoverModels: false,
      };
      await saveProviderMutation.mutateAsync(updateReq);
      
      const result = await modelPoolApi.providers.discover(providerId, baseUrl, apiKey);
      return result;
    },
    onSettled: () => setDiscovering(false),
    onSuccess: (models) => {
      if (models && models.length > 0) {
        setDiscoveredModels(models);
        setShowDiscovered(true);
      }
    },
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    const preset = providerOptions.find(p => p.id === form.providerId);
    const providerUpdate: ProviderRequest = {
      id: form.providerId,
      displayName: preset?.displayName || form.providerId,
      protocol: (preset?.protocol === 'openai_compatible' ? 'OPENAI_COMPATIBLE' : preset?.protocol?.toUpperCase()) as any || 'OPENAI_COMPATIBLE',
      baseUrl: form.baseUrl || preset?.defaultBaseUrl || '',
      apiKeyEncrypted: form.apiKey,
      enabled: true,
      supportsToolChoice: preset?.supportsToolChoice ?? true,
      defaultMaxTokens: form.maxOutputTokens,
      autoDiscoverModels: false,
    };
    await saveProviderMutation.mutateAsync(providerUpdate);

    const modelData: ModelRequest = {
      providerId: form.providerId,
      modelName: form.modelName,
      displayName: form.displayName || form.modelName,
      contextWindow: form.contextWindow,
      maxOutputTokens: form.maxOutputTokens,
      supportsVision: form.supportsVision,
      supportsReasoning: form.supportsReasoning,
      temperature: form.temperature ? parseFloat(form.temperature) : null,
      enabled: form.enabled,
      recommended: false,
      bestFor: '',
      inputTypes: isEmbeddingModelName(form.modelName) ? 'embedding' : 'text',
    };
    
    saveModelMutation.mutate(modelData);
  };

  const openCreate = () => {
    setEditingModel(null);
    const defaultProvider = providerOptions[0];
    setForm({
      providerId: defaultProvider?.id || 'anthropic',
      modelName: '',
      displayName: '',
      baseUrl: defaultProvider?.defaultBaseUrl || '',
      apiKey: '',
      contextWindow: 128000,
      maxOutputTokens: defaultProvider?.defaultMaxTokens || 8192,
      supportsVision: false,
      supportsReasoning: false,
      temperature: '',
      enabled: true,
    });
    setShowForm(true);
  };

  const openEdit = (model: LlmModel) => {
    setEditingModel(model);
    const provider = providers.find((p: ProviderConfig) => p.id === model.providerId);
    setForm({
      providerId: model.providerId,
      modelName: model.modelName,
      displayName: model.displayName,
      baseUrl: provider?.baseUrl || '',
      apiKey: '',
      contextWindow: model.contextWindow,
      maxOutputTokens: model.maxOutputTokens,
      supportsVision: model.supportsVision,
      supportsReasoning: model.supportsReasoning,
      temperature: model.temperature?.toString() || '',
      enabled: model.enabled,
    });
    setShowForm(true);
  };

  const modelCount = models?.length ?? 0;
  const providerCount = new Set(models?.map((m: LlmModel) => m.providerId)).size;
  const selectedCount = selectedIds.size;

  const toggleSelect = (id: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const toggleSelectAll = () => {
    if (selectedCount === modelCount && modelCount > 0) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(models.map((m: LlmModel) => m.id)));
    }
  };

  const handleBatchDelete = () => {
    if (selectedCount === 0) return;
    if (confirm(`确认删除选中的 ${selectedCount} 个模型？此操作不可撤销。`)) {
      batchDeleteMutation.mutate(Array.from(selectedIds));
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div style={{ display: 'flex', gap: 20, fontSize: 13, color: '#94a3b8', alignItems: 'center' }}>
          <span>模型: <b style={{ color: '#fff' }}>{modelCount}</b></span>
          <span>供应商: <b style={{ color: '#fff' }}>{providerCount}</b></span>
          {selectedCount > 0 && (
            <>
              <span>已选: <b style={{ color: '#fbbf24' }}>{selectedCount}</b></span>
              <button
                onClick={handleBatchDelete}
                disabled={batchDeleteMutation.isPending}
                style={{ padding: '4px 12px', background: '#dc2626', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}
              >
                {batchDeleteMutation.isPending ? '删除中...' : `删除选中 (${selectedCount})`}
              </button>
              <button
                onClick={() => setSelectedIds(new Set())}
                style={{ padding: '4px 10px', background: '#334155', color: '#e2e8f0', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}
              >
                取消选择
              </button>
            </>
          )}
        </div>
        <button
          onClick={openCreate}
          style={{ padding: '8px 20px', background: '#10b981', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 14 }}
        >
          + 添加模型
        </button>
      </div>

      {showForm && (
        <div style={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, padding: 20, marginBottom: 20 }}>
          <h3 style={{ marginTop: 0, marginBottom: 16 }}>{editingModel ? '编辑模型' : '添加模型'}</h3>
          <form onSubmit={handleSubmit}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, color: '#94a3b8', marginBottom: 4 }}>供应商</label>
                <select 
                  value={form.providerId} 
                  onChange={e => handleProviderChange(e.target.value)}
                  disabled={!!editingModel}
                  style={{ width: '100%', padding: 8, background: '#0f172a', border: '1px solid #334155', borderRadius: 6, color: '#fff' }}
                >
                  {providerOptions.map(p => (
                    <option key={p.id} value={p.id}>{p.displayName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, color: '#94a3b8', marginBottom: 4 }}>模型名称</label>
                <div style={{ display: 'flex', gap: 6 }}>
                  <input 
                    value={form.modelName} 
                    onChange={e => setForm({ ...form, modelName: e.target.value })} 
                    required
                    placeholder="e.g. claude-sonnet-4-20250514"
                    style={{ flex: 1, padding: 8, background: '#0f172a', border: '1px solid #334155', borderRadius: 6, color: '#fff' }} 
                  />
                  {!editingModel && (
                    <button 
                      type="button"
                      onClick={() => discoverModelsMutation.mutate({ providerId: form.providerId, baseUrl: form.baseUrl, apiKey: form.apiKey })}
                      disabled={discovering || !form.apiKey}
                      title={form.apiKey ? '从服务端发现可用模型' : '请先填写 API Key'}
                      style={{ padding: '8px 12px', background: discovering ? '#334155' : '#8b5cf6', color: '#fff', border: 'none', borderRadius: 6, cursor: form.apiKey ? 'pointer' : 'not-allowed', fontSize: 12, whiteSpace: 'nowrap' }}
                    >
                      {discovering ? '发现中...' : '🔍 发现模型'}
                    </button>
                  )}
                </div>
                {showDiscovered && discoveredModels.length > 0 && (
                  <div style={{ marginTop: 6, maxHeight: 120, overflowY: 'auto', background: '#0f172a', border: '1px solid #334155', borderRadius: 6 }}>
                    {discoveredModels.map(m => (
                      <div
                        key={m}
                        onClick={() => { setForm({ ...form, modelName: m, displayName: m }); setShowDiscovered(false); }}
                        style={{ padding: '6px 10px', fontSize: 12, color: '#e2e8f0', cursor: 'pointer', borderBottom: '1px solid #1e293b' }}
                        onMouseEnter={e => (e.currentTarget.style.background = '#1e293b')}
                        onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                      >
                        {m}
                      </div>
                    ))}
                  </div>
                )}
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, color: '#94a3b8', marginBottom: 4 }}>显示名称</label>
                <input 
                  value={form.displayName} 
                  onChange={e => setForm({ ...form, displayName: e.target.value })} 
                  placeholder="可选，用于界面显示"
                  style={{ width: '100%', padding: 8, background: '#0f172a', border: '1px solid #334155', borderRadius: 6, color: '#fff' }} 
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, color: '#94a3b8', marginBottom: 4 }}>Base URL</label>
                <input 
                  value={form.baseUrl} 
                  onChange={e => setForm({ ...form, baseUrl: e.target.value })} 
                  placeholder="API 端点地址"
                  style={{ width: '100%', padding: 8, background: '#0f172a', border: '1px solid #334155', borderRadius: 6, color: '#fff' }} 
                />
              </div>
              <div style={{ gridColumn: 'span 2' }}>
                <label style={{ display: 'block', fontSize: 13, color: '#94a3b8', marginBottom: 4 }}>API Key</label>
                <input 
                  value={form.apiKey} 
                  onChange={e => setForm({ ...form, apiKey: e.target.value })} 
                  type="password"
                  placeholder={editingModel ? '留空不修改' : '必填'}
                  required={!editingModel}
                  style={{ width: '100%', padding: 8, background: '#0f172a', border: '1px solid #334155', borderRadius: 6, color: '#fff' }} 
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, color: '#94a3b8', marginBottom: 4 }}>上下文窗口</label>
                <input 
                  type="number" 
                  value={form.contextWindow} 
                  onChange={e => setForm({ ...form, contextWindow: parseInt(e.target.value) || 128000 })}
                  style={{ width: '100%', padding: 8, background: '#0f172a', border: '1px solid #334155', borderRadius: 6, color: '#fff' }} 
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, color: '#94a3b8', marginBottom: 4 }}>最大输出 Tokens</label>
                <input 
                  type="number" 
                  value={form.maxOutputTokens} 
                  onChange={e => setForm({ ...form, maxOutputTokens: parseInt(e.target.value) || 4096 })}
                  style={{ width: '100%', padding: 8, background: '#0f172a', border: '1px solid #334155', borderRadius: 6, color: '#fff' }} 
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, color: '#94a3b8', marginBottom: 4 }}>Temperature</label>
                <input 
                  type="number" 
                  step="0.1"
                  min="0"
                  max="2"
                  value={form.temperature} 
                  onChange={e => setForm({ ...form, temperature: e.target.value })}
                  placeholder="留空使用默认值"
                  style={{ width: '100%', padding: 8, background: '#0f172a', border: '1px solid #334155', borderRadius: 6, color: '#fff' }} 
                />
              </div>
            </div>
            <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 16 }}>
              <label style={{ fontSize: 13, color: '#94a3b8', display: 'flex', alignItems: 'center', gap: 4 }}>
                <input type="checkbox" checked={form.enabled} onChange={e => setForm({ ...form, enabled: e.target.checked })} /> 启用
              </label>
              <label style={{ fontSize: 13, color: '#94a3b8', display: 'flex', alignItems: 'center', gap: 4 }}>
                <input type="checkbox" checked={form.supportsVision} onChange={e => setForm({ ...form, supportsVision: e.target.checked })} /> 支持视觉
              </label>
              <label style={{ fontSize: 13, color: '#94a3b8', display: 'flex', alignItems: 'center', gap: 4 }}>
                <input type="checkbox" checked={form.supportsReasoning} onChange={e => setForm({ ...form, supportsReasoning: e.target.checked })} /> 支持推理
              </label>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button type="submit" disabled={saveModelMutation.isPending}
                style={{ padding: '8px 20px', background: '#3b82f6', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer' }}>
                {saveModelMutation.isPending ? '保存中...' : '保存'}
              </button>
              <button 
                type="button" 
                onClick={() => testMutation.mutate({ 
                  providerId: form.providerId, 
                  model: form.modelName, 
                  baseUrl: form.baseUrl, 
                  apiKey: form.apiKey 
                })}
                disabled={testingProvider === form.providerId || !form.modelName || (!form.apiKey && !providers.find((p: ProviderConfig) => p.id === form.providerId))}
                style={{ padding: '8px 20px', background: '#334155', color: '#e2e8f0', border: 'none', borderRadius: 6, cursor: 'pointer' }}
              >
                {testingProvider === form.providerId ? '测试中...' : '测试连接'}
              </button>
              <button type="button" onClick={() => { setShowForm(false); setEditingModel(null); }}
                style={{ padding: '8px 20px', background: 'transparent', color: '#94a3b8', border: '1px solid #334155', borderRadius: 6, cursor: 'pointer' }}>
                取消
              </button>
            </div>
          </form>
        </div>
      )}

      {testResult && (
        <div style={{ background: testResult.success ? '#064e3b' : '#7f1d1d', border: `1px solid ${testResult.success ? '#065f46' : '#991b1b'}`, borderRadius: 8, padding: 16, marginBottom: 20 }}>
          <strong>{testResult.success ? '连接成功' : '连接失败'}</strong>
          <div style={{ fontSize: 13, marginTop: 4, opacity: 0.8 }}>
            {testResult.message} {testResult.latencyMs > 0 && `(延迟: ${testResult.latencyMs}ms)`}
          </div>
          {testResult.response && <div style={{ fontSize: 12, marginTop: 8, background: 'rgba(0,0,0,0.3)', padding: 8, borderRadius: 4 }}>{testResult.response.slice(0, 200)}</div>}
          <button onClick={() => setTestResult(null)} style={{ marginTop: 8, padding: '4px 12px', background: 'transparent', color: '#fff', border: '1px solid rgba(255,255,255,0.3)', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>关闭</button>
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {modelCount > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 4px' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: '#94a3b8', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={selectedCount === modelCount && modelCount > 0}
                onChange={toggleSelectAll}
                style={{ accentColor: '#3b82f6', width: 16, height: 16 }}
              />
              {selectedCount > 0 ? `已选 ${selectedCount}/${modelCount}` : '全选'}
            </label>
          </div>
        )}
        {(models || []).map((model: LlmModel) => {
          const provider = providers.find((p: ProviderConfig) => p.id === model.providerId);
          return (
            <div key={model.id} style={{ background: '#1e293b', border: '1px solid #334155', borderRadius: 8, padding: 16, display: 'flex', alignItems: 'flex-start', gap: 12 }}>
              <div style={{ paddingTop: 2 }}>
                <input
                  type="checkbox"
                  checked={selectedIds.has(model.id)}
                  onChange={() => toggleSelect(model.id)}
                  style={{ accentColor: '#3b82f6', width: 16, height: 16, cursor: 'pointer' }}
                />
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flex: 1 }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                    <h4 style={{ margin: 0, fontSize: 15 }}>{model.displayName || model.modelName}</h4>
                    <span style={{
                      padding: '2px 8px', borderRadius: 4, fontSize: 11,
                      background: model.enabled ? '#064e3b' : '#7f1d1d',
                      color: model.enabled ? '#34d399' : '#fca5a5',
                    }}>
                      {model.enabled ? '已启用' : '已禁用'}
                    </span>
                    {model.supportsVision && (
                      <span style={{ padding: '2px 6px', borderRadius: 4, fontSize: 10, background: '#1e40af', color: '#93c5fd' }}>视觉</span>
                    )}
                    {model.supportsReasoning && (
                      <span style={{ padding: '2px 6px', borderRadius: 4, fontSize: 10, background: '#7c2d12', color: '#fdba74' }}>推理</span>
                    )}
                    {model.inputTypes === 'embedding' && (
                      <span style={{ padding: '2px 6px', borderRadius: 4, fontSize: 10, background: '#065f46', color: '#6ee7b7' }}>嵌入</span>
                    )}
                  </div>
                  <div style={{ fontSize: 12, color: '#64748b', marginBottom: 4 }}>
                    {model.modelName}
                  </div>
                  <div style={{ fontSize: 12, color: '#64748b' }}>
                    供应商: {getProviderDisplayName(model.providerId)} · 上下文: {model.contextWindow.toLocaleString()} · 输出: {model.maxOutputTokens.toLocaleString()}
                    {model.temperature !== null && ` · Temperature: ${model.temperature}`}
                  </div>
                  {provider?.baseUrl && (
                    <div style={{ fontSize: 11, color: '#475569', marginTop: 4, wordBreak: 'break-all' }}>
                      {provider.baseUrl}
                    </div>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  <button onClick={() => openEdit(model)}
                    style={{ padding: '4px 10px', background: '#334155', color: '#e2e8f0', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>
                    编辑
                  </button>
                  <button onClick={() => { if (confirm('确认删除此模型?')) deleteModelMutation.mutate(model.id); }}
                    style={{ padding: '4px 10px', background: '#7f1d1d', color: '#fca5a5', border: 'none', borderRadius: 4, cursor: 'pointer', fontSize: 12 }}>
                    删除
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {models?.length === 0 && (
        <div style={{ textAlign: 'center', padding: 40, color: '#64748b' }}>
          <div style={{ fontSize: 16, marginBottom: 8 }}>暂无模型</div>
          <div style={{ fontSize: 13 }}>点击"添加模型"开始配置</div>
        </div>
      )}
    </div>
  );
}

const EMBEDDING_KEYWORDS = ['bge', 'e5', 'embedding', 'text-embedding-ada', 'text-embedding-3', 'gte', 'jina', 'm3e', 'voyage', 'cohere-embed', 'retrieval', 'sentence'];

function isEmbeddingModelName(name: string): boolean {
  if (!name) return false;
  const lower = name.toLowerCase();
  return EMBEDDING_KEYWORDS.some(kw => lower.includes(kw));
}
