# CRM Integration

> CRM系统集成技能，连接客户关系管理系统、销售平台、营销工具

## 一、技能描述

CRM集成技能使销售部数字员工能够与各类客户关系管理系统进行操作，包括：
- CRM系统对接（Salesforce、HubSpot、纷享销客）
- 销售平台对接（企业微信、钉钉客户管理）
- 营销工具对接（邮件营销、短信营销）
- 数据分析（销售漏斗、客户画像）

## 二、适用编制

| 编制代码 | 职位 | 主要用途 |
|---------|------|---------|
| S01 | 销售代表 | 客户信息管理、跟进记录 |
| S02 | 市场专员 | 营销活动管理、线索转化 |
| S03 | 渠道经理 | 平台对接、渠道数据分析 |

## 三、触发词

- CRM、客户管理、销售系统
- Salesforce、HubSpot、分享销客
- 销售漏斗、客户跟进
- 线索、商机、成交

## 四、支持的平台

### 4.1 CRM系统

| 平台 | API支持 | 功能 |
|------|--------|------|
| Salesforce | ✅ | 完整CRM功能 |
| HubSpot | ✅ | 营销+销售一体化 |
| 纷享销客 | ✅ | 国内CRM |
| 销售易 | ✅ | 移动CRM |

### 4.2 企业平台

| 平台 | API支持 | 功能 |
|------|--------|------|
| 企业微信 | ✅ | 客户联系、群发 |
| 钉钉 | ✅ | 客户管理 |
| 飞书 | ✅ | 多维表格 |

### 4.3 营销工具

| 平台 | API支持 | 功能 |
|------|--------|------|
| Mailchimp | ✅ | 邮件营销 |
| SendGrid | ✅ | 邮件发送 |
| 阿里云短信 | ✅ | 短信营销 |

## 五、使用示例

### 5.1 创建客户

```
用户: 在CRM中创建一个新客户
公司: ABC科技有限公司
联系人: 张三
电话: 138xxxx

技能执行:
1. 调用CRM API创建客户记录
2. 自动关联销售代表
3. 返回客户ID和访问链接
```

### 5.2 查询销售漏斗

```
用户: 查看本季度销售漏斗数据

技能执行:
1. 调用CRM报表API
2. 返回各阶段商机数量和金额
3. 计算转化率
```

### 5.3 发送营销邮件

```
用户: 给所有未成交客户发送促销邮件

技能执行:
1. 筛选目标客户列表
2. 调用邮件营销API
3. 返回发送结果统计
```

## 六、配置要求

```yaml
crm:
  salesforce:
    api_url: https://api.salesforce.com
    client_id: ${SF_CLIENT_ID}
    client_secret: ${SF_CLIENT_SECRET}
  
  hubspot:
    api_url: https://api.hubapi.com
    api_key: ${HUBSPOT_API_KEY}
  
  feishu:
    app_id: ${FEISHU_APP_ID}
    app_secret: ${FEISHU_APP_SECRET}

marketing:
  mailchimp:
    api_url: https://api.mailchimp.com
    api_key: ${MAILCHIMP_API_KEY}
```

## 七、数据模型

### 7.1 客户

```json
{
  "customer_id": "CUS001",
  "company": "ABC科技",
  "industry": "互联网",
  "size": "50-100人",
  "contacts": [
    {"name": "张三", "title": "技术总监", "phone": "138xxxx"}
  ],
  "stage": "商机挖掘",
  "owner": "S01"
}
```

### 7.2 商机

```json
{
  "opportunity_id": "OPP001",
  "customer_id": "CUS001",
  "name": "ABC科技数字化转型项目",
  "amount": 500000,
  "stage": "方案报价",
  "probability": 60,
  "expected_close": "2026-04-30"
}
```

## 八、自动化规则

| 触发条件 | 自动动作 |
|---------|---------|
| 新客户创建 | 分配给销售代表、发送欢迎邮件 |
| 商机阶段变更 | 更新预测、通知相关人员 |
| 7天未跟进 | 提醒销售代表、升级主管 |
| 商机成交 | 创建合同、通知财务 |

---

*来源: 基于 ClawHub api-gateway 和 automation-workflows 技能转换*
*更新时间: 2026-03-13*
