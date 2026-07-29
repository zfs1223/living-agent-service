/**
 * 应用菜单栏
 */
import { app, Menu, shell } from 'electron';
import { SHARED_CONSTANTS } from '../shared/constants';
import { showMainWindow } from './window';
import { openLocalSaveFolder, triggerSync } from './local-save-sync';

export function buildApplicationMenu(): void {
  const isMac = process.platform === 'darwin';
  const template: Electron.MenuItemConstructorOptions[] = [
    ...(isMac ? [{
      label: SHARED_CONSTANTS.APP_NAME,
      submenu: [
        { role: 'about' as const, label: '关于' },
        { type: 'separator' as const },
        { role: 'services' as const },
        { type: 'separator' as const },
        { role: 'hide' as const, label: '隐藏' },
        { role: 'hideOthers' as const },
        { role: 'unhide' as const },
        { type: 'separator' as const },
        { role: 'quit' as const, label: '退出' }
      ]
    }] : []),
    {
      label: '文件',
      submenu: [
        {
          label: '🏢 打开主界面',
          accelerator: 'CmdOrCtrl+Shift+M',
          click: () => showMainWindow()
        },
        { type: 'separator' },
        {
          label: '📂 打开本地产物文件夹',
          click: () => openLocalSaveFolder()
        },
        {
          label: '🔄 立即同步本地产物',
          click: () => triggerSync()
        },
        { type: 'separator' },
        isMac ? { role: 'close', label: '关闭窗口' } : { role: 'quit', label: '退出' }
      ]
    },
    {
      label: '编辑',
      submenu: [
        { role: 'undo', label: '撤销' },
        { role: 'redo', label: '重做' },
        { type: 'separator' },
        { role: 'cut', label: '剪切' },
        { role: 'copy', label: '复制' },
        { role: 'paste', label: '粘贴' }
      ]
    },
    {
      label: '沟通',
      submenu: [
        {
          label: '办公室聊天',
          click: () => showMainWindow('/office-chat')
        },
        { type: 'separator' },
        {
          label: '历史对话',
          click: () => showMainWindow('/chat-history')
        }
      ]
    },
    {
      label: '视图',
      submenu: [
        { role: 'reload', label: '重新加载' },
        { role: 'forceReload', label: '强制重新加载' },
        { role: 'toggleDevTools', label: '开发者工具' },
        { type: 'separator' },
        { role: 'resetZoom', label: '重置缩放' },
        { role: 'zoomIn', label: '放大' },
        { role: 'zoomOut', label: '缩小' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: '全屏' }
      ]
    },
    {
      label: '窗口',
      submenu: [
        { role: 'minimize', label: '最小化' },
        { role: 'close', label: '关闭' }
      ]
    },
    {
      label: '帮助',
      submenu: [
        {
          label: '📖 用户文档',
          click: () => shell.openExternal('https://github.com/your-org/living-agent-service')
        },
        {
          label: '🐛 反馈问题',
          click: () => shell.openExternal('https://github.com/your-org/living-agent-service/issues')
        },
        { type: 'separator' },
        {
          label: 'ℹ️ 关于',
          click: () => {
            const { dialog } = require('electron');
            dialog.showMessageBox({
              type: 'info',
              title: '关于',
              message: SHARED_CONSTANTS.APP_NAME,
              detail: `版本: ${app.getVersion()}\nElectron: ${process.versions.electron}\nNode: ${process.versions.node}`
            });
          }
        }
      ]
    }
  ];

  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}
