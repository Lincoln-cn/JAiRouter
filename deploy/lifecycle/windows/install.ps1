<#
.SYNOPSIS
    安装 JAiRouter（Windows）。

.DESCRIPTION
    与 linux/install.sh 对齐的三件事：
      1. 装前检测，不过就不落文件；
      2. 托管形态降级链，每次降级打印原因；
      3. 装完真去请求 /actuator/health，不是看到进程在就算成功。

    停机语义（v3.8.6 实测，这决定了为什么默认选服务而不是计划任务）：
      - WinSW 服务 : sc stop → 转发 CTRL_BREAK → Spring 优雅停机（1.43s）
      - 计划任务   : 没有控制台宿主，只能 taskkill /F，在途请求直接断
      - 前台运行   : 可 attach 控制台，按 Ctrl+C 能优雅停
      - taskkill /PID（不带 /F）: 看似成功，实测 shutdown hook 一行都不跑，等价硬杀

.EXAMPLE
    .\install.ps1 -Jar .\model-router.jar -WinSwPath .\WinSW.NET4.exe
    .\install.ps1 -Jar .\model-router.jar -Mode task -Port 9900
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Jar,

    [string]$Prefix = 'C:\JAiRouter',
    [int]$Port = 9900,
    [ValidateSet('auto', 'service', 'task', 'foreground')]
    [string]$Mode = 'auto',
    [string]$WinSwPath = '',
    [string]$Profile = 'prod',
    [int]$HealthTimeoutSeconds = 90,
    [switch]$SkipStart,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$ToolingVersion = '0.1.0'
$ServiceName = 'jairouter'

function Write-Step([string]$Message) { Write-Host "[install] $Message" }
function Write-Warn([string]$Message) { Write-Host "[install] WARN: $Message" -ForegroundColor Yellow }
function Fail([string]$Message) { Write-Host "[install] ERROR: $Message" -ForegroundColor Red; exit 1 }

function Test-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-JavaMajorVersion {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) { return $null }
    $lines = & java -version 2>&1 | Out-String
    if ($lines -match 'version "([^"]+)"') {
        $raw = $Matches[1]
        $major = $raw.Split('.')[0]
        if ($major -eq '1') { $major = $raw.Split('.')[1] }
        if ($major -match '^\d+$') { return [int]$major }
    }
    return $null
}

function New-RandomSecret([int]$Bytes) {
    $buffer = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    return [Convert]::ToBase64String($buffer)
}

