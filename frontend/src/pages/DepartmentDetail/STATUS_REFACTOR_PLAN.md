# 固定数字员工状态前后端映射重构计划

## 背景

后端 `EmployeeStatus.java` 定义了 11 种员工状态，但前端 `status.ts` / `officeMotion.ts` 只映射了 7 种归一化状态，导致 9 个后端状态回退到 `idle`（休息室），视觉呈现严重失真。

---

## 一、修改清单

### 1. status.ts — 补充完整状态映射表 ✅

**文件**: [status.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/status.ts)

| # | 修改内容 | 状态 |
|---|---------|------|
| 1.1 | 在 `STATUS_LABELS` 中新增 `online` → workstation/work | ✅ |
| 1.2 | 在 `STATUS_LABELS` 中新增 `away` → lounge/idle | ✅ |
| 1.3 | 在 `STATUS_LABELS` 中新增 `offline` → offline/idle | ✅ |
| 1.4 | 在 `STATUS_LABELS` 中新增 `dormant` → offline/idle | ✅ |
| 1.5 | 在 `STATUS_LABELS` 中新增 `disabled` → alert/alert | ✅ |
| 1.6 | 在 `STATUS_LABELS` 中新增 `archived` → offline/idle | ✅ |
| 1.7 | 在 `STATUS_LABELS` 中新增 `terminated` → offline/idle | ✅ |
| 1.8 | 在 `STATUS_LABELS` 中新增 `learning` → workstation/work | ✅ |
| 1.9 | 在 `STATUS_LABELS` 中新增 `evolving` → workstation/work | ✅ |

**预期映射结果**:

| 后端状态 | 前端归一化 | zone | accent | 中文标签 |
|----------|-----------|------|--------|----------|
| ONLINE | online | workstation | work | 在线 |
| OFFLINE | offline | offline | idle | 离线 |
| BUSY | busy | collaboration | work | 协作中 |
| AWAY | away | lounge | idle | 离开中 |
| ACTIVE | active | workstation | work | 工作中 |
| DORMANT | dormant | offline | idle | 休眠 |
| DISABLED | disabled | alert | alert | 禁用 |
| ARCHIVED | archived | offline | idle | 归档 |
| TERMINATED | terminated | offline | idle | 已离职 |
| LEARNING | learning | workstation | work | 学习中 |
| EVOLVING | evolving | workstation | work | 进化中 |

---

### 2. officeMotion.ts — 补充动作配置表 ✅

**文件**: [officeMotion.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/officeMotion.ts)

| # | 修改内容 | 状态 |
|---|---------|------|
| 2.1 | 在 `MOTION_BY_STATUS` 中新增 `online` 配置 | ✅ |
| 2.2 | 在 `MOTION_BY_STATUS` 中新增 `away` 配置 | ✅ |
| 2.3 | 在 `MOTION_BY_STATUS` 中新增 `offline` 配置 | ✅ |
| 2.4 | 在 `MOTION_BY_STATUS` 中新增 `dormant` 配置 | ✅ |
| 2.5 | 在 `MOTION_BY_STATUS` 中新增 `disabled` 配置 | ✅ |
| 2.6 | 在 `MOTION_BY_STATUS` 中新增 `archived` 配置 | ✅ |
| 2.7 | 在 `MOTION_BY_STATUS` 中新增 `terminated` 配置 | ✅ |
| 2.8 | 在 `MOTION_BY_STATUS` 中新增 `learning` 配置 | ✅ |
| 2.9 | 在 `MOTION_BY_STATUS` 中新增 `evolving` 配置 | ✅ |

**配置原则**:
- 工作状态 (`online/active/busy/learning/evolving`) → `workstation`, `walk`/`focused`
- 离开状态 (`away/idle`) → `lounge`, `sit`/`resting`
- 离线状态 (`offline/stopped/inactive/dormant/archived/terminated`) → `offline`, `standby`/`calm`
- 异常状态 (`disabled/error`) → `alert`, `alert`/`urgent`

---

### 3. index.css — 补充 CSS 变量与脉冲样式 ✅

**文件**: [index.css](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/index.css)

| # | 修改内容 | 状态 |
|---|---------|------|
| 3.1 | 在 `:root` 中新增 `--status-online`, `--status-away`, `--status-dormant`, `--status-disabled`, `--status-archived`, `--status-terminated`, `--status-learning`, `--status-evolving` | ✅ |
| 3.2 | 在 `[data-theme="dark"]` 中同步新增上述变量 | ✅ |
| 3.3 | 为 `.pixel-agent__pulse` 新增 `status-online`, `status-away`, `status-dormant`, `status-disabled`, `status-archived`, `status-terminated`, `status-learning`, `status-evolving` 样式 | ✅ |
| 3.4 | 为 `.station-avatar__dot` 同步新增上述状态样式 | ✅ |
| 3.5 | 为 `.pixel-agent` 新增特殊状态视觉特效 CSS | ✅ |

