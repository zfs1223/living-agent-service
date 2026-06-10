# Living Agent Desktop 落地方案与进度跟踪

> 创建日期：2026-06-04
> 依据：[HERMES_COMPARISON_AND_BORROWING_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/HERMES_COMPARISON_AND_BORROWING_PLAN.md) §3 §5 §6 §6.18 §6.19
> 目标：基于 Electron + Vite + React + TypeScript 构建**独立桌面客户端** living-agent-desktop
> 与后端 Web 服务（端口 8382）通过 HTTP/WS 通信；与 `frontend/` 完全解耦，**不复用其任何代码**

---

## 1. 项目定位

| 维度 | 说明 |
|------|------|
| **目标用户** | 数字员工/管理员需要在本地 PC 客户端管理任务、查看虚拟办公室、接收系统通知 |
| **与 Web 端差异** | 系统托盘、本地文件访问、离线缓存、OS 通知、本地产物保存、任务中心悬浮窗、客户端唯一标识 |
| **后端复用** | **API 层面**复用 living-agent-service 后端（WebSocket + REST，端口 8382） |
| **代码基础** | **不复用 frontend/ 任何代码**。渲染层入口、CSS、组件、类型都位于 `living-agent-desktop/src/renderer/` 独立维护 |
| **部署位置** | **客户端机器**（用户 PC），以安装包形式分发（electron-builder 打包 Windows x64） |
| **与 frontend 的关系** | 两个**完全独立**的应用，部署在**不同物理机器**。desktop 通过 HTTP/WS 走后端 API 复用同一后端服务；复杂业务（部门详情/虚拟办公室/AI 对话）由 desktop 主动跳转 `frontend/` 完成 |
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
| **P10 构建与验证** | electron-builder + 启动测试 + 文档收口 | 1 天 | 🔲 待启动验证 |
| **总计** | | **~14 天** | - |

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
│   │   └── client-id.ts             # ⭐ 客户端唯一标识（UUID 持久化到 userData）
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
    clearToken: () => ipcRenderer.invoke('auth:clear-token')
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

### Step 10：构建与验证（P10）
- [ ] `electron-builder.yml` 配置
- [ ] npm run dev 验证启动
- [ ] 打包 Windows x64
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
| 待开始 | P10 npm run dev 启动验证 + electron-builder 打包 + 多客户端 clientId 验证 + 后端 WindowsAppTool 路由接入 | - |

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

1. **P10 启动验证**：npm run dev 跑通 desktop 主流程，验证 clientId 持久化、托盘/悬浮窗/通知/快捷键
2. **多客户端 clientId 验证**：在不同机器安装 desktop，确认每个 clientId 唯一
3. **后端 WindowsAppTool 接入**：
   - WebSocket 连接解析 `clientId` query
   - HTTP 拦截器读取 `X-Client-Id` header
   - `node_registry` 表建立 `clientId → pywinauto_node` 映射
   - 自动化任务派发时按 clientId 路由
4. **electron-builder 打包**：Windows x64 安装包，签名证书（避免杀毒误报）
5. **HERMES_COMPARISON_AND_BORROWING_PLAN.md 同步**：将 desktop 独立性 + clientId 决策同步到主项目对比文档

---

**文档状态**：与代码实现同步（2026-06-04 更新到 P9 完工）
