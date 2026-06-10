/**
 * 本地保存设置页
 *
 * window.livingAgentAPI 类型由 src/types/electron-api.d.ts 统一声明，
 * 这里不再重复 declare global，避免覆盖造成类型丢失。
 */
import React, { useEffect, useState } from 'react';
import type { LocalSaveConfig, LocalSaveStats } from '@shared/types';

export function LocalSaveSettings() {
  const [config, setConfig] = useState<LocalSaveConfig | null>(null);
  const [stats, setStats] = useState<LocalSaveStats | null>(null);
  const [syncing, setSyncing] = useState(false);

  useEffect(() => {
    window.livingAgentAPI.localSave.getConfig().then(setConfig);
    window.livingAgentAPI.localSave.getStats().then(setStats);
  }, []);

  if (!config) return <div>加载中...</div>;

  const update = (patch: Partial<LocalSaveConfig>) => {
    const next = { ...config, ...patch };
    setConfig(next);
    window.livingAgentAPI.localSave.setConfig(next);
  };

  const choosePath = async () => {
    const path = await window.livingAgentAPI.localSave.choosePath();
    if (path) update({ basePath: path });
  };

  const trigger = async () => {
    setSyncing(true);
    try {
      await window.livingAgentAPI.localSave.triggerSync();
      const s = await window.livingAgentAPI.localSave.getStats();
      setStats(s);
    } finally {
      setSyncing(false);
    }
  };

  return (
    <div className="local-save-settings">
      <h2>本地产物保存</h2>

      <label>
        <input
          type="checkbox"
          checked={config.enabled}
          onChange={(e) => update({ enabled: e.target.checked })}
        />
        启用本地产物保存
      </label>

      <fieldset>
        <legend>保存路径</legend>
        <div>
          <input value={config.basePath} readOnly style={{ width: 400 }} />
          <button onClick={choosePath}>浏览...</button>
        </div>
        <p style={{ fontSize: 12, color: '#999' }}>
          留空将使用默认路径：~/Documents/LivingAgent
        </p>
      </fieldset>

      <fieldset>
        <legend>保存范围</legend>
        {(['artifacts', 'conversations', 'receipts', 'screenshots'] as const).map((key) => (
          <label key={key} style={{ display: 'block' }}>
            <input
              type="checkbox"
              checked={config.scopes[key]}
              onChange={(e) =>
                update({ scopes: { ...config.scopes, [key]: e.target.checked } })
              }
            />
            {{
              artifacts: 'HTML 产物 / Markdown 报告 / 代码产物',
              conversations: '对话历史',
              receipts: '执行回执',
              screenshots: '截图（体积较大，默认不启用）'
            }[key]}
          </label>
        ))}
      </fieldset>

      <fieldset>
        <legend>容量与保留</legend>
        <label>
          容量上限 (GB):
          <input
            type="number"
            value={config.capacity.maxBytes / (1024 * 1024 * 1024)}
            onChange={(e) =>
              update({
                capacity: {
                  ...config.capacity,
                  maxBytes: Number(e.target.value) * 1024 * 1024 * 1024
                }
              })
            }
          />
        </label>
        <label>
          保留天数:
          <input
            type="number"
            value={config.capacity.retentionDays}
            onChange={(e) =>
              update({
                capacity: { ...config.capacity, retentionDays: Number(e.target.value) }
              })
            }
          />
        </label>
      </fieldset>

      <fieldset>
        <legend>统计</legend>
        {stats ? (
          <ul>
            <li>已用空间：{(stats.totalBytes / (1024 * 1024)).toFixed(2)} MB</li>
            <li>文件数：{stats.fileCount}</li>
            <li>最近同步：{stats.lastSyncAt || '未同步'}</li>
          </ul>
        ) : (
          <p>无统计信息</p>
        )}
        <button onClick={() => window.livingAgentAPI.localSave.openFolder()}>
          打开保存文件夹
        </button>
        <button onClick={trigger} disabled={syncing}>
          {syncing ? '同步中...' : '立即同步'}
        </button>
      </fieldset>
    </div>
  );
}
