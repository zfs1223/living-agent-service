# Living Agent Desktop 落地方案与进度跟踪

> 创建日期：2026-06-04
> 最后更新：2026-06-16
> 依据：[HERMES_COMPARISON_AND_BORROWING_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/HERMES_COMPARISON_AND_BORROWING_PLAN.md) §3 §5 §6 §6.18 §6.19
> 目标：基于 Electron + Vite + React + TypeScript 构建**独立桌面客户端** living-agent-desktop
> 与后端 Web 服务（端口 8382）通过 HTTP/WS 通信；与 `frontend/` 完全解耦，**不复用其任何代码**

---

## 1. 项目定位

| 维度 | 说明 |
|------|------|
| **目标用户** | 数字员工/管理员需要在本地 PC 客户端管理任务、**登录认证**、查看虚拟办公室、接收系统通知 |
| **与 Web 端差异** | 系统托盘、本地文件访问、离线缓存、OS 通知、本地产物保存、任务中心悬浮窗、客户端唯一标识、**独立手机号+验证码登录（与 frontend 对齐）** |
| **后端复用** | **API 层面**复用 living-agent-service 后端（WebSocket + REST，端口 8382） |
| **代码基础** | **不复用 frontend/ 任何代码**。渲染层入口、CSS、组件、类型都位于 `living-agent-desktop/src/renderer/` 独立维护 |
| **部署位置** | **客户端机器**（用户 PC），以安装包形式分发（electron-builder 打包 Windows x64）。**桌面端是主要使用方式，Web 端仅用于测试和复杂业务场景** |
| **与 frontend 的关系** | 两个**完全独立**的应用，部署在**不同物理机器**。desktop 通过 HTTP/WS **直连后端 API**（REST + WebSocket），**不复用、不嵌套 frontend 任何代码或页面**。**登录流程与 frontend 完全对齐（手机号+短信验证码）** |
| **新增工程** | `f:\SoarCloudAI\docker\living-agent-service\living-agent-desktop\` |

---

## 2. 总体实施阶段

| 阶段 | 内容 | 估时 | 状态 |
|------|------|------|------|
| **P1 基础骨架** | 项目初始化（package.json / tsconfig / electron-vite） | 0.5 天 | ✅ 已完成 |
| **P2 主进程** | 窗口/托盘/菜单/IPC/连接/shortcuts/auth/notifications | 2 天 | ✅ 已完成 |
| **P3 Preload + 类型** | 暴露 `window.livingAgentAPI` + 类型声明 | 0.5 天 | ✅ 已完成 |
| **P4 渲染层接入** | ~~electron-vite 复用 frontend~~ → 改为**独立渲染层**（src/renderer/index.html + main.tsx + App.tsx） | 0.5 天 | ✅ 已完成 |
| **P5 本地产物保存** | LocalSaveConfig + LocalSaveSyncService | 1.5 天 | ✅ 已完成 |
| **P6 公共任务栏** | 页面 + 托盘 + OS 通知 + 快捷键 + 悬浮窗 + 本地缓存 | 4 天 | ✅ 已完成（含后端事件） |
| **P7 产物权限控制** | 后端 API 权限过滤 + 前端 UI Tabs + 桌面端同步 | 2 天 | ✅ 已完成 |
| **P8 依赖与类型修复** | vite/electron-vite/plugin-react 版本对齐 + Electron 42 API 调整 + tsconfig 6.0 deprecation | 0.5 天 | ✅ 已完成 |
| **P9 桌面端独立性 + clientId** | 与 `frontend/` 完全解耦（移除 tsconfig 跨目录、electron.vite.config renderer 改用本地）；新增 `client-id.ts` + HTTP `X-Client-Id` + WS `clientId` 携带；新增 `taskboard:list/claim` / `credits:get-balance` IPC；独立 `PublicTaskBoard` 组件 | 1.5 天 | ✅ 已完成 |
| **P10a 登录对齐 frontend** | ~~window.prompt() 粘贴 token~~ → 手机号+短信验证码登录（与 frontend Login.tsx 完全对齐）；新增 `sendSmsCode` / `phoneLogin` / `getCurrentUser` API；IPC handler + preload 暴露；登录表单 UI（手机号输入+验证码发送+倒计时+测试模式自动填入）；登录后获取用户信息并展示 | 1 天 | ✅ 已完成 |
| **P10b 构建与验证** | electron-builder 打包 + npm run dev 启动验证 + 文档收口 | 1 天 | 🔲 待完成 |
| **P11 桌面端功能集成** | 移除浏览器跳转，改为直连后端 API；侧边栏按权限分类（基础功能 + 管理功能）；新增部门聊天/项目/审批/消息/管理设置页面；管理功能无权限时隐藏 | 1 天 | ✅ 已完成（2026-06-16） |
| **总计** | | **~16 天** | - |

---

## 3. 目录结构

```
living-agent-desktop/
├── package.json
├── tsconfig.json
├── tsconfig.node.json                 # 主进程 + preload + shared
├── tsconfig.web.json                  # 渲染层（renderer + types + shared，不含 frontend 跨目录）
├── electron.vite.config.ts            # renderer 根改用本地 src/renderer
├── electron-builder.yml
├── index.html                         # 主进程加载的 dev 入口（vite serve）
├── README.md
├── LIVING_AGENT_DESKTOP_PLAN.md       # 本文档
├── DEPENDENCIES.md                    # 依赖清单（精确版本锁定）
├── src/
│   ├── main/                        # Electron 主进程
│   │   ├── index.ts                 # 入口（app/ready/window-all-closed）
│   │   ├── window.ts                # 窗口管理（主窗口+悬浮窗）
│   │   ├── tray.ts                  # 系统托盘 + 任务栏红点
│   │   ├── menu.ts                  # 菜单栏
│   │   ├── ipc.ts                   # IPC handlers 注册中心
│   │   ├── connection.ts            # 后端连接检测
│   │   ├── shortcuts.ts             # 全局快捷键
│   │   ├── auth.ts                  # 鉴权 + safeStorage
│   │   ├── notifications.ts         # OS 通知
│   │   ├── api-client.ts            # 后端 REST 客户端
│   │   ├── ws-client.ts             # 后端 WebSocket 客户端
│   │   ├── local-save-config.ts     # 本地保存配置
│   │   ├── local-save-sync.ts       # 本地保存同步服务
│   │   ├── task-board-tray.ts       # 任务栏托盘红点
│   │   ├── task-notification.ts     # 任务 OS 通知
│   │   ├── task-board-cache.ts      # 任务本地缓存
│   │   ├── floating-task-board.ts   # 任务中心悬浮窗
│   │   ├── client-id.ts             # ⭐ 客户端唯一标识（UUID 持久化到 userData）
│   │   ├── app-scanner.ts           # ⭐ 本机应用扫描器（注册表+开始菜单+桌面快捷方式）
│   ├── preload/
│   │   └── index.ts                 # contextBridge 暴露 window.livingAgentAPI
│   ├── renderer/                    # ⭐ 独立渲染层（与 frontend/ 完全解耦）
│   │   ├── index.html               # 桌面端独立 HTML 入口
│   │   ├── main.tsx                 # React 入口
│   │   ├── App.tsx                  # 桌面端应用壳（侧边栏 + 视图路由 + clientId 展示）
│   │   ├── index.css                # 桌面端基础样式
│   │   ├── vite-env.d.ts            # *.css / *.svg 等模块声明
│   │   ├── components/              # 桌面端独立组件
│   │   │   ├── PublicTaskBoard.tsx  # ⭐ 桌面端独立实现（不复用 frontend）
│   │   │   └── PublicTaskBoard.css  # 桌面端独立样式（类名前缀 .desktop-public-task-board）
│   │   └── pages/                   # 桌面端页面
│   │       ├── Settings/
│   │       │   └── LocalSave.tsx    # 本地保存设置
│   │       └── TaskBoard/
│   │           └── PublicTaskBoardPage.tsx
│   ├── shared/                      # 主进程+渲染进程共享
│   │   ├── types.ts
│   │   ├── constants.ts
│   │   └── api-types.ts             # ⭐ LivingAgentAPI 类型契约（避免跨目录 import）
│   └── types/
│       └── electron-api.d.ts        # window.livingAgentAPI 类型声明
├── assets/
│   ├── tray-normal.png
│   ├── tray-badge-red.png
│   └── logo.png
├── scripts/
│   ├── check-deps.js                # 依赖审计（精确版本锁定）
│   └── verify-lockfile.js           # lockfile 完整性验证
└── data/                            # 运行时数据（git ignore）
    ├── client-id.json               # ⭐ 客户端唯一标识
    ├── backend-config.json          # 后端 URL
    └── token.dat                    # safeStorage 加密 token
