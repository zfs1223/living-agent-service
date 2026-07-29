---
name: tenant-management
description: Tenant Management
risk: low
source: internal
date_added: '2026-07-13'
personalSafe: false
---

# Tenant Management

> 租户管理技能，实现多租户系统的生命周期管理（闭环50）

## 一、技能描述

租户管理技能使系统管理员能够：
- 租户注册和配置管理
- 租户邀请和成员管理
- 租户健康监控
- 租户数据隔离验证

## 二、适用编制

| 编制代码 | 职位 | 主要用途 |
|---------|------|---------|
| A01 | 行政助理 | 租户配置管理 |
| T04 | DevOps工程师 | 租户部署监控 |

## 三、触发词

- 租户、tenant、多租户
- 租户管理、租户配置
- 租户监控、租户健康

## 四、核心功能

### 4.1 租户生命周期

```
注册 → 配置 → 邀请 → 激活 → 使用 → 暂停 → 恢复 → 归档
```

### 4.2 租户配置

| 配置项 | 描述 | 默认值 |
|-------|------|--------|
| name | 租户名称 | - |
| domain | 访问域名 | *.tenant.com |
| plan | 订阅套餐 | basic |
| max_users | 最大用户数 | 10 |
| storage_limit | 存储限制 | 1GB |
| features | 功能开关 | [] |

### 4.3 健康监控指标

| 指标 | 告警阈值 | 处理动作 |
|------|---------|---------|
| 用户数超限 | > 90% | 通知升级 |
| 存储超限 | > 85% | 清理/扩容 |
| API调用异常 | 错误率 > 5% | 检查配置 |
| 数据隔离失败 | 任何 | 紧急修复 |

## 五、使用示例

### 5.1 创建租户

```
用户: 创建新租户 "Acme Corp"

技能执行:
1. 验证租户名称唯一性
2. 创建数据库schema
3. 配置访问域名
4. 初始化默认设置
5. 返回租户ID和凭证
```

### 5.2 监控租户健康

```
用户: 检查租户健康状态

技能执行:
1. 查询所有租户状态
2. 检查资源使用率
3. 检测异常指标
4. 生成健康报告
5. 触发告警通知
```

### 5.3 租户数据隔离验证

```
用户: 验证租户数据隔离

技能执行:
1. 检查数据库schema隔离
2. 验证访问权限配置
3. 测试跨租户访问拦截
4. 返回隔离验证报告
```

## 六、代码对接

### 6.1 TenantHealthMonitor（P50-A）

```java
// 租户健康监控
public class TenantHealthMonitor {
    public TenantHealthReport checkHealth(String tenantId) {
        // 检查用户数、存储、API调用
        // 计算健康评分
        // 返回健康报告
    }
}
```

## 七、配置要求

```yaml
tenant:
  default_plan: "basic"
  max_tenants: 100
  health_check_interval: "5m"
  isolation_mode: "schema"
  backup_enabled: true
```

## 八、相关闭环

- **闭环50**: 租户管理闭环 - 注册→配置→邀请→管理→健康监控
- **依赖**: 数据库多schema隔离, Redis命名空间隔离

---

*来源: L4业务闭环50补充*
*更新时间: 2026-07-09*