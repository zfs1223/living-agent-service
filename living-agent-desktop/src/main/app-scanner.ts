/**
 * 本机应用扫描器
 * 
 * 目的：
 * - 扫描 Windows 系统已安装的应用程序
 * - 生成应用列表供后端记录
 * - 用于 WindowsAppTool 路由时了解目标设备可操作的应用
 * 
 * 扫描策略：
 * 1. 注册表：HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall
 * 2. 注册表：HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall
 * 3. 开始菜单快捷方式
 * 4. 桌面快捷方式
 */
import { exec } from 'child_process';
import { promisify } from 'util';
import { readdir } from 'fs/promises';
import { join } from 'path';

const execAsync = promisify(exec);

export interface InstalledApp {
  name: string;
  version?: string;
  publisher?: string;
  installLocation?: string;
  uninstallString?: string;
}

/**
 * 从注册表扫描已安装应用
 * 注意：Windows 中文系统 reg 命令返回 GBK 编码，需设置 chcp 65001
 */
async function scanRegistry(): Promise<InstalledApp[]> {
  const apps: InstalledApp[] = [];
  
  // 扫描 HKLM 和 HKCU 的 Uninstall 键
  const registryPaths = [
    'HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall',
    'HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall'
  ];
  
  for (const regPath of registryPaths) {
    try {
      // 先切换到 UTF-8 编码，再执行 reg query
      // 使用 /s 递归查询，但直接查询子键而非通配符
      const { stdout } = await execAsync(
        `chcp 65001 >nul && reg query "${regPath}" /s`,
        { 
          encoding: 'utf-8',
          maxBuffer: 1024 * 1024 * 10  // 10MB buffer
        }
      );
      
      // 解析注册表输出
      const lines = stdout.split('\n');
      let currentApp: Partial<InstalledApp> = {};
      
      for (const line of lines) {
        const trimmed = line.trim();
        
        if (trimmed.startsWith('HKEY_')) {
          // 新的应用条目
          if (currentApp.name) {
            apps.push(currentApp as InstalledApp);
          }
          currentApp = {};
        } else if (trimmed.includes('DisplayName')) {
          const match = trimmed.match(/DisplayName\s+REG_SZ\s+(.+)/);
          if (match) {
            currentApp.name = match[1].trim();
          }
        } else if (trimmed.includes('DisplayVersion')) {
          const match = trimmed.match(/DisplayVersion\s+REG_SZ\s+(.+)/);
          if (match) {
            currentApp.version = match[1].trim();
          }
        } else if (trimmed.includes('Publisher')) {
          const match = trimmed.match(/Publisher\s+REG_SZ\s+(.+)/);
          if (match) {
            currentApp.publisher = match[1].trim();
          }
        } else if (trimmed.includes('InstallLocation')) {
          const match = trimmed.match(/InstallLocation\s+REG_SZ\s+(.+)/);
          if (match) {
            currentApp.installLocation = match[1].trim();
          }
        } else if (trimmed.includes('UninstallString')) {
          const match = trimmed.match(/UninstallString\s+REG_SZ\s+(.+)/);
          if (match) {
            currentApp.uninstallString = match[1].trim();
          }
        }
      }
      
      // 最后一个应用
      if (currentApp.name) {
        apps.push(currentApp as InstalledApp);
      }
    } catch (e) {
      console.warn('[app-scanner] Failed to scan registry path:', regPath, e);
    }
  }
  
  return apps;
}

/**
 * 扫描开始菜单快捷方式
 */
async function scanStartMenu(): Promise<string[]> {
  const apps: string[] = [];
  const startMenuPaths = [
    'C:\\ProgramData\\Microsoft\\Windows\\Start Menu\\Programs',
    join(process.env.APPDATA || '', 'Microsoft\\Windows\\Start Menu\\Programs')
  ];
  
  for (const dir of startMenuPaths) {
    try {
      const files = await readdir(dir, { recursive: true });
      for (const file of files) {
        if (file.endsWith('.lnk')) {
          // 提取应用名称（去掉 .lnk 后缀）
          const appName = file.replace('.lnk', '');
          if (!apps.includes(appName)) {
            apps.push(appName);
          }
        }
      }
    } catch (e) {
      console.warn('[app-scanner] Failed to scan start menu:', dir, e);
    }
  }
  
  return apps;
}

/**
 * 扫描桌面快捷方式
 */
async function scanDesktop(): Promise<string[]> {
  const apps: string[] = [];
  const desktopPath = join(process.env.USERPROFILE || '', 'Desktop');
  
  try {
    const files = await readdir(desktopPath);
    for (const file of files) {
      if (file.endsWith('.lnk')) {
        const appName = file.replace('.lnk', '');
        if (!apps.includes(appName)) {
          apps.push(appName);
        }
      }
    }
  } catch (e) {
    console.warn('[app-scanner] Failed to scan desktop:', e);
  }
  
  return apps;
}

/**
 * 扫描所有已安装应用
 */
export async function scanInstalledApps(): Promise<InstalledApp[]> {
  console.log('[app-scanner] Scanning installed applications...');
  
  // 1. 从注册表扫描
  const registryApps = await scanRegistry();
  console.log('[app-scanner] Found', registryApps.length, 'apps from registry');
  
  // 2. 从开始菜单扫描
  const startMenuApps = await scanStartMenu();
  console.log('[app-scanner] Found', startMenuApps.length, 'apps from start menu');
  
  // 3. 从桌面扫描
  const desktopApps = await scanDesktop();
  console.log('[app-scanner] Found', desktopApps.length, 'apps from desktop');
  
  // 4. 合并去重
  const appMap = new Map<string, InstalledApp>();
  
  // 优先使用注册表信息（更完整）
  for (const app of registryApps) {
    if (app.name) {
      appMap.set(app.name.toLowerCase(), app);
    }
  }
  
  // 补充开始菜单和桌面的应用
  for (const appName of [...startMenuApps, ...desktopApps]) {
    const key = appName.toLowerCase();
    if (!appMap.has(key)) {
      appMap.set(key, { name: appName });
    }
  }
  
  const result = Array.from(appMap.values());
  console.log('[app-scanner] Total unique apps:', result.length);
  
  return result;
}

/**
 * 生成应用列表字符串（逗号分隔）
 */
export async function getInstalledAppsString(): Promise<string> {
  const apps = await scanInstalledApps();
  return apps.map(a => a.name).join(',');
}
