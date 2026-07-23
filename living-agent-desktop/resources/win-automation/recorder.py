"""
Windows 操作录制器

独立于 service.py 的录制模块，仅在录制期间临时运行。
通信协议：JSON 行协议（stdin 读命令，stdout 写事件）

依赖：pynput（全局键鼠钩子）+ comtypes（ElementFromPoint）

启动方式：
  python recorder.py

输入命令（stdin，每行一个 JSON）：
  {"cmd":"start","config":{"target_app":"微信","note_mode":"key"}}
  {"cmd":"stop"}
  {"cmd":"pause"}
  {"cmd":"resume"}
  {"cmd":"note","index":3,"text":"搜索联系人"}

输出事件（stdout，每行一个 JSON）：
  {"type":"status","recording":true,"paused":false,"step_count":0}
  {"type":"step","index":1,"operation":"click","args":{},"target":{},"timestamp":"..."}
  {"type":"note_request","index":2,"operation":"type","suggestion":"输入文本"}
  {"type":"result","steps":[...],"meta":{...}}
  {"type":"error","message":"..."}
"""

import sys
import json
import time
import threading
from datetime import datetime
from typing import Optional

# ============================================================
# 依赖检测
# ============================================================
PYNPUT_AVAILABLE = False
try:
    from pynput import mouse, keyboard
    PYNPUT_AVAILABLE = True
except ImportError:
    pass

COMTYPES_AVAILABLE = False
try:
    import comtypes
    from comtypes import GUID
    from comtypes.client import GetModule, CreateObject
    COMTYPES_AVAILABLE = True
except ImportError:
    pass

PSUTIL_AVAILABLE = False
try:
    import psutil
    PSUTIL_AVAILABLE = True
except ImportError:
    pass


# ============================================================
# UIAutomation 初始化（与 service.py 共享模式）
# ============================================================
uia = None
IUIAutomation = None
tagPOINT = None
UIA_ControlTypeMap = {}

def _init_uia():
    """初始化 UIAutomation 接口（ElementFromPoint 所需）"""
    global uia, IUIAutomation, tagPOINT, UIA_ControlTypeMap
    if not COMTYPES_AVAILABLE:
        return False
    try:
        comtypes.client.GetModule("UIAutomationCore.dll")
        from comtypes.gen.UIAutomationClient import (  # type: ignore
            CUIAutomation, IUIAutomation, tagPOINT as _tagPOINT
        )
        uia = CreateObject(CUIAutomation._reg_clsid_, interface=IUIAutomation)
        tagPOINT = _tagPOINT
        UIA_ControlTypeMap = {
            50000: "Button", 50001: "Calendar", 50002: "CheckBox",
            50003: "ComboBox", 50004: "Edit", 50005: "Hyperlink",
            50006: "Image", 50007: "ListItem", 50008: "List",
            50009: "Menu", 50010: "MenuBar", 50011: "MenuItem",
            50012: "ProgressBar", 50013: "RadioButton", 50014: "ScrollBar",
            50015: "Slider", 50016: "Spinner", 50017: "StatusBar",
            50018: "Tab", 50019: "TabItem", 50020: "Text",
            50021: "ToolBar", 50022: "ToolTip", 50023: "Tree",
            50024: "TreeItem", 50025: "Custom", 50026: "Group",
            50027: "Thumb", 50028: "DataGrid", 50029: "DataItem",
            50030: "Document", 50031: "SplitButton", 50032: "Window",
            50033: "Pane", 50034: "Header", 50035: "HeaderItem",
            50036: "Table", 50037: "TitleBar", 50038: "Separator",
            50039: "SemanticZoom", 50040: "AppBar",
        }
        return True
    except Exception as e:
        print(json.dumps({"type": "error", "message": f"UIAutomation init failed: {e}"}), flush=True)
        return False


