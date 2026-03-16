# Google Workspace

> Google Workspace 集成技能，Gmail、日历、Drive、文档、表格操作

## 一、技能描述

Google Workspace 技能使行政部和销售部数字员工能够操作 Google Workspace 服务，包括：
- Gmail 邮件管理
- Google Calendar 日历管理
- Google Drive 文件管理
- Google Sheets 表格操作
- Google Docs 文档操作

## 二、适用编制

| 编制代码 | 职位 | 主要用途 |
|---------|------|---------|
| A01 | 行政助理 | 日程管理、邮件处理 |
| A03 | 文案创作员 | 文档协作 |
| S01 | 销售代表 | 客户邮件、日程安排 |
| S02 | 市场专员 | 文档协作、表格分析 |

## 三、触发词

- Google、Gmail、日历
- Google Drive、Google Sheets
- Google Docs、Workspace
- 邮件、日程、文档

## 四、功能模块

### 4.1 Gmail 邮件

| 功能 | 命令示例 |
|------|---------|
| 发送邮件 | `gog mail send --to user@example.com --subject "主题" --body "内容"` |
| 搜索邮件 | `gog mail search --query "from:boss@company.com"` |
| 获取邮件 | `gog mail get --id <message-id>` |
| 标记已读 | `gog mail read --id <message-id>` |

### 4.2 Google Calendar

| 功能 | 命令示例 |
|------|---------|
| 创建事件 | `gog calendar create --summary "会议" --start "2026-03-15T10:00" --end "2026-03-15T11:00"` |
| 查看日程 | `gog calendar list --date 2026-03-15` |
| 查找空闲时间 | `gog calendar free --start "2026-03-15" --end "2026-03-16"` |
| 删除事件 | `gog calendar delete --id <event-id>` |

### 4.3 Google Drive

| 功能 | 命令示例 |
|------|---------|
| 上传文件 | `gog drive upload --file report.pdf --folder "Reports"` |
| 下载文件 | `gog drive download --id <file-id> --output ./local/` |
| 列出文件 | `gog drive list --folder "Documents"` |
| 分享文件 | `gog drive share --id <file-id> --email user@example.com --role reader` |

### 4.4 Google Sheets

| 功能 | 命令示例 |
|------|---------|
| 创建表格 | `gog sheets create --title "销售数据"` |
| 读取数据 | `gog sheets get --id <sheet-id> --range "A1:D10"` |
| 写入数据 | `gog sheets update --id <sheet-id> --range "A1:D10" --values "[[...]]"` |
| 追加数据 | `gog sheets append --id <sheet-id> --range "Sheet1" --values "[[...]]"` |

## 五、使用示例

### 5.1 发送会议邀请

```
用户: 发送会议邀请给张三，明天下午3点

技能执行:
1. 检查日历空闲时间
2. 创建日历事件
3. 发送Gmail邀请邮件
4. 返回事件ID
```

### 5.2 创建报告文档

```
用户: 创建一份销售报告文档

技能执行:
1. 创建Google Docs文档
2. 应用模板格式
3. 插入数据表格
4. 分享给相关人员
```

### 5.3 更新数据表格

```
用户: 将本月销售数据更新到Google Sheets

技能执行:
1. 获取销售数据
2. 连接Google Sheets
3. 追加数据行
4. 返回更新结果
```

## 六、认证配置

```yaml
google:
  credentials:
    type: service_account  # 或 oauth
    service_account_file: ${GOOGLE_APPLICATION_CREDENTIALS}
    # 或
    client_id: ${GOOGLE_CLIENT_ID}
    client_secret: ${GOOGLE_CLIENT_SECRET}
    redirect_uri: http://localhost:8080/callback
  
  scopes:
    - https://www.googleapis.com/auth/gmail.send
    - https://www.googleapis.com/auth/calendar
    - https://www.googleapis.com/auth/drive
    - https://www.googleapis.com/auth/spreadsheets
    - https://www.googleapis.com/auth/documents
```

## 七、权限范围

| 服务 | Scope |
|------|-------|
| Gmail | `gmail.send`, `gmail.readonly`, `gmail.modify` |
| Calendar | `calendar`, `calendar.events` |
| Drive | `drive`, `drive.file`, `drive.readonly` |
| Sheets | `spreadsheets`, `spreadsheets.readonly` |
| Docs | `documents`, `documents.readonly` |

## 八、错误处理

| 错误 | 处理方式 |
|------|---------|
| 认证失败 | 刷新Token或提示重新授权 |
| 配额超限 | 等待重试或升级配额 |
| 文件不存在 | 返回错误信息 |
| 权限不足 | 提示需要额外权限 |

---

*来源: 基于 ClawHub gog 技能转换*
*更新时间: 2026-03-13*
