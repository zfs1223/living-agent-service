/**
 * 系统托盘
 * - 正常态：托盘普通图标
 * - 任务态：托盘红点（待接取任务 > 0）
 *
 * 由 task-board-tray.ts 控制 pendingCount 后调用 setBadgeVisible()
 */
import { app, Tray, Menu, nativeImage } from 'electron';
import { join } from 'path';
import { SHARED_CONSTANTS } from '../shared/constants';
import { showMainWindow, hideMainWindow } from './window';
import { refreshPendingCount } from './task-board-tray';

let tray: Tray | null = null;
let badgeVisible = false;

function getAssetPath(filename: string): string {
  // 生产环境：resources/assets
  // 开发环境：assets/
  return app.isPackaged
    ? join(process.resourcesPath, 'assets', filename)
    : join(__dirname, '../../assets', filename);
}

export function initTray(): void {
  const iconPath = getAssetPath(SHARED_CONSTANTS.TRAY_NORMAL_ICON);
  const icon = nativeImage.createFromPath(iconPath);
  tray = new Tray(icon.isEmpty() ? nativeImage.createEmpty() : icon);
  tray.setToolTip(SHARED_CONSTANTS.APP_NAME);
  rebuildContextMenu();
  tray.on('click', () => {
    showMainWindow();
  });
}

function rebuildContextMenu(): void {
  if (!tray) return;
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: '🏢 打开主界面', click: () => showMainWindow() },
    { type: 'separator' },
    {
      label: badgeVisible ? '🔴 有待接取任务' : '📋 公共任务栏',
      click: () => {
        showMainWindow();
        // 触发任务中心页面跳转（通过 IPC 通知渲染层）
        const { getMainWindow } = require('./window');
        const w = getMainWindow();
        if (w) w.webContents.send('navigate', '/task-board');
      }
    },
    {
      label: '🔄 刷新待接取数',
      click: () => refreshPendingCount()
    },
    { type: 'separator' },
    { label: '❌ 退出', click: () => { (app as any).isQuitting = true; app.quit(); } }
  ]));
}

export function setBadgeVisible(visible: boolean, count: number): void {
  badgeVisible = visible && count > 0;
  if (!tray) return;

  const iconPath = getAssetPath(
    badgeVisible ? SHARED_CONSTANTS.TRAY_RED_ICON : SHARED_CONSTANTS.TRAY_NORMAL_ICON
  );
  const icon = nativeImage.createFromPath(iconPath);
  if (!icon.isEmpty()) tray.setImage(icon);

  tray.setToolTip(
    badgeVisible
      ? `${SHARED_CONSTANTS.APP_NAME} - 待接取 ${count} 个任务`
      : SHARED_CONSTANTS.APP_NAME
  );
  rebuildContextMenu();
}

export function destroyTray(): void {
  tray?.destroy();
  tray = null;
}
