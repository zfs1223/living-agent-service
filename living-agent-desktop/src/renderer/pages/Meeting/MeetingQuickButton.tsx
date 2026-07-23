/**
 * 会议快捷入口组件 - P83/P84 桌面端会议 UI / 闭环 67
 *
 * <p>侧边栏/桌面快捷按钮，提供快速创建会议和查看即将开始会议的入口。
 * 点击"快速会议"→立即创建并进入；点击预约→跳转预约表单。</p>
 *
 * @author P83 桌面端会议 UI
 * @since 1.0.0
 */

import React, { useState } from 'react';

interface MeetingQuickButtonProps {
  /** 快速创建会议回调 */
  onQuickMeeting?: () => void;
  /** 跳转预约页面回调 */
  onScheduleMeeting?: () => void;
  /** 即将开始的会议数量（显示徽标） */
  upcomingCount?: number;
}

/**
 * 会议快捷入口组件
 */
export const MeetingQuickButton: React.FC<MeetingQuickButtonProps> = ({
  onQuickMeeting,
  onScheduleMeeting,
  upcomingCount = 0,
}) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div style={{ position: 'relative' }}>
      {/* 主按钮 */}
      <button
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          padding: '6px 12px',
          background: '#337ab7',
          color: '#fff',
          border: 'none',
          borderRadius: 4,
          cursor: 'pointer',
          fontSize: 13,
          fontWeight: 500,
          position: 'relative',
        }}
      >
        📹 会议
        {upcomingCount > 0 && (
          <span style={{
            position: 'absolute',
            top: -6,
            right: -6,
            background: '#d9534f',
            color: '#fff',
            borderRadius: '50%',
            width: 18,
            height: 18,
            fontSize: 11,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
            {upcomingCount > 9 ? '9+' : upcomingCount}
          </span>
        )}
      </button>

      {/* 展开菜单 */}
      {expanded && (
        <div style={{
          position: 'absolute',
          top: '100%',
          right: 0,
          marginTop: 4,
          background: '#fff',
          border: '1px solid #ddd',
          borderRadius: 4,
          boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          minWidth: 140,
          zIndex: 100,
        }}>
          <button
            onClick={() => { onQuickMeeting?.(); setExpanded(false); }}
            style={{
              display: 'block',
              width: '100%',
              padding: '8px 12px',
              background: 'transparent',
              border: 'none',
              textAlign: 'left',
              cursor: 'pointer',
              fontSize: 13,
            }}
            onMouseEnter={(e) => e.currentTarget.style.background = '#f5f5f5'}
            onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
          >
            🚀 快速会议
          </button>
          <button
            onClick={() => { onScheduleMeeting?.(); setExpanded(false); }}
            style={{
              display: 'block',
              width: '100%',
              padding: '8px 12px',
              background: 'transparent',
              border: 'none',
              textAlign: 'left',
              cursor: 'pointer',
              fontSize: 13,
            }}
            onMouseEnter={(e) => e.currentTarget.style.background = '#f5f5f5'}
            onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
          >
            📅 预约会议
          </button>
        </div>
      )}
    </div>
  );
};

export default MeetingQuickButton;