function Wait-Health([int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $uri = "http://127.0.0.1:$Port/actuator/health"
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 3
            if ($resp.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

# --- 装前检测 ---------------------------------------------------------------

if (-not (Test-Path $Jar)) { Fail "jar 不存在: $Jar" }
$Jar = (Resolve-Path $Jar).Path

$javaMajor = Get-JavaMajorVersion
if ($null -eq $javaMajor) { Fail 'PATH 里没有可用的 java（或者版本无法解析）' }
if ($javaMajor -lt 17) { Fail "java 版本过低: $javaMajor < 17" }
$javaBin = (Get-Command java).Source
Write-Step "检测到 java $javaMajor（$javaBin）"

$isAdmin = Test-Administrator
Write-Step "管理员权限: $isAdmin"

$winsw = $null
if ($WinSwPath -ne '' -and (Test-Path $WinSwPath)) {
    $winsw = (Resolve-Path $WinSwPath).Path
    Write-Step "WinSW: $winsw"
} else {
    foreach ($candidate in @('WinSW.NET4.exe', 'WinSW.NET6.exe', 'winsw.exe')) {
        $path = Join-Path $PSScriptRoot $candidate
        if (Test-Path $path) { $winsw = $path; break }
    }
    if ($winsw) { Write-Step "WinSW: $winsw" }
}

$listener = $null
try { $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop } catch { }
if ($listener) { Fail "端口 $Port 已被占用（PID $($listener[0].OwningProcess)）" }

# --- 模式决策 ---------------------------------------------------------------

if ($Mode -eq 'auto') {
    if ($isAdmin -and $winsw) {
        $Mode = 'service'
        Write-Step '选择服务托管（管理员 + WinSW 均可用）'
    } elseif ($isAdmin) {
        $Mode = 'task'
        Write-Warn '降级到计划任务：没有提供 WinSW（-WinSwPath），而计划任务没有控制台宿主，停机只能是强制终止'
    } elseif ($winsw) {
        $Mode = 'foreground'
        Write-Warn '降级到前台运行：当前不是管理员，无法注册服务（安装时请「以管理员身份运行」）'
    } else {
        $Mode = 'foreground'
        Write-Warn '降级到前台运行：既没有管理员权限也没有 WinSW —— 进程不会随开机自启'
    }
} elseif ($Mode -eq 'service' -and (-not $isAdmin -or -not $winsw)) {
    Fail '-Mode service 需要管理员权限且提供 -WinSwPath'
}

Write-Step "模式: $Mode"

if ($DryRun) {
    Write-Step 'DryRun：检测与计划都通过了，未写入任何文件'
    exit 0
}

# --- 落文件 -----------------------------------------------------------------

$libDir = Join-Path $Prefix 'lib'
$configDir = Join-Path $Prefix 'config'
$dataDir = Join-Path $Prefix 'data'
$logsDir = Join-Path $Prefix 'logs'
foreach ($dir in @($libDir, $configDir, $dataDir, $logsDir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
}

$jarDest = Join-Path $libDir 'model-router.jar'
Copy-Item -Path $Jar -Destination $jarDest -Force
Write-Step "已复制 jar → $jarDest"

# 密钥只在第一次生成：重复执行不覆盖，避免把已投入使用的密钥换掉。
$envFile = Join-Path $configDir 'jairouter.env'
if (-not (Test-Path $envFile)) {
    $jwtSecret = New-RandomSecret 48
    $adminPassword = New-RandomSecret 24
    @(
        "# JAiRouter 运行时环境（install.ps1 生成，$ToolingVersion）",
        '# 含密钥，权限已收紧为 SYSTEM + Administrators，不要提交到任何仓库。',
        "JWT_SECRET=$jwtSecret",
        "INITIAL_ADMIN_PASSWORD=$adminPassword",
        'JAVA_OPTS=-Xms512m -Xmx2g -XX:+UseG1GC'
    ) | Set-Content -Path $envFile -Encoding UTF8
    Write-Step "已生成运行时密钥（$envFile）"
} else {
    Write-Step "环境文件已存在，保留原密钥（$envFile）"
}
$envValues = @{}
Get-Content $envFile | Where-Object { $_ -match '^([A-Z_]+)=(.*)$' } | ForEach-Object {
    $envValues[$Matches[1]] = $Matches[2]
}

$appYml = Join-Path $configDir 'application.yml'
if (-not (Test-Path $appYml)) {
    @(
        "# install.ps1 生成的覆盖层（$ToolingVersion）：后加载者优先，只写与默认值不同的键。",
        'server:',
        "  port: $Port",
        'logging:',
        '  file:',
        "    name: $(($dataDir -replace '\\','/'))/../logs/jairouter.log"
    ) | Set-Content -Path $appYml -Encoding UTF8
    Write-Step "已生成配置覆盖层（$appYml）"
}

# 收紧密钥文件权限：只留 SYSTEM 与 Administrators。
try {
    & icacls $envFile /inheritance:r /grant:r 'SYSTEM:(F)' 'BUILTIN\Administrators:(F)' | Out-Null
} catch {
    Write-Warn "收紧 $envFile 权限失败：$($_.Exception.Message)"
}

$installRecord = Join-Path $Prefix 'jairouter.json'
@{
    schemaVersion = 1
    toolingVersion = $ToolingVersion
    mode = $Mode
    platform = 'windows'
    prefix = $Prefix
    port = $Port
    profile = $Profile
    javaBin = $javaBin
    jar = $jarDest
    workingDirectory = $Prefix
    logDirectory = $logsDir
    dataDirectory = $dataDir
    envFile = $envFile
    service = @{ kind = $(if ($Mode -eq 'service') { 'winsw' } elseif ($Mode -eq 'task') { 'schtasks' } else { 'none' }); name = $ServiceName }
} | ConvertTo-Json -Depth 4 | Set-Content -Path $installRecord -Encoding UTF8
Write-Step "已写入安装记录（$installRecord）"

# --- 注册托管 ---------------------------------------------------------------

if ($Mode -eq 'service') {
    $serviceExe = Join-Path $Prefix "$ServiceName.exe"
    Copy-Item -Path $winsw -Destination $serviceExe -Force

    $envBlock = ($envValues.GetEnumerator() | ForEach-Object {
        "  <env name=`"$($_.Key)`" value=`"$([Security.SecurityElement]::Escape($_.Value))`"/>"
    }) -join "`r`n"

    # 45s > Spring 的 30s 优雅停机窗口，与 linux unit 的 TimeoutStopSec=40 同一个理由。
    $stopTimeout = 45
    $template = Get-Content (Join-Path $PSScriptRoot 'jairouter-service.xml.template') -Raw
    $rendered = $template.
        Replace('@@JAVA_BIN@@', [Security.SecurityElement]::Escape($javaBin)).
        Replace('@@PREFIX@@', [Security.SecurityElement]::Escape($Prefix)).
        Replace('@@PROFILE@@', $Profile).
        Replace('@@PORT@@', "$Port").
        Replace('@@STOP_TIMEOUT_SEC@@', "$stopTimeout").
        Replace('@@ENV_BLOCK@@', $envBlock)
    $rendered | Set-Content -Path (Join-Path $Prefix "$ServiceName.xml") -Encoding UTF8
    Write-Step "已渲染服务定义（$(Join-Path $Prefix "$ServiceName.xml")）"

    if (& $serviceExe status 2>&1 | Select-String -Quiet 'Installed|Running') {
        Write-Step '服务已存在，先卸载旧定义'
        & $serviceExe stop 2>&1 | Out-Null
        & $serviceExe uninstall 2>&1 | Out-Null
        Start-Sleep -Seconds 2
    }
    & $serviceExe install | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'WinSW 注册服务失败（看上面的输出）' }
} elseif ($Mode -eq 'task') {
    $action = "`"$javaBin`" -jar `"$jarDest`" --spring.profiles.active=$Profile --spring.config.additional-location=file:$configDir\"
    & schtasks /create /tn 'JAiRouter' /tr $action /sc onstart /ru 'SYSTEM' /rl HIGHEST /f | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Warn '计划任务创建失败（可能是权限或策略限制），回落到前台运行' ; $Mode = 'foreground' }
}

if ($SkipStart) {
    Write-Step '-SkipStart：文件与托管定义已就位，未启动'
    exit 0
}

# --- 启动与自检 -------------------------------------------------------------

$started = $false
if ($Mode -eq 'service') {
    & (Join-Path $Prefix "$ServiceName.exe") start | Out-Null
    $started = $true
} elseif ($Mode -eq 'task') {
    & schtasks /run /tn 'JAiRouter' | Out-Null
    $started = $true
} else {
    $argList = @('-jar', $jarDest, "--spring.profiles.active=$Profile", "--spring.config.additional-location=file:$configDir\")
    foreach ($entry in $envValues.GetEnumerator()) { Set-Item -Path "env:$($entry.Key)" -Value $entry.Value }
    $proc = Start-Process -FilePath $javaBin -ArgumentList $argList -WorkingDirectory $Prefix -PassThru -WindowStyle Hidden -Environment $envValues
    Set-Content -Path (Join-Path $Prefix 'jairouter.pid') -Value $proc.Id -Encoding ASCII
    Write-Step "已后台启动（PID $($proc.Id)）—— 前台模式不会随开机自启"
    $started = $true
}

if (-not $started) { Fail '没有任何托管形态启动成功' }

Write-Step "等待 http://127.0.0.1:$Port/actuator/health 就绪（最长 ${HealthTimeoutSeconds}s）"
if (Wait-Health -TimeoutSeconds $HealthTimeoutSeconds) {
    Write-Step '健康检查通过'
    Write-Step "安装完成：模式 $Mode，端口 $Port，数据 $dataDir"
    exit 0
}

Write-Warn "启动后 ${HealthTimeoutSeconds}s 内健康检查没通过，开始回滚"
if ($Mode -eq 'service') {
    & (Join-Path $Prefix "$ServiceName.exe") stop 2>&1 | Out-Null
    & (Join-Path $Prefix "$ServiceName.exe") uninstall 2>&1 | Out-Null
} elseif ($Mode -eq 'task') {
    & schtasks /delete /tn 'JAiRouter' /f 2>&1 | Out-Null
} else {
    $pidFile = Join-Path $Prefix 'jairouter.pid'
    if (Test-Path $pidFile) { Stop-Process -Id (Get-Content $pidFile) -Force -ErrorAction SilentlyContinue }
}
Fail '安装未完成，已回滚（数据目录与密钥都保留）。排查后可用同一命令重试'
