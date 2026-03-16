# Contract Monitor - 合同监控器技能

> 到期提醒与续签预警

## 技能概述

Contract Monitor 持续监控合同状态，在合同即将到期时主动提醒相关人员，并协助准备续签材料。

## 核心能力

| 能力 | 说明 |
|------|------|
| **到期监控** | 监控合同到期日期 |
| **分级预警** | 30天/15天/7天/3天分级预警 |
| **续签提醒** | 提醒相关人员处理续签 |
| **材料准备** | 协助准备续签所需材料 |

## 技能参数

### 查询即将到期合同

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | get_expiring_contracts |
| `daysThreshold` | int | 否 | 天数阈值，默认30天 |

### 发送到期提醒

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | send_expiry_alert |
| `contractId` | string | 是 | 合同ID |

## 使用示例

### 查询即将到期合同

```json
{
  "action": "get_expiring_contracts",
  "daysThreshold": 30
}
```

**响应:**

```json
{
  "contracts": [
    {
      "contractId": "CT-2025-001",
      "contractName": "办公场地租赁合同",
      "party": "XX物业管理有限公司",
      "expiryDate": "2026-03-20",
      "daysRemaining": 14,
      "alertLevel": "MEDIUM",
      "amount": 500000,
      "department": "行政部",
      "manager": "王五",
      "renewalStatus": "PENDING"
    }
  ],
  "summary": {
    "total": 1,
    "critical": 0,
    "high": 0,
    "medium": 1,
    "low": 0
  }
}
```

## 预警级别

| 剩余天数 | 预警级别 | 通知渠道 | 通知对象 |
|---------|---------|---------|---------|
| 30天 | 低 | 系统消息 | 合同管理员 |
| 15天 | 中 | 钉钉 | 合同管理员 + 部门负责人 |
| 7天 | 高 | 钉钉 + 邮件 | 合同管理员 + 部门负责人 + 分管领导 |
| 3天 | 紧急 | 钉钉 + 邮件 + 短信 | 所有关注者 |

## 触发词

- 合同、到期、续签、监控

## 神经元集成

```java
@Service
public class ContractMonitor {
    
    @Autowired
    private EventDrivenNotifier notifier;
    
    // 每天上午9点检查合同到期
    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Shanghai")
    public void checkContractExpiry() {
        List<Contract> allContracts = contractService.getActiveContracts();
        
        for (Contract contract : allContracts) {
            int daysRemaining = calculateDaysRemaining(contract.getExpiryDate());
            
            if (daysRemaining <= 30) {
                AlertLevel level = determineAlertLevel(daysRemaining);
                List<String> channels = resolveChannels(level);
                
                notifier.pushNotification(Notification.builder()
                    .userId(contract.getManagerId())
                    .title("合同即将到期: " + contract.getName())
                    .content(String.format("合同将在%d天后到期，请及时处理续签", daysRemaining))
                    .type(NotificationType.ALERT)
                    .priority(mapToPriority(level))
                    .channels(channels)
                    .actionUrl("/contracts/" + contract.getId())
                    .build());
            }
        }
    }
}
```

## 配置

```yaml
living-agent:
  skills:
    contract-monitor:
      enabled: true
      
      # 检查配置
      check:
        cron: "0 0 9 * * ?"      # 每天上午9点检查
        timezone: "Asia/Shanghai"
        
      # 预警阈值
      alert-thresholds:
        - days: 30
          level: LOW
          channels: [SYSTEM_MESSAGE]
        - days: 15
          level: MEDIUM
          channels: [DINGTALK]
        - days: 7
          level: HIGH
          channels: [DINGTALK, EMAIL]
        - days: 3
          level: CRITICAL
          channels: [DINGTALK, EMAIL, SMS]
```

## 相关技能

- [event-driven-notifier](../../core/event-driven-notifier/SKILL.md) - 事件驱动通知器
- [risk-predictor](../../core/risk-predictor/SKILL.md) - 风险预警器
