# JAiRouter 安全功能部署测试脚本
# 用于测试容器化部署的安全功能

param(
    [switch]$Help,
    [switch]$Quick,
    [switch]$Cleanup
)

# 脚本配置
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$TestEnvFile = Join-Path $ProjectRoot ".env.security.test"

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
JAiRouter 安全功能部署测试脚本

用法: .\test-security-deployment.ps1 [选项]

选项:
    -Help      显示此帮助信息
    -Quick     快速测试（跳过构建）
    -Cleanup   清理测试环境

示例:
    .\test-security-deployment.ps1        # 完整测试
    .\test-security-deployment.ps1 -Quick # 快速测试
    .\test-security-deployment.ps1 -Cleanup # 清理环境

"@
}

# 创建测试环境变量文件
function New-TestEnvFile {
    Log-Info "创建测试环境变量文件..."
    
    $testEnvContent = @'
# JAiRouter 安全功能测试环境变量

# 安全功能总开关
JAIROUTER_SECURITY_ENABLED=true

# API Key 认证配置
JAIROUTER_SECURITY_API_KEY_ENABLED=true
ADMIN_API_KEY=test-admin-key-1234567890abcdef
USER_API_KEY=test-user-key-abcdef1234567890

# JWT 认证配置
JAIROUTER_SECURITY_JWT_ENABLED=false
JWT_SECRET=test-jwt-secret-key-for-testing-only-1234567890abcdef
JWT_ALGORITHM=HS256
JWT_EXPIRATION_MINUTES=60

# 数据脱敏配置
JAIROUTER_SECURITY_SANITIZATION_REQUEST_ENABLED=true
JAIROUTER_SECURITY_SANITIZATION_RESPONSE_ENABLED=true

# 审计配置
JAIROUTER_SECURITY_AUDIT_ENABLED=true

# Redis 缓存配置
REDIS_PASSWORD=test-redis-password-123
REDIS_DATABASE=0
REDIS_TIMEOUT=2000

# 测试配置
COMPOSE_PROJECT_NAME=jairouter-security-test
'@
    
    Set-Content -Path $TestEnvFile -Value $testEnvContent -Encoding UTF8
    Log-Success "测试环境变量文件创建完成"
}

# 检查 Docker 环境
function Test-DockerEnvironment {
    Log-Info "检查 Docker 环境..."
    
    # 检查 Docker 是否运行
    try {
        $dockerVersion = docker version --format '{{.Server.Version}}' 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "Docker 服务未运行"
        }
        Log-Success "Docker 服务运行正常 (版本: $dockerVersion)"
    } catch {
        Log-Error "Docker 服务检查失败: $($_.Exception.Message)"
        return $false
    }
    
    # 检查 Docker Compose
    $dockerComposeCmd = $null
    if (Get-Command docker-compose -ErrorAction SilentlyContinue) {
        $dockerComposeCmd = "docker-compose"
    } elseif ((docker compose version 2>$null) -and ($LASTEXITCODE -eq 0)) {
        $dockerComposeCmd = "docker compose"
    } else {
        Log-Error "Docker Compose 未安装"
        return $false
    }
    
    Log-Success "Docker Compose 可用: $dockerComposeCmd"
    return $dockerComposeCmd
}

# 检查镜像是否存在
function Test-DockerImageExists {
    param(
        [string]$ImageName
    )
    
    $imageExists = docker images sodlinken/jairouter:latest -q
    return [bool]$imageExists
}

# 构建测试镜像
function Build-TestImages {
    param([string]$DockerComposeCmd)
    
    Log-Info "构建测试镜像..."
    
    Push-Location $ProjectRoot
    try {
        # 检查是否需要构建
        $imageExists = docker images sodlinken/jairouter:latest -q
        if ($imageExists) {
            Log-Info "镜像已存在，跳过构建"
            return $true
        }
        
        & $DockerComposeCmd.Split() -f docker-compose.yml --env-file $TestEnvFile build --quiet
        
        if ($LASTEXITCODE -ne 0) {
            Log-Error "镜像构建失败"
            return $false
        }
        
        Log-Success "测试镜像构建完成"
        return $true
    }
    finally {
        Pop-Location
    }
}

