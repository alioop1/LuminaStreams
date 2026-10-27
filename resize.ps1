Add-Type -AssemblyName System.Drawing

$iconPath = "C:\Users\TechPC\.gemini\antigravity\brain\a9527ef3-8c8d-41e1-8243-a7270e874be1\app_icon_1780321145611.png"
$bannerPath = "C:\Users\TechPC\.gemini\antigravity\brain\a9527ef3-8c8d-41e1-8243-a7270e874be1\tv_banner_1780321166952.png"

$resDir = "C:\Users\TechPC\Documents\LuminaStreams\app\src\main\res"

function Resize-Image {
    param([string]$src, [string]$dest, [int]$w, [int]$h)
    Write-Host "Resizing $src to $dest ($w x $h)"
    $img = [System.Drawing.Image]::FromFile($src)
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $rect = New-Object System.Drawing.Rectangle(0, 0, $w, $h)
    $g.DrawImage($img, $rect)
    $g.Dispose()
    $img.Dispose()
    
    # Check if dest exists and delete first to avoid lock
    if (Test-Path $dest) { Remove-Item $dest -Force }
    $bmp.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# Banner
Resize-Image $bannerPath "$resDir\drawable\tv_banner.png" 320 180
Resize-Image $bannerPath "$resDir\drawable\tv_banner_hd.png" 640 360

# Icon Foreground (Adaptive icon) - High Res for xxxhdpi
Resize-Image $iconPath "$resDir\drawable\ic_launcher_foreground.png" 432 432
Resize-Image $iconPath "$resDir\drawable\ic_launcher_monochrome.png" 432 432

# Icons
Resize-Image $iconPath "$resDir\mipmap-hdpi\ic_launcher.png" 72 72
Resize-Image $iconPath "$resDir\mipmap-hdpi\ic_launcher_round.png" 72 72
Resize-Image $iconPath "$resDir\mipmap-mdpi\ic_launcher.png" 48 48
Resize-Image $iconPath "$resDir\mipmap-mdpi\ic_launcher_round.png" 48 48
Resize-Image $iconPath "$resDir\mipmap-xhdpi\ic_launcher.png" 96 96
Resize-Image $iconPath "$resDir\mipmap-xhdpi\ic_launcher_round.png" 96 96
Resize-Image $iconPath "$resDir\mipmap-xxhdpi\ic_launcher.png" 144 144
Resize-Image $iconPath "$resDir\mipmap-xxhdpi\ic_launcher_round.png" 144 144
Resize-Image $iconPath "$resDir\mipmap-xxxhdpi\ic_launcher.png" 192 192
Resize-Image $iconPath "$resDir\mipmap-xxxhdpi\ic_launcher_round.png" 192 192

Write-Host "Done resizing images."