---

### 4. PixelAgent.tsx — 增加特殊状态视觉特效 ✅

**文件**: [PixelAgent.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/PixelAgent.tsx)

| # | 修改内容 | 状态 |
|---|---------|------|
| 4.1 | `LEARNING` 状态：脉冲青色 | ✅ |
| 4.2 | `EVOLVING` 状态：脉冲紫色 + 动画加速 (`animation-duration: 1.2s`) | ✅ |
| 4.3 | `DORMANT` 状态：人物半透明 `opacity: 0.5` | ✅ |
| 4.4 | `DISABLED` 状态：红色边框 (`box-shadow: 0 0 0 2px var(--status-disabled)`) | ✅ |
| 4.5 | `ARCHIVED` 状态：灰度显示 `filter: grayscale(1)` + 透明度 0.7 | ✅ |
| 4.6 | `TERMINATED` 状态：完全灰度 + 透明度 0.3 | ✅ |

**实现方式**: 为 `PixelAgent` 的 `className` 增加 `pixel-agent--status-${status}` 类名，在 CSS 中控制特效。

---

## 二、实施优先级

| 优先级 | 任务 | 影响范围 | 状态 |
|--------|------|----------|------|
| P0 | 任务 1 (`status.ts`) | 状态标签与区域分配 | ✅ 已完成 |
| P0 | 任务 2 (`officeMotion.ts`) | 动作与姿态 | ✅ 已完成 |
| P1 | 任务 3 (`index.css`) | 脉冲颜色与视觉反馈 | ✅ 已完成 |
| P2 | 任务 4 (`PixelAgent.tsx`) | 特殊状态视觉特效 | ✅ 已完成 |

---

## 三、状态映射总表（修改后预期）

| 后端状态 | 前端状态 | Zone | Pose | Mood | 脉冲颜色 | 特殊效果 |
|----------|----------|------|------|------|----------|----------|
| ONLINE | online | workstation | walk | focused | 绿色 | 无 |
| OFFLINE | offline | offline | stand | calm | 灰色 | 无 |
| BUSY | busy | collaboration | walk | urgent | 蓝色 | 无 |
| AWAY | away | lounge | sit | resting | 浅蓝灰 | 无 |
| ACTIVE | active | workstation | walk | focused | 绿色 | 无 |
| DORMANT | dormant | offline | stand | calm | 灰蓝 | 半透明 |
| DISABLED | disabled | alert | alert | urgent | 红色 | 红色边框 |
| ARCHIVED | archived | offline | stand | calm | 灰白 | 灰度 |
| TERMINATED | terminated | offline | stand | calm | 浅灰 | 灰度+低透明 |
| LEARNING | learning | workstation | walk | focused | 青色 | 无 |
| EVOLVING | evolving | workstation | walk | urgent | 紫色 | 动画加速 |
| (error) | error | alert | alert | urgent | 红色 | 无 |

---

## 四、修改记录

| 时间 | 修改文件 | 修改内容摘要 | 完成 |
|------|----------|-------------|------|
| 2026-05-18 | `status.ts` | 补充 9 个缺失状态映射（online/away/offline/dormant/disabled/archived/terminated/learning/evolving） | ✅ |
| 2026-05-18 | `officeMotion.ts` | 补充 9 个缺失状态动作配置，按工作状态/离开状态/离线状态/异常状态分组 | ✅ |
| 2026-05-18 | `index.css` | 新增 14 个 CSS 状态变量（light/dark 双主题），补充 pixel-agent__pulse 和 station-avatar__dot 全部状态样式，新增特殊状态视觉特效 | ✅ |
| 2026-05-18 | `PixelAgent.tsx` | 为 className 增加 `pixel-agent--status-${status}` 类名，使 CSS 特效生效 | ✅ |

---

## 五、相关文件清单

| 文件 | 职责 |
|------|------|
| [EmployeeStatus.java](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-core/src/main/java/com/livingagent/core/employee/EmployeeStatus.java) | 后端状态枚举定义 |
| [status.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/status.ts) | 前端状态标签映射 |
| [officeMotion.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/officeMotion.ts) | 动作/区域/姿态配置 |
| [PixelAgent.tsx](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/PixelAgent.tsx) | 像素人物渲染组件 |
| [fixedEmployeePersona.ts](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/pages/DepartmentDetail/fixedEmployeePersona.ts) | 人物形象配置 |
| [index.css](file:///f:/SoarCloudAI/docker/living-agent-service/frontend/src/index.css) | 全局 CSS 样式与状态变量 |
