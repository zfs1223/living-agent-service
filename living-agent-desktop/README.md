# Living Agent Desktop

> Living Agent Service 的桌面客户端，基于 Electron + Vite + React + TypeScript。
> 复用 `living-agent-service/frontend` 渲染层，桥接后端 Web API。

## 文档

- 详细计划：[LIVING_AGENT_DESKTOP_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/LIVING_AGENT_DESKTOP_PLAN.md)
- 依赖清单与安全：[DEPENDENCIES.md](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/DEPENDENCIES.md)
- 上游依据：[HERMES_COMPARISON_AND_BORROWING_PLAN.md §3 §5 §6](file:///f:/SoarCloudAI/docker/living-agent-service/docs/HERMES_COMPARISON_AND_BORROWING_PLAN.md)
- 权限矩阵：[权限与入口矩阵.md](file:///f:/SoarCloudAI/docker/living-agent-service/docs/权限与入口矩阵.md)

## 快速开始

### 环境要求

- Node.js 18+
- npm 9+（或 pnpm）
- 后端服务（端口 8382）

### 安装与启动

```bash
# 1. 安装依赖（自动运行依赖审计）
npm install

# 2. 验证 lockfile 完整性
npm run verify:lockfile

# 3. 开发模式（自动启动 Electron + Vite HMR）
npm run dev

# 4. 类型检查
npm run typecheck

# 5. 构建生产包
npm run build

# 6. 打包 Windows x64
npm run build:win
```

### 安全审计

```bash
# 依赖审计（preinstall 自动运行）
npm run audit:deps

# 仅生产依赖安全审计
npm run audit:security

# 完整安全审计（包含开发依赖）
npm run audit:security:full
```

## 核心能力

| 能力 | 详细 |
|------|------|
| **虚拟办公室** | 复用 `frontend/pages/DepartmentDetail` |
| **本地产物保存** | 详见 [HERMES_COMPARISON_AND_BORROWING_PLAN.md §6](file:///f:/SoarCloudAI/docker/living-agent-service/docs/HERMES_COMPARISON_AND_BORROWING_PLAN.md) |
| **公共任务栏** | 托盘红点 + OS 通知 + 悬浮窗 + 本地缓存 |
| **WindowsAppTool** | Python 节点管理（详见 §4） |
| **产物权限控制** | 按角色过滤可见产物（详见 §6.18） |

## 项目结构

```
living-agent-desktop/
├── src/
│   ├── main/                  # Electron 主进程
│   ├── preload/               # Preload（暴露 window.livingAgentAPI）
│   ├── renderer/              # 桌面端特有页面（复用 frontend）
│   ├── shared/                # 主进程+渲染进程共享类型
│   └── types/                 # 类型声明
├── electron.vite.config.ts
├── package.json
└── LIVING_AGENT_DESKTOP_PLAN.md
```

## 状态

🚧 实施中 - 详见 [LIVING_AGENT_DESKTOP_PLAN.md](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/LIVING_AGENT_DESKTOP_PLAN.md) §8
