---
name: sensor-data
description: "WiFi物理感知传感器数据查询，人员检测/占用统计/生命体征"
risk: low
source: internal
personalSafe: false
date_added: '2026-07-13'
---

# WiFi 物理感知传感器数据查询

## 技能描述

按需查询 RuView WiFi CSI 物理感知数据，包括人员检测、占用统计、生命体征等。不进行后台轮询，仅在需要时调用 API 获取数据。

## 适用编制

- **部门**: ops（运营部）、admin（行政部）、core（核心层）
- **权限**: DEPARTMENT 及以上
- **场景**: 智能会议室管理、办公区占用分析、老人看护告警

## 触发词

- "传感器数据"、"WiFi 感知"、"人员检测"
- "会议室占用"、"办公区人数"
- "生命体征"、"呼吸检测"
- "RuView"、"CSI 数据"

## 检查维度

1. **数据可用性**: RuView 服务是否在线
2. **数据完整性**: 是否有有效的感知结果
3. **时间戳**: 数据是否为最新（5分钟内）
4. **置信度**: 检测结果的可靠性

## 使用示例

### 示例 1：查询会议室占用情况

```
用户: 帮我查一下会议室A现在有多少人

助手: 我来查询一下会议室的传感器数据。

[调用 sensor_data 工具，query_type=current]

根据 WiFi 感知数据，会议室A当前检测到 3 人，占用状态为"轻度使用"。
```

### 示例 2：查询生命体征

```
用户: 老人的生命体征正常吗？

助手: 我来查询最新的生命体征数据。

[调用 sensor_data 工具，query_type=vital-signs]

根据最新检测，呼吸频率为 16 次/分钟，处于正常范围。未检测到跌倒或异常活动。
```

### 示例 3：查询模型信息

```
用户: 当前使用的是什么感知模型？

助手: [调用 sensor_data 工具，query_type=model-info]

当前使用 WiFi-DensePose v1.0.0 模型，支持 17 个身体关键点检测和生命体征监测。
数据源为 ESP32 硬件，采样率约 0.5 Hz。
```

## 审查报告模板

```json
{
  "query_type": "current|vital-signs|model-info",
  "timestamp": "2026-07-14T10:30:00Z",
  "source": "ruview-api",
  "data": {
    "persons": 3,
    "occupancy": "moderate",
    "vital_signs": {
      "breathing_rate_bpm": 16,
      "motion_detected": true
    }
  },
  "confidence": 0.85
}
```

## 自动化规则

1. **按需调用**: 不自动轮询，仅在用户请求时查询
2. **缓存控制**: 单次查询结果不缓存，每次都获取最新数据
3. **失败处理**: API 调用失败时返回友好提示，不抛出异常
4. **日志抑制**: 同类警告 5 分钟内只记录一次

## 配置要求

| 配置项 | 环境变量 | 默认值 |
|--------|----------|--------|
| RuView API 地址 | `RUVIEW_API_BASE_URL` | `http://ruview-sensing:3000` |
| API 超时 | `RUVIEW_API_TIMEOUT_MS` | 5000 |

## 质量门禁

- ✅ API 响应时间 < 2 秒
- ✅ 数据时间戳在 5 分钟内
- ✅ 置信度 > 0.7 时才报告具体人数
- ⚠️ 置信度 0.5-0.7 时标注"低置信度"
- ❌ 置信度 < 0.5 时返回"无法确定"

## 技术说明

### 数据流

```
用户请求 → 大脑 → SensorDataTool → RuView API → 返回数据
```

### 与旧版对比

| 特性 | 旧版（已弃用） | 新版 |
|------|----------------|------|
| 调用方式 | 后台轮询（每10秒） | 按需查询 |
| 资源消耗 | 持续消耗 | 仅请求时消耗 |
| 消息堆积 | 有（无消费者） | 无 |
| 数据新鲜度 | 可能滞后 | 实时获取 |

## 相关文档

- [RuView 集成计划](../../docs/pending/RUVIEW_INTEGRATION_PLAN.md)
- [ESP32 Docker 对接指南](../../docker/RuView/ESP32-DOCKER-INTEGRATION-GUIDE.md)