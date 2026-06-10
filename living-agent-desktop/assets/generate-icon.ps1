# 主图标程序化生成（避开 Trae API 限制 + IDE 缓存问题）
# 设计：蓝色渐变圆角方块 + 神经网络节点装饰 + 粗体 "LA" 字母
# 输出：icon.png (1024×1024) + 同时刷新 icon.ico

Add-Type -AssemblyName System.Drawing

# 硬编码绝对路径，避免 sandbox 中 $PSScriptRoot 为空
$assetsDir = "f:\SoarCloudAI\docker\living-agent-service\living-agent-desktop\assets"
$iconPath = Join-Path $assetsDir "icon.png"

Write-Host "Icon path: $iconPath"

if (Test-Path $iconPath) { Remove-Item $iconPath -Force }

function Create-RoundedRectPath {
    param($x, $y, $width, $height, $radius)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $width - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $width - $d, $y + $height - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $height - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return ,$path
}

function Generate-Icon {
    param($outputPath, $iconSize)

    $cornerRadius = [int]($iconSize * 0.22)

    $bgTop = [System.Drawing.Color]::FromArgb(255, 67, 56, 202)
    $bgBottom = [System.Drawing.Color]::FromArgb(255, 37, 99, 235)
    $letterColor = [System.Drawing.Color]::White
    $nodeColor = [System.Drawing.Color]::FromArgb(150, 255, 255, 255)

    $bmp = New-Object System.Drawing.Bitmap $iconSize, $iconSize
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear([System.Drawing.Color]::Transparent)

    # 渐变背景
    $bgPath = Create-RoundedRectPath 0 0 $iconSize $iconSize $cornerRadius
    $bgRect = New-Object System.Drawing.Rectangle 0, 0, $iconSize, $iconSize
    $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $bgRect, $bgTop, $bgBottom,
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical
    )
    $g.FillPath($bgBrush, $bgPath)
    $bgBrush.Dispose()

    # 装饰节点
    $nodeRadius = [Math]::Max(2, [int]($iconSize * 0.012))
    $nodeBrush = New-Object System.Drawing.SolidBrush $nodeColor
    $nodes = @(
        @{x = $iconSize * 0.18; y = $iconSize * 0.22},
        @{x = $iconSize * 0.25; y = $iconSize * 0.15},
        @{x = $iconSize * 0.32; y = $iconSize * 0.20},
        @{x = $iconSize * 0.82; y = $iconSize * 0.78},
        @{x = $iconSize * 0.75; y = $iconSize * 0.85},
        @{x = $iconSize * 0.68; y = $iconSize * 0.80}
    )
    foreach ($n in $nodes) {
        $nx = [int]$n.x
        $ny = [int]$n.y
        $nr = $nodeRadius
        $nodePath = New-Object System.Drawing.Drawing2D.GraphicsPath
        $nodePath.AddEllipse($nx - $nr, $ny - $nr, $nr * 2, $nr * 2)
        $g.FillPath($nodeBrush, $nodePath)
        $nodePath.Dispose()
    }
    $nodeBrush.Dispose()

    # "LA" 字母
    $fontSize = [int]($iconSize * 0.52)
    if ($fontSize -le 0) { $fontSize = 100 }
    $font = New-Object System.Drawing.Font('Segoe UI', [single]$fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $textBrush = New-Object System.Drawing.SolidBrush $letterColor
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = 'Center'
    $sf.LineAlignment = 'Center'
    $yOffset = [int]($iconSize * 0.02)
    $rect = New-Object System.Drawing.RectangleF ([single]0), ([single]$yOffset), ([single]$iconSize), ([single]$iconSize)
    $g.DrawString('LA', $font, $textBrush, $rect, $sf)
    $textBrush.Dispose()
    $font.Dispose()

    $bmp.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
}

Write-Host "Generating icon.png (1024x1024)..." -ForegroundColor Cyan
Generate-Icon -outputPath $iconPath -iconSize 1024
Write-Host "  Done" -ForegroundColor Green

# 验证
$bytes = [System.IO.File]::ReadAllBytes($iconPath)
$header = [BitConverter]::ToString($bytes[0..7])
$hash = (Get-FileHash $iconPath).Hash
$sizeBytes = (Get-Item $iconPath).Length
Write-Host ("  icon.png: {0} bytes, header: {1}" -f $sizeBytes, $header)
Write-Host ("  Hash: {0}" -f $hash)

# 重新生成 ICO
Write-Host ""
Write-Host "Regenerating icon.ico..." -ForegroundColor Cyan
powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $assetsDir "generate-ico.ps1")
