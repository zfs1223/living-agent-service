# Living Agent Desktop - 依赖清单

> **目的**：列出所有直接依赖，说明用途、来源、版本锁定原因、风险评估
>
> **更新日期**：2026-07-23
>
> **变更摘要**：相对 2026-06-04 基线，新增 Calendar（@fullcalendar）与 Meeting（@livekit）功能所需的运行时依赖，并同步 devDependencies 到当前实际版本。注意：本清单以 `package.json` / 源码 `import` 为准，安装以 `npm install` + `package-lock.json` 落地。

---

## 1. 锁定策略总览

| 维度 | 措施 | 位置 |
|------|------|------|
| **直接依赖** | 精确版本（无 `^`/`~`/通配符） | [package.json](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/package.json) |
| **间接依赖** | `overrides` 强制覆盖 + `package-lock.json` 完整性 | [package.json#overrides](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/package.json) + [package-lock.json](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/package-lock.json) |
| **安装脚本** | `ignore-scripts=true`（默认不跑 postinstall） | [.npmrc](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/.npmrc) |
| **Registry** | 强制 npm 官方 | [.npmrc](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/.npmrc) |
| **预安装审计** | `preinstall` 钩子 + `check-deps.js` | [scripts/check-deps.js](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/scripts/check-deps.js) |
| **Lockfile 验证** | `verify:lockfile` 脚本 | [scripts/verify-lockfile.js](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/scripts/verify-lockfile.js) |
| **安全审计** | `npm audit` 集成 | scripts.audit:* |

> ⚠️ **审计钩子说明**：`.npmrc` 中 `ignore-scripts=true` 会禁用 `preinstall` 钩子，因此日常 `npm install` 不会触发 `check-deps.js`。如需强制审计，请显式运行 `npm run audit:deps`（其直接调用脚本，不受 `ignore-scripts` 影响）。该脚本要求所有直接依赖为**精确版本**（禁止 `^`/`~`/`x`）。

---

## 2. 直接依赖（22 个）

### 2.1 dependencies（运行时，10 个）

| 包名 | 版本 | 用途 | 维护者 | 风险评估 |
|------|------|------|--------|----------|
| [@fullcalendar/react](https://www.npmjs.com/package/@fullcalendar/react) | `6.1.21` | 日历 React 组件封装（[src/renderer/pages/Calendar/CalendarPage.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/Calendar/CalendarPage.tsx)） | fullcalendar | 🟢 **低风险** — 主流日历库，维护活跃 |
| [@fullcalendar/core](https://www.npmjs.com/package/@fullcalendar/core) | `6.1.21` | FullCalendar 核心（提供 `locales/zh-cn` 等） | fullcalendar | 🟢 **低风险** |
| [@fullcalendar/daygrid](https://www.npmjs.com/package/@fullcalendar/daygrid) | `6.1.21` | 月/日网格视图插件 | fullcalendar | 🟢 **低风险** |
| [@fullcalendar/timegrid](https://www.npmjs.com/package/@fullcalendar/timegrid) | `6.1.21` | 周/日时间轴视图插件 | fullcalendar | 🟢 **低风险** |
| [@fullcalendar/interaction](https://www.npmjs.com/package/@fullcalendar/interaction) | `6.1.21` | 拖拽/选择交互插件 | fullcalendar | 🟢 **低风险** |
| [@livekit/components-react](https://www.npmjs.com/package/@livekit/components-react) | `2.9.23` | 音视频会议 React 组件（[src/renderer/pages/Meeting/MeetingRoom.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/renderer/pages/Meeting/MeetingRoom.tsx)） | livekit | 🟢 **低风险** — LiveKit 官方 SDK |
| [@livekit/components-styles](https://www.npmjs.com/package/@livekit/components-styles) | `1.2.0` | LiveKit 组件样式 | livekit | 🟢 **低风险** — ⚠️ **仅 1.x 线**，切勿写成 `^2.0.0`（不存在，会导致 `ETARGET`） |
| [livekit-client](https://www.npmjs.com/package/livekit-client) | `2.20.2` | LiveKit 客户端 SDK（与 components-react 配套） | livekit | 🟢 **低风险** — LiveKit 官方 SDK |
| [date-fns](https://www.npmjs.com/package/date-fns) | `3.6.0` | 时间格式化/计算（Calendar 页面） | date-fns | 🟢 **低风险** — 主流日期库，选用 v3 以匹配 `date-fns/locale` 命名导入写法 |
| [ws](https://www.npmjs.com/package/ws) | `8.21.0` | WebSocket 客户端（[src/main/ws-client.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts)） | websockets (websockets/ws) | 🟢 **低风险** — 最流行的 WebSocket 实现，npm 周下载量 1.5 亿+ |

> **变更说明（相对 2026-06-04 基线）**：
> - 新增 `@fullcalendar/react` / `@fullcalendar/core` / `@fullcalendar/daygrid` / `@fullcalendar/timegrid` / `@fullcalendar/interaction`（`6.1.21`）— Calendar 页面功能，2026-07 新增。
> - 新增 `@livekit/components-react`（`2.9.23`）/ `@livekit/components-styles`（`1.2.0`）/ `livekit-client`（`2.20.2`）— Meeting 音视频会议功能，2026-07 新增。
> - 新增 `date-fns`（`3.6.0`）— 时间处理。
> - `ws` 由 `8.18.0` 升级到 `8.21.0`（仍为直接依赖，已从 `overrides` 移除，避免重复锁定）。
> - 历史项：`electron-store@8.2.0`、`glob@10.4.5` 已移除（当前实现未使用，减少攻击面）；`ws` 由“未声明但使用”改为正式声明。

### 2.2 devDependencies（开发时，12 个）

| 包名 | 版本 | 用途 | 维护者 | 风险评估 |
|------|------|------|--------|----------|
| [@types/node](https://www.npmjs.com/package/@types/node) | `25.9.1` | Node.js API 类型定义 | microsoft/DefinitelyTyped | 🟢 **低风险** — 官方维护 |
| [@types/react](https://www.npmjs.com/package/@types/react) | `19.2.16` | React 类型定义 | react/DefinitelyTyped | 🟢 **低风险** |
| [@types/react-dom](https://www.npmjs.com/package/@types/react-dom) | `19.2.3` | ReactDOM 类型定义 | react/DefinitelyTyped | 🟢 **低风险** |
| [@types/ws](https://www.npmjs.com/package/@types/ws) | `8.18.1` | ws 包类型定义 | websockets/DefinitelyTyped | 🟢 **低风险** |
| [@vitejs/plugin-react](https://www.npmjs.com/package/@vitejs/plugin-react) | `5.2.0` | Vite 的 React 插件 | vitejs | 🟢 **低风险** — 官方 |
| [electron](https://www.electronjs.org/) | `42.3.3` | 桌面应用框架 | electron | 🟢 **低风险** — 官方 |
| [electron-builder](https://www.electron.build/) | `26.14.0` | 打包工具 | electron-userland | 🟢 **低风险** — 社区主流 |
| [electron-vite](https://electron-vite.org/) | `5.0.0` | Electron + Vite 集成 | alex8088 | 🟡 **中低风险** — 知名独立维护者，已被广泛采用 |
| [react](https://react.dev/) | `19.2.7` | UI 库 | react | 🟢 **低风险** — 官方 |
| [react-dom](https://react.dev/) | `19.2.7` | React DOM 渲染 | react | 🟢 **低风险** — 官方 |
| [typescript](https://www.typescriptlang.org/) | `6.0.3` | TypeScript 编译器 | microsoft | 🟢 **低风险** — 官方 |
| [vite](https://vitejs.dev/) | `7.3.5` | 构建工具 | vitejs | 🟢 **低风险** — 官方 |

> **变更说明（相对 2026-06-04 基线）**：devDependencies 版本随上游升级同步到当前实际安装版本（非安全相关，仅版本对齐）：
> - `react` / `react-dom` `19.2.3` → `19.2.7`
> - `@types/react` `19.2.14` → `19.2.16`；`@types/react-dom` `19.2.3` 不变；`@types/node` `20.14.10` → `25.9.1`；`@types/ws` `8.5.13` → `8.18.1`
> - `electron` `32.2.5` → `42.3.3`；`electron-builder` `25.1.8` → `26.14.0`；`electron-vite` `2.3.0` → `5.0.0`
> - `typescript` `5.9.3` → `6.0.3`；`vite` `6.4.1` → `7.3.5`；`@vitejs/plugin-react` `4.7.0` → `5.2.0`

---

## 3. overrides（强制间接依赖锁定，4 个）

| 包名 | 版本 | 锁定原因 |
|------|------|----------|
| `electron-store` | `8.2.0` | 防止升级到 ESM-only 9.x（与 Electron CJS 主进程不兼容） |
| `glob` | `10.4.5` | 修复 [CVE-2024-28863](https://nvd.nist.gov/vuln/detail/CVE-2024-28863) 等历史问题 |
| `braces` | `3.0.3` | 修复 [CVE-2024-4068](https://nvd.nist.gov/vuln/detail/CVE-2024-4068)（ReDoS） |
| `minimatch` | `9.0.5` | 修复 [CVE-2024-4067](https://nvd.nist.gov/vuln/detail/CVE-2024-4067)（ReDoS） |

> **变更说明**：原 `ws` 的 override（`8.18.0`）已移除 —— `ws` 现为直接依赖（`8.21.0`），无需在 overrides 重复锁定。
>
> **说明**：`overrides` 字段会强制所有依赖（包括间接）使用指定版本，即使上游包未升级。

---

## 4. npm 病毒攻击防护策略

### 4.1 供应链攻击类型

| 类型 | 描述 | 防护措施 |
|------|------|----------|
| **typosquatting** | 仿冒知名包名（如 `electon` vs `electron`） | ✅ 白名单 + 依赖审计 |
| **恶意 postinstall** | 安装时执行任意代码 | ✅ `ignore-scripts=true` |
| **依赖混淆** | 私有包名被公开注册 | ✅ 仅从官方 registry 安装 |
| **lockfile 漂移** | 升级时引入新代码 | ✅ `package-lock.json` 强制使用 |
| **恶意维护者** | 知名包被收购后注入代码 | ✅ `overrides` + 完整性校验 |
| **中间人劫持** | 下载时被替换 | ✅ npm 强制 HTTPS + integrity SHA512 |

### 4.2 关键配置说明

#### `save-exact=true`
- 默认保存精确版本号（不带 `^`）
- 防止 `^1.2.3` 自动升级到 `1.x.x` 最新版本

#### `package-lock=true`
- 强制生成和使用 `package-lock.json`
- 每次安装都基于 lockfile，不重新解析版本范围

#### `ignore-scripts=true`
- **不运行** 任何 `preinstall`/`install`/`postinstall` 钩子
- 这是**最关键**的防护，能阻止大部分供应链攻击
- 例外：如需构建 native 模块（electron-rebuild），需临时关闭：
  ```bash
  npm install --ignore-scripts=false <package>
  ```

#### `registry=https://registry.npmjs.org/`
- 强制使用 npm 官方 registry
- 避免镜像站被劫持（部分公司内网镜像曾出现事件）

#### 完整性校验（默认开启）
- npm 会校验每个 tarball 的 SHA-512 哈希
- 与 lockfile 中 `integrity` 字段对比
- 不一致则拒绝安装

---

## 5. 验证命令

```bash
# 1. 预安装审计（默认被 ignore-scripts 禁用，可显式运行）
npm run audit:deps
#   ↓
# [1/4] 检查版本锁定...
# [2/4] 检查可疑包名（typosquatting）...
# [3/4] 检查 overrides 配置...
# [4/4] 检查 Node.js 版本要求...
# ✅ 依赖审计通过

# 2. 安全审计（仅生产依赖）
npm run audit:security

# 3. 安全审计（包含开发依赖）
npm run audit:security:full

# 4. 验证 lockfile 完整性
npm run verify:lockfile

# 5. 手动运行依赖审计
npm run audit:deps
```

> ⚠️ **重要**：`preinstall` 钩子默认不执行（见 §1）。CI/本地如要强制审计，务必显式调用 `npm run audit:deps`。该脚本要求全部直接依赖为精确版本——请保持本清单与 `package.json` 一致（无 `^`/`~`）。

---

## 6. CI/CD 集成建议

在 CI 中加入以下检查：

```yaml
# GitHub Actions 示例
- name: 安装依赖
  run: npm ci   # 注意：ci 而非 install

- name: 依赖审计
  run: npm run audit:deps

- name: 安全扫描
  run: npm run audit:security:full

- name: 验证 lockfile
  run: npm run verify:lockfile
```

> **关键**：CI 中使用 `npm ci` 而非 `npm install`，前者严格按 lockfile 安装，不会引入新版本。

---

## 7. 紧急响应

如发现已安装的依赖存在安全问题：

```bash
# 1. 立即更新到安全版本（修改 overrides + package.json）
# 2. 删除旧的 lockfile 和 node_modules
rm -rf node_modules package-lock.json

# 3. 重新安装
npm ci

# 4. 验证
npm run verify:lockfile
npm run audit:security
```

---

## 8. 不在依赖中的可选包

如未来需要以下功能，请添加到 `dependencies`（**谨慎评估**）：

| 功能 | 推荐包 | 评估 |
|------|--------|------|
| 配置存储 | 改用 `electron-store@8.2.0` | 需评估 ESM/CJS 兼容性 |
| 文件监听 | `chokidar@3.6.0` | 已较稳定 |
| HTTP 客户端 | 浏览器原生 `fetch`（足够用） | 避免 axios 体积 |
| 加密 | Node.js 内置 `crypto` | 避免引入 |
| YAML 解析 | `yaml@2.x` | 必要时引入 |
| 时间处理 | 已引入 `date-fns@3.6.0` | ✅ 已完成 |
| 日历 UI | 已引入 `@fullcalendar/*@6.1.21` | ✅ 已完成 |
| 音视频会议 | 已引入 `@livekit/*` + `livekit-client` | ✅ 已完成 |

---

**最后审计**：2026-07-23
**审计人**：Living Agent Team
