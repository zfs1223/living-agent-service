# Hermes Desktop vs Living Agent Service 前端对比与借鉴分析

> 创建日期：2026-06-03
> 范围：`hermes-desktop-0.5.5` 前端架构与 `living-agent-service/frontend` 对比
> 目的：识别 Hermes 中适合 living-agent-service 借鉴的前端模式

---

## 一、项目背景对比

| 维度 | Hermes Desktop 0.5.5 | Living Agent Service |
|------|----------------------|----------------------|
| **类型** | Electron 桌面应用 | Web 应用（Vite + React + TypeScript） |
| **目标** | 通用 AI Agent 客户端 | 企业级数字员工服务 |
| **运行模式** | 本地（+ SSH 远程） | Web 服务（容器化） |
| **核心场景** | 单用户聊天/编码/记忆 | 多部门虚拟办公室 |
| **LLM 接入** | 30+ Provider（OpenRouter/Nous/Mistral…） | 后端抽象更强：详见下方"LLM Provider 架构对比" |
| **i18n** | 10 种语言 | 2 种（zh/en） |
| **规模** | ~280 文件 | ~120 文件 |
| **架构特点** | 单进程 React + IPC | React + WebSocket + REST |

---

## 二、LLM Provider 架构对比

> 这部分补充第一节第 17 行：Living Agent Service 的 LLM 接入方式**比 Hermes 更强**，
> 但供应商添加受后端固定类型枚举限制。

### 2.0 后端 Provider 抽象（参考 `CODE_STRUCTURE_AND_FILE_GUIDE.md` L684-715）

| 层次 | 类/接口 | 职责 |
|------|---------|------|
| **Provider 接口** | `provider/Provider.java` | LLM/ASR/TTS 统一抽象 |
| **Provider 注册** | `ProviderRegistry` / `impl/ProviderRegistryImpl` | 运行时 Provider 注册 |
| **Provider 实现** | `provider/impl/QwenProvider`、`OllamaProvider`、`BitNetProvider`、`AnthropicProvider`、`AsrProvider`、`TtsProvider` | 具体协议实现 |
| **Provider 工厂** | `provider/impl/ProviderFactory` | 根据配置创建 Provider |
| **模型池管理器** | `model/pool/ModelPoolManager` | 模型 + Provider 统一管理，支持测试连接 |
| **Provider 注册清单** | `model/pool/LlmProviderRegistry`（原 `ProviderRegistry` 已重命名） | Provider 注册清单查询 |
| **客户端工厂** | `model/pool/LlmClientFactory` | **按 Provider 类型创建客户端** |
| **客户端实现** | `model/pool/client/AnthropicClient`、`OpenAiCompatibleClient` | Anthropic 协议 + OpenAI 兼容 |
| **已解析 Provider** | `provider/impl/ResolvedBrainModelProvider` | 模型池解析后的大脑调用链路 |
| **配置实体** | `model/pool/ProviderConfig`、`LlmModel` | Provider ID、类型、BaseURL、API Key、启用状态 |

### 2.1 与 Hermes 对比

| 维度 | Hermes | Living Agent |
|------|--------|--------------|
| **Provider 数量** | 30+（OpenRouter/Nous/Mistral…） | 内置 7 类（Qwen/Ollama/BitNet/Anthropic/Asr/Tts + OpenAI 兼容） |
| **可扩展性** | Provider 自动发现（[default-models.ts](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/main/default-models.ts)） | **需后端新增实现类 + 注册到 `LlmClientFactory`** |
| **协议抽象** | 每个 Provider 一个文件 | **统一抽象**（`Provider` 接口）+ 协议客户端（`AnthropicClient`/`OpenAiCompatibleClient`） |
| **模型选择** | Profile + Model picker | **三层架构**（MainBrain 动态选择 / Qwen3Neuron / ToolNeuron）+ 模型池能力评估 |
| **Brain 路由** | 无 | **`BrainModelResolver`** 能力匹配 + 部门继承 |
| **Claude CLI 代理** | 无 | **`ClaudeProxyController`** + `AnthropicToOpenAiConverter` |
| **OAuth 发现** | `oauth-model-discovery` | 无（依赖 API Key） |
| **运行模式** | 桌面 / SSH 远程 | Docker 容器化部署 |

### 2.2 结论

- **架构层面 Living Agent 远超 Hermes**：模型池 + 能力评估 + 三层 LLM + Brain 路由 + Claude CLI 代理
- **Provider 数量少于 Hermes**：但通过 `OpenAiCompatibleClient` 已兼容所有 OpenAI 协议
- **扩展瓶颈**：新增 Provider 需要在后端写 Java 实现类（不像 Hermes 配置文件即可）

### 2.3 Living Agent LLM 链路

```
用户输入
  ↓
权限检查（CHAT_ONLY/LIMITED/DEPARTMENT/FULL）
  ↓
意图分析 → 部门路由
  ↓
BrainModelResolver.resolveForEmployee(employeeId, departmentId, departmentBrainId)
  ├── 1. 员工专属模型分配（数据库）
  ├── 2. 基于任务能力选择（ModelCapabilityAssessor）
  │      └── 60% 能力匹配度 + 40% 性能分
  ├── 3. 部门分配的员工模型
  ├── 4. 继承部门 Brain 的模型
  └── 5. 回退默认模型
  ↓
LlmClientFactory.createClient(providerType)
  ├── QwenProvider / OllamaProvider / BitNetProvider
  ├── AnthropicClient（Anthropic 协议）
  └── OpenAiCompatibleClient（OpenAI 兼容 - 智谱/Mistral/OpenAI）
  ↓
LLM 调用（流式或阻塞）
```

---

## 二（原节顺延为三）、Hermes 的核心特色模块

### 2.1 三层 Office 视图

- **`Office.tsx`**：用 webview 嵌入 Claw3D 3D 虚拟办公室
- 实时状态检测：`checking → not-installed → installing → ready → error`
- 进度条 + 日志流
- **WebSocket URL 可配置**（本地/远程）
- 自动重连 + 错误横幅
- 端口冲突检测

### 2.2 高级 Chat 体验

| 组件 | 功能 |
|------|------|
| `ChatInput.tsx` | IME 兼容、附件拖拽、Slash 命令、语音输入、上下文仪表 |
| `useVoiceInput.ts` | 双策略：浏览器 SpeechRecognition + MediaRecorder 兜底（Whisper 转录） |
| `ContextGauge.tsx` | 圆形进度环显示 prompt token 占用 + cache 命中 |
| `ChatHeader.tsx` | Fast Mode 切换、Token/Cost 徽章、清空按钮 |
| `useChatIPC.ts` | IPC 监听（消息/进度/工具调用/审批/中止） |
| `MessageRow.tsx` | 消息渲染 + Markdown + 工具调用展示 |
| `slashCommands.ts` | /help /model /clear 等 |
| `useInputHistory.ts` | 输入历史（上下箭头） |
| `WorktreePanel.tsx` | 文件树（vscode 风格图标） |
| `FileViewer.tsx` | 文件预览 |

### 2.3 Memory 多维展示

- **`Memory.tsx`**：4 个 Tab（entries / profile / providers / soul）
- **`CapacityCards.tsx`**：容量卡片
- **`MemoryEntries.tsx`**：条目列表（CRUD）
- **`MemoryProfile.tsx`**：用户画像编辑（带 charLimit）
- **`MemoryProviders.tsx`**：Memory provider 切换
- **`Soul.tsx`**：SOUL 配置（agent 性格/规则）
- 顶部 `Refresh` 按钮 + Loading 状态

### 2.4 看板（Kanban）

- 任务卡片：title/body/assignee/status/priority/skills/result
- 看板板：slug/name/description/icon/color
- 状态计数：counts 字段
- 任务状态机：pending → running → completed/failed
- **支持 current/archived 看板切换**

### 2.5 Sessions 增强

- 时间分组：today / yesterday / thisWeek / earlier
- 搜索（按 snippet）
- 模型/消息数/时间显示
- 一键恢复 + 删除

### 2.6 国际化架构

- **`shared/i18n/locales/{en,zh-CN,zh-TW,ja,es,pl,pt-BR,pt-PT,id}/`**
- 每个语言独立 TypeScript 文件
- 强类型 key（编译时检查）
- 主进程 + 渲染进程共享（IPC 同步 locale）
- localStorage 持久化 + 主进程覆盖

### 2.7 Profiles 切换

- 多 Profile 概念（per-user 配置）
- `ProfileSwitcher.tsx` UI
- 切换 profile 重新加载记忆/模型/Provider
- 适合 living-agent 的"部门"概念

### 2.8 ConfigHealthBanner 模式

- 配置预检（API key 缺失、Provider 未配置）
- Banner 提示 + 一键跳转到 Settings
- 不阻塞操作，只在发送前拦截

---

## 三、独立桌面应用前端方案

> Living Agent Service 后端 LLM 能力远超 Hermes，但供应商扩展受后端类型限制。
> 借鉴 Hermes 的 **Electron 桌面应用思路**，但适配 Living Agent 的 Web API，
> 可作为"客户端"产品形态补充 Web 端。

### 3.1 方案定位

| 维度 | 说明 |
|------|------|
| **目标用户** | 数字员工/管理员需要在本地用 PC 客户端管理任务、查看虚拟办公室 |
| **与 Web 端差异** | 桌面端增加：系统托盘、本地文件访问、离线缓存、OS 通知 |
| **后端复用** | 完全复用现有 Web 后端（WebSocket + REST） |
| **代码基础** | Hermes Desktop 0.5.5 已验证可行（Electron + Vite + React + TS） |
| **新增工程** | `living-agent-desktop/`（独立项目） |

### 3.2 架构对比

```
Hermes Desktop:
  Electron Main Process (Node.js)
    ├── IPC handlers
    ├── Provider registry (本地调用)
    └── Profile management
  Renderer Process (React)
    └── 直接通过 IPC 调用主进程

Living Agent Desktop（建议）:
  Electron Main Process (Node.js)
    ├── 本地缓存（任务历史/产物）
    ├── 文件系统访问（产物/知识库）
    ├── OS 通知（任务完成）
    └── 启动器（检测后端可达性）
  Renderer Process (React)
    ├── 复用 living-agent-service/frontend（Vite build）
    └── 通过 WebSocket/REST 调后端
```

### 3.3 与 Hermes 桌面端的关键差异

| 维度 | Hermes Desktop | Living Agent Desktop |
|------|---------------|----------------------|
| **后端调用** | 主进程直连 Provider API | 主进程仅做本地服务，**所有 LLM 走 Web 后端** |
| **Provider 配置** | 桌面端配置 | **后端统一管理**（模型池） |
| **多用户** | 单 Profile | **多账号登录**（auth API） |
| **数据存储** | 本地 SQLite | **后端为主 + 本地缓存** |
| **任务执行** | 本地 Kanban | **远端部门/员工任务**（WebSocket） |
| **桌面更新** | electron-updater | **跟随 Web 后端**（无需自更新） |

### 3.4 借鉴 Hermes 的桌面端特性（优先级排序）

| 借鉴项 | 借鉴来源 | 价值 |
|--------|---------|------|
| **Electron + Vite 构建** | `electron.vite.config.ts` | 桌面端脚手架 |
| **IPC 桥接模式** | `preload/index.ts` 暴露 `window.hermesAPI` | 安全的前后端通信 |
| **系统托盘** | `claw3d.ts` 等 | 显示后端服务状态、待办任务数 |
| **OS 通知** | `notification.ts` | 任务完成推送 |
| **本地附件暂存** | `attachment-staging.ts` | 上传文件本地暂存 |
| **启动 Splash** | `SplashScreen.tsx` | 桌面端品牌体验 |
| **菜单栏** | `Menu.setApplicationMenu` | 桌面端常用快捷键 |
| **窗口管理** | `BrowserWindow` + 持久化 | 记忆窗口位置/大小 |
| **快捷键** | `globalShortcut` | 全局快捷键唤醒 |
| **远程模式** | `remote-mode-url-and-spawn` | 通过 SSH/VPN 访问内网后端 |

### 3.5 桌面端架构设计

#### 3.5.1 项目结构

```
living-agent-desktop/
├── src/
│   ├── main/                    # Electron 主进程
│   │   ├── index.ts             # 入口
│   │   ├── window.ts            # 窗口管理
│   │   ├── tray.ts              # 系统托盘
│   │   ├── menu.ts              # 菜单栏
│   │   ├── ipc.ts               # IPC handlers
│   │   ├── local-cache.ts       # SQLite 本地缓存
│   │   ├── notifications.ts     # OS 通知
│   │   ├── connection.ts        # 后端连接检测
│   │   └── shortcuts.ts         # 全局快捷键
│   ├── preload/
│   │   └── index.ts             # 暴露 window.livingAgentAPI
│   └── renderer/                # 复用 living-agent-service/frontend
│       ├── App.tsx
│       ├── main.tsx
│       └── pages/...
├── electron.vite.config.ts
├── electron-builder.yml
├── package.json
└── README.md
```

#### 3.5.2 IPC 接口设计（参考 Hermes）

```typescript
// preload/index.ts 暴露给渲染进程
window.livingAgentAPI = {
  // 后端连接
  checkBackend: () => Promise<{ok: boolean; url: string}>;
  getBackendUrl: () => string;
  setBackendUrl: (url: string) => void;
  
  // 本地缓存
  cacheReceipt: (receipt: any) => Promise<void>;
  getCachedReceipts: (employeeCode: string) => Promise<any[]>;
  
  // 文件系统
  openArtifact: (path: string) => Promise<void>;
  showInFolder: (path: string) => void;
  saveAttachment: (file: File) => Promise<string>;
  
  // OS 通知
  notify: (title: string, body: string) => void;
  
  // 窗口控制
  minimizeToTray: () => void;
  showWindow: () => void;
  
  // 应用信息
  getVersion: () => string;
  getPlatform: () => string;
};
```

#### 3.5.3 复用 Web 端代码

```typescript
// electron.vite.config.ts
import { defineConfig } from 'electron-vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  main: { /* 主进程配置 */ },
  preload: { /* preload 配置 */ },
  renderer: {
    root: '../living-agent-service/frontend',
    build: {
      rollupOptions: {
        input: path.resolve(__dirname, '../living-agent-service/frontend/index.html')
      }
    }
  }
});
```

