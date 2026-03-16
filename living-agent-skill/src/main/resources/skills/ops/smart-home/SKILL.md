---
name: smart-home
description: 智能家居控制技能 - 通过 Home Assistant 控制灯光、空调、窗帘等智能设备
metadata: { "brain": "ops", "category": "iot" }
---

# 智能家居控制技能

通过 Home Assistant 控制企业办公环境中的智能设备。

## 适用场景

- 办公室灯光控制
- 会议室空调调节
- 窗帘/百叶窗控制
- 智能插座管理
- 场景模式切换

## 配置要求

需要在 `application.yml` 中配置 Home Assistant 连接：

```yaml
home-assistant:
  url: ${HOME_ASSISTANT_URL:http://homeassistant.local:8123}
  token: ${HOME_ASSISTANT_TOKEN:}
  enabled: ${HOME_ASSISTANT_ENABLED:false}
```

## 支持的设备类型

| 设备类型 | 域 (Domain) | 功能 |
|---------|------------|------|
| 灯光 | light | 开关、亮度、色温、颜色 |
| 空调 | climate | 温度、模式、风速 |
| 窗帘 | cover | 开合位置 |
| 开关 | switch | 开关状态 |
| 传感器 | sensor | 状态读取 |
| 场景 | scene | 一键切换 |

## 常用命令

### 灯光控制

```bash
# 打开灯光
ha-call service light.turn_on --entity light.office_main

# 关闭灯光
ha-call service light.turn_off --entity light.office_main

# 调节亮度 (0-255)
ha-call service light.turn_on --entity light.office_main --data '{"brightness": 200}'

# 调节色温 (Kelvin)
ha-call service light.turn_on --entity light.office_main --data '{"kelvin": 4000}'
```

### 空调控制

```bash
# 设置温度
ha-call service climate.set_temperature --entity climate.office_ac --data '{"temperature": 24}'

# 设置模式 (off/heat/cool/auto/dry/fan_only)
ha-call service climate.set_hvac_mode --entity climate.office_ac --data '{"hvac_mode": "cool"}'
```

### 窗帘控制

```bash
# 打开窗帘
ha-call service cover.set_cover_position --entity cover.office_blinds --data '{"position": 100}'

# 关闭窗帘
ha-call service cover.set_cover_position --entity cover.office_blinds --data '{"position": 0}'
```

### 场景切换

```bash
# 执行场景
ha-call service scene.turn_on --entity scene.meeting_mode
```

## API 接口

### 获取设备状态

```
GET /api/states/{entity_id}
```

### 调用服务

```
POST /api/services/{domain}/{service}
{
  "entity_id": "light.office_main",
  "brightness": 200
}
```

## 企业应用场景

### 会议室管理

- 会议开始前自动打开灯光、调节空调
- 会议结束后自动关闭设备
- 投影模式：调暗灯光、关闭窗帘

### 节能管理

- 下班后自动关闭非必要设备
- 根据人员存在自动调节
- 峰谷电价时段优化

### 安防联动

- 离开时启动安防模式
- 异常时触发警报
- 访客模式切换

## 安全注意事项

1. 仅控制办公区域公共设备
2. 敏感区域需要权限验证
3. 操作日志记录审计
4. 异常状态告警通知

## 依赖

- Home Assistant 实例
- 网络连接
- API 访问令牌
