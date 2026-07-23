Add-Type -AssemblyName System.Drawing

function New-IconBitmap {
    param([int]$Size)

    $bmp = New-Object System.Drawing.Bitmap $Size, $Size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    $cx = $Size / 2.0
    $cy = $Size / 2.0
    $outerR = $Size * 0.47
    $innerR = $Size * 0.30
    $centerR = $Size * 0.24

    # Background circle (matches the chart's dark center color).
    $bgColor = [System.Drawing.Color]::FromArgb(255, 0x37, 0x47, 0x4f)
    $bgBrush = New-Object System.Drawing.SolidBrush $bgColor
    $g.FillEllipse($bgBrush, $cx - $outerR, $cy - $outerR, $outerR * 2, $outerR * 2)

    # Ring wedges, reusing the app's chart palette.
    $palette = @(
        [System.Drawing.Color]::FromArgb(255, 0x4E, 0x79, 0xA7),
        [System.Drawing.Color]::FromArgb(255, 0xF2, 0x8E, 0x2B),
        [System.Drawing.Color]::FromArgb(255, 0xE1, 0x57, 0x59),
        [System.Drawing.Color]::FromArgb(255, 0x76, 0xB7, 0xB2),
        [System.Drawing.Color]::FromArgb(255, 0x59, 0xA1, 0x4F)
    )
    $spans = @(80, 55, 95, 60, 70)
    $angle = -90.0
    $rectX = [int][Math]::Round($cx - $outerR)
    $rectY = [int][Math]::Round($cy - $outerR)
    $rectSize = [int][Math]::Round($outerR * 2)
    for ($i = 0; $i -lt $spans.Length; $i++) {
        $brush = New-Object System.Drawing.SolidBrush $palette[$i]
        $g.FillPie($brush, $rectX, $rectY, $rectSize, $rectSize, [float]$angle, [float]$spans[$i])
        $angle += $spans[$i]
        $brush.Dispose()
    }

    # Inner hole (donut effect) back to the dark background color / transparent center.
    $holeBrush = New-Object System.Drawing.SolidBrush $bgColor
    $g.FillEllipse($holeBrush, $cx - $innerR, $cy - $innerR, $innerR * 2, $innerR * 2)

    # Small light center dot (echoes the chart's root circle).
    $centerBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 0xEC, 0xEF, 0xF1))
    $g.FillEllipse($centerBrush, $cx - $centerR, $cy - $centerR, $centerR * 2, $centerR * 2)

    $g.Dispose()
    return $bmp
}

$projectRoot = "D:\Code\DiskUsageVisualizer"
$iconsDir = Join-Path $projectRoot "icons"
$resourcesDir = Join-Path $projectRoot "src\main\resources\com\diskusage"
New-Item -ItemType Directory -Force -Path $iconsDir | Out-Null
New-Item -ItemType Directory -Force -Path $resourcesDir | Out-Null

# PNG for the JavaFX window/taskbar icon.
$pngBitmap = New-IconBitmap -Size 256
$pngPath = Join-Path $resourcesDir "app-icon.png"
$pngBitmap.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$pngBitmap.Dispose()
"Saved PNG: $pngPath"

# Multi-resolution .ico for the packaged Windows .exe (PNG-compressed frames, Vista+ format).
$sizes = @(16, 32, 48, 64, 128, 256)
$pngBlobs = @()
foreach ($size in $sizes) {
    $bmp = New-IconBitmap -Size $size
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngBlobs += ,($ms.ToArray())
    $bmp.Dispose()
    $ms.Dispose()
}

$icoPath = Join-Path $iconsDir "app-icon.ico"
$fs = [System.IO.File]::Create($icoPath)
$bw = New-Object System.IO.BinaryWriter($fs)

# ICONDIR
$bw.Write([UInt16]0)      # reserved
$bw.Write([UInt16]1)      # type = icon
$bw.Write([UInt16]$sizes.Length)

$headerSize = 6 + (16 * $sizes.Length)
$offset = $headerSize
for ($i = 0; $i -lt $sizes.Length; $i++) {
    $size = $sizes[$i]
    $blob = $pngBlobs[$i]
    $dim = if ($size -ge 256) { 0 } else { $size }
    $bw.Write([byte]$dim)          # width
    $bw.Write([byte]$dim)          # height
    $bw.Write([byte]0)             # color count
    $bw.Write([byte]0)             # reserved
    $bw.Write([UInt16]1)           # planes
    $bw.Write([UInt16]32)          # bit count
    $bw.Write([UInt32]$blob.Length)
    $bw.Write([UInt32]$offset)
    $offset += $blob.Length
}
foreach ($blob in $pngBlobs) {
    $bw.Write($blob)
}
$bw.Flush()
$bw.Close()
$fs.Close()
"Saved ICO: $icoPath"
