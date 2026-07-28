#requires -Version 5
<#
.SYNOPSIS
  Regenerates the X-Men app icons from src/main/resources/images/Front-End-Logo.png.

.DESCRIPTION
  - Auto-trims transparent pixels from the source PNG so the diamond-X fills the canvas.
  - Re-exports Front-End-Logo.png (1024x1024) with a tight crop + a small padding margin.
  - Re-exports x-men-icon.png (1024x1024) for jpackage (Linux + Mac fallback).
  - Generates a multi-resolution x-men-icon.ico (16/24/32/48/64/128/256) for Windows jpackage.
  - Generates x-men-icon.icns (ic07/ic08/ic09/ic10) for macOS jpackage.

  Run from the project root:
      pwsh -File scripts\build-icons.ps1
  or
      powershell -NoProfile -File scripts\build-icons.ps1

  Re-run whenever the source logo changes.
#>

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$srcLogo  = Join-Path $repoRoot 'src\main\resources\images\Front-End-Logo.png'
$imgDir   = Join-Path $repoRoot 'src\main\resources\images'
$pkgDir   = Join-Path $repoRoot 'src\main\resources\packaging'

if (-not (Test-Path $srcLogo)) {
    throw "Source logo not found: $srcLogo"
}

Add-Type -AssemblyName System.Drawing

# --------------------------------------------------------------------
# 1. Load source, find tight bounding box of non-transparent pixels.
# --------------------------------------------------------------------
$source = [System.Drawing.Image]::FromFile($srcLogo)
$srcW = $source.Width
$srcH = $source.Height
Write-Host ("Source: {0}x{1}" -f $srcW, $srcH)

$bmp = New-Object System.Drawing.Bitmap $source
$source.Dispose()

# Lock pixels for fast scan.
$rect = New-Object System.Drawing.Rectangle 0, 0, $bmp.Width, $bmp.Height
$data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $data.Stride
$bytesLen = [Math]::Abs($stride) * $bmp.Height
$buffer = New-Object byte[] $bytesLen
[System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $buffer, 0, $bytesLen)
$bmp.UnlockBits($data)

