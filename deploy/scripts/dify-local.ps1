param(
  [ValidateSet("start", "stop", "status")]
  [string]$Action = "status"
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
  Write-Host "[Dify] $Message" -ForegroundColor Red
  exit 1
}

function Resolve-DifyDockerDir {
  $candidates = @()
  if ($env:DIFY_HOME) {
    $candidates += $env:DIFY_HOME
    $candidates += Join-Path $env:DIFY_HOME "docker"
  }
  $candidates += "D:\dify\dify\docker"
  $candidates += "D:\dify\docker"

  foreach ($candidate in $candidates | Select-Object -Unique) {
    if (Test-Path (Join-Path $candidate "docker-compose.yaml")) {
      return (Resolve-Path $candidate).Path
    }
  }
  Fail "未找到 Dify docker-compose.yaml。请设置 DIFY_HOME 指向 Dify 根目录或 docker 目录。"
}

function Wait-Docker([int]$TimeoutSeconds = 180) {
  $desktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
  docker info *> $null
  if ($LASTEXITCODE -ne 0) {
    if (-not (Test-Path $desktop)) {
      Fail "Docker Desktop 未安装或路径不正确。"
    }
    if (-not (Get-Process "Docker Desktop" -ErrorAction SilentlyContinue)) {
      Start-Process -FilePath $desktop -WindowStyle Hidden
    }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
      Start-Sleep -Seconds 3
      docker info *> $null
      if ($LASTEXITCODE -eq 0) {
        return
      }
    } while ((Get-Date) -lt $deadline)
    Fail "Docker 引擎在 $TimeoutSeconds 秒内未就绪。"
  }
}

function Read-EnvMap([string]$Path) {
  $map = @{}
  Get-Content -LiteralPath $Path | ForEach-Object {
    if ($_ -match "^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$") {
      $map[$Matches[1]] = $Matches[2]
    }
  }
  return $map
}

function Repair-CeleryBroker([string]$EnvFile) {
  $values = Read-EnvMap $EnvFile
  $redisPassword = $values["REDIS_PASSWORD"]
  $broker = $values["CELERY_BROKER_URL"]
  if ([string]::IsNullOrWhiteSpace($redisPassword) -or [string]::IsNullOrWhiteSpace($broker)) {
    return $false
  }
  $brokerPassword = $null
  if ($broker -match "redis://[^:]*:([^@]*)@") {
    $brokerPassword = $Matches[1]
  }
  if ($brokerPassword -eq $redisPassword) {
    return $false
  }

  $lines = Get-Content -LiteralPath $EnvFile
  $updated = foreach ($line in $lines) {
    if ($line -match "^CELERY_BROKER_URL=") {
      "CELERY_BROKER_URL=redis://:$redisPassword@redis:6379/1"
    } else {
      $line
    }
  }
  [System.IO.File]::WriteAllLines(
    $EnvFile,
    $updated,
    [System.Text.UTF8Encoding]::new($false))
  Write-Host "[Dify] 已修正本地 Redis Broker 凭据，未输出密钥。" -ForegroundColor Yellow
  return $true
}

function Invoke-Compose([string]$DockerDir, [string[]]$Arguments) {
  $compose = Join-Path $DockerDir "docker-compose.yaml"
  $envFile = Join-Path $DockerDir ".env"
  Push-Location $DockerDir
  try {
    & docker compose -f $compose --env-file $envFile @Arguments
    if ($LASTEXITCODE -ne 0) {
      throw "docker compose 执行失败。"
    }
  } finally {
    Pop-Location
  }
}

function Wait-DifyHttp([int]$TimeoutSeconds = 120) {
  $url = "http://localhost:8501/console/api/setup"
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    try {
      $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
      if ($response.StatusCode -eq 200) {
        return
      }
    } catch {
      Start-Sleep -Seconds 2
    }
  } while ((Get-Date) -lt $deadline)
  Fail "Dify 控制台在 $TimeoutSeconds 秒内未就绪：$url"
}

function Show-Status([string]$DockerDir) {
  Wait-Docker
  $services = @(
    "api",
    "worker",
    "worker_beat",
    "web",
    "nginx",
    "db_postgres",
    "redis",
    "sandbox",
    "plugin_daemon"
  )
  $running = @(Invoke-Compose $DockerDir @("ps", "--services", "--status", "running"))
  $missing = $services | Where-Object { $_ -notin $running }
  if ($missing.Count -gt 0) {
    Fail "Dify 服务未全部运行：$($missing -join ', ')"
  }

  try {
    $response = Invoke-WebRequest -Uri "http://localhost:8501/console/api/setup" -UseBasicParsing -TimeoutSec 15
    if ($response.StatusCode -ne 200) {
      Fail "Dify 控制台返回 HTTP $($response.StatusCode)。"
    }
  } catch {
    Fail "Dify 控制台不可访问：$($_.Exception.Message)"
  }

  $workerErrors = docker logs --since 5m docker-worker-1 2>&1 |
    Select-String -Pattern "invalid username-password|Connection refused|Traceback"
  if ($workerErrors) {
    Fail "Dify Worker 最近 5 分钟存在连接错误。"
  }

  Write-Host "[Dify] 状态正常" -ForegroundColor Green
  Write-Host "  控制台：http://localhost:8501"
  Write-Host "  版本：1.13.3"
  Write-Host "  核心服务：$($services.Count) 个均运行"
  Write-Host "  Redis/Celery：正常"
}

$dockerDir = Resolve-DifyDockerDir
$envFile = Join-Path $dockerDir ".env"
if (-not (Test-Path $envFile)) {
  Fail "未找到 Dify .env：$envFile"
}

switch ($Action) {
  "start" {
    Wait-Docker
    $repaired = Repair-CeleryBroker $envFile
    Invoke-Compose $dockerDir @("up", "-d")
    Invoke-Compose $dockerDir @("restart", "nginx")
    Wait-DifyHttp
    Show-Status $dockerDir
    if ($repaired) {
      Write-Host "[Dify] Redis Broker 配置已持久化到本地 .env。" -ForegroundColor Yellow
    }
  }
  "stop" {
    Invoke-Compose $dockerDir @("stop")
    Write-Host "[Dify] 已停止，数据卷保留。" -ForegroundColor Green
  }
  "status" {
    Show-Status $dockerDir
  }
}
