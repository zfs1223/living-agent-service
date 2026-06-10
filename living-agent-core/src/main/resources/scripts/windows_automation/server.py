"""
Windows 自动化桥接服务
基于 pywinauto + FastAPI，提供 HTTP API 供 Java 服务调用
支持自动注册到服务器和定时心跳
"""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from pywinauto import Application, Desktop
from pywinauto.timings import wait_until_passes
import psutil
import json
import time
import os
import socket
import uuid
import threading
import requests as http_requests
from pathlib import Path
from typing import Optional, Dict, Any, List
import logging
from contextlib import asynccontextmanager


logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


# 全局应用实例存储
active_sessions: Dict[str, Dict[str, Any]] = {}

# ==================== 节点注册与心跳 ====================

NODE_ID_FILE = Path(__file__).parent / "node_id.txt"
CONFIG_PATH = Path(__file__).parent / "config.json"

# 从 config.json 读取服务器地址
def load_config():
    if CONFIG_PATH.exists():
        with open(CONFIG_PATH, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {}

_config = load_config()
_server_config = _config.get("server", {})
_registration_config = _config.get("registration", {})
SERVER_URL = _registration_config.get("server_url", "")
HEARTBEAT_INTERVAL = _registration_config.get("heartbeat_interval", 60)


def load_or_create_node_id() -> str:
    """加载或创建节点 ID（首次启动生成 UUID，保存到文件）"""
    if NODE_ID_FILE.exists():
        with open(NODE_ID_FILE, 'r') as f:
            node_id = f.read().strip()
            if node_id:
                return node_id
    # 首次启动，生成新 ID
    node_id = f"node-{uuid.uuid4().hex[:12]}"
    with open(NODE_ID_FILE, 'w') as f:
        f.write(node_id)
    logger.info(f"生成新节点 ID: {node_id}")
    return node_id


def get_local_ip() -> str:
    """获取本机局域网 IP"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"


def get_host_info() -> Dict[str, Any]:
    """获取本机硬件信息"""
    try:
        mem = psutil.virtual_memory()
        return {
            "hostname": socket.gethostname(),
            "cpu_count": psutil.cpu_count(logical=True),
            "memory_gb": round(mem.total / (1024**3), 1),
        }
    except Exception:
        return {}


def load_applications() -> Dict[str, Any]:
    """从 config.json 读取已配置的应用列表"""
    apps = _config.get("applications", {})
    return {k: {"name": v.get("name", k), "enabled": v.get("enabled", True)}
            for k, v in apps.items() if v.get("enabled", True)}


def register_to_server():
    """向服务器注册本节点"""
    if not SERVER_URL:
        logger.info("未配置服务器地址 (registration.server_url)，跳过自动注册")
        return

    node_id = load_or_create_node_id()
    host_info = get_host_info()
    port = _server_config.get("port", 8765)

    registration_data = {
        "node_id": node_id,
        "ip": get_local_ip(),
        "port": port,
        "applications": load_applications(),
        **host_info,
    }

    # 从 config.json 读取可选字段
    if _registration_config.get("tenant_id"):
        registration_data["tenant_id"] = _registration_config["tenant_id"]
    if _registration_config.get("user_id"):
        registration_data["user_id"] = _registration_config["user_id"]
    if _registration_config.get("description"):
        registration_data["description"] = _registration_config["description"]

    try:
        response = http_requests.post(
            f"{SERVER_URL}/api/windows-automation/nodes/register",
            json=registration_data,
            timeout=10
        )
        if response.status_code == 200:
            data = response.json()
            logger.info(f"节点注册成功: {node_id}, 心跳间隔: {data.get('heartbeat_interval', 60)}s")
            global HEARTBEAT_INTERVAL
            HEARTBEAT_INTERVAL = data.get("heartbeat_interval", HEARTBEAT_INTERVAL)
        else:
            logger.warning(f"节点注册失败: HTTP {response.status_code}")
    except Exception as e:
        logger.warning(f"无法连接服务器 {SERVER_URL}: {e}")


def heartbeat_loop():
    """定时心跳线程"""
    if not SERVER_URL:
        return

    node_id = load_or_create_node_id()
    while True:
        try:
            http_requests.post(
                f"{SERVER_URL}/api/windows-automation/nodes/{node_id}/heartbeat",
                json={
                    "status": "online",
                    "ip": get_local_ip(),
                    "active_sessions": len(active_sessions),
                },
                timeout=5
            )
        except Exception:
            pass
        time.sleep(HEARTBEAT_INTERVAL)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    logger.info("Windows 自动化服务启动")

    # 启动时向服务器注册
    register_to_server()

    # 启动心跳线程
    if SERVER_URL:
        heartbeat_thread = threading.Thread(target=heartbeat_loop, daemon=True)
        heartbeat_thread.start()
        logger.info("心跳线程已启动")

    yield
    # 清理所有活跃会话
    logger.info("关闭所有活跃的 Windows 应用会话")
    for session_id, session in list(active_sessions.items()):
        try:
            if 'app' in session and session['app']:
                session['app'].kill()
        except:
            pass
    active_sessions.clear()
    logger.info("Windows 自动化服务已关闭")


app = FastAPI(title="Windows Automation Service", lifespan=lifespan)


# ==================== 数据模型 ====================

class LaunchRequest(BaseModel):
    app_name: str
    exe_path: Optional[str] = None
    backend: str = "win32"
    session_id: Optional[str] = None


class LoginRequest(BaseModel):
    session_id: str
    username: str
    password: str
    username_pattern: Optional[str] = ".*用户名.*|.*账号.*|.*操作员.*"
    password_pattern: Optional[str] = ".*密码.*|.*口令.*"
    login_button_pattern: Optional[str] = ".*登录.*|.*确定.*|.*OK.*"
    timeout: int = 30


class MenuRequest(BaseModel):
    session_id: str
    menu_path: str


class ClickRequest(BaseModel):
    session_id: str
    control_type: Optional[str] = "Button"
    title_pattern: str
    timeout: int = 5


class TypeKeysRequest(BaseModel):
    session_id: str
    control_type: Optional[str] = "Edit"
    title_pattern: str
    text: str
    with_spaces: bool = True


class GetTextRequest(BaseModel):
    session_id: str
    control_type: Optional[str] = None
    title_pattern: Optional[str] = None


class ScreenshotRequest(BaseModel):
    session_id: str
    output_path: Optional[str] = None


class CloseRequest(BaseModel):
    session_id: str


class WindowInfo(BaseModel):
    title: str
    control_type: str
    auto_id: Optional[str] = None
    class_name: Optional[str] = None


class ControlInfo(BaseModel):
    title: str
    control_type: str
    value: Optional[str] = None
    children: Optional[List['ControlInfo']] = None


ControlInfo.model_rebuild()


# ==================== 工具函数 ====================

def generate_session_id(app_name: str) -> str:
    """生成会话ID"""
    import uuid
    return f"{app_name}_{uuid.uuid4().hex[:8]}"


def find_control(window, control_type: Optional[str], title_pattern: str, timeout: int = 5):
    """查找控件"""
    try:
        kwargs = {}
        if control_type:
            kwargs['control_type'] = control_type
        if title_pattern:
            kwargs['title_re'] = title_pattern
        
        control = window.child_window(**kwargs)
        control.wait('exists', timeout=timeout)
        return control
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"控件未找到: {e}")


# ==================== API 端点 ====================

@app.get("/health")
async def health_check():
    """健康检查"""
    return {
        "status": "ok",
        "active_sessions": len(active_sessions),
        "timestamp": time.time()
    }


@app.post("/api/windows/launch")
async def launch_app(request: LaunchRequest):
    """启动 Windows 应用"""
    try:
        # 确定可执行文件路径
        exe_path = request.exe_path
        if not exe_path:
            # 从配置中查找
            config_path = Path(__file__).parent / "config.json"
            if config_path.exists():
                with open(config_path, 'r', encoding='utf-8') as f:
                    config = json.load(f)
                    app_config = config.get('applications', {}).get(request.app_name)
                    if app_config:
                        exe_path = app_config.get('exe_path')
                        request.backend = app_config.get('backend', 'win32')
        
        if not exe_path:
            raise HTTPException(status_code=400, detail="未提供 exe_path 且在配置中未找到应用")
        
        if not os.path.exists(exe_path):
            raise HTTPException(status_code=404, detail=f"可执行文件不存在: {exe_path}")
        
        # 启动应用
        logger.info(f"启动应用: {request.app_name}, 路径: {exe_path}")
        app_instance = Application(backend=request.backend).start(exe_path)
        
        # 生成会话ID
        session_id = request.session_id or generate_session_id(request.app_name)
        
        # 存储会话信息
        active_sessions[session_id] = {
            'app': app_instance,
            'app_name': request.app_name,
            'backend': request.backend,
            'started_at': time.time()
        }
        
        logger.info(f"应用启动成功，会话ID: {session_id}")
        return {
            "success": True,
            "session_id": session_id,
            "message": f"应用 {request.app_name} 已启动"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"启动应用失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/windows/login")
async def login_app(request: LoginRequest):
    """登录应用"""
    try:
        if request.session_id not in active_sessions:
            raise HTTPException(status_code=404, detail="会话不存在")
        
        session = active_sessions[request.session_id]
        app_instance = session['app']
        
        # 等待登录窗口
        login_window = app_instance.window(title_re=".*登录.*")
        login_window.wait('visible', timeout=request.timeout)
        
        # 输入用户名
        username_edit = find_control(
            login_window,
            "Edit",
            request.username_pattern,
            request.timeout
        )
        username_edit.set_text(request.username)
        
        # 输入密码
        password_edit = find_control(
            login_window,
            "Edit",
            request.password_pattern,
            request.timeout
        )
        password_edit.set_text(request.password)
        
        # 点击登录按钮
        login_button = find_control(
            login_window,
            "Button",
            request.login_button_pattern,
            request.timeout
        )
        login_button.click()
        
        time.sleep(3)
        
        return {
            "success": True,
            "message": "登录成功"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"登录失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/windows/menu")
async def select_menu(request: MenuRequest):
    """选择菜单"""
    try:
        if request.session_id not in active_sessions:
            raise HTTPException(status_code=404, detail="会话不存在")
        
        session = active_sessions[request.session_id]
        app_instance = session['app']
        
        # 获取主窗口
        main_window = app_instance.window(title_re=f".*{session['app_name']}.*")
        
        # 选择菜单
        main_window.menu_select(request.menu_path)
        time.sleep(2)
        
        return {
            "success": True,
            "message": f"菜单已选择: {request.menu_path}"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"菜单选择失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/windows/click")
async def click_control(request: ClickRequest):
    """点击控件"""
    try:
        if request.session_id not in active_sessions:
            raise HTTPException(status_code=404, detail="会话不存在")
        
        session = active_sessions[request.session_id]
        app_instance = session['app']
        
        # 获取活动窗口
        window = app_instance.window()
        
        # 查找并点击控件
        control = find_control(
            window,
            request.control_type,
            request.title_pattern,
            request.timeout
        )
        control.click()
        
        return {
            "success": True,
            "message": f"控件已点击: {request.title_pattern}"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"点击控件失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/windows/type_keys")
async def type_keys(request: TypeKeysRequest):
    """输入文本"""
    try:
        if request.session_id not in active_sessions:
            raise HTTPException(status_code=404, detail="会话不存在")
        
        session = active_sessions[request.session_id]
        app_instance = session['app']
        
        # 获取活动窗口
        window = app_instance.window()
        
        # 查找控件
        control = find_control(
            window,
            request.control_type,
            request.title_pattern
        )
        
        # 输入文本
        control.set_text(request.text)
        
        return {
            "success": True,
            "message": f"文本已输入: {request.text}"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"输入文本失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/windows/get_text")
async def get_control_text(request: GetTextRequest):
    """获取控件文本"""
    try:
        if request.session_id not in active_sessions:
            raise HTTPException(status_code=404, detail="会话不存在")
        
        session = active_sessions[request.session_id]
        app_instance = session['app']
        
        # 获取活动窗口
        window = app_instance.window()
        
        # 查找控件
        if request.title_pattern:
            control = find_control(
                window,
                request.control_type,
                request.title_pattern
            )
        else:
            control = window
        
        # 获取文本
        text = control.window_text()
        
        return {
            "success": True,
            "text": text
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"获取文本失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/windows/screenshot")
async def take_screenshot(request: ScreenshotRequest):
    """截取屏幕截图"""
    try:
        if request.session_id not in active_sessions:
            raise HTTPException(status_code=404, detail="会话不存在")
        
        session = active_sessions[request.session_id]
        app_instance = session['app']
        
        # 获取窗口截图
        window = app_instance.window()
        
        try:
            # 使用 pywinauto 的截图功能
            image = window.capture_as_image()
            
            # 保存截图
            output_path = request.output_path or f"screenshot_{request.session_id}_{int(time.time())}.png"
            image.save(output_path)
            
            return {
                "success": True,
                "output_path": output_path
            }
        except Exception as e:
            # 如果 capture_as_image 不可用，使用备用方法
            logger.warning(f"capture_as_image 不可用: {e}")
            return {
                "success": False,
                "message": "截图功能不可用，请安装 Pillow"
            }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"截图失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/windows/controls")
async def get_controls(session_id: str):
    """获取窗口控件树"""
    try:
        if session_id not in active_sessions:
            raise HTTPException(status_code=404, detail="会话不存在")
        
        session = active_sessions[session_id]
        app_instance = session['app']
        
        # 获取主窗口
        window = app_instance.window()
        
        # 打印控件信息（这里简化处理）
        controls_info = []
        try:
            for ctrl in window.children():
                info = {
                    'title': ctrl.window_text(),
                    'control_type': ctrl.friendly_class_name(),
                    'auto_id': ctrl.automation_id() if hasattr(ctrl, 'automation_id') else None,
                }
                controls_info.append(info)
        except:
            pass
        
        return {
            "success": True,
            "controls": controls_info
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"获取控件失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/windows/close")
async def close_app(request: CloseRequest):
    """关闭应用"""
    try:
        if request.session_id not in active_sessions:
            raise HTTPException(status_code=404, detail="会话不存在")
        
        session = active_sessions[request.session_id]
        app_instance = session['app']
        
        try:
            app_instance.kill()
        except:
            pass
        
        del active_sessions[request.session_id]
        
        return {
            "success": True,
            "message": "应用已关闭"
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"关闭应用失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/windows/sessions")
async def list_sessions():
    """列出活跃会话"""
    sessions = []
    for session_id, session in active_sessions.items():
        sessions.append({
            'session_id': session_id,
            'app_name': session['app_name'],
            'backend': session['backend'],
            'started_at': session['started_at'],
            'duration': time.time() - session['started_at']
        })
    
    return {
        "success": True,
        "sessions": sessions,
        "count": len(sessions)
    }


# ==================== 启动 ====================

if __name__ == "__main__":
    import uvicorn
    
    # 加载配置
    config_path = Path(__file__).parent / "config.json"
    config = {}
    if config_path.exists():
        with open(config_path, 'r', encoding='utf-8') as f:
            config = json.load(f)
    
    server_config = config.get('server', {})
    host = server_config.get('host', '0.0.0.0')
    port = server_config.get('port', 8765)
    
    logger.info(f"启动 Windows 自动化服务: {host}:{port}")
    uvicorn.run(app, host=host, port=port)
