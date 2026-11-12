# 本机启动脚本：跑 jar，带持久化配置。
# 用法：.\deploy\local\start.ps1 [-Jar <path>] [-Port <port>]

param(
    [string]$Jar = "",
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Jar) {
    $Jar = Get-ChildItem -Path (Join-Path $repo 'target') -Filter 'model-router-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'original|sources|javadoc' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $Jar -or -not (Test-Path $Jar)) {
    Write-Error "找不到 jar。先执行：.\mvnw.cmd -q package -Dmaven.test.skip=true '-Dfrontend.build.skip=true'"
}

$cfg = Join-Path $PSScriptRoot 'application-local.yml'
if (-not (Test-Path (Join-Path $repo 'data'))) { New-Item -ItemType Directory -Path (Join-Path $repo 'data') | Out-Null }
if (-not (Test-Path (Join-Path $repo 'logs'))) { New-Item -ItemType Directory -Path (Join-Path $repo 'logs') | Out-Null }

Write-Host "启动: $Jar" -ForegroundColor Cyan
Write-Host "配置: $cfg" -ForegroundColor Cyan
Write-Host "端口: $Port" -ForegroundColor Cyan

$args = @(
    '-jar', $Jar,
    "--spring.config.additional-location=file:$cfg",
    "--server.port=$Port"
)
# 刻意不重定向 stdout/stderr：Windows 上没有 SIGTERM，stop.ps1 只能靠"向进程所在控制台
# 发送 Ctrl+C"触发 JVM shutdown hook。带重定向启动时子进程是否仍有控制台取决于启动方式，
# 一旦没有，优雅停机就退化成强制终止。保持子进程拥有自己的（隐藏）控制台是这条路径成立的前提；
# 运行日志本来就在 logs\jairouter-*.log（logback 固定写 <工作目录>\logs），不依赖标准流。
$proc = Start-Process -FilePath 'java' -ArgumentList $args -WorkingDirectory $repo -PassThru -WindowStyle Hidden

Set-Content -Path (Join-Path $repo 'logs\jairouter.pid') -Value $proc.Id
Write-Host "已启动，PID=$($proc.Id)。等待就绪（首次启动约 60 秒）…" -ForegroundColor Yellow

for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/actuator/health" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) {
            Write-Host "就绪：http://127.0.0.1:$Port/admin/ （健康检查 200）" -ForegroundColor Green
            exit 0
        }
    } catch { }
}
Write-Host "120 秒内未就绪，看 logs\stdout.log。" -ForegroundColor Red
exit 1
