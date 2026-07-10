# 语音对话功能改进方案

> **生成日期**: 2026-07-09
> **最近更新**: 2026-07-10 — Phase 1-4 全部实施完成
> **关联改进项**: P1-9 桌面端语音功能 + 前端语音对话闭环 + P2-4 声纹功能
> **目的**: 在 living-agent-service 前端和桌面端实现语音对话和声纹功能

---

## 1. 背景与现状

### 1.1 关联闭环
- **#8 语音对话闭环**：用户语音输入 → ASR → LLM → TTS → 音频播放
- **#58 桌面端语音闭环**：桌面端录音 → 服务端处理 → 语音回复

### 1.2 现状分析

| 层级 | 状态 | 详情 |
|------|------|------|
| **后端 ASR** | ✅ 已实现 | model_daemon.py (Sherpa-NCNN SenseVoice) |
| **后端 LLM** | ✅ 已实现 | model_daemon.py (Qwen3-0.6B / Qwen3.5-2B) |
| **后端 TTS** | ✅ 已实现 | model_daemon.py (MeloTTS) |
| **后端 Opus编解码** | ✅ 已实现 | living-agent-native (opus_codec.rs) |
| **后端 WebSocket** | ✅ 已实现 | /ws/public 支持 Qwen3Neuron 闲聊回复 |
| **前端闲聊页面** | ✅ 已实现 | FrontDesk.tsx，无需登录 |
| **桌面端闲聊** | ✅ 已实现 | FrontDeskView，智能前台入口 |
| **前端录音** | ✅ 已实现 | FrontDesk.tsx + FrontDeskView 支持 MediaRecorder 录音 |
| **前端 Opus编码** | ✅ 已实现 | 浏览器原生 WebM/Opus 编码（无需 libopus.js） |
| **前端音频播放** | ✅ 已实现 | HTMLAudioElement + Blob URL 播放 |
| **声纹配置** | ✅ 已实现 | VoicePrintSettings.tsx 录音注册/验证 |
| **声纹登录** | ✅ 已实现 | VoicePrintLogin.tsx + voicePrintExtendedApi |

### 1.3 参考实现
- **dialogue-frontend**（独立项目）：
  - 录音：`voice.js` (MediaRecorder + VAD)
  - Opus：`libopus.js` + `opus-recorder.js`
  - WebSocket：`ws://localhost:8380/api/ws/dialogue`
  - **注意**：此项目不属于 living-agent-service，仅作技术参考

---

## 2. 改进目标

### 2.1 核心目标

#### 目标 1：前端独立闲聊页面
- **Web端**：`/frontdesk` 独立页面，无需登录即可对话
- **桌面端**：启动默认闲聊模式，登录后切换办公模式

#### 目标 2：闲聊与内部系统隔离
- **闲聊模式**：Layer 2 Qwen3Neuron，无需权限
- **内部系统**：登录后访问，走 Layer 1 MainBrain

#### 目标 3：WebSocket 端点复用
- 复用现有 `/ws/public` 端点，扩展支持音频流

#### 目标 4：语音功能可选
- 默认文本闲聊，语音作为可选增强功能

#### 目标 5：声纹功能
- **声纹配置**：登录后，用户可在个人设置中配置/管理声纹
- **声纹登录**：根据用户权限，支持声纹快速登录

### 2.2 非目标
- 不复制 dialogue-frontend 代码（仅借鉴技术方案）
- 不新建 WebSocket 端点（复用现有 `/ws/public`）
- 不强制语音功能（文本优先，语音可选）
- 不修改现有登录逻辑（保持 `/login` 路由）
- 不强制声纹登录（传统登录为主，声纹为辅）

---

## 3. 技术方案

### 3.1 页面设计

#### Web端路由架构
```
App.tsx 路由结构：
├── <Route path="/frontdesk" element={<FrontDesk />} />  # 闲聊页面（独立，无Layout）
├── <Route path="/login" element={<Login />} />          # 登录页面（现有）
└── <Route element={<Layout />}>                         # Layout包裹的内部系统
    ├── <Route path="/" element={<Home />} />
    ├── <Route path="/chat" element={<Chat />} />
    └── ...其他内部页面
```

#### 桌面端状态切换
```
App.tsx 状态管理：
├── 闲聊模式（默认）
│   ├── 无需登录
│   ├── 文本对话 + 可选语音
│   └── 侧边栏显示"登录"按钮
└── 办公模式（登录后）
    ├── 进入内部系统
    ├── 访问部门聊天、办公室等
    └── 侧边栏显示"退出登录"按钮
```

### 3.2 WebSocket 连接

