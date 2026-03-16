# Browser Automation

> 浏览器自动化技能，网页导航、点击、输入、截图

## 一、技能描述

浏览器自动化技能使技术部数字员工能够自动化操作网页，包括：
- 网页导航和截图
- 表单填写和提交
- 数据抓取
- UI自动化测试

## 二、适用编制

| 编制代码 | 职位 | 主要用途 |
|---------|------|---------|
| T09 | 前端工程师 | UI自动化测试 |
| T04 | 运维工程师 | 网站监控 |
| T03 | DevOps工程师 | 自动化部署验证 |

## 三、触发词

- 浏览器、browser、自动化
- 网页操作、网页截图
- 表单填写、点击
- UI测试、E2E测试

## 四、功能模块

### 4.1 导航操作

| 功能 | 命令示例 |
|------|---------|
| 打开网页 | `navigate --url https://example.com` |
| 后退 | `back` |
| 前进 | `forward` |
| 刷新 | `refresh` |

### 4.2 元素操作

| 功能 | 命令示例 |
|------|---------|
| 点击 | `click --selector "#submit-btn"` |
| 输入文本 | `type --selector "#username" --value "admin"` |
| 选择下拉 | `select --selector "#country" --value "CN"` |
| 等待元素 | `wait --selector ".result" --timeout 5000` |

### 4.3 数据获取

| 功能 | 命令示例 |
|------|---------|
| 截图 | `screenshot --output page.png` |
| 获取文本 | `text --selector ".title"` |
| 获取属性 | `attr --selector "a" --name href` |
| 获取HTML | `html --selector ".content"` |

## 五、使用示例

### 5.1 自动登录

```
用户: 自动登录到测试系统

技能执行:
1. 导航到登录页面
2. 输入用户名
3. 输入密码
4. 点击登录按钮
5. 等待登录成功
6. 截图确认
```

### 5.2 表单填写

```
用户: 填写注册表单

技能执行:
1. 定位表单元素
2. 填写姓名字段
3. 填写邮箱字段
4. 填写电话字段
5. 选择下拉选项
6. 点击提交
```

### 5.3 数据抓取

```
用户: 抓取商品列表数据

技能执行:
1. 导航到商品页面
2. 等待列表加载
3. 循环获取商品信息
4. 提取名称、价格、链接
5. 保存为JSON
```

## 六、命令格式

```bash
# 导航
agent-browser navigate --url <url>

# 点击
agent-browser click --selector <css-selector>

# 输入
agent-browser type --selector <css-selector> --value <text>

# 截图
agent-browser screenshot --output <path>

# 获取快照
agent-browser snapshot

# 等待
agent-browser wait --selector <css-selector> --timeout <ms>
```

## 七、选择器示例

| 目标元素 | CSS选择器 |
|---------|----------|
| ID元素 | `#username` |
| Class元素 | `.btn-primary` |
| 属性选择 | `[data-testid="submit"]` |
| 文本包含 | `button:has-text("登录")` |
| 组合选择 | `form .input-group input` |

## 八、自动化脚本示例

```javascript
// 登录流程
await navigate({ url: "https://app.example.com/login" });
await type({ selector: "#email", value: "user@example.com" });
await type({ selector: "#password", value: "secret123" });
await click({ selector: "button[type=submit]" });
await wait({ selector: ".dashboard", timeout: 5000 });
await screenshot({ output: "login-success.png" });
```

## 九、配置要求

```yaml
browser:
  engine: rust  # rust 或 node
  
  headless: true
  
  viewport:
    width: 1920
    height: 1080
  
  timeout:
    navigation: 30000
    element: 5000
  
  proxy:
    enabled: false
    server: http://proxy:8080
  
  user_agent: "Mozilla/5.0 ..."
```

## 十、安全注意事项

1. **敏感数据**: 密码等敏感信息应从环境变量获取
2. **速率限制**: 避免过快请求导致被封
3. **Cookie管理**: 登录状态需要安全存储
4. **截图存储**: 敏感页面截图需加密存储

---

*来源: 基于 ClawHub agent-browser 技能转换*
*更新时间: 2026-03-13*
