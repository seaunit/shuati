param([switch]$Elevated)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$runDir = Join-Path $root ".local-run"
$pidFile = Join-Path $runDir "processes.json"
$backendLog = Join-Path $runDir "backend.log"
$backendErr = Join-Path $runDir "backend.err.log"
$paymentLog = Join-Path $runDir "payment.log"
$paymentErr = Join-Path $runDir "payment.err.log"
$frontendLog = Join-Path $runDir "frontend.log"
$frontendErr = Join-Path $runDir "frontend.err.log"
$buildLog = Join-Path $runDir "build.log"
$openUrl = "http://127.0.0.1:5173/app/"

function Test-Administrator {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
  $principal = New-Object Security.Principal.WindowsPrincipal($identity)
  return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Test-Port([int]$Port) {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $task = $client.ConnectAsync("127.0.0.1", $Port)
    return $task.Wait(600) -and $client.Connected
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

function Wait-Port([int]$Port, [int]$Seconds = 60) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-Port $Port) { return $true }
    Start-Sleep -Seconds 1
  }
  return $false
}

function Wait-Http([string]$Url, [int]$Seconds = 120, $Process = $null) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  while ((Get-Date) -lt $deadline) {
    if ($null -ne $Process -and $Process.HasExited) {
      return $false
    }
    try {
      $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 $Url
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
        return $true
      }
    } catch {
      Start-Sleep -Seconds 1
    }
  }
  return $false
}

$services = foreach ($serviceName in @("MySQL84", "Redis")) {
  $service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
  if ($null -eq $service) {
    throw "缺少 Windows 服务：$serviceName"
  }
  $service
}

if (-not (Test-Administrator) -and ($services | Where-Object Status -ne "Running")) {
  Start-Process powershell.exe -Verb RunAs -ArgumentList @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$PSCommandPath`"",
    "-Elevated"
  )
  exit 0
}

New-Item -ItemType Directory -Path $runDir -Force | Out-Null
Set-Location $root

foreach ($service in $services) {
  $serviceName = $service.Name
  if ($service.Status -ne "Running") {
    Start-Service -Name $serviceName
  }
}
if (-not (Wait-Port 3306 60)) {
  throw "MySQL 未在 60 秒内就绪"
}
if (-not (Wait-Port 6379 30)) {
  throw "Redis 未在 30 秒内就绪"
}

if (-not (Test-Path (Join-Path $root "frontend\node_modules\vite"))) {
  & npm.cmd --prefix frontend install *> $buildLog
  if ($LASTEXITCODE -ne 0) { throw "前端依赖安装失败，请查看 $buildLog" }
}
if (-not (Test-Path (Join-Path $root "payments\node_modules\@waffo\pancake-ts"))) {
  & npm.cmd --prefix payments install *>> $buildLog
  if ($LASTEXITCODE -ne 0) { throw "支付侧车依赖安装失败，请查看 $buildLog" }
}

$jar = Join-Path $root "backend\target\shuati-backend.jar"
$latestSource = Get-ChildItem (Join-Path $root "backend\src") -Recurse -File |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1
$needsBuild = -not (Test-Path $jar)
if (-not $needsBuild) {
  $needsBuild = $latestSource.LastWriteTime -gt (Get-Item $jar).LastWriteTime
}
if ($needsBuild) {
  & mvn.cmd -f backend/pom.xml -DskipTests package -q *> $buildLog
  if ($LASTEXITCODE -ne 0) {
    throw "后端构建失败，请查看 $buildLog"
  }
}

if (Test-Path $pidFile) {
  & (Join-Path $PSScriptRoot "Stop-Shuati-Test.ps1") -Elevated
}

if (Test-Port 8080 -or Test-Port 8090 -or Test-Port 5173) {
  throw "测试端口 8080 / 8090 / 5173 已被其他程序占用"
}

$backend = $null
$payment = $null
$frontend = $null
try {
  $backend = Start-Process java.exe `
    -ArgumentList @("-jar", "`"$jar`"", "--spring.profiles.active=local") `
    -WorkingDirectory $root `
    -RedirectStandardOutput $backendLog `
    -RedirectStandardError $backendErr `
    -WindowStyle Hidden `
    -PassThru

  $payment = Start-Process node.exe `
    -ArgumentList @("`"$root\payments\src\server.mjs`"") `
    -WorkingDirectory (Join-Path $root "payments") `
    -RedirectStandardOutput $paymentLog `
    -RedirectStandardError $paymentErr `
    -WindowStyle Hidden `
    -PassThru

  if (-not (Wait-Http "http://127.0.0.1:8080/actuator/health" 120 $backend)) {
    throw "后端启动超时，请查看 $backendLog 和 $backendErr"
  }
  if (-not (Wait-Http "http://127.0.0.1:8090/health" 30 $payment)) {
    throw "支付侧车启动超时，请查看 $paymentLog 和 $paymentErr"
  }

  $frontend = Start-Process node.exe `
    -ArgumentList @(
      "`"$root\frontend\node_modules\vite\bin\vite.js`"",
      "--host", "127.0.0.1",
      "--port", "5173"
    ) `
    -WorkingDirectory (Join-Path $root "frontend") `
    -RedirectStandardOutput $frontendLog `
    -RedirectStandardError $frontendErr `
    -WindowStyle Hidden `
    -PassThru

  if (-not (Wait-Http "http://127.0.0.1:5173/" 60 $frontend)) {
    throw "前端启动超时，请查看 $frontendLog 和 $frontendErr"
  }
} catch {
  foreach ($process in @($frontend, $payment, $backend)) {
    if ($null -ne $process -and -not $process.HasExited) {
      Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
  }
  throw
}

@{
  backend = $backend.Id
  payment = $payment.Id
  frontend = $frontend.Id
  startedAt = (Get-Date).ToString("o")
} | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8

Start-Process $openUrl
Start-Process powershell.exe -ArgumentList @(
  "-NoExit",
  "-NoProfile",
  "-ExecutionPolicy", "Bypass",
  "-File", "`"$root\scripts\Show-Shuati-Test-Logs.ps1`""
)

Write-Host ""
Write-Host "拾题测试环境已启动：" -ForegroundColor Green
Write-Host "  应用：$openUrl"
Write-Host "  支付模式：Waffo test"
Write-Host "  邮件模式：控制台模拟发送"
Write-Host "  模拟验证码：查看“拾题测试环境日志”窗口"
Write-Host ""
