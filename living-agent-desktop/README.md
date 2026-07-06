# Living Agent Desktop

> Living Agent Service 的桌面客户端，基于 Electron + Vite + React + TypeScript。
> 复用 `living-agent-service/frontend` 渲染层，桥接后端 Web API。

## 文档

- 详细计划：[LIVING_AGENT_DESKTOP_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/LIVING_AGENT_DESKTOP_PLAN.md)
- 依赖清单与安全：[DEPENDENCIES.md](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/DEPENDENCIES.md)
- 上游依据：[HERMES_COMPARISON_AND_BORROWING_PLAN.md §3 §5 §6](file:///f:/SoarCloudAI/docker/living-agent-service/docs/HERMES_COMPARISON_AND_BORROWING_PLAN.md)
- 权限矩阵：[权限与入口矩阵.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/权限与入口矩阵.md)
- Windows 自动化设计：[WINDOWS_MCP_INTEGRATION_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/WINDOWS_MCP_INTEGRATION_PLAN.md)

---

## 两种运行模式

本桌面客户端支持两种运行模式，请根据场景选择：

| 模式 | Python 来源 | 适用场景 | 目标机器要求 |
|------|------------|----------|-------------|
| **开发模式** | 系统 Python | 本机开发测试 | 需安装 Python + 依赖 |
| **生产模式** | 嵌入式 Python | 打包分发到其他客户端 | 无需安装 Python |

---

## 开发模式（本机测试）

### 环境要求

- Node.js 18+
- npm 9+（或 pnpm）
- Python 3.10+（Windows）
- 后端服务（端口 8382）

### 步骤

```powershell
# 1. 安装 Node.js 依赖
cd f:\SoarCloudAI\docker\living-agent-service\living-agent-desktop
npm install

# 2. 安装 Python 依赖（Windows 自动化）
pip install -r resources/win-automation/requirements.txt

# 3. 验证 lockfile 完整性
npm run verify:lockfile

# 4. 启动开发模式（Electron + Vite HMR）
npm run dev

# 5. 类型检查（可选）
npm run typecheck
```

### 开发模式说明

- **Python 调用方式**：使用系统 `python` 命令，需提前安装依赖
- **热更新**：Vite HMR 实时更新前端代码
- **调试**：DevTools 自动可用（main/renderer 均可调试）

---

## 生产模式（打包分发）

### 环境要求

- Node.js 18+
- npm 9+
- Python 3.10+ 嵌入式包（`python-3.10.x-embed-amd64.zip`）

### 打包前准备

#### 1. 准备嵌入式 Python 环境

```powershell
# 创建嵌入式 Python 目录
cd f:\SoarCloudAI\docker\living-agent-service\living-agent-desktop
mkdir resources\python

# 下载 Python 嵌入式包（从 python.org）
# 解压到 resources\python\ 目录

# 目录结构：
# resources/python/
#   python.exe
#   python310.dll
#   python310.zip
#   ...
```

#### 2. 安装 Python 依赖到嵌入式环境

```powershell
# 方法 A：使用 pip 安装到嵌入式环境（推荐）
# 先解压 python310.zip，添加 Lib/site-packages 目录
cd resources\python
# 编辑 python310._pth，取消注释 import site
# 使用嵌入式 pip 安装依赖
python.exe -m pip install -r ../win-automation/requirements.txt --target Lib/site-packages

# 方法 B：手动复制已安装的依赖
# 从系统 Python 的 site-packages 复制所需包到 Lib/site-packages
```

#### 3. 配置 electron-builder.yml

当前配置已包含：

```yaml
extraResources:
  - from: resources/win-automation
    to: win-automation
  - from: resources/python       # 需要添加
    to: python
```

### 打包步骤

```powershell
# 1. 确保 Node.js 依赖已安装
npm install

# 2. 确保 Python 嵌入式环境已准备
#    - resources/python/python.exe 存在
#    - 依赖已安装到 resources/python/Lib/site-packages

# 3. 构建 + 打包 Windows x64
npm run build:win

# 4. 输出位置
#    - dist/Living Agent Desktop Setup 1.0.0.exe（安装包）
#    - dist/win-unpacked/（免安装版）
```

### 生产模式说明

- **Python 调用方式**：使用 `process.resourcesPath/python/python.exe`
- **目标机器无需安装 Python**：嵌入式 Python 随应用打包
- **依赖自带**：所有 Python 依赖打包在嵌入式环境中

---

## 安全审计

```powershell
# 依赖审计（preinstall 自动运行）
npm run audit:deps

# 仅生产依赖安全审计
npm run audit:security

# 完整安全审计（包含开发依赖）
npm run audit:security:full
```

---

## 核心能力

| 能力 | 详细 |
|------|------|
| **虚拟办公室** | 复用 `frontend/pages/DepartmentDetail` |
| **本地产物保存** | 详见 [HERMES_COMPARISON_AND_BORROWING_PLAN.md §6](file:///f:/SoarCloudAI/docker/living-agent-service/docs/HERMES_COMPARISON_AND_BORROWING_PLAN.md) |
| **公共任务栏** | 托盘红点 + OS 通知 + 悬浮窗 + 本地缓存 |
| **Windows 自动化** | Python 服务（UIAutomation + PowerShell + 进程管理等） |
| **产物权限控制** | 按角色过滤可见产物（详见 §6.18） |

---

## Windows 自动化操作列表

| 操作 | 功能 | 权限要求 |
|------|------|----------|
| `launch_app` | 启动应用（模糊匹配） | DEPARTMENT |
| `switch_app` | 切换窗口 | DEPARTMENT |
| `click` | 鼠标点击 | DEPARTMENT |
| `type` | 键盘输入 | DEPARTMENT |
| `screenshot` | 截图 | DEPARTMENT |
| `snapshot` | UI 树 + 截图 | DEPARTMENT |
| `shell` | PowerShell 命令 | FULL + 审批 |
| `process_list` | 进程列表 | DEPARTMENT |
| `filesystem_*` | 文件操作 | DEPARTMENT/FULL |
| `registry_*` | 注册表操作 | DEPARTMENT/FULL |

详见 [WINDOWS_MCP_INTEGRATION_PLAN.md §2.1](file:///f:/SoarCloudAI/docker/living-agent-service/docs/WINDOWS_MCP_INTEGRATION_PLAN.md)

---

## 项目结构

```
living-agent-desktop/
├── src/
│   ├── main/                  # Electron 主进程
│   │   ├── win-automation-service.ts  # Python 子进程管理
│   │   ├── ipc.ts             # IPC handler
│   │   ├── ws-client.ts       # WebSocket 连接
│   │   └── index.ts           # 入口
│   ├── preload/               # Preload（暴露 window.livingAgentAPI）
│   ├── renderer/              # 桌面端特有页面（OfficeChat 等）
│   ├── shared/                # 主进程+渲染进程共享类型
│   └── types/                 # 类型声明
├── resources/
│   ├── win-automation/        # Python 自动化服务
│   │   ├── service.py         # 主服务（借鉴 Windows-MCP）
│   │   └── requirements.txt   # Python 依赖
│   └── python/                # 嵌入式 Python（打包时需要）
│       ├── python.exe
│       ├── Lib/site-packages/
│       └── ...
├── electron.vite.config.ts
├── electron-builder.yml
├── package.json
└── LIVING_AGENT_DESKTOP_PLAN.md
```

---

## 常见问题

### 1. 开发模式下 Windows 自动化不工作？

**原因**：Python 依赖未安装。

**解决**：
```powershell
pip install -r resources/win-automation/requirements.txt
```

### 2. 打包后在其他机器运行报错 "Python not found"？

**原因**：未准备嵌入式 Python 环境。

**解决**：按上述「生产模式」步骤准备 `resources/python/` 目录。

### 3. Python 嵌入式包如何获取？

从 python.org 下载：
- Windows embeddable package (64-bit)
- 例如：`python-3.10.11-embed-amd64.zip`

---

## 状态

🚧 实施中 - 详见 [LIVING_AGENT_DESKTOP_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/LIVING_AGENT_DESKTOP_PLAN.md) §8