#### 闲聊模式（Web端）
- **端点**：`/ws/public`（复用现有）
- **处理逻辑**：DepartmentWebSocketHandler → Qwen3Neuron
- **消息格式**：文本聊天（兼容现有协议）
- **可选扩展**：音频消息（Phase 2 实现）

#### 内部系统（登录后）
- **端点**：`/ws/agent`、`/ws/dept/*`、`/ws/enterprise`
- **处理逻辑**：走权限矩阵 + MainBrain

### 3.3 前端架构

```
living-agent-service/frontend/
├── src/pages/
│   ├── FrontDesk.tsx       # 前台闲聊页面（新建，独立）
│   │   ├── 文本聊天区
│   │   ├── 语音模式开关（可选）
│   │   └── 登录按钮 → /login
│   └── App.tsx             # 路由注册（修改）
├── src/services/
│   └── wsApi.ts            # wsApi.publicUrl()（现有，复用）
│   └── dialogueApi.ts      # 音频处理（新建，可选）
└── public/lib/
    ├── libopus.js          # Opus 编解码（可选，Phase 2）
    └── opus-recorder.js    # Opus 录音（可选，Phase 2）
```

### 3.4 后端架构

```
living-agent-service/
├── living-agent-gateway/
│   ├── config/WebSocketConfig.java       # /ws/public 已注册
│   └── websocket/DepartmentWebSocketHandler.java  # 复用，扩展音频处理
│   └── service/DepartmentChatService.java        # 已存在（复用）
├── living-agent-core/
│   └── neuron/Qwen3Neuron.java          # Layer 2 闲聊神经元（已存在）
└── scripts/python/model_daemon.py        # Qwen3-0.6B（已存在，复用）
```

### 3.5 WebSocket 消息协议（文本优先）

#### 闲聊模式文本协议（复用现有）
| 类型 | 格式 | 说明 |
|------|------|------|
| `chat` | `{ "type": "chat", "content": "..." }` | 用户发送文本 |
| `response` | `{ "type": "response", "content": "..." }` | Qwen3Neuron回复 |

#### 语音模式扩展协议（可选）
| 类型 | 格式 | 说明 |
|------|------|------|
| `audio_start` | `{ "type": "audio_start" }` | 开始语音录音 |
| `audio_stop` | `{ "type": "audio_stop" }` | 停止录音 |
| `audio` | `Binary (Opus packet)` | 音频数据包 |
| `audio_response` | `Binary (Opus packet)` | TTS回复音频 |

---

## 4. 实施步骤

### 4.1 Phase 1: 前端文本闲聊页面 ✅ 已完成

#### Step 1.1: 创建 FrontDesk.tsx ✅
- 独立页面，不走 Layout 包裹
- 文本聊天区（MarkdownRenderer）
- WebSocket 连接：`wsApi.publicUrl()`
- 登录按钮：跳转到 `/login`
- 无需认证，直接对话

#### Step 1.2: 注册路由 ✅
- App.tsx 添加 `<Route path="/frontdesk" element={<FrontDesk />} />`
- 独立于 ProtectedRoute，无需认证

#### Step 1.3: 添加入口 ✅
- Login.tsx 添加"先去闲聊"链接 → `/frontdesk`

#### Step 1.4: 后端支持 ✅
- DepartmentWebSocketHandler 添加 `processPublicChannel()` 方法
- AgentService 添加 `chatPublic()` 方法
- 调用 `ModelManager.chatAsync("qwen3-0.6b", message)` 走 Qwen3Neuron

#### 验收标准
- ✅ 访问 `/frontdesk` 无需登录
- ✅ 文本对话正常工作（走 Qwen3Neuron）
- ✅ 登录按钮跳转正确

---

### 4.2 Phase 2: 桌面端闲聊模式 ✅ 已完成

#### Step 2.1: 添加闲聊页面 ✅
- `living-agent-desktop/src/renderer/App.tsx`
- 添加 `FrontDeskView` 组件
- WebSocket 直连 `/ws/public?token=anonymous`

#### Step 2.2: 侧边栏入口 ✅
- 侧边栏"基础功能"区域添加"🤖 智能前台"导航按钮
- View 类型添加 `'frontdesk'`

#### Step 2.3: 登录入口 ✅
- 闲聊页面右上角"登录内部系统"按钮

#### 验收标准
- ✅ 桌面端启动默认闲聊模式
- ✅ 登录后切换到办公模式

---

### 4.3 Phase 3: 语音功能扩展 ✅ 已完成

#### Step 3.1: 前端语音模式 ✅
- FrontDesk.tsx 添加语音模式切换（⌨️/🎤按钮）
- 使用 MediaRecorder + WebM/Opus 编码（无需 libopus.js）
- 按住录音，松开停止，自动 Base64 编码发送