# 启动测试服务
function Start-TestServices {
    param([string]$DockerComposeCmd)
    
    Log-Info "启动测试服务..."
    
    Push-Location $ProjectRoot
    try {
        # 启动基础服务
        & $DockerComposeCmd.Split() -f docker-compose.yml --env-file $TestEnvFile up -d jairouter redis
        
        if ($LASTEXITCODE -ne 0) {
            Log-Error "服务启动失败"
            return $false
        }
        
        # 等待服务启动
        Log-Info "等待服务启动..."
        Start-Sleep -Seconds 30
        
        Log-Success "测试服务启动完成"
        return $true
    }
    finally {
        Pop-Location
    }
}

# 测试服务健康状态
function Test-ServiceHealth {
    Log-Info "测试服务健康状态..."
    
    $healthTests = @()
    
    # 测试 JAiRouter 健康检查
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -TimeoutSec 10 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            $healthTests += @{ Name = "JAiRouter 健康检查"; Status = "通过" }
        } else {
            $healthTests += @{ Name = "JAiRouter 健康检查"; Status = "失败 (HTTP $($response.StatusCode))" }
        }
    } catch {
        $healthTests += @{ Name = "JAiRouter 健康检查"; Status = "失败 ($($_.Exception.Message))" }
    }
    
    # 测试 Redis 连接
    try {
        $tcpClient = New-Object System.Net.Sockets.TcpClient
        $connectTask = $tcpClient.ConnectAsync("localhost", 6379)
        $connectTask.Wait(5000)
        if ($tcpClient.Connected) {
            $healthTests += @{ Name = "Redis 连接"; Status = "通过" }
            $tcpClient.Close()
        } else {
            $healthTests += @{ Name = "Redis 连接"; Status = "失败 (连接超时)" }
        }
    } catch {
        $healthTests += @{ Name = "Redis 连接"; Status = "失败 ($($_.Exception.Message))" }
    }
    
    # 显示测试结果
    Log-Info "健康检查结果:"
    foreach ($test in $healthTests) {
        if ($test.Status -eq "通过") {
            Log-Success "  ✓ $($test.Name): $($test.Status)"
        } else {
            Log-Error "  ✗ $($test.Name): $($test.Status)"
        }
    }
    
    $passedTests = ($healthTests | Where-Object { $_.Status -eq "通过" }).Count
    $totalTests = $healthTests.Count
    
    if ($passedTests -eq $totalTests) {
        Log-Success "所有健康检查通过 ($passedTests/$totalTests)"
        return $true
    } else {
        Log-Warning "部分健康检查失败 ($passedTests/$totalTests)"
        return $false
    }
}

# 测试安全功能
function Test-SecurityFeatures {
    Log-Info "测试安全功能..."
    
    $securityTests = @()
    
    # 测试无 API Key 访问（应该被拒绝）
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/v1/models" -TimeoutSec 10 -UseBasicParsing
        $securityTests += @{ Name = "无 API Key 访问控制"; Status = "失败 (应该被拒绝但通过了)" }
    } catch {
        if ($_.Exception.Response.StatusCode -eq 401) {
            $securityTests += @{ Name = "无 API Key 访问控制"; Status = "通过" }
        } else {
            $securityTests += @{ Name = "无 API Key 访问控制"; Status = "失败 ($($_.Exception.Message))" }
        }
    }
    
    # 测试有效 API Key 访问
    try {
        $headers = @{ "X-API-Key" = "test-admin-key-1234567890abcdef" }
        $response = Invoke-WebRequest -Uri "http://localhost:8080/v1/models" -Headers $headers -TimeoutSec 10 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            $securityTests += @{ Name = "有效 API Key 访问"; Status = "通过" }
        } else {
            $securityTests += @{ Name = "有效 API Key 访问"; Status = "失败 (HTTP $($response.StatusCode))" }
        }
    } catch {
        $securityTests += @{ Name = "有效 API Key 访问"; Status = "失败 ($($_.Exception.Message))" }
    }
    
    # 测试无效 API Key 访问
    try {
        $headers = @{ "X-API-Key" = "invalid-api-key" }
        $response = Invoke-WebRequest -Uri "http://localhost:8080/v1/models" -Headers $headers -TimeoutSec 10 -UseBasicParsing
        $securityTests += @{ Name = "无效 API Key 访问控制"; Status = "失败 (应该被拒绝但通过了)" }
    } catch {
        if ($_.Exception.Response.StatusCode -eq 401) {
            $securityTests += @{ Name = "无效 API Key 访问控制"; Status = "通过" }
        } else {
            $securityTests += @{ Name = "无效 API Key 访问控制"; Status = "失败 ($($_.Exception.Message))" }
        }
    }
    
    # 显示测试结果
    Log-Info "安全功能测试结果:"
    foreach ($test in $securityTests) {
        if ($test.Status -eq "通过") {
            Log-Success "  ✓ $($test.Name): $($test.Status)"
        } else {
            Log-Error "  ✗ $($test.Name): $($test.Status)"
        }
    }
    
    $passedTests = ($securityTests | Where-Object { $_.Status -eq "通过" }).Count
    $totalTests = $securityTests.Count
    
    if ($passedTests -eq $totalTests) {
        Log-Success "所有安全功能测试通过 ($passedTests/$totalTests)"
        return $true
    } else {
        Log-Warning "部分安全功能测试失败 ($passedTests/$totalTests)"
        return $false
    }
}