$minX = $bmp.Width
$minY = $bmp.Height
$maxX = 0
$maxY = 0
$alphaThreshold = 8  # treat alpha < 8 as fully transparent (anti-noise)
for ($y = 0; $y -lt $bmp.Height; $y++) {
    $row = $y * $stride
    for ($x = 0; $x -lt $bmp.Width; $x++) {
        $a = $buffer[$row + $x * 4 + 3]
        if ($a -gt $alphaThreshold) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
if ($maxX -le $minX -or $maxY -le $minY) {
    throw "Could not find any opaque pixels in the source logo."
}

$bbW = $maxX - $minX + 1
$bbH = $maxY - $minY + 1
Write-Host ("Tight bbox: ({0},{1}) -> ({2},{3})  size {4}x{5}" -f $minX, $minY, $maxX, $maxY, $bbW, $bbH)

# --------------------------------------------------------------------
# 2. Build a 1024x1024 master with a small 4% padding around the bbox.
# --------------------------------------------------------------------
$masterSize = 1024
$paddingPct = 0.04
$logoMax    = [int]($masterSize * (1.0 - 2.0 * $paddingPct))
$scale      = [Math]::Min($logoMax / $bbW, $logoMax / $bbH)
$drawW      = [int]([Math]::Round($bbW * $scale))
$drawH      = [int]([Math]::Round($bbH * $scale))
$drawX      = [int](($masterSize - $drawW) / 2)
$drawY      = [int](($masterSize - $drawH) / 2)

$master = New-Object System.Drawing.Bitmap $masterSize, $masterSize, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($master)
$g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
$g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

$srcRect  = New-Object System.Drawing.Rectangle $minX, $minY, $bbW, $bbH
$destRect = New-Object System.Drawing.Rectangle $drawX, $drawY, $drawW, $drawH
$g.DrawImage($bmp, $destRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
$bmp.Dispose()

# --------------------------------------------------------------------
# 3. Save the 1024x1024 master to both PNG locations.
# --------------------------------------------------------------------
$outFrontEnd = Join-Path $imgDir 'Front-End-Logo.png'
$outIconPng  = Join-Path $pkgDir 'x-men-icon.png'

$master.Save($outFrontEnd, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host ("Wrote {0} ({1}x{1})" -f $outFrontEnd, $masterSize)
$master.Save($outIconPng, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host ("Wrote {0} ({1}x{1})" -f $outIconPng, $masterSize)

# Helper: scale to a square size with high-quality resampling, return Bitmap.
function Resize-ToSquare {
    param([System.Drawing.Bitmap]$src, [int]$size)
    $dst = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gg = [System.Drawing.Graphics]::FromImage($dst)
    $gg.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $gg.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gg.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $gg.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $gg.Clear([System.Drawing.Color]::Transparent)
    $gg.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $size, $size))
    $gg.Dispose()
    return $dst
}

# Helper: bitmap -> PNG byte[].
function PngBytes {
    param([System.Drawing.Bitmap]$bm)
    $ms = New-Object System.IO.MemoryStream
    $bm.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    return $ms.ToArray()
}

# --------------------------------------------------------------------
# 4. Build x-men-icon.ico with sizes 16,24,32,48,64,128,256.
#    ICONDIR (6 bytes) + N x ICONDIRENTRY (16 bytes) + PNG payloads.
# --------------------------------------------------------------------
$icoSizes = @(16, 24, 32, 48, 64, 128, 256)
$entries = @()
foreach ($s in $icoSizes) {
    $resized = Resize-ToSquare -src $master -size $s
    $png = PngBytes -bm $resized
    $resized.Dispose()
    $entries += [PSCustomObject]@{
        Size  = $s
        Bytes = $png
    }
}

$ms = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter $ms
# ICONDIR
$bw.Write([UInt16]0)          # reserved
$bw.Write([UInt16]1)          # type = 1 (icon)
$bw.Write([UInt16]$entries.Count)
$dataOffset = 6 + 16 * $entries.Count
foreach ($e in $entries) {
    $w = if ($e.Size -ge 256) { [byte]0 } else { [byte]$e.Size }
    $h = $w
    $bw.Write([byte]$w)           # width  (0 = 256)
    $bw.Write([byte]$h)           # height (0 = 256)
    $bw.Write([byte]0)            # colour count
    $bw.Write([byte]0)            # reserved
    $bw.Write([UInt16]1)          # colour planes
    $bw.Write([UInt16]32)         # bits/pixel
    $bw.Write([UInt32]$e.Bytes.Length)
    $bw.Write([UInt32]$dataOffset)
    $dataOffset += $e.Bytes.Length
}
foreach ($e in $entries) {
    # Pass offset+length explicitly so PowerShell binds BinaryWriter.Write(byte[], int, int)
    # instead of resolving to Write(byte) and writing only the first byte (PS overload-resolution gotcha).
    $bw.Write($e.Bytes, 0, $e.Bytes.Length)
}
$bw.Flush()
$icoPath = Join-Path $pkgDir 'x-men-icon.ico'
[System.IO.File]::WriteAllBytes($icoPath, $ms.ToArray())
$ms.Dispose()
Write-Host ("Wrote {0} (sizes: {1})" -f $icoPath, ($icoSizes -join ','))

# --------------------------------------------------------------------
# 5. Build x-men-icon.icns.
#    Magic 'icns' + total length (BE) + chunks: 4-byte type + 4-byte length BE + PNG data.
#    Chunks used: ic07 (128), ic08 (256), ic09 (512), ic10 (1024).
# --------------------------------------------------------------------
$icnsChunks = @(
    @{ Type='ic07'; Size=128 },
    @{ Type='ic08'; Size=256 },
    @{ Type='ic09'; Size=512 },
    @{ Type='ic10'; Size=1024 }
)

$chunkBytes = New-Object System.IO.MemoryStream
foreach ($c in $icnsChunks) {
    $resized = Resize-ToSquare -src $master -size $c.Size
    $png = PngBytes -bm $resized
    $resized.Dispose()
    $typeBytes = [System.Text.Encoding]::ASCII.GetBytes($c.Type)
    $chunkBytes.Write($typeBytes, 0, 4)
    # length includes the 8-byte header itself
    $totalLen = 8 + $png.Length
    $lenBytes = [BitConverter]::GetBytes([UInt32]$totalLen)
    if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($lenBytes) }
    $chunkBytes.Write($lenBytes, 0, 4)
    $chunkBytes.Write($png, 0, $png.Length)
}
$icnsChunkBlob = $chunkBytes.ToArray()
$chunkBytes.Dispose()

$icnsTotal = 8 + $icnsChunkBlob.Length
$icnsMs = New-Object System.IO.MemoryStream
$icnsMs.Write([System.Text.Encoding]::ASCII.GetBytes('icns'), 0, 4)
$tlBytes = [BitConverter]::GetBytes([UInt32]$icnsTotal)
if ([BitConverter]::IsLittleEndian) { [Array]::Reverse($tlBytes) }
$icnsMs.Write($tlBytes, 0, 4)
$icnsMs.Write($icnsChunkBlob, 0, $icnsChunkBlob.Length)
$icnsPath = Join-Path $pkgDir 'x-men-icon.icns'
[System.IO.File]::WriteAllBytes($icnsPath, $icnsMs.ToArray())
$icnsMs.Dispose()
$chunkTypes = ($icnsChunks | ForEach-Object { $_.Type }) -join ','
Write-Host ("Wrote {0} (chunks: {1})" -f $icnsPath, $chunkTypes)

$master.Dispose()
Write-Host 'Icons regenerated successfully.'