def resolve_element_at_point(x: int, y: int) -> Optional[dict]:
    """通过 IUIAutomation.ElementFromPoint 解析坐标对应的 UI 元素"""
    if not uia or not tagPOINT:
        return None
    try:
        pt = tagPOINT(x=x, y=y)
        element = uia.ElementFromPoint(pt)
        if not element:
            return None

        name = element.CurrentName or ""
        class_name = element.CurrentClassName or ""
        auto_id = element.CurrentAutomationId or ""
        control_type_id = element.CurrentControlType
        process_id = element.CurrentProcessId

        process_name = ""
        if process_id and PSUTIL_AVAILABLE:
            try:
                process_name = psutil.Process(process_id).name()
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                pass

        control_type = UIA_ControlTypeMap.get(control_type_id, f"Unknown({control_type_id})")

        # 获取边界矩形
        bounding_rect = None
        try:
            rect = element.CurrentBoundingRectangle
            bounding_rect = {
                "left": rect.left, "top": rect.top,
                "right": rect.right, "bottom": rect.bottom
            }
        except Exception:
            pass

        return {
            "name": name,
            "className": class_name,
            "autoId": auto_id,
            "controlType": control_type,
            "processName": process_name,
            "boundingRect": bounding_rect
        }
    except Exception:
        return None


# ============================================================
# 备注预填推测
# ============================================================
def suggest_note(operation: str, args: dict, target: Optional[dict]) -> str:
    """根据操作类型自动推测备注文本"""
    if operation == "launch_app":
        return f"打开{args.get('name', '应用')}"
    elif operation == "switch_app":
        return f"切换到{args.get('name', '应用')}"
    elif operation == "click":
        if target:
            name = target.get("name", "")
            ctrl = target.get("controlType", "")
            if name:
                return f"点击{name}"
            elif ctrl == "Edit":
                return "定位输入框"
            elif ctrl == "Button":
                return "点击按钮"
        return "点击目标"
    elif operation == "double_click":
        if target and target.get("name"):
            return f"双击{target['name']}"
        return "双击目标"
    elif operation == "right_click":
        if target and target.get("name"):
            return f"右键{target['name']}"
        return "右键目标"
    elif operation == "type":
        text = args.get("text", "")
        if text:
            display = text[:20] + "..." if len(text) > 20 else text
            return f"输入{display}"
        return "输入文本"
    elif operation == "shortcut":
        keys = args.get("keys", "")
        return f"执行{keys}"
    elif operation == "scroll":
        direction = args.get("direction", "down")
        return f"向{direction}滚动"
    elif operation == "drag":
        return "拖拽目标"
    return ""


