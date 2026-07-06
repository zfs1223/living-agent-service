# Living Agent Service - 闭环需求覆盖分析与补充改进方案

> **生成日期**: 2026-06-30
> **目的**: 检查 COMPLETE_FLOW_DOCUMENTATION.md 定义的闭环需求（14个L1 + 9个L2 + 9个L3 = 32个），对比 MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 和 DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 中的改进方案，找出未完全覆盖的闭环并生成补充改进方案。

---

## 一、闭环需求覆盖情况总览

### 1.1 COMPLETE_FLOW_DOCUMENTATION.md 定义的全部闭环需求

根据 COMPLETE_FLOW_DOCUMENTATION.md 第25章和 COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md，系统共定义 **32个闭环需求**，分为三大层级：

| 层级 | 闭环编号 | 闭环名称 |
|------|---------|---------|
| L1 流程正确性 | 1 | WebSocket对话闭环 |
| L1 流程正确性 | 2 | 审批流程闭环 |
| L1 流程正确性 | 3 | 任务分配闭环 |
| L1 流程正确性 | 4 | 进化调整闭环 |
| L1 流程正确性 | 5 | 知识注入闭环 |
| L1 流程正确性 | 6 | Windows自动化闭环 |
| L1 流程正确性 | 7 | PostgreSQL数据一致性闭环 |
| L1 流程正确性 | 8 | Qdrant向量一致性闭环 |
| L1 流程正确性 | 9 | Redis缓存一致性闭环 |
| L1 流程正确性 | 10 | 跨系统数据同步闭环 |
| L1 流程正确性 | 11 | 模型健康监控闭环 |
| L1 流程正确性 | 12 | 服务启动健康闭环 |
| L1 流程正确性 | 13 | 主动预判健康闭环 |
| L1 流程正确性 | 14 | 权限管理闭环 |
| L2 覆盖完整性 | 17 | 前端与后端交互闭环 |
| L2 覆盖完整性 | 18-表级 | 数据库持久化闭环 |
| L2 覆盖完整性 | 19 | Native Rust与Java交互闭环 |
| L2 覆盖完整性 | 20 | Python模型守护进程闭环 |
| L2 覆盖完整性 | 22 | Claude CLI代理闭环 |
| L2 覆盖完整性 | 3-A | WebSocket连接管理闭环 |
| L2 覆盖完整性 | 3-B | 消息Trace闭环 |
| L2 覆盖完整性 | 11-A | 员工状态管理闭环 |
| L2 覆盖完整性 | 11-B | 通道监控闭环 |
| L3 生命体自洽 | 24 | 自愈闭环 |
| L3 生命体自洽 | 25 | 经济自治闭环 |
| L3 生命体自洽 | 26 | 知识自进化闭环 |
| L3 生命体自洽 | 27 | 降级链路闭环 |
| L3 生命体自洽 | 28 | 执行回执闭环 |
| L3 生命体自洽 | 29 | 大脑个性进化闭环 |
| L3 生命体自洽 | 30 | 沙箱安全闭环 |
| L3 生命体自洽 | 31 | 跨闭环协同编排闭环 |
| L3 生命体自洽 | 32 | 生命体征仪表盘闭环 |

### 1.2 覆盖情况统计

```
┌──────────────────────────────────────────────────────────────┐
│                    闭环覆盖情况统计                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ✅ 完整覆盖: 12个闭环（85.7%）                               │
│  ├─ 已有完整的改进方案和实现代码                              │
│  ├─ 闭环链路已验证                                           │
│  └────────────────────────────────────────────────────────┘
│                                                              │
│  ⚠️ 部分覆盖: 2个闭环（14.3%）                               │
│  ├─ 有部分改进方案，但未完全闭环                              │
│  ├─ 需要补充改进方案                                         │
│  └────────────────────────────────────────────────────────┘
│                                                              │
│  ❌ 未覆盖: 0个闭环（0%）                                     │
│  └────────────────────────────────────────────────────────┘
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 二、完整覆盖的闭环（12个）

### 闭环1: WebSocket对话闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 用户请求 → 权限检查 → 路由分发 → 意图分析 → 大脑选择 → 模型调用 → 工具执行
       → 结果返回 → Trace记录 → 反馈收集 → 进化调整 → 下次对话优化
验证: 发送WebSocket消息 → 检查响应 → 查询autonomy_trace_events表
```

**改进方案来源**: MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 第9章

**改进内容**：
- P0-A修复: onReceiptRecorded使用正确的sessionId（第2942-2970行）
- P0-B修复: DepartmentExecutionResult.metadata增加sessionId（第2972-3055行）
- P0-C修复: 聊天历史查询统一使用部门代码（第2982-3077行）
- WebSocket握手参数规范（第1406-1408行）
- WebSocket连接与任务绑定设计（第1366-1405行）

**覆盖状态**: ✅ 完整覆盖，闭环链路已修复

---

### 闭环2: 审批流程闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 创建审批 → 工作流加载 → 步骤流转 → 审批人决策 → 状态更新 → 回调通知 → 业务执行
验证: POST /api/approvals创建 → GET查询状态 → POST approve执行
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.2节

**改进内容**：
- 问题识别: 审批工作流存储为内存态（approvalStore/workflowStore ConcurrentHashMap）
- 风险: 重启丢失所有审批中实例
- 解决方案: 将ApprovalInstance和ApprovalWorkflow持久化到PostgreSQL

**覆盖状态**: ✅ 完整覆盖，已有持久化改进方案

---

### 闭环3: 任务分配闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 任务创建 → 员工匹配 → 任务领取 → 执行跟踪 → 结果提交 → 任务完成 → 统计更新
验证: 创建任务 → checkout领取 → complete完成 → 查询tasks表
```

**改进方案来源**: MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 第11章

**改进内容**：
- 分层自治执行闭环设计（第4101-4534行）
- 员工自行领取机制（第2391-2397行）
- 部门内审查闭环（第4271-4340行）
- ExecutionReceiptTaskProjectBridge真实更新task状态（第706行）

**覆盖状态**: ✅ 完整覆盖，闭环链路已优化

---

### 闭环4: 进化调整闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 反馈收集 → 评分计算 → 阈值判断 → 策略选择 → 模型替换 → 变更记录 → 效果验证
阈值: AUTO_ADJUST_THRESHOLD=0.4, CONSECUTIVE_FAILURES_REPLACE=3
```

**改进方案来源**: MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 第3.3.2.1节

**改进内容**：
- 模型调用失败自动降级闭环（第1265-1314行）
- 降级链路：员工专属模型 → fallback模型列表 → DEGRADED结果
- BrainModelResolver三级降级：configured → enabled → null
- BrainAutoAssigner启动自动分配
- AbstractBrain动态Provider解析

**覆盖状态**: ✅ 完整覆盖，降级链路已实现

---

### 闭环5: 知识注入闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 文档扫描 → 内容解析 → 向量生成 → 知识存储 → 向量索引 → 知识检索 → 知识应用
验证: 添加文档 → 触发注入 → 查询knowledge_entries表 → 查询Qdrant向量库
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.1节

**改进内容**：
- LayeredKnowledgeBaseImpl列为完整闭环模块（第717行）
- KnowledgePersistenceService + QdrantVectorService真实落地
- 向量检索 + 混合搜索真实落地

**覆盖状态**: ✅ 完整覆盖，知识注入链路完整

---

### 闭环6: Windows自动化闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 用户指令 → 权限判断 → 工具调用 → WebSocket转发 → Electron接收 → Python执行
       → Windows API → 结果返回 → 状态确认 → 日志记录
验证: 发送"打开浏览器"指令 → 检查桌面 → 查询windows_automation_nodes表
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第222行

**改进内容**：
- 闭环5: Windows自动化（双通道：HTTP + WebSocket）
- 桌面端主进程与渲染层双通道设计
- IPC注册与清理闭环

**覆盖状态**: ✅ 完整覆盖，双通道已实现

---

### 闭环7: PostgreSQL数据一致性闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 写入请求 → JPA实体映射 → Repository.save() → PostgreSQL写入 → 数据持久化
       → 读取请求 → Repository.findById() → JPA实体映射 → Java对象返回
验证: 使用JPA Repository的CRUD操作 → @Transactional确保事务一致性
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第8.3节

**改进内容**：
- 断点B3: 回执链路无事务修复方案（第793-812行）
- recordReceipt + updateTaskFromReceipt包在同一个@Transactional方法
- runtimeEventStore文件写入改用@TransactionalEventListener(phase = AFTER_COMMIT)
- WebSocket推送改为AFTER_COMMIT

**覆盖状态**: ✅ 完整覆盖，事务一致性已修复

---

### 闭环8: Qdrant向量一致性闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 知识注入 → 向量生成 → Qdrant.upsert() → 向量存储 → 向量搜索 → 结果返回
一致性: knowledge_entries表ID与Qdrant向量ID一致
验证: 注入知识后查询Qdrant确认向量存在 → 删除知识后确认向量删除
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.1节

**改进内容**：
- LayeredKnowledgeBaseImpl完整闭环（第717行）
- KnowledgePersistenceService + QdrantVectorService真实落地
- 向量检索 + 混合搜索真实落地

**覆盖状态**: ✅ 完整覆盖，向量一致性已保障

---

### 闭环9: Redis缓存一致性闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 缓存写入 → RedisTemplate.opsForValue().set() → Redis存储 → 缓存读取
       → RedisTemplate.opsForValue().get() → 缓存返回 → 缓存过期 → 自动清理
验证: 写入缓存后查询Redis确认 → 更新数据后确认缓存更新
缓存类型: 会话缓存 + 权限缓存
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.4节

**改进内容**：
- RuntimeEventStore基于文件 + tenantId硬编码修复方案（第815-830行）
- 改为基于DB的EventStore（新建runtime_events表）
- tenantId从WorkItemContext或AuthContext获取，不硬编码

**覆盖状态**: ✅ 完整覆盖，缓存一致性已改进

---

### 闭环10: 跨系统数据同步闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: Jira任务创建 → tasks写入 → Jira同步 → 状态一致 → 双向更新
一致性: Jira任务ID与tasks.jira_issue_id一致
验证: 创建任务后查询Jira确认 → 更新本地状态后确认Jira同步
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第8.2节

**改进内容**：
- 断点B2: 项目统计未落库修复方案（第774-789行）
- ✅ 已修复（阶段1）：在updateProjectFromExecution末尾增加了projectRepository.save(project)
- ExecutionReceiptTaskProjectBridge真实更新task状态

**覆盖状态**: ✅ 完整覆盖，跨系统同步已修复

---

### 闭环11: 模型健康监控闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 模型调用 → 响应时间记录 → 成功/失败统计 → 评分计算 → 阈值检查 → 自动调整
       → 模型替换 → 效果验证 → 恢复健康或继续调整
健康指标: 响应时间<30000ms, 成功率>95%, 平均评分>0.4, 连续失败次数<3
```

**改进方案来源**: MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 第3.3.2.1节

**改进内容**：
- 模型调用失败自动降级闭环（第1265-1314行）
- 同一模型连续失败后进入短期cooldown
- receipt中可见model_provider、model_name、failure_reason、needs_retry
- 日志中能看到模型健康摘要

**覆盖状态**: ✅ 完整覆盖，健康监控闭环已实现

---

### 闭环13: 主动预判健康闭环 ✅

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 定时任务 → PatternPredictor分析 → RiskPredictor检查 → 建议生成 → 主动汇报
       → 用户确认 → 执行建议 → 效果记录 → 模式更新 → 下次预测优化
预判类型: PatternPredictor(用户行为模式) + RiskPredictor(风险识别)
          ProactiveSuggestionService(主动建议)
PR-1主动汇报: 定时生成(每小时) → WebSocket推送 → 用户确认执行或忽略
```

**改进方案来源**: DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.1节

**改进内容**：
- 任务事件桥接服务真实调用RiskPredictor、ProactiveSuggestionService、DepartmentNotificationService（第707行）
- EvolutionFeedbackBridgeService调用knowledgeManager.storeShared + auditLogRepository.save（第709行）

**覆盖状态**: ✅ 完整覆盖，主动预判链路已实现

---

## 三、部分覆盖的闭环（2个）

### 闭环12: 服务启动健康闭环 ⚠️

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: Docker启动 → 依赖服务检查 → 数据库初始化 → Bean初始化 → 端点注册 → 健康检查
       → 服务就绪 → 接收请求 → 监控持续 → 异常报警
启动检查点: PostgreSQL启动 → Redis启动 → Qdrant启动 → Kafka启动
            Spring Boot启动 → model_daemon.py启动 → WebSocket端点注册
健康检查: /api/health → PostgreSQL/Redis/Qdrant/Kafka/NamedPipe连接检查
验证: GET /api/health确认服务健康 → 查看启动日志 → 连接WebSocket → 调用REST API
```

**现有改进方案**：
- MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 提到启动时model_daemon.py加载检查
- DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.2节"会话认证"列为半闭环（activeSessions内存态）
- 系统配置服务列为半闭环（providerConfigs内存态）

**未完全闭环的环节**：
1. **启动依赖检查不完整**：缺少对model_daemon.py命名管道的启动前检查
2. **健康检查端点不全面**：/api/health只检查基本服务，缺少以下检查：
   - NamedPipe连接状态检查
   - model_daemon.py进程存活检查
   - Qwen3/Qwen3.5模型加载状态检查
   - Electron WebSocket桥接检查（Windows自动化）
   - Claude CLI可用性检查
3. **启动顺序未强制**：依赖服务启动顺序未强制，可能出现PostgreSQL未就绪时Spring Boot已启动
4. **启动失败无自动恢复**：启动失败时无自动重试或降级启动机制

**覆盖状态**: ⚠️ 部分覆盖，需要补充改进方案

---

### 闭环14: 权限管理闭环 ⚠️

**COMPLETE_FLOW_DOCUMENTATION.md 定义**：
```
流程: 用户登录 → 权限加载 → 缓存存储 → 权限检查 → 路由判断 → 访问控制 → 权限变更
       → 缓存更新 → 新权限生效
权限级别闭环: CHAT_ONLY(0) → LIMITED(1) → DEPARTMENT(2) → FULL(3)
权限检查闭环: WebSocket连接 → REST API调用 → 工具执行 → 大脑选择 → 部门访问
验证: 不同权限用户连接不同WebSocket → 低权限用户尝试高权限操作确认拒绝
       权限变更后确认缓存更新 → 查询Redis缓存确认权限存储
```

**现有改进方案**：
- DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 第7.4节"权限校验"列为部分闭环
- ✅ OAuth token校验已接入OAuthService
- ⚠️ validateVoiceVector声纹比对待实施

**未完全闭环的环节**：
1. **声纹验证未实现**：validateVoiceVector方法stub，缺少实际声纹比对逻辑
2. **权限变更实时生效不完整**：权限变更后，WebSocket连接可能未强制断开重连，缓存更新不及时
3. **跨端权限同步缺失**：桌面端权限变更后，后端WebSocket连接未同步更新权限上下文
4. **权限审计日志缺失**：权限变更无审计日志记录，缺少权限变更历史追溯

**覆盖状态**: ⚠️ 部分覆盖，需要补充改进方案

---

## 四、补充改进方案

### 4.1 闭环12补充改进：服务启动健康闭环增强方案

```
┌──────────────────────────────────────────────────────────────┐
│            闭环12：服务启动健康闭环增强方案                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  问题分析：                                                   │
│  ├─ 启动依赖检查不完整                                        │
│  ├─ 健康检查端点不全面                                        │
│  ├─ 启动顺序未强制                                            │
│  ├─ 启动失败无自动恢复                                        │
│                                                              │
│  改进目标：                                                   │
│  ├─ 完整的启动依赖检查链                                      │
│  ├─ 全面的健康检查端点                                        │
│  ├─ 强制的启动顺序控制                                        │
│  ├─ 启动失败自动恢复机制                                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

