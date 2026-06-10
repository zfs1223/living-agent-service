Add-Type -AssemblyName System.Drawing

$src = [System.Drawing.Image]::FromFile('f:\SoarCloudAI\docker\living-agent-service\living-agent-desktop\assets\icon.png')

$sizes = 16, 32, 48, 64, 128, 256
$bitmaps = @()
foreach ($s in $sizes) {
    $b = New-Object System.Drawing.Bitmap($s, $s)
    $g = [System.Drawing.Graphics]::FromImage($b)
    $g.InterpolationMode = 'HighQualityBicubic'
    $g.SmoothingMode = 'HighQuality'
    $g.PixelOffsetMode = 'HighQuality'
    $g.DrawImage($src, 0, 0, $s, $s)
    $g.Dispose()
    $bitmaps += $b
}

$out = 'f:\SoarCloudAI\docker\living-agent-service\living-agent-desktop\assets\icon.ico'
$iconStream = New-Object System.IO.MemoryStream
$writer = New-Object System.IO.BinaryWriter($iconStream)

# ICONDIR header
$writer.Write([UInt16]0)          # reserved
$writer.Write([UInt16]1)          # type 1 = .ico
$writer.Write([UInt16]$bitmaps.Count)

# Pre-compute sizes and offsets
$bmpStreams = @()
foreach ($b in $bitmaps) {
    $s = New-Object System.IO.MemoryStream
    $b.Save($s, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmpStreams += $s
}

$headerSize = 6 + 16 * $bitmaps.Count
$offset = $headerSize
$entries = @()
for ($i = 0; $i -lt $bitmaps.Count; $i++) {
    $bytes = $bmpStreams[$i].ToArray()
    $entries += [PSCustomObject]@{
        Width  = $bitmaps[$i].Width
        Height = $bitmaps[$i].Height
        Size   = $bytes.Length
        Offset = $offset
    }
    $offset += $bytes.Length
}

# ICONDIRENTRY for each size
for ($i = 0; $i -lt $entries.Count; $i++) {
    $e = $entries[$i]
    $w = if ($e.Width -ge 256) { 0 } else { $e.Width }
    $h = if ($e.Height -ge 256) { 0 } else { $e.Height }
    $writer.Write([Byte]$w)
    $writer.Write([Byte]$h)
    $writer.Write([Byte]0)            # color palette
    $writer.Write([Byte]0)            # reserved
    $writer.Write([UInt16]1)          # color planes
    $writer.Write([UInt16]32)         # bits per pixel
    $writer.Write([UInt32]$e.Size)
    $writer.Write([UInt32]$e.Offset)
}

# Image data
for ($i = 0; $i -lt $bitmaps.Count; $i++) {
    $bytes = $bmpStreams[$i].ToArray()
    $writer.Write($bytes)
    $bitmaps[$i].Dispose()
}

[System.IO.File]::WriteAllBytes($out, $iconStream.ToArray())
$writer.Close()
$iconStream.Close()
$src.Dispose()

$info = Get-Item $out
Write-Host "icon.ico generated: $($info.Length) bytes"
Write-Host "Sizes included: $($sizes -join ', ')"
