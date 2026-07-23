/**
 * 设置页
 *
 * 包含：
 * - 后端 URL 配置（必填，缺失时强制进入）
 * - 客户端标识详情（可复制完整 UUID）
 * - 习惯录制（操作录制开关与配置）
 * - 退出登录
 * - 桌面端版本信息
 */
import { useEffect, useState, useCallback } from 'react';
import type { ClientInfo } from '@shared/api-types';

interface SettingsProps {
  clientInfo: ClientInfo | null;
  onBackendChanged: (url: string) => void;
  onLogout: () => void;
  hasToken: boolean;
}

/** 录制步骤 */
interface RecorderStep {
  index: number;
  operation: string;
  args: Record<string, unknown>;
  target: Record<string, unknown>;
  note: string;
  timestamp: string;
}

/** 录制结果 */
interface RecorderResult {
  steps: RecorderStep[];
  meta: {
    app: string;
    recorded_at: string;
    duration_seconds: number;
    step_count: number;
    note_mode: string;
  };
}

export function Settings({ clientInfo, onBackendChanged, onLogout, hasToken }: SettingsProps) {
  const [currentUrl, setCurrentUrl] = useState<string>('');
  const [draftUrl, setDraftUrl] = useState<string>('');
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; latency?: number; error?: string } | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  // ---- 习惯录制状态 ----
  const [recording, setRecording] = useState(false);
  const [paused, setPaused] = useState(false);
  const [stepCount, setStepCount] = useState(0);
  const [targetApp, setTargetApp] = useState('');
  const [noteMode, setNoteMode] = useState<'all' | 'key' | 'summary'>('key');
  const [recorderError, setRecorderError] = useState<string | null>(null);
  const [pendingNote, setPendingNote] = useState<{ index: number; operation: string; suggestion: string } | null>(null);
  const [noteText, setNoteText] = useState('');
  const [recordedSteps, setRecordedSteps] = useState<RecorderStep[]>([]);
  const [showResult, setShowResult] = useState(false);
  const [recorderResult, setRecorderResult] = useState<RecorderResult | null>(null);

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

  // ---- 习惯录制事件监听 ----
  useEffect(() => {
    const unsubStatus = window.livingAgentAPI.onRecorderStatus((info) => {
      setRecording(info.recording);
      setPaused(info.paused);
      setStepCount(info.stepCount);
    });
    const unsubStep = window.livingAgentAPI.onRecorderStep((step) => {
      setRecordedSteps(prev => [...prev, step]);
      setStepCount(prev => prev + 1);
    });
    const unsubNoteRequest = window.livingAgentAPI.onRecorderNoteRequest((info) => {
      setPendingNote({ index: info.index, operation: info.operation, suggestion: info.suggestion });
      setNoteText(info.suggestion);
    });
    const unsubResult = window.livingAgentAPI.onRecorderResult((result) => {
      setRecorderResult(result);
      setShowResult(true);
      setRecording(false);
      setPaused(false);
    });
    const unsubError = window.livingAgentAPI.onRecorderError((info) => {
      setRecorderError(info.message);
    });
    return () => {
      unsubStatus();
      unsubStep();
      unsubNoteRequest();
      unsubResult();
      unsubError();
    };
  }, []);

  // ---- 习惯录制操作 ----
  async function handleStartRecording() {
    setRecorderError(null);
    setShowResult(false);
    setRecordedSteps([]);
    setStepCount(0);
    const result = await window.livingAgentAPI.recorder.start({
      targetApp,
      noteMode
    });
    if (!result.success) {
      setRecorderError(result.error || '启动录制失败');
    }
  }

  async function handleStopRecording() {
    const result = await window.livingAgentAPI.recorder.stop();
    if (!result.success && result.error) {
      setRecorderError(result.error);
    }
  }

  async function handlePauseRecording() {
    await window.livingAgentAPI.recorder.pause();
  }

  async function handleResumeRecording() {
    await window.livingAgentAPI.recorder.resume();
  }

  async function handleSetNote() {
    if (!pendingNote) return;
    await window.livingAgentAPI.recorder.setNote(pendingNote.index, noteText);
    setPendingNote(null);
    setNoteText('');
  }

  async function handleSkipNote() {
    await window.livingAgentAPI.recorder.skipNote();
    setPendingNote(null);
    setNoteText('');
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

      {/* 习惯录制 */}
      <section className="settings-section">
        <div className="settings-section-head">
          <h2>📹 习惯录制</h2>
          <span className="settings-hint">
            录制您的桌面操作流程，LAS 大脑将学习并形成可复用的操作经验
          </span>
        </div>

        {recorderError && (
          <div className="test-result fail" role="alert" style={{ marginBottom: 12 }}>
            ❌ {recorderError}
          </div>
        )}

        {/* 录制中状态 */}
        {recording && (
          <div style={{
            padding: '12px 16px',
            borderRadius: 8,
            background: paused ? '#fff3cd' : '#d4edda',
            marginBottom: 12,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}>
            <div>
              <span style={{ fontSize: 16, marginRight: 8 }}>
                {paused ? '⏸' : '🔴'}
              </span>
              <strong>{paused ? '已暂停' : '录制中'}</strong>
              <span style={{ marginLeft: 12, color: '#666' }}>
                步骤: {stepCount}
              </span>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              {paused ? (
                <button className="btn btn-secondary" onClick={handleResumeRecording}>
                  ▶ 继续
                </button>
              ) : (
                <button className="btn btn-secondary" onClick={handlePauseRecording}>
                  ⏸ 暂停
                </button>
              )}
              <button className="btn btn-danger" onClick={handleStopRecording}>
                ⏹ 停止
              </button>
            </div>
          </div>
        )}

        {/* 备注输入弹窗（嵌入式） */}
        {pendingNote && (
          <div style={{
            padding: '12px 16px',
            borderRadius: 8,
            background: '#e7f3ff',
            marginBottom: 12,
            border: '1px solid #b3d7ff'
          }}>
            <div style={{ marginBottom: 8 }}>
              <strong>📝 步骤 #{pendingNote.index} 备注</strong>
              <span style={{ marginLeft: 8, color: '#666' }}>
                操作: {pendingNote.operation}
              </span>
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input
                type="text"
                value={noteText}
                onChange={(e) => setNoteText(e.target.value)}
                placeholder="为此操作添加备注..."
                style={{ flex: 1, padding: '6px 10px', borderRadius: 4, border: '1px solid #ccc' }}
                onKeyDown={(e) => { if (e.key === 'Enter') handleSetNote(); }}
              />
              <button className="btn btn-primary" onClick={handleSetNote}>
                ✓ 确认
              </button>
              <button className="btn btn-secondary" onClick={handleSkipNote}>
                ⏭ 跳过
              </button>
            </div>
          </div>
        )}

        {/* 录制配置（未录制时显示） */}
        {!recording && !showResult && (
          <div style={{ marginBottom: 12 }}>
            <div className="form-row">
              <label>目标应用</label>
              <input
                type="text"
                value={targetApp}
                onChange={(e) => setTargetApp(e.target.value)}
                placeholder="如：微信、记事本（留空录制全部应用）"
                style={{ flex: 1 }}
              />
            </div>
            <div className="form-row">
              <label>备注模式</label>
              <select
                value={noteMode}
                onChange={(e) => setNoteMode(e.target.value as 'all' | 'key' | 'summary')}
                style={{ padding: '4px 8px', borderRadius: 4, border: '1px solid #ccc' }}
              >
                <option value="all">每步弹出</option>
                <option value="key">关键步骤弹出（推荐）</option>
                <option value="summary">仅总结</option>
              </select>
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: '#888' }}>
              {noteMode === 'all' && '每个操作都会弹出备注输入，适合初学者或复杂流程'}
              {noteMode === 'key' && '仅在操作类型变化时弹出备注（如从点击变为输入），推荐模式'}
              {noteMode === 'summary' && '录制结束后统一添加备注，适合有经验的用户'}
            </div>
          </div>
        )}

        {/* 录制按钮 */}
        {!recording && !showResult && (
          <div className="form-actions">
            <button
              className="btn btn-primary"
              onClick={handleStartRecording}
            >
              🔴 开始录制
            </button>
          </div>
        )}

        {/* 已录制的步骤列表 */}
        {recordedSteps.length > 0 && !showResult && (
          <div style={{ marginTop: 12 }}>
            <h3 style={{ fontSize: 14, marginBottom: 8 }}>已录制步骤 ({recordedSteps.length})</h3>
            <div style={{ maxHeight: 200, overflow: 'auto', fontSize: 12, fontFamily: 'monospace' }}>
              {recordedSteps.map((step) => (
                <div key={step.index} style={{
                  padding: '4px 8px',
                  borderBottom: '1px solid #eee',
                  display: 'flex',
                  gap: 8
                }}>
                  <span style={{ color: '#999' }}>#{step.index}</span>
                  <span style={{ color: '#0066cc' }}>{step.operation}</span>
                  <span style={{ color: '#666' }}>
                    {step.operation === 'type'
                      ? `"${(step.args.text as string) || ''}"`
                      : step.operation === 'shortcut'
                        ? step.args.keys as string
                        : step.operation === 'click' || step.operation === 'double_click' || step.operation === 'right_click'
                          ? `(${step.args.x}, ${step.args.y})`
                          : step.operation === 'scroll'
                            ? `${step.args.direction} x${step.args.amount}`
                            : ''}
                  </span>
                  {step.note ? <span style={{ color: '#28a745' }}>/ {step.note}</span> : null}
                  {typeof step.target?.name === 'string' && step.target.name.length > 0 ? (
                    <span style={{ color: '#999' }}>[{step.target.name}]</span>
                  ) : null}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 录制结果展示 */}
        {showResult && recorderResult && (
          <div style={{ marginTop: 12 }}>
            <div style={{
              padding: '12px 16px',
              borderRadius: 8,
              background: '#d4edda',
              marginBottom: 12
            }}>
              ✅ 录制完成！
              <span style={{ marginLeft: 12, color: '#666' }}>
                应用: {recorderResult.meta.app || '全部'} |
                步骤: {recorderResult.meta.step_count} |
                时长: {recorderResult.meta.duration_seconds}秒
              </span>
            </div>
            <div style={{ maxHeight: 300, overflow: 'auto', fontSize: 12, fontFamily: 'monospace', border: '1px solid #ddd', borderRadius: 4 }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: '#f5f5f5' }}>
                    <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>#</th>
                    <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>操作</th>
                    <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>参数</th>
                    <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>目标</th>
                    <th style={{ padding: '6px 8px', textAlign: 'left', borderBottom: '1px solid #ddd' }}>备注</th>
                  </tr>
                </thead>
                <tbody>
                  {recorderResult.steps.map((step) => (
                    <tr key={step.index}>
                      <td style={{ padding: '4px 8px', borderBottom: '1px solid #eee' }}>{step.index}</td>
                      <td style={{ padding: '4px 8px', borderBottom: '1px solid #eee', color: '#0066cc' }}>{step.operation}</td>
                      <td style={{ padding: '4px 8px', borderBottom: '1px solid #eee', color: '#666' }}>
                        {step.operation === 'type'
                          ? `"${(step.args.text as string) || ''}"`
                          : step.operation === 'shortcut'
                            ? step.args.keys as string
                            : step.operation === 'click' || step.operation === 'double_click' || step.operation === 'right_click'
                              ? `(${step.args.x}, ${step.args.y})`
                              : step.operation === 'scroll'
                                ? `${step.args.direction} x${step.args.amount}`
                                : JSON.stringify(step.args)}
                      </td>
                      <td style={{ padding: '4px 8px', borderBottom: '1px solid #eee', color: '#999', fontSize: 11 }}>
                        {typeof step.target?.name === 'string' ? step.target.name : (typeof step.target?.className === 'string' ? step.target.className : '-')}
                      </td>
                      <td style={{ padding: '4px 8px', borderBottom: '1px solid #eee', color: '#28a745' }}>{step.note || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="form-actions" style={{ marginTop: 12 }}>
              <button className="btn btn-secondary" onClick={() => {
                setShowResult(false);
                setRecorderResult(null);
                setRecordedSteps([]);
                setStepCount(0);
              }}>
                重新录制
              </button>
              <button className="btn btn-primary" onClick={() => {
                // TODO: 将录制结果保存为经验（后续 Phase 3 实现）
                alert('保存为经验功能将在后续版本实现');
              }}>
                💾 保存为经验
              </button>
            </div>
          </div>
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
