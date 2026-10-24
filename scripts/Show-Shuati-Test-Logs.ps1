$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendLog = Join-Path $root ".local-run\backend.log"
$paymentLog = Join-Path $root ".local-run\payment.log"

while ($true) {
  Clear-Host
  Write-Host "拾题测试环境日志（Ctrl+C 关闭窗口不会停止服务）" -ForegroundColor Green
  Write-Host "这里只显示模拟邮件验证码、关键后端错误和支付侧车日志。"
  Write-Host ""

  Write-Host "===== 模拟邮件验证码 / 后端错误 =====" -ForegroundColor DarkGray
  if (Test-Path $backendLog) {
    $mailLines = Get-Content -LiteralPath $backendLog -Tail 300 -ErrorAction SilentlyContinue |
      Where-Object { $_ -match "to=.*purpose=.*code=" } |
      Select-Object -Last 15
    $errorLines = Get-Content -LiteralPath $backendLog -Tail 300 -ErrorAction SilentlyContinue |
      Where-Object { $_ -match "\sERROR\s" -and $_ -notmatch "ExplanationSaveConsumer" } |
      Select-Object -Last 5
    @($errorLines) + @($mailLines) | Where-Object { $_ } | ForEach-Object { Write-Host $_ }
  }

  Write-Host ""
  Write-Host "===== 支付侧车 =====" -ForegroundColor DarkGray
  if (Test-Path $paymentLog) {
    Get-Content -LiteralPath $paymentLog -Tail 12 -ErrorAction SilentlyContinue
  }
  Start-Sleep -Seconds 2
}