```

---

## 4. 关键技术决策

### 4.1 渲染层本地化（不复用 frontend）

```typescript
// electron.vite.config.ts
import { defineConfig, externalizeDepsPlugin } from 'electron-vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  main: {
    plugins: [externalizeDepsPlugin()],
    build: {
      outDir: 'dist/main',
      rollupOptions: { input: resolve(__dirname, 'src/main/index.ts') }
    },
    resolve: { alias: { '@shared': resolve(__dirname, 'src/shared') } }
  },
  preload: {
    plugins: [externalizeDepsPlugin()],
    build: {
      outDir: 'dist/preload',
      rollupOptions: { input: resolve(__dirname, 'src/preload/index.ts') }
    }
  },
  renderer: {
    // ⭐ 桌面端独立渲染层根（不是 ../frontend）
    root: resolve(__dirname, 'src/renderer'),
    plugins: [react()],
    build: {
      outDir: resolve(__dirname, 'dist/renderer'),
      emptyOutDir: true,
      rollupOptions: { input: resolve(__dirname, 'src/renderer/index.html') }
    },
    resolve: { alias: { '@shared': resolve(__dirname, 'src/shared') } },
    server: { port: 5174 }
  }
});
```

**与 `frontend/` 边界（tsconfig.web.json）**：

```json
{
  "include": [
    "src/renderer/**/*",
    "src/types/**/*",
    "src/shared/**/*"
    // ❌ 不含 ../frontend/src/**/*（已移除）
  ],
  "paths": {
    "@shared/*": ["src/shared/*"]
    // ❌ 不含 "@/*": ["../frontend/src/*"]（已移除）
  }
}
```

任何 `import xxx from '@/...'` 都将编译失败 → 强制 renderer 走本地代码。

### 4.2 IPC 暴露

```typescript
// src/preload/index.ts
contextBridge.exposeInMainWorld('livingAgentAPI', {
  // 后端连接
  checkBackend: () => ipcRenderer.invoke('backend:check'),
  getBackendUrl: () => ipcRenderer.invoke('backend:get-url'),
  setBackendUrl: (url: string) => ipcRenderer.invoke('backend:set-url', url),
  
  // 鉴权
  auth: {
    getToken: () => ipcRenderer.invoke('auth:get-token'),
    setToken: (t: string) => ipcRenderer.invoke('auth:set-token'),
    clearToken: () => ipcRenderer.invoke('auth:clear-token'),
    // ⭐ 手机号+验证码登录（与 frontend 对齐）
    smsSend: (phone: string, type?: string) => ipcRenderer.invoke('auth:sms-send', phone, type || 'login'),
    phoneLogin: (phone: string, code: string) => ipcRenderer.invoke('auth:phone-login', phone, code),
    me: () => ipcRenderer.invoke('auth:me')
  },
  
  // 文件系统
  openArtifact: (path: string) => ipcRenderer.invoke('fs:open-artifact', path),
  showInFolder: (path: string) => ipcRenderer.invoke('fs:show-in-folder', path),
  
  // OS 通知
  notify: (title: string, body: string) => ipcRenderer.invoke('notify', title, body),
  
  // 本地保存
  localSave: {
    getConfig: () => ipcRenderer.invoke('localsave:get-config'),
    setConfig: (cfg: any) => ipcRenderer.invoke('localsave:set-config', cfg),
    choosePath: () => ipcRenderer.invoke('localsave:choose-path'),
    openFolder: () => ipcRenderer.invoke('localsave:open-folder'),
    triggerSync: () => ipcRenderer.invoke('localsave:sync'),
    getStats: () => ipcRenderer.invoke('localsave:stats'),
    onSaved: (cb: (info: any) => void) => ipcRenderer.on('localsave:saved', (_, info) => cb(info))
  },
  
  // 任务栏
  taskBoard: {
    getPendingCount: () => ipcRenderer.invoke('taskboard:pending-count'),
    refresh: () => ipcRenderer.invoke('taskboard:refresh'),
    onNewTask: (cb: (task: any) => void) => ipcRenderer.on('taskboard:new-task', (_, t) => cb(t))
  },
  
  // 任务中心悬浮窗
  floating: {
    show: () => ipcRenderer.invoke('floating:show'),
    hide: () => ipcRenderer.invoke('floating:hide')
  },
  
  // 窗口控制
  window: {
    minimizeToTray: () => ipcRenderer.invoke('window:minimize-to-tray'),
    show: () => ipcRenderer.invoke('window:show'),
    quit: () => ipcRenderer.invoke('window:quit')
  },
  
  // 应用信息
  app: {
    getVersion: () => ipcRenderer.invoke('app:version'),
    getPlatform: () => ipcRenderer.invoke('app:platform')
  }
});
```

### 4.3 安全与权限

- 严格遵循 [权限与入口矩阵.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/权限与入口矩阵.md)
- Token 通过 `safeStorage` 加密保存
- 所有 LLM 调用走后端（不直连 Provider）
- 不硬编码模型名/Provider 名
- 所有 API 路径不带末尾斜杠
- 严格 ApiResponse 格式
- 跨部门公开标记需有可见性元数据

---

## 5. 公共任务栏桌面端适配（§6.19 实施）

### 5.1 桌面端独立 `PublicTaskBoard` 组件

```tsx
// src/renderer/pages/TaskBoard/PublicTaskBoardPage.tsx
/**
 * 桌面端独立实现：与 web 端 frontend/src/components/PublicTaskBoard 完全分离
 * 通过 window.livingAgentAPI.taskBoard.list/claim 与 credits.getBalance IPC 调后端
 */
