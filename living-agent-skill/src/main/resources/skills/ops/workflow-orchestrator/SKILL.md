---
name: workflow-orchestrator
description: "工作流编排，多阶段业务流程自动化"
risk: medium
source: internal
date_added: '2026-07-09'
---

# Workflow Orchestrator

> 工作流编排技能，实现复杂业务流程的自动化编排与监控（闭环43）

## 一、技能描述

工作流编排技能使运营部数字员工能够：
- 定义和执行多阶段业务流程
- 监控流程进度和卡点检测
- 处理超时和回退逻辑
- 优化流程效率

## 二、适用编制

| 编制代码 | 职位 | 主要用途 |
|---------|------|---------|
| O01 | 数据分析师 | 分析流程瓶颈 |
| O02 | 运营专员 | 执行运营流程 |
| O04 | 流程管理员 | 设计和监控流程 |

## 三、触发词

- 工作流、流程编排、workflow
- 流程监控、流程优化
- 阶段推进、卡点检测

## 四、核心功能

### 4.1 流程定义

```yaml
workflow:
  name: "审批流程"
  stages:
    - id: "submit"
      name: "提交申请"
      timeout: "1h"
      actions: ["validate", "notify"]
    - id: "review"
      name: "审核"
      timeout: "24h"
      actions: ["approve", "reject"]
      rollback: "submit"
    - id: "execute"
      name: "执行"
      timeout: "4h"
      actions: ["process", "complete"]
```

### 4.2 阶段监控

| 监控项 | 指标 | 阈值 |
|-------|------|------|
| 进度 | 完成率 | < 50% 预警 |
| 超时 | 等待时间 | > timeout |
| 卡点 | 阻塞时长 | > 2h |
| 失败 | 错误率 | > 5% |

### 4.3 回退策略

| 场景 | 策略 |
|------|------|
| 审核拒绝 | 回退到提交阶段 |
| 执行失败 | 回退到审核阶段 |
| 超时 | 自动提醒+手动干预 |
| 系统异常 | 保存状态+恢复执行 |

## 五、使用示例

### 5.1 创建工作流

```
用户: 创建一个审批工作流

技能执行:
1. 解析流程需求
2. 定义阶段和超时
3. 配置回退策略
4. 创建监控规则
5. 返回workflowId
```

### 5.2 监控流程

```
用户: 监控审批流程进度

技能执行:
1. 查询workflow实例
2. 计算各阶段进度
3. 检测卡点和超时
4. 生成进度报告
5. 触发预警通知
```

### 5.3 处理异常

```
用户: 审批流程卡住了

技能执行:
1. 定位卡点阶段
2. 分析阻塞原因
3. 推荐处理方案
4. 执行回退或推进
5. 记录处理结果
```

## 六、代码对接

### 6.1 WorkflowStageMonitor（P43-A）

```java
// 监控流程阶段进度
public class WorkflowStageMonitor {
    public WorkflowStatus checkStatus(String workflowId) {
        // 查询当前阶段
        // 检测超时和卡点
        // 返回状态报告
    }
}
```

### 6.2 WorkflowOptimizationService（P43-B）

```java
// 优化流程效率
public class WorkflowOptimizationService {
    public OptimizationReport analyze(String workflowId) {
        // 分析瓶颈阶段
        // 计算优化建议
        // 返回优化报告
    }
}
```

## 七、配置要求

```yaml
workflow:
  default_timeout: "24h"
  max_stages: 20
  checkpoint_interval: "5m"
  retry_count: 3
  retry_delay: "1m"
```

## 八、相关闭环

- **闭环43**: 工作流编排闭环 - 启动→阶段推进→卡点检测→超时→回退→完成
- **依赖**: `inngest` 工作流引擎, `cron` 定时任务

---

*来源: L4业务闭环43补充*
*更新时间: 2026-07-09*