# ============================================================
# 操作录制器
# ============================================================
class OperationRecorder:
    """Windows 桌面操作录制器"""

    def __init__(self):
        self.config: dict = {}
        self.target_app: str = ""
        self.note_mode: str = "key"  # all / key / summary
        self.running: bool = False
        self.paused: bool = False
        self.steps: list = []
        self.key_buffer: list = []       # 按键缓冲
        self.last_key_time: float = 0
        self.last_click_time: float = 0
        self.last_click_pos: tuple = (0, 0)
        self.pending_note_index: Optional[int] = None
        self._mouse_listener = None
        self._keyboard_listener = None
        self._key_buffer_timer: Optional[threading.Timer] = None
        self._lock = threading.Lock()
        self._ctrl_pressed = False
        self._alt_pressed = False
        self._shift_pressed = False
        self._win_pressed = False
        self._modifier_buffer: list = []  # 修饰键组合缓冲

    def handle_command(self, cmd: dict):
        """处理来自 Electron 的命令"""
        action = cmd.get("cmd", "")
        if action == "start":
            self.start_recording(cmd.get("config", {}))
        elif action == "stop":
            self.stop_recording()
        elif action == "pause":
            self.pause_recording()
        elif action == "resume":
            self.resume_recording()
        elif action == "note":
            self.set_note(cmd.get("index", -1), cmd.get("text", ""))
        elif action == "skip_note":
            self.skip_note()

    def start_recording(self, config: dict):
        """启动录制"""
        if self.running:
            self._emit_error("Already recording")
            return

        if not PYNPUT_AVAILABLE:
            self._emit_error("pynput not installed. Run: pip install pynput")
            return

        self.config = config
        self.target_app = config.get("target_app", "")
        self.note_mode = config.get("note_mode", "key")
        self.steps = []
        self.key_buffer = []
        self.running = True
        self.paused = False

        # 启动键鼠监听
        try:
            self._mouse_listener = mouse.Listener(
                on_click=self._on_mouse_click,
                on_scroll=self._on_mouse_scroll
            )
            self._keyboard_listener = keyboard.Listener(
                on_press=self._on_key_press,
                on_release=self._on_key_release
            )
            self._mouse_listener.start()
            self._keyboard_listener.start()
        except Exception as e:
            self._emit_error(f"Failed to start listeners: {e}")
            self.running = False
            return

        self._emit_status()

    def stop_recording(self):
        """停止录制并输出结果"""
        if not self.running:
            return

        self.running = False

        # 刷出按键缓冲
        self._flush_key_buffer()

        # 停止监听
        if self._mouse_listener:
            self._mouse_listener.stop()
            self._mouse_listener = None
        if self._keyboard_listener:
            self._keyboard_listener.stop()
            self._keyboard_listener = None
        if self._key_buffer_timer:
            self._key_buffer_timer.cancel()
            self._key_buffer_timer = None

        # 输出最终结果
        result = {
            "type": "result",
            "steps": self.steps,
            "meta": {
                "app": self.target_app,
                "recorded_at": datetime.now().isoformat(),
                "duration_seconds": 0,
                "step_count": len(self.steps),
                "note_mode": self.note_mode
            }
        }
        if self.steps:
            first_ts = self.steps[0].get("timestamp", "")
            last_ts = self.steps[-1].get("timestamp", "")
            try:
                t1 = datetime.fromisoformat(first_ts)
                t2 = datetime.fromisoformat(last_ts)
                result["meta"]["duration_seconds"] = int((t2 - t1).total_seconds())
            except Exception:
                pass

        self._emit(result)
        self._emit_status()

    def pause_recording(self):
        """暂停录制"""
        if self.running and not self.paused:
            self.paused = True
            self._flush_key_buffer()
            self._emit_status()

    def resume_recording(self):
        """继续录制"""
        if self.running and self.paused:
            self.paused = False
            self._emit_status()

    def set_note(self, index: int, text: str):
        """为指定步骤设置备注"""
        if 0 <= index < len(self.steps):
            self.steps[index]["note"] = text

    def skip_note(self):
        """跳过当前备注请求"""
        self.pending_note_index = None

    # ============================================================
    # 鼠标事件处理
    # ============================================================

    def _on_mouse_click(self, x, y, button, pressed):
        """鼠标点击回调"""
        if not self.running or self.paused or not pressed:
            return

        # 只处理左键/右键
        if button not in (mouse.Button.left, mouse.Button.right):
            return

        # 刷出按键缓冲
        self._flush_key_buffer()

        now = time.time()

        # 双击检测：500ms内同位置两次左键单击
        if (button == mouse.Button.left
                and now - self.last_click_time < 0.5
                and abs(x - self.last_click_pos[0]) < 5
                and abs(y - self.last_click_pos[1]) < 5):
            # 修改上一步为双击
            with self._lock:
                if self.steps and self.steps[-1].get("operation") == "click":
                    self.steps[-1]["operation"] = "double_click"
                    self._emit_step_update(len(self.steps) - 1, "double_click")
                    self.last_click_time = 0  # 重置，避免三击
                    return

        self.last_click_time = now
        self.last_click_pos = (x, y)

        # 解析 UI 元素
        target = resolve_element_at_point(x, y)

        # 检查是否属于目标应用
        if self.target_app and target:
            proc = target.get("processName", "")
            if proc and self.target_app.lower() not in proc.lower():
                # 不属于目标应用，忽略
                # 但如果是窗口切换，记录 switch_app
                return

        operation = "click" if button == mouse.Button.left else "right_click"
        args = {"x": x, "y": y}

        # 密码框过滤：不记录输入内容
        if target and "password" in (target.get("className", "") + target.get("autoId", "")).lower():
            target["_sensitive"] = True

        self._add_step(operation, args, target)

    def _on_mouse_scroll(self, x, y, dx, dy):
        """鼠标滚轮回调"""
        if not self.running or self.paused:
            return

        self._flush_key_buffer()

        direction = "down" if dy < 0 else "up"
        amount = abs(dy)

        target = resolve_element_at_point(x, y)

        # 检查目标应用
        if self.target_app and target:
            proc = target.get("processName", "")
            if proc and self.target_app.lower() not in proc.lower():
                return

        # 合并连续滚动
        with self._lock:
            if (self.steps
                    and self.steps[-1].get("operation") == "scroll"
                    and self.steps[-1].get("_scroll_mergeable", False)):
                last = self.steps[-1]
                last_direction = last["args"].get("direction", "")
                if last_direction == direction:
                    last["args"]["amount"] = last["args"].get("amount", 1) + amount
                    return

        self._add_step("scroll", {"direction": direction, "amount": amount}, target,
                        extra={"_scroll_mergeable": True})

    # ============================================================
    # 键盘事件处理
    # ============================================================

    def _on_key_press(self, key):
        """按键按下回调"""
        if not self.running or self.paused:
            return

        # 检测修饰键
        if key in (keyboard.Key.ctrl, keyboard.Key.ctrl_l, keyboard.Key.ctrl_r):
            self._ctrl_pressed = True
            return
        elif key in (keyboard.Key.alt, keyboard.Key.alt_l, keyboard.Key.alt_r, keyboard.Key.alt_gr):
            self._alt_pressed = True
            return
        elif key in (keyboard.Key.shift, keyboard.Key.shift_l, keyboard.Key.shift_r):
            self._shift_pressed = True
            return
        elif key in (keyboard.Key.cmd, keyboard.Key.cmd_l, keyboard.Key.cmd_r):
            self._win_pressed = True
            return

        # 修饰键组合：等待 release 确认
        if self._ctrl_pressed or self._alt_pressed or self._win_pressed:
            # 将普通键加入修饰键缓冲
            char = self._key_to_char(key)
            if char:
                self._modifier_buffer.append(char)
            return

        # 普通字符输入：加入按键缓冲
        char = self._key_to_char(key)
        if char:
            self.key_buffer.append(char)
            self.last_key_time = time.time()
            # 设置超时自动刷出（500ms无新按键则聚合）
            if self._key_buffer_timer:
                self._key_buffer_timer.cancel()
            self._key_buffer_timer = threading.Timer(0.5, self._flush_key_buffer)
            self._key_buffer_timer.daemon = True
            self._key_buffer_timer.start()
        elif key == keyboard.Key.enter:
            # 回车键：立即刷出缓冲并记录
            self._flush_key_buffer()
            self._add_step("shortcut", {"keys": "Enter"}, None)
        elif key == keyboard.Key.tab:
            self._flush_key_buffer()
            self._add_step("shortcut", {"keys": "Tab"}, None)
        elif key == keyboard.Key.esc:
            self._flush_key_buffer()
            self._add_step("shortcut", {"keys": "Esc"}, None)
        elif key == keyboard.Key.backspace:
            # 退格：简单处理为从缓冲移除最后一个字符
            if self.key_buffer:
                self.key_buffer.pop()

    def _on_key_release(self, key):
        """按键释放回调"""
        if key in (keyboard.Key.ctrl, keyboard.Key.ctrl_l, keyboard.Key.ctrl_r):
            # 修饰键释放：如果有组合，记录快捷键
            if self._modifier_buffer:
                parts = []
                if self._ctrl_pressed:
                    parts.append("Ctrl")
                if self._alt_pressed:
                    parts.append("Alt")
                if self._win_pressed:
                    parts.append("Win")
                parts.extend(self._modifier_buffer)
                keys_str = "+".join(parts)
                self._flush_key_buffer()
                self._add_step("shortcut", {"keys": keys_str}, None)
            self._ctrl_pressed = False
            self._modifier_buffer.clear()
        elif key in (keyboard.Key.alt, keyboard.Key.alt_l, keyboard.Key.alt_r, keyboard.Key.alt_gr):
            if self._modifier_buffer:
                parts = []
                if self._ctrl_pressed:
                    parts.append("Ctrl")
                if self._alt_pressed:
                    parts.append("Alt")
                if self._win_pressed:
                    parts.append("Win")
                parts.extend(self._modifier_buffer)
                keys_str = "+".join(parts)
                self._flush_key_buffer()
                self._add_step("shortcut", {"keys": keys_str}, None)
            self._alt_pressed = False
            self._modifier_buffer.clear()
        elif key in (keyboard.Key.shift, keyboard.Key.shift_l, keyboard.Key.shift_r):
            self._shift_pressed = False
        elif key in (keyboard.Key.cmd, keyboard.Key.cmd_l, keyboard.Key.cmd_r):
            if self._modifier_buffer:
                parts = []
                if self._ctrl_pressed:
                    parts.append("Ctrl")
                if self._alt_pressed:
                    parts.append("Alt")
                if self._win_pressed:
                    parts.append("Win")
                parts.extend(self._modifier_buffer)
                keys_str = "+".join(parts)
                self._flush_key_buffer()
                self._add_step("shortcut", {"keys": keys_str}, None)
            self._win_pressed = False
            self._modifier_buffer.clear()

    def _key_to_char(self, key) -> str:
        """将 pynput key 转为字符"""
        if isinstance(key, keyboard.KeyCode):
            if key.char:
                return key.char
            # 处理虚拟键码（如方向键等）
            return ""
        return ""

    def _flush_key_buffer(self):
        """将按键缓冲聚合为 type 步骤"""
        with self._lock:
            if not self.key_buffer:
                return
            text = "".join(self.key_buffer)
            self.key_buffer.clear()

        if not text:
            return

        # 密码保护：如果当前焦点是密码框，不记录具体内容
        safe_text = text
        # 简单检测：无法在此处获取焦点元素，由 UI 端过滤

        self._add_step("type", {"text": safe_text}, None)

    # ============================================================
    # 步骤管理
    # ============================================================

    def _add_step(self, operation: str, args: dict, target: Optional[dict],
                  extra: Optional[dict] = None):
        """添加一个语义步骤"""
        with self._lock:
            step = {
                "index": len(self.steps) + 1,
                "operation": operation,
                "args": args,
                "target": target or {},
                "note": "",
                "timestamp": datetime.now().isoformat()
            }
            if extra:
                step.update(extra)
            self.steps.append(step)

            # 输出步骤事件
            self._emit({"type": "step", **step})

            # 备注请求
            should_request_note = False
            if self.note_mode == "all":
                should_request_note = True
            elif self.note_mode == "key":
                # 关键步骤：操作类型变化时请求备注
                if len(self.steps) < 2:
                    should_request_note = True
                else:
                    prev_op = self.steps[-2].get("operation", "")
                    if prev_op != operation:
                        should_request_note = True

            if should_request_note:
                suggestion = suggest_note(operation, args, target)
                self._emit({
                    "type": "note_request",
                    "index": step["index"],
                    "operation": operation,
                    "suggestion": suggestion
                })

    def _emit_step_update(self, index: int, operation: str):
        """通知步骤被更新（如单击→双击）"""
        self._emit({
            "type": "step_update",
            "index": index,
            "operation": operation
        })

    # ============================================================
    # 输出
    # ============================================================

    def _emit(self, data: dict):
        """向 stdout 输出 JSON 事件"""
        try:
            print(json.dumps(data, ensure_ascii=False), flush=True)
        except Exception:
            pass

    def _emit_status(self):
        """输出录制状态"""
        self._emit({
            "type": "status",
            "recording": self.running,
            "paused": self.paused,
            "step_count": len(self.steps)
        })

    def _emit_error(self, message: str):
        """输出错误"""
        self._emit({"type": "error", "message": message})


# ============================================================
# 主循环
# ============================================================
def main():
    """读取 stdin 命令，驱动录制器"""
    # 初始化 UIAutomation
    uia_ok = _init_uia()
    if not uia_ok:
        print(json.dumps({
            "type": "warning",
            "message": "UIAutomation not available, ElementFromPoint disabled"
        }), flush=True)

    if not PYNPUT_AVAILABLE:
        print(json.dumps({
            "type": "error",
            "message": "pynput not installed. Run: pip install pynput"
        }), flush=True)
        # 仍然启动主循环，等待 stop 命令

    recorder = OperationRecorder()

    # 通知 Electron 录制器已就绪
    print(json.dumps({"type": "ready"}), flush=True)

    # 读取 stdin 命令
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            cmd = json.loads(line)
            recorder.handle_command(cmd)
        except json.JSONDecodeError as e:
            print(json.dumps({"type": "error", "message": f"Invalid JSON: {e}"}), flush=True)
        except Exception as e:
            print(json.dumps({"type": "error", "message": f"Command error: {e}"}), flush=True)


if __name__ == "__main__":
    main()
