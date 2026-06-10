# 托盘图标程序化生成（独立函数版）
# 用函数返回 hash，调试用

Add-Type -AssemblyName System.Drawing

$assetsDir = "f:\SoarCloudAI\docker\living-agent-service\living-agent-desktop\assets"
Set-Location $assetsDir

function Create-RoundedRectPath {
    param($x, $y, $width, $height, $radius)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $width - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $width - $d, $y + $height - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $height - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return ,$path   # 注意: 加上逗号防止 PowerShell 解包
}

function Generate-TrayNormal {
    param($outputPath)

    $size = 256
    $cornerRadius = 56
    $bgColor = [System.Drawing.Color]::FromArgb(255, 37, 99, 235)
    $letterColor = [System.Drawing.Color]::White

    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear([System.Drawing.Color]::Transparent)

    $bgPath = Create-RoundedRectPath 0 0 $size $size $cornerRadius
    $bgBrush = New-Object System.Drawing.SolidBrush $bgColor
    $g.FillPath($bgBrush, $bgPath)
    $bgBrush.Dispose()

    $font = New-Object System.Drawing.Font('Segoe UI', 168, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $textBrush = New-Object System.Drawing.SolidBrush $letterColor
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = 'Center'
    $sf.LineAlignment = 'Center'
    $rect = New-Object System.Drawing.RectangleF 0, 4, $size, $size
    $g.DrawString('L', $font, $textBrush, $rect, $sf)
    $textBrush.Dispose()
    $font.Dispose()

    $bmp.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
}

function Generate-TrayBadgeRed {
    param($outputPath)

    $size = 256
    $cornerRadius = 56
    $bgColor = [System.Drawing.Color]::FromArgb(255, 37, 99, 235)
    $letterColor = [System.Drawing.Color]::White
    $badgeColor = [System.Drawing.Color]::FromArgb(255, 220, 38, 38)

    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.PixelOffsetMode = 'HighQuality'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear([System.Drawing.Color]::Transparent)

    $bgPath = Create-RoundedRectPath 0 0 $size $size $cornerRadius
    $bgBrush = New-Object System.Drawing.SolidBrush $bgColor
    $g.FillPath($bgBrush, $bgPath)
    $bgBrush.Dispose()

    $font = New-Object System.Drawing.Font('Segoe UI', 168, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $textBrush = New-Object System.Drawing.SolidBrush $letterColor
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = 'Center'
    $sf.LineAlignment = 'Center'
    $rect = New-Object System.Drawing.RectangleF 0, 4, $size, $size
    $g.DrawString('L', $font, $textBrush, $rect, $sf)
    $textBrush.Dispose()
    $font.Dispose()

    # 红色徽章
    $badgeSize = 64
    $whiteRing = 6
    $badgeX = $size - $badgeSize - 16
    $badgeY = 16

    # 白色描边
    $ringSize = $badgeSize + 2 * $whiteRing
    $ringX = $badgeX - $whiteRing
    $ringY = $badgeY - $whiteRing
    $ringPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $ringPath.AddEllipse($ringX, $ringY, $ringSize, $ringSize)
    $ringBrush = New-Object System.Drawing.SolidBrush $letterColor
    $g.FillPath($ringBrush, $ringPath)
    $ringBrush.Dispose()
    $ringPath.Dispose()

    # 红色圆点
    $badgePath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $badgePath.AddEllipse($badgeX, $badgeY, $badgeSize, $badgeSize)
    $badgeBrush = New-Object System.Drawing.SolidBrush $badgeColor
    $g.FillPath($badgeBrush, $badgePath)
    $badgeBrush.Dispose()
    $badgePath.Dispose()

    $bmp.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
}

# 删除旧文件，避免 hash 缓存
$normalPath = Join-Path $assetsDir "tray-normal.png"
$badgePath = Join-Path $assetsDir "tray-badge-red.png"
if (Test-Path $normalPath) { Remove-Item $normalPath -Force }
if (Test-Path $badgePath) { Remove-Item $badgePath -Force }

# 生成
Write-Host "Generating tray-normal.png..." -ForegroundColor Cyan
Generate-TrayNormal -outputPath $normalPath
Write-Host "  Done" -ForegroundColor Green

Write-Host "Generating tray-badge-red.png..." -ForegroundColor Cyan
Generate-TrayBadgeRed -outputPath $badgePath
Write-Host "  Done" -ForegroundColor Green

# 验证
foreach ($f in "tray-normal.png","tray-badge-red.png") {
    $path = Join-Path $assetsDir $f
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $header = [BitConverter]::ToString($bytes[0..3])
    $hash = (Get-FileHash $path).Hash
    $sizeBytes = (Get-Item $path).Length
    Write-Host ("  {0}: {1} bytes, header: {2}, hash: {3}" -f $f, $sizeBytes, $header, $hash)
}
