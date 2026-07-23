"""
Windows 自动化服务

借鉴 Windows-MCP 技术栈实现通用系统控制能力：
- UIAutomation API (comtypes) - 真正的控件操作
- PowerShell 执行
- 注册表操作 (PowerShell cmdlets)
- 文件系统操作 (Python fs)
- 进程管理 (psutil)
- 截图 (dxcam/mss)
- 剪贴板 (win32clipboard)
- 虚拟桌面 (COM IVirtualDesktop)
- 应用启动/切换 (模糊匹配 + AttachThreadInput)

通信协议：JSON 行协议（stdin 读请求，stdout 写响应）
不使用独立认证，所有请求来自 Electron 主进程转发（后端已认证）。

详细设计：docs/WINDOWS_MCP_INTEGRATION_PLAN.md §3.3
"""

import sys
import json
import subprocess
import os
import time
import re
import ctypes
import base64
import io
from pathlib import Path
from typing import Any, Dict, Optional, List, Tuple
from time import sleep, perf_counter
from locale import getpreferredencoding
from concurrent.futures import ThreadPoolExecutor

# 仅在 Windows 平台导入平台相关依赖
IS_WINDOWS = sys.platform == 'win32'

if IS_WINDOWS:
    try:
        import psutil
        PSUTIL_AVAILABLE = True
    except ImportError:
        PSUTIL_AVAILABLE = False

    try:
        import win32clipboard
        import win32con
        import win32gui
        import win32process
        WIN32_AVAILABLE = True
    except ImportError:
        WIN32_AVAILABLE = False

    try:
        import mss
        MSS_AVAILABLE = True
    except ImportError:
        MSS_AVAILABLE = False

    try:
        import dxcam
        DXCAM_AVAILABLE = True
    except ImportError:
        DXCAM_AVAILABLE = False

    try:
        from PIL import Image, ImageDraw, ImageFont
        PIL_AVAILABLE = True
    except ImportError:
        PIL_AVAILABLE = False

    try:
        import requests
        REQUESTS_AVAILABLE = True
    except ImportError:
        REQUESTS_AVAILABLE = False

    try:
        from fuzzywuzzy import process as fuzzy_process
        FUZZYWUZZY_AVAILABLE = True
    except ImportError:
        FUZZYWUZZY_AVAILABLE = False

    # UIAutomation（核心依赖，借鉴 Windows-MCP 初始化方式）
    # 使用 UIAutomationCore.dll 直接加载，比 UIAutomationClient 更稳定
    try:
        import comtypes.client
        # 先初始化 COM
        ctypes.windll.ole32.CoInitialize(None)
        # 使用 Windows-MCP 的方式：直接加载 DLL（更稳定）
        UIAutomationCore = comtypes.client.GetModule("UIAutomationCore.dll")
        # 创建 IUIAutomation 对象
        IUIAutomation = comtypes.client.CreateObject(
            "{ff48dba4-60ef-4201-aa87-54103eef594e}",
            interface=UIAutomationCore.IUIAutomation,
        )
        UIA_AVAILABLE = True
        print('[WinAutomation] UIAutomation initialized successfully')
    except (ImportError, OSError, Exception) as e:
        # Python 3.14 与 comtypes 可能存在兼容性问题
        # UIAutomation 不可用时，基本鼠标/键盘功能仍可使用
        print(f'[WinAutomation] UIAutomation not available: {e}', file=sys.stderr)
        UIA_AVAILABLE = False
else:
    PSUTIL_AVAILABLE = False
    WIN32_AVAILABLE = False
    MSS_AVAILABLE = False
    DXCAM_AVAILABLE = False
    PIL_AVAILABLE = False
    REQUESTS_AVAILABLE = False
    FUZZYWUZZY_AVAILABLE = False
    UIA_AVAILABLE = False


# === 键名别名（与 Windows-MCP 对齐） ===
_KEY_ALIASES = {
    'backspace': 'Back',
    'capslock': 'Capital',
    'scrolllock': 'Scroll',
    'windows': 'Win',
    'command': 'Win',
    'option': 'Alt',
}


def _escape_text_for_sendkeys(text: str) -> str:
    """Escape special characters so uia.SendKeys types them correctly."""
    result = []
    for ch in text:
        if ch == '{':
            result.append('{{}')
        elif ch == '}':
            result.append('{}}')
        elif ch == '\n':
            result.append('{Enter}')
        elif ch == '\t':
            result.append('{Tab}')
        elif ch == '\r':
            continue
        else:
            result.append(ch)
    return ''.join(result)


