/**
 * 桌面端公共任务中心页面
 * 桌面端是独立安装包，所有 UI 组件都在 src/renderer/components 下，
 * 不复用 web 端 frontend/src/components。组件通过 window.livingAgentAPI IPC
 * 与主进程通信，由主进程转发到后端服务（与 web 端共享同一后端 API）。
 */
import React from 'react';
import PublicTaskBoard from '../../components/PublicTaskBoard';

export function PublicTaskBoardPage() {
  return (
    <div className="desktop-task-board-page" style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <header style={{ marginBottom: 16 }}>
        <h1>📋 公共任务栏</h1>
        <p style={{ color: '#666' }}>
          固定数字员工无法处理的任务，可接取并完成以获得积分奖励
        </p>
      </header>
      <PublicTaskBoard />
    </div>
  );
}
