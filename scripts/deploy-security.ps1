# JAiRouter 安全功能部署脚本 (PowerShell)
# 用于部署启用安全功能的 JAiRouter 实例

param(
    [switch]$Help,
    [string]$EnvFile = ".env.security",
    [switch]$Force,
    [switch]$Dev,
    [switch]$Stop,
    [switch]$Restart,
    [switch]$Logs,
    [switch]$EnableMonitoring,
    [switch]$CheckHealth
)

# 脚本配置
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"
$EnvFilePath = Join-Path $ProjectRoot $EnvFile

# 颜色输出函数
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = "White"
    )
    
    $colorMap = @{
        "Red" = "Red"
        "Green" = "Green"
        "Yellow" = "Yellow"
        "Blue" = "Blue"
        "White" = "White"
    }
    
    Write-Host $Message -ForegroundColor $colorMap[$Color]
}

function Log-Info {
    param([string]$Message)
    Write-ColorOutput "[INFO] $Message" "Blue"
}

function Log-Success {
    param([string]$Message)
    Write-ColorOutput "[SUCCESS] $Message" "Green"
}

function Log-Warning {
    param([string]$Message)
    Write-ColorOutput "[WARNING] $Message" "Yellow"
}

function Log-Error {
    param([string]$Message)
    Write-ColorOutput "[ERROR] $Message" "Red"
}

# 显示帮助信息
function Show-Help {
    @"
JAiRouter 安全功能部署脚本 (PowerShell)

用法: .\deploy-security.ps1 [选项]

选项:
    -Help                   显示此帮助信息
    -EnvFile FILE          指定环境变量文件 (默认: .env.security)
    -Force                 强制重新构建镜像
    -Dev                   使用开发环境配置
    -Stop                  停止服务
    -Restart               重启服务
    -Logs                  查看服务日志
    -EnableMonitoring      启用监控服务 (Prometheus + Grafana)
    -CheckHealth           检查服务健康状态

示例:
    .\deploy-security.ps1                    # 使用默认配置部署
    .\deploy-security.ps1 -Force             # 强制重新构建并部署
    .\deploy-security.ps1 -Dev               # 使用开发环境部署
    .\deploy-security.ps1 -EnableMonitoring  # 启用监控功能部署
    .\deploy-security.ps1 -Stop              # 停止所有服务
    .\deploy-security.ps1 -Logs              # 查看服务日志

"@
}

# 检查依赖
function Test-Dependencies {
    Log-Info "检查依赖..."
    
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Log-Error "Docker 未安装或不在 PATH 中"
        exit 1
    }
    
    $dockerComposeCmd = $null
    if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
        $dockerComposeCmd = "docker-compose"
    } elseif ((docker compose version 2>$null) -and ($LASTEXITCODE -eq 0)) {
        $dockerComposeCmd = "docker compose"
    } else {
        Log-Error "Docker Compose 未安装或不在 PATH 中"
        exit 1
    }
    
    Log-Success "依赖检查通过"
    return $dockerComposeCmd
}

# 创建默认环境变量文件
function New-DefaultEnvFile {
    if (-not (Test-Path $EnvFilePath)) {
        Log-Info "创建默认环境变量文件: $EnvFilePath"
        
        $envContent = @'
# JAiRouter 安全功能环境变量配置

# 安全功能总开关
JAIROUTER_SECURITY_ENABLED=true

# API Key 认证配置
JAIROUTER_SECURITY_API_KEY_ENABLED=true
ADMIN_API_KEY=your-admin-api-key-here
USER_API_KEY=your-user-api-key-here

# JWT 认证配置
JAIROUTER_SECURITY_JWT_ENABLED=false
JWT_SECRET=your-jwt-secret-key-here
JWT_ALGORITHM=HS256
JWT_EXPIRATION_MINUTES=60

# 数据脱敏配置
JAIROUTER_SECURITY_SANITIZATION_REQUEST_ENABLED=true
JAIROUTER_SECURITY_SANITIZATION_RESPONSE_ENABLED=true

# 审计配置
JAIROUTER_SECURITY_AUDIT_ENABLED=true

# Redis 缓存配置
REDIS_PASSWORD=your-redis-password-here
REDIS_DATABASE=0
REDIS_TIMEOUT=2000

# 其他配置
COMPOSE_PROJECT_NAME=jairouter-security
'@
        
        Set-Content -Path $EnvFilePath -Value $envContent -Encoding UTF8
        Log-Warning "请编辑 $EnvFilePath 文件，设置正确的安全配置参数"
        Log-Warning "特别注意设置强密码用于 ADMIN_API_KEY, USER_API_KEY, JWT_SECRET 和 REDIS_PASSWORD"
    }
}

