/**
 * 设置页
 *
 * 包含：
 * - 后端 URL 配置（必填，缺失时强制进入）
 * - 客户端标识详情（可复制完整 UUID）
 * - 退出登录
 * - 桌面端版本信息
 */
import { useEffect, useState } from 'react';
import type { ClientInfo } from '@shared/api-types';

interface SettingsProps {
  clientInfo: ClientInfo | null;
  onBackendChanged: (url: string) => void;
  onLogout: () => void;
  hasToken: boolean;
}

export function Settings({ clientInfo, onBackendChanged, onLogout, hasToken }: SettingsProps) {
  const [currentUrl, setCurrentUrl] = useState<string>('');
  const [draftUrl, setDraftUrl] = useState<string>('');
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; latency?: number; error?: string } | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    void (async () => {
      const url = await window.livingAgentAPI.getBackendUrl();
      setCurrentUrl(url);
      setDraftUrl(url);
    })();
  }, []);

  async function handleTest() {
    setTesting(true);
    setTestResult(null);
    try {
      // 先临时设置再测（或者直接 fetch）
      const t0 = Date.now();
      const target = draftUrl.replace(/\/+$/, '');
      const res = await fetch(`${target}/api/health`, { method: 'GET' });
      const dt = Date.now() - t0;
      setTestResult({ ok: res.ok, latency: dt, error: res.ok ? undefined : `HTTP ${res.status}` });
    } catch (e) {
      setTestResult({ ok: false, error: String(e) });
    } finally {
      setTesting(false);
    }
  }

  async function handleSave() {
    setSaving(true);
    setSaveError(null);
    try {
      const saved = await window.livingAgentAPI.setBackendUrl(draftUrl);
      setCurrentUrl(saved);
      setDraftUrl(saved);
      onBackendChanged(saved);
    } catch (e) {
      setSaveError(String(e));
    } finally {
      setSaving(false);
    }
  }

  async function copyClientId() {
    if (!clientInfo) return;
    try {
      await navigator.clipboard.writeText(clientInfo.clientId);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (e) {
      console.warn('[settings] clipboard failed:', e);
    }
  }

  const dirty = draftUrl !== currentUrl;

  return (
    <div className="settings-page" style={{ padding: 24, maxWidth: 760 }}>
      <h1 style={{ margin: '0 0 6px' }}>设置</h1>
      <p style={{ color: '#666', margin: '0 0 24px' }}>
        后端地址、客户端标识、登录态等本地配置
      </p>

      {/* 后端 URL */}
      <section className="settings-section">
        <div className="settings-section-head">
          <h2>🌐 后端服务地址</h2>
          <span className="settings-hint">
            桌面端通过此地址与后端 API 通信。修改后立即生效（无需重启）
          </span>
        </div>

        <div className="form-row">
          <label>后端 URL</label>
          <input
            type="text"
            value={draftUrl}
            onChange={(e) => setDraftUrl(e.target.value)}
            placeholder="https://api.living-agent.example.com"
            spellCheck={false}
          />
        </div>

        {testResult && (
          <div
            className={`test-result ${testResult.ok ? 'ok' : 'fail'}`}
            role="alert"
          >
            {testResult.ok
              ? `✅ 连接成功（${testResult.latency} ms）`
              : `❌ ${testResult.error || '连接失败'}`}
          </div>
        )}

        {saveError && (
          <div className="test-result fail" role="alert">
            ❌ {saveError}
          </div>
        )}

        <div className="form-actions">
          <button
            className="btn btn-secondary"
            onClick={handleTest}
            disabled={!draftUrl || testing}
          >
            {testing ? '测试中…' : '🔌 测试连接'}
          </button>
          <button
            className="btn btn-primary"
            onClick={handleSave}
            disabled={!dirty || !draftUrl || saving}
          >
            {saving ? '保存中…' : '💾 保存'}
          </button>
        </div>

        {currentUrl && (
          <div style={{ marginTop: 12, color: '#888', fontSize: 12 }}>
            当前生效：<code>{currentUrl}</code>
          </div>
        )}
      </section>

      {/* 客户端标识 */}
      <section className="settings-section">
        <div className="settings-section-head">
          <h2>🆔 客户端标识</h2>
          <span className="settings-hint">
            每台客户端机器的唯一标识，后端用于审计 / windows_automation 任务路由
          </span>
        </div>

        {clientInfo ? (
          <>
            <div className="client-id-card">
              <div className="client-id-row">
                <span className="client-id-label">Client ID</span>
                <code className="client-id-value">{clientInfo.clientId}</code>
                <button className="btn-icon" onClick={copyClientId} title="复制完整 UUID">
                  {copied ? '✓' : '📋'}
                </button>
              </div>
              <div className="client-id-meta">
                <span>📡 {clientInfo.hostname}</span>
                <span>🖥 {clientInfo.platform}</span>
                <span>👤 {clientInfo.osUser}</span>
                <span>🏷 v{clientInfo.appVersion}</span>
                <span>📅 {clientInfo.createdAt.slice(0, 10)}</span>
              </div>
            </div>

            <div className="form-actions">
              <button
                className="btn btn-secondary"
                onClick={async () => {
                  if (
                    window.confirm(
                      '重置客户端标识？\n\n将生成新的 UUID 并覆盖当前标识。\n原标识在所有历史日志中仍可追溯，但当前 PC 的关联状态会变更。'
                    )
                  ) {
                    await window.livingAgentAPI.app.resetClientId();
                    window.location.reload();
                  }
                }}
              >
                🔄 重置 Client ID
              </button>
            </div>
          </>
        ) : (
          <div style={{ color: '#999' }}>加载中…</div>
        )}
      </section>

      {/* 登录态 */}
      <section className="settings-section">
        <div className="settings-section-head">
          <h2>🔑 登录态</h2>
        </div>
        <div style={{ marginBottom: 12 }}>
          {hasToken ? '✅ 已登录' : '⚠️ 未登录'}
        </div>
        {hasToken && (
          <button
            className="btn btn-danger"
            onClick={() => {
              if (window.confirm('确定退出登录？将清除本地 token。')) {
                onLogout();
              }
            }}
          >
            🚪 退出登录
          </button>
        )}
      </section>
    </div>
  );
}