### 3.6 实施步骤

| 步骤 | 内容 | 工作量 |
|------|------|--------|
| 1 | 初始化 `living-agent-desktop/` 项目，复用 `package.json` | 1 天 |
| 2 | 配置 `electron.vite.config.ts` 引用 Web 端 | 0.5 天 |
| 3 | 实现主进程（窗口/托盘/菜单/IPC） | 2 天 |
| 4 | 实现本地缓存（SQLite） | 1 天 |
| 5 | 实现 OS 通知 | 0.5 天 |
| 6 | 适配 Web 端 API（用后端 URL 配置） | 1 天 |
| 7 | 桌面端构建（electron-builder） | 0.5 天 |
| 8 | 测试 + 文档 | 1 天 |

### 3.7 收益

- ✅ 复用 Web 端 100% 业务代码
- ✅ 复用后端 LLM Provider 体系（无需重复实现）
- ✅ 桌面端特性：托盘/通知/全局快捷键/离线缓存
- ✅ 与现有 Web 部署互补，不冲突
- ✅ 适合企业内网部署（远程后端模式）

### 3.8 不需要重新实现

- ❌ Provider 注册（直接用后端）
- ❌ 30+ Provider 适配（后端已统一）
- ❌ 记忆/知识库存储（后端 + 文件镜像）
- ❌ Brain 路由（后端已实现）
- ❌ 模型选择（后端 `BrainModelResolver`）

---

## 四、作为 WindowsAppTool 客户端落地

> 现有 [windows_automation](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/resources/scripts/windows_automation) 是 Python + FastAPI 实现，
> **不是桌面应用**。living-agent-desktop 天然适合替代/封装它，作为更完整的 Windows 自动化客户端。

### 4.1 现有实现分析

**目录**：[windows_automation/](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/resources/scripts/windows_automation)

| 文件 | 角色 | 功能 |
|------|------|------|
| [server.py](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/resources/scripts/windows_automation/server.py) | FastAPI 服务 | HTTP API + pywinauto 自动化 |
| [config.json](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/resources/scripts/windows_automation/config.json) | 服务端配置 | 应用列表 + 安全策略 |
| [config.client.example.json](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/resources/scripts/windows_automation/config.client.example.json) | 客户端配置示例 | exe_path + backend |
| [README.md](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/resources/scripts/windows_automation/README.md) | 部署文档 | 单机 + 多节点 |
| [MULTI_NODE_DEPLOY.md](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/resources/scripts/windows_automation/MULTI_NODE_DEPLOY.md) | 多节点文档 | 局域网多 PC 控制 |
| [requirements.txt](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/resources/scripts/windows_automation/requirements.txt) | 依赖 | fastapi/pywinauto/Pillow/psutil |

**技术栈**：
- 后端：FastAPI + uvicorn + pywinauto + psutil + Pillow
- 通信：HTTP REST + 自动注册 + 心跳
- 节点管理：UUID 节点 ID + 60s 心跳
- 部署：Python 脚本 + Windows 计划任务

**已有能力**：
- ✅ 应用启动（launch）：`POST /api/windows/launch`
- ✅ 自动登录：`POST /api/windows/login`
- ✅ 菜单选择：`POST /api/windows/menu`
- ✅ 控件点击：`POST /api/windows/click`
- ✅ 文本输入：`POST /api/windows/type_keys`
- ✅ 文本读取：`POST /api/windows/get_text`
- ✅ 截图：`POST /api/windows/screenshot`
- ✅ 控件树：`GET /api/windows/controls`
- ✅ 关闭应用：`POST /api/windows/close`
- ✅ 健康检查：`GET /health`
- ✅ 多节点注册 + 心跳
- ✅ 会话管理（active_sessions）

**痛点**：
- ❌ 部署复杂（需 Python 环境 + 计划任务 + 防火墙）
- ❌ 配置分散（每台 PC 独立 config.json）
- ❌ 无可视化管理界面（要 curl 调试）
- ❌ 无节点状态可视化
- ❌ 截图查看不便
- ❌ 控件树查看不便
- ❌ 启动/停止需要手动操作
- ❌ 没有日志面板
- ❌ 没有应用配置管理 UI

### 4.2 桌面应用落地方案

**建议**：living-agent-desktop **同时承载虚拟办公室 + WindowsAppTool 客户端**两大功能。

#### 4.2.1 架构整合

```
living-agent-desktop/
├── src/
│   ├── main/                      # Electron 主进程
│   │   ├── index.ts
│   │   ├── window.ts              # 窗口管理
│   │   ├── tray.ts                # 系统托盘
│   │   ├── menu.ts
│   │   ├── ipc.ts
│   │   ├── local-cache.ts
│   │   ├── notifications.ts
│   │   ├── connection.ts          # 后端连接检测
│   │   ├── shortcuts.ts
│   │   ├── win-auto/              # WindowsAppTool 子系统（新增）
│   │   │   ├── service-manager.ts # Python 服务管理
│   │   │   ├── node-registry.ts   # 节点注册
│   │   │   ├── heartbeat.ts       # 心跳发送
│   │   │   ├── app-launcher.ts    # 应用启动
│   │   │   ├── control-driver.ts  # 控件驱动（pywinauto 封装）
│   │   │   ├── screenshot.ts      # 截图（via Python）
│   │   │   └── config.ts          # 客户端配置
│   │   └── bridge.py              # Python 桥接脚本（替代裸 server.py）
│   ├── preload/
│   │   └── index.ts               # 暴露 window.livingAgentAPI
│   └── renderer/                  # 复用 living-agent-service/frontend
│       └── pages/
│           ├── DepartmentDetail/  # 虚拟办公室
│           └── WindowsTool/       # WindowsAppTool 客户端（新增）
│               ├── WinToolHome.tsx       # 首页（节点状态 + 应用列表）
│               ├── NodeManager.tsx       # 节点管理
│               ├── AppConfig.tsx         # 应用配置
│               ├── SessionRunner.tsx     # 会话运行器
│               ├── ControlTree.tsx       # 控件树查看
│               ├── ScreenshotViewer.tsx  # 截图查看
│               └── LogPanel.tsx          # 日志面板
```

#### 4.2.2 与现有 Python 服务的关系

| 维度 | 现有 Python 服务 | living-agent-desktop 集成方案 |
|------|-----------------|------------------------------|
| **服务进程** | 用户手动 `python server.py` | **主进程拉起 + 进程守护**（无需手动） |
| **配置管理** | 手动编辑 `config.json` | **UI 配置** + 写入 `config.json` |
| **启动/停止** | PowerShell `Start-Process` / `Stop-Process` | **按钮一键启停** |
| **节点注册** | Python 启动时自动注册 | **桌面应用启动时自动注册** + 持续心跳 |
| **防火墙配置** | 手动 PowerShell 命令 | **首次运行自动配置**（需管理员权限） |
| **日志查看** | 控制台输出 | **UI 日志面板** |
| **应用启动** | curl POST | **UI 按钮** + 调用 Python API |
| **截图查看** | 保存为 PNG 文件 | **实时预览**（拉流或定期拉取） |
| **控件树查看** | 仅返回 JSON | **树形 UI 渲染** |
| **多节点管理** | 服务器端查看 | **桌面端**直接看所有节点状态 |

#### 4.2.3 推荐迁移路径

**Phase 1：保留 Python 后端，主进程管理**（推荐先做）

- 桌面应用主进程负责：
  - 启动/停止 Python 服务（`spawn('python', ['server.py'])`）
  - 监控 Python 进程（崩溃自动重启）
  - 读写 `config.json`
  - 显示 Python 日志（捕获 stdout/stderr）
- 渲染进程通过 HTTP 调用 Python 服务（同现在）
- 优势：改动小，立即可用

**Phase 2：Python 桥接脚本（`bridge.py`）**

- 新增 `bridge.py`，与 `server.py` 共存
- `bridge.py` 通过 **stdin/stdout JSON-RPC** 与主进程通信（而非 HTTP）
- 主进程通过 `child_process.spawn` 启动，按需调用
- 优势：性能更好（无 HTTP 开销）、状态可追踪
- `server.py` 保留向后兼容（多节点场景仍需要 HTTP）

**Phase 3：Node.js 重写 pywinauto 逻辑**（长期）

- 用 `@nut-tree/nut-js` 或 `node-gyp + Win32 API` 替代 pywinauto
- 优势：无需 Python 依赖、安装更简单
- 风险：Windows API 调用复杂，开发周期长

#### 4.2.4 桌面端 WindowsAppTool 客户端功能

| 功能 | 描述 | 优先级 |
|------|------|--------|
| **节点状态面板** | 显示所有节点（IP/状态/活跃会话/最后心跳） | P0 |
| **应用启动** | 选择节点 + 应用 → 一键启动 + 显示结果 | P0 |
| **登录向导** | 选择节点 → 输入凭证 → 自动登录 | P0 |
| **菜单导航** | 应用启动后选择菜单路径 | P0 |
| **截图查看** | 实时显示目标窗口截图 | P1 |
| **控件树** | 树形 UI 显示当前窗口控件 | P1 |
| **配置管理** | UI 增删改应用列表、exe_path、backend | P1 |
| **日志面板** | 显示 Python 服务 stdout/stderr | P1 |
| **节点注册向导** | 首次运行向导：自动发现 + 注册 | P2 |
| **多语言** | 中/英切换 | P2 |
| **服务进程监控** | 实时显示 Python 进程状态 | P2 |
| **防火墙配置** | 一键开放端口（需管理员） | P2 |
| **会话录制** | 记录操作步骤 + 回放 | P3 |
| **远程协助** | 通过 WebRTC 让管理员远程查看 | P3 |

#### 4.2.5 核心代码示例

**主进程拉起 Python 服务**（`service-manager.ts`）：

```typescript
import { spawn, ChildProcess } from 'child_process';
import { EventEmitter } from 'events';

export class WinAutoServiceManager extends EventEmitter {
  private pyProcess: ChildProcess | null = null;
  private logBuffer: string[] = [];

  start(scriptPath: string, configPath: string): void {
    if (this.pyProcess) {
      this.emit('warn', 'Service already running');
      return;
    }
    this.pyProcess = spawn('python', [scriptPath], {
      cwd: path.dirname(scriptPath),
      env: { ...process.env, WIN_AUTO_CONFIG: configPath }
    });
    
    this.pyProcess.stdout?.on('data', (data) => {
      const line = data.toString();
      this.logBuffer.push(line);
      this.emit('log', line);
    });
    this.pyProcess.stderr?.on('data', (data) => {
      this.emit('error', data.toString());
    });
    this.pyProcess.on('exit', (code) => {
      this.emit('exit', code);
      this.pyProcess = null;
    });
  }

  async stop(): Promise<void> {
    if (!this.pyProcess) return;
    this.pyProcess.kill('SIGTERM');
    // 等待优雅退出
    await new Promise(r => setTimeout(r, 2000));
    if (this.pyProcess) this.pyProcess.kill('SIGKILL');
  }

  isRunning(): boolean {
    return this.pyProcess !== null;
  }
}
```

**Preload 暴露**（`preload/index.ts`）：

```typescript
import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('livingAgentAPI', {
  // 虚拟办公室
  checkBackend: () => ipcRenderer.invoke('backend:check'),
  
  // WindowsAppTool
  winAuto: {
    startService: (scriptPath: string, configPath: string) => 
      ipcRenderer.invoke('winauto:start', scriptPath, configPath),
    stopService: () => ipcRenderer.invoke('winauto:stop'),
    isRunning: () => ipcRenderer.invoke('winauto:status'),
    onLog: (callback: (line: string) => void) => {
      ipcRenderer.on('winauto:log', (_, line) => callback(line));
    },
    
    // HTTP 透传到 Python 服务
    callApi: (endpoint: string, method: string, data?: any) =>
      ipcRenderer.invoke('winauto:call', endpoint, method, data)
  }
});
```

**前端 React 组件**（`NodeManager.tsx`）：

```tsx
import { useEffect, useState } from 'react';

interface Node {
  nodeId: string;
  ip: string;
  port: number;
  status: 'online' | 'offline';
  activeSessions: number;
  lastHeartbeat: string;
  applications: string[];
}

export function NodeManager() {
  const [nodes, setNodes] = useState<Node[]>([]);
  const [serviceRunning, setServiceRunning] = useState(false);

  useEffect(() => {
    // 定期拉取节点状态
    const timer = setInterval(async () => {
      const data = await fetch('/api/windows-automation/nodes').then(r => r.json());
      setNodes(data);
    }, 10000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="node-manager">
      <header>
        <h2>Windows 自动化节点</h2>
        <button onClick={() => window.livingAgentAPI.winAuto.startService(...)}>
          {serviceRunning ? '运行中' : '启动服务'}
        </button>
      </header>
      <div className="node-grid">
        {nodes.map(node => (
          <NodeCard key={node.nodeId} node={node} />
        ))}
      </div>
    </div>
  );
}
```

### 4.3 收益

- ✅ **统一管理入口**：虚拟办公室 + WindowsAppTool 一体化桌面端
- ✅ **零 Python 部署**：桌面应用打包 Python 解释器（PyInstaller）
- ✅ **可视化运维**：节点状态、截图、控件树、日志一目了然
- ✅ **降低使用门槛**：管理员无需学习 Python + curl
- ✅ **远程办公支持**：通过 VPN 远程连接内网节点
- ✅ **完全复用后端 LLM 能力**（机器人 + 自动化联动）
- ✅ **向 Hermes 学习**：Hermes 的 Skill 系统、IPC 模式可借鉴

### 4.4 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| Python 打包体积大（~50MB） | 安装包大 | 后续可重写为 Node.js + Win32 API |
| pywinauto 仅 Windows | 限制 | 明确仅 Windows 桌面端（其他平台用 Web 端） |
| 跨平台打包复杂 | 维护成本 | 初期只做 Windows x64，其他平台暂缓 |
| Windows UAC | 部分操作需管理员 | 首次启动提示获取权限 |
| 杀毒软件误报 | 部署问题 | 申请代码签名证书、白名单 |

### 4.5 实施步骤

