$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runDir = Join-Path $root ".local-run"
$desktop = [Environment]::GetFolderPath("Desktop")
$iconPath = Join-Path $runDir "shuati-test.ico"
$pngPath = Join-Path $runDir "shuati-test.png"
$sourceImage = Join-Path $root "mobile\android\app\src\main\res\drawable-nodpi\splash_brand_mark.png"

New-Item -ItemType Directory -Path $runDir -Force | Out-Null

Add-Type -AssemblyName System.Drawing
$source = [System.Drawing.Image]::FromFile($sourceImage)
try {
  $bitmap = New-Object System.Drawing.Bitmap(256, 256)
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  try {
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $crop = New-Object System.Drawing.Rectangle(145, 15, 310, 310)
    $target = New-Object System.Drawing.Rectangle(0, 0, 256, 256)
    $graphics.DrawImage($source, $target, $crop, [System.Drawing.GraphicsUnit]::Pixel)
    $bitmap.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
  } finally {
    $graphics.Dispose()
    $bitmap.Dispose()
  }
} finally {
  $source.Dispose()
}

$png = [System.IO.File]::ReadAllBytes($pngPath)
$icon = New-Object byte[] (22 + $png.Length)
[BitConverter]::GetBytes([UInt16]0).CopyTo($icon, 0)
[BitConverter]::GetBytes([UInt16]1).CopyTo($icon, 2)
[BitConverter]::GetBytes([UInt16]1).CopyTo($icon, 4)
$icon[6] = 0
$icon[7] = 0
$icon[8] = 0
$icon[9] = 0
[BitConverter]::GetBytes([UInt16]1).CopyTo($icon, 10)
[BitConverter]::GetBytes([UInt16]32).CopyTo($icon, 12)
[BitConverter]::GetBytes([UInt32]$png.Length).CopyTo($icon, 14)
[BitConverter]::GetBytes([UInt32]22).CopyTo($icon, 18)
$png.CopyTo($icon, 22)
[System.IO.File]::WriteAllBytes($iconPath, $icon)

$shell = New-Object -ComObject WScript.Shell
$startShortcut = $shell.CreateShortcut((Join-Path $desktop "启动拾题测试环境.lnk"))
$startShortcut.TargetPath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
$startShortcut.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$root\scripts\Start-Shuati-Test.ps1`""
$startShortcut.WorkingDirectory = $root
$startShortcut.Description = "启动拾题测试环境（邮件模拟 + Waffo test）"
$startShortcut.IconLocation = "$iconPath,0"
$startShortcut.Save()

$stopShortcut = $shell.CreateShortcut((Join-Path $desktop "停止拾题测试环境.lnk"))
$stopShortcut.TargetPath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
$stopShortcut.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$root\scripts\Stop-Shuati-Test.ps1`""
$stopShortcut.WorkingDirectory = $root
$stopShortcut.Description = "停止拾题本地测试环境"
$stopShortcut.IconLocation = "$iconPath,0"
$stopShortcut.Save()

Write-Host "桌面快捷方式已创建：" -ForegroundColor Green
Write-Host "  启动拾题测试环境"
Write-Host "  停止拾题测试环境"
