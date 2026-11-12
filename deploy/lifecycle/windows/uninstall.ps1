<#
.SYNOPSIS
    卸载 JAiRouter（Windows）。

.DESCRIPTION
    默认只删「程序」：服务注册 / 计划任务、jar、WinSW exe 与 xml。数据、日志、密钥保留。
    加 -Purge 才连数据与密钥一起删（二次确认）。

    刻意不做：不 DROP 数据库表、不清 Redis 键、不删镜像 —— 那些是共享资源，
    一个卸载脚本无权决定它们的去留，只会把该看的命令打印出来。

.EXAMPLE
    .\uninstall.ps1
    .\uninstall.ps1 -Prefix D:\JAiRouter -Purge
#>
[CmdletBinding()]
param(
    [string]$Prefix = 'C:\JAiRouter',
    [ValidateSet('auto', 'service', 'task', 'foreground')]
    [string]$Mode = 'auto',
    [int]$StopTimeoutSeconds = 45,
    [switch]$Purge,
    [switch]$Yes
)

$ErrorActionPreference = 'Stop'
$ServiceName = 'jairouter'

function Write-Step([string]$Message) { Write-Host "[uninstall] $Message" }
function Write-Warn([string]$Message) { Write-Host "[uninstall] WARN: $Message" -ForegroundColor Yellow }
function Fail([string]$Message) { Write-Host "[uninstall] ERROR: $Message" -ForegroundColor Red; exit 1 }

function Confirm-Step([string]$Question) {
    if ($Yes) { return $true }
    $answer = Read-Host "$Question [y/N]"
    return ($answer -eq 'y' -or $answer -eq 'Y')
}

$installRecord = Join-Path $Prefix 'jairouter.json'
$record = $null
if (Test-Path $installRecord) {
    try { $record = Get-Content $installRecord -Raw | ConvertFrom-Json } catch { }
}

if ($Mode -eq 'auto') {
    if ($record -and $record.service -and $record.service.kind -ne 'none') {
        $Mode = if ($record.service.kind -eq 'winsw') { 'service' } else { 'task' }
    } elseif (Test-Path (Join-Path $Prefix "$ServiceName.exe")) {
        $Mode = 'service'
    } elseif (Test-Path (Join-Path $Prefix 'jairouter.pid')) {
        $Mode = 'foreground'
    } else {
        $Mode = 'service'
        Write-Warn '没找到安装记录，按服务模式尝试清理（没装过的话下面的步骤都会安全跳过）'
    }
}
Write-Step "卸载模式: $Mode"

$serviceExe = Join-Path $Prefix "$ServiceName.exe"

# --- 停掉并移除托管 ---------------------------------------------------------

switch ($Mode) {
    'service' {
        if (Test-Path $serviceExe) {
            Write-Step '停止服务（SCM 会转发 CTRL_BREAK，走 Spring 优雅停机）'
            & $serviceExe stop 2>&1 | Out-Null
            $deadline = (Get-Date).AddSeconds($StopTimeoutSeconds)
            while ((Get-Date) -lt $deadline) {
                $status = & $serviceExe status 2>&1 | Out-String
                if ($status -notmatch 'Running') { break }
                Start-Sleep -Milliseconds 500
            }
            if ((& $serviceExe status 2>&1 | Out-String) -match 'Running') {
                Write-Warn "${StopTimeoutSeconds}s 内没停下来，强制终止 —— 在途请求已中断"
            }
            & $serviceExe uninstall 2>&1 | Out-Null
            Write-Step '已移除服务注册'
        } else {
            Write-Warn "没有找到 $serviceExe，跳过服务清理"
        }
    }
    'task' {
        $existing = & schtasks /query /tn 'JAiRouter' 2>&1
        if ($LASTEXITCODE -eq 0) {
            & schtasks /end /tn 'JAiRouter' 2>&1 | Out-Null
            & schtasks /delete /tn 'JAiRouter' /f 2>&1 | Out-Null
            Write-Step '已删除计划任务 JAiRouter'
        } else {
            Write-Warn '没有找到计划任务 JAiRouter'
        }
    }
    'foreground' {
        $pidFile = Join-Path $Prefix 'jairouter.pid'
        if (Test-Path $pidFile) {
            $target = [int](Get-Content $pidFile)
            $proc = Get-Process -Id $target -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Step "结束进程 $target"
                Stop-Process -Id $target -Force
            } else {
                Write-Warn "pid 文件里的 $target 已不在运行"
            }
            Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        } else {
            Write-Warn '没有找到 jairouter.pid'
        }
    }
}

# --- 删程序文件 -------------------------------------------------------------

$programFiles = @(
    (Join-Path $Prefix 'lib\model-router.jar'),
    (Join-Path $Prefix "$ServiceName.exe"),
    (Join-Path $Prefix "$ServiceName.xml")
)
foreach ($file in $programFiles) {
    if (Test-Path $file) { Remove-Item $file -Force; Write-Step "已删除 $file" }
}

$libDir = Join-Path $Prefix 'lib'
if ((Test-Path $libDir) -and -not (Get-ChildItem $libDir)) { Remove-Item $libDir -Force }

# --- 数据去留 ---------------------------------------------------------------

$dataDir = Join-Path $Prefix 'data'
$logsDir = Join-Path $Prefix 'logs'
$configDir = Join-Path $Prefix 'config'

if ($Purge) {
    Write-Warn '即将删除数据与密钥，这一步不可恢复'
    Write-Step "  数据目录: $dataDir"
    Write-Step "  日志目录: $logsDir"
    Write-Step "  配置与密钥: $configDir"
    if (-not (Confirm-Step '确认继续？')) { Fail '已取消，未删除任何数据' }
    foreach ($dir in @($dataDir, $logsDir, $configDir)) {
        if (Test-Path $dir) { Remove-Item $dir -Recurse -Force; Write-Step "已删除 $dir" }
    }
    Remove-Item $installRecord -Force -ErrorAction SilentlyContinue
} else {
    Write-Step '以下内容被保留（远程状态脚本无权处置）：'
    Write-Step '  - PostgreSQL 里的库与表：卸载不会 DROP，需要人工清理'
    Write-Step '  - Redis 里的键：jairouter:* 会随 TTL 过期，但不会立即消失'
    Write-Step '  - 容器镜像：别的部署可能还在用同一个 tag'
    if (Test-Path $dataDir) { Write-Step "  - 本地数据目录: $dataDir（需要 -Purge 才会删）" }
    if (Test-Path $configDir) { Write-Step "  - 配置与密钥: $configDir（需要 -Purge 才会删）" }
}

Write-Step '卸载完成'