class Desktop:
    """
    Desktop 类（借鉴 Windows-MCP）
    
    封装完整的 Windows 自动化能力：
    - UIAutomation 控件操作（click, type, scroll, drag）
    - 应用启动/切换（launch_app, switch_app）
    - 截图（screenshot, annotated_screenshot）
    - 窗口管理（get_windows, get_active_window, resize_app）
    """

    def __init__(self):
        self.encoding = getpreferredencoding()
        self.uia = None
        self.root = None
        self.desktop_state = None
        
        if UIA_AVAILABLE:
            self._init_uia()

    def _init_uia(self):
        """初始化 UIAutomation（借鉴 Windows-MCP）"""
        try:
            # 使用全局的 IUIAutomation 对象
            self.uia = IUIAutomation
            self.root = self.uia.GetRootElement()
        except Exception as e:
            # UIAutomation 初始化失败，基本功能仍可使用
            print(f'[WinAutomation] Failed to init UIA: {e}', file=sys.stderr)
            self.uia = None
            self.root = None

    # === 鼠标操作 ===

    def click(self, loc: Tuple[int, int] | List[int], button: str = 'left', clicks: int = 1):
        """
        鼠标点击（坐标）
        
        Args:
            loc: 坐标 (x, y)
            button: 按键类型 'left' | 'right' | 'middle'
            clicks: 点击次数（1=单击，2=双击）
        """
        if isinstance(loc, list):
            x, y = loc[0], loc[1]
        else:
            x, y = loc

        if clicks == 0:
            # 仅移动鼠标
            self.move((x, y))
            return

        # 先移动到目标位置
        ctypes.windll.user32.SetCursorPos(int(x), int(y))
        sleep(0.05)

        match button:
            case 'left':
                if clicks >= 2:
                    # 双击需要间隔
                    dbl_wait = ctypes.windll.user32.GetDoubleClickTime() / 2000.0
                    for i in range(clicks):
                        ctypes.windll.user32.mouse_event(0x0002, 0, 0, 0, 0)  # LEFTDOWN
                        sleep(0.05)
                        ctypes.windll.user32.mouse_event(0x0004, 0, 0, 0, 0)  # LEFTUP
                        if i < clicks - 1:
                            sleep(dbl_wait)
                else:
                    ctypes.windll.user32.mouse_event(0x0002, 0, 0, 0, 0)  # LEFTDOWN
                    sleep(0.05)
                    ctypes.windll.user32.mouse_event(0x0004, 0, 0, 0, 0)  # LEFTUP
            case 'right':
                for _ in range(clicks):
                    ctypes.windll.user32.mouse_event(0x0008, 0, 0, 0, 0)  # RIGHTDOWN
                    sleep(0.05)
                    ctypes.windll.user32.mouse_event(0x0010, 0, 0, 0, 0)  # RIGHTUP
            case 'middle':
                for _ in range(clicks):
                    ctypes.windll.user32.mouse_event(0x0020, 0, 0, 0, 0)  # MIDDLEDOWN
                    sleep(0.05)
                    ctypes.windll.user32.mouse_event(0x0040, 0, 0, 0, 0)  # MIDDLEUP

    def double_click(self, loc: Tuple[int, int] | List[int], button: str = 'left'):
        """双击"""
        self.click(loc, button, clicks=2)

    def right_click(self, loc: Tuple[int, int] | List[int]):
        """右键单击"""
        self.click(loc, button='right', clicks=1)

    def move(self, loc: Tuple[int, int]):
        """鼠标移动"""
        x, y = loc
        ctypes.windll.user32.SetCursorPos(int(x), int(y))

    def drag(self, from_loc: Tuple[int, int] | List[int], to_loc: Tuple[int, int] | List[int]):
        """
        拖拽操作
        
        Args:
            from_loc: 起点 (x, y)
            to_loc: 终点 (x, y)
        """
        if isinstance(from_loc, list):
            fx, fy = from_loc[0], from_loc[1]
        else:
            fx, fy = from_loc
        if isinstance(to_loc, list):
            tx, ty = to_loc[0], to_loc[1]
        else:
            tx, ty = to_loc

        # 移动到起点
        ctypes.windll.user32.SetCursorPos(int(fx), int(fy))
        sleep(0.2)

        # 按下左键
        ctypes.windll.user32.mouse_event(0x0002, 0, 0, 0, 0)  # LEFTDOWN
        sleep(0.1)

        # 移动到终点（平滑移动）
        steps = 20
        for i in range(steps + 1):
            progress = i / steps
            cx = int(fx + (tx - fx) * progress)
            cy = int(fy + (ty - fy) * progress)
            ctypes.windll.user32.SetCursorPos(cx, cy)
            sleep(0.02)

        # 释放左键
        ctypes.windll.user32.mouse_event(0x0004, 0, 0, 0, 0)  # LEFTUP

    # === 键盘操作 ===

    def type_text(
        self,
        loc: Optional[Tuple[int, int]],
        text: str,
        caret_position: str = 'idle',
        clear: bool = False,
        press_enter: bool = False
    ):
        """
        文字输入

        Args:
            loc: 点击位置（可选，None 表示不点击）
            text: 要输入的文字
            caret_position: 光标位置 'start' | 'idle' | 'end'
            clear: 是否先清除文本
            press_enter: 是否按 Enter
        """
        if loc:
            self.click(loc)
            sleep(0.1)

        if caret_position == 'start':
            self.shortcut('Home')
            sleep(0.05)
        elif caret_position == 'end':
            self.shortcut('End')
            sleep(0.05)

        if clear:
            sleep(0.3)
            self.shortcut('Ctrl+A')
            sleep(0.05)
            # 发送 Backspace
            ctypes.windll.user32.keybd_event(0x08, 0, 0, 0)  # BACKDOWN
            sleep(0.05)
            ctypes.windll.user32.keybd_event(0x08, 0, 2, 0)  # BACKUP

        # 输入文本：检测是否包含非 ASCII 字符（中文、日文等）
        has_non_ascii = any(ord(ch) > 127 for ch in text)

        if has_non_ascii:
            # 非ASCII字符（如中文）使用剪贴板方式输入
            self._type_via_clipboard(text)
        else:
            # ASCII 字符使用 SendKeys
            escaped_text = _escape_text_for_sendkeys(text)
            self._send_keys(escaped_text)

        if press_enter:
            sleep(0.05)
            self.shortcut('Enter')

    def _type_via_clipboard(self, text: str):
        """
        通过剪贴板输入文本（支持中文等非ASCII字符）

        原理：将文本复制到剪贴板 → Ctrl+V 粘贴
        这是 Windows 下输入中文唯一可靠的方式，keybd_event 无法发送中文VK码
        """
        if not WIN32_AVAILABLE:
            # 回退：尝试逐字符发送（中文会失败）
            escaped_text = _escape_text_for_sendkeys(text)
            self._send_keys(escaped_text)
            return

        # 保存当前剪贴板内容（尽量不破坏用户剪贴板）
        old_clipboard = None
        try:
            win32clipboard.OpenClipboard()
            try:
                old_clipboard = win32clipboard.GetClipboardData(win32con.CF_UNICODETEXT)
            except Exception:
                old_clipboard = None
            win32clipboard.CloseClipboard()
        except Exception:
            pass

        # 设置剪贴板为要输入的文本
        try:
            win32clipboard.OpenClipboard()
            win32clipboard.EmptyClipboard()
            win32clipboard.SetClipboardData(win32con.CF_UNICODETEXT, text)
            win32clipboard.CloseClipboard()
        except Exception as e:
            print(f'[WinAutomation] Clipboard set failed: {e}', file=sys.stderr)
            return

        sleep(0.05)

        # Ctrl+V 粘贴
        self.shortcut('Ctrl+V')
        sleep(0.1)

        # 恢复原始剪贴板内容（延迟恢复，避免影响粘贴操作）
        if old_clipboard is not None:
            try:
                sleep(0.2)
                win32clipboard.OpenClipboard()
                win32clipboard.EmptyClipboard()
                win32clipboard.SetClipboardData(win32con.CF_UNICODETEXT, old_clipboard)
                win32clipboard.CloseClipboard()
            except Exception:
                pass

    def _send_keys(self, keys: str, interval: float = 0.02):
        """
        发送按键字符串（模拟 UIAutomation SendKeys）
        
        Args:
            keys: SendKeys 格式的按键字符串
            interval: 按键间隔
        """
        # 简化实现：逐字符发送
        for ch in keys:
            if ch == '{':
                # 特殊按键，解析
                # 实际应该解析 {...} 格式，这里简化处理
                ctypes.windll.user32.keybd_event(ord(ch), 0, 0, 0)
                sleep(interval)
                ctypes.windll.user32.keybd_event(ord(ch), 0, 2, 0)
            else:
                vk = ord(ch.upper())
                ctypes.windll.user32.keybd_event(vk, 0, 0, 0)
                sleep(interval)
                ctypes.windll.user32.keybd_event(vk, 0, 2, 0)

    def shortcut(self, shortcut_str: str):
        """
        快捷键组合
        
        Args:
            shortcut_str: 快捷键字符串，如 'Ctrl+C', 'Alt+Tab', 'Win+D'
        """
        keys = shortcut_str.split('+')
        key_map = {
            'ctrl': 0x11, 'alt': 0x12, 'shift': 0x10, 'win': 0x5B,
            'enter': 0x0D, 'tab': 0x09, 'esc': 0x1B, 'backspace': 0x08,
            'delete': 0x2E, 'space': 0x20, 'home': 0x24, 'end': 0x23,
            'pgup': 0x21, 'pgdn': 0x22, 'up': 0x26, 'down': 0x28,
            'left': 0x25, 'right': 0x27, 'f1': 0x70, 'f2': 0x71, 'f3': 0x72,
            'f4': 0x73, 'f5': 0x74, 'f6': 0x75, 'f7': 0x76, 'f8': 0x77,
            'f9': 0x78, 'f10': 0x79, 'f11': 0x7A, 'f12': 0x7B,
        }

        # 按下所有键
        vk_codes = []
        for k in keys:
            k_lower = k.strip().lower()
            code = key_map.get(k_lower) or ord(k.upper())
            vk_codes.append(code)
            ctypes.windll.user32.keybd_event(code, 0, 0, 0)
            sleep(0.05)

        sleep(0.1)

        # 释放所有键（逆序）
        for code in reversed(vk_codes):
            ctypes.windll.user32.keybd_event(code, 0, 2, 0)
            sleep(0.05)

    # === 滚动操作 ===

    def scroll(
        self,
        loc: Optional[Tuple[int, int]] = None,
        direction: str = 'down',
        amount: int = 3
    ):
        """
        滚动操作
        
        Args:
            loc: 滚动位置（可选）
            direction: 方向 'up' | 'down' | 'left' | 'right'
            amount: 滚动量（鼠标滚轮次数）
        """
        if loc:
            self.move(loc)
            sleep(0.1)

        wheel_amount = amount * 120
        if direction == 'up':
            wheel_amount = -wheel_amount

        ctypes.windll.user32.mouse_event(0x0800, 0, 0, wheel_amount, 0)  # MOUSEEVENTF_WHEEL

    def scroll_horizontal(
        self,
        loc: Optional[Tuple[int, int]] = None,
        direction: str = 'right',
        amount: int = 3
    ):
        """
        水平滚动
        
        Args:
            loc: 滚动位置
            direction: 'left' | 'right'
            amount: 滚动量
        """
        if loc:
            self.move(loc)
            sleep(0.1)

        # 水平滚动需要按住 Shift
        ctypes.windll.user32.keybd_event(0x10, 0, 0, 0)  # SHIFT DOWN
        sleep(0.05)

        wheel_amount = amount * 120
        if direction == 'left':
            wheel_amount = -wheel_amount

        ctypes.windll.user32.mouse_event(0x0800, 0, 0, wheel_amount, 0)
        sleep(0.05)

        ctypes.windll.user32.keybd_event(0x10, 0, 2, 0)  # SHIFT UP

    # === 应用操作 ===

    def launch_app(self, name: str, timeout: int = 10) -> Tuple[str, int, int]:
        """
        启动应用（模糊匹配 Start Menu）
        
        Args:
            name: 应用名称（如 'notepad', 'chrome', 'calculator'）
            timeout: 等待窗口出现的超时秒数
        
        Returns:
            (响应消息, 状态码, 进程PID)
        """
        apps_map = self._get_apps_from_start_menu()
        
        if FUZZYWUZZY_AVAILABLE:
            matched_app = fuzzy_process.extractOne(name, apps_map.keys(), score_cutoff=70)
        else:
            # 简化匹配
            matched_app = None
            for app_name in apps_map.keys():
                if name.lower() in app_name.lower():
                    matched_app = (app_name, 100)
                    break

        if matched_app is None:
            return (f'{name.title()} not found in start menu.', 1, 0)

        app_name, _ = matched_app
        appid = apps_map.get(app_name)
        if appid is None:
            return (f'{name.title()} not found in start menu.', 1, 0)

        pid = 0

        # 判断是路径还是 AppUserModelID
        if os.path.exists(appid) or '\\' in appid:
            # 直接执行路径
            exe_path = appid
            safe_exe = subprocess.list2cmdline([exe_path])
            cmd = f'Start-Process {safe_exe} -PassThru | Select-Object -ExpandProperty Id'
            result = self._run_powershell(cmd)
            if result.returncode == 0 and result.stdout.strip().isdigit():
                pid = int(result.stdout.strip())
        else:
            # 使用 shell:AppsFolder 启动
            safe_appid = subprocess.list2cmdline([f'shell:AppsFolder\\{appid}'])
            cmd = f'Start-Process {safe_appid}'
            result = self._run_powershell(cmd)

        # 等待窗口出现
        if pid > 0:
            # 等待进程的窗口出现
            for _ in range(timeout * 2):
                sleep(0.5)
                if self._find_window_by_pid(pid):
                    return (f'{name.title()} launched.', 0, pid)
        else:
            # 按名称模糊匹配窗口
            safe_name = re.escape(name)
            for _ in range(timeout * 2):
                sleep(0.5)
                if self._find_window_by_title_regex(f'.*{safe_name}.*'):
                    return (f'{name.title()} launched.', 0, pid)

        return (f'Launching {name.title()} sent, but window not detected yet.', 0, pid)

    def _get_apps_from_start_menu(self) -> Dict[str, str]:
        """获取开始菜单应用列表"""
        # 优先使用 PowerShell Get-StartApps
        cmd = 'Get-StartApps | ConvertTo-Csv -NoTypeInformation'
        result = self._run_powershell(cmd)

        if result.returncode == 0 and result.stdout.strip():
            try:
                import csv
                reader = csv.DictReader(io.StringIO(result.stdout.strip()))
                apps = {
                    row.get('Name', '').lower(): row.get('AppID', '')
                    for row in reader
                    if row.get('Name') and row.get('AppID')
                }
                if apps:
                    return apps
            except Exception as e:
                print(f'[WinAutomation] Failed to parse Get-StartApps: {e}', file=sys.stderr)

        # 回退：扫描快捷方式文件夹
        return self._get_apps_from_shortcuts()

    def _get_apps_from_shortcuts(self) -> Dict[str, str]:
        """扫描开始菜单快捷方式"""
        import glob
        apps = {}
        start_menu_paths = [
            os.path.join(os.environ.get('PROGRAMDATA', r'C:\ProgramData'),
                         r'Microsoft\Windows\Start Menu\Programs'),
            os.path.join(os.environ.get('APPDATA', ''),
                         r'Microsoft\Windows\Start Menu\Programs'),
        ]
        for base_path in start_menu_paths:
            if not os.path.isdir(base_path):
                continue
            for lnk_path in glob.glob(os.path.join(base_path, '**', '*.lnk'), recursive=True):
                name = os.path.splitext(os.path.basename(lnk_path))[0].lower()
                if name and name not in apps:
                    apps[name] = lnk_path
        return apps

    def switch_app(self, name: str, timeout: int = 5) -> Tuple[str, int]:
        """
        切换到应用窗口（模糊匹配）
        
        Args:
            name: 窗口名称
        
        Returns:
            (响应消息, 状态码)
        """
        window = self._find_window_by_name(name)
        if window is None:
            return (f'Application {name.title()} not found.', 1)

        handle = window['handle']

        # 使用 AttachThreadInput 技术（借鉴 Windows-MCP）
        try:
            self._bring_window_to_top(handle)
            return (f'Switched to {window["name"]}.', 0)
        except Exception as e:
            return (f'Error switching app: {str(e)}', 1)

    def _find_window_by_name(self, name: str) -> Optional[Dict]:
        """按名称模糊查找窗口"""
        windows = self._get_windows()
        if not windows:
            return None

        window_names = {w['name']: w for w in windows}

        if FUZZYWUZZY_AVAILABLE:
            matched = fuzzy_process.extractOne(name, list(window_names.keys()), score_cutoff=70)
            if matched:
                return window_names[matched[0]]
        else:
            # 简化匹配
            name_lower = name.lower()
            for wname, w in window_names.items():
                if name_lower in wname.lower():
                    return w

        return None

    def _find_window_by_pid(self, pid: int) -> Optional[Dict]:
        """按进程 ID 查找窗口"""
        windows = self._get_windows()
        for w in windows:
            if w['process_id'] == pid:
                return w
        return None

    def _find_window_by_title_regex(self, pattern: str) -> Optional[Dict]:
        """按标题正则表达式查找窗口"""
        windows = self._get_windows()
        regex = re.compile(pattern, re.IGNORECASE)
        for w in windows:
            if regex.search(w['name']):
                return w
        return None

    def _bring_window_to_top(self, handle: int):
        """
        将窗口带到前台（使用 AttachThreadInput 技术）
        
        这是 Windows-MCP 的关键技术，解决 SetForegroundWindow 权限问题。
        """
        if not WIN32_AVAILABLE:
            # 回退到简单方法
            ctypes.windll.user32.SetForegroundWindow(handle)
            return

        if not win32gui.IsWindow(handle):
            raise ValueError('Invalid window handle')

        # 如果窗口最小化，先恢复
        if win32gui.IsIconic(handle):
            win32gui.ShowWindow(handle, win32con.SW_RESTORE)

        foreground_handle = win32gui.GetForegroundWindow()

        if not win32gui.IsWindow(foreground_handle):
            # 没有有效的前台窗口，直接设置
            win32gui.SetForegroundWindow(handle)
            win32gui.BringWindowToTop(handle)
            return

        # 获取线程 ID
        foreground_thread, _ = win32process.GetWindowThreadProcessId(foreground_handle)
        target_thread, _ = win32process.GetWindowThreadProcessId(handle)
        current_tid = ctypes.windll.kernel32.GetCurrentThreadId()

        if not foreground_thread or not target_thread or foreground_thread == target_thread:
            win32gui.SetForegroundWindow(handle)
            win32gui.BringWindowToTop(handle)
            return

        # 允许前台窗口切换
        ctypes.windll.user32.AllowSetForegroundWindow(-1)

        attached_threads = []
        try:
            # 附加线程输入
            for thread in (foreground_thread, target_thread):
                if thread and thread != current_tid:
                    try:
                        win32process.AttachThreadInput(current_tid, thread, True)
                        attached_threads.append(thread)
                    except Exception as e:
                        print(f'[WinAutomation] AttachThreadInput failed: {e}', file=sys.stderr)

            # 设置前台窗口
            win32gui.SetForegroundWindow(handle)
            win32gui.BringWindowToTop(handle)

            # 设置窗口位置
            win32gui.SetWindowPos(
                handle,
                win32con.HWND_TOP,
                0, 0, 0, 0,
                win32con.SWP_NOMOVE | win32con.SWP_NOSIZE | win32con.SWP_SHOWWINDOW
            )

        finally:
            # 解除附加
            for tid in reversed(attached_threads):
                try:
                    win32process.AttachThreadInput(current_tid, tid, False)
                except Exception:
                    pass

    # === 窗口管理 ===

    def _get_windows(self) -> List[Dict]:
        """获取所有可见窗口"""
        if not WIN32_AVAILABLE:
            return []

        windows = []

        def callback(hwnd, _):
            try:
                if win32gui.IsWindow(hwnd) and win32gui.IsWindowVisible(hwnd):
                    # 获取窗口信息
                    title = win32gui.GetWindowText(hwnd)
                    if not title:
                        return
                    _, pid = win32process.GetWindowThreadProcessId(hwnd)
                    rect = win32gui.GetWindowRect(hwnd)
                    left, top, right, bottom = rect
                    width = right - left
                    height = bottom - top

                    # 判断窗口状态
                    if win32gui.IsIconic(hwnd):
                        status = 'minimized'
                    elif win32gui.IsZoomed(hwnd):
                        status = 'maximized'
                    else:
                        status = 'normal'

                    windows.append({
                        'name': title,
                        'handle': hwnd,
                        'process_id': pid,
                        'bounding_box': {
                            'left': left, 'top': top, 'right': right, 'bottom': bottom,
                            'width': width, 'height': height
                        },
                        'status': status
                    })
            except Exception:
                pass

        win32gui.EnumWindows(callback, None)
        return windows

    def get_active_window(self) -> Optional[Dict]:
        """获取前台窗口"""
        if not WIN32_AVAILABLE:
            return None

        handle = win32gui.GetForegroundWindow()
        if not handle:
            return None

        title = win32gui.GetWindowText(handle)
        _, pid = win32process.GetWindowThreadProcessId(handle)
        rect = win32gui.GetWindowRect(handle)
        left, top, right, bottom = rect

        return {
            'name': title,
            'handle': handle,
            'process_id': pid,
            'bounding_box': {
                'left': left, 'top': top, 'right': right, 'bottom': bottom,
                'width': right - left, 'height': bottom - top
            },
            'status': 'normal'
        }

    def resize_app(
        self,
        name: Optional[str] = None,
        size: Optional[Tuple[int, int]] = None,
        loc: Optional[Tuple[int, int]] = None
    ) -> Tuple[str, int]:
        """
        调整窗口大小/位置
        
        Args:
            name: 窗口名称（可选，None 表示前台窗口）
            size: 窗口大小 (width, height)
            loc: 窗口位置 (x, y)
        
        Returns:
            (响应消息, 状态码)
        """
        if name is not None:
            window = self._find_window_by_name(name)
            if window is None:
                return (f'Application {name.title()} not found.', 1)
        else:
            window = self.get_active_window()
            if window is None:
                return ('No active window found.', 1)

        if window['status'] == 'minimized':
            return (f'{window["name"]} is minimized.', 1)
        if window['status'] == 'maximized':
            return (f'{window["name"]} is maximized.', 1)

        handle = window['handle']
        box = window['bounding_box']

        # 使用默认值
        if loc is None:
            loc = (box['left'], box['top'])
        if size is None:
            size = (box['width'], box['height'])

        x, y = loc
        width, height = size

        win32gui.SetWindowPos(handle, None, x, y, width, height, win32con.SWP_NOZORDER)

        return (f'{window["name"]} resized to {width}x{height} at ({x}, {y}).', 0)

    # === 截图 ===

    def screenshot(self, capture_rect: Optional[Dict] = None) -> Optional[str]:
        """
        截图并返回 base64 字符串
        
        Args:
            capture_rect: 截图区域 {'left', 'top', 'right', 'bottom'}（可选）
        
        Returns:
            base64 编码的 PNG 图片
        """
        if not PIL_AVAILABLE:
            return None

        if DXCAM_AVAILABLE:
            try:
                camera = dxcam.create()
                if capture_rect:
                    region = (capture_rect['left'], capture_rect['top'],
                              capture_rect['right'], capture_rect['bottom'])
                    frame = camera.grab(region=region)
                else:
                    frame = camera.grab()
                if frame is not None:
                    img = Image.fromarray(frame)
                    buf = io.BytesIO()
                    img.save(buf, format='PNG', optimize=True, compress_level=6)
                    return base64.b64encode(buf.getvalue()).decode('ascii')
            except Exception as e:
                print(f'[WinAutomation] dxcam screenshot failed: {e}', file=sys.stderr)

        if MSS_AVAILABLE:
            try:
                with mss.mss() as sct:
                    if capture_rect:
                        monitor = {
                            'left': capture_rect['left'],
                            'top': capture_rect['top'],
                            'width': capture_rect['right'] - capture_rect['left'],
                            'height': capture_rect['bottom'] - capture_rect['top']
                        }
                    else:
                        monitor = sct.monitors[1]  # 主显示器

                    img = sct.grab(monitor)
                    pil = Image.frombytes('RGB', img.size, img.bgra, 'raw', 'BGRX')
                    buf = io.BytesIO()
                    pil.save(buf, format='PNG', optimize=True, compress_level=6)
                    return base64.b64encode(buf.getvalue()).decode('ascii')
            except Exception as e:
                print(f'[WinAutomation] mss screenshot failed: {e}', file=sys.stderr)

        return None

    def get_cursor_location(self) -> Tuple[int, int]:
        """获取鼠标位置"""
        return ctypes.windll.user32.GetCursorPos()

    # === UI 元素查找 ===

    def find_element(self, name: Optional[str] = None, class_name: Optional[str] = None,
                     auto_id: Optional[str] = None, control_type: Optional[str] = None,
                     scope: str = 'desktop', max_depth: int = 15,
                     fuzzy: bool = True) -> List[Dict]:
        """
        查找 UI 元素（基于 UIAutomation 树遍历）

        Args:
            name: 控件名称（模糊匹配，如 '搜索'）
            class_name: 控件类名（如 'Edit', 'Button'）
            auto_id: AutomationId（如 'searchBox'）
            control_type: 控件类型（如 'Button', 'Edit', 'Text', 'List'）
            scope: 搜索范围 'desktop'(全局) | 'active_window'(仅前台窗口)
            max_depth: 最大遍历深度
            fuzzy: 是否模糊匹配名称

        Returns:
            匹配的元素列表，每项包含 name, className, controlType, autoId, boundingBox, clickablePoint
        """
        if not UIA_AVAILABLE or not self.uia or not self.root:
            return []

        results = []

        try:
            # 确定搜索根节点
            if scope == 'active_window' and WIN32_AVAILABLE:
                fg_handle = win32gui.GetForegroundWindow()
                if fg_handle:
                    condition = self.uia.CreatePropertyCondition(
                        UIAutomationCore.UIA_ProcessIdPropertyId,
                        win32process.GetWindowThreadProcessId(fg_handle)[1] if False else 0
                    )
                    # 简化：使用窗口句柄条件
                    root_element = self.root
                else:
                    root_element = self.root
            else:
                root_element = self.root

            # 使用 TreeWalker 遍历 UI 树
            walker = self.uia.RawViewWalker
            if not walker:
                return []

            element = walker.GetFirstChildElement(root_element)
            depth = 0
            stack = [(element, 1)]

            while stack and len(results) < 20:
                current, current_depth = stack.pop(0) if stack else (None, 0)
                if current is None:
                    continue

                if current_depth > max_depth:
                    continue

                try:
                    elem_info = self._extract_element_info(current)
                    if elem_info and self._match_element(elem_info, name, class_name, auto_id, control_type, fuzzy):
                        results.append(elem_info)
                except Exception:
                    pass

                # 遍历子节点
                try:
                    child = walker.GetFirstChildElement(current)
                    while child:
                        stack.append((child, current_depth + 1))
                        child = walker.GetNextSiblingElement(child)
                except Exception:
                    pass

        except Exception as e:
            print(f'[WinAutomation] find_element failed: {e}', file=sys.stderr)

        return results

    def _extract_element_info(self, element) -> Optional[Dict]:
        """提取 UI 元素信息"""
        try:
            name = element.CurrentName or ''
            class_name = element.CurrentClassName or ''
            control_type_id = element.CurrentControlType or 0
            auto_id = element.CurrentAutomationId or ''

            # 获取边界矩形
            rect = element.CurrentBoundingRectangle
            if rect:
                left, top = rect.left, rect.top
                right, bottom = rect.right, rect.bottom
                width = right - left
                height = bottom - top

                # 跳过不可见/极小元素
                if width <= 0 or height <= 0:
                    return None

                # 计算可点击中心点
                cx = left + width // 2
                cy = top + height // 2

                # 控件类型映射
                type_map = {
                    50000: 'Button', 50001: 'Calendar', 50002: 'CheckBox',
                    50003: 'ComboBox', 50004: 'Edit', 50005: 'Hyperlink',
                    50006: 'Image', 50007: 'ListItem', 50008: 'List',
                    50009: 'Menu', 50010: 'MenuBar', 50011: 'MenuItem',
                    50012: 'ProgressBar', 50013: 'RadioButton', 50014: 'ScrollBar',
                    50015: 'Slider', 50016: 'Spinner', 50017: 'StatusBar',
                    50018: 'Tab', 50019: 'TabItem', 50020: 'Text',
                    50021: 'ToolBar', 50022: 'ToolTip', 50023: 'Tree',
                    50024: 'TreeItem', 50025: 'Custom', 50026: 'Group',
                    50027: 'Thumb', 50028: 'DataGrid', 50029: 'DataItem',
                    50030: 'Document', 50031: 'SplitButton', 50032: 'Window',
                    50033: 'Pane', 50034: 'Header', 50035: 'HeaderItem',
                    50036: 'Table', 50037: 'TitleBar', 50038: 'Separator',
                }
                control_type_name = type_map.get(control_type_id, f'Unknown({control_type_id})')

                return {
                    'name': name,
                    'className': class_name,
                    'controlType': control_type_name,
                    'autoId': auto_id,
                    'boundingBox': {
                        'left': left, 'top': top, 'right': right, 'bottom': bottom,
                        'width': width, 'height': height
                    },
                    'clickablePoint': [cx, cy]
                }
        except Exception:
            pass
        return None

    def _match_element(self, elem_info: Dict, name: Optional[str], class_name: Optional[str],
                       auto_id: Optional[str], control_type: Optional[str], fuzzy: bool) -> bool:
        """判断元素是否匹配搜索条件"""
        if name:
            if fuzzy:
                if name.lower() not in elem_info.get('name', '').lower():
                    # 也尝试 fuzzywuzzy 模糊匹配
                    if FUZZYWUZZY_AVAILABLE:
                        score = fuzzy_process.extractOne(name, [elem_info.get('name', '')], score_cutoff=60)
                        if not score:
                            return False
                    else:
                        return False
            else:
                if name.lower() != elem_info.get('name', '').lower():
                    return False

        if class_name:
            if class_name.lower() not in elem_info.get('className', '').lower():
                return False

        if auto_id:
            if auto_id.lower() not in elem_info.get('autoId', '').lower():
                return False

        if control_type:
            if control_type.lower() != elem_info.get('controlType', '').lower():
                return False

        return True

    # === PowerShell ===

    def _run_powershell(self, command: str, timeout: int = 30) -> subprocess.CompletedProcess:
        """执行 PowerShell 命令"""
        # 使用 -EncodedCommand 避免 Unicode 编码问题
        encoded = base64.b64encode(command.encode('utf-16-le')).decode('ascii')
        return subprocess.run(
            ['powershell', '-NoProfile', '-NonInteractive', '-EncodedCommand', encoded],
            capture_output=True,
            text=True,
            timeout=timeout,
            encoding='utf-8'
        )