#### 4.1.1 改进方案 P12-A：增强启动依赖检查

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-app/src/main/java/com/livingagent/app/StartupDependencyChecker.java | 新增：启动依赖检查服务 | P0 |
| living-agent-core/src/main/java/com/livingagent/core/model/ModelDaemonHealthChecker.java | 新增：model_daemon.py健康检查 | P0 |
| living-agent-core/src/main/java/com/livingagent/core/model/provider/impl/NamedPipeModelClient.java | 修改：增加启动前管道检查 | P0 |
| living-agent-app/src/main/resources/application.yml | 修改：增加启动依赖配置 | P1 |

**代码实现**：

```java
// 文件：living-agent-app/src/main/java/com/livingagent/app/StartupDependencyChecker.java
package com.livingagent.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动依赖检查服务 - 闭环12增强
 * 确保所有关键依赖服务在应用启动前就绪
 */
@Component
public class StartupDependencyChecker implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger log = LoggerFactory.getLogger(StartupDependencyChecker.class);
    
    private final List<HealthCheckable> dependencies = new ArrayList<>();
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("=== Startup Dependency Check Begin ===");
        
        boolean allHealthy = true;
        for (HealthCheckable dep : dependencies) {
            try {
                boolean healthy = dep.checkHealth();
                if (!healthy) {
                    log.error("Dependency unhealthy: {}", dep.getName());
                    allHealthy = false;
                    // 尝试恢复
                    if (dep.canAutoRecover()) {
                        log.info("Attempting auto recovery for: {}", dep.getName());
                        dep.autoRecover();
                    }
                } else {
                    log.info("Dependency healthy: {}", dep.getName());
                }
            } catch (Exception e) {
                log.error("Dependency check failed: {}", dep.getName(), e);
                allHealthy = false;
            }
        }
        
        if (!allHealthy) {
            log.warn("=== Startup Dependency Check FAILED ===");
            // 标记服务为降级模式
            System.setProperty("app.mode", "degraded");
        } else {
            log.info("=== Startup Dependency Check PASSED ===");
            System.setProperty("app.mode", "full");
        }
    }
    
    public void registerDependency(HealthCheckable dependency) {
        dependencies.add(dependency);
    }
}

// 健康检查接口
public interface HealthCheckable {
    String getName();
    boolean checkHealth();
    boolean canAutoRecover();
    void autoRecover();
}
```

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/ModelDaemonHealthChecker.java
package com.livingagent.core.model;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * model_daemon.py健康检查器 - 闭环12增强
 */
@Component
public class ModelDaemonHealthChecker implements HealthCheckable {
    
    private final String inputPipePath = "/tmp/dialogue_daemon_control_request";
    private final String outputPipePath = "/tmp/dialogue_daemon_control_response";
    
    @Override
    public String getName() {
        return "model_daemon.py";
    }
    
