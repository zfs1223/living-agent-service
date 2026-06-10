/**
 * 桌面端渲染进程入口
 * - 桌面端是独立安装包，不复用 web 端任何文件
 * - React 入口、CSS、组件全部在 desktop/src/renderer 下
 * - 后端通信走 preload 暴露的 window.livingAgentAPI IPC
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import './index.css';

const rootEl = document.getElementById('root');
if (!rootEl) {
  throw new Error('[desktop] #root element not found');
}

ReactDOM.createRoot(rootEl).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
