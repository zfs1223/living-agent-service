/**
 * P2 截图工具 - 主进程服务
 *
 * 功能：
 * - 区域截图（桌面捕获）
 * - 窗口截图
 * - 全屏截图
 * - 截图后自动打开标注编辑器
 * - 标注完成插入到聊天输入框
 *
 * 使用：
 * - 快捷键 Ctrl+Shift+S 触发区域截图
 * - IPC 通道：screenshot:capture, screenshot:capture-region
 */
import { BrowserWindow, desktopCapturer, screen, ipcMain, app, nativeImage } from 'electron';
import { writeFile } from 'fs/promises';
import { join } from 'path';
import { randomUUID } from 'crypto';

let screenshotEditorWindow: BrowserWindow | null = null;

/**
 * 初始化截图服务
 */
export function initScreenshotService(): void {
  // IPC: 截取全屏
  ipcMain.handle('screenshot:capture-full', async () => {
    try {
      const result = await captureFullScreen();
      return { success: true, data: result };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  // IPC: 截取区域（先显示区域选择器）
  ipcMain.handle('screenshot:capture-region', async () => {
    try {
      const result = await captureRegion();
      return { success: true, data: result };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  // IPC: 从标注编辑器获取裁剪后的截图
  ipcMain.handle('screenshot:apply-crop', async (_e, dataUrl: string, region: { x: number; y: number; width: number; height: number }) => {
    try {
      const result = await applyCrop(dataUrl, region);
      return { success: true, data: result };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  // IPC: 保存截图到临时文件
  ipcMain.handle('screenshot:save-temp', async (_e, dataUrl: string) => {
    try {
      const tempPath = join(app.getPath('temp'), `screenshot-${Date.now()}.png`);
      const base64 = dataUrl.replace(/^data:image\/png;base64,/, '');
      await writeFile(tempPath, Buffer.from(base64, 'base64'));
      return { success: true, path: tempPath };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  // IPC: 打开标注编辑器
  ipcMain.handle('screenshot:open-editor', async (_e, imageDataUrl: string) => {
    try {
      await openScreenshotEditor(imageDataUrl);
      return { success: true };
    } catch (e: any) {
      return { success: false, error: e.message };
    }
  });

  // IPC: 关闭标注编辑器
  ipcMain.handle('screenshot:close-editor', async () => {
    closeScreenshotEditor();
    return { success: true };
  });
}

/**
 * 销毁截图服务
 */
export function destroyScreenshotService(): void {
  closeScreenshotEditor();
}

/**
 * 截取全屏
 */
async function captureFullScreen(): Promise<{ dataUrl: string; width: number; height: number }> {
  const primaryDisplay = screen.getPrimaryDisplay();
  const { width, height } = primaryDisplay.workAreaSize;

  // 获取桌面捕获源
  const sources = await desktopCapturer.getSources({
    types: ['screen'],
    thumbnailSize: { width: width * 2, height: height * 2 } // 高 DPI 支持
  });

  if (sources.length === 0) {
    throw new Error('无法获取屏幕捕获源');
  }

  // 使用主屏幕
  const primarySource = sources[0];
  const thumbnail = primarySource.thumbnail;

  return {
    dataUrl: thumbnail.toDataURL(),
    width: thumbnail.getSize().width,
    height: thumbnail.getSize().height
  };
}

/**
 * 截取区域（先显示区域选择器窗口）
 */
async function captureRegion(): Promise<{ dataUrl: string; width: number; height: number }> {
  // 先截取全屏
  const fullScreenshot = await captureFullScreen();

  // 创建区域选择器窗口（全屏透明覆盖）
  const selectorWindow = new BrowserWindow({
    fullscreen: true,
    transparent: true,
    frame: false,
    alwaysOnTop: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: join(__dirname, '../../preload/index.js')
    }
  });

  // 加载区域选择器页面
  await selectorWindow.loadFile(join(__dirname, '../../renderer/region-picker.html'));

  // 发送全屏截图数据
  selectorWindow.webContents.send('screenshot:full-screen', fullScreenshot);

  return new Promise((resolve, reject) => {
    // 监听区域选择完成
    ipcMain.once('screenshot:region-selected', (_e, region: { x: number; y: number; width: number; height: number }) => {
      selectorWindow.close();

      // 裁剪截图
      applyCrop(fullScreenshot.dataUrl, region)
        .then(resolve)
        .catch(reject);
    });

    // 监听取消
    ipcMain.once('screenshot:region-canceled', () => {
      selectorWindow.close();
      reject(new Error('用户取消截图'));
    });

    // 窗口关闭时清理
    selectorWindow.on('closed', () => {
      ipcMain.removeAllListeners('screenshot:region-selected');
      ipcMain.removeAllListeners('screenshot:region-canceled');
    });
  });
}

/**
 * 应用裁剪区域
 */
async function applyCrop(
  dataUrl: string,
  region: { x: number; y: number; width: number; height: number }
): Promise<{ dataUrl: string; width: number; height: number }> {
  const image = nativeImage.createFromDataURL(dataUrl);

  // 使用 Electron 的 resize API（需要转换）
  // 由于 Electron 不直接支持裁剪，我们需要通过渲染进程处理
  // 这里返回原始数据，由标注编辑器处理裁剪
  const cropped = image.crop(region);

  return {
    dataUrl: cropped.toDataURL(),
    width: region.width,
    height: region.height
  };
}

/**
 * 打开标注编辑器
 */
async function openScreenshotEditor(imageDataUrl: string): Promise<void> {
  if (screenshotEditorWindow && !screenshotEditorWindow.isDestroyed()) {
    // 如果已打开，发送新图片
    screenshotEditorWindow.webContents.send('screenshot:set-image', imageDataUrl);
    screenshotEditorWindow.focus();
    return;
  }

  const primaryDisplay = screen.getPrimaryDisplay();
  const { width, height } = primaryDisplay.workAreaSize;

  screenshotEditorWindow = new BrowserWindow({
    width: Math.min(width * 0.8, 1200),
    height: Math.min(height * 0.8, 800),
    minWidth: 600,
    minHeight: 400,
    title: '截图标注',
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: join(__dirname, '../../preload/index.js')
    }
  });

  // 加载标注编辑器页面
  await screenshotEditorWindow.loadFile(join(__dirname, '../../renderer/screenshot-editor.html'));

  // 发送初始图片
  screenshotEditorWindow.webContents.send('screenshot:set-image', imageDataUrl);

  // 窗口关闭时清理
  screenshotEditorWindow.on('closed', () => {
    screenshotEditorWindow = null;
  });
}

/**
 * 关闭标注编辑器
 */
function closeScreenshotEditor(): void {
  if (screenshotEditorWindow && !screenshotEditorWindow.isDestroyed()) {
    screenshotEditorWindow.close();
  }
}

/**
 * 触发区域截图（供快捷键调用）
 */
export async function triggerRegionScreenshot(): Promise<void> {
  try {
    const result = await captureRegion();
    // 截图成功后打开标注编辑器
    await openScreenshotEditor(result.dataUrl);
  } catch (e: any) {
    console.error('[ScreenshotService] Region screenshot failed:', e);
  }
}

/**
 * 触发全屏截图（供快捷键调用）
 */
export async function triggerFullScreenScreenshot(): Promise<void> {
  try {
    const result = await captureFullScreen();
    // 截图成功后打开标注编辑器
    await openScreenshotEditor(result.dataUrl);
  } catch (e: any) {
    console.error('[ScreenshotService] Full screenshot failed:', e);
  }
}