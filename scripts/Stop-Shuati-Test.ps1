param([switch]$Elevated)

$ErrorActionPreference = "SilentlyContinue"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$pidFile = Join-Path $root ".local-run\processes.json"

function Test-Administrator {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = New-Object Security.Principal.WindowsPrincipal($identity)
  return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Stop-RecordedProcesses {
  if (-not (Test-Path $pidFile)) {
    return $true
  }

  $saved = Get-Content -Raw -LiteralPath $pidFile | ConvertFrom-Json
  $expected = @{
    backend = "shuati-backend.jar"
    payment = "payments\src\server.mjs"
    frontend = "vite.js"
  }
  $allStopped = $true

  foreach ($name in @("frontend", "payment", "backend")) {
    $processId = [int]$saved.$name
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($null -eq $process) {
      continue
    }

    $details = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    if ($null -eq $details -or $details.CommandLine -notmatch [regex]::Escape($expected[$name])) {
      continue
    }

    try {
      Stop-Process -Id $processId -Force -ErrorAction Stop
      Wait-Process -Id $processId -Timeout 5 -ErrorAction SilentlyContinue
    } catch {
      $allStopped = $false
    }
  }

  if ($allStopped) {
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    return $true
  }
  return $false
}

if (-not (Stop-RecordedProcesses)) {
  if (-not (Test-Administrator) -and -not $Elevated) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList @(
      "-NoProfile",
      "-ExecutionPolicy", "Bypass",
      "-File", "`"$PSCommandPath`"",
      "-Elevated"
    )
    exit 0
  }
  throw "部分测试进程无法停止，请打开任务管理器检查端口 8080 / 8090 / 5173"
}

Write-Host "拾题测试环境已停止。" -ForegroundColor Green