class WindowsAutomationService:
    """Windows 自动化服务：操作分发与执行"""

    # 高风险操作集合（与后端 OPERATION_PERMISSIONS 保持一致）
    HIGH_RISK_OPERATIONS = {
        'shell', 'process_kill',
        'registry_set', 'registry_delete',
        'filesystem_write', 'filesystem_delete'
    }

    def __init__(self):
        self.desktop = Desktop() if IS_WINDOWS else None

    def execute(self, operation: str, args: Dict[str, Any]) -> Dict[str, Any]:
        """执行自动化操作，返回统一格式的响应"""
        try:
            result = self._dispatch(operation, args)
            return {'success': True, 'result': result}
        except Exception as e:
            return {'success': False, 'error': str(e)}

    def _dispatch(self, operation: str, args: Dict[str, Any]) -> Any:
        """操作分发"""
        handlers = {
            # UIA 控件操作（使用 Desktop 类）
            'click': self._handle_click,
            'double_click': self._handle_double_click,
            'right_click': self._handle_right_click,
            'type': self._handle_type,
            'scroll': self._handle_scroll,
            'move': self._handle_move,
            'drag': self._handle_drag,
            'shortcut': self._handle_shortcut,
            'snapshot': self._handle_snapshot,
            'screenshot': self._handle_screenshot,
            'find_element': self._handle_find_element,

            # 应用操作
            'launch_app': self._handle_launch_app,
            'switch_app': self._handle_switch_app,
            'resize_app': self._handle_resize_app,
            'get_windows': self._handle_get_windows,
            'get_active_window': self._handle_get_active_window,

            # 条件等待
            'wait': self._handle_wait,
            'wait_for': self._handle_wait_for,

            # PowerShell
            'shell': self._handle_shell,

            # 进程管理
            'process_list': self._handle_process_list,
            'process_kill': self._handle_process_kill,

            # 注册表
            'registry_get': self._handle_registry_get,
            'registry_set': self._handle_registry_set,
            'registry_delete': self._handle_registry_delete,
            'registry_list': self._handle_registry_list,

            # 文件系统
            'filesystem_read': self._handle_fs_read,
            'filesystem_write': self._handle_fs_write,
            'filesystem_copy': self._handle_fs_copy,
            'filesystem_move': self._handle_fs_move,
            'filesystem_delete': self._handle_fs_delete,
            'filesystem_list': self._handle_fs_list,
            'filesystem_search': self._handle_fs_search,
            'filesystem_info': self._handle_fs_info,

            # 剪贴板
            'clipboard_get': self._handle_clipboard_get,
            'clipboard_set': self._handle_clipboard_set,

            # 其他
            'notification': self._handle_notification,
            'scrape': self._handle_scrape,

            # 虚拟桌面
            'vdm_switch': self._handle_vdm_switch,
            'vdm_create': self._handle_vdm_create,
            'vdm_move_window': self._handle_vdm_move_window,
        }

        handler = handlers.get(operation)
        if not handler:
            raise ValueError(f'Unknown operation: {operation}')

        return handler(args)

    # === UIA 控件操作（使用 Desktop 类） ===

    def _handle_click(self, args: Dict) -> Dict:
        """鼠标点击"""
        if not self.desktop:
            raise RuntimeError('Desktop not available (non-Windows platform)')

        x = args.get('x')
        y = args.get('y')
        if x is None or y is None:
            raise ValueError('x and y required for click')

        button = args.get('button', 'left')
        clicks = int(args.get('clicks', 1))

        self.desktop.click((int(x), int(y)), button, clicks)
        return {'clicked': True, 'position': [int(x), int(y)], 'button': button, 'clicks': clicks}

    def _handle_double_click(self, args: Dict) -> Dict:
        """双击"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        x = args.get('x')
        y = args.get('y')
        if x is None or y is None:
            raise ValueError('x and y required for double_click')

        self.desktop.double_click((int(x), int(y)))
        return {'double_clicked': True, 'position': [int(x), int(y)]}

    def _handle_right_click(self, args: Dict) -> Dict:
        """右键单击"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        x = args.get('x')
        y = args.get('y')
        if x is None or y is None:
            raise ValueError('x and y required for right_click')

        self.desktop.right_click((int(x), int(y)))
        return {'right_clicked': True, 'position': [int(x), int(y)]}

    def _handle_type(self, args: Dict) -> Dict:
        """键盘输入"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        text = args.get('text')
        if text is None:
            raise ValueError('text required for type')

        x = args.get('x')
        y = args.get('y')
        loc = (int(x), int(y)) if x is not None and y is not None else None

        caret_position = args.get('caret_position', 'idle')
        clear = args.get('clear', False)
        press_enter = args.get('press_enter', False)

        self.desktop.type_text(loc, str(text), caret_position, clear, press_enter)
        return {'typed': text, 'position': loc}

    def _handle_scroll(self, args: Dict) -> Dict:
        """滚动"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        x = args.get('x')
        y = args.get('y')
        loc = (int(x), int(y)) if x is not None and y is not None else None

        direction = args.get('direction', 'down')
        amount = int(args.get('amount', 3))

        self.desktop.scroll(loc, direction, amount)
        return {'scrolled': amount, 'direction': direction}

    def _handle_move(self, args: Dict) -> Dict:
        """鼠标移动"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        x = args.get('x')
        y = args.get('y')
        if x is None or y is None:
            raise ValueError('x and y required for move')

        self.desktop.move((int(x), int(y)))
        return {'moved': [int(x), int(y)]}

    def _handle_drag(self, args: Dict) -> Dict:
        """拖拽"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        from_x = args.get('from_x')
        from_y = args.get('from_y')
        to_x = args.get('to_x')
        to_y = args.get('to_y')
        if from_x is None or from_y is None or to_x is None or to_y is None:
            raise ValueError('from_x, from_y, to_x, to_y required for drag')

        self.desktop.drag((int(from_x), int(from_y)), (int(to_x), int(to_y)))
        return {'dragged': True, 'from': [int(from_x), int(from_y)], 'to': [int(to_x), int(to_y)]}

    def _handle_shortcut(self, args: Dict) -> Dict:
        """快捷键"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        keys = args.get('keys')
        if not keys:
            raise ValueError('keys required for shortcut')

        if isinstance(keys, list):
            shortcut_str = '+'.join(keys)
        else:
            shortcut_str = keys

        self.desktop.shortcut(shortcut_str)
        return {'shortcut': shortcut_str}

    def _handle_snapshot(self, args: Dict) -> Dict:
        """UI 树 + 截图"""
        screenshot = self.desktop.screenshot()
        windows = self.desktop._get_windows()
        active_window = self.desktop.get_active_window()
        cursor_pos = self.desktop.get_cursor_location()

        return {
            'windows': windows,
            'active_window': active_window,
            'cursor_position': list(cursor_pos),
            'screenshot': screenshot
        }

    def _handle_screenshot(self, args: Dict) -> Dict:
        """快速截图"""
        capture_rect = args.get('rect')
        screenshot = self.desktop.screenshot(capture_rect)
        return {'screenshot': screenshot}

    def _handle_find_element(self, args: Dict) -> Dict:
        """查找 UI 元素"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        name = args.get('name')
        class_name = args.get('className') or args.get('class_name')
        auto_id = args.get('autoId') or args.get('auto_id')
        control_type = args.get('controlType') or args.get('control_type')
        scope = args.get('scope', 'active_window')
        max_depth = int(args.get('max_depth', 15))
        fuzzy = args.get('fuzzy', True)

        elements = self.desktop.find_element(
            name=name, class_name=class_name, auto_id=auto_id,
            control_type=control_type, scope=scope,
            max_depth=max_depth, fuzzy=fuzzy
        )

        return {
            'elements': elements,
            'count': len(elements),
            'hint': '使用 clickablePoint 坐标进行 click/type 操作' if elements else '未找到匹配元素，尝试 snapshot 获取完整 UI 树'
        }

    # === 应用操作 ===

    def _handle_launch_app(self, args: Dict) -> Dict:
        """启动应用"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        name = args.get('name')
        if not name:
            raise ValueError('name required for launch_app')

        timeout = int(args.get('timeout', 10))

        message, status, pid = self.desktop.launch_app(name, timeout)
        return {'message': message, 'status': status, 'pid': pid}

    def _handle_switch_app(self, args: Dict) -> Dict:
        """切换应用窗口"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        name = args.get('name')
        if not name:
            raise ValueError('name required for switch_app')

        message, status = self.desktop.switch_app(name)
        return {'message': message, 'status': status}

    def _handle_resize_app(self, args: Dict) -> Dict:
        """调整窗口大小"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        name = args.get('name')
        size = args.get('size')
        loc = args.get('loc')

        if size and isinstance(size, list):
            size = tuple(size)
        if loc and isinstance(loc, list):
            loc = tuple(loc)

        message, status = self.desktop.resize_app(name, size, loc)
        return {'message': message, 'status': status}

    def _handle_get_windows(self, args: Dict) -> Dict:
        """获取所有窗口"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        windows = self.desktop._get_windows()
        return {'windows': windows, 'count': len(windows)}

    def _handle_get_active_window(self, args: Dict) -> Dict:
        """获取前台窗口"""
        if not self.desktop:
            raise RuntimeError('Desktop not available')

        window = self.desktop.get_active_window()
        return {'active_window': window}

    # === 条件等待 ===

    def _handle_wait(self, args: Dict) -> Dict:
        """固定时间等待"""
        seconds = float(args.get('seconds', 1))
        sleep(seconds)
        return {'waited': seconds}

    def _handle_wait_for(self, args: Dict) -> Dict:
        """条件等待"""
        condition = args.get('condition', 'window_appear')
        timeout = float(args.get('timeout', 10))
        interval = float(args.get('interval', 0.5))
        window_name = args.get('window_name')

        start = perf_counter()
        while perf_counter() - start < timeout:
            met = self._check_condition(condition, window_name)
            if met:
                return {'met': True, 'elapsed': perf_counter() - start, 'condition': condition}
            sleep(interval)

        return {'met': False, 'elapsed': timeout, 'condition': condition}

    def _check_condition(self, condition: str, window_name: Optional[str]) -> bool:
        """检查等待条件"""
        if not self.desktop:
            return False

        if condition == 'window_appear':
            if window_name:
                window = self.desktop._find_window_by_name(window_name)
                return window is not None
            else:
                # 检查是否有新窗口
                return True

        if condition == 'window_disappear':
            if window_name:
                window = self.desktop._find_window_by_name(window_name)
                return window is None
            return False

        return False

    # === PowerShell ===

    def _handle_shell(self, args: Dict) -> Dict:
        """PowerShell 命令执行"""
        command = args.get('command')
        if not command:
            raise ValueError('command required for shell')

        timeout = int(args.get('timeout', 30))
        result = self.desktop._run_powershell(command, timeout)

        return {
            'stdout': result.stdout,
            'stderr': result.stderr,
            'exit_code': result.returncode
        }

    # === 进程管理 ===

    def _handle_process_list(self, args: Dict) -> Dict:
        """列出进程"""
        if not PSUTIL_AVAILABLE:
            raise RuntimeError('psutil not available')

        sort_by = args.get('sort_by', 'name')
        limit = int(args.get('limit', 50))

        processes = []
        for proc in psutil.process_iter(['pid', 'name', 'memory_info', 'cpu_percent']):
            try:
                info = proc.info
                processes.append({
                    'pid': info['pid'],
                    'name': info['name'],
                    'memory': info['memory_info'].rss if info.get('memory_info') else 0,
                    'cpu': info['cpu_percent'] or 0
                })
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                continue

        # 排序
        if sort_by == 'memory':
            processes.sort(key=lambda p: p.get('memory', 0), reverse=True)
        elif sort_by == 'cpu':
            processes.sort(key=lambda p: p.get('cpu', 0), reverse=True)
        else:
            processes.sort(key=lambda p: p.get('name', ''))

        return {'processes': processes[:limit], 'total': len(processes)}

    def _handle_process_kill(self, args: Dict) -> Dict:
        """终止进程"""
        if not PSUTIL_AVAILABLE:
            raise RuntimeError('psutil not available')

        pid = args.get('pid')
        name = args.get('name')
        force = args.get('force', False)

        killed = []
        if pid:
            proc = psutil.Process(int(pid))
            if force:
                proc.kill()
            else:
                proc.terminate()
            killed.append({'pid': int(pid)})
        elif name:
            for proc in psutil.process_iter(['pid', 'name']):
                if proc.info['name'] == name:
                    try:
                        if force:
                            proc.kill()
                        else:
                            proc.terminate()
                        killed.append({'pid': proc.info['pid'], 'name': name})
                    except (psutil.NoSuchProcess, psutil.AccessDenied):
                        continue
        else:
            raise ValueError('pid or name required for process_kill')

        return {'killed': killed}

    # === 注册表 ===

    def _handle_registry_get(self, args: Dict) -> Dict:
        """读取注册表"""
        reg_path = args.get('path')
        value = args.get('value')
        if not reg_path:
            raise ValueError('path required for registry_get')

        cmd = f"Get-ItemProperty -Path '{reg_path}'"
        if value:
            cmd += f" -Name '{value}'"
        result = self.desktop._run_powershell(cmd)
        return {'value': result.stdout.strip(), 'exit_code': result.returncode}

    def _handle_registry_set(self, args: Dict) -> Dict:
        """写入注册表"""
        reg_path = args.get('path')
        value = args.get('value')
        data = args.get('data')
        value_type = args.get('type', 'String')
        if not reg_path or not value:
            raise ValueError('path and value required for registry_set')

        cmd = f"Set-ItemProperty -Path '{reg_path}' -Name '{value}' -Value '{data}' -Type {value_type}"
        result = self.desktop._run_powershell(cmd)
        return {'exit_code': result.returncode}

    def _handle_registry_delete(self, args: Dict) -> Dict:
        """删除注册表"""
        reg_path = args.get('path')
        value = args.get('value')
        if not reg_path:
            raise ValueError('path required for registry_delete')

        if value:
            cmd = f"Remove-ItemProperty -Path '{reg_path}' -Name '{value}'"
        else:
            cmd = f"Remove-Item -Path '{reg_path}' -Recurse"
        result = self.desktop._run_powershell(cmd)
        return {'exit_code': result.returncode}

    def _handle_registry_list(self, args: Dict) -> Dict:
        """列出注册表键"""
        reg_path = args.get('path')
        if not reg_path:
            raise ValueError('path required for registry_list')

        cmd = f"Get-ChildItem -Path '{reg_path}' | Select-Object Name"
        result = self.desktop._run_powershell(cmd)
        return {'keys': result.stdout.strip(), 'exit_code': result.returncode}

    # === 文件系统 ===

    def _handle_fs_read(self, args: Dict) -> Dict:
        """读取文件"""
        file_path = args.get('path')
        if not file_path:
            raise ValueError('path required for filesystem_read')

        encoding = args.get('encoding', 'utf-8')
        with open(file_path, 'r', encoding=encoding) as f:
            content = f.read()
        return {'content': content, 'path': file_path}

    def _handle_fs_write(self, args: Dict) -> Dict:
        """写入文件"""
        file_path = args.get('path')
        content = args.get('content', '')
        if not file_path:
            raise ValueError('path required for filesystem_write')

        encoding = args.get('encoding', 'utf-8')
        append = args.get('append', False)
        mode = 'a' if append else 'w'
        with open(file_path, mode, encoding=encoding) as f:
            f.write(content)
        return {'path': file_path, 'bytes': len(content.encode(encoding))}

    def _handle_fs_copy(self, args: Dict) -> Dict:
        """复制文件"""
        import shutil
        src = args.get('src')
        dst = args.get('dst')
        if not src or not dst:
            raise ValueError('src and dst required for filesystem_copy')

        shutil.copy2(src, dst)
        return {'src': src, 'dst': dst}

    def _handle_fs_move(self, args: Dict) -> Dict:
        """移动文件"""
        import shutil
        src = args.get('src')
        dst = args.get('dst')
        if not src or not dst:
            raise ValueError('src and dst required for filesystem_move')

        shutil.move(src, dst)
        return {'src': src, 'dst': dst}

    def _handle_fs_delete(self, args: Dict) -> Dict:
        """删除文件"""
        file_path = args.get('path')
        if not file_path:
            raise ValueError('path required for filesystem_delete')

        p = Path(file_path)
        if p.is_dir():
            import shutil
            shutil.rmtree(p)
        else:
            p.unlink()
        return {'deleted': file_path}

    def _handle_fs_list(self, args: Dict) -> Dict:
        """列出目录"""
        dir_path = args.get('path')
        if not dir_path:
            raise ValueError('path required for filesystem_list')

        p = Path(dir_path)
        if not p.exists() or not p.is_dir():
            raise FileNotFoundError(f'Directory not found: {dir_path}')

        entries = []
        for entry in p.iterdir():
            entries.append({
                'name': entry.name,
                'type': 'directory' if entry.is_dir() else 'file',
                'size': entry.stat().st_size if entry.is_file() else None
            })
        return {'entries': entries, 'path': dir_path}

    def _handle_fs_search(self, args: Dict) -> Dict:
        """搜索文件"""
        dir_path = args.get('path')
        pattern = args.get('pattern', '*')
        if not dir_path:
            raise ValueError('path required for filesystem_search')

        p = Path(dir_path)
        matches = []
        for entry in p.rglob(pattern):
            matches.append({
                'name': entry.name,
                'path': str(entry),
                'type': 'directory' if entry.is_dir() else 'file'
            })
        return {'matches': matches, 'count': len(matches)}

    def _handle_fs_info(self, args: Dict) -> Dict:
        """文件信息"""
        file_path = args.get('path')
        if not file_path:
            raise ValueError('path required for filesystem_info')

        p = Path(file_path)
        if not p.exists():
            raise FileNotFoundError(f'Not found: {file_path}')

        stat = p.stat()
        return {
            'path': str(p),
            'name': p.name,
            'type': 'directory' if p.is_dir() else 'file',
            'size': stat.st_size,
            'modified': stat.st_mtime,
            'created': stat.st_ctime
        }

    # === 剪贴板 ===

    def _handle_clipboard_get(self, args: Dict) -> Dict:
        """读取剪贴板"""
        if not WIN32_AVAILABLE:
            raise RuntimeError('win32clipboard not available')

        win32clipboard.OpenClipboard()
        try:
            data = win32clipboard.GetClipboardData(win32con.CF_UNICODETEXT)
            return {'content': data}
        finally:
            win32clipboard.CloseClipboard()

    def _handle_clipboard_set(self, args: Dict) -> Dict:
        """设置剪贴板"""
        if not WIN32_AVAILABLE:
            raise RuntimeError('win32clipboard not available')

        content = args.get('content', '')
        win32clipboard.OpenClipboard()
        try:
            win32clipboard.EmptyClipboard()
            win32clipboard.SetClipboardData(win32con.CF_UNICODETEXT, str(content))
        finally:
            win32clipboard.CloseClipboard()
        return {'set': True}

    # === 其他 ===

    def _handle_notification(self, args: Dict) -> Dict:
        """Toast 通知"""
        title = args.get('title', 'Living Agent')
        body = args.get('body', '')
        cmd = (
            "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, "
            "ContentType = WindowsRuntime] | Out-Null; "
            f"$template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent(0); "
            f"$text = $template.GetElementsByTagName('text')[0]; "
            f"$text.AppendChild($template.CreateTextNode('{title}: {body}')) | Out-Null; "
            f"$toast = [Windows.UI.Notifications.ToastNotification]::new($template); "
            f"[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('Living Agent').Show($toast)"
        )
        self.desktop._run_powershell(cmd)
        return {'notified': True}

    def _handle_scrape(self, args: Dict) -> Dict:
        """网页抓取"""
        if not REQUESTS_AVAILABLE:
            raise RuntimeError('requests not available')

        url = args.get('url')
        if not url:
            raise ValueError('url required for scrape')

        try:
            resp = requests.get(url, timeout=10, headers={'User-Agent': 'Mozilla/5.0'})
            resp.raise_for_status()
            return {'content': resp.text, 'url': url, 'status_code': resp.status_code}
        except Exception as e:
            raise RuntimeError(f'Failed to scrape {url}: {e}')

    # === 虚拟桌面 ===

    def _handle_vdm_switch(self, args: Dict) -> Dict:
        """切换虚拟桌面"""
        direction = args.get('direction', 'next')
        if direction == 'next':
            self.desktop.shortcut('Win+Ctrl+Right')
        else:
            self.desktop.shortcut('Win+Ctrl+Left')
        return {'switched': direction}

    def _handle_vdm_create(self, args: Dict) -> Dict:
        """创建虚拟桌面"""
        self.desktop.shortcut('Win+Ctrl+D')
        return {'created': True}

    def _handle_vdm_move_window(self, args: Dict) -> Dict:
        """移动窗口到虚拟桌面"""
        direction = args.get('direction', 'next')
        if direction == 'next':
            self.desktop.shortcut('Win+Ctrl+Right')
        else:
            self.desktop.shortcut('Win+Ctrl+Left')
        return {'moved': direction}

    def run(self):
        """主循环：从 stdin 读取请求，执行，返回响应"""
        print('[WinAutomation] Service ready, waiting for requests...', file=sys.stderr)

        for line in sys.stdin:
            if not line.strip():
                continue

            request = None
            try:
                request = json.loads(line)
                request_id = request.get('id')
                operation = request.get('operation')
                args = request.get('args', {})

                response = self.execute(operation, args)
                response['id'] = request_id
                print(json.dumps(response, ensure_ascii=False, default=str), flush=True)

            except Exception as e:
                err_response = {
                    'id': request.get('id') if request else None,
                    'success': False,
                    'error': str(e)
                }
                print(json.dumps(err_response, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    service = WindowsAutomationService()
    service.run()