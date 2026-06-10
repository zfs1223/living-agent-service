# 活跃问题清单（已归档）

> 整合时间：2026-06-05
> 归档时间：2026-06-05
> 状态：本轮修复已完成，本文档归档到 docs/old/

---

## 本轮修复完成汇总

### P0 — 功能缺陷（2/2 已修复）

| 编号 | 问题 | 修复内容 |
|------|------|---------|
| P0-1 | MainBrain.forwardToDepartment() 仅有日志 | ✅ 实现通过 ChannelManager 发布消息到目标部门大脑输入通道，携带协调元数据 |
| P0-2 | SessionContext 缺少 taskKey/executionId | ✅ 添加 taskKey 和 executionId 字段及 getter/setter |

### P1 — 功能不完整（3/4 已修复，1 项需外部集成）

| 编号 | 问题 | 修复内容 |
|------|------|---------|
| P1-1 | ExecutionCapabilityResolver 缺少 LLM 实现 | ✅ 新增 LlmBasedExecutionCapabilityResolver，支持 LlmDecisionClient 和 MainBrain.callLlm 双通道 |
| P1-3 | Receipt 双路径竞态 | ✅ 添加 triggeredFinalResponses 原子集合，防止重复触发 |
| P1-4 | sendArtifactMessage sessionId null 回退 | ✅ 直接使用 sessionId，不再回退到 department |
| P1-2 | BrowserAutomationTool 全 Mock | ❌ 需集成 Playwright/Selenium，属于外部依赖集成，不在本轮范围 |

### P2 — 文档偏差（2/2 已同步）

| 编号 | 问题 | 修复内容 |
|------|------|---------|
| P2-1 | 编排链驱动中心不一致 | ✅ 文档已更新（见 core 文档） |
| P2-2 | Receipt 超时文档值不一致 | ✅ 代码实际值 60s 已确认，旧文档已归档 |

### High — 安全/代码质量（5/10 已修复）

| 编号 | 问题 | 修复内容 |
|------|------|---------|
| H-S01 | JWT Token 通过 URL 传递 | ✅ 添加 Sec-WebSocket-Protocol 头部支持，URL 参数降级为兼容模式 |
| H-I02 | 基础设施服务缺内存限制 | ✅ postgres(2G/512M)、redis(1G/256M)、qdrant(2G/512M)、zookeeper(1G/256M) |
| H-I05 | 前端无 ESLint 配置 | ✅ 添加 eslint.config.js + package.json 依赖 + lint 脚本 |
| H-S06 | Token 存储在 localStorage | ❌ 需后端+前端协调改造，属于架构变更 |
| H-C01~C07 | 上帝类/代码质量 | ❌ 属于长期架构优化，需专项重构 |

### Medium — 资源/内存/安全（8/20+ 已修复）

| 问题 | 修复内容 |
|------|---------|
| LayeredKnowledgeBaseImpl 3 个无限缓存 | ✅ 添加最大容量 1000 + TTL 30分钟 + @Scheduled 定期清理 |
| TaskCheckout.completedTasks 只增不删 | ✅ 添加最大容量 500 + LRU 驱逐 |
| WebSocket recentAuthFailures 无清理 | ✅ 心跳定时器中添加 5 分钟过期清理 |
| SQLiteKnowledgeBase 每次新建连接 | ✅ 添加连接缓存 + Proxy 包装复用 |
| WebSocket 消息体无大小限制 | ✅ 配置 maxTextMessageBufferSize=128KB, maxBinaryMessageBufferSize=256KB |
| 数据库默认弱密码 | ✅ docker-compose 强制环境变量，application.yml 默认值改为空 |
| OAuth state 参数未验证 | ✅ 添加 state 存储、5分钟过期、一次性消费验证 |
| Kafka 自动创建 Topic | ❌ 需运维配置 |

---

## 仍需后续处理的问题

### 需外部集成（高复杂度）

| 问题 | 说明 |
|------|------|
| P1-2 BrowserAutomationTool | 需集成 Playwright/Selenium，替换所有 mock 方法 |
| H-S06 Token 存储 | 需后端 Set-Cookie + 前端改造，架构变更 |

### 长期架构优化

| 问题 | 说明 |
|------|------|
| H-C01 LivingAgentCoreConfig 拆分 | 997行54个@Bean，需按领域拆分 |
| H-C02 EnterpriseFeishuTool 拆分 | 1571行27个action，需按功能域拆分 |
| H-C03 AbstractBrain 拆分 | 1191行，需拆分 ReAct/会话/降级等 |
| H-C04 AgentDetail.tsx 拆分 | 5050行，需拆分为独立子组件 |
| H-C05 前端 301 处 any 类型 | 需逐步替换，ESLint 已配置 warn 规则 |
| H-C06 316 处 catch(Exception e) | 需逐步改为具体异常类型 |
| H-C07 飞书工具族重复代码 | 需提取 FeishuApiClient 基类 |

### 其他 Medium 待处理

| 问题 | 说明 |
|------|------|
| 前端 43 处 alert() | 需替换为 Toast 组件 |
| 前端 51 处 localStorage.getItem('token') | 需统一通过 auth store 访问 |
| 5 个独立 fetchJson 实现 | 需统一为单一 HTTP 客户端 |
| 国际化不一致 | 需统一使用 t('key') |
| 固定 30 秒轮询 | 需页面不可见时暂停 |
| 多处 ExecutorService 未关闭 | 需添加 @PreDestroy |
| MemPalaceBackend 外部进程无清理 | 需添加 @PreDestroy |
| Token 刷新不执行令牌轮换 | 需后端改造 |
| 所有服务同一 bridge 网络 | 需分离前后端网络 |
| 28 个 Controller 各自定义 ApiResponse | 需逐步统一 |