# 读取环境变量文件
function Get-EnvVariables {
    $envVars = @{}
    
    if (Test-Path $EnvFilePath) {
        Get-Content $EnvFilePath | ForEach-Object {
            if ($_ -match '^([^#][^=]+)=(.*)$') {
                $envVars[$matches[1].Trim()] = $matches[2].Trim()
            }
        }
    }
    
    return $envVars
}

# 验证环境变量
function Test-EnvVariables {
    Log-Info "验证环境变量配置..."
    
    $envVars = Get-EnvVariables
    
    # 检查必要的环境变量
    $requiredVars = @(
        "JAIROUTER_SECURITY_ENABLED",
        "ADMIN_API_KEY",
        "USER_API_KEY"
    )
    
    $missingVars = @()
    foreach ($var in $requiredVars) {
        if (-not $envVars.ContainsKey($var) -or [string]::IsNullOrEmpty($envVars[$var])) {
            $missingVars += $var
        }
    }
    
    if ($missingVars.Count -gt 0) {
        Log-Error "以下必要的环境变量未设置:"
        foreach ($var in $missingVars) {
            Write-Host "  - $var"
        }
        exit 1
    }
    
    # 检查密钥强度
    if ($envVars["ADMIN_API_KEY"] -eq "your-admin-api-key-here" -or $envVars["ADMIN_API_KEY"].Length -lt 16) {
        Log-Error "ADMIN_API_KEY 必须设置为至少16位的强密钥"
        exit 1
    }
    
    if ($envVars["USER_API_KEY"] -eq "your-user-api-key-here" -or $envVars["USER_API_KEY"].Length -lt 16) {
        Log-Error "USER_API_KEY 必须设置为至少16位的强密钥"
        exit 1
    }
    
    if ($envVars["JAIROUTER_SECURITY_JWT_ENABLED"] -eq "true") {
        if ($envVars["JWT_SECRET"] -eq "your-jwt-secret-key-here" -or $envVars["JWT_SECRET"].Length -lt 32) {
            Log-Error "启用 JWT 时，JWT_SECRET 必须设置为至少32位的强密钥"
            exit 1
        }
    }
    
    Log-Success "环境变量验证通过"
}

# 构建镜像
function Build-Images {
    param([bool]$ForceBuild, [string]$DockerComposeCmd)
    
    Log-Info "构建 JAiRouter 镜像..."
    
    Push-Location $ProjectRoot
    try {
        if ($ForceBuild) {
            & $DockerComposeCmd.Split() -f $ComposeFile --env-file $EnvFilePath build --no-cache
        } else {
            & $DockerComposeCmd.Split() -f $ComposeFile --env-file $EnvFilePath build
        }
        
        if ($LASTEXITCODE -ne 0) {
            throw "镜像构建失败"
        }
        
        Log-Success "镜像构建完成"
    }
    finally {
        Pop-Location
    }
}

# 部署服务
function Deploy-Services {
    param([bool]$EnableMonitoring, [string]$DockerComposeCmd)
    
    Log-Info "部署 JAiRouter 安全功能服务..."
    
    Push-Location $ProjectRoot
    try {
        # 基础服务
        $services = @("jairouter", "redis")
        
        # 如果启用监控，添加监控服务
        if ($EnableMonitoring) {
            $services += @("prometheus", "grafana")
            $env:COMPOSE_PROFILES = "monitoring"
        }
        
        $serviceArgs = $services -join " "
        & $DockerComposeCmd.Split() -f $ComposeFile --env-file $EnvFilePath up -d $serviceArgs.Split()
        
        if ($LASTEXITCODE -ne 0) {
            throw "服务部署失败"
        }
        
        Log-Success "服务部署完成"
    }
    finally {
        Pop-Location
    }
}

# 停止服务
function Stop-Services {
    param([string]$DockerComposeCmd)
    
    Log-Info "停止 JAiRouter 服务..."
    
    Push-Location $ProjectRoot
    try {
        & $DockerComposeCmd.Split() -f $ComposeFile --env-file $EnvFilePath down
        
        if ($LASTEXITCODE -ne 0) {
            throw "停止服务失败"
        }
        
        Log-Success "服务已停止"
    }
    finally {
        Pop-Location
    }
}

