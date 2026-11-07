# 停止本机实例。
$ErrorActionPreference = 'SilentlyContinue'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pidFile = Join-Path $repo 'logs\jairouter.pid'

if (-not (Test-Path $pidFile)) {
    Write-Host "没有 pid 文件，进程可能没在跑。" -ForegroundColor Yellow
    exit 0
}

$target = Get-Content $pidFile | Select-Object -First 1
$proc = Get-Process -Id $target -ErrorAction SilentlyContinue
if (-not $proc) {
    Write-Host "PID $target 已不在运行。" -ForegroundColor Yellow
    Remove-Item $pidFile -Force
    exit 0
}

Stop-Process -Id $target -Force
Remove-Item $pidFile -Force
Write-Host "已停止 PID $target。" -ForegroundColor Green
