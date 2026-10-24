param([switch]$Elevated)

$ErrorActionPreference = "SilentlyContinue"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$pidFile = Join-Path $root ".local-run\processes.json"

function Test-Administrator {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = New-Object Security.Principal.WindowsPrincipal($identity)
  return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-Administrator) -and -not $Elevated) {
  Start-Process powershell.exe -Verb RunAs -ArgumentList @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$PSCommandPath`"",
    "-Elevated"
  )
  exit 0
}

if (-not (Test-Path $pidFile)) {
  Write-Host "拾题测试环境没有运行。"
  exit 0
}

$saved = Get-Content -Raw -LiteralPath $pidFile | ConvertFrom-Json
foreach ($name in @("frontend", "payment", "backend")) {
  $processId = [int]$saved.$name
  $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
  if ($null -ne $process) {
    Stop-Process -Id $processId -Force
  }
}

Remove-Item -LiteralPath $pidFile -Force
Write-Host "拾题测试环境已停止。" -ForegroundColor Green