| 步骤 | 内容 | 工作量 |
|------|------|--------|
| 1 | 初始化 `living-agent-desktop/` 项目，Electron + Vite + TS | 1 天 |
| 2 | 主进程：窗口/托盘/菜单 | 2 天 |
| 3 | 主进程：Python 服务管理（service-manager.ts） | 2 天 |
| 4 | 主进程：IPC 桥接 | 1 天 |
| 5 | PyInstaller 打包 Python 解释器 | 2 天 |
| 6 | 前端：NodeManager 组件 | 1 天 |
| 7 | 前端：SessionRunner 组件 | 2 天 |
| 8 | 前端：ControlTree + ScreenshotViewer | 2 天 |
| 9 | 测试 + 文档 | 1 天 |
| **总计** | | **~14 天** |

---

## 五、规范开发细节（依据项目核心文档）

> 严格遵循 [权限与入口矩阵.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/权限与入口矩阵.md)、
> [MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md)、
> [API_REFERENCE.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/references/API_REFERENCE.md)、
> [CODE_STRUCTURE_AND_FILE_GUIDE.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/CODE_STRUCTURE_AND_FILE_GUIDE.md)。

### 5.1 API 响应格式（必遵守）

**所有 API 调用必须使用 `ApiResponse<T>` 格式**（来自 [ApiResponse.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/common/ApiResponse.java)）：

```typescript
interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
  errorDescription: string | null;
}

// 服务端使用：ApiResponse.ok(data) / ApiResponse.err(error, description)
```

**前端调用封装**（必须使用 `request<T>()` 统一处理）：