import PublicTaskBoard from '../../components/PublicTaskBoard';

export function PublicTaskBoardPage() {
  return (
    <div className="desktop-task-board-page" style={{ padding: 24 }}>
      <header><h1>📋 公共任务栏</h1></header>
      <PublicTaskBoard />
    </div>
  );
}
```

**与 web 端组件的关键差异**：

| 项 | web 端（`frontend/src/components/PublicTaskBoard.tsx`） | desktop 端（`src/renderer/components/PublicTaskBoard.tsx`） |
|---|---|---|
| API 调用 | `taskApi.getPublicTasks()` / `creditApi.getBalance()` | `window.livingAgentAPI.taskBoard.list(dept)` / `credits.getBalance()` |
| 鉴权状态 | `useAuthStore`（web 端 Zustand） | `window.livingAgentAPI.auth.getToken()` + 本地 useState |
| 样式类名 | 全局 `PublicTaskBoard` | 前缀 `.desktop-public-task-board` 隔离 |
| 依赖路径 | 业务组件树 | 桌面端独立 React 组件树 |

desktop 端不 import web 端任何文件。安装包中 desktop 完整可独立运行；web 端 `frontend/` 仍独立部署在服务端。

---### 5.2 托盘红点

- 启动时拉取 `taskApi.getPublicTasks()` 数
- 订阅 WebSocket `public_task_published` 事件（后端新增）
- 5 分钟兜底轮询
- 待接取 > 0 → 切换 `tray-badge-red.png`

### 5.3 OS 通知

- 仅 priority ≥ 3（高/紧急）触发
- 用户可配置：免打扰时段、过滤部门、自动接取（默认关闭）

### 5.4 全局快捷键

- `Ctrl+Shift+T`：打开任务中心
- `Ctrl+Shift+C`：快速接取最优先任务

### 5.5 任务中心悬浮窗

- 320×480，无边框 + 透明 + alwaysOnTop + skipTaskbar
- 默认隐藏，用户主动展开
- 折叠态：待接取数；展开态：任务列表

### 5.6 任务本地缓存

- 路径：`{baseDir}/cache/task-board/{year}/{month}/{dept}.json`
- 每部门最多 100 条，TTL 24h
- 离线时显示缓存
- 多账号切换时清理

### 5.7 任务权限规则

| 角色 | 可见 | 接取 | 接取后产物 visibility |
|------|------|------|---------------------|
| 未登录 | ❌ | ❌ | - |
| 普通员工 | 本部门+PUBLIC | ✅ | DEPARTMENT（默认） |
| 部门负责人 | 本部门全部 | ✅ | DEPARTMENT |
| 董事长/FULL | 全部 | ✅ | DEPARTMENT |
| 跨部门 | 仅 PUBLIC | ✅ | RESTRICTED |

---

## 6. 产物访问权限控制（§6.18 实施）

### 6.1 数据模型扩展（Flyway migration）

```sql
-- V20260604__add_artifact_visibility.sql
ALTER TABLE artifact_record
  ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'DEPARTMENT',
  ADD COLUMN created_by VARCHAR(100),
  ADD COLUMN participant_ids TEXT,
  ADD COLUMN viewer_departments TEXT,
  ADD COLUMN visible_to_leader BOOLEAN NOT NULL DEFAULT TRUE;
