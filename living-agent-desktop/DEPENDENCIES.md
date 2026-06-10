# Living Agent Desktop - 依赖清单

> **目的**：列出所有直接依赖，说明用途、来源、版本锁定原因、风险评估
>
> **更新日期**：2026-06-04

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

---

## 2. 直接依赖（13 个）

### 2.1 dependencies（运行时）

| 包名 | 版本 | 用途 | 维护者 | 风险评估 |
|------|------|------|--------|----------|
| [ws](https://www.npmjs.com/package/ws) | `8.18.0` | WebSocket 客户端（[src/main/ws-client.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/ws-client.ts)） | websockets (websockets/ws) | 🟢 **低风险** — 最流行的 WebSocket 实现，npm 周下载量 1.5 亿+ |

> **变更说明**：
> - 移除 `electron-store@8.2.0` — 当前实现未使用（手工实现配置存储），减少攻击面
> - 移除 `glob@10.4.5` — 当前实现未使用，仅 `@types/glob` 不再需要
> - 新增 `ws@8.18.0` — 之前未声明但代码中已使用

### 2.2 devDependencies（开发时）

| 包名 | 版本 | 用途 | 维护者 | 风险评估 |
|------|------|------|--------|----------|
| [@types/node](https://www.npmjs.com/package/@types/node) | `20.14.10` | Node.js API 类型定义 | microsoft/DefinitelyTyped | 🟢 **低风险** — 官方维护 |
| [@types/react](https://www.npmjs.com/package/@types/react) | `19.2.14` | React 类型定义 | react/DefinitelyTyped | 🟢 **低风险** |
| [@types/react-dom](https://www.npmjs.com/package/@types/react-dom) | `19.2.3` | ReactDOM 类型定义 | react/DefinitelyTyped | 🟢 **低风险** |
| [@types/ws](https://www.npmjs.com/package/@types/ws) | `8.5.13` | ws 包类型定义 | websockets/DefinitelyTyped | 🟢 **低风险** |
| [@vitejs/plugin-react](https://www.npmjs.com/package/@vitejs/plugin-react) | `4.7.0` | Vite 的 React 插件 | vitejs | 🟢 **低风险** — 官方 |
| [electron](https://www.electronjs.org/) | `32.2.5` | 桌面应用框架 | electron | 🟢 **低风险** — 官方 |
| [electron-builder](https://www.electron.build/) | `25.1.8` | 打包工具 | electron-userland | 🟢 **低风险** — 社区主流 |
| [electron-vite](https://electron-vite.org/) | `2.3.0` | Electron + Vite 集成 | alex8088 | 🟡 **中低风险** — 知名独立维护者，已被广泛采用 |
| [react](https://react.dev/) | `19.2.3` | UI 库 | react | 🟢 **低风险** — 官方 |
| [react-dom](https://react.dev/) | `19.2.3` | React DOM 渲染 | react | 🟢 **低风险** — 官方 |
| [typescript](https://www.typescriptlang.org/) | `5.9.3` | TypeScript 编译器 | microsoft | 🟢 **低风险** — 官方 |
| [vite](https://vitejs.dev/) | `6.4.1` | 构建工具 | vitejs | 🟢 **低风险** — 官方 |

---

## 3. overrides（强制间接依赖锁定）

| 包名 | 版本 | 锁定原因 |
|------|------|----------|
| `electron-store` | `8.2.0` | 防止升级到 ESM-only 9.x（与 Electron CJS 主进程不兼容） |
| `glob` | `10.4.5` | 修复 [CVE-2024-28863](https://nvd.nist.gov/vuln/detail/CVE-2024-28863) 等历史问题 |
| `braces` | `3.0.3` | 修复 [CVE-2024-4068](https://nvd.nist.gov/vuln/detail/CVE-2024-4068)（ReDoS） |
| `minimatch` | `9.0.5` | 修复 [CVE-2024-4067](https://nvd.nist.gov/vuln/detail/CVE-2024-4067)（ReDoS） |
| `ws` | `8.18.0` | 修复多个 WebSocket 历史漏洞，确保与直接依赖一致 |

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
# 1. 预安装审计（自动运行）
npm install
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
| 时间处理 | 浏览器原生 `Intl` | 避免 moment/dayjs |

---

**最后审计**：2026-06-04
**审计人**：Living Agent Team
