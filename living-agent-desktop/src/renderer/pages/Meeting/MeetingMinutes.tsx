/**
 * 会议纪要展示组件 - P82 录制与纪要自动化 / 闭环 68
 *
 * <p>展示会议纪要的摘要、决议事项、待办任务等内容。
 * 纪要数据由后端 MeetingMinutesService 通过 ASR→LLM 管线自动生成。</p>
 *
 * @author P82 录制与纪要自动化
 * @since 1.0.0
 */

import React, { useEffect, useState } from 'react';

/** 会议纪要数据 */
interface MeetingMinutesData {
  minutesId: string;
  roomName: string;
  title: string;
  summary: string;
  resolutions: string[];   // 决议事项
  actionItems: ActionItem[]; // 待办任务
  fullText?: string;        // 转写全文（展开显示）
  status: 'GENERATING' | 'COMPLETED' | 'FAILED';
  generatedAt?: string;
}

/** 待办任务 */
interface ActionItem {
  content: string;
  assignee?: string;
  deadline?: string;
  done?: boolean;
}

interface MeetingMinutesProps {
  /** LiveKit 房间名称 */
  roomName: string;
  /** 后端 URL */
  backendUrl?: string;
}

/**
 * 会议纪要展示组件
 */
export const MeetingMinutes: React.FC<MeetingMinutesProps> = ({ roomName, backendUrl }) => {
  const [minutes, setMinutes] = useState<MeetingMinutesData | null>(null);
  const [loading, setLoading] = useState(true);
  const [showFullText, setShowFullText] = useState(false);

  useEffect(() => {
    if (!backendUrl || !roomName) return;

    const fetchMinutes = async () => {
      try {
        const token = await window.livingAgentAPI.auth.getToken();
        const res = await fetch(
          `${backendUrl}/api/meetings/minutes?roomName=${encodeURIComponent(roomName)}`,
          { headers: { Authorization: `Bearer ${token}` } }
        );
        if (res.ok) {
          const json = await res.json();
          setMinutes(json?.data ?? json);
        }
      } catch (e) {
        console.warn('[MeetingMinutes] 获取纪要失败:', e);
      } finally {
        setLoading(false);
      }
    };

    fetchMinutes();
    // 每 30 秒刷新（纪要可能在生成中）
    const timer = setInterval(fetchMinutes, 30000);
    return () => clearInterval(timer);
  }, [roomName, backendUrl]);

  if (loading) {
    return (
      <div style={{ padding: 16, textAlign: 'center', color: '#888' }}>
        加载纪要中...
      </div>
    );
  }

  if (!minutes) {
    return (
      <div style={{ padding: 16, color: '#888' }}>
        暂无会议纪要
      </div>
    );
  }

  if (minutes.status === 'GENERATING') {
    return (
      <div style={{ padding: 16, color: '#f0ad4e' }}>
        纪要生成中...
      </div>
    );
  }

  if (minutes.status === 'FAILED') {
    return (
      <div style={{ padding: 16, color: '#d9534f' }}>
        纪要生成失败
      </div>
    );
  }

  return (
    <div style={{ padding: 16 }}>
      <h3 style={{ marginBottom: 8 }}>{minutes.title} - 会议纪要</h3>

      {/* 摘要 */}
      {minutes.summary && (
        <div style={{ marginBottom: 12 }}>
          <strong>摘要</strong>
          <p style={{ marginTop: 4, whiteSpace: 'pre-wrap' }}>{minutes.summary}</p>
        </div>
      )}

      {/* 决议事项 */}
      {minutes.resolutions?.length > 0 && (
        <div style={{ marginBottom: 12 }}>
          <strong>决议事项</strong>
          <ul style={{ marginTop: 4, paddingLeft: 20 }}>
            {minutes.resolutions.map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>
        </div>
      )}

      {/* 待办任务 */}
      {minutes.actionItems?.length > 0 && (
        <div style={{ marginBottom: 12 }}>
          <strong>待办任务</strong>
          <ul style={{ marginTop: 4, paddingLeft: 20, listStyle: 'none' }}>
            {minutes.actionItems.map((item, i) => (
              <li key={i} style={{ textDecoration: item.done ? 'line-through' : 'none', marginBottom: 4 }}>
                {item.content}
                {item.assignee && <span style={{ color: '#888', marginLeft: 8 }}>@{item.assignee}</span>}
                {item.deadline && <span style={{ color: '#888', marginLeft: 8 }}>截止: {item.deadline}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 转写全文（可折叠） */}
      {minutes.fullText && (
        <div>
          <strong
            style={{ cursor: 'pointer', color: '#337ab7' }}
            onClick={() => setShowFullText(!showFullText)}
          >
            {showFullText ? '▼ 收起全文' : '▶ 展开全文'}
          </strong>
          {showFullText && (
            <pre style={{ marginTop: 8, padding: 8, background: '#f5f5f5', borderRadius: 4, maxHeight: 400, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
              {minutes.fullText}
            </pre>
          )}
        </div>
      )}

      {/* 生成时间 */}
      {minutes.generatedAt && (
        <div style={{ marginTop: 12, color: '#888', fontSize: 12 }}>
          生成时间: {new Date(minutes.generatedAt).toLocaleString()}
        </div>
      )}
    </div>
  );
};

export default MeetingMinutes;