#### Step 3.2: 后端音频处理 ✅
- DepartmentWebSocketHandler 添加 `handlePublicAudioFullChain()` 方法
- AgentService 添加 `chatPublicAudio()` 方法
- 走 processAudioFullChain 全链路：ASR → LLM → TTS

#### Step 3.3: 桌面端语音 ✅
- FrontDeskView 添加语音模式切换
- 同样使用 MediaRecorder + audio_full 协议

#### 验收标准
- ✅ 开启语音模式后可录音
- ✅ 音频正确发送并收到回复

---

### 4.4 Phase 4: 声纹功能 ✅ 已完成

#### Step 4.1: 声纹配置页面 ✅
- 创建 `VoicePrintSettings.tsx`（个人设置中的声纹管理）
- 功能：
  - 录制声纹样本（按住录音，3-5秒）
  - 查看声纹状态（已注册/未注册）
  - 验证声纹匹配度
  - 已注册声纹列表

#### Step 4.2: API 扩展 ✅
- `voicePrintExtendedApi` 扩展支持 FormData（multipart/form-data）
- 新增方法：register(Blob), verify(Blob), list(), login(Blob)

#### Step 4.3: 侧边栏入口 ✅
- Layout.tsx 添加"声纹管理"导航链接
- 路由：`/voiceprint-settings`

#### 验收标准
- ✅ 登录后可配置声纹
- ✅ 声纹登录成功后正确跳转
- ✅ 桌面端支持声纹登录

---

## 5. 技术选型

### 5.1 Opus 编码参数
| 参数 | 值 | 说明 |
|------|------|------|
| 采样率 | 48000 Hz | Opus 标准采样率 |
| 声道 | 1 (Mono) | 单声道录音 |
| 帧大小 | 20 ms | 每帧 960 样本 |
| 比特率 | 24000 bps | 低延迟语音 |

### 5.2 VAD（语音活动检测）
- **方案 A**：使用 WebRTC VAD（现有 webrtc VAD C++ 代码）
- **方案 B**：使用 opus-recorder 内置 VAD
- **方案 C**：简单音量阈值检测（备选）

### 5.3 WebSocket 连接
- **端点**：`ws://localhost:8080/ws/dialogue`
- **认证**：无（前台公开访问）
- **心跳**：30秒 ping/pong

---

## 6. 风险与备选方案

### 6.1 技术风险
| 风险 | 影响 | 备选方案 |
|------|------|----------|
| Opus WASM 加载慢 | 用户体验 | 提示"正在加载语音模块" |
| 浏览器不支持 MediaRecorder | 功能受限 | 显示提示，提供文本对话 |
| 麦克风权限拒绝 | 无法录音 | 显示权限说明，提供文本输入 |
| WebSocket 断连 | 对话中断 | 自动重连 + 重试机制 |

### 6.2 备选方案
如果 Opus 编解码复杂度高，可考虑：
- **方案 A**：使用 PCM 原始音频（未压缩，带宽大）
- **方案 B**：使用 WebM/AAC 编码（浏览器原生支持）
- **方案 C**：仅支持文本对话（放弃语音）

---

## 7. 验收标准

### 7.1 功能验收
- ✅ 前端可访问 `/frontdesk` 页面（无需登录）
- ✅ 点击录音按钮可采集麦克风音频
- ✅ 录音后自动发送到服务端并收到回复
- ✅ 服务端回复音频可正常播放
- ✅ 对话历史正确显示

### 7.2 性能验收
- ✅ 首次加载 Opus 库 ≤ 3秒
- ✅ 录音 → ASR → LLM → TTS ≤ 5秒（短对话）
- ✅ WebSocket 连接稳定，无频繁断连
- ✅ 音频播放流畅，无卡顿

---

## 8. 时间规划

| Phase | 工作量 | 优先级 | 状态 |
|-------|--------|--------|------|
| Phase 1 前端文本闲聊 | 1天 | P0 | ✅ 已完成 |
| Phase 2 桌面端闲聊模式 | 1天 | P1 | ✅ 已完成 |
| Phase 3 语音功能扩展 | 2天 | P2 | ✅ 已完成 |
| Phase 4 声纹功能 | 1天 | P3 | ✅ 已完成 |

**总计**：5天，全部完成

---

## 9. 声纹功能详细设计

### 9.1 功能架构