```

### 6.2 后端服务

- `ArtifactAccessService.canView(record, user)` - 权限校验
- `ArtifactController` 接入权限过滤
- 新增 `GET /api/artifacts/my-visible` API
- `ToolBackedEmployeeTaskExecutor.saveArtifactFile()` 写入时设置 visibility

### 6.3 前端 UI

- `artifactService.listMyVisible()` - 默认使用
- 产物列表 Tabs：`我的` / `本部门` / `全部`（仅 FULL/董事长）
- 产物卡片权限标签：`我的` / `本部门` / `可见`

### 6.4 桌面端同步

- 启动时按当前用户权限全量同步本地副本
- 角色变更（从领导降为员工、跨部门调动）触发重同步
- 多账号切换清理上个账号副本

---

## 7. 实施步骤（细化）

### Step 1：项目初始化（P1）
- [x] 创建 `living-agent-desktop/` 目录
- [x] 创建 `package.json`（electron + electron-vite + react）
- [x] 创建 `tsconfig.json` + `tsconfig.node.json` + `tsconfig.web.json`
- [x] 创建 `electron.vite.config.ts`（renderer 复用 frontend）
- [x] 创建 `index.html` 入口
- [x] 创建 `.gitignore`
- [x] 创建 `README.md`

### Step 2：主进程（P2）
- [x] `src/main/index.ts` 入口
- [x] `src/main/window.ts` 主窗口
- [x] `src/main/tray.ts` 托盘
- [x] `src/main/menu.ts` 菜单栏
- [x] `src/main/connection.ts` 后端连接检测
- [x] `src/main/auth.ts` 鉴权（safeStorage）
- [x] `src/main/shortcuts.ts` 全局快捷键
- [x] `src/main/notifications.ts` OS 通知
- [x] `src/main/api-client.ts` REST 客户端
- [x] `src/main/ws-client.ts` WebSocket 客户端
- [x] `src/main/ipc.ts` IPC 注册中心

### Step 3：Preload（P3）
- [x] `src/preload/index.ts` 暴露 API
- [x] `src/types/electron-api.d.ts` 类型声明
- [x] `src/shared/types.ts` 共享类型

### Step 4：渲染层接入（P4）—— 后调整为"独立渲染层"
- [x] **独立** `src/renderer/index.html`（不复用 `frontend/index.html`）
- [x] **独立** `src/renderer/main.tsx` + `App.tsx`（不复用 `frontend/src/App.tsx`）
- [x] 检测 Electron 环境变量
- [x] 桌面端独立路由：概览 / 公共任务栏 / 本地保存

### Step 5：本地产物保存（P5）
- [x] `local-save-config.ts` 配置存储
- [x] `local-save-sync.ts` 同步服务
- [x] IPC handlers
- [x] 默认路径计算
- [x] `LocalSave.tsx` 设置 UI

### Step 6：公共任务栏桌面端（P6）
- [x] `task-board-tray.ts` 托盘红点
- [x] `task-notification.ts` OS 通知
- [x] `task-board-cache.ts` 本地缓存
- [x] `floating-task-board.ts` 悬浮窗
- [x] `renderer/components/PublicTaskBoard.tsx` **独立组件**（不复用 web 端）
- [x] `renderer/pages/TaskBoard/PublicTaskBoardPage.tsx`
- [x] IPC handlers
- [x] 后端 `public_task_published` WebSocket 事件
- [x] 后端 `TaskClaimService` 提交时设置 visibility

### Step 7：产物权限控制（P7）
- [x] 数据库 migration
- [x] `ArtifactRecord` 实体扩展
- [x] `ArtifactAccessService` 权限服务
- [x] `ArtifactController` 接入
- [x] `ToolBackedEmployeeTaskExecutor` 写入
- [x] 前端 `artifactService.listMyVisible()` + UI Tabs
- [x] 桌面端 `local-save-sync.ts` 角色变更重同步

### Step 8：依赖与类型修复（P8）
- [x] 依赖版本对齐（vite 7.3.5 / electron-vite 5.0.0 / @vitejs/plugin-react 5.2.0 / electron 42.3.3）
- [x] `package-lock.json` 重新生成
- [x] `verify-lockfile.js` 逻辑修正（key 规范化）
- [x] Electron 42 API 调整：`app.isQuitting` → `(app as any).isQuitting`、`notification.on('action')` 第二参数 `actionIndex`
- [x] `tsconfig.json` 加 `ignoreDeprecations: "6.0"` 兼容 baseUrl
- [x] 修复 `local-save-config.ts` / `local-save-sync.ts` / `api-client.ts` / `window.ts` / `notifications.ts` 类型错误

### Step 9：桌面端独立性 + 客户端标识（P9）
- [x] `tsconfig.web.json` 移除 `@/*` 跨目录别名与 `../frontend/src/**/*` include
- [x] `electron.vite.config.ts` renderer root 改用本地 `src/renderer`
- [x] 创建 `src/renderer/index.html` / `main.tsx` / `App.tsx` / `index.css` / `vite-env.d.ts`
- [x] 创建 `src/renderer/components/PublicTaskBoard.tsx` / `PublicTaskBoard.css` 独立组件
- [x] 创建 `src/shared/api-types.ts`（LivingAgentAPI 类型契约，避免跨目录 import）
- [x] 移除 `LocalSave.tsx` 中重复的 `declare global`（与 `electron-api.d.ts` 冲突）
- [x] 创建 `src/main/client-id.ts` 客户端唯一标识（UUID 持久化到 userData/client-id.json）
- [x] `api-client.ts` 自动注入 `X-Client-Id` header
- [x] `ws-client.ts` WebSocket URL 携带 `clientId` 参数
- [x] `ipc.ts` 注册 `app:client-id` / `app:client-info` / `app:reset-client-id` handler
- [x] `preload/index.ts` 暴露 `app.getClientId` / `app.getClientInfo` / `app.resetClientId`
- [x] 新增 `taskboard:list` / `taskboard:claim` / `credits:get-balance` IPC
- [x] `App.tsx` HomeView 展示客户端标识
- [x] `npm run typecheck` 完整通过

### Step 10a：登录对齐 frontend（P10a）—— ⭐ 核心改进
- [x] `api-client.ts` 新增 `sendSmsCode()` / `phoneLogin()` / `getCurrentUser()` API（对齐 frontend `/auth/sms/send`、`/auth/phone/login`、`/auth/me`）
- [x] `shared/types.ts` 新增 `DesktopUser` 接口（与 frontend User 对齐）
- [x] `ipc.ts` 新增 `auth:sms-send` / `auth:phone-login` / `auth:me` handler
- [x] `preload/index.ts` 暴露 `auth.smsSend()` / `auth.phoneLogin()` / `auth.me()`
- [x] `shared/api-types.ts` 更新 `LivingAgentAPI.auth` 类型契约 + re-export DesktopUser
- [x] **App.tsx** 登录对话框重构：~~window.prompt() 粘贴 token~~ → 手机号+验证码登录表单
  - 手机号输入（11位限制、自动聚焦）
  - 验证码发送按钮 + 60s 倒计时
  - 测试模式自动填入验证码（与 frontend 行为一致）
  - 错误提示（invalid/expired/not found 分支处理）
  - 加载动画（spinner）
  - 登录成功后自动获取用户信息并展示在 header
- [x] `index.css` 完整登录表单样式（字段/按钮/错误提示/测试模式提示/加载旋转器）

### Step 10b：构建与验证（P10b）—— 待完成
- [ ] `electron-builder.yml` 配置验证
- [ ] npm run dev 启动验证（主流程走通）
- [ ] 打包 Windows x64 安装包
- [ ] 多客户端安装 + clientId 唯一性验证
- [ ] 后端侧 WindowsAppTool 接收 `X-Client-Id` 路由到 pywinauto 节点
- [ ] 编写用户文档
- [ ] 编写开发者文档

---

## 8. 进度跟踪

| 日期 | 完成步骤 | 备注 |
|------|---------|------|
| 2026-06-04 | 落地方案文档创建 | ✅ |
| 2026-06-04 | P1 项目初始化（package.json/tsconfig/electron-vite.config） | ✅ |
| 2026-06-04 | P2 主进程 11 个核心文件 | ✅ |
| 2026-06-04 | P3 Preload 暴露 window.livingAgentAPI | ✅ |
| 2026-06-04 | P4 渲染层路由适配 | ✅ |
| 2026-06-04 | P5 本地产物保存（local-save-config + local-save-sync + LocalSaveSettings UI） | ✅ |
| 2026-06-04 | P6 公共任务栏（task-board-tray/task-notification/task-board-cache/floating-task-board） | ✅ |
| 2026-06-04 | P6 后端事件：PublicTaskEventPublisher + DepartmentWebSocketHandler.broadcastRawJson + TaskController 集成 | ✅ |
| 2026-06-04 | P7 产物权限控制后端：ArtifactAccessService + my-visible API + 权限字段扩展 + migration | ✅ |
| 2026-06-04 | 数据库 migration：V20260604__add_artifact_visibility.sql | ✅ |
| 2026-06-04 | P8 依赖修复：vite 8→7.3.5、electron-vite 5.0.0、@vitejs/plugin-react 6→5.2.0、electron 40→42、tsconfig 6.0 deprecation、Electron 42 API 适配 | ✅ |
| 2026-06-04 | P9 桌面端独立性：移除与 frontend/ 跨目录耦合、electron.vite.config renderer 改用本地、独立 App.tsx / PublicTaskBoard / index.html / main.tsx | ✅ |
| 2026-06-04 | P9 客户端标识：client-id.ts UUID 持久化、HTTP `X-Client-Id`、WS `clientId`、HomeView 展示 | ✅ |
| 2026-06-04 | P9 文档：LIVING_AGENT_DESKTOP_PLAN.md 同步更新（与现状对齐） | ✅ |
| 2026-06-15 | **P10a 登录对齐 frontend**：手机号+验证码登录替代 window.prompt()；sendSmsCode/phoneLogin/getCurrentUser API 全链路（api-client→ipc→preload→App.tsx）；登录表单 UI（倒计时/测试模式/错误提示/加载动画）；DesktopUser 类型对齐 | ✅ |
| 2026-06-15 | **后端 SecurityConfig**：`/api/health` 和 `/api/tasks/public` 加入公开端点列表，解决桌面端连接 403 问题 | ✅ |
| 2026-06-15 | **ModelHealthProber**：新增 minAvailableThreshold 配置（默认3），可用模型充足时跳过探测避免频繁检查 | ✅ |
| 2026-06-15 | **客户端应用列表上报**：新增 app-scanner.ts 扫描本机已安装应用（注册表+开始菜单+桌面快捷方式）；ws-client.ts 连接时自动携带设备信息和应用列表；后端 client_device_registry 表新增 applications 字段；ClientDeviceRegistryService 支持保存和追加应用记录 | ✅ |
| 2026-06-15 | **WebSocket 架构改进**：ws-client.ts 添加 handshakeTimeout（10秒）、指数退避重连（2s→4s→8s→16s→30s）、自动携带设备信息（hostname/macAddress/platform/osUser） | ✅ |
| 待开始 | P10b electron-builder 打包 + npm run dev 启动验证 + 多客户端 clientId 验证 + 后端 WindowsAppTool 路由接入 | - |

---

## 9. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| Electron 打包体积大（~150MB） | 安装包大 | 后续可拆出 web 端只下载必要模块 |
| Windows UAC | 部分操作需管理员 | 首次启动提示获取权限 |
| 杀毒软件误报 | 部署问题 | 申请代码签名证书 |
| Web 端代码耦合 | 复用难 | Preload + IPC 解耦 |
| 渲染进程访问主进程 | 安全 | contextBridge 严格隔离 |
| token 泄露 | 严重 | safeStorage 加密 + 不落盘明文 |
| 本地文件膨胀 | 容量 | 容量限制 + retentionDays 清理 |

---

## 10. 不在本次实施范围

- ❌ Mac/Linux 桌面端（仅 Windows x64）
- ❌ 加密功能（v2 路线图）
- ❌ 云盘直连（v2 路线图）
- ❌ 自动接取（决策点 D3 默认关闭）
- ❌ 跨设备同步（依赖云盘透明实现）

---

## 11. 客户端唯一标识 clientId（P9 关键决策）

### 11.1 设计目标

桌面应用以安装包形式分发到**多台客户端 PC**。后端 `WindowsAppTool` 在调用
`windows_automation` 自动化脚本（pywinauto）时，必须知道是**哪一台物理机**上的
哪个桌面用户在请求，才能路由到对应的 pywinauto 节点。

例如：
- 财务部的"金蝶记账"自动化，可能由固定在 `pc-finance-01` 上的客户端发起
- HR 部的"钉钉消息群发"自动化，可能由 `pc-hr-02` 上的客户端发起
- 后端 `WindowsAppTool` 根据 `X-Client-Id` header 决定调用哪个 pywinauto 节点

### 11.2 实现

| 位置 | 内容 |
|---|---|
| [client-id.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/client-id.ts) | 首次启动生成 v4 UUID，持久化到 `app.getPath('userData')/client-id.json`（含 hostname / platform / osUser / appVersion / createdAt） |
| [api-client.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/api-client.ts) | 所有 HTTP 请求自动注入 `X-Client-Id` header |
| [ws-client.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts) | WebSocket URL 通过 query string `clientId` 传递 |
| [ipc.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ipc.ts) | `app:client-id` / `app:client-info` / `app:reset-client-id` handler |
| [preload](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/preload/index.ts) | `window.livingAgentAPI.app.getClientId()` / `getClientInfo()` / `resetClientId()` |
| [App.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/App.tsx) | HomeView 展示当前 clientId，方便用户/排障人员确认身份 |

### 11.3 后端接入（待排期）

后端需在以下位置识别 `X-Client-Id` 并据此路由：

```
WebSocket 连接: /ws/agent?token=xxx&clientId=xxx
HTTP Header:    X-Client-Id: <uuid>
```

`WindowsAppTool.execute()` 内部：
1. 解析当前请求的 `clientId`（从 WebSocket session 或 ThreadLocal）
2. 查 `node_registry` 表 → 找到 `clientId` 对应的 pywinauto 节点（`pc-finance-01` 上的 5001 端口）
3. 调 `node.call(automation_script, params)` 执行具体自动化
4. 回调结果回写

---

## 12. 桌面端独立性边界（P9 关键决策）

### 12.1 与 `frontend/` 的边界

| 维度 | `frontend/` | `living-agent-desktop/` |
|---|---|---|
| 部署位置 | 服务端（Docker / nginx） | 客户端 PC（electron-builder 打包） |
| 物理机 | 一台 | 多台（每台一个 clientId） |
| 入口文件 | `frontend/index.html` | `src/renderer/index.html` |
| 渲染层代码 | `frontend/src/**` | `src/renderer/**` |
| 共享后端 | ✅ 同一后端（端口 8382） | ✅ 同一后端（端口 8382） |
| 共享前端代码 | — | ❌ 零依赖 |
| 通信方式 | 用户浏览器 | Electron + IPC + HTTP/WS |

### 12.2 强制解耦机制

1. **`tsconfig.web.json` 不含 `../frontend/src/**/*`** —— 任何对 web 端源码的 import 都将编译失败
2. **`tsconfig.web.json` 不含 `"@/*": ["../frontend/src/*"]`** —— 跨目录路径别名已移除
3. **`electron.vite.config.ts` renderer root 改用本地** —— 打包不会拉入 `frontend/` 任何文件
4. **`shared/api-types.ts` 独立定义 LivingAgentAPI** —— 渲染层不依赖 preload 文件
5. **`LocalSave.tsx` 移除重复的 `declare global`** —— 避免覆盖全局类型

### 12.3 复杂功能处理原则

| 场景 | desktop 端做法 |
|---|---|
| 公共任务栏（核心场景） | 独立实现（不依赖 web 端） |
| 本地保存配置 | 独立实现 |
| 后端连接 / 鉴权 | 独立 IPC |
| 部门详情 / 虚拟办公室 / AI 对话 | App.tsx 中点 "🌐 在浏览器中打开" → 跳 `frontend/` |
| 数字员工配置 | 跳 web 端 |
| 任何业务复杂逻辑 | 跳 web 端 |

桌面端**不重复实现 web 端已有功能**，避免维护成本 ×2。

### 12.4 未来扩展点（不在本次范围）

如需在 desktop 窗口内"内嵌 web"，可加：
- IPC `app:openWebInBrowserView(path)` 用 BrowserView 加载 `frontend/` 页面
- desktop 仅做容器角色，web 页面代码仍归 `frontend/` 管
- 这是"嵌 web 端"，**不是"做 web 端"**，代码维护仍归前端团队

---

## 13. 后续重点

### P10b 待完成（构建与验证）
1. **npm run dev 启动验证**：跑通桌面端完整主流程（连接后端 → 手机号登录 → 查看任务 → 接取任务 → 查看积分/产物）
2. **electron-builder 打包**：Windows x64 安装包，签名证书（避免杀毒误报）
3. **多客户端 clientId 验证**：在不同机器安装 desktop，确认每个 clientId 唯一

### P11+ 未来扩展（桌面端独立使用）
4. **后端 WindowsAppTool 接入**：
   - WebSocket 连接解析 `clientId` query
   - HTTP 拦截器读取 `X-Client-Id` header
   - `node_registry` 表建立 `clientId → pywinauto_node` 映射
   - 自动化任务派发时按 clientId 路由
5. **AI 对话内嵌**（P11）：在桌面端窗口内实现部门聊天/AI 对话（当前跳转 web 端），可选 BrowserView 嵌入或独立 WebSocket 直连后端 `/ws/dept/{dept}`
6. **数字员工管理**（P11）：桌面端内嵌员工配置页面（当前跳转 web 端）
7. **HERMES_COMPARISON_AND_BORROWING_PLAN.md 同步**：将 desktop 独立性 + clientId + 登录对齐决策同步到主项目对比文档

---

## 14. WebSocket 架构审查改进项（前端/桌面端）

> 来源：[websocket-architecture-review.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/websocket-architecture-review.md) 核查确认
> 核查日期：2026-06-15

### 14.1 CRITICAL（必须修复）

| # | 问题 | 代码位置 | 修复方案 | 状态 |
|---|------|----------|----------|------|
| 1 | **WebSocket connect() 从未被调用** | [ws-client.ts:33](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L33) | 在 `index.ts` 启动流程中，Token 加载成功后调用 `wsClient.connect('/ws/agent', { clientId })`；Token 变更时重新连接 | ✅ 已修复（2026-06-15） |

**影响**：所有依赖 WebSocket 的功能（实时任务通知、执行事件推送、制品就绪通知）全部不工作。系统退化为 HTTP 轮询模式。

### 14.2 MODERATE（应该修复）

| # | 问题 | 代码位置 | 修复方案 | 状态 |
|---|------|----------|----------|------|
| 3 | **Token 通过 URL 查询参数传递**（不安全） | [ws-client.ts:42-47](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L42) | 改用 `Sec-WebSocket-Protocol` 头传递 token（后端已支持） | ✅ 已修复（2026-06-15） |
| 5 | **WebSocket 未设置 handshakeTimeout** | [ws-client.ts:50](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L50) | `new WebSocket(url, { handshakeTimeout: 10000 })` | ✅ 已修复（2026-06-15） |
| 6 | **重连使用固定 5s 间隔，无指数退避** | [ws-client.ts:80](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts#L80) | 改为指数退避：2s→4s→8s→16s→30s 上限（参考 `DepartmentChatInline.tsx` 实现） | ✅ 已修复（2026-06-15） |
| 9 | **safeStorage 不可用时明文存储 token** | [auth.ts:34-37](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/auth.ts#L34) | 明文降级时弹出警告，或拒绝登录而非明文存储 | ✅ 已修复（2026-06-15） |
| 10 | **无 Token 刷新机制** | api-client.ts 全文件 | 登录时保存 refreshToken，定时检查过期，401 响应拦截自动刷新 | ✅ 已修复（2026-06-15） |

### 14.3 MINOR（可以改进）

| # | 问题 | 代码位置 | 修复方案 | 状态 |
|---|------|----------|----------|------|
| 14 | **DEFAULT_BACKEND_URL 为空字符串** | [types.ts:17](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/shared/types.ts#L17) | 改为 `http://localhost:8382`（已修复） | ✅ 已修复 |
| 15 | **sandbox: false** | [window.ts:21](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/window.ts#L21) | 生产环境改为 `sandbox: true` + 确保 contextBridge 满足所有 IPC 需求 | ✅ 已修复（2026-06-15） |
| 16 | **computeStats() 返回零值** | [local-save-config.ts:110-121](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/local-save-config.ts#L110) | 实现异步分批遍历目录计算文件数和总大小 | ✅ 已修复（2026-06-15） |

### 14.4 修复优先级路线图

```
✅ P0: #1 WebSocket connect() 从未被调用 → 已修复（2026-06-15）
✅ P1: #3 Token 传递方式迁移到 Sec-WebSocket-Protocol → 已修复（2026-06-15）
✅ P1: #6 重连改为指数退避 → 已修复（2026-06-15）
✅ P1: #10 Token 刷新机制 → 已修复（2026-06-15）
✅ P2: #5 handshakeTimeout → 已修复（2026-06-15）
✅ P2: #9 明文存储降级警告 → 已修复（2026-06-15）
✅ P3: #15 sandbox: true → 已修复（2026-06-15）
✅ P3: #16 computeStats() 实现 → 已修复（2026-06-15）
```

---

**文档状态**：与代码实现同步（2026-06-15 更新到 P10a 登录对齐完成，WebSocket 架构改进全部完成，P10b 待验证）

---

## 14. 当前状态总览与剩余项

### 已完成（P1~P10a，共 10 个阶段）

| 阶段 | 状态 | 核心交付物 |
|------|------|-----------|
| P1 基础骨架 | ✅ | 项目初始化、package.json、tsconfig、electron-vite |
| P2 主进程 | ✅ | 窗口/托盘/菜单/IPC/连接/shortcuts/auth/notifications/api-client/ws-client |
| P3 Preload + 类型 | ✅ | contextBridge 暴露 window.livingAgentAPI |
| P4 渲染层接入 | ✅ | 独立渲染层（index.html/main.tsx/App.tsx） |
| P5 本地产物保存 | ✅ | LocalSaveConfig + LocalSaveSyncService + UI |
| P6 公共任务栏 | ✅ | 托盘红点/OS通知/快捷键/悬浮窗/本地缓存/独立组件 |
| P7 产物权限控制 | ✅ | 后端权限过滤 + 前端 Tabs + 桌面端同步 |
| P8 依赖修复 | ✅ | 版本对齐/Electron 42 API/tsconfig 6.0 |
| P9 桌面端独立性 + clientId | ✅ | 完全解耦 frontend/、UUID 客户端标识、HTTP/WS 携带 |
| **P10a 登录对齐 frontend** | **✅** | **手机号+验证码登录（替代 window.prompt）、全链路 API 对齐、完整 UI** |

### 待完成（P10b）

| # | 任务 | 优先级 | 说明 |
|---|------|--------|------|
| 1 | `npm run dev` 启动验证 | P0 | 跑通完整主流程：连接→登录→任务→积分→产物 |
| 2 | electron-builder 打包 | P0 | Windows x64 安装包 |
| 3 | 多客户端 clientId 验证 | P1 | 不同机器安装后确认唯一性 |
| 4 | 后端 WindowsAppTool 接入 X-Client-Id | P1 | pywinauto 节点路由 |
| 5 | 用户文档 / 开发者文档 | P2 | 安装指南 + 开发者手册 |

### 未来扩展（P11+，桌面端独立使用）

| # | 功能 | 说明 | 当前方案 |
|---|------|------|----------|
| 1 | AI 对话 | 部门聊天/AI 对话内嵌到桌面端 | 跳转 web 端 → 可改为 BrowserView 或独立 WS 直连 `/ws/dept/{dept}` |
| 2 | 数字员工管理 | 员工配置/查看页面 | 跳转 web 端 → 可改为独立页面 |
| 3 | 部门详情/虚拟办公室 | 办公室视图 | 跳转 web 端 → 可改为独立页面或 BrowserView |

> **核心结论**：P1-P10a 全部代码已完成，TypeScript 编译通过。**当前阻塞项是 P10b 的启动验证和打包**。一旦 npm run dev 验证通过并打包成功，桌面端即可作为独立客户端分发使用。
