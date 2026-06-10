# Assets

Living Agent Desktop 桌面端静态资源。

## 文件清单

| 文件 | 用途 | 尺寸 | 大小 | 来源 |
|------|------|------|------|------|
| [icon.png](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/assets/icon.png) | 应用主图标（macOS/Linux/通用） | 1024×1024 | ~26 KB | **程序化生成**（蓝渐变 + LA + 节点） |
| [icon.ico](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/assets/icon.ico) | 应用主图标（Windows） | 16/32/48/64/128/256 | ~24 KB | 基于 icon.png 打包 |
| [tray-normal.png](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/assets/tray-normal.png) | 托盘正常态 | 256×256 | ~3 KB | 程序化生成（蓝+L） |
| [tray-badge-red.png](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/assets/tray-badge-red.png) | 托盘待接取态（带红点） | 256×256 | ~4 KB | 程序化生成（蓝+L+红点） |
| [generate-icon.ps1](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/assets/generate-icon.ps1) | 主图标程序化生成 | - | - | - |
| [generate-ico.ps1](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/assets/generate-ico.ps1) | ICO 多尺寸打包脚本 | - | - | - |
| [generate-tray-icons.ps1](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/assets/generate-tray-icons.ps1) | 托盘图标程序化生成脚本 | - | - | - |

## 设计说明

- **icon.png / icon.ico**：
  - 蓝色线性渐变（indigo-700 → blue-600）
  - 圆角矩形（22% 圆角半径）
  - 6 个装饰性神经网络节点（半透明白色小圆点）
  - 中心：粗体 Segoe UI "LA" 字母（Living Agent 缩写）
- **tray-normal.png**：蓝色圆角方块 + 白色 "L" 字母（更简洁的托盘版）
- **tray-badge-red.png**：蓝底 + 白色 "L" + 6px 白色描边的 64px 红色圆点

## ⚠️ 已知问题与解决方案

### Trae API 限制
- Trae API 返回 **JPEG 格式**（不是 PNG），需要手动转换
- Trae API 似乎**忽略了 prompt 差异**（不同 prompt 返回相同图像）
- IDE 可能缓存之前的占位图 "The image is generating..."

### 当前方案
**所有图标均采用 PowerShell + System.Drawing 程序化绘制**，优点：
- ✅ 不依赖外部 API
- ✅ IDE 可正常识别（标准 PNG/ICO 头）
- ✅ 视觉风格完全统一（同一色系 + 同一字体）
- ✅ 离线可生成
- ✅ 可重复执行（幂等）

## 重新生成

如需重新生成所有图标：

```powershell
# 1. 重新生成主图标
powershell -NoProfile -ExecutionPolicy Bypass -File .\generate-icon.ps1

# 2. 重新生成托盘图标
powershell -NoProfile -ExecutionPolicy Bypass -File .\generate-tray-icons.ps1

# 3. 重新打包 ICO（基于 icon.png）
powershell -NoProfile -ExecutionPolicy Bypass -File .\generate-ico.ps1
```

## Electron 引用

- 托盘：[src/main/tray.ts](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/src/main/tray.ts) 中 `getAssetPath(...)`
- 打包图标：[electron-builder.yml](file:///f:/SoarCloudAI/docker/living-agent-service/living-agent-desktop/electron-builder.yml) 中 `icon: assets/icon.ico`