# 重启服务
function Restart-Services {
    param([string]$DockerComposeCmd)
    
    Log-Info "重启 JAiRouter 服务..."
    
    Push-Location $ProjectRoot
    try {
        & $DockerComposeCmd.Split() -f $ComposeFile --env-file $EnvFilePath restart
        
        if ($LASTEXITCODE -ne 0) {
            throw "重启服务失败"
        }
        
        Log-Success "服务已重启"
    }
    finally {
        Pop-Location
    }
}

# 查看日志
function Show-Logs {
    param([string]$DockerComposeCmd)
    
    Log-Info "显示服务日志..."
    
    Push-Location $ProjectRoot
    try {
        & $DockerComposeCmd.Split() -f $ComposeFile --env-file $EnvFilePath logs -f
    }
    finally {
        Pop-Location
    }
}

# 检查健康状态
function Test-Health {
    param([string]$DockerComposeCmd)
    
    Log-Info "检查服务健康状态..."
    
    Push-Location $ProjectRoot
    try {
        # 检查容器状态
        $containers = & $DockerComposeCmd.Split() -f $ComposeFile --env-file $EnvFilePath ps -q
        
        if (-not $containers) {
            Log-Error "没有运行的容器"
            return
        }
        
        # 检查每个容器的健康状态
        foreach ($container in $containers) {
            $name = (docker inspect --format='{{.Name}}' $container).TrimStart('/')
            $health = docker inspect --format='{{.State.Health.Status}}' $container 2>$null
            if (-not $health) { $health = "no-healthcheck" }
            $status = docker inspect --format='{{.State.Status}}' $container
            
            if ($status -eq "running") {
                if ($health -eq "healthy" -or $health -eq "no-healthcheck") {
                    Log-Success "$name`: 运行正常"
                } else {
                    Log-Warning "$name`: 运行中但健康检查失败 ($health)"
                }
            } else {
                Log-Error "$name`: 未运行 ($status)"
            }
        }
        
        # 检查服务端点
        Log-Info "检查服务端点..."
        
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -TimeoutSec 5 -UseBasicParsing
            Log-Success "JAiRouter 健康检查端点可访问"
        } catch {
            Log-Error "JAiRouter 健康检查端点不可访问"
        }
        
        try {
            $tcpClient = New-Object System.Net.Sockets.TcpClient
            $tcpClient.ConnectAsync("localhost", 6379).Wait(5000)
            if ($tcpClient.Connected) {
                Log-Success "Redis 服务可访问"
                $tcpClient.Close()
            } else {
                Log-Error "Redis 服务不可访问"
            }
        } catch {
            Log-Error "Redis 服务不可访问"
        }
    }
    finally {
        Pop-Location
    }
}

# 主函数
function Main {
    # 显示帮助
    if ($Help) {
        Show-Help
        return
    }
    
    # 开发模式配置
    if ($Dev) {
        $script:ComposeFile = Join-Path $ProjectRoot "docker-compose.dev.yml"
        $script:EnvFilePath = Join-Path $ProjectRoot ".env.security.dev"
    }
    
    # 检查依赖
    $dockerComposeCmd = Test-Dependencies
    
    try {
        # 执行相应操作
        if ($Stop) {
            Stop-Services $dockerComposeCmd
        } elseif ($Restart) {
            Restart-Services $dockerComposeCmd
        } elseif ($Logs) {
            Show-Logs $dockerComposeCmd
        } elseif ($CheckHealth) {
            Test-Health $dockerComposeCmd
        } else {
            # 部署模式
            New-DefaultEnvFile
            Test-EnvVariables
            Build-Images $Force $dockerComposeCmd
            Deploy-Services $EnableMonitoring $dockerComposeCmd
            
            Log-Success "JAiRouter 安全功能部署完成!"
            Log-Info "服务访问地址:"
            Log-Info "  - JAiRouter API: http://localhost:8080"
            Log-Info "  - 健康检查: http://localhost:8080/actuator/health"
            Log-Info "  - API 文档: http://localhost:8080/swagger-ui/index.html"
            
            if ($EnableMonitoring) {
                Log-Info "  - Prometheus: http://localhost:9090"
                Log-Info "  - Grafana: http://localhost:3000 (admin/admin)"
            }
            
            Log-Info ""
            Log-Info "使用以下命令管理服务:"
            Log-Info "  - 查看日志: .\deploy-security.ps1 -Logs"
            Log-Info "  - 检查健康: .\deploy-security.ps1 -CheckHealth"
            Log-Info "  - 重启服务: .\deploy-security.ps1 -Restart"
            Log-Info "  - 停止服务: .\deploy-security.ps1 -Stop"
        }
    } catch {
        Log-Error $_.Exception.Message
        exit 1
    }
}

# 执行主函数
Main