# 清理测试环境
function Clear-TestEnvironment {
    param([string]$DockerComposeCmd)
    
    Log-Info "清理测试环境..."
    
    Push-Location $ProjectRoot
    try {
        # 停止并删除容器
        & $DockerComposeCmd.Split() -f docker-compose.yml --env-file $TestEnvFile down -v --remove-orphans
        
        # 删除测试环境变量文件
        if (Test-Path $TestEnvFile) {
            Remove-Item $TestEnvFile -Force
        }
        
        Log-Success "测试环境清理完成"
    }
    finally {
        Pop-Location
    }
}

# 主函数
function Main {
    if ($Help) {
        Show-Help
        return
    }
    
    # 清理模式
    if ($Cleanup) {
        $dockerComposeCmd = Test-DockerEnvironment
        if ($dockerComposeCmd) {
            Clear-TestEnvironment $dockerComposeCmd
        }
        return
    }
    
    Log-Info "开始 JAiRouter 安全功能部署测试..."
    
    try {
        # 检查 Docker 环境
        $dockerComposeCmd = Test-DockerEnvironment
        if (-not $dockerComposeCmd) {
            throw "Docker 环境检查失败"
        }
        
        # 创建测试环境
        New-TestEnvFile
        
        # 构建镜像（除非快速模式）
        if (-not $Quick) {
            $buildResult = Build-TestImages $dockerComposeCmd
            if (-not $buildResult) {
                throw "镜像构建失败"
            }
        }
        
        # 启动测试服务
        $startResult = Start-TestServices $dockerComposeCmd
        if (-not $startResult) {
            throw "服务启动失败"
        }
        
        # 测试服务健康状态
        $healthResult = Test-ServiceHealth
        
        # 测试安全功能
        $securityResult = Test-SecurityFeatures
        
        # 显示总结
        Log-Info ""
        Log-Info "测试总结:"
        if ($healthResult -and $securityResult) {
            Log-Success "✓ 所有测试通过！安全功能部署成功"
        } elseif ($healthResult) {
            Log-Warning "⚠ 服务健康但安全功能测试失败"
        } else {
            Log-Error "✗ 测试失败，请检查配置和日志"
        }
        
        Log-Info ""
        Log-Info "测试服务访问地址:"
        Log-Info "  - JAiRouter API: http://localhost:8080"
        Log-Info "  - 健康检查: http://localhost:8080/actuator/health"
        Log-Info "  - API 文档: http://localhost:8080/swagger-ui/index.html"
        Log-Info ""
        Log-Info "使用以下命令清理测试环境:"
        Log-Info "  .\test-security-deployment.ps1 -Cleanup"
        
    } catch {
        Log-Error "测试失败: $($_.Exception.Message)"
        
        # 尝试清理
        if ($dockerComposeCmd) {
            Log-Info "尝试清理测试环境..."
            Clear-TestEnvironment $dockerComposeCmd
        }
        
        exit 1
    }
}

# 执行主函数
Main