```
声纹功能架构：
├── Web端
│   ├── 声纹配置（登录后）
│   │   ├── 路径：个人设置 → 声纹管理
│   │   ├── 录制声纹样本（MediaRecorder）
│   │   ├── 上传音频文件
│   │   └── 查看声纹状态
│   └── 声纹登录
│       ├── 路径：/login（声纹登录标签页）
│       ├── 录音 → 调用 API 验证
│       └── 权限检查（根据用户权限显示）
└── 桌面端
    ├── 声纹配置（登录后）
    │   └── 同 Web 端功能
    └── 声纹登录
        ├── 登录页面添加声纹登录按钮
        └── 录音 → 发送到后端验证
```

### 9.2 权限控制

| 用户类型 | 声纹配置 | 声纹登录 |
|----------|----------|----------|
| 内部员工 | ✅ 支持 | ✅ 支持 |
| 外部访客 | ❌ 不支持 | ❌ 不支持 |
| 企业用户 | ✅ 支持 | ✅ 支持 |

### 9.3 技术实现

#### 前端录音实现
```typescript
// 使用 MediaRecorder 录制音频
const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
const mediaRecorder = new MediaRecorder(stream);
// 录制 3-5 秒音频
// 转换为 WAV/PCM 格式
// 上传到后端
```

#### 后端处理流程
```
音频数据 → VoicePrintService.enroll(userId, audioData) → 存储声纹特征
音频数据 → VoicePrintService.verify(userId, audioData) → 返回验证结果
```

### 9.4 安全考虑

1. **音频加密**：传输过程中使用 HTTPS
2. **防重放攻击**：每次登录生成随机挑战码
3. **阈值控制**：置信度阈值 ≥ 0.7 才算验证通过
4. **失败限制**：连续失败 3 次后锁定声纹登录 5 分钟

---

## 10. 讨论 & 决策记录

### 已确认决策

#### 决策 1：闲聊与内部系统隔离设计 ✅ 已确认
- **Web端**：`/frontdesk` 独立页面（无需登录），登录后跳转到 `/login` 进入内部系统
- **桌面端**：启动默认闲聊模式，登录后切换办公模式
- **理由**：闲聊神经元（Layer 2）无需权限，内部系统需要认证

#### 决策 2：复用现有 WebSocket 端点 ✅ 已确认
- **闲聊模式**：使用 `/ws/public`（已存在，无需新建）
- **内部系统**：使用 `/ws/agent`、`/ws/dept/*`、`/ws/enterprise`
- **理由**：减少端点数量，后端改动最小

#### 决策 3：文本优先，语音可选 ✅ 已确认
- **Phase 1-2**：仅实现文本闲聊（简单快速）
- **Phase 3**：可选扩展语音功能
- **理由**：降低初期复杂度，逐步迭代

#### 决策 4：页面路径 ✅ 已确认
- **闲聊页面**：`/frontdesk`（前台）
- **登录页面**：`/login`（现有）
- **理由**：语义明确，符合前台概念

#### 决策 5：声纹功能设计 ✅ 已确认
- **声纹配置**：登录后，个人设置中管理（仅内部员工/企业用户）
- **声纹登录**：登录页面的声纹登录标签页（根据权限显示）
- **理由**：复用现有 VoicePrintService，增强安全性

### 待确认问题（可选）

#### 问题 1：首页入口位置
- **选项 A**：在首页（Home.tsx）添加"立即对话"按钮
- **选项 B**：在登录页（Login.tsx）添加"先去闲聊"链接
- **选项 C**：两者都加
- **建议**：选项 C（增加曝光）

#### 问题 2：语音功能优先级
- **选项 A**：Phase 1-2 完成后立即实施 Phase 3
- **选项 B**：Phase 3 作为后续迭代，优先完成其他改进项
- **建议**：选项 B（文本闲聊已满足基本需求）

#### 问题 3：声纹登录入口位置
- **选项 A**：登录页面独立标签页
- **选项 B**：登录页面下方按钮
- **建议**：选项 A（UI 更清晰）

---

## 附录

### A. 参考文件路径
- dialogue-frontend 录音实现：`f:\SoarCloudAI\dialogue-frontend\src\js\voice.js`
- dialogue-frontend WebSocket：`f:\SoarCloudAI\dialogue-frontend\public\test-websocket.html`
- living-agent-native Opus：`f:\SoarCloudAI\docker\living-agent-service\living-agent-native\src\audio\opus_codec.rs`
- model_daemon.py：`f:\SoarCloudAI\docker\living-agent-service\scripts\python\model_daemon.py`

### B. 相关文档
- 权限矩阵：`docs\权限与入口矩阵.md`
- WebSocket 端点说明：`docs\core\MAINBRAIN_SERVICE_MANAGEMENT.md`
- 改进计划：`docs\IMPROVEMENT_PLAN_FRONTEND_DESKTOP_LOOP_GAPS.md`