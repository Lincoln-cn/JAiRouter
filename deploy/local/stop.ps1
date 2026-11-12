<#
.SYNOPSIS
    停止本机 JAiRouter 实例：优先优雅停机，超时才强制终止。

.DESCRIPTION
    Windows 没有 SIGTERM，`Stop-Process -Force` 走 TerminateProcess，相当于直接拔电源：
    JVM 不跑 shutdown hook，Spring 的 graceful shutdown（server.shutdown=graceful，
    spring.lifecycle.timeout-per-shutdown-phase=30s）完全不会执行，在途的流式请求被截断。

    本脚本改为向目标 JVM 所在的控制台发送 Ctrl+C（等价于用户在窗口里按 Ctrl+C），
    这是 Windows 上唯一的进程外优雅停机手段，实测能看到 "Commencing graceful shutdown"
    并完整执行 Spring 的关闭序列。

    只有两种情况才强制终止，并且都会明确打印原因，不做无声降级：
      1. 目标进程没有控制台可发信号（例如由 SCM / 计划任务托管）；
      2. 等待超过 $TimeoutSeconds 仍未退出（长连接或 shutdown hook 卡住）。

.PARAMETER TimeoutSeconds
    优雅停机最长等待秒数，默认 35（大于 spring.lifecycle.timeout-per-shutdown-phase 的 30s）。

.PARAMETER Force
    跳过优雅停机，直接强制终止（仅用于进程已卡死、Ctrl+C 无效的场景）。

.EXAMPLE
    .\deploy\local\stop.ps1

.EXAMPLE
    .\deploy\local\stop.ps1 -TimeoutSeconds 60
#>
param(
    [int]$TimeoutSeconds = 35,
    [switch]$Force
)

$ErrorActionPreference = 'SilentlyContinue'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pidFile = Join-Path $repo 'logs\jairouter.pid'

if (-not (Test-Path $pidFile)) {
    Write-Host '没有 pid 文件，进程可能没在跑。' -ForegroundColor Yellow
    exit 0
}

$targetPid = (Get-Content $pidFile | Select-Object -First 1).Trim()
$proc = Get-Process -Id $targetPid -ErrorAction SilentlyContinue
if (-not $proc) {
    Write-Host "PID $targetPid 已不在运行。" -ForegroundColor Yellow
    Remove-Item $pidFile -Force
    exit 0
}

function Stop-Hard {
    param([int]$ProcessId)
    Stop-Process -Id $ProcessId -Force
    Remove-Item $pidFile -Force
    Write-Host "已强制终止 PID $ProcessId：shutdown hook 未执行，在途请求已中断。" -ForegroundColor Red
}

if ($Force) {
    Write-Host '按 -Force 要求跳过优雅停机。' -ForegroundColor Yellow
    Stop-Hard -ProcessId $targetPid
    exit 0
}

$helper = Join-Path $PSScriptRoot 'send-ctrlc.ps1'
if (-not (Test-Path $helper)) {
    Write-Host "缺少辅助脚本 $helper，无法优雅停机，只能强制终止。" -ForegroundColor Yellow
    Stop-Hard -ProcessId $targetPid
    exit 1
}

$sentinel = Join-Path $env:TEMP ("jairouter-ctrlc-" + $targetPid + "-" + [guid]::NewGuid().ToString('N') + ".flag")
Write-Host "正在向 PID $targetPid 发送 Ctrl+C（优雅停机，最多等待 $TimeoutSeconds 秒）…"
# 用独立进程执行：发送者 attach 到目标控制台后自己也会收到该信号，不能在主进程里做。
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $helper -TargetPid $targetPid -Sentinel $sentinel | Out-Null
$signalSent = Test-Path $sentinel
Remove-Item $sentinel -Force

if (-not $signalSent) {
    Write-Host '目标进程没有可用的控制台，无法发送 Ctrl+C，只能强制终止。' -ForegroundColor Yellow
    Write-Host '（需要优雅停机的场景请用 WinSW 等 SCM 宿主托管，或前台运行后手动按 Ctrl+C。）' -ForegroundColor Yellow
    Stop-Hard -ProcessId $targetPid
    exit 1
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    if (-not (Get-Process -Id $targetPid -ErrorAction SilentlyContinue)) {
        Remove-Item $pidFile -Force
        Write-Host "已优雅停止 PID $targetPid。" -ForegroundColor Green
        exit 0
    }
    Start-Sleep -Milliseconds 500
}

Write-Host "等待 $TimeoutSeconds 秒仍未退出（可能有长连接或 shutdown hook 卡住）。" -ForegroundColor Yellow
Stop-Hard -ProcessId $targetPid
exit 1