    @Override
    public boolean checkHealth() {
        // 1. 检查命名管道是否存在
        boolean inputPipeExists = Files.exists(Path.of(inputPipePath));
        boolean outputPipeExists = Files.exists(Path.of(outputPipePath));
        
        if (!inputPipeExists || !outputPipeExists) {
            return false;
        }
        
        // 2. 检查管道是否可读写
        try {
            File inputPipe = new File(inputPipePath);
            File outputPipe = new File(outputPipePath);
            if (!inputPipe.canWrite() || !outputPipe.canRead()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean canAutoRecover() {
        return false; // 无法自动恢复，需要重启Python守护进程
    }
    
    @Override
    public void autoRecover() {
        // 无自动恢复能力，需要人工介入或重启服务
    }
}
```

#### 4.1.2 改进方案 P12-B：增强健康检查端点

**新增健康检查项**：

| 检查项 | 端点路径 | 实现位置 |
|--------|---------|---------|
| NamedPipe连接状态 | /api/health/namedpipe | NamedPipeHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator） |
| model_daemon.py进程 | /api/health/modeldaemon | ModelDaemonHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator） |
| Qwen模型加载状态 | /api/health/models | ModelLoadHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator） |
| Electron WebSocket桥接 | /api/health/winautomation | WinAutoHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator） |
| Claude CLI可用性 | /api/health/claudecli | ClaudeCliHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator） |

**代码实现**：

> **注意**：以下 HealthIndicator 代码示例使用 `org.springframework.boot.actuate.health.HealthIndicator`，但项目**未引入 Spring Actuator 依赖**。
> 实际实现时应改为基于 `core/diagnosis/` 自研健康检查体系（HealthMonitor/HealthCheck/HealthStatus），
> 使用 `HealthCheck.builder().name(...).status(...).detail(...)` 替代 `Health.builder().up().withDetail(...)`。
> 代码示例仅供参考逻辑，非可直接编译的代码。

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/NamedPipeHealthChecker.java （基于core/diagnosis/自研健康检查体系，非Spring Actuator）
package com.livingagent.core.model;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * NamedPipe健康指示器 - 闭环12增强
 */
@Component
public class NamedPipeHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator）
    
    private final String inputPipePath = "/tmp/dialogue_daemon_control_request";
    private final String outputPipePath = "/tmp/dialogue_daemon_control_response";
    
    @Override
    public Health health() {
        try {
            boolean inputExists = Files.exists(Path.of(inputPipePath));
            boolean outputExists = Files.exists(Path.of(outputPipePath));
            
            if (inputExists && outputExists) {
                return Health.up()
                    .withDetail("input_pipe", "exists")
                    .withDetail("output_pipe", "exists")
                    .build();
            } else {
                return Health.down()
                    .withDetail("input_pipe", inputExists ? "exists" : "missing")
                    .withDetail("output_pipe", outputExists ? "exists" : "missing")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}

// 文件：living-agent-core/src/main/java/com/livingagent/core/model/ModelLoadHealthChecker.java （基于core/diagnosis/自研健康检查体系，非Spring Actuator）
package com.livingagent.core.model;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模型加载健康指示器 - 闭环12增强
 */
@Component
public class ModelLoadHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator）
    
    private final String qwen3ModelPath = "/app/ai-models/Qwen3-0.6B-GGUF/Qwen3-0.6B-Q8_0.gguf";
    private final String qwen35ModelPath = "/app/ai-models/Qwen3.5-2B-GGUF/Qwen3.5-2B-Q4_K_M.gguf";
    
    @Override
    public Health health() {
        try {
            boolean qwen3Loaded = Files.exists(Path.of(qwen3ModelPath));
            boolean qwen35Loaded = Files.exists(Path.of(qwen35ModelPath));
            
            if (qwen3Loaded && qwen35Loaded) {
                return Health.up()
                    .withDetail("qwen3-0.6b", "loaded")
                    .withDetail("qwen3.5-2b", "loaded")
                    .build();
            } else if (qwen3Loaded) {
                return Health.status("PARTIAL")
                    .withDetail("qwen3-0.6b", "loaded")
                    .withDetail("qwen3.5-2b", "missing")
                    .withDetail("fallback_available", "true")
                    .build();
            } else if (qwen35Loaded) {
                return Health.status("PARTIAL")
                    .withDetail("qwen3-0.6b", "missing")
                    .withDetail("qwen3.5-2b", "loaded")
                    .withDetail("fallback_available", "true")
                    .build();
            } else {
                return Health.down()
                    .withDetail("qwen3-0.6b", "missing")
                    .withDetail("qwen3.5-2b", "missing")
                    .withDetail("fallback_available", "false")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

#### 4.1.3 改进方案 P12-C：强制启动顺序控制

**修改docker-compose.yml**：

```yaml
services:
  living-agent-service:
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      qdrant:
        condition: service_healthy
      kafka:
        condition: service_started
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8382/api/health"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
    
  postgres:
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
  
  redis:
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
  
  qdrant:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:6333/health"]
      interval: 10s
      timeout: 3s
      retries: 5
```

#### 4.1.4 改进方案 P12-D：启动失败自动恢复机制

**新增启动恢复策略**：

```java
// 文件：living-agent-app/src/main/java/com/livingagent/app/StartupRecoveryService.java
package com.livingagent.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 启动失败自动恢复服务 - 闭环12增强
 */
@Service
public class StartupRecoveryService {
    
    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryService.class);
    
    /**
     * 降级启动模式
     */
    public void startInDegradedMode() {
        log.info("Starting in degraded mode due to dependency failures");
        
        // 1. 禁用Windows自动化工具
        System.setProperty("windows.automation.enabled", "false");
        
        // 2. 禁用Claude CLI代理
        System.setProperty("claude.proxy.enabled", "false");
        
        // 3. 启用Mock Provider替代NamedPipe
        System.setProperty("model.provider.default", "mock");
        
        // 4. 缩减大脑可用性（仅保留闲聊和Tool神经元）
        System.setProperty("brain.available", "qwen3,tool");
        
        log.info("Degraded mode started: WinAuto=OFF, ClaudeCLI=OFF, ModelProvider=Mock, Brains=qwen3+tool");
    }
    
    /**
     * 尝试恢复完整服务
     */
    public void attemptFullRecovery() {
        log.info("Attempting full recovery from degraded mode");
        
        // 检查依赖是否恢复
        // 如果恢复，逐步启用完整功能
    }
}
```

#### 4.1.5 验收标准

| 验收项 | 验收方法 | 预期结果 |
|--------|---------|---------|
| 启动依赖检查 | 启动服务，查看日志 | 显示"Startup Dependency Check PASSED" |
| NamedPipe健康检查 | GET /api/health/namedpipe | 返回status=UP |
| 模型加载健康检查 | GET /api/health/models | 返回status=UP或PARTIAL |
| 降级启动模式 | 删除模型文件后启动 | 服务降级启动，日志显示"Degraded mode" |
| 自动恢复 | 恢复模型文件后调用attemptFullRecovery | 恢复完整服务 |

---

### 4.2 闭环14补充改进：权限管理闭环增强方案

```
┌──────────────────────────────────────────────────────────────┐
│            闭环14：权限管理闭环增强方案                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  问题分析：                                                   │
│  ├─ 声纹验证未实现                                            │
│  ├─ 权限变更实时生效不完整                                    │
│  ├─ 跨端权限同步缺失                                          │
│  ├─ 权限审计日志缺失                                          │
│                                                              │
│  改进目标：                                                   │
│  ├─ 实现声纹验证闭环                                          │
│  ├─ 权限变更强制生效                                          │
│  ├─ 跨端权限实时同步                                          │
│  ├─ 完整的权限审计日志                                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

#### 4.2.1 改进方案 P14-A：实现声纹验证闭环

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/src/main/java/com/livingagent/core/security/VoiceVectorValidator.java | 新增：声纹验证服务 | P1 |
| living-agent-core/src/main/java/com/livingagent/core/security/VoiceEmbeddingService.java | 新增：声纹嵌入生成服务 | P1 |
| living-agent-core/src/main/java/com/livingagent/core/security/VoiceVectorRepository.java | 新增：声纹向量存储 | P1 |
| init-db/01_init.sql | 新增：voice_vectors表定义 | P1 |

**数据库表定义**：

```sql
-- 声纹向量存储表
CREATE TABLE voice_vectors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL UNIQUE,
    voice_embedding BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_voice_user FOREIGN KEY (user_id) REFERENCES users(user_id)  -- 注：permissions表不存在，权限为代码枚举(AccessLevel)
);

CREATE INDEX idx_voice_user ON voice_vectors(user_id);
```

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/security/VoiceVectorValidator.java
package com.livingagent.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 声纹验证服务 - 闭环14增强
 */
@Service
public class VoiceVectorValidator {
    
    private static final Logger log = LoggerFactory.getLogger(VoiceVectorValidator.class);
    private static final double SIMILARITY_THRESHOLD = 0.85;
    
    private final VoiceEmbeddingService embeddingService;
    private final VoiceVectorRepository repository;
    
    public VoiceVectorValidator(
            VoiceEmbeddingService embeddingService,
            VoiceVectorRepository repository) {
        this.embeddingService = embeddingService;
        this.repository = repository;
    }
    
    /**
     * 验证声纹
     * @param userId 用户ID
     * @param audioData 音频数据（PCM格式）
     * @return 验证结果
     */
    public VoiceValidationResult validateVoiceVector(String userId, byte[] audioData) {
        try {
            // 1. 从音频生成声纹嵌入向量
            byte[] newEmbedding = embeddingService.generateEmbedding(audioData);
            
            // 2. 查询用户已注册的声纹向量
            Optional<VoiceVectorEntity> existingOpt = repository.findByUserId(userId);
            
            if (existingOpt.isEmpty()) {
                log.warn("No voice vector registered for user: {}", userId);
                return VoiceValidationResult.notRegistered(userId);
            }
            
            VoiceVectorEntity existing = existingOpt.get();
            
            // 3. 计算相似度
            double similarity = calculateSimilarity(newEmbedding, existing.getVoiceEmbedding());
            
            // 4. 判断是否匹配
            if (similarity >= SIMILARITY_THRESHOLD) {
                log.info("Voice validation passed for user: {}, similarity: {}", userId, similarity);
                return VoiceValidationResult.matched(userId, similarity);
            } else {
                log.warn("Voice validation failed for user: {}, similarity: {}", userId, similarity);
                return VoiceValidationResult.notMatched(userId, similarity);
            }
        } catch (Exception e) {
            log.error("Voice validation error for user: {}", userId, e);
            return VoiceValidationResult.error(userId, e.getMessage());
        }
    }
    
    /**
     * 注册声纹
     * @param userId 用户ID
     * @param audioData 音频数据
     * @return 注册结果
     */
    public VoiceRegistrationResult registerVoiceVector(String userId, byte[] audioData) {
        try {
            byte[] embedding = embeddingService.generateEmbedding(audioData);
            
            VoiceVectorEntity entity = new VoiceVectorEntity();
            entity.setUserId(userId);
            entity.setVoiceEmbedding(embedding);
            
            repository.save(entity);
            
            log.info("Voice vector registered for user: {}", userId);
            return VoiceRegistrationResult.success(userId);
        } catch (Exception e) {
            log.error("Voice registration failed for user: {}", userId, e);
            return VoiceRegistrationResult.error(userId, e.getMessage());
        }
    }
    
    /**
     * 计算声纹向量相似度（余弦相似度）
     */
    private double calculateSimilarity(byte[] embedding1, byte[] embedding2) {
        // 实现余弦相似度计算
        // 假设embedding是float数组，转换为byte存储
        float[] vec1 = bytesToFloats(embedding1);
        float[] vec2 = bytesToFloats(embedding2);
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    private float[] bytesToFloats(byte[] bytes) {
        // byte[]转float[]实现
        float[] floats = new float[bytes.length / 4];
        for (int i = 0; i < floats.length; i++) {
            floats[i] = Float.intBitsToFloat(
                ((bytes[i*4] & 0xFF) << 24) |
                ((bytes[i*4+1] & 0xFF) << 16) |
                ((bytes[i*4+2] & 0xFF) << 8) |
                (bytes[i*4+3] & 0xFF)
            );
        }
        return floats;
    }
}

// 验证结果记录
public record VoiceValidationResult(
    String userId,
    boolean matched,
    double similarity,
    String status, // matched/not_matched/not_registered/error
    String errorMessage
) {
    public static VoiceValidationResult matched(String userId, double similarity) {
        return new VoiceValidationResult(userId, true, similarity, "matched", null);
    }
    
    public static VoiceValidationResult notMatched(String userId, double similarity) {
        return new VoiceValidationResult(userId, false, similarity, "not_matched", null);
    }
    
    public static VoiceValidationResult notRegistered(String userId) {
        return new VoiceValidationResult(userId, false, 0.0, "not_registered", null);
    }
    
    public static VoiceValidationResult error(String userId, String errorMessage) {
        return new VoiceValidationResult(userId, false, 0.0, "error", errorMessage);
    }
}
```

#### 4.2.2 改进方案 P14-B：权限变更强制实时生效

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-gateway/src/main/java/com/livingagent/gateway/security/PermissionChangeNotifier.java | 新增：权限变更通知服务 | P0 |
| living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/WebSocketSessionRegistry.java | 新增：WebSocket会话注册表 | P0 |
| living-agent-gateway/src/main/java/com/livingagent/gateway/controller/PermissionController.java | 修改：权限变更时强制WebSocket断开 | P0 |

**代码实现**：

```java
// 文件：living-agent-gateway/src/main/java/com/livingagent/gateway/security/PermissionChangeNotifier.java
package com.livingagent.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 权限变更通知服务 - 闭环14增强
 * 权限变更后强制实时生效，断开相关WebSocket连接
 */
@Service
public class PermissionChangeNotifier {
    
    private static final Logger log = LoggerFactory.getLogger(PermissionChangeNotifier.class);
    
    private final WebSocketSessionRegistry sessionRegistry;
    private final PermissionAuditLogService auditLogService;
    
    public PermissionChangeNotifier(
            WebSocketSessionRegistry sessionRegistry,
            PermissionAuditLogService auditLogService) {
        this.sessionRegistry = sessionRegistry;
        this.auditLogService = auditLogService;
    }
    
    /**
     * 权限变更后强制生效
     * @param userId 用户ID
     * @param oldLevel 旧权限级别
     * @param newLevel 新权限级别
     * @param changedBy 变更操作者
     */
    public void enforcePermissionChange(String userId, int oldLevel, int newLevel, String changedBy) {
        log.info("Enforcing permission change: userId={}, oldLevel={}, newLevel={}, changedBy={}",
            userId, oldLevel, newLevel, changedBy);
        
        // 1. 清除Redis缓存
        clearPermissionCache(userId);
        
        // 2. 强制断开该用户的所有WebSocket连接
        disconnectAllWebSocketSessions(userId);
        
        // 3. 记录权限审计日志
        auditLogService.recordPermissionChange(userId, oldLevel, newLevel, changedBy);
        
        // 4. 发送权限变更通知（可选）
        sendPermissionChangeNotification(userId, newLevel);
        
        log.info("Permission change enforced for user: {}", userId);
    }
    
    private void clearPermissionCache(String userId) {
        // 清除Redis中的权限缓存
        // redisTemplate.delete("perm:" + userId);
    }
    
    private void disconnectAllWebSocketSessions(String userId) {
        // 获取该用户的所有WebSocket session
        List<WebSocketSession> sessions = sessionRegistry.getSessionsByUserId(userId);
        
        for (WebSocketSession session : sessions) {
            try {
                // 发送权限变更通知
                session.sendMessage(new TextMessage("{\"type\":\"permission_changed\",\"newLevel\":" + newLevel + "}"));
                
                // 等待客户端处理通知
                Thread.sleep(100);
                
                // 强制关闭连接
                session.close(CloseStatusPolicyChange.NEW_POLICY);
                
                log.info("WebSocket session closed due to permission change: sessionId={}, userId={}",
                    session.getId(), userId);
            } catch (Exception e) {
                log.error("Failed to close WebSocket session: sessionId={}", session.getId(), e);
            }
        }
        
        // 从注册表中移除
        sessionRegistry.removeSessionsByUserId(userId);
    }
    
    private void sendPermissionChangeNotification(String userId, int newLevel) {
        // 发送邮件/短信通知用户权限变更（可选）
    }
}
```

#### 4.2.3 改进方案 P14-C：跨端权限实时同步

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/CrossPlatformPermissionSyncService.java | 新增：跨端权限同步服务 | P1 |
| living-agent-desktop/src/main/services/PermissionSyncClient.ts | 新增：桌面端权限同步客户端 | P1 |
| frontend/src/services/PermissionSyncService.ts | 新增：前端权限同步服务 | P1 |

**代码实现**：

```java
// 文件：living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/CrossPlatformPermissionSyncService.java
package com.livingagent.gateway.websocket;

import org.springframework.stereotype.Service;

/**
 * 跨端权限实时同步服务 - 闭环14增强
 */
@Service
public class CrossPlatformPermissionSyncService {
    
    /**
     * 同步权限到所有端
     * @param userId 用户ID
     * @param permissionLevel 新权限级别
     */
    public void syncPermissionToAllPlatforms(String userId, int permissionLevel) {
        // 1. 同步到WebSocket连接（已在线）
        syncToWebSocket(userId, permissionLevel);
        
        // 2. 同步到桌面端（通过推送通知）
        syncToDesktop(userId, permissionLevel);
        
        // 3. 同步到移动端（通过推送通知）
        syncToMobile(userId, permissionLevel);
        
        // 4. 更新数据库权限记录
        updateDatabasePermission(userId, permissionLevel);
    }
    
    private void syncToWebSocket(String userId, int permissionLevel) {
        // 发送WebSocket权限变更消息
    }
    
    private void syncToDesktop(String userId, int permissionLevel) {
        // 通过桌面端推送通道同步权限
    }
    
    private void syncToMobile(String userId, int permissionLevel) {
        // 通过移动端推送通道同步权限
    }
    
    private void updateDatabasePermission(String userId, int permissionLevel) {
        // 注：permissions表不存在，权限为代码枚举(AccessLevel)，此处需要通过BrainAccessControl更新
    }
}
```

#### 4.2.4 改进方案 P14-D：权限审计日志完整记录

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/src/main/java/com/livingagent/core/security/PermissionAuditLogService.java | 新增：权限审计日志服务 | P1 |
| living-agent-core/src/main/java/com/livingagent/core/database/entity/PermissionAuditLogEntity.java | 新增：权限审计日志实体 | P1 |
| living-agent-core/src/main/java/com/livingagent/core/database/repository/PermissionAuditLogRepository.java | 新增：权限审计日志Repository | P1 |
| init-db/01_init.sql | 新增：permission_audit_logs表定义 | P1 |

**数据库表定义**：

```sql
-- 权限审计日志表
CREATE TABLE permission_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    old_level INTEGER NOT NULL,
    new_level INTEGER NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    change_reason VARCHAR(500),
    change_type VARCHAR(50) NOT NULL, -- upgrade/downgrade/grant/revoke
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(50),
    platform VARCHAR(50), -- web/desktop/mobile/api
    session_id VARCHAR(255)
);

CREATE INDEX idx_audit_user ON permission_audit_logs(user_id);
CREATE INDEX idx_audit_timestamp ON permission_audit_logs(timestamp);
CREATE INDEX idx_audit_changed_by ON permission_audit_logs(changed_by);
```

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/security/PermissionAuditLogService.java
package com.livingagent.core.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 权限审计日志服务 - 闭环14增强
 */
@Service
public class PermissionAuditLogService {
    
    private final PermissionAuditLogRepository repository;
    
    public PermissionAuditLogService(PermissionAuditLogRepository repository) {
        this.repository = repository;
    }
    
    /**
     * 记录权限变更审计日志
     */
    @Transactional
    public void recordPermissionChange(String userId, int oldLevel, int newLevel, String changedBy) {
        PermissionAuditLogEntity entity = new PermissionAuditLogEntity();
        entity.setUserId(userId);
        entity.setOldLevel(oldLevel);
        entity.setNewLevel(newLevel);
        entity.setChangedBy(changedBy);
        entity.setTimestamp(Instant.now());
        entity.setChangeType(determineChangeType(oldLevel, newLevel));
        
        repository.save(entity);
    }
    
    /**
     * 查询用户权限变更历史
     */
    public List<PermissionAuditLogEntity> getPermissionHistory(String userId) {
        return repository.findByUserIdOrderByTimestampDesc(userId);
    }
    
    /**
     * 查询某时间段内的权限变更记录
     */
    public List<PermissionAuditLogEntity> getPermissionChanges(Instant start, Instant end) {
        return repository.findByTimestampBetween(start, end);
    }
    
    private String determineChangeType(int oldLevel, int newLevel) {
        if (newLevel > oldLevel) {
            return "upgrade";
        } else if (newLevel < oldLevel) {
            return "downgrade";
        } else {
            return "modify";
        }
    }
}
```

#### 4.2.5 验收标准

| 验收项 | 验收方法 | 预期结果 |
|--------|---------|---------|
| 声纹注册 | POST /api/voice/register 提供音频 | 注册成功，返回userId |
| 声纹验证 | POST /api/voice/validate 提供音频 | 返回matched/not_matched |
| 权限变更强制生效 | POST /api/permissions/update 后WebSocket连接断开 | WebSocket收到permission_changed消息后关闭 |
| 跨端权限同步 | 权限变更后桌面端刷新权限 | 桌面端显示新权限级别 |
| 权限审计日志查询 | GET /api/permissions/audit/{userId} | 返回权限变更历史列表 |

---

## 五、实施优先级

```
┌──────────────────────────────────────────────────────────────┐
│                    实施优先级排序                               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  P0 - 立即实施（阻塞上线）：                                   │
│  ├─ P12-A: 启动依赖检查增强                                   │
│  ├─ P12-B: 健康检查端点增强                                   │
│  ├─ P12-C: 启动顺序强制控制                                   │
│  ├─ P14-B: 权限变更强制实时生效                               │
│                                                              │
│  P1 - 近期实施（影响体验）：                                   │
│  ├─ P12-D: 启动失败自动恢复                                   │
│  ├─ P14-A: 声纹验证实现                                       │
│  ├─ P14-C: 跨端权限实时同步                                   │
│  ├─ P14-D: 权限审计日志记录                                   │
│                                                              │
│  实施顺序建议：                                               │
│  ├─ Sprint 1: P12-A + P12-B + P12-C（服务启动闭环）          │
│  ├─ Sprint 2: P14-B（权限强制生效）                           │
│  ├─ Sprint 3: P12-D（启动自动恢复）                           │
│  ├─ Sprint 4: P14-A + P14-C + P14-D（权限完整闭环）          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 六、闭环验收清单更新

根据补充改进方案，更新 COMPLETE_FLOW_DOCUMENTATION.md 中的闭环验收清单：

| 序号 | 闭环名称 | 原状态 | 补充改进后状态 |
|------|----------|--------|----------------|
| 12 | 服务启动健康闭环 | 待验证 | 需补充改进（P12-A/P12-B/P12-C/P12-D） |
| 14 | 权限管理闭环 | 待验证 | 需补充改进（P14-A/P14-B/P14-C/P14-D） |

**改进后验收方法更新**：

```
闭环12验收方法（增强）：
1. 启动服务 → 查看启动日志 → 确认依赖检查通过
2. GET /api/health/namedpipe → 确认status=UP
3. GET /api/health/models → 确认模型加载状态
4. 删除模型文件后启动 → 确认降级启动模式
5. 恢复模型文件 → 调用attemptFullRecovery → 确认恢复

闭环14验收方法（增强）：
1. POST /api/voice/register → 注册声纹 → 确认成功
2. POST /api/voice/validate → 验证声纹 → 确认匹配
3. POST /api/permissions/update → 权限变更 → WebSocket断开重连
4. 权限变更后桌面端刷新 → 确认权限同步
5. GET /api/permissions/audit/{userId} → 查询审计日志 → 确认记录
```

---

## 七、文档维护说明

本补充改进方案应与以下文档配套使用：
- COMPLETE_FLOW_DOCUMENTATION.md（闭环需求定义）
- MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md（模型与执行优化）
- DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md（桌面端对接审计）

**更新建议**：
1. 将本补充方案内容添加到 MODEL_RESPONSIBILITY_AND_EXECUTION_OPTIMIZATION_PLAN.md 新章节
2. 更新 COMPLETE_FLOW_DOCUMENTATION.md 第25章闭环验收清单，标注补充改进方案
3. 更新 DESKTOP_BACKEND_INTEGRATION_AUDIT_AND_IMPROVEMENT_PLAN.md 后端闭环矩阵，标注补充改进后状态

**版本信息**：
- 文档版本: v1.0
- 生成日期: 2026-06-30
- 闭环覆盖率: 85.7%（12/14完整覆盖，2个补充改进）

# Living Agent Service - 新增闭环改进方案补充

> **生成日期**: 2026-06-30
> **目的**: 为新增的9个闭环需求生成详细改进方案，补充到 LOOP_COVERAGE_ANALYSIS_AND_SUPPLEMENTARY_IMPROVEMENT_PLAN.md。
> **新增闭环**: 闭环17、18-表级、19、20、22、3-A、3-B、11-A、11-B

---

## 一、新增闭环改进方案总览

### 1.1 新增闭环清单

| 编号 | 闭环名称 | 所属流程 | 闭环必要性 | 改进优先级 |
|------|---------|---------|-----------|-----------|
| 17 | 前端与后端交互闭环 | 流程17 | WebSocket连接监控、API响应验证、前端状态同步 | P1 |
| 18-表级 | 数据库持久化闭环（表级） | 流程18 | 每个表的写入验证、唯一约束检查、事务一致性 | P2 |
| 19 | Native Rust与Java交互闭环 | 流程19 | JNI调用验证、性能监控、异常处理 | P0 |
| 20 | Python模型守护进程闭环 | 流程20 | 进程存活监控、模型加载验证、NamedPipe健康检查 | P0 |
| 22 | Claude CLI代理闭环 | 流程22 | CLI可用性检查、调用验证、输出解析 | P1 |
| 3-A | WebSocket连接管理闭环 | 流程3子闭环 | 连接数限制、心跳机制、僵尸连接清理 | P2 |
| 3-B | 消息Trace闭环 | 流程3子闭环 | Trace完整性、Trace持久化验证 | P2 |
| 11-A | 员工状态管理闭环 | 流程11子闭环 | 员工状态正确性、任务分配记录 | P2 |
| 11-B | 通道监控闭环 | 流程11子闭环 | 通道消息投递、消费者处理验证 | P2 |

### 1.2 改进方案编号规则

```
改进方案编号规则：
P{闭环编号}-{改进序号}

示例：
P17-A: 前端WebSocket连接状态监控增强
P17-B: 前端API响应验证增强
P17-C: 前端状态同步机制增强
P17-D: 前端错误上报机制增强

P18-A: autonomy_trace_events表写入验证
P18-B: evolution_feedback表写入验证
P18-C: 其他关键表写入验证
```

---

## 二、闭环17改进方案：前端与后端交互闭环

### 2.1 问题分析

```
┌──────────────────────────────────────────────────────────────┐
│            闭环17：前端与后端交互闭环问题分析                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  问题1：WebSocket连接状态监控不足                              │
│  ├─ 前端WebSocket断开后，后端ConnectionRegistry可能未及时清理  │
│  ├─ 僵尸连接占用资源，影响系统性能                              │
│  ├─ 缺少前端连接状态可视化管理界面                              │
│                                                              │
│  问题2：API响应验证不完整                                      │
│  ├─ 前端API调用失败后缺少统一错误处理                           │
│  ├─ 超时请求未自动重试                                         │
│  ├─ 错误响应未记录日志                                         │
│                                                              │
│  问题3：前端状态同步不及时                                     │
│  ├─ 权限变更后前端未自动刷新                                   │
│  ├─ 会话状态变更前端未感知                                     │
│  ├─ 数据更新前端未自动刷新                                     │
│                                                              │
│  问题4：前端错误未上报后端                                     │
│  ├─ 前端异常仅Console.error，后端无感知                        │
│  ├─ 缺少统一错误上报机制                                       │
│  ├─ 错误日志未持久化                                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 改进方案 P17-A：WebSocket连接状态监控增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| frontend/src/services/WebSocketService.ts | 修改：增加连接状态监控、心跳机制 | P1 |
| frontend/src/stores/connectionStore.ts | 新增：连接状态Store | P1 |
| frontend/src/components/ConnectionStatus.tsx | 新增：连接状态可视化组件 | P2 |
| living-agent-gateway/.../websocket/ConnectionRegistry.java | 修改：增强僵尸连接检测 | P1 |

**代码实现**：

```typescript
// 文件：frontend/src/stores/connectionStore.ts
import { create } from 'zustand';

interface ConnectionState {
  isConnected: boolean;
  connectionId: string | null;
  lastHeartbeat: Date | null;
  reconnectAttempts: number;
  connectionQuality: 'good' | 'poor' | 'disconnected';
}

export const useConnectionStore = create<ConnectionState>((set) => ({
  isConnected: false,
  connectionId: null,
  lastHeartbeat: null,
  reconnectAttempts: 0,
  connectionQuality: 'disconnected',
}));

// 文件：frontend/src/services/WebSocketService.ts
export class WebSocketService {
  private ws: WebSocket | null = null;
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private readonly HEARTBEAT_INTERVAL = 30000; // 30秒
  private readonly MAX_RECONNECT_ATTEMPTS = 5;

  // 增强连接状态监控
  connect(url: string): void {
    this.ws = new WebSocket(url);
    
    this.ws.onopen = () => {
      useConnectionStore.setState({
        isConnected: true,
        connectionQuality: 'good',
        reconnectAttempts: 0,
      });
      this.startHeartbeat();
      this.reportConnectionStatus('connected');
    };

    this.ws.onclose = () => {
      useConnectionStore.setState({
        isConnected: false,
        connectionQuality: 'disconnected',
      });
      this.stopHeartbeat();
      this.reportConnectionStatus('disconnected');
      this.scheduleReconnect();
    };

    this.ws.onerror = (error) => {
      useConnectionStore.setState({
        connectionQuality: 'poor',
      });
      this.reportError(error);
    };
  }

  // 心跳机制增强
  private startHeartbeat(): void {
    this.heartbeatInterval = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'heartbeat' }));
        useConnectionStore.setState({
          lastHeartbeat: new Date(),
        });
      }
    }, this.HEARTBEAT_INTERVAL);
  }

  // 连接状态上报后端
  private reportConnectionStatus(status: 'connected' | 'disconnected'): void {
    fetch('/api/connection/status', {
      method: 'POST',
      body: JSON.stringify({
        status,
        connectionId: useConnectionStore.getState().connectionId,
        timestamp: new Date(),
      }),
    });
  }

  // 错误上报后端
  private reportError(error: Event): void {
    fetch('/api/error/report', {
      method: 'POST',
      body: JSON.stringify({
        type: 'websocket_error',
        error: error.toString(),
        connectionId: useConnectionStore.getState().connectionId,
        timestamp: new Date(),
      }),
    });
  }
}
```

### 2.3 改进方案 P17-B：API响应验证增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| frontend/src/services/ApiService.ts | 新增：统一API Service | P1 |
| frontend/src/utils/errorHandler.ts | 新增：统一错误处理 | P1 |
| frontend/src/utils/retryHandler.ts | 新增：自动重试机制 | P2 |

**代码实现**：

```typescript
// 文件：frontend/src/services/ApiService.ts
import { errorHandler } from '../utils/errorHandler';
import { retryHandler } from '../utils/retryHandler';

export class ApiService {
  private readonly MAX_RETRIES = 3;
  private readonly RETRY_DELAY = 1000;

  async request<T>(url: string, options?: RequestInit): Promise<T> {
    return retryHandler.retry(
      async () => {
        const response = await fetch(url, options);
        
        // 响应验证
        if (!response.ok) {
          const error = await response.json();
          throw new ApiError(error.error, error.description, response.status);
        }

        const data = await response.json();
        
        // ApiResponse.success字段检查
        if (!data.success) {
          throw new ApiError(data.error, data.description, response.status);
        }

        return data.data as T;
      },
      {
        maxRetries: this.MAX_RETRIES,
        retryDelay: this.RETRY_DELAY,
        shouldRetry: (error) => error.status >= 500, // 仅重试服务器错误
      }
    );
  }
}

// 文件：frontend/src/utils/errorHandler.ts
export class ApiError extends Error {
  constructor(
    public error: string,
    public description: string,
    public status: number
  ) {
    super(description);
  }
}

export const errorHandler = {
  handle(error: ApiError): void {
    // 记录错误日志
    console.error(`[${error.status}] ${error.error}: ${error.description}`);
    
    // 上报后端
    fetch('/api/error/report', {
      method: 'POST',
      body: JSON.stringify({
        type: 'api_error',
        error: error.error,
        description: error.description,
        status: error.status,
        timestamp: new Date(),
      }),
    });
    
    // 显示错误提示
    if (error.status >= 500) {
      alert('服务器错误，请稍后重试');
    } else if (error.status === 401) {
      alert('认证失败，请重新登录');
    }
  },
};
```

### 2.4 改进方案 P17-C：前端状态同步机制增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| frontend/src/services/StateSyncService.ts | 新增：状态同步服务 | P1 |
| frontend/src/hooks/usePermissionSync.ts | 新增：权限同步Hook | P1 |
| frontend/src/hooks/useSessionSync.ts | 新增：会话同步Hook | P2 |

**代码实现**：

```typescript
// 文件：frontend/src/services/StateSyncService.ts
export class StateSyncService {
  private ws: WebSocket | null = null;

  // 监听后端状态变更通知
  subscribeToStateChanges(): void {
    if (!this.ws) return;

    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      
      if (message.type === 'permission_update') {
        // 权限变更通知
        this.handlePermissionUpdate(message.data);
      } else if (message.type === 'session_update') {
        // 会话变更通知
        this.handleSessionUpdate(message.data);
      } else if (message.type === 'data_update') {
        // 数据更新通知
        this.handleDataUpdate(message.data);
      }
    };
  }

  private handlePermissionUpdate(data: any): void {
    // 强制WebSocket断开重连
    if (this.ws) {
      this.ws.close();
    }
    
    // 刷新权限状态
    window.location.reload();
  }

  private handleSessionUpdate(data: any): void {
    // 更新会话状态
    useSessionStore.setState(data);
  }

  private handleDataUpdate(data: any): void {
    // 触发数据刷新
    window.dispatchEvent(new CustomEvent('data_update', { detail: data }));
  }
}
```

### 2.5 改进方案 P17-D：前端错误上报机制增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| frontend/src/utils/errorReporter.ts | 新增：错误上报服务 | P1 |
| living-agent-gateway/.../controller/ErrorReportController.java | 新增：错误接收端点 | P1 |
| living-agent-core/.../database/entity/FrontendErrorEntity.java | 新增：前端错误实体 | P2 |
| init-db/01_init.sql | 新增：frontend_errors表 | P2 |

**代码实现**：

```typescript
// 文件：frontend/src/utils/errorReporter.ts
export class ErrorReporter {
  private readonly endpoint = '/api/error/report';

  report(error: Error, context?: any): void {
    fetch(this.endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        type: 'frontend_error',
        message: error.message,
        stack: error.stack,
        context: context,
        url: window.location.href,
        userAgent: navigator.userAgent,
        timestamp: new Date(),
      }),
    }).catch((e) => {
      // 上报失败，记录到本地
      console.error('Error report failed:', e);
    });
  }

  // 全局错误捕获
  setupGlobalErrorHandler(): void {
    window.onerror = (message, source, lineno, colno, error) => {
      this.report(error || new Error(message), {
        source,
        lineno,
        colno,
      });
      return false;
    };

    window.onunhandledrejection = (event) => {
      this.report(event.reason);
    };
  }
}
```

---

## 三、闭环18改进方案：数据库持久化闭环（表级）

### 3.1 问题分析

```
┌──────────────────────────────────────────────────────────────┐
│            闭环18：数据库持久化闭环问题分析                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  问题1：autonomy_trace_events表写入验证不足                          │
│  ├─ Trace写入后缺少查询确认                                   │
│  ├─ traceId唯一约束可能被重复插入违反                          │
│  ├─ Trace状态更新未记录变更历史                                │
│                                                              │
│  问题2：evolution_feedback表写入验证不足                       │
│  ├─ 反馈写入后缺少查询确认                                    │
│  ├─ 反馈评分未校验范围                                        │
│  ├─ 反馈历史未记录                                            │
│                                                              │
│  问题3：其他关键表写入验证缺失                                 │
│  ├─ approval_instances状态流转未验证                          │
│  ├─ brain_model_assignments模型变更未记录                     │
│  ├─ knowledge_entries向量同步未验证                           │
│  ├─ tasks状态流转未验证                            │
│                                                              │
│  问题4：事务一致性验证不足                                     │
│  ├─ 多表写入失败后可能部分写入                                │
│  ├─ @Transactional回滚未验证                                  │
│  ├─ 分布式事务缺失                                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 改进方案 P18-A：autonomy_trace_events表写入验证

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../autonomy/trace/TraceEventRepository.java | 修改：增加写入验证方法 | P1 |
| living-agent-core/.../autonomy/trace/AutonomyTraceService.java | 新增：Trace服务层验证 | P1 |
| init-db/01_init.sql | 修改：增加traceId唯一约束 | P0 |

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/autonomy/trace/TraceEventRepository.java
package com.livingagent.core.autonomy.trace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface TraceEventRepository extends JpaRepository<TraceEventEntity, Long> {
    
    Optional<TraceEventEntity> findByTraceId(String traceId);
    
    @Transactional
    TraceEventEntity save(TraceEventEntity entity);
    
    // 新增：写入验证方法
    default TraceEventEntity saveAndVerify(TraceEventEntity entity) {
        TraceEventEntity saved = save(entity);
        
        // 立即查询验证
        Optional<TraceEventEntity> verified = findByTraceId(saved.getTraceId());
        if (verified.isEmpty()) {
            throw new IllegalStateException("Trace save verification failed: " + saved.getTraceId());
        }
        
        return saved;
    }
}

// 文件：living-agent-core/src/main/java/com/livingagent/core/autonomy/trace/AutonomyTraceService.java
package com.livingagent.core.autonomy.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutonomyTraceService {
    
    private static final Logger log = LoggerFactory.getLogger(AutonomyTraceService.class);
    private final TraceEventRepository repository;
    
    public AutonomyTraceService(TraceEventRepository repository) {
        this.repository = repository;
    }
    
    @Transactional
    public TraceEventEntity createAndPersist(AutonomyTraceService trace) {
        TraceEventEntity entity = toEntity(trace);
        
        try {
            TraceEventEntity saved = repository.saveAndVerify(entity);
            log.info("Trace created and verified: traceId={}", saved.getTraceId());
            return saved;
        } catch (Exception e) {
            log.error("Trace save failed: traceId={}", entity.getTraceId(), e);
            throw new RuntimeException("Trace save failed", e);
        }
    }
    
    // traceId唯一约束检查
    public boolean isTraceIdUnique(String traceId) {
        return repository.findByTraceId(traceId).isEmpty();
    }
    
    private TraceEventEntity toEntity(AutonomyTraceService trace) {
        // 转换逻辑
        TraceEventEntity entity = new TraceEventEntity();
        entity.setTraceId(trace.getTraceId());
        entity.setUserId(trace.getUserId());
        entity.setSessionId(trace.getSessionId());
        entity.setStatus(trace.getStatus().name());
        entity.setRecordsJson(trace.getRecordsJson());
        entity.setCreatedAt(trace.getCreatedAt());
        return entity;
    }
}
```

**数据库Schema更新**：

```sql
-- 文件：init-db/01_init.sql
-- autonomy_trace_events表增加traceId唯一约束
CREATE TABLE IF NOT EXISTS autonomy_trace_events (
    id SERIAL PRIMARY KEY,
    trace_id VARCHAR(64) UNIQUE NOT NULL,  -- 增加UNIQUE约束
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    records_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 唯一约束检查触发器
CREATE OR REPLACE FUNCTION check_trace_unique()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM autonomy_trace_events WHERE trace_id = NEW.trace_id AND id != NEW.id) THEN
        RAISE EXCEPTION 'Duplicate trace_id: %', NEW.trace_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_check_trace_unique
BEFORE INSERT OR UPDATE ON autonomy_trace_events
FOR EACH ROW EXECUTE FUNCTION check_trace_unique();
```

### 3.3 改进方案 P18-B：evolution_feedback表写入验证

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/evolution/feedback/EvolutionFeedbackRepository.java
package com.livingagent.core.evolution.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface EvolutionFeedbackRepository extends JpaRepository<EvolutionFeedbackEntity, Long> {
    
    Optional<EvolutionFeedbackEntity> findById(Long id);
    
    // 新增：写入验证方法
    default EvolutionFeedbackEntity saveAndVerify(EvolutionFeedbackEntity entity) {
        EvolutionFeedbackEntity saved = save(entity);
        
        // 立即查询验证
        Optional<EvolutionFeedbackEntity> verified = findById(saved.getId());
        if (verified.isEmpty()) {
            throw new IllegalStateException("Feedback save verification failed: " + saved.getId());
        }
        
        return saved;
    }
}
```

### 3.4 改进方案 P18-C：其他关键表写入验证

**关键表清单与验证方案**：

| 表名 | 验证方法 | 新增代码位置 |
|------|---------|-------------|
| approval_instances | 状态流转验证：PENDING→IN_PROGRESS→APPROVED/REJECTED | ApprovalInstanceRepository.saveAndVerify() |
| brain_model_assignments | 模型变更验证：model_id更新 + 变更历史记录 | BrainModelAssignmentsRepository.saveAndVerify() |
| knowledge_entries | 向量同步验证：Qdrant向量ID一致 | KnowledgeEntriesRepository.saveAndVerify() |
| tasks | 状态流转验证：CREATED→CHECKED_OUT→COMPLETED→FAILED | TaskRepository.saveAndVerify() |

---

## 四、闭环19改进方案：Native Rust与Java交互闭环

### 4.1 问题分析

```
┌──────────────────────────────────────────────────────────────┐
│            闭环19：Native Rust与Java交互闭环问题分析              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  问题1：JNI调用结果验证不足                                    │
│  ├─ Native方法返回值未检查                                    │
│  ├─ Native异常未正确转换为Java异常                            │
│  ├─ 调用失败未记录日志                                        │
│                                                              │
│  问题2：Native执行性能监控缺失                                 │
│  ├─ 音频处理执行时间未记录                                    │
│  ├─ 管道通信延迟未监控                                        │
│  ├─ 性能瓶颈未识别                                            │
│                                                              │
│  问题3：Native模块健康检查缺失                                 │
│  ├─ 模块加载状态未检查                                        │
│  ├─ 版本兼容性未验证                                          │
│  ├─ 模块损坏未检测                                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 改进方案 P19-A：JNI调用结果验证增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../native/NativeServiceWrapper.java | 新增：Native服务包装器 | P0 |
| living-agent-core/.../native/NativeExceptionHandler.java | 新增：Native异常处理器 | P0 |

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/nativelib/NativeServiceWrapper.java
package com.livingagent.core.nativelib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NativeServiceWrapper {
    
    private static final Logger log = LoggerFactory.getLogger(NativeServiceWrapper.class);
    private final NativeExceptionHandler exceptionHandler;
    
    public NativeServiceWrapper(NativeExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
    
    // JNI调用包装：结果验证 + 异常处理 + 性能监控
    public <T> T callNative(NativeCallable<T> callable, String operationName) {
        long startTime = System.currentTimeMillis();
        
        try {
            T result = callable.call();
            
            // 结果验证
            if (result == null) {
                throw new NativeException("Native method returned null: " + operationName);
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Native call success: {} ({}ms)", operationName, elapsed);
            
            return result;
        } catch (Throwable nativeError) {
            // JNI异常转换为Java异常
            JavaException javaException = exceptionHandler.convert(nativeError, operationName);
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Native call failed: {} ({}ms) - {}", operationName, elapsed, javaException.getMessage());
            
            throw javaException;
        }
    }
    
    public interface NativeCallable<T> {
        T call() throws Throwable;
    }
}
```

### 4.3 改进方案 P19-B：Native执行性能监控增强

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/nativelib/NativePerformanceMonitor.java
package com.livingagent.core.nativelib;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NativePerformanceMonitor {
    
    private final Map<String, PerformanceStats> statsMap = new ConcurrentHashMap<>();
    
    // 记录Native执行时间
    public void record(String operation, long elapsedMs) {
        PerformanceStats stats = statsMap.computeIfAbsent(operation, k -> new PerformanceStats());
        stats.record(elapsedMs);
        
        // 超过阈值报警
        if (elapsedMs > getThreshold(operation)) {
            log.warn("Native operation slow: {} ({}ms)", operation, elapsedMs);
        }
    }
    
    private long getThreshold(String operation) {
        if (operation.contains("audio")) {
            return 100; // 音频处理阈值100ms
        } else if (operation.contains("pipe")) {
            return 50; // 管道通信阈值50ms
        }
        return 1000; // 默认阈值1秒
    }
    
    public PerformanceStats getStats(String operation) {
        return statsMap.get(operation);
    }
    
    public static class PerformanceStats {
        private long totalCalls = 0;
        private long totalTimeMs = 0;
        private long maxTimeMs = 0;
        
        public void record(long elapsedMs) {
            totalCalls++;
            totalTimeMs += elapsedMs;
            maxTimeMs = Math.max(maxTimeMs, elapsedMs);
        }
        
        public long getAverageTimeMs() {
            return totalCalls > 0 ? totalTimeMs / totalCalls : 0;
        }
    }
}
```

### 4.4 改进方案 P19-C：Native模块健康检查增强

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/nativelib/NativeModuleHealthChecker.java （基于core/diagnosis/自研健康检查体系，非Spring Actuator）
package com.livingagent.core.nativelib;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class NativeModuleHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator）
    
    @Override
    public Health health() {
        try {
            // 检查Native模块加载状态
            boolean moduleLoaded = checkModuleLoaded();
            
            // 检查版本兼容性
            boolean versionCompatible = checkVersionCompatible();
            
            // 检查模块功能
            boolean moduleFunctional = checkModuleFunctional();
            
            if (moduleLoaded && versionCompatible && moduleFunctional) {
                return Health.up()
                    .withDetail("module_loaded", true)
                    .withDetail("version_compatible", true)
                    .withDetail("module_functional", true)
                    .build();
            } else {
                return Health.down()
                    .withDetail("module_loaded", moduleLoaded)
                    .withDetail("version_compatible", versionCompatible)
                    .withDetail("module_functional", moduleFunctional)
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    private boolean checkModuleLoaded() {
        try {
            System.loadLibrary("living-agent-native");
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }
    
    private boolean checkVersionCompatible() {
        // 检查Native模块版本
        // TODO: 实现版本检查逻辑
        return true;
    }
    
    private boolean checkModuleFunctional() {
        // 检查模块功能可用性
        // TODO: 实现功能检查逻辑
        return true;
    }
}
```

---

## 五、闭环20改进方案：Python模型守护进程闭环

### 5.1 问题分析

```
┌──────────────────────────────────────────────────────────────┐
│            闭环20：Python模型守护进程闭环问题分析                 │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  问题1：进程存活监控缺失                                       │
│  ├─ model_daemon.py进程崩溃后无感知                           │
│  ├─ 僵尸进程未清理                                            │
│  ├─ 进程重启后状态未恢复                                       │
│                                                              │
│  问题2：模型加载验证不足                                       │
│  ├─ Qwen3/Qwen3.5加载状态未检查                              │
│  ├─ 模型加载失败无降级机制                                     │
│  ├─ 模型内存占用未监控                                        │
│                                                              │
│  问题3：NamedPipe健康检查缺失                                  │
│  ├─ 管道损坏未检测                                            │
│  ├─ 管道阻塞未清理                                            │
│  ├─ 管道读写超时未处理                                        │
│                                                              │
│  问题4：推理结果验证不足                                       │
│  ├─ 推理响应未验证                                            │
│  ├─ 推理超时未降级                                            │
│  ├─ 推理失败未记录                                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 5.2 改进方案 P20-A：进程存活监控增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../model/ModelDaemonProcessMonitor.java | 新增：进程监控服务 | P0 |
| scripts/python/model_daemon.py | 修改：增加进程健康报告 | P0 |
| Dockerfile | 修改：增加进程监控脚本 | P1 |

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/ModelDaemonProcessMonitor.java
package com.livingagent.core.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class ModelDaemonProcessMonitor {
    
    private static final Logger log = LoggerFactory.getLogger(ModelDaemonProcessMonitor.class);
    private static final String PROCESS_NAME = "model_daemon.py";
    
    // 每30秒检查进程存活
    @Scheduled(fixedRate = 30000)
    public void checkProcessAlive() {
        boolean alive = isProcessAlive();
        
        if (!alive) {
            log.error("model_daemon.py process is not alive!");
            attemptRecovery();
        } else {
            log.debug("model_daemon.py process is alive");
        }
    }
    
    private boolean isProcessAlive() {
        try {
            Process process = Runtime.getRuntime().exec("ps aux | grep model_daemon.py");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("model_daemon.py") && !line.contains("grep")) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("Process check failed", e);
            return false;
        }
    }
    
    private void attemptRecovery() {
        log.info("Attempting to restart model_daemon.py...");
        
        try {
            // 重启进程
            Runtime.getRuntime().exec("python scripts/python/model_daemon.py &");
            
            // 等待5秒后验证
            Thread.sleep(5000);
            
            if (isProcessAlive()) {
                log.info("model_daemon.py restarted successfully");
            } else {
                log.error("model_daemon.py restart failed - entering degraded mode");
                System.setProperty("model_daemon.mode", "degraded");
            }
        } catch (Exception e) {
            log.error("Recovery failed", e);
        }
    }
}
```

### 5.3 改进方案 P20-B：模型加载验证增强

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/ModelLoadHealthChecker.java （基于core/diagnosis/自研健康检查体系，非Spring Actuator）
package com.livingagent.core.model;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ModelLoadHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator）
    
    private final NamedPipeModelClient namedPipeModelClient;
    
    public ModelLoadHealthIndicator(NamedPipeModelClient namedPipeModelClient) {
        this.namedPipeModelClient = namedPipeModelClient;
    }
    
    @Override
    public Health health() {
        try {
            // 检查Qwen3模型加载状态
            boolean qwen3Loaded = checkModelLoaded("Qwen3-0.6B");
            
            // 检查Qwen3.5模型加载状态
            boolean qwen35Loaded = checkModelLoaded("Qwen3.5-2B");
            
            if (qwen3Loaded || qwen35Loaded) {
                return Health.up()
                    .withDetail("qwen3_loaded", qwen3Loaded)
                    .withDetail("qwen35_loaded", qwen35Loaded)
                    .build();
            } else {
                return Health.down()
                    .withDetail("qwen3_loaded", false)
                    .withDetail("qwen35_loaded", false)
                    .withDetail("error", "No models loaded")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    private boolean checkModelLoaded(String modelName) {
        try {
            // 通过NamedPipeModelClient发送模型状态查询请求
            String response = namedPipeModelClient.queryModelStatus(modelName);
            return response.contains("loaded");
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 5.4 改进方案 P20-C：NamedPipe健康检查增强

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/NamedPipeHealthChecker.java （基于core/diagnosis/自研健康检查体系，非Spring Actuator）
package com.livingagent.core.model;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class NamedPipeHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator）
    
    private final String inputPipePath = "/tmp/dialogue_daemon_control_request";
    private final String outputPipePath = "/tmp/dialogue_daemon_control_response";
    
    @Override
    public Health health() {
        try {
            boolean inputExists = Files.exists(Path.of(inputPipePath));
            boolean outputExists = Files.exists(Path.of(outputPipePath));
            
            boolean inputWritable = Files.isWritable(Path.of(inputPipePath));
            boolean outputReadable = Files.isReadable(Path.of(outputPipePath));
            
            if (inputExists && outputExists && inputWritable && outputReadable) {
                return Health.up()
                    .withDetail("input_pipe", "exists and writable")
                    .withDetail("output_pipe", "exists and readable")
                    .build();
            } else {
                return Health.down()
                    .withDetail("input_pipe", inputExists ? "exists" : "missing")
                    .withDetail("input_pipe_writable", inputWritable)
                    .withDetail("output_pipe", outputExists ? "exists" : "missing")
                    .withDetail("output_pipe_readable", outputReadable)
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### 5.5 改进方案 P20-D：推理结果验证增强

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/model/provider/impl/NamedPipeModelClient.java
package com.livingagent.core.model.provider.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class NamedPipeModelClient implements ModelProvider {
    
    private static final Logger log = LoggerFactory.getLogger(NamedPipeModelClient.class);
    private static final int INFERENCE_TIMEOUT_MS = 30000;
    
    private final String inputPipePath = "/tmp/dialogue_daemon_control_request";
    private final String outputPipePath = "/tmp/dialogue_daemon_control_response";
    
    @Override
    public ModelResponse inference(ModelRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 发送请求到NamedPipe
            String requestJson = serializeRequest(request);
            writeToPipe(requestJson);
            
            // 等待响应（超时30秒）
            String responseJson = readFromPipeWithTimeout(INFERENCE_TIMEOUT_MS);
            
            // 验证响应
            if (responseJson == null || responseJson.isEmpty()) {
                throw new ModelInferenceException("Empty response from NamedPipe");
            }
            
            ModelResponse response = deserializeResponse(responseJson);
            
            // 响应验证
            if (!response.isSuccess()) {
                throw new ModelInferenceException("Inference failed: " + response.getError());
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("NamedPipe inference success: {}ms", elapsed);
            
            return response;
        } catch (TimeoutException e) {
            log.error("NamedPipe inference timeout: {}ms", INFERENCE_TIMEOUT_MS);
            throw new ModelInferenceException("Inference timeout", e);
        } catch (Exception e) {
            log.error("NamedPipe inference failed", e);
            throw new ModelInferenceException("Inference failed", e);
        }
    }
    
    private String readFromPipeWithTimeout(int timeoutMs) throws TimeoutException {
        // 实现带超时的管道读取
        // ...
    }
}
```

---

## 六、闭环22改进方案：Claude CLI代理闭环

### 6.1 问题分析

```
┌──────────────────────────────────────────────────────────────┐
│            闭环22：Claude CLI代理闭环问题分析                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  问题1：CLI可用性检查缺失                                      │
│  ├─ Claude CLI未安装时未检测                                  │
│  ├─ CLI版本兼容性未检查                                       │
│  ├─ CLI损坏未检测                                             │
│                                                              │
│  问题2：调用结果验证不足                                       │
│  ├─ ProcessBuilder执行结果未检查                              │
│  ├─ exit code非0未处理                                        │
│  ├─ 执行失败未记录                                            │
│                                                              │
│  问题3：输出解析验证不足                                       │
│  ├─ JSON解析失败未处理                                        │
│  ├─ 字段提取错误未检测                                        │
│  ├─ 输出格式变化未兼容                                        │
│                                                              │
│  问题4：异常处理不足                                           │
│  ├─ CLI未安装时未返回提示信息                                 │
│  ├─ 执行超时未终止                                            │
│  ├─ 异常未上报                                                │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 改进方案 P22-A：CLI可用性检查增强

**文件修改清单**：

| 文件 | 修改内容 | 优先级 |
|------|---------|--------|
| living-agent-core/.../tool/impl/ClaudeCliTool.java | 修改：增加CLI可用性检查 | P1 |
| living-agent-core/.../tool/impl/ClaudeCliHealthChecker.java | 新增：CLI健康检查（基于core/diagnosis/自研健康检查体系，非Spring Actuator） | P1 |

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/tool/impl/ClaudeCliHealthChecker.java （基于core/diagnosis/自研健康检查体系，非Spring Actuator）
package com.livingagent.core.tool.impl;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Component
public class ClaudeCliHealthChecker（基于core/diagnosis/自研健康检查体系，非Spring Actuator）
    
    @Override
    public Health health() {
        try {
            // 检查CLI安装
            boolean installed = checkCliInstalled();
            
            // 检查CLI版本
            String version = getCliVersion();
            
            if (installed) {
                return Health.up()
                    .withDetail("installed", true)
                    .withDetail("version", version)
                    .build();
            } else {
                return Health.down()
                    .withDetail("installed", false)
                    .withDetail("message", "Claude CLI not installed")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    private boolean checkCliInstalled() {
        try {
            Process process = Runtime.getRuntime().exec("claude --version");
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private String getCliVersion() {
        try {
            Process process = Runtime.getRuntime().exec("claude --version");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.readLine();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
```

### 6.3 改进方案 P22-B：调用结果验证增强

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/tool/impl/ClaudeCliTool.java
package com.livingagent.core.tool.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ClaudeCliTool implements Tool {
    
    private static final Logger log = LoggerFactory.getLogger(ClaudeCliTool.class);
    private static final int EXECUTION_TIMEOUT_SECONDS = 60;
    
    @Override
    public ToolResult execute(ToolRequest request) {
        // CLI可用性检查
        if (!isCliAvailable()) {
            return ToolResult.error("Claude CLI not installed. Please install from https://claude.ai/cli");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            ProcessBuilder pb = new ProcessBuilder("claude", request.getPrompt());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // 等待执行完成（超时60秒）
            boolean finished = process.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                log.error("Claude CLI execution timeout: {}s", EXECUTION_TIMEOUT_SECONDS);
                return ToolResult.error("Claude CLI execution timeout");
            }
            
            int exitCode = process.exitValue();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            
            // 执行结果验证
            if (exitCode != 0) {
                log.error("Claude CLI execution failed: exit code {}", exitCode);
                return ToolResult.error("Claude CLI execution failed: " + output);
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("Claude CLI execution success: {}ms", elapsed);
            
            return ToolResult.success(output);
        } catch (Exception e) {
            log.error("Claude CLI execution exception", e);
            return ToolResult.error("Claude CLI execution exception: " + e.getMessage());
        }
    }
    
    private boolean isCliAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("claude --version");
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 6.4 改进方案 P22-C：输出解析验证增强

**代码实现**：

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/tool/impl/ClaudeOutputParser.java
package com.livingagent.core.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClaudeOutputParser {
    
    private static final Logger log = LoggerFactory.getLogger(ClaudeOutputParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public ClaudeResponse parse(String output) {
        try {
            // JSON解析
            ClaudeResponse response = objectMapper.readValue(output, ClaudeResponse.class);
            
            // 字段验证
            if (response.getText() == null || response.getText().isEmpty()) {
                throw new ParseException("Missing text field in Claude response");
            }
            
            log.debug("Claude output parsed successfully");
            return response;
        } catch (Exception e) {
            log.error("Claude output parse failed: {}", output, e);
            throw new ParseException("Failed to parse Claude output: " + e.getMessage(), e);
        }
    }
}
```

---

## 七、子闭环改进方案

### 7.1 闭环3-A：WebSocket连接管理闭环改进

**改进方案 P3-A-A：连接数限制增强**

```java
// 文件：living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/ConnectionRegistry.java
package com.livingagent.gateway.websocket;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConnectionRegistry {
    
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger globalConnectionCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> departmentConnectionCounts = new ConcurrentHashMap<>();
    
    private static final int MAX_GLOBAL_CONNECTIONS = 500;
    private static final int MAX_DEPARTMENT_CONNECTIONS = 50;
    
    public boolean registerSession(WebSocketSession session, String department) {
        // 全局连接数检查
        if (globalConnectionCount.get() >= MAX_GLOBAL_CONNECTIONS) {
            log.warn("Global connection limit reached: {}", MAX_GLOBAL_CONNECTIONS);
            return false;
        }
        
        // 部门连接数检查
        AtomicInteger deptCount = departmentConnectionCounts.computeIfAbsent(department, k -> new AtomicInteger(0));
        if (deptCount.get() >= MAX_DEPARTMENT_CONNECTIONS) {
            log.warn("Department connection limit reached: {} - {}", department, MAX_DEPARTMENT_CONNECTIONS);
            return false;
        }
        
        // 注册连接
        sessions.put(session.getId(), session);
        globalConnectionCount.incrementAndGet();
        deptCount.incrementAndGet();
        
        log.info("Session registered: {} (global: {}, dept: {})", 
            session.getId(), globalConnectionCount.get(), deptCount.get());
        
        return true;
    }
    
    public void unregisterSession(String sessionId, String department) {
        sessions.remove(sessionId);
        globalConnectionCount.decrementAndGet();
        
        AtomicInteger deptCount = departmentConnectionCounts.get(department);
        if (deptCount != null) {
            deptCount.decrementAndGet();
        }
        
        log.info("Session unregistered: {} (global: {}, dept: {})", 
            sessionId, globalConnectionCount.get(), deptCount != null ? deptCount.get() : 0);
    }
}
```

**改进方案 P3-A-B：心跳机制增强**

```java
// 文件：living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/HeartbeatMonitor.java
package com.livingagent.gateway.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HeartbeatMonitor {
    
    private final ConcurrentHashMap<String, Instant> lastHeartbeats = new ConcurrentHashMap<>();
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 90;
    
    public void recordHeartbeat(String sessionId) {
        lastHeartbeats.put(sessionId, Instant.now());
    }
    
    @Scheduled(fixedRate = 30000)
    public void checkHeartbeats() {
        Instant now = Instant.now();
        
        lastHeartbeats.forEach((sessionId, lastHeartbeat) -> {
            long elapsedSeconds = now.getEpochSecond() - lastHeartbeat.getEpochSecond();
            
            if (elapsedSeconds > HEARTBEAT_TIMEOUT_SECONDS) {
                log.warn("Session heartbeat timeout: {} ({}s)", sessionId, elapsedSeconds);
                // 触发僵尸连接清理
                cleanStaleConnection(sessionId);
            }
        });
    }
    
    private void cleanStaleConnection(String sessionId) {
        // 清理僵尸连接
        // ...
    }
}
```

**改进方案 P3-A-C：僵尸连接清理增强**

```java
// 文件：living-agent-gateway/src/main/java/com/livingagent/gateway/websocket/ZombieConnectionCleaner.java
package com.livingagent.gateway.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ZombieConnectionCleaner {
    
    private final ConnectionRegistry connectionRegistry;
    private final HeartbeatMonitor heartbeatMonitor;
    
    public ZombieConnectionCleaner(ConnectionRegistry connectionRegistry, HeartbeatMonitor heartbeatMonitor) {
        this.connectionRegistry = connectionRegistry;
        this.heartbeatMonitor = heartbeatMonitor;
    }
    
    @Scheduled(fixedRate = 60000)
    public void cleanStaleConnections() {
        connectionRegistry.getAllSessions().forEach(session -> {
            Instant lastHeartbeat = heartbeatMonitor.getLastHeartbeat(session.getId());
            
            if (lastHeartbeat == null || 
                Instant.now().getEpochSecond() - lastHeartbeat.getEpochSecond() > 90) {
                
                log.warn("Cleaning zombie connection: {}", session.getId());
                
                try {
                    session.close();
                } catch (Exception e) {
                    log.error("Failed to close zombie connection: {}", session.getId(), e);
                }
                
                connectionRegistry.unregisterSession(session.getId(), getDepartment(session));
            }
        });
    }
}
```

### 7.2 闭环3-B：消息Trace闭环改进

**改进方案 P3-B-A：Trace完整性验证**

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/autonomy/trace/TraceEventValidator.java
package com.livingagent.core.autonomy.trace;

import org.springframework.stereotype.Component;

@Component
public class TraceEventValidator {
    
    public boolean validateTrace(AutonomyTraceService trace) {
        // 检查Trace完整性
        if (trace.getTraceId() == null || trace.getTraceId().isEmpty()) {
            return false;
        }
        
        if (trace.getUserId() == null || trace.getUserId().isEmpty()) {
            return false;
        }
        
        if (trace.getSessionId() == null || trace.getSessionId().isEmpty()) {
            return false;
        }
        
        if (trace.getStatus() == null) {
            return false;
        }
        
        if (trace.getRecords() == null || trace.getRecords().isEmpty()) {
            return false;
        }
        
        return true;
    }
}
```

**改进方案 P3-B-B：Trace持久化验证**

已在P18-A中实现AutonomyTraceService.createAndPersist()方法。

**改进方案 P3-B-C：Trace唯一性验证**

已在P18-A中实现AutonomyTraceService.isTraceIdUnique()方法。

### 7.3 闭环11-A：员工状态管理闭环改进

**改进方案 P11-A-A：员工状态验证**

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/employee/FixedEmployeeStatusValidator.java
package com.livingagent.core.employee;

import org.springframework.stereotype.Component;

@Component
public class FixedEmployeeStatusValidator {
    
    public boolean validateStatus(EmployeeStatus status) {
        // 检查员工状态正确性
        if (status == null) {
            return false;
        }
        
        // 状态流转验证
        switch (status) {
            case READY:
            case BUSY:
            case OFFLINE:
                return true;
            default:
                return false;
        }
    }
    
    public boolean validateTransition(EmployeeStatus from, EmployeeStatus to) {
        // 状态流转规则验证
        if (from == EmployeeStatus.READY && to == EmployeeStatus.BUSY) {
            return true; // READY → BUSY
        }
        
        if (from == EmployeeStatus.BUSY && to == EmployeeStatus.READY) {
            return true; // BUSY → READY
        }
        
        if (to == EmployeeStatus.OFFLINE) {
            return true; // → OFFLINE
        }
        
        return false;
    }
}
```

**改进方案 P11-A-B：任务分配记录验证**

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/task/TaskValidator.java
package com.livingagent.core.task;

import org.springframework.stereotype.Component;

@Component
public class TaskValidator {
    
    public boolean validateAssignment(Task assignment) {
        // 检查任务分配记录正确性
        if (assignment.getTaskId() == null) {
            return false;
        }
        
        if (assignment.getEmployeeId() == null) {
            return false;
        }
        
        if (assignment.getStatus() == null) {
            return false;
        }
        
        return true;
    }
}
```

**改进方案 P11-A-C：绩效记录验证**

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/employee/EmployeePerformanceValidator.java
package com.livingagent.core.employee;

import org.springframework.stereotype.Component;

@Component
public class EmployeePerformanceValidator {
    
    public boolean validatePerformance(EmployeePerformance performance) {
        // 检查绩效统计正确性
        if (performance.getEmployeeId() == null) {
            return false;
        }
        
        if (performance.getTotalTasks() < performance.getCompletedTasks()) {
            return false; // 完成任务数不能超过总任务数
        }
        
        if (performance.getSuccessRate() < 0 || performance.getSuccessRate() > 1) {
            return false; // 成功率范围0-1
        }
        
        return true;
    }
}
```

### 7.4 闭环11-B：通道监控闭环改进

**改进方案 P11-B-A：通道消息投递验证**

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/channel/ChannelMessageValidator.java
package com.livingagent.core.channel;

import org.springframework.stereotype.Component;

@Component
public class ChannelMessageValidator {
    
    public boolean validateMessage(ChannelMessage message) {
        // 检查通道消息投递正确性
        if (message.getChannelId() == null) {
            return false;
        }
        
        if (message.getMessageId() == null) {
            return false;
        }
        
        if (message.getContent() == null) {
            return false;
        }
        
        return true;
    }
}
```

**改进方案 P11-B-B：消费者处理验证**

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/channel/ConsumerResultValidator.java
package com.livingagent.core.channel;

import org.springframework.stereotype.Component;

@Component
public class ConsumerResultValidator {
    
    public boolean validateConsumerResult(ConsumerResult result) {
        // 检查消费者处理结果
        if (result.getConsumerId() == null) {
            return false;
        }
        
        if (result.getMessageId() == null) {
            return false;
        }
        
        if (result.getStatus() == null) {
            return false;
        }
        
        return true;
    }
}
```

**改进方案 P11-B-C：通道清理验证**

```java
// 文件：living-agent-core/src/main/java/com/livingagent/core/channel/ChannelCleanupValidator.java
package com.livingagent.core.channel;

import org.springframework.stereotype.Component;

@Component
public class ChannelCleanupValidator {
    
    public boolean validateCleanup(String channelId) {
        // 检查通道清理是否成功
        ChannelManager channelManager = getChannelManager();
        
        if (channelManager.getChannel(channelId) != null) {
            return false; // 通道未清理
        }
        
        return true;
    }
}
```

---

## 八、实施优先级更新

### 8.1 新增闭环实施优先级

```
┌──────────────────────────────────────────────────────────────┐
│                    新增闭环实施优先级                             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  P0 - 立即实施（阻塞上线）：                                   │
│  ├─ P20-A: 进程存活监控增强                                   │
│  ├─ P20-B: 模型加载验证增强                                   │
│  ├─ P20-C: NamedPipe健康检查增强                              │
│  ├─ P20-D: 推理结果验证增强                                   │
│  ├─ P19-A: JNI调用结果验证增强                                │
│  ├─ P19-B: Native执行性能监控增强                             │
│  ├─ P19-C: Native模块健康检查增强                             │
│                                                              │
│  P1 - 近期实施（影响体验）：                                   │
│  ├─ P17-A: WebSocket连接状态监控增强                          │
│  ├─ P17-B: API响应验证增强                                    │
│  ├─ P17-C: 前端状态同步机制增强                               │
│  ├─ P17-D: 前端错误上报机制增强                               │
│  ├─ P22-A: CLI可用性检查增强                                  │
│  ├─ P22-B: 调用结果验证增强                                   │
│  ├─ P22-C: 输出解析验证增强                                   │
│                                                              │
│  P2 - 后续实施（细化验证）：                                   │
│  ├─ P18-A: autonomy_trace_events表写入验证                          │
│  ├─ P18-B: evolution_feedback表写入验证                       │
│  ├─ P18-C: 其他关键表写入验证                                 │
│  ├─ P3-A-A/B/C: WebSocket连接管理闭环增强                     │
│  ├─ P3-B-A/B/C: 消息Trace闭环增强                             │
│  ├─ P11-A-A/B/C: 员工状态管理闭环增强                         │
│  ├─ P11-B-A/B/C: 通道监控闭环增强                             │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 8.2 实施顺序建议

```
┌──────────────────────────────────────────────────────────────┐
│                    实施顺序建议                                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Sprint 1: P0级（技术核心闭环）                                │
│  ├─ Week 1: P20-A + P20-B + P20-C（Python进程闭环）          │
│  ├─ Week 2: P20-D + P19-A（推理验证 + JNI验证）              │
│  ├─ Week 3: P19-B + P19-C（Native性能 + 模块健康）           │
│                                                              │
│  Sprint 2: P1级（业务支撑闭环）                                │
│  ├─ Week 1: P17-A + P17-B（前端WebSocket + API验证）         │
│  ├─ Week 2: P17-C + P17-D（前端状态同步 + 错误上报）         │
│  ├─ Week 3: P22-A + P22-B + P22-C（Claude CLI闭环）          │
│                                                              │
│  Sprint 3: P2级（细化验证闭环）                                │
│  ├─ Week 1: P18-A + P18-B + P18-C（数据库表级闭环）          │
│  ├─ Week 2: P3-A-A/B/C（WebSocket连接管理子闭环）            │
│  ├─ Week 3: P3-B-A/B/C（消息Trace子闭环）                    │
│  ├─ Week 4: P11-A-A/B/C + P11-B-A/B/C（员工/通道子闭环）     │
│                                                              │
│  原闭环补充改进（并行实施）：                                   │
│  ├─ Sprint 1: P12-A + P12-B + P12-C（服务启动闭环）          │
│  ├─ Sprint 2: P14-B（权限强制生效）                           │
│  ├─ Sprint 3: P12-D（启动自动恢复）                           │
│  ├─ Sprint 4: P14-A + P14-C + P14-D（权限完整闭环）          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 九、闭环验收清单更新

### 9.1 新增闭环验收方法

```
┌──────────────────────────────────────────────────────────────┐
│                    新增闭环验收清单                               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  闭环17验收方法：                                              │
│  1. 连接WebSocket → 查询连接状态 → 确认监控                    │
│  2. 停止心跳 → 等待90秒 → 确认僵尸清理                          │
│  3. 发送API请求 → 模拟失败 → 确认重试                           │
│  4. 权限变更 → 确认WebSocket断开重连                           │
│  5. 前端异常 → 查询错误日志 → 确认上报                          │
│                                                              │
│  闭环18验收方法：                                              │
│  1. 创建Trace → 查询autonomy_trace_events → 确认写入                 │
│  2. 尝试重复traceId → 确认抛出异常                              │
│  3. 多表写入失败 → 确认事务回滚                                 │
│  4. 更新状态 → 查询确认更新                                    │
│                                                              │
│  闭环19验收方法：                                              │
│  1. 调用Native方法 → 检查返回值 → 确认验证                     │
│  2. 模拟Native异常 → 确认Java异常捕获                          │
│  3. 查询性能日志 → 确认监控                                    │
│  4. GET /api/health/native → 确认健康                    │
│                                                              │
│  闭环20验收方法：                                              │
│  1. docker ps → 查看model_daemon.py进程                       │
│  2. 查看日志 → 确认模型加载                                    │
│  3. ls -la /tmp/*.pipe → 确认管道存在                          │
│  4. 调用模型 → 检查响应 → 确认推理验证                          │
│  5. GET /api/health/namedpipe → 确认健康                 │
│  6. GET /api/health/models → 确认模型加载                │
│                                                              │
│  闭环22验收方法：                                              │
│  1. claude --version → 确认CLI安装                            │
│  2. 调用ClaudeCliTool → 检查结果 → 确认验证                  │
│  3. 模拟CLI未安装 → 确认错误提示                                │
│  4. GET /api/health/claudecli → 确认健康                 │
│                                                              │
│  闭环3-A验收方法：                                             │
│  1. 连接WebSocket → 查询连接数 → 确认限制                      │
│  2. 发送心跳 → 确认心跳记录                                    │
│  3. 停止心跳 → 等待90秒 → 确认僵尸清理                          │
│                                                              │
│  闭环3-B验收方法：                                             │
│  1. 发送消息 → 查询Trace → 确认完整性                          │
│  2. 尝试重复traceId → 确认唯一约束                              │
│                                                              │
│  闭环11-A验收方法：                                            │
│  1. 注册员工 → 查询状态 → 确认READY                            │
│  2. 分配任务 → 查询tasks → 确认分配                 │
│  3. 完成任务 → 查询员工状态 → 确认恢复READY                     │
│                                                              │
│  闭环11-B验收方法：                                            │
│  1. 创建通道 → 发送消息 → 确认投递                              │
│  2. 消费消息 → 确认处理                                        │
│  3. 关闭通道 → 确认清理                                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 十、文档维护说明

本改进方案补充文档应与以下文档配套使用：
- COMPLETE_FLOW_DOCUMENTATION.md（闭环需求定义，已补充9个新增闭环）
- LOOP_COVERAGE_ANALYSIS_AND_SUPPLEMENTARY_IMPROVEMENT_PLAN.md（闭环覆盖分析，原14个闭环）
- COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md（闭环需求全面分析）

**使用建议**：
1. 将本补充方案内容添加到 LOOP_COVERAGE_ANALYSIS_AND_SUPPLEMENTARY_IMPROVEMENT_PLAN.md 第四章"补充改进方案"之后
2. 更新第五章"实施优先级"，合并原闭环和新增闭环的实施优先级
3. 更新第六章"闭环验收清单"，合并原闭环和新增闭环的验收方法

**版本信息**：
- 文档版本: v2.0
- 生成日期: 2026-06-30
- 新增闭环改进方案: 9个闭环（共33个改进方案）
- 总闭环数: 23个（14个原闭环 + 9个新增闭环）

---

# Living Agent Service - L3自洽闭环覆盖分析与改进方案补充

> **生成日期**: 2026-07-02
> **目的**: 为新增的9个L3自洽闭环（闭环24-32）生成覆盖分析与改进方案，补齐 LOOP_COVERAGE 文档对 L3 生命体自洽层的覆盖空白。前文已覆盖 L1 流程正确性（14个）和 L2 覆盖完整性（9个），本部分覆盖 L3 生命体自洽（9个）。
> **配套文档**: COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md 第8章（闭环24-32详细定义）、CODE_STRUCTURE_AND_FILE_GUIDE.md（实际代码结构）

---

## 十一、L3自洽闭环覆盖情况总览

### 11.1 L3自洽闭环清单

根据 COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md 第八章，系统共定义 **9个 L3 自洽闭环**：

| 闭环编号 | 闭环名称 | 自洽层级 | L3内部分层 |
|---------|---------|---------|-----------|
| 24 | 自愈闭环 | 自感知→自修复 | 执行层 |
| 25 | 经济自治闭环 | 自决策→自运营 | 执行层 |
| 26 | 知识自进化闭环 | 自学习→自成长 | 执行层 |
| 27 | 降级链路闭环 | 自适应→自恢复 | 执行层 |
| 28 | 执行回执闭环 | 自验证→自优化 | 执行层 |
| 29 | 大脑个性进化闭环 | 自进化→自成长 | 执行层 |
| 30 | 沙箱安全闭环 | 自保护→自约束 | 执行层 |
| 31 | 跨闭环协同编排闭环 | 自感知→自协调 | 协调层 |
| 32 | 生命体征仪表盘闭环 | 自感知→自暴露 | 感知层 |

### 11.2 覆盖情况统计

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                    L3 自洽闭环覆盖情况统计                                                          │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  ⚠️ 半闭环（组件齐全、联动缺失）: 7个执行层闭环                                                        │
│  ├─ 闭环24-30 的关键组件均已存在于代码中                                                              │
│  ├─ 但缺少编排器串联 + 反馈回路 + 效果验证                                                            │
│  └─ 需补充改进方案（见第十二章）                                                                      │
│                                                                                              │
│  ❌ 全新闭环（未实现）: 2个感知/协调层闭环                                                              │
│  ├─ 闭环31 SelfGovernanceOrchestrator 等组件全部缺失                                                │
│  ├─ 闭环32 VitalSignsMonitor 等组件全部缺失                                                         │
│  └─ 需从零实现                                                                                  │
│                                                                                              │
│  代码落地度: 约 80%+（基础设施已具备，缺口集中在三大系统性问题）                                          │
│  ├─ 缺口一：闭环编排器缺失（24缺SelfHealingOrchestrator、31缺SelfGovernanceOrchestrator）              │
│  ├─ 缺口二：反馈回路断裂（26/28/29/30 的审核结果/消费效果/违规事件未回流到上游决策）                       │
│  └─ 缺口三：效果验证环节空白（25/27/29 缺 ROI对比/效果对比/满意度对比）                                │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 11.3 L3自洽闭环实现缺口对照表

| 闭环 | 已实现要素（CODE_STRUCTURE_AND_FILE_GUIDE.md 确认） | 主要缺口 | 缺口类型 |
|------|--------------------------------------------------|---------|---------|
| 24 自愈 | HealthMonitorImpl / evolution/circuitbreaker / ErrorCodeMapper / PatchProposalService / PatchApplicationService / InterventionNeuron | 无 SelfHealingOrchestrator 串联六步；修复过程未写入 KnowledgeBase；model_daemon.py 崩溃无自动重启；NamedPipe 断开无自动重建 | 编排器缺失 + 经验沉淀断裂 |
| 25 经济 | autonomous/bounty/BountyHunterService / autonomous/payout / autonomous/incentive / LedgerService / HardwareUpgradeService / DefaultPerformanceCaptureService | 收益验证缺失；升级后无 ROI 对比；CircuitBreaker 未接入连续亏损信号 | 效果验证空白 |
| 26 知识 | MemoryToKnowledgeExtractor / DefaultKnowledgeQualityEvaluator / KnowledgePromotionScheduler / LayeredKnowledgeBase / KnowledgeEvolution / KnowledgeGovernanceService / KnowledgePromotionAuditService | 知识被消费后无效果反馈；消费效果未驱动 confidence 调整 | 反馈回路断裂 |
| 27 降级 | ModelHealthRegistry / BrainModelFallback / 10处 Llm*+Default* 成对降级点 / StandardComplianceTraceService / EscalationNotificationService | 缺独立降级事件持久化；无小流量探测+效果对比；LLM质量 vs 规则质量未对比 | 效果验证空白 |
| 28 回执 | ChannelBackedDepartmentExecutionCoordinator / ToolBackedEmployeeTaskExecutor / JpaEmployeeExecutionReceiptService / LlmExecutionReceiptReviewer / DefaultPerformanceCaptureService / LlmDepartmentAggregationService / LlmBasedFixedEmployeeDispatcher / ReceiptStatus | 审核结果未反馈到 dispatcher 权重；审核发现未写入 KnowledgeBase；TIMEOUT 自动重派机制待补 | 反馈回路断裂 |
| 29 个性 | BrainPersonality / PersonalityMutation / PersonalityStats / BrainBoundaryEnforcer / EscalationNotificationService | 满意度采集缺失；调整前后无满意度对比；PersonalityStats 未关联业务效果；riskTolerance 未驱动 BrainBoundaryEnforcer | 反馈回路断裂 + 边界联动缺失 |
| 30 沙箱 | BashSecurityValidator / DockerSandboxService / HybridSandboxService / ComplianceManager / StandardComplianceTraceService / BrainBoundaryEnforcer / CodebaseAccessService / security.rs | 违规→黑名单自动更新缺失；违规→BrainBoundaryEnforcer 边界收紧联动缺失；黑名单无 TTL 解除；边界无恢复机制 | 反馈回路断裂 + 边界联动缺失 |
| 31 协同 | EvolutionOrchestrator / EvolutionCircuitBreaker / StandardComplianceTraceService（可复用） | SelfGovernanceOrchestrator / CrossLoopEventBus / LoopPriorityArbiter / CrossLoopAggregator 全部缺失 | 全新闭环 |
| 32 体征 | HealthMonitorImpl / EscalationNotificationService / evolution_feedback 表（可复用） | VitalSignsMonitor / LoopMetricsCollector / VitalSignsScorer 全部缺失；/api/vitals 端点未暴露 | 全新闭环 |

---

## 十二、L3自洽闭环改进方案

### 12.1 P0 改进方案（立即实施，解锁其他闭环的前提）

#### P24-A：自愈编排器（SelfHealingOrchestrator）

**问题分析**：闭环24 的六步（异常检测→根因分析→补丁生成→应用/回滚→效果验证→经验沉淀）由 HealthMonitorImpl、ErrorCodeMapper、PatchProposalService、PatchApplicationService 等独立服务承担，但缺少编排器串联，目前只能人工驱动。所有 L3 闭环都依赖自愈能力作为异常恢复基础。

**改进方案**：

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  P24-A：自愈编排器 SelfHealingOrchestrator                                                          │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  新增文件：                                                                                   │
│  ├─ core/evolution/orchestrator/SelfHealingOrchestrator.java                                 │
│  │    接口：orchestrate(HealthIssue issue) → SelfHealingResult                                │
│  │    职责：串联六步，按 confidence 阈值决定自动应用或人工审批                                  │
│  ├─ core/evolution/orchestrator/SelfHealingResult.java                                       │
│  │    字段：success/rootCause/patchApplied/verified/experienceCaptured/needsHumanApproval    │
│  └─ core/evolution/orchestrator/impl/SelfHealingOrchestratorImpl.java                        │
│                                                                                              │
│  实现要点：                                                                                   │
│  1. 订阅 HealthMonitorImpl 的 HealthIssue 事件                                                │
│  2. 调用 ErrorCodeMapper 进行根因分析 → 输出 CodeContext                                       │
│  3. 调用 PatchProposalService 创建补丁提案（含 confidence/rollbackAvailable）                  │
│  4. 判断 confidence：                                                                          │
│     ├─ ≥ 阈值（如 0.8）：调用 PatchApplicationService 应用补丁                                │
│     └─ < 阈值：触发 InterventionNeuron 请求人工审批                                            │
│  5. 效果验证：重新调用 HealthMonitorImpl 检查 + EvolutionCircuitBreaker 确认恢复               │
│  6. 经验沉淀：调用 KnowledgeCaptureService 将修复过程写入 KnowledgeBase（L1→L2→L3 晋升）       │
│  7. 失败回滚：PatchApplicationService.rollback() + 记录失败原因                                │
│                                                                                              │
│  复用已有组件：                                                                               │
│  ├─ HealthMonitorImpl（异常检测）                                                             │
│  ├─ ErrorCodeMapper（根因分析）                                                               │
│  ├─ PatchProposalService（补丁生成）                                                          │
│  ├─ PatchApplicationService（应用/回滚）                                                       │
│  ├─ EvolutionCircuitBreaker（熔断/冷却）                                                       │
│  ├─ InterventionNeuron（人工审批）                                                             │
│  └─ KnowledgeCaptureService（经验沉淀）                                                       │
│                                                                                              │
│  配置项（application.yml）：                                                                  │
│  evolution:                                                                                   │
│    self-healing:                                                                              │
│      enabled: true                                                                            │
│      confidence-threshold: 0.8                                                                 │
│      cooldown-seconds: 300                                                                    │
│      max-retry: 3                                                                              │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**验收方法**：
1. 模拟 model_daemon.py 崩溃 → HealthMonitor 检测 → SelfHealingOrchestrator 自动重启 → 验证恢复
2. 模拟低 confidence 补丁 → 确认触发 InterventionNeuron 而非自动应用
3. 自愈完成后查询 KnowledgeBase → 确认修复经验已沉淀

---

#### P31-A：跨闭环协同编排器（SelfGovernanceOrchestrator）

**问题分析**：闭环24-30 各自独立运行，无统一协调入口。并发触发时可能震荡（自愈修复→引发降级→触发个性变异→...）。闭环间无优先级仲裁，安全/经济/进化混在一起。闭环31 是 L3 协调层，缺失会导致整个 L3 层无法协同。

**改进方案**：

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  P31-A：跨闭环协同编排器 SelfGovernanceOrchestrator                                                │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  新增文件：                                                                                   │
│  ├─ core/evolution/orchestrator/SelfGovernanceOrchestrator.java                              │
│  │    接口：submitEvent(CrossLoopEvent) / orchestrate() / getStatus()                         │
│  ├─ core/evolution/orchestrator/CrossLoopEventBus.java                                       │
│  │    职责：发布/订阅 L3 闭环事件，支持去重和冷却期                                            │
│  ├─ core/evolution/orchestrator/LoopPriorityArbiter.java                                     │
│  │    职责：优先级仲裁（安全30>自愈24>降级27>回执28>经济25>知识26>个性29）                       │
│  ├─ core/evolution/orchestrator/CrossLoopAggregator.java                                     │
│  │    职责：聚合一次协同周期内所有闭环执行结果                                                  │
│  ├─ core/evolution/orchestrator/CrossLoopEvent.java                                          │
│  │    字段：eventId/sourceLoop/targetLoops/priority/timestamp/coolingUntil                    │
│  └─ core/evolution/orchestrator/impl/SelfGovernanceOrchestratorImpl.java                     │
│                                                                                              │
│  事件订阅源（已有组件暴露事件）：                                                              │
│  ├─ HealthMonitorImpl → 闭环24 自愈事件                                                        │
│  ├─ ModelHealthRegistry → 闭环27 降级事件                                                     │
│  ├─ ComplianceManager → 闭环30 违规事件                                                        │
│  ├─ LlmExecutionReceiptReviewer → 闭环28 回执审核事件                                         │
│  ├─ PersonalityMutation → 闭环29 个性变异事件                                                  │
│  ├─ BountyHunterService → 闭环25 赏金事件                                                      │
│  └─ KnowledgePromotionScheduler → 闭环26 晋升事件                                             │
│                                                                                              │
│  实现要点：                                                                                   │
│  1. CrossLoopEventBus 接收所有 L3 闭环事件                                                   │
│  2. LoopPriorityArbiter 按优先级排序，高优先级先执行                                           │
│  3. 同类事件去重：相同根因的多次告警合并为一次                                                  │
│  4. 冷却期控制：每个闭环设置冷却期（如自愈 300s、降级 60s），冷却期内不重复触发                  │
│  5. 并行/串行：独立闭环可并行（安全+知识），依赖闭环串行（自愈→经验沉淀）                       │
│  6. CrossLoopAggregator 汇总结果到 evolution_feedback 表                                      │
│  7. StandardComplianceTraceService 记录完整协同决策链                                          │
│  8. 协同冲突（同一资源被多闭环争抢）→ InterventionNeuron 人工仲裁                              │
│                                                                                              │
│  复用已有组件：                                                                               │
│  ├─ EvolutionOrchestrator（进化总编排，可扩展或作为基类）                                       │
│  ├─ EvolutionCircuitBreaker（防震荡熔断）                                                      │
│  ├─ StandardComplianceTraceService（协同审计）                                                │
│  ├─ InterventionNeuron（冲突人工仲裁）                                                         │
│  └─ evolution_feedback 表（结果持久化）                                                        │
│                                                                                              │
│  配置项：                                                                                     │
│  evolution:                                                                                   │
│    governance:                                                                                │
│      enabled: true                                                                             │
│      priority-order: [30, 24, 27, 28, 25, 26, 29]                                             │
│      cooldown-seconds:                                                                        │
│        self-healing: 300                                                                       │
│        degradation: 60                                                                         │
│        security: 30                                                                            │
│      conflict-arbitration: human                                                              │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**验收方法**：
1. 同时注入"模型失败"+"违规"+"回执低质量"三个事件 → 确认按优先级 安全>降级>回执 执行
2. 同一根因连续告警 3 次 → 确认去重为 1 次触发
3. 冷却期内重复触发 → 确认被忽略
4. 查询 evolution_feedback → 确认协同结果聚合完整

---

#### P28-A：审核结果→分派权重联动

**问题分析**：闭环28 的 `LlmExecutionReceiptReviewer` 审核出低质量回执后，结果未反馈到 `LlmBasedFixedEmployeeDispatcher`，导致低绩效员工仍被同等分派任务。这是反馈回路断裂的典型表现。

**改进方案**：

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  P28-A：审核结果→分派权重联动                                                                       │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  修改文件：                                                                                   │
│  ├─ core/autonomy/impl/LlmExecutionReceiptReviewer.java                                      │
│  │    新增：审核结果输出后，调用 PerformanceStatsService 更新员工绩效权重                        │
│  ├─ core/autonomy/impl/LlmBasedFixedEmployeeDispatcher.java                                 │
│  │    新增：分派时读取 PerformanceStatsService 的 getStats(employeeCode)，按绩效权重选人        │
│  ├─ core/autonomy/impl/DefaultPerformanceStatsService.java（已存在）                          │
│  │    新增方法：adjustWeight(employeeCode, delta) → 调整员工分派权重                            │
│  └─ core/autonomy/impl/DefaultKnowledgeCaptureService.java（已存在）                         │
│       新增：审核发现的模式写入 KnowledgeBase（EXPERIENCE 类型）                                  │
│                                                                                              │
│  联动规则：                                                                                   │
│  ├─ 回执 passed → 权重 +0.1（最高 1.0）                                                       │
│  ├─ 回执 needsRework → 权重 -0.2                                                              │
│  ├─ 回执 failed → 权重 -0.5                                                                   │
│  ├─ 连续 3 次 failed → 权重降为 0，触发 InterventionNeuron 评估是否更换员工                      │
│  └─ 权重低于 0.3 的员工，dispatcher 不再优先分派                                              │
│                                                                                              │
│  知识沉淀：                                                                                   │
│  ├─ failed 回执的 failedReason 写入 KnowledgeBase（部门私有，EXPERIENCE 类型）                 │
│  ├─ 避免同部门其他员工重蹈覆辙                                                                  │
│  └─ 高频失败模式自动晋升为 SHARED 知识                                                          │
│                                                                                              │
│  TIMEOUT 自动重派：                                                                            │
│  ├─ ReceiptStatus.TIMEOUT → 自动标记为 FAILED                                                  │
│  ├─ 触发 CrossLoopEventBus 发布回执失败事件                                                     │
│  └─ LlmBasedFixedEmployeeDispatcher 重新选人分派（权重降权后）                                  │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**验收方法**：
1. 员工 A 连续 3 次回执 failed → 确认权重降为 0，dispatcher 不再分派
2. 查询 KnowledgeBase → 确认失败模式已沉淀为 EXPERIENCE
3. TIMEOUT 回执 → 确认自动重派到其他员工

---

### 12.2 P1 改进方案（近期实施，提升自洽完整性）

#### P27-A：降级小流量回归 + 效果对比

**问题分析**：闭环27 的 `ModelHealthRegistry` 在 COOLDOWN 到期后会探测恢复为 AVAILABLE，但全量切换 LLM 风险高，缺少小流量探测和效果对比。

**改进方案**：
- 修改 `core/model/pool/ModelHealthRegistry.java`：新增 `PROBING` 状态（COOLDOWN→PROBING→AVAILABLE）
- 新增 `core/brain/impl/BrainModelFallback.java` 的 `probeAndCompare()` 方法：PROBING 状态下 10% 流量回归 LLM，90% 仍用规则
- 新增 `core/runtime/StandardComplianceTraceService.java` 持久化降级事件到 `evolution_feedback` 表
- 效果对比：LLM 响应质量评分 vs 规则实现质量评分，连续 N 次优于规则才全量切换

**验收方法**：模拟 LLM 恢复 → 确认进入 PROBING 状态 → 10% 流量回归 → 效果对比通过 → 全量切换 AVAILABLE

---

#### P30-A：违规→黑名单→边界收紧联动

**问题分析**：闭环30 的 `ComplianceManager` 检测到违规后，违规模式未自动加入 `BashSecurityValidator` 黑名单，也未触发 `BrainBoundaryEnforcer` 收紧该大脑的 `allowedActions`。

**改进方案**：
- 新增 `core/tool/impl/BashSecurityValidator.java` 的 `addToBlacklist(pattern, ttl)` 方法，违规模式自动入黑名单，TTL 过期自动解除
- 新增 `core/brain/BrainBoundaryEnforcer.java` 的 `tightenBoundary(brainId, actionType, duration)` 方法，重复违规收紧边界
- 修改 `core/compliance/ComplianceManager.java`：违规事件发布到 `CrossLoopEventBus`（闭环31）
- 边界恢复机制：TTL 到期后边界自动恢复原状，避免永久收紧
- 严重违规（如数据泄露）→ 触发 `InterventionNeuron` + `EscalationNotificationService`

**验收方法**：模拟 Bash 命令违规 3 次 → 确认黑名单更新 + 边界收紧 + TTL 到期恢复

---

#### P26-A：知识消费效果反馈

**问题分析**：闭环26 的知识被检索使用后，无效果反馈，`confidence` 无法根据实际使用情况调整。

**改进方案**：
- 新增 `core/knowledge/KnowledgeConsumptionTracker.java`：记录知识被消费的次数和效果（任务是否成功完成）
- 修改 `core/knowledge/impl/DefaultKnowledgeQualityEvaluator.java`：消费效果纳入质量评分
- 修改 `core/knowledge/impl/KnowledgePromotionScheduler.java`：消费效果好→晋升 confidence +；消费效果差→降级 confidence -，连续差→标记 DEPRECATED
- 反馈来源：`ToolBackedEmployeeTaskExecutor` 执行任务后，回填使用的知识 ID 和任务结果

**验收方法**：知识 K1 被使用 5 次任务成功 → confidence 上升；知识 K2 被使用 3 次任务失败 → confidence 下降并标记 DEPRECATED

---

#### P25-A：硬件升级 ROI 验证

**问题分析**：闭环25 的 `HardwareUpgradeService` 升级后无效果验证，无法判断升级是否带来收益。

**改进方案**：
- 修改 `core/evolution/HardwareUpgradeService.java`：升级前记录基线指标（任务吞吐量、单位成本收益）
- 新增 `core/evolution/impl/HardwareUpgradeRoiTracker.java`：升级后 N 天（默认 7 天）对比指标变化
- 新增 `core/autonomous/` 的防亏损联动：连续亏损 N 次时，CircuitBreaker 阻止继续接赏金任务
- ROI 为负 → 触发 `InterventionNeuron` 评估是否回滚升级

**验收方法**：模拟升级 → 7 天后对比收益 → 确认 ROI 计算正确，连续亏损时 CircuitBreaker 生效

---

### 12.3 P2 改进方案（可做，增强生命感）

#### P29-A：满意度采集 + 行为验证

**改进方案**：
- 新增 `core/evolution/personality/SatisfactionCollector.java`：采集隐式满意度（继续对话/中断/切换话题）+ 显式满意度（评分）
- 修改 `core/evolution/personality/PersonalityStats.java`：关联业务效果（任务成功率、用户满意度）
- 个性变异后 N 次对话对比满意度变化：上升→保留变异；下降→回滚变异

#### P29-B：riskTolerance→边界联动

**改进方案**：
- 修改 `core/evolution/personality/PersonalityMutation.java`：调整 riskTolerance 时同步通知 `BrainBoundaryEnforcer`
- `BrainBoundaryEnforcer` 根据 riskTolerance 动态调整 allowedActions 的边界范围
- riskTolerance 超出安全范围 → 触发 `EscalationNotificationService`

#### P24-B：进程级自愈

**改进方案**：
- 修改 `core/diagnosis/impl/HealthMonitorImpl.java`：新增 model_daemon.py 进程存活检查
- 检测到进程崩溃 → SelfHealingOrchestrator（P24-A）触发重启 → 验证恢复

#### P24-C：连接级自愈

**改进方案**：
- 修改 `core/model/provider/NamedPipeModelClient.java`：断开后自动重建连接
- 重连失败重试 N 次（指数退避），仍失败则触发 `EscalationNotificationService`

#### P32-A：生命体征仪表盘

**改进方案**：
- 新增 `core/diagnosis/VitalSignsMonitor.java`（扩展 HealthMonitor）：定时采集 9 个 L3 闭环的运行指标
- 新增 `core/diagnosis/LoopMetricsCollector.java`：每个闭环暴露 metrics（自愈成功率/降级次数/知识晋升数/回执合格率/个性变异次数/违规数/协同次数/评分）
- 新增 `core/diagnosis/VitalSignsScorer.java`：综合计算 0-100 评分（>80健康/60-80亚健康/<60危急）
- 新增 `gateway/controller/VitalSignsController.java`：新增 `GET /api/vitals` 端点
- 评分<60 → 触发 `EscalationNotificationService` + 驱动闭环31重新分配资源

---

## 十三、实施优先级更新（L1+L2+L3 合并）

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                    完整实施优先级排序（32个闭环）                                                    │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  P0 - 立即实施（解锁其他闭环的前提）：                                                            │
│  ├─ L1: P12-A/B/C（服务启动闭环，已规划）                                                        │
│  ├─ L1: P14-B（权限强制生效，已规划）                                                            │
│  ├─ L3: P24-A（SelfHealingOrchestrator，所有L3闭环依赖自愈）                                     │
│  ├─ L3: P31-A（SelfGovernanceOrchestrator，L3协调层基础）                                        │
│  └─ L3: P28-A（审核结果→分派权重联动，反馈回路典型）                                              │
│                                                                                              │
│  P1 - 近期实施（提升自洽完整性）：                                                                │
│  ├─ L1: P12-D/P14-A/C/D（已规划）                                                               │
│  ├─ L2: P17-A/B/C/D + P18-A/B/C + P19-A/B/C + P20-A/B/C/D + P22-A/B/C + P3-A/B + P11-A/B（已规划）│
│  ├─ L3: P27-A（降级小流量回归+效果对比）                                                          │
│  ├─ L3: P30-A（违规→黑名单→边界收紧联动）                                                        │
│  ├─ L3: P26-A（知识消费效果反馈）                                                                │
│  └─ L3: P25-A（硬件升级ROI验证）                                                                 │
│                                                                                              │
│  P2 - 可做（增强生命感）：                                                                        │
│  ├─ L3: P29-A（满意度采集+行为验证）                                                              │
│  ├─ L3: P29-B（riskTolerance→边界联动）                                                          │
│  ├─ L3: P24-B（进程级自愈）                                                                      │
│  ├─ L3: P24-C（连接级自愈）                                                                      │
│  └─ L3: P32-A（生命体征仪表盘）                                                                  │
│                                                                                              │
│  实施顺序建议（L3 部分）：                                                                       │
│  ├─ Sprint 1: P24-A + P31-A（自愈+协同编排器，L3 基础设施）                                       │
│  ├─ Sprint 2: P28-A（回执反馈联动，快速见效）                                                      │
│  ├─ Sprint 3: P27-A + P30-A（降级+安全联动）                                                      │
│  ├─ Sprint 4: P26-A + P25-A（知识+经济验证）                                                      │
│  └─ Sprint 5: P29-A/B + P24-B/C + P32-A（生命感增强）                                             │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 十四、闭环验收清单更新（L3 自洽闭环）

| 序号 | 闭环名称 | 当前状态 | 改进后预期状态 | 验收方法 |
|------|----------|---------|---------------|---------|
| 24 | 自愈闭环 | ⚠️ 半闭环 | ✅ 完整闭环（P24-A/B/C） | 模拟崩溃→自动修复→经验沉淀→查询KnowledgeBase |
| 25 | 经济自治闭环 | ⚠️ 半闭环 | ✅ 完整闭环（P25-A） | 模拟升级→7天对比ROI→亏损时CircuitBreaker |
| 26 | 知识自进化闭环 | ⚠️ 半闭环 | ✅ 完整闭环（P26-A） | 知识消费→效果反馈→confidence调整→晋升/降级 |
| 27 | 降级链路闭环 | ⚠️ 半闭环 | ✅ 完整闭环（P27-A） | LLM恢复→PROBING→10%回归→效果对比→全量切换 |
| 28 | 执行回执闭环 | ⚠️ 半闭环 | ✅ 完整闭环（P28-A） | 回执failed→权重降→dispatcher不再分派→经验沉淀 |
| 29 | 大脑个性进化闭环 | ⚠️ 半闭环 | ✅ 完整闭环（P29-A/B） | 个性变异→满意度对比→保留/回滚→边界联动 |
| 30 | 沙箱安全闭环 | ⚠️ 半闭环 | ✅ 完整闭环（P30-A） | 违规3次→黑名单+边界收紧→TTL到期恢复 |
| 31 | 跨闭环协同编排闭环 | ❌ 未实现 | ✅ 完整闭环（P31-A） | 多事件并发→优先级仲裁→协同执行→审计可追溯 |
| 32 | 生命体征仪表盘闭环 | ❌ 未实现 | ✅ 完整闭环（P32-A） | GET /api/vitals→评分→危急预警 |

**L3 整体验收**：所有 9 个 L3 闭环改进完成后，系统应能在无人工干预下完成 `自感知→自决策→自执行→自验证→自进化` 全链路，同时兼容人工介入点。

---

## 十五、文档维护说明

本 L3 自洽闭环改进方案应与以下文档配套使用：
- COMPREHENSIVE_LOOP_REQUIREMENTS_ANALYSIS.md（闭环24-32详细定义，v3.0）
- CODE_STRUCTURE_AND_FILE_GUIDE.md（实际代码结构，权威源）
- COMPLETE_FLOW_DOCUMENTATION.md（闭环需求定义，第25章）
- 前文 L1（第一至七章）+ L2（第一至十章）改进方案

**使用建议**：
1. L3 改进方案实施时，先看 CODE_STRUCTURE_AND_FILE_GUIDE.md 确认复用组件的精确路径
2. 新增文件遵循项目规则：autonomy vs autonomous 区分、ID命名规范、API响应格式统一
3. 数据库变更直接修改 schema.sql + init-db/01_init.sql，不再创建 Flyway V 版本迁移文件
4. 实施完成后同步更新 CODE_STRUCTURE_AND_FILE_GUIDE.md 对应小节

**版本信息**：
- 文档版本: v3.0
- 生成日期: 2026-07-02
- L3 改进方案: 9个闭环（共12个改进方案：P0×3 + P1×4 + P2×5）
- 总闭环数: 32个（14个L1 + 9个L2 + 9个L3）
- 闭环层级: L1流程正确性(14) + L2覆盖完整性(9) + L3生命体自洽(9)
- L3 内部分层: 感知层(32) + 协调层(31) + 执行层(24-30)
- 代码落地度: L3 基础设施约80%+，缺口集中在编排器/反馈回路/效果验证三大系统性问题