```typescript
async function request<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`/api${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${getToken()}`,
      ...options?.headers
    }
  });
  const json: ApiResponse<T> = await res.json();
  if (!json.success) {
    throw new ApiError(json.error!, json.errorDescription!);
  }
  return json.data;
}
```

### 5.2 WebSocket 通道（与权限矩阵对齐）

> **桌面端不得绕过权限矩阵**。通道选择完全由登录状态、身份、对象 origin 决定。

| 通道 | 处理器 | 桌面端用途 |
|------|--------|----------|
| `/ws/agent` | `AgentWebSocketHandler` | 仅个人助手 `origin=personal` 聊天 |
| `/ws/dept/{dept}` | `DepartmentWebSocketHandler` | 部门大脑对话 |
| `/ws/enterprise` | `DepartmentWebSocketHandler` | 董事长频道 |
| `/ws/public` | `DepartmentWebSocketHandler` | 未登录闲聊 / 固定员工降级 |

**WebSocket 连接格式**：
```
ws://localhost:8382/ws/agent?token={authToken}&agentId={agentId}
ws://localhost:8382/ws/dept/{dept}?token={authToken}
```

**桌面端必须遵守的规则**：

1. **未登录用户**：桌面端只能连 `/ws/public`，不可显示部门/智能体入口
2. **固定员工（origin=fixed）**：必须自动降级到 `/ws/public`，**禁止走 `/ws/agent`**
3. **个人助手（origin=personal）**：允许 `/ws/agent` 直连
4. **董事长/FULL**：可访问所有 `/ws/dept/*`
5. **部门负责人/普通员工**：仅限本部门 `/ws/dept/{department}`

**WebSocket 消息类型**（从已有后端实现提取）：

| 类型 | 方向 | 说明 |
|------|------|------|
| `chat` / `text` | C→S | 用户消息 |
| `chat_response` / `text_response` | S→C | 文本响应 |
| `audio_full` | C→S | 语音完整链路（仅 `/ws/agent`） |
| `employee_task_update` | S→C | 员工任务状态变更 |
| `execution_event` / `execution_progress` | S→C | 任务执行事件 |
| `block` / `BLOCKED` | S→C | 任务阻塞 |
| `error` | S→C | 错误 |

### 5.3 LLM Provider 接入（模型层规范）

**严格遵循 [CODE_STRUCTURE_AND_FILE_GUIDE.md L684-715](file:///f:/SoarCloudAI/docker/living-agent-service/docs/CODE_STRUCTURE_AND_FILE_GUIDE.md)**：

| 桌面端能力 | 实现位置 | 禁止点 |
|----------|---------|--------|
| Provider 类型枚举 | 后端 `provider/Provider.java` | ❌ 桌面端不要硬编码 Provider 名 |
| 客户端创建 | 后端 `model/pool/LlmClientFactory` | ❌ 桌面端不要直接调 Provider API |
| 模型选择 | 后端 `BrainModelResolver` | ❌ 桌面端不要前端决策 |
| 任务执行 | 后端 `autonomy/` | ❌ 桌面端不要绕过权限执行 |
| 工具调用 | 后端 `ToolRegistry` | ❌ 桌面端不要直接调 Tool |

**结论**：桌面端**永远不直接连 LLM Provider**，所有 LLM 调用必须通过 WebSocket 或 REST API 走后端。

### 5.4 Windows 自动化 API 规范

**严格使用 [API_REFERENCE.md §36](file:///f:/SoarCloudAI/docker/living-agent-service/docs/references/API_REFERENCE.md) 定义**：

#### 5.4.1 客户端调用（Python 服务 → 后端）

```http
POST /api/windows-automation/nodes/register
POST /api/windows-automation/nodes/{nodeId}/heartbeat
```

**注册请求体**（`server.py` 启动时调用）：
```json
{
  "node_id": "node-uuid",
  "ip": "192.168.1.101",
  "port": 8765,
  "hostname": "FINANCE-PC01",
  "cpu_count": 8,
  "memory_gb": 16.0,
  "applications": ["金蝶KIS", "用友U8"],
  "description": "财务电脑01",
  "tenant_id": "tenant-001",
  "user_id": "user-001"
}
```

**心跳请求体**（每 60 秒一次）：
```json
{
  "status": "online",
  "ip": "192.168.1.101",
  "active_sessions": 0
}
```

#### 5.4.2 管理端调用（桌面端 React → 后端）

```http
GET    /api/windows-automation/nodes              # 列出所有节点
GET    /api/windows-automation/nodes/{nodeId}/status  # 检查节点状态
PUT    /api/windows-automation/nodes/{nodeId}     # 启用/禁用
DELETE /api/windows-automation/nodes/{nodeId}     # 删除节点
```

**节点对象**：
```typescript
interface WindowsNode {
  node_id: string;
  ip_address: string;
  port: number;
  hostname: string;
  cpu_count: number;
  memory_gb: number;
  description: string;
  status: 'online' | 'offline';
  last_heartbeat: string;
  registered_at: string;
  tenant_id: string;
  user_id: string;
  enabled: boolean;
}
```

#### 5.4.3 业务规则（必遵守）

- **心跳超时 90 秒视为离线**
- **节点 ID 由客户端生成 UUID 并持久化到 `node_id.txt`**
- **节点启用/禁用会同步到 `WindowsAppTool` 运行时**
- **多租户隔离**：必须传 `tenant_id`

### 5.5 鉴权与会话

**桌面端登录流程**（与 Web 端一致）：

1. 启动时读取 `secureStorage` 中的 token
2. 调用 `GET /api/auth/me` 验证 token 有效性
3. 失败 → 跳转 Login
4. 成功后维持 WebSocket 连接

**Token 存储**（Electron 推荐）：

```typescript
import { safeStorage } from 'electron';

// 主进程
const encrypted = safeStorage.encryptString(token);
store.set('auth.token', encrypted);

// 渲染进程通过 IPC 获取（不要直接访问 safeStorage）
const token = await window.livingAgentAPI.auth.getToken();
```

**权限检查**（前端必须做、后端必须再做一次）：

| 桌面端功能 | 所需权限 |
|----------|---------|
| 虚拟办公室 | 任何已登录用户 |
| 部门大脑对话 | 部门匹配或 FULL |
| WindowsAppTool 节点管理 | 管理员或工具管理员 |
| 发起自动化任务 | 工具权限 + 对应部门权限 |

### 5.6 ID 命名规范（与项目一致）

```
员工: employee://human/{authProvider}/{accountId}  或  employee://digital/{domain}/{name}/{instance}
神经元: neuron://{domain}/{name}/{instance}
通道: channel://{scope}/{name}
```

**桌面端涉及**：
- 显示员工 ID 时保留 `employee://` 前缀
- WebSocket 连接时使用完整 neuronId

### 5.7 文件路径与目录结构

**桌面端项目结构**（建议）：

```
living-agent-desktop/
├── src/
│   ├── main/                          # Electron 主进程
│   │   ├── index.ts                   # 入口
│   │   ├── window.ts                  # 窗口管理
│   │   ├── tray.ts                    # 系统托盘
│   │   ├── menu.ts                    # 菜单栏
│   │   ├── ipc.ts                     # IPC handlers
│   │   ├── connection.ts              # 后端连接检测
│   │   ├── shortcuts.ts               # 全局快捷键
│   │   ├── auth.ts                    # 鉴权 + safeStorage
│   │   ├── notifications.ts           # OS 通知
│   │   ├── local-cache.ts             # 本地 SQLite 缓存
│   │   ├── api-client.ts              # 后端 REST 客户端
│   │   ├── ws-client.ts               # 后端 WebSocket 客户端
│   │   ├── win-auto/                  # WindowsAppTool 子系统
│   │   │   ├── service-manager.ts     # Python 服务管理
│   │   │   ├── node-registry.ts       # 节点注册
│   │   │   ├── heartbeat.ts           # 心跳发送
│   │   │   ├── app-launcher.ts        # 应用启动
│   │   │   ├── control-driver.ts      # 控件驱动
│   │   │   ├── screenshot.ts          # 截图
│   │   │   └── config.ts              # 客户端配置
│   │   └── bridge.py                  # Python 桥接脚本（可选）
│   ├── preload/
│   │   └── index.ts                   # 暴露 window.livingAgentAPI
│   ├── renderer/                      # 复用 living-agent-service/frontend
│   │   └── pages/
│   │       ├── DepartmentDetail/      # 虚拟办公室
│   │       └── WindowsTool/           # WindowsAppTool 客户端
│   ├── shared/                        # 主进程 + 渲染进程共享类型
│   │   ├── types.ts
│   │   └── constants.ts
│   └── types/
│       └── electron-api.d.ts          # window.livingAgentAPI 类型声明
├── electron.vite.config.ts
├── electron-builder.yml
├── package.json
└── README.md
```

### 5.8 错误处理与日志

**统一错误格式**（与后端对齐）：

```typescript
class ApiError extends Error {
  constructor(
    public code: string,
    public description: string,
    public statusCode?: number
  ) {
    super(`[${code}] ${description}`);
  }
}
```

**日志规范**（桌面端）：

| 级别 | 用途 | 输出位置 |
|------|------|---------|
| `error` | 致命错误、连接失败 | 主进程日志 + 用户通知 |
| `warn` | 恢复性错误（如重连） | 主进程日志 |
| `info` | 关键节点（启动、连接、停止） | 主进程日志 |
| `debug` | 详细调试 | 仅 dev 模式 |

**不向渲染进程输出敏感信息**（token、API key、用户隐私）。

### 5.9 桌面端特有的合规要求

#### 5.9.1 操作系统权限

- **Windows 通知**：需要 `electron-builder` 配置 `"notifications"` 权限
- **全局快捷键**：需要用户首次明确授权
- **开机自启**：需要 `app.setLoginItemSettings()` 调用
- **托盘图标**：使用 `app.dock.hide()` (macOS) / `tray.setContextMenu()` (Windows)

#### 5.9.2 代码签名

- **必须**申请 Windows 代码签名证书（避免 SmartScreen 拦截）
- macOS 公证（避免 Gatekeeper 拦截）
- 内部测试可暂时跳过，但生产**必须**签名

#### 5.9.3 隐私与安全

- **不收集**用户聊天内容、任务数据
- **不发送**任何数据到非项目后端的地址
- **后端地址**可由用户在 Settings 配置（避免硬编码）
- **离线模式**下停止所有外部连接
- **所有网络请求**走系统代理设置

### 5.10 与 living-agent-service 的代码复用

#### 5.10.1 复用的前端代码

```typescript
// electron.vite.config.ts 复用 frontend
export default defineConfig({
  renderer: {
    root: '../living-agent-service/frontend',
    build: {
      rollupOptions: {
        input: path.resolve(__dirname, '../living-agent-service/frontend/index.html')
      }
    }
  }
});
```

**可直接复用的文件**：
- `frontend/src/services/api.ts` - 改 baseURL 即可
- `frontend/src/components/MarkdownRenderer.tsx`
- `frontend/src/i18n/*`
- `frontend/src/utils/theme.ts`
- `frontend/src/stores/index.ts` - Zustand 状态
- `frontend/src/types/*` - 业务类型

**需要适配的文件**：
- `frontend/src/main.tsx` - 检测是否在 Electron 环境
- `frontend/src/services/apiBase.ts` - 桌面端走 IPC 而非 fetch

#### 5.10.2 不复用的代码

- `frontend/src/pages/Login.tsx` - 桌面端可走 OAuth/SSO
- `frontend/src/pages/Layout.tsx` - 桌面端用 ApplicationWindow

#### 5.10.3 Python 代码策略

- **保留** `server.py`（向后兼容多节点）
- **新增** `bridge.py`（stdin/stdout JSON-RPC）用于主进程直接调用
- **可选** `wrapper.py`（打包所有依赖为 exe）

### 5.11 验证标准（开发完成判定）

#### 5.11.0 双向产物保存机制（重要！）

> **桌面端的核心增量价值**：用户在桌面端发起的对话/任务，产物可以**同时**保存到：
> 1. **服务器**（默认，与 Web 端一致，方便固定数字员工后续操作）
> 2. **用户指定本地文件夹**（可选，桌面端独有）

详见 [§六、本地产物保存机制](#六本地产物保存机制)。

#### 5.11.1 功能完整性

- [ ] 桌面端可登录、查看部门大脑对话
- [ ] 桌面端可管理 Windows 自动化节点（增删改查）
- [ ] 桌面端可启动/停止 Python 服务
- [ ] 桌面端可查看节点状态、截图、控件树
- [ ] 桌面端支持语音（语音开关受前端的 Settings 控制）
- [ ] 桌面端遵守权限矩阵（未登录/登录/部门匹配）

#### 5.11.2 兼容性

- [ ] 不影响现有 Web 端（独立应用）
- [ ] 复用后端 LLM/工具能力（不重复实现）
- [ ] 支持 Windows 10/11 x64

#### 5.11.3 代码规范

- [ ] TypeScript strict 模式
- [ ] ESLint + Prettier
- [ ] 不硬编码模型名、Provider 名
- [ ] 所有 LLM 调用走后端
- [ ] 不绕过权限检查
- [ ] 日志按级别规范输出

---

## 六、本地产物保存机制

> 桌面应用的核心增量价值之一：**用户可指定本地文件夹，保存对话/任务产物到本地**。
> 服务器保存照常（便于固定数字员工后续协作与追溯）。

### 6.1 设计目标

| 目标 | 说明 |
|------|------|
| **服务器照常保存** | 默认行为，与 Web 端一致，方便固定数字员工继续操作 |
| **本地可指定保存** | 用户在桌面端设置一个本地文件夹，所有产物同步保存 |
| **可关闭** | 用户可关闭本地保存（仅服务器保存） |
| **可多端同步** | 本地文件夹可挂载 OneDrive/坚果云/网盘实现跨设备同步 |
| **离线可访问** | 本地保存的产物在断网时仍可查看 |
| **隐私可控** | 敏感产物（合同、薪酬）可指定加密保存 |

### 6.2 保存范围

| 产物类型 | 服务器路径 | 本地路径 | 说明 |
|---------|----------|---------|------|
| **HTML 产物** | `data/artifacts/by-execution/{execId}/{empCode}/*.html` | `{localDir}/artifacts/{year}/{month}/{execId}/*.html` | 网页/界面原型 |
| **Markdown 报告** | 同上 + `.md` | `{localDir}/artifacts/.../*.md` | 分析报告/文档 |
| **代码产物** | `data/artifacts/.../*.java` 等 | `{localDir}/artifacts/.../*` | 生成的代码 |
| **截图** | 内存/Python 服务 | `{localDir}/screenshots/{execId}/*.png` | WindowsAppTool 截图 |
| **对话历史** | `data/conversations/.../events.jsonl` | `{localDir}/conversations/{year}/{month}/{chatId}/events.jsonl` | 完整对话 |
| **执行回执** | `data/receipts/{execId}.json` + `data/receipts/by-employee/{code}/{execId}.json` | `{localDir}/receipts/{year}/{month}/{execId}.json` | Receipt 副本 |
| **导出文件** | 临时 | `{localDir}/exports/{type}/{execId}/` | 用户的导出操作 |

### 6.3 触发时机

| 时机 | 行为 |
|------|------|
| **任务执行完成** | 服务器保存完成后，本地主进程监听 WebSocket 推送 `employee_task_update(status=COMPLETED)` → 拉取产物 → 写入本地 |
| **会话结束** | 用户点击"导出对话" → 写入本地（同时保留服务器） |
| **手动触发** | 用户在产物预览面板点击"保存到本地" |
| **定期同步** | 每 5 分钟全量同步一次（保证本地与服务器一致） |
| **应用启动** | 拉取最近 24h 增量产物 |
| **应用退出** | 同步未完成的任务 |

### 6.4 配置 UI

**Settings → 本地保存设置**：

```
┌─ 本地保存设置 ───────────────────────────────┐
│                                              │
│  [✓] 启用本地产物保存                        │
│                                              │
│  保存路径：[ D:\LivingAgent\我的产物    ]  │
│                                              [浏览...]                                       │
│                                              │
│  保存范围：                                  │
│    [✓] HTML 产物                             │
│    [✓] Markdown 报告                         │
│    [✓] 代码产物                              │
│    [✓] 对话历史                              │
│    [✓] 执行回执                              │
│    [ ] 截图（体积较大，默认不启用）          │
│                                              │
│  同步策略：                                  │
│    ( ) 仅保存当前设备的产物                  │
│    (•) 同步所有已登录设备（通过云盘）        │
│                                              │
│  加密：[  ] 敏感产物加密保存（密码：********）│
│                                              │
│  容量限制：[ 10 GB ▾ ] 超出后只保留最近 [30 天] │
│                                              │
│                              [应用]  [取消]  │
└──────────────────────────────────────────────┘
```

### 6.5 主进程实现

#### 6.5.1 配置存储（`local-save-config.ts`）

```typescript
interface LocalSaveConfig {
  enabled: boolean;
  basePath: string;                        // D:\LivingAgent\我的产物
  scopes: {
    artifacts: boolean;                    // HTML/Markdown/代码
    conversations: boolean;                // 对话历史
    receipts: boolean;                     // 执行回执
    screenshots: boolean;                  // 截图
  };
  syncStrategy: 'local-only' | 'cloud-sync';
  encryption: {
    enabled: boolean;
    password?: string;                      // 主进程内存，不落盘
  };
  capacity: {
    maxBytes: number;                       // 10 GB
    retentionDays: number;                  // 30 天
  };
}

class LocalSaveConfigService {
  load(): LocalSaveConfig;
  save(config: LocalSaveConfig): void;
  getDefaultPath(): string;                 // ~/Documents/LivingAgent
}
```

#### 6.5.2 同步服务（`local-save-sync.ts`）

```typescript
import { app, dialog, shell } from 'electron';
import * as path from 'path';
import * as fsp from 'fs/promises';

class LocalSaveSyncService extends EventEmitter {
  private config: LocalSaveConfig;
  private syncing = false;
  
  /**
   * 处理来自后端的产物保存事件
   * WebSocket 收到 employee_task_update(status=COMPLETED) 后调用
   */
  async onArtifactReady(artifact: ArtifactEvent): Promise<void> {
    if (!this.config.enabled) return;
    if (!this.config.scopes.artifacts) return;
    
    const targetPath = this.resolveTargetPath(artifact);
    await this.copyWithDeduplication(artifact.sourcePath, targetPath);
    this.emit('saved', { path: targetPath, size: artifact.size });
  }
  
  /**
   * 全量同步 - 应用启动或定时任务
   */
  async fullSync(): Promise<SyncResult> {
    // 1. 通过 REST API 拉取增量产物
    const recent = await this.fetchRecentArtifacts();
    
    // 2. 复制到本地
    let savedCount = 0, errorCount = 0;
    for (const artifact of recent) {
      try {
        await this.onArtifactReady(artifact);
        savedCount++;
      } catch (e) {
        errorCount++;
      }
    }
    
    // 3. 清理过期
    await this.cleanupExpired();
    
    return { savedCount, errorCount };
  }
  
  private resolveTargetPath(artifact: ArtifactEvent): string {
    const date = new Date(artifact.completedAt);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    return path.join(
      this.config.basePath,
      'artifacts',
      String(year),
      month,
      artifact.executionId,
      artifact.fileName
    );
  }
  
  private async copyWithDeduplication(src: string, dest: string): Promise<void> {
    await fsp.mkdir(path.dirname(dest), { recursive: true });
    // SHA-256 校验：避免重复保存
    const srcHash = await this.sha256(src);
    if (await this.exists(dest)) {
      const destHash = await this.sha256(dest);
      if (srcHash === destHash) return;  // 已存在相同内容
    }
    await fsp.copyFile(src, dest);
  }
  
  private async cleanupExpired(): Promise<void> {
    // 清理超过 retentionDays 的文件
  }
}
```

#### 6.5.3 加密支持（`local-save-crypto.ts`）

```typescript
import * as crypto from 'crypto';

class LocalSaveCrypto {
  /**
   * AES-256-GCM 加密单个文件
   * 密码从用户输入派生（PBKDF2）
   */
  async encryptFile(srcPath: string, destPath: string, password: string): Promise<void> {
    const key = crypto.pbkdf2Sync(password, 'living-agent-salt', 100000, 32, 'sha256');
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    
    const input = await fsp.readFile(srcPath);
    const encrypted = Buffer.concat([cipher.update(input), cipher.final()]);
    const authTag = cipher.getAuthTag();
    
    // 文件格式: [16B IV][16B AuthTag][加密内容]
    await fsp.writeFile(destPath, Buffer.concat([iv, authTag, encrypted]));
  }
}
```

### 6.6 Preload 暴露

```typescript
// preload/index.ts
contextBridge.exposeInMainWorld('livingAgentAPI', {
  // ... 现有 API
  
  localSave: {
    getConfig: () => ipcRenderer.invoke('localsave:get-config'),
    setConfig: (config: LocalSaveConfig) => ipcRenderer.invoke('localsave:set-config', config),
    choosePath: () => ipcRenderer.invoke('localsave:choose-path'),
    openFolder: () => ipcRenderer.invoke('localsave:open-folder'),
    triggerSync: () => ipcRenderer.invoke('localsave:sync'),
    getStats: () => ipcRenderer.invoke('localsave:stats'),
    onSaved: (callback: (info: SavedInfo) => void) => {
      ipcRenderer.on('localsave:saved', (_, info) => callback(info));
    }
  }
});
```

### 6.7 前端组件

#### 6.7.1 LocalSaveSettings（`pages/Settings/LocalSave.tsx`）

- 启用开关、路径选择器、范围复选框
- 容量/保留期设置
- "立即同步"按钮
- 统计信息：已用空间、文件数、最近保存时间

#### 6.7.2 LocalSaveIndicator（`components/LocalSaveIndicator.tsx`）

- 工具栏小图标，显示"本地产物已同步 ✓"或"未启用"
- 点击展开 → 统计 + 立即同步

#### 6.7.3 ProductPreviewPanel 增强

- 在产物预览面板增加"保存到本地"按钮
- 产物卡片显示"已保存到本地"标记

### 6.8 与现有 receipt/artifact 机制的关系

| 现有机制 | 桌面端增强 |
|---------|----------|
| `ToolBackedEmployeeTaskExecutor.saveArtifactFile()` 写服务器 `data/artifacts/` | **保留服务器写入**，主进程同时复制到本地 |
| `FileBasedEmployeeExecutionReceiptService.persistExecution()` 写服务器 receipt | **保留服务器写入**，主进程订阅事件后复制 |
| WebSocket `employee_task_update` 推送 | 主进程监听后**触发本地保存** |
| `KnowledgeFileMirrorService` L1/L2/L3 镜像 | 主进程可**同步镜像到本地**（可选） |

### 6.9 路径模板（与 Docker 卷对齐）

| 用途 | 服务器路径 | 本地路径 |
|------|----------|---------|
| 产物（artifacts） | `data/artifacts/by-execution/{execId}/{empCode}/{file}` | `{baseDir}/artifacts/{year}/{month}/{execId}/{file}` |
| Receipt | `data/receipts/by-employee/{empCode}/{execId}.json` | `{baseDir}/receipts/{year}/{month}/{empCode}/{execId}.json` |
| 对话 | `data/conversations/{chatId}/events.jsonl` | `{baseDir}/conversations/{year}/{month}/{chatId}/events.jsonl` |
| L1 知识 | `data/personal-knowledge/{empCode}/experiences.jsonl` | `{baseDir}/knowledge/personal/{empCode}/experiences.jsonl` |
| 截图 | 内存/Python 服务 | `{baseDir}/screenshots/{year}/{month}/{execId}/{file}.png` |

### 6.10 与云盘同步

**OneDrive / 坚果云 / Google Drive 集成方案**：

- 用户在 Settings 配置 `{baseDir}` 指向云盘同步文件夹
  ```
  D:\OneDrive\LivingAgent\
  C:\Users\xxx\Nutstore\LivingAgent\
  ```
- 桌面端按本地产物保存
- 云盘客户端自动同步到云端
- 其他设备登录后**拉取增量**

**与 `KnowledgeFileMirrorService` 协作**：

```
服务器 data/personal-knowledge/   ←——后端写入—— Living Agent Service
                                       ↓ (WebSocket 事件)
本地 {baseDir}/knowledge/personal/ ←——主进程复制—— living-agent-desktop
                                       ↓
云盘 OneDrive 同步                 ←——客户端——  OneDrive
                                       ↓
其他设备下载                       ←——客户端——  另一台 living-agent-desktop
```

### 6.11 安全与隐私

| 风险 | 对策 |
|------|------|
| **本地文件泄露** | 启用加密（AES-256-GCM），密码仅在内存 |
| **云盘泄露** | 敏感产物单独标记 `sensitive=true`，加密后再上传云盘 |
| **权限泄露** | 本地文件夹权限 700（仅当前用户） |
| **容量爆炸** | 容量限制 + 自动清理过期文件 |
| **路径注入** | 路径模板参数化，`executionId` 用 `sanitizePath()` 处理 |
| **误删** | 不主动删除服务器文件，本地清理仅影响本地副本 |

### 6.12 实施步骤

| 步骤 | 内容 | 工作量 |
|------|------|--------|
| 1 | `LocalSaveConfig` + 默认值 | 0.5 天 |
| 2 | `LocalSaveSyncService` 主进程 | 2 天 |
| 3 | WebSocket 事件订阅 | 0.5 天 |
| 4 | SHA-256 去重 + 容量/保留期清理 | 1 天 |
| 5 | 加密模块（`LocalSaveCrypto`） | 1 天 |
| 6 | Settings UI（`LocalSaveSettings`） | 1 天 |
| 7 | 产物面板"保存到本地"按钮 | 0.5 天 |
| 8 | Indicator 组件 + 统计信息 | 0.5 天 |
| 9 | 启动/退出/定时同步 | 0.5 天 |
| 10 | 测试 + 文档 | 1 天 |
| **总计** | | **~8.5 天** |

### 6.13 验证标准

#### 功能验证

- [ ] 用户可在 Settings 配置本地保存路径
- [ ] 任务完成时，产物同时保存到服务器和本地
- [ ] 对话历史可手动导出到本地
- [ ] 本地路径下有按 `年/月` 组织的目录
- [ ] 关闭本地产物保存后，服务器仍正常保存
- [ ] 容量限制生效（超出后只保留最近 N 天）
- [ ] 加密模式下，文件无法直接读取

#### 兼容性验证

- [ ] 与 Web 端数据兼容（同一后端、同一格式）
- [ ] 与固定数字员工协作（服务器数据完整）
- [ ] 跨设备同步（通过 OneDrive/坚果云）

#### 性能验证

- [ ] 单产物保存 < 100ms
- [ ] 全量同步 100 条产物 < 5s
- [ ] 不影响 WebSocket 主链路延迟

### 6.14 与现有架构的契合度

| 现有项目设计 | 本地保存的契合 |
|----------|--------------|
| Docker `data/` 卷挂载 | 桌面端可**复制**这些目录到本地 |
| `data/personal-knowledge/{empCode}/` | 桌面端可同步 L1 知识到本地 |
| `data/artifacts/by-employee/{empCode}/` | 桌面端可同步自己的产物 |
| WebSocket `employee_task_update` | 主进程**订阅事件触发保存** |
| `KnowledgeFileMirrorService` L1/L2/L3 | 桌面端可**镜像到本地** |
| 权限矩阵 | 桌面端保存走**与 Web 同等权限**（不绕过） |
| ID 命名规范（employee://、neuron://、channel://） | 桌面端**保留完整 ID** |

### 6.15 决策点（已确认 ✅）

#### 决策 1：默认本地路径 + 用户可修改

**采用方案**：桌面应用安装后提供默认路径，但用户可自由修改。

```
┌─ 默认路径策略 ─────────────────────────────────┐
│  Windows: C:\Users\{username}\Documents\LivingAgent    │
│  macOS:   ~/Documents/LivingAgent                     │
│  Linux:   ~/Documents/LivingAgent                     │
│                                                    │
│  用户可在 Settings → 本地保存设置 → 保存路径 中   │
│  通过"浏览..."按钮自由选择任意文件夹。            │
│                                                    │
│  ✅ 默认值开箱即用                                 │
│  ✅ 用户随时可调整                                 │
│  ✅ 移动到 OneDrive/坚果云 等云盘文件夹可实现     │
│     跨设备同步（不强制集成云盘 SDK）               │
└────────────────────────────────────────────────────┘
```

**实现细节**：
- `LocalSaveConfig.getDefaultPath()` 根据 `app.getPath('documents')` + `/LivingAgent` 计算
- 首次启动写入默认值到配置文件
- 用户修改后立即生效（不重启）
- 修改前提示：路径不存在时自动创建；路径已存在时检测是否为空

#### 决策 2：不实现加密功能 ✅

**理由**：本地保存是在用户自己的电脑/文件夹下，等同于用户的"我的文档"性质，不强制加密。

```
┌─ 安全策略调整 ───────────────────────────────────┐
│  ❌ 不实现 AES-256-GCM 加密                       │
│  ❌ 不实现 PBKDF2 密码派生                        │
│  ❌ 不需要 LocalSaveCrypto 模块                   │
│                                                    │
│  ✅ 仅依靠操作系统文件夹权限（默认 700）          │
│  ✅ 依靠用户的全盘加密（BitLocker/FileVault）     │
│  ✅ 不增加 LocalSaveConfig.encryption 字段         │
└────────────────────────────────────────────────────┘
```

**UI 调整**（`LocalSaveSettings` 页面）：
- **删除** "加密" 复选框
- **删除** "密码" 输入框
- 简化说明文字："本地产物保存到您电脑上的指定文件夹，安全性依赖操作系统账户。"

**v2 加密需求**（如未来需要）：
- 企业用户（部门敏感数据）→ 管理员可推送加密策略
- 个人用户（私密内容）→ 可选启用
- 当前版本保持简洁

#### 决策 3：不实现云盘集成 ✅

**理由**：当前阶段专注"本地 + 服务器"双保存。云盘同步由用户通过操作系统/第三方客户端自行处理。

```
┌─ 云盘策略 ────────────────────────────────────────┐
│  ❌ 不内嵌 OneDrive SDK                            │
│  ❌ 不内嵌 坚果云 SDK                              │
│  ❌ 不内嵌 Google Drive SDK                        │
│  ❌ 不实现"云盘登录"流程                           │
│                                                    │
│  ✅ 用户可自行将默认路径改为云盘同步文件夹：      │
│     - D:\OneDrive\LivingAgent                     │
│     - D:\Nutstore\LivingAgent                     │
│     - ~/Google Drive/LivingAgent                  │
│  ✅ 云盘客户端（已安装在系统上）会自动同步        │
│  ✅ 桌面端不感知云盘存在（透明）                  │
└────────────────────────────────────────────────────┘
```

**配置示例**：

```typescript
// 用户在 Settings 中可输入任意路径，包括云盘路径
const userConfig: LocalSaveConfig = {
  enabled: true,
  basePath: 'D:\\OneDrive\\LivingAgent',  // 用户的 OneDrive 文件夹
  scopes: { artifacts: true, conversations: true, receipts: true, screenshots: false },
  syncStrategy: 'local-only',  // 仅本地，不感知云盘
  capacity: { maxBytes: 10 * 1024 * 1024 * 1024, retentionDays: 30 }
};
```

**未来扩展**（如需要）：
- v2：可选"云盘直连"（OAuth 流程上传）
- v2：与"个人知识库"打通（云端知识库同步）

#### 决策 4：服务器优先，Web 端可直接打开服务器产物 ✅

**理由**：服务器是数据主源，Web 端和桌面端都通过权限登录访问，服务器产物始终是最新版本。

```
┌─ 优先级与一致性策略 ──────────────────────────────┐
│  数据主源：服务器（Docker 数据卷）                 │
│                                                    │
│  保存优先级：                                      │
│    1. 服务器保存（必须，与 Web 端一致）            │
│    2. 本地保存（可选，桌面端独有）                 │
│                                                    │
│  读取优先级：                                      │
│    1. Web 端：直接打开服务器产物（不下载到本地）  │
│    2. 桌面端：                                    │
│       - 默认从服务器读取（保证最新）               │
│       - 本地副本仅用于：                           │
│         * 离线访问（断网时）                       │
│         * 导出/备份（用户主动操作）                │
│       - 读取时优先服务器，失败时回退本地           │
└────────────────────────────────────────────────────┘
```

**Web 端行为**（不变）：
- 产物预览、下载、查看：直接通过后端 API 访问 `data/artifacts/`
- 不受桌面端本地保存影响
- 用户登录后即可查看最新产物

**桌面端行为**（增强）：
- **正常情况下**：从服务器读取（保证最新）
- **服务器不可达时**：回退到本地副本
- **离线模式**：仅显示本地副本
- **导出操作**：默认保存到本地副本路径

**实现细节**：

```typescript
class ProductAccessService {
  /**
   * 读取产物：服务器优先，本地回退
   */
  async readProduct(executionId: string, fileName: string): Promise<Buffer> {
    // 1. 尝试从服务器读取
    try {
      const data = await this.apiClient.getArtifact(executionId, fileName);
      return data;
    } catch (e) {
      // 2. 服务器失败时回退到本地
      if (this.config.enabled) {
        const localPath = this.resolveLocalPath(executionId, fileName);
        if (await this.exists(localPath)) {
          return await fsp.readFile(localPath);
        }
      }
      throw new Error(`Product not found: ${executionId}/${fileName}`);
    }
  }
  
  /**
   * 列出产物：合并服务器 + 本地（去重）
   */
  async listProducts(executionId: string): Promise<ProductInfo[]> {
    const serverProducts = await this.apiClient.listArtifacts(executionId);
    const localProducts = await this.listLocalProducts(executionId);
    
    // 以服务器为权威，本地为补充
    return this.mergeProducts(serverProducts, localProducts);
  }
}
```

**一致性保证**：
- 服务器为准：本地副本仅作缓存/备份
- 定期同步：从服务器拉取增量更新到本地
- 冲突解决：服务器最新 → 覆盖本地（不反过来覆盖服务器）
- 删除：服务器删除时本地保留（由 retentionDays 控制）

**UI 表现**：

```
┌─ 产物列表（桌面端）────────────────────────────┐
│  [服务器] index.html        2026-06-03 10:30  [查看]│
│  [本地]   index.html        2026-06-03 10:30  [查看]│
│           ↑ 本地副本与服务器同步                │
│                                                │
│  [服务器] report.md         2026-06-03 10:30  [查看]│
│  [本地]   report.md         2026-06-03 10:30  [查看]│
│                                                │
│  离线模式：仅显示本地副本（标记 [本地]）       │
│  同步中：显示 "🔄 同步中..." 提示               │
└────────────────────────────────────────────────┘
```

### 6.16 决策影响总结

| 决策 | 实施调整 | 工作量影响 |
|------|---------|----------|
| **1. 默认路径 + 用户自选** | `LocalSaveConfig` 默认值 + UI 路径选择器 | 0（已规划） |
| **2. 不实现加密** | 删除 `LocalSaveCrypto` 模块 + 删除 UI 加密字段 | **-1 天** |
| **3. 不实现云盘集成** | 不引入云盘 SDK，保持 `local-only` | **-2 天**（含预留） |
| **4. 服务器优先读取** | 实现"服务器→本地"回退策略 | 0（已规划） |

**总工作量**：原 8.5 天 → **调整为 5.5 天**（节省 3 天）

**调整后实施步骤**：

| 步骤 | 内容 | 工作量 |
|------|------|--------|
| 1 | `LocalSaveConfig` + 默认值（默认路径计算） | 0.5 天 |
| 2 | `LocalSaveSyncService` 主进程 | 1.5 天 |
| 3 | WebSocket 事件订阅 + 服务器优先读取回退 | 0.5 天 |
| 4 | SHA-256 去重 + 容量/保留期清理 | 0.5 天 |
| 5 | ~~加密模块~~ **（取消）** | 0 天 |
| 6 | Settings UI（`LocalSaveSettings`，含路径选择器） | 1 天 |
| 7 | 产物面板"保存到本地"按钮 | 0.5 天 |
| 8 | Indicator 组件 + 统计信息 | 0.5 天 |
| 9 | 启动/退出/定时同步 | 0.5 天 |
| 10 | 测试 + 文档 | 1 天 |
| **总计** | | **~5.5 天** |

### 6.17 v2 路线图（未实施，预留扩展点）

| 需求 | 触发条件 | 实施方式 |
|------|---------|---------|
| **加密功能** | 企业用户要求 / 部门敏感数据 | 新增 `LocalSaveCrypto` 模块 + UI 加密开关 |
| **云盘直连** | 用户频繁跨设备 | 引入云盘 SDK + OAuth 流程 |
| **多设备同步** | 用户使用多台桌面端 | 通过云盘文件夹实现（用户配置） |
| **跨端回退** | Web 端访问本地副本（用户出差时） | 需评估安全性 |
| **只读加密导出** | 敏感产物仅可查看不可编辑 | PDF/A 导出 + DRM |

### 6.18 产物访问权限控制（按用户角色过滤）

> **核心规则**：每个用户只能看到
> 1. **自己**作为发起者/沟通者产生的产物
> 2. **自己所在部门**的**公开**产物
> 3. **自己创建的个人助理**产生的产物
> 4. **部门领导**额外可见：本部门内**所有员工**的产物
> 5. **董事长/FULL** 可见：企业内**所有**产物
>
> 严格遵循 [权限与入口矩阵.md §3](file:///f:/SoarCloudAI/docker/living-agent-service/docs/权限与入口矩阵.md) 的"登录状态→身份→通道→流程"分层。

#### 6.18.1 角色 × 产物可见性矩阵

| 角色 | 自己的产物 | 本部门公开产物 | 本部门其他员工产物 | 跨部门公开产物 | 跨部门其他员工产物 | 部门领导 | 董事长 |
|------|----------|--------------|------------------|--------------|------------------|---------|--------|
| **未登录** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | - |
| **普通员工** | ✅ | ✅ | ❌ | ✅（需是公开标记） | ❌ | ❌ | - |
| **部门负责人** | ✅ | ✅ | ✅（本部门全员） | ✅（需是公开标记） | ❌ | ❌ | - |
| **董事长/FULL** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | - |

**字段映射**：
- "自己" = `ArtifactRecord.createdBy == currentUser.id` 或 `participantIds` 包含当前用户
- "本部门" = `ArtifactRecord.department == currentUser.departmentId`
- "公开" = `ArtifactRecord.visibility == "PUBLIC" || visibility == "DEPARTMENT"`
- "其他员工" = `ArtifactRecord.employeeCode != currentUser.linkedEmployeeCode`

#### 6.18.2 产物可见性字段（数据模型扩展）

**当前现状**（参考 [ArtifactController.java L42-77](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-gateway/src/main/java/com/livingagent/gateway/controller/ArtifactController.java)）：
- 产物记录仅含 `executionId / taskId / employeeCode / department / projectId / type / path`
- 无 `visibility / createdBy / participantIds` 字段
- API 直接返回所有数据，**无权限过滤**

**需扩展的字段**：

```sql
-- 新增 Flyway migration: V2026xxxx__add_artifact_visibility.sql
ALTER TABLE artifact_record
  ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
  ADD COLUMN created_by VARCHAR(100),
  ADD COLUMN participant_ids TEXT,           -- 逗号分隔 userId 列表
  ADD COLUMN viewer_departments TEXT,        -- 逗号分隔 deptCode 列表（额外可查看部门）
  ADD COLUMN visible_to_leader BOOLEAN NOT NULL DEFAULT TRUE;  -- 部门领导可见

-- visibility 枚举：
--   PRIVATE    - 仅创建者 + participants 可见
--   DEPARTMENT - 本部门全员可见
--   PUBLIC     - 跨部门公开（需审批或标记）
--   RESTRICTED - 仅指定 viewerDepartments 可见
```

**默认值策略**：
- 数字员工产物 → `visibility=DEPARTMENT`（部门内共享）
- 个人助理产物 → `visibility=PRIVATE`（仅个人）
- 用户在 UI 标记为"公开" → `visibility=PUBLIC`
- 默认 `visible_to_leader=TRUE`（部门领导可见），用户可关闭

#### 6.18.3 后端 API 权限过滤实现

##### 6.18.3.1 权限服务（`ArtifactAccessService.java`）

```java
package com.livingagent.gateway.security;

@Service
public class ArtifactAccessService {

    /**
     * 判断当前用户是否有权查看指定产物
     */
    public boolean canView(ArtifactRecord record, UserContext user) {
        // 1. 董事长/FULL 全部可见
        if (user.isChairman() || user.getAccessLevel() == AccessLevel.FULL) {
            return true;
        }

        // 2. 创建者 / 参与者始终可见
        if (record.getCreatedBy() != null && record.getCreatedBy().equals(user.getUserId())) {
            return true;
        }
        if (record.getParticipantIds() != null
            && Arrays.asList(record.getParticipantIds().split(",")).contains(user.getUserId())) {
            return true;
        }

        // 3. 根据 visibility 判断
        switch (record.getVisibility()) {
            case PUBLIC:
                return true;  // 跨部门公开
            case DEPARTMENT:
                if (record.getDepartment() != null
                    && record.getDepartment().equals(user.getDepartmentId())) {
                    return true;  // 本部门可见
                }
                // 部门领导：可见本部门所有产物（即使 createdBy 不是自己）
                if (record.getVisibleToLeader()
                    && user.isDepartmentLeader()
                    && record.getDepartment() != null
                    && record.getDepartment().equals(user.getDepartmentId())) {
                    return true;
                }
                return false;
            case PRIVATE:
                return false;  // 已在第 2 步处理
            case RESTRICTED:
                if (record.getViewerDepartments() != null
                    && Arrays.asList(record.getViewerDepartments().split(","))
                        .contains(user.getDepartmentId())) {
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * 批量过滤产物列表
     */
    public List<ArtifactRecord> filterVisible(
            List<ArtifactRecord> records, UserContext user) {
        return records.stream()
            .filter(r -> canView(r, user))
            .toList();
    }
}
```

##### 6.18.3.2 Controller 改造（`ArtifactController.java`）

```java
@GetMapping
public ApiResponse<List<ArtifactRecord>> listArtifacts(
        @RequestParam(required = false) String department,
        @RequestParam(required = false) String executionId,
        // ... 其他参数
        @AuthenticationPrincipal UserContext currentUser) {
    
    List<ArtifactRecord> all = artifactRecordService.query(...);
    
    // ★ 新增：按当前用户权限过滤
    List<ArtifactRecord> visible = accessService.filterVisible(all, currentUser);
    
    return ApiResponse.ok(visible);
}

@GetMapping("/{artifactId}/download")
public ResponseEntity<Resource> downloadArtifact(
        @PathVariable String artifactId,
        @AuthenticationPrincipal UserContext currentUser) {
    
    return artifactRecordService.getArtifact(artifactId)
        .filter(record -> accessService.canView(record, currentUser))  // ★ 权限校验
        .map(record -> { /* 现有下载逻辑 */ })
        .orElseGet(() -> {
            log.warn("User {} denied access to artifact {}", 
                currentUser.getUserId(), artifactId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).<Resource>build();
        });
}
```

##### 6.18.3.3 新增 API（按用户列出自己可见产物）

```java
@GetMapping("/my-visible")
public ApiResponse<List<ArtifactRecord>> listMyVisibleArtifacts(
        @AuthenticationPrincipal UserContext currentUser,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    
    // 1. 拉取可能可见的产物（按部门 + 公开）
    List<ArtifactRecord> candidates = artifactRecordService.getVisibleCandidates(
        currentUser.getUserId(),
        currentUser.getDepartmentId(),
        currentUser.isChairman() || currentUser.getAccessLevel() == AccessLevel.FULL,
        currentUser.isDepartmentLeader(),
        PageRequest.of(page, size * 3, Sort.by(Sort.Direction.DESC, "createdAt"))
    );
    
    // 2. 二次过滤
    List<ArtifactRecord> visible = accessService.filterVisible(candidates, currentUser);
    
    return ApiResponse.ok(visible);
}

@GetMapping("/by-department/{department}")
public ApiResponse<List<ArtifactRecord>> getArtifactsByDepartment(
        @PathVariable String department,
        @AuthenticationPrincipal UserContext currentUser) {
    
    // 权限校验：仅本部门成员 / 部门领导 / 董事长可调用
    boolean allowed = currentUser.isChairman()
        || currentUser.getAccessLevel() == AccessLevel.FULL
        || (currentUser.getDepartmentId() != null
            && currentUser.getDepartmentId().equals(department))
        || (currentUser.isDepartmentLeader()
            && currentUser.getDepartmentId() != null
            && currentUser.getDepartmentId().equals(department));
    
    if (!allowed) {
        return ApiResponse.err("FORBIDDEN", "无权限查看其他部门的产物");
    }
    
    return ApiResponse.ok(artifactRecordService.getByDepartment(department));
}
```

#### 6.18.4 产物创建时的可见性默认值

**`ToolBackedEmployeeTaskExecutor.saveArtifactFile()` 修改**：

```java
private String saveArtifactFile(...) {
    // ... 现有逻辑：写入 by-execution/{execId}/{empCode}/ 和 by-employee/{empCode}/{execId}/
    
    // ★ 新增：插入 ArtifactRecord 时设置 visibility
    ArtifactRecord record = new ArtifactRecord();
    record.setExecutionId(executionId);
    record.setEmployeeCode(empCode);
    record.setDepartment(department);
    record.setVisibility(determineDefaultVisibility(executionContext));  // 见下方
    record.setCreatedBy(executionContext.getUserId());
    record.setVisibleToLeader(true);  // 默认部门领导可见
    record.setCreatedAt(Instant.now());
    // ... 其他字段
    artifactRecordRepository.save(record);
}

private String determineDefaultVisibility(TaskExecutionContext ctx) {
    // 1. 个人助理（origin=personal）的产物 → PRIVATE
    if (ctx.getEmployeeOrigin() == EmployeeOrigin.PERSONAL) {
        return "PRIVATE";
    }
    // 2. 部门内协作产物 → DEPARTMENT
    if (ctx.getDepartment() != null) {
        return "DEPARTMENT";
    }
    // 3. 跨部门协调产物 → RESTRICTED（指定 viewerDepartments）
    return "RESTRICTED";
}
```

#### 6.18.5 前端 UI 过滤

##### 6.18.5.1 API 客户端（`frontend/src/services/api.ts`）

```typescript
export interface ArtifactListParams {
  department?: string;
  executionId?: string;
  employeeCode?: string;       // 仅自己/本部门可见
  visibility?: 'PRIVATE' | 'DEPARTMENT' | 'PUBLIC' | 'RESTRICTED';
  type?: string;
  page?: number;
  size?: number;
}

class ArtifactService {
  /**
   * 获取"我可见的产物" - 推荐默认使用
   */
  async listMyVisible(params?: Omit<ArtifactListParams, 'visibility'>): Promise<ArtifactRecord[]> {
    return request<ArtifactRecord[]>('/artifacts/my-visible', { params });
  }

  /**
   * 获取本部门产物 - 部门领导可看全部
   */
  async listByDepartment(dept: string): Promise<ArtifactRecord[]> {
    return request<ArtifactRecord[]>(`/artifacts/by-department/${dept}`);
  }
}
```

##### 6.18.5.2 前端组件（`ArtifactList.tsx`）

```tsx
import { useEffect, useState } from 'react';
import { useAuthStore } from '@/stores/auth';

export function ArtifactList() {
  const currentUser = useAuthStore(s => s.user);
  const [artifacts, setArtifacts] = useState<ArtifactRecord[]>([]);
  const [scope, setScope] = useState<'mine' | 'department' | 'all'>('mine');

  useEffect(() => {
    const load = async () => {
      let data: ArtifactRecord[];
      switch (scope) {
        case 'mine':
          data = await artifactService.listMyVisible();
          break;
        case 'department':
          // 仅当用户有部门时
          if (currentUser?.departmentId) {
            data = await artifactService.listByDepartment(currentUser.departmentId);
          } else {
            data = [];
          }
          break;
        case 'all':
          // 仅 FULL/董事长可见
          if (currentUser?.accessLevel === 'FULL' || currentUser?.isChairman) {
            data = await artifactService.list({});
          } else {
            data = [];  // 前端兜底：非 FULL 用户不显示"全部"页签
          }
          break;
      }
      setArtifacts(data);
    };
    load();
  }, [scope, currentUser]);

  return (
    <div className="artifact-list">
      <Tabs value={scope} onChange={setScope}>
        <Tab value="mine" label="我的产物" />
        <Tab value="department" label="本部门" />
        {(currentUser?.accessLevel === 'FULL' || currentUser?.isChairman) && (
          <Tab value="all" label="全部" />
        )}
      </Tabs>
      <ArtifactGrid artifacts={artifacts} />
    </div>
  );
}
```

##### 6.18.5.3 产物卡片权限标签

```tsx
function ArtifactCard({ artifact }: { artifact: ArtifactRecord }) {
  const isOwner = useAuthStore(s => s.user?.id === artifact.createdBy);
  const isLeader = useAuthStore(s => s.user?.isDepartmentLeader);
  
  return (
    <Card>
      <CardHeader>
        <Title>{artifact.name}</Title>
        <Badge>
          {isOwner ? '我的'
            : isLeader && artifact.department === useAuthStore.getState().user?.departmentId
              ? '本部门'
              : '可见'}
        </Badge>
        <VisibilityTag visibility={artifact.visibility} />
      </CardHeader>
      {/* 预览 + 下载按钮（按钮已通过后端 canView 二次校验） */}
    </Card>
  );
}
```

#### 6.18.6 桌面端本地产物与权限同步

> **核心原则**：本地副本不绕过权限。即使文件在本地，也只显示用户有权查看的产物。

**主进程改造**（`local-save-sync.ts`）：

```typescript
class LocalSaveSyncService extends EventEmitter {
  
  /**
   * 全量同步时，按当前用户权限过滤本地副本
   */
  async fullSync(): Promise<SyncResult> {
    // 1. 拉取"我可见的"产物（走后端权限过滤后的列表）
    const visible = await this.apiClient.getMyVisibleArtifacts();
    
    let savedCount = 0, removedCount = 0;
    for (const artifact of visible) {
      try {
        await this.onArtifactReady(artifact);
        savedCount++;
      } catch (e) {
        // 记录日志
      }
    }
    
    // 2. 清理已无权限的本地副本
    removedCount = await this.cleanupUnauthorized(this.localInventory, visible);
    
    return { savedCount, removedCount };
  }
  
  /**
   * 启动时权限变更检测
   * 场景：用户从"部门领导"降级为"普通员工"后，原可见的部门全员产物应被清理
   */
  async onUserRoleChanged(oldRole: UserRole, newRole: UserRole): Promise<void> {
    log.info('User role changed: {} → {}, re-syncing local artifacts', oldRole, newRole);
    await this.fullSync();
  }
}
```

**关键场景**：
| 场景 | 行为 |
|------|------|
| 用户从部门领导降级为普通员工 | 启动时全量重同步，移除无权查看的本地副本 |
| 跨部门调动 | 全量重同步，清理旧部门产物（保留 30 天回收站） |
| 临时查看跨部门产物 | 保留本地副本，但仅在该会话期间可读 |
| 删除产物 | 服务器删除时，本地保留 30 天（与 retentionDays 一致），但前端标记"已失效" |
| 多账号切换 | 切换账号前清理上一个账号的本地副本（避免泄露） |

#### 6.18.7 审计与日志

**审计字段**（补充到 [权限与入口矩阵.md §8.2](file:///f:/SoarCloudAI/docker/living-agent-service/docs/权限与入口矩阵.md)）：

```json
{
  "actor": "user-001",
  "actor_role": "DEPARTMENT_LEADER",
  "actor_department": "tech",
  "action": "ARTIFACT_VIEW",
  "target_type": "artifact",
  "target_id": "artifact-123",
  "target_owner": "user-002",
  "target_department": "tech",
  "visibility": "DEPARTMENT",
  "access_granted": true,
  "access_reason": "DEPARTMENT_LEADER_CAN_VIEW_DEPARTMENT",
  "channel": "desktop",
  "trace_id": "trace-uuid",
  "timestamp": "2026-06-04T10:30:00Z"
}
```

**审计要求**：
- **下载** / **预览** 行为均需审计（强审计）
- 跨部门访问需额外标记 `cross_department=true`
- 董事长查看全企业产物需标记 `chairman_view=true`（合规追溯）

#### 6.18.8 验证标准

**功能验证**：
- [ ] 普通员工 A 看不到员工 B 的私有产物（PRIVATE）
- [ ] 普通员工 A 看不到跨部门非公开产物
- [ ] 部门领导 L 可看到本部门所有员工产物（包括 PRIVATE 中 `visible_to_leader=TRUE` 的）
- [ ] 董事长可见所有产物
- [ ] 跨部门调动后，本地副本在下次启动时被清理
- [ ] 产物创建时自动按规则设置 visibility

**API 验证**：
- [ ] `GET /api/artifacts/my-visible` 仅返回当前用户有权查看的产物
- [ ] `GET /api/artifacts/{id}/download` 无权时返回 403
- [ ] `GET /api/artifacts/by-department/{dept}` 跨部门调用返回 403
- [ ] 审计日志记录所有下载/预览行为

**性能验证**：
- [ ] `listMyVisible` 在 10 万条产物下 < 500ms（数据库索引 + 缓存）
- [ ] 部门领导查本部门全员产物 < 1s

#### 6.18.9 实施步骤

| 步骤 | 内容 | 工作量 | 涉及文件 |
|------|------|-------|---------|
| 1 | 数据库 migration（visibility/createdBy/participantIds 字段） | 0.5 天 | `init-db/` |
| 2 | `ArtifactRecord` 实体扩展 | 0.5 天 | `core/database/entity/ArtifactRecord.java` |
| 3 | `ArtifactAccessService` 权限服务 | 1 天 | `gateway/security/` |
| 4 | `ArtifactController` 接入权限过滤 | 0.5 天 | `gateway/controller/ArtifactController.java` |
| 5 | `ToolBackedEmployeeTaskExecutor` 写入时设置 visibility | 0.5 天 | `core/autonomy/impl/ToolBackedEmployeeTaskExecutor.java` |
| 6 | 前端 `artifactService.listMyVisible()` + UI Tabs | 1 天 | `frontend/src/services/api.ts` + `ArtifactList.tsx` |
| 7 | 桌面端 `local-save-sync.ts` 角色变更重同步 | 0.5 天 | `desktop/src/main/local-save-sync.ts` |
| 8 | 审计日志（`AuditLogService`） | 1 天 | `gateway/service/AuditLogService.java` |
| 9 | 测试 + 文档 | 1 天 | - |
| **总计** | | **~6.5 天** | |

#### 6.18.10 决策点（待用户确认）

| 决策 | 选项 A | 选项 B | 推荐 |
|------|-------|-------|------|
| **D1：默认 visibility** | 部门内协作产物默认 DEPARTMENT | 全部默认 PRIVATE，用户手动改 | **A**（与现有共享模型对齐） |
| **D2：部门领导可见范围** | 本部门所有产物（含 PRIVATE） | 仅本部门公开（DEPARTMENT/PUBLIC）产物 | **A**（按用户要求"部门领导可以看到部门内的所有员工产物"） |
| **D3：董事长可见范围** | 所有产物（含 PRIVATE） | 所有非 PRIVATE 产物 | **A**（按用户要求"董事长是可以看到所有产物"） |
| **D4：跨部门公开标记** | 仅产物创建者可标记 PUBLIC | 需部门领导审批后才能 PUBLIC | **A**（当前阶段简化） |
| **D5：本地副本权限同步** | 启动时检测角色变更，全量重同步 | 不重同步，仅下次新事件触发 | **A**（保证一致性） |

#### 6.18.11 兼容性影响

| 现有功能 | 兼容性 |
|---------|--------|
| 旧 API `/api/artifacts` 无参数调用 | ✅ 仍可用，但仅返回当前用户可见的产物（**可能过滤掉旧数据**） |
| 旧产物（无 visibility 字段） | ✅ migration 默认 `visibility=DEPARTMENT, visible_to_leader=TRUE` |
| `by-execution/{execId}/{empCode}/` 目录结构 | ✅ 不变，目录是物理存储，权限是逻辑过滤 |
| `by-employee/{empCode}/{execId}/` 索引 | ✅ 不变，权限过滤在 API 层 |
| 桌面端本地产物 | ✅ 本地保存时即带权限元数据，无需二次过滤 |

### 6.19 公共任务栏（Public Task Board）桌面端适配

> **场景**：固定数字员工无法完成的任务会发布到"公共任务栏"（见 [PublicTaskBoard.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/components/PublicTaskBoard.tsx)），
> 普通员工可接取并完成以获得积分奖励。**该功能在桌面端有显著增量价值**，应纳入 living-agent-desktop。

#### 6.19.1 桌面端增量价值

| 场景 | 桌面端优势 |
|------|----------|
| **管理员监控任务流转** | 托盘红点显示待接取数 + 任务中心独立页 |
| **员工抢高优任务** | OS 通知（紧急/高优先级时弹窗，避免错过） |
| **离线查看任务列表** | 本地缓存（与本地产物保存机制复用 baseDir） |
| **接取任务产出物** | 直接保存到本地（与 §6 产物保存联动） |
| **多账号切换** | 当前账号可接取的任务独立缓存，切换账号时清理 |
| **任务到期提醒** | 临近截止时间时弹窗（`estimatedHours` 维度） |
| **接取/提交快捷键** | 全局快捷键（如 `Ctrl+Shift+C` 打开任务中心） |
| **任务中心悬浮窗** | 持续显示在桌面角落（类似聊天悬浮窗） |

#### 6.19.2 任务栏页面（复用 Web 端 `PublicTaskBoard`）

**桌面端无需重写**，直接复用 [PublicTaskBoard.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/components/PublicTaskBoard.tsx)：

```typescript
// desktop/src/renderer/pages/TaskBoard/PublicTaskBoardPage.tsx
import PublicTaskBoard from '../../../../living-agent-service/frontend/src/components/PublicTaskBoard';

export function PublicTaskBoardPage() {
  return (
    <div className="desktop-task-board-page">
      <header>
        <h1>公共任务栏</h1>
        <span className="subtitle">固定数字员工无法处理的任务，可接取获得积分</span>
      </header>
      <PublicTaskBoard />
    </div>
  );
}
```

**理由**：
- 组件本身与 Web 端一致（数据来自后端 API，无 Electron 特定逻辑）
- 桌面端不需要修改接取/提交流程
- 减少维护成本（Web 端和桌面端共享一套组件）

#### 6.19.3 桌面端增强能力

##### 6.19.3.1 系统托盘红点（待接取数）

**主进程**（`task-board-tray.ts`）：

```typescript
import { Tray, Menu } from 'electron';
import { taskApi } from '../api-client';

class TaskBoardTray {
  private tray: Tray;
  private pendingCount: number = 0;

  async refreshPendingCount() {
    const tasks = await taskApi.getPublicTasks();
    this.pendingCount = tasks.length;
    this.updateTray();
  }

  private updateTray() {
    const iconPath = this.pendingCount > 0
      ? 'assets/tray-badge-red.png'   // 有待接取任务
      : 'assets/tray-normal.png';     // 正常
    
    this.tray.setImage(iconPath);
    this.tray.setToolTip(
      this.pendingCount > 0
        ? `公共任务栏：${this.pendingCount} 个待接取任务`
        : 'Living Agent'
    );
    
    // 右键菜单增加"任务中心"
    this.tray.setContextMenu(Menu.buildFromTemplate([
      { label: `📋 待接取任务 (${this.pendingCount})`, click: () => this.openTaskBoard() },
      { label: '🔄 刷新', click: () => this.refreshPendingCount() },
      { type: 'separator' },
      { label: '退出', role: 'quit' }
    ]));
  }

  private openTaskBoard() {
    // 唤起主窗口，跳转 /task-board 路由
  }
}
```

**定时刷新策略**：

| 策略 | 说明 |
|------|------|
| **启动时刷新** | 应用启动后立即拉取一次 |
| **WebSocket 事件驱动** | 订阅 `public_task_published` 事件（需后端新增） |
| **定时轮询（兜底）** | 每 5 分钟拉取一次（保证不漏） |
| **窗口聚焦时刷新** | 用户回到桌面端时立即刷新 |

##### 6.19.3.2 OS 通知（高优任务）

**主进程**（`task-notification.ts`）：

```typescript
import { Notification } from 'electron';

class TaskNotification {
  private notifiedTaskIds: Set<string> = new Set();  // 防重复

  /**
   * 处理任务栏变更事件
   */
  onTasksChanged(tasks: PublicTask[]) {
    for (const task of tasks) {
      // 仅通知"高"和"紧急"优先级
      if (task.priority < 3) continue;
      
      // 防重复通知
      if (this.notifiedTaskIds.has(task.taskId)) continue;
      this.notifiedTaskIds.add(task.taskId);
      
      // 避免通知列表无限增长
      if (this.notifiedTaskIds.size > 200) {
        const arr = [...this.notifiedTaskIds];
        this.notifiedTaskIds = new Set(arr.slice(-100));
      }
      
      new Notification({
        title: `${this.getPriorityEmoji(task.priority)} 新任务：${task.taskType}`,
        body: `${task.description}\n奖励：${task.reward} 积分`,
        actions: [
          { type: 'button', text: '查看任务' },
          { type: 'button', text: '直接接取' }
        ],
        closeButtonText: '稍后'
      }).on('action', (action) => {
        if (action.index === 0) this.openTaskBoard();
        if (action.index === 1) this.claimTaskDirectly(task.taskId);
      }).show();
    }
  }

  private getPriorityEmoji(priority: number): string {
    if (priority >= 5) return '🚨';
    if (priority >= 3) return '🔥';
    return '📋';
  }
}
```

**通知规则**（用户可配置）：

```typescript
interface TaskNotificationConfig {
  enabled: boolean;
  minPriority: number;           // 默认 3（高）
  departments: string[];         // 仅通知指定部门，[] = 全部
  difficultyFilter: string[];    // 仅通知指定难度
  quietHours: {                  // 免打扰时段
    enabled: boolean;
    start: string;               // "22:00"
    end: string;                 // "08:00"
  };
  autoClaim: boolean;            // 自动接取（高风险，默认关闭）
}
```

##### 6.19.3.3 全局快捷键

```typescript
import { globalShortcut } from 'electron';

class TaskBoardShortcut {
  register() {
    // Ctrl+Shift+T 打开任务中心
    globalShortcut.register('CommandOrControl+Shift+T', () => {
      this.showTaskBoard();
    });
    
    // Ctrl+Shift+C 快速接取最优先任务
    globalShortcut.register('CommandOrControl+Shift+C', () => {
      this.claimTopPriorityTask();
    });
  }
}
```

##### 6.19.3.4 任务中心悬浮窗

类似微信/QQ 聊天悬浮窗：
- 任务中心常驻桌面右侧
- 折叠态：仅显示待接取数
- 展开态：显示任务列表 + 一键接取
- 不抢主窗口焦点（透明背景 + 无任务栏图标）

```typescript
// desktop/src/main/floating-task-board.ts
class FloatingTaskBoard {
  private win: BrowserWindow;
  
  create() {
    this.win = new BrowserWindow({
      width: 320,
      height: 480,
      frame: false,                    // 无边框
      transparent: true,               // 透明背景
      alwaysOnTop: true,               // 桌面常驻
      skipTaskbar: true,               // 不显示在任务栏
      resizable: false,
      minimizable: false,
      maximizable: false
    });
    this.win.loadFile('floating-task-board.html');
  }
}
```

#### 6.19.4 任务本地缓存

**缓存策略**（与 §6.18.6 权限过滤一致）：

```typescript
class TaskBoardLocalCache {
  /**
   * 缓存当前用户可见的公共任务
   * 路径：{baseDir}/cache/task-board/{year}/{month}/{dept}.json
   */
  async cacheVisibleTasks(tasks: PublicTask[]): Promise<void> {
    // 按部门分组缓存
    const groupedByDept = _.groupBy(tasks, 'department');
    for (const [dept, items] of Object.entries(groupedByDept)) {
      const cachePath = path.join(
        this.config.basePath,
        'cache', 'task-board',
        String(new Date().getFullYear()),
        String(new Date().getMonth() + 1).padStart(2, '0'),
        `${dept || 'all'}.json`
      );
      await fsp.mkdir(path.dirname(cachePath), { recursive: true });
      await fsp.writeFile(cachePath, JSON.stringify(items, null, 2));
    }
  }
  
  /**
   * 离线时显示缓存
   */
  async loadCachedTasks(dept?: string): Promise<PublicTask[]> {
    const files = await glob(
      path.join(this.config.basePath, 'cache', 'task-board', '**', `${dept || 'all'}.json`)
    );
    if (files.length === 0) return [];
    
    const items: PublicTask[] = [];
    for (const file of files) {
      const content = await fsp.readFile(file, 'utf-8');
      items.push(...JSON.parse(content));
    }
    return items;
  }
}
```

**缓存规则**：
- 容量限制：每部门最多保留最近 100 条
- TTL：24 小时过期
- 权限过滤：仅缓存 `canClaim(currentUser)` 的任务
- 多账号：账号切换时清理上一个账号的缓存

#### 6.19.5 任务权限规则（与产物权限协同）

> **任务权限与产物权限是两条独立链路，但策略一致**：
> - 公共任务栏本身是"全企业可见"的（任何登录用户都能看到）
> - 接取任务后产生的产物，遵循 §6.18 visibility 规则

| 用户角色 | 查看公共任务栏 | 接取任务 | 接取后产物可见性 |
|---------|--------------|---------|----------------|
| 未登录 | ❌ | ❌ | - |
| 普通员工 | ✅（自己部门 + PUBLIC） | ✅ | PRIVATE → 提交后改 DEPARTMENT |
| 部门负责人 | ✅（本部门全部） | ✅ | DEPARTMENT（默认） |
| 董事长/FULL | ✅（全部） | ✅ | 自由选择 |
| 跨部门 | 仅 PUBLIC 标记的任务 | ✅ | 提交时指定 RESTRICTED |

**接取后产物自动设置 visibility**（修改 `TaskClaimService`）：

```java
// 员工提交任务产物时
public void submitTaskResult(String taskId, String userId, String result) {
    Task task = taskRepo.findById(taskId).orElseThrow();
    
    // ★ 产物 visibility 根据用户角色自动设置
    String visibility = determineVisibility(userId, task);
    
    artifactRecordService.saveArtifact(ArtifactRecord.builder()
        .taskId(taskId)
        .userId(userId)
        .visibility(visibility)
        .createdBy(userId)
        .visibleToLeader(true)
        .build()
    );
}

private String determineVisibility(String userId, Task task) {
    User user = userRepo.findById(userId).orElseThrow();
    if (user.isChairman() || user.getAccessLevel() == AccessLevel.FULL) {
        return "DEPARTMENT";  // 董事长/FULL 提交默认部门内
    }
    if (user.getDepartmentId() != null
        && user.getDepartmentId().equals(task.getDepartmentId())) {
        return "DEPARTMENT";  // 本部门员工提交 → 部门内可见
    }
    return "RESTRICTED";  // 跨部门接取 → 受限可见
}
```

#### 6.19.6 实施步骤

| 步骤 | 内容 | 工作量 | 涉及文件 |
|------|------|-------|---------|
| 1 | 任务中心页面（复用 `PublicTaskBoard`） | 0.5 天 | `desktop/src/renderer/pages/TaskBoard/PublicTaskBoardPage.tsx` |
| 2 | 托盘红点 + 待接取数刷新 | 1 天 | `desktop/src/main/task-board-tray.ts` |
| 3 | OS 通知（高优任务弹窗） | 1 天 | `desktop/src/main/task-notification.ts` |
| 4 | 全局快捷键（打开/接取） | 0.5 天 | `desktop/src/main/shortcuts.ts` |
| 5 | 任务中心悬浮窗 | 1 天 | `desktop/src/main/floating-task-board.ts` |
| 6 | 任务本地缓存（含权限过滤） | 1 天 | `desktop/src/main/task-board-cache.ts` |
| 7 | 后端：`public_task_published` WebSocket 事件 | 0.5 天 | `gateway/websocket/DepartmentWebSocketHandler.java` |
| 8 | 后端：`TaskClaimService` 提交时设置产物 visibility | 0.5 天 | `core/employee/claim/TaskClaimService.java` |
| 9 | 测试 + 文档 | 1 天 | - |
| **总计** | | **~7 天** | |

#### 6.19.7 验证标准

**功能验证**：
- [ ] 桌面端任务中心页面可正常加载任务列表
- [ ] 托盘图标显示待接取数（无任务时为正常图标）
- [ ] 高优先级（≥3）新任务触发 OS 通知
- [ ] OS 通知点击"查看任务"打开任务中心
- [ ] OS 通知点击"直接接取"自动接取该任务
- [ ] 全局快捷键 `Ctrl+Shift+T` 打开任务中心
- [ ] 离线时显示本地缓存的任务列表
- [ ] 账号切换时本地缓存被清理

**权限验证**：
- [ ] 未登录桌面端不可见任务中心
- [ ] 普通员工仅看到本部门 + PUBLIC 任务
- [ ] 部门领导看到本部门全部任务
- [ ] 董事长看到全企业任务
- [ ] 接取跨部门任务后，产物 visibility = RESTRICTED

**性能验证**：
- [ ] 待接取数刷新（WebSocket）< 1s
- [ ] OS 通知弹出 < 500ms
- [ ] 悬浮窗启动 < 1s
- [ ] 本地缓存读取 < 200ms

#### 6.19.8 决策点（待用户确认）

| 决策 | 选项 A | 选项 B | 推荐 |
|------|-------|-------|------|
| **D1：托盘红点触发** | 任何新任务都显示 | 仅高/紧急优先级显示 | **B**（避免打扰） |
| **D2：OS 通知频率** | 每条新任务都通知 | 按优先级 + 免打扰时段过滤 | **B**（避免通知疲劳） |
| **D3：自动接取功能** | 默认关闭，用户手动开启 | 默认开启（自动抢单） | **A**（避免误操作） |
| **D4：悬浮窗默认状态** | 默认显示 | 默认隐藏，用户主动展开 | **A**（不抢屏幕空间） |
| **D5：跨部门任务接取** | 允许（带标记） | 禁止，仅本部门 | **A**（公共任务栏本身就是跨部门） |
| **D6：接取后产物默认 visibility** | DEPARTMENT（部门内共享） | PRIVATE（仅自己） | **A**（与现有协作模式一致） |

#### 6.19.9 与现有功能的关系

| 现有功能 | 桌面端增强 |
|---------|----------|
| [PublicTaskBoard.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/components/PublicTaskBoard.tsx) Web 端 | 复用组件 + 桌面端独有：托盘/通知/快捷键/悬浮窗 |
| `taskApi.getPublicTasks` REST | 桌面端按相同 API 拉取，无后端改动 |
| `taskApi.claimTask` REST | 桌面端走相同接取流程 |
| `MyTasks.tsx` 任务管理 | 桌面端跳转到 Web 端 MyTasks 或新增"我的接取" Tab |
| WebSocket `employee_task_update` | 扩展为 `public_task_published`（后端新增事件） |
| §6 产物本地产物保存 | 联动：接取任务的产物保存到本地（共享 baseDir） |
| §6.18 产物访问权限 | 协同：任务产物 visibility 自动设置 |
| 系统托盘（Hermes 借鉴） | 任务中心 = 托盘 + 红点 + 通知 |

#### 6.19.10 v2 路线图

| 需求 | 触发条件 | 实施方式 |
|------|---------|---------|
| **任务推荐（智能）** | AI 推荐最合适任务 | 基于员工能力 + 部门 + 历史接取 |
| **任务订阅** | 员工订阅特定类型 | 新增 `task_subscriptions` 表 |
| **任务到期提醒** | `estimatedHours` 临近 | 定时任务 + 通知 |
| **多人协作任务** | 任务需要多人接取 | `TaskCollaborationService` |
| **任务转交** | 接取后无法完成 | `reassignTask` API |
| **积分排行榜** | 员工激励 | 全企业 / 部门内排行 |

---

---

## 三、可借鉴清单（按优先级）

### P0 - 立即可借鉴

#### 借鉴 1：Chat 头部 Token/Cost 徽章

**Hermes**：[ChatHeader.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/ChatHeader.tsx) + [ContextGauge.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/ContextGauge.tsx)

```tsx
// Hermes ChatHeader 显示
{usage && <UsageBadge usage={usage} />}
{t("X tokens")} · ${cost.toFixed(4)}
```

**Living Agent 现状**：[DepartmentChatInline.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/DepartmentChatInline.tsx) 无 token/cost 显示

**借鉴方案**：
- 在部门聊天头部增加 `TokenBadge` 组件
- 后端 LLM 调用返回时附带 `usage` 字段（已有 Spring AI 元数据）
- 显示：prompt/completion tokens + cost
- 点击展开 → 详细统计图表

#### 借鉴 2：ChatInput 拖拽附件

**Hermes**：[ChatInput.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/ChatInput.tsx) + [attachmentUtils.ts](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/attachmentUtils.ts)

**能力**：
- 拖文件到聊天区显示高亮 overlay
- 粘贴文件（从剪贴板）
- 多文件批处理
- 错误处理（不支持类型）

**Living Agent 现状**：无文件附件能力

**借鉴方案**：
- 在 `DepartmentChatInline` 输入框增加 `useDropZone`
- 文件上传到 `/api/attachments/`
- 任务中作为上下文传递给 LLM
- 与 P0-2 artifacts 配合：上传文件可作为任务输入

#### 借鉴 3：Splash Screen 启动体验

**Hermes**：[SplashScreen.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/SplashScreen/SplashScreen.tsx) + `hermesbg.webp`

**Living Agent 现状**：登录后直接进 Dashboard，无过渡

**借鉴方案**：
- 增加品牌 Splash Screen（背景图 + logo）
- 解决白屏体验差的问题
- 与现有品牌色对齐

#### 借鉴 4：ChatEmptyState 建议卡片

**Hermes**：[ChatEmptyState.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/ChatEmptyState.tsx)

**能力**：
- 6 个示例 Suggestion（搜索/提醒/邮件/脚本/定时/分析）
- 每个带图标，点击填入输入框

**Living Agent 现状**：空状态只显示文字

**借鉴方案**：
- 部门聊天空状态显示 4-6 个建议：
  - "查看本部门任务进展"
  - "生成销售周报"
  - "审查代码质量"
  - "分析本月财务"
- 点击后自动填入 + 发送

### P1 - 重要借鉴

#### 借鉴 5：Memory 标签页架构

**Hermes**：[MemoryTabs.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Memory/MemoryTabs.tsx) + [Memory.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Memory/Memory.tsx)

**架构**：
```
[Agent Memory] [User Profile] [Providers] [Soul]
```

**Living Agent 借鉴**：
- 新增 `KnowledgePage.tsx`，4 个 Tab：
  - **部门知识** (L2) - `data/department-knowledge/`
  - **个人知识** (L1) - `data/personal-knowledge/`
  - **共享知识** (L3) - `data/knowledge/`
  - **我的 SOUL** - 数字员工人格/规则
- 与 P2-8 `KnowledgeFileMirrorService` 集成
- 支持查看/搜索/编辑

#### 借鉴 6：Kanban 任务面板

**Hermes**：[Kanban.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Kanban/Kanban.tsx)

**能力**：
- 任务卡片：title/body/assignee/priority/result
- 状态栏：counts 显示各状态数量
- 看板切换：current/archived

**Living Agent 现状**：`MyTasks.tsx` 是列表式，不是 Kanban

**借鉴方案**：
- 在 `MyTasks.tsx` 增加 Kanban 视图切换
- 5 列：PENDING / RUNNING / BLOCKED / COMPLETED / FAILED
- 卡片支持拖拽改状态
- 与 P1-4 receipt 集成（点击卡片看 receipt）

#### 借鉴 7：Sessions 时间分组

**Hermes**：[Sessions.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Sessions/Sessions.tsx)

```typescript
type DateGroup = "today" | "yesterday" | "thisWeek" | "earlier";
function getDateGroup(ts: number): DateGroup { ... }
```

**Living Agent 现状**：`Chat.tsx` 会话列表无时间分组

**借鉴方案**：
- 在会话列表增加 `getDateGroup()` 工具
- 显示 `今天/昨天/本周/更早` 分组
- 提升长列表可读性

#### 借鉴 8：ConfigHealthBanner 预检

**Hermes**：[ConfigHealthBanner.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/components/ConfigHealthBanner.tsx)

**能力**：
- 配置异常 Banner（如 API key 缺失）
- 显示问题描述 + 修复位置
- 一键跳转到 Settings

**Living Agent 借鉴**：
- 部门聊天页增加 `ModelHealthBanner`
- 检测：模型池无可用模型、Provider API key 缺失
- 显示最近失败 + "去配置" 按钮

### P2 - 增强借鉴

#### 借鉴 9：Voice Input 语音输入

**Hermes**：[useVoiceInput.ts](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/hooks/useVoiceInput.ts)

**双策略**：
- 主：浏览器 `SpeechRecognition`（实时）
- 兜底：`MediaRecorder` + Whisper API（Web 不支持时）

**Living Agent 现状**：`useVoiceInput` 不存在

**借鉴方案**：
- 借鉴双策略实现（前端有 `SpeechRecognition`）
- 兜底调用后端 `/api/asr/transcribe`（已有 living-agent-perception）
- 部门聊天页输入框增加麦克风按钮

#### 借鉴 10：Fast Mode 切换

**Hermes**：[ChatHeader.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/ChatHeader.tsx) + [useFastMode.ts](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/hooks/useFastMode.ts)

**能力**：
- 快速模式开关（用轻量模型）
- 悬浮提示：当前模式说明
- 持久化到 profile

**Living Agent 借鉴**：
- 部门聊天头部增加"快速模式"开关
- 开启时使用 `BrainModelResolver.resolveForQuickTask()`
- 适合简单问答场景

#### 借鉴 11：Worktree 文件树

**Hermes**：[WorktreePanel.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/WorktreePanel.tsx) + [FileViewer.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Chat/FileViewer.tsx)

**能力**：
- vscode 风格文件树（图标 + 缩进）
- 文件点击预览
- 折叠/展开

**Living Agent 借鉴**：
- 在部门页面增加"工作目录"侧栏
- 调用 `/api/files/{dept}/` 列出 `data/department-knowledge/{dept}/` 文件
- 与职责卡、政策文档结合

#### 借鉴 12：Office 状态机模式

**Hermes**：[Office.tsx](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/renderer/src/screens/Office/Office.tsx)

```typescript
type OfficeState = "checking" | "not-installed" | "installing" | "ready" | "error";
```

**Living Agent 借鉴**：
- 当前 `DepartmentDetail` 状态管理较简单
- 引入显式状态机：
  ```
  LOADING → READY → STALE → ERROR
  ```
- 在 `useOfficePresence` 中明确状态转换
- 与 WebSocket 连接状态绑定

#### 借鉴 13：i18n 强类型 Key

**Hermes**：[shared/i18n/locales/zh-CN/](file:///f:/SoarCloudAI/docker/hermes-desktop-0.5.5/src/shared/i18n/locales/zh-CN/) + 10 个语言

**Living Agent 现状**：[zh.json](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/i18n/zh.json) + [en.json](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/i18n/en.json) 是 JSON 文件

**借鉴方案**：
- 改为 TypeScript 文件 + 强类型
- 编译时 key 检查
- 长期可扩展更多语言

---

## 四、不适合借鉴的

| Hermes 特性 | 原因 |
|-------------|------|
| Electron IPC | Web 应用用 WebSocket/REST |
| WebView 嵌入 3D Office | 已有 2D 像素虚拟办公室 |
| SSH 远程模式 | Web 应用不适用 |
| 桌面更新器 | Web 应用不适用 |
| 系统级 askpass | 桌面端安全特性 |
| 多 Profile 切换 | Living Agent 用"部门"代替 |

---

## 五、实施建议

### 优先级排序

| 优先级 | 借鉴项 | 理由 |
|--------|--------|------|
| **P0-1** | Token/Cost 徽章 | 数据已有（Spring AI usage），UI 增量 |
| **P0-2** | 拖拽附件 | 实用功能，扩展任务输入能力 |
| **P0-3** | Splash Screen | 改善品牌体验 |
| **P0-4** | ChatEmptyState 建议 | 引导用户使用 |
| **P1-5** | Memory 标签页 | 与 P2-8 知识库整合 |
| **P1-6** | Kanban 视图 | 任务管理升级 |
| **P1-7** | 会话时间分组 | 现有组件增强 |
| **P1-8** | ConfigHealthBanner | 配置预检 |
| **P2-9** | Voice Input | 高级功能 |
| **P2-10** | Fast Mode | 模型策略增强 |
| **P2-11** | Worktree 文件树 | 文件浏览 |
| **P2-12** | 状态机显式化 | 架构优化 |
| **P2-13** | 强类型 i18n | 长期维护性 |

### 实施约束

1. **保持虚拟办公室架构**：Hermes 的 3D Office 不适合 Living Agent 的 2D 像素风格
2. **不引入 Electron**：纯 Web 方案
3. **复用现有组件**：`MarkdownRenderer` 等可直接复用
4. **渐进式增强**：借鉴项独立可关闭
5. **后端 API 优先**：部分借鉴项需要后端配合（如 token/cost）

---

## 六、关键差异点总结

| 维度 | Hermes | Living Agent |
|------|--------|--------------|
| **多用户** | 单用户多 Profile | 多用户多部门 |
| **多模态** | 强（语音/文件/截图） | 弱（仅文本+HTML） |
| **可视化** | 3D Office (Claw3D) | 2D 像素 Office |
| **任务系统** | Kanban | MyTasks 列表 |
| **记忆架构** | entries/profile/soul | 三层知识库 |
| **通信** | IPC + SSH | WebSocket + REST |
| **状态管理** | React State + IPC | Zustand + WebSocket |
| **构建** | Electron + Vite | Vite + Docker |

---

## 七、决策点

请确认借鉴优先级：

1. **P0-1 ~ P0-4 是否全部实现？**（Token/Cost、附件、Splash、建议）
2. **P1-5 Memory 标签页**与现有 `MyTasks` 整合还是独立页面？
3. **P1-6 Kanban 视图**：MyTasks 增加切换，还是新建页面？
4. **P2-9 语音输入**：依赖 living-agent-perception 模块，是否先确认该模块状态？

---

**待用户审阅后开始按 P0 顺序实施。**
