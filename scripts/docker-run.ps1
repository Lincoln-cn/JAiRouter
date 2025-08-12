# JAiRouter Docker 运行脚本 (PowerShell)
# 用法: .\scripts\docker-run.ps1 [环境] [版本]

param(
    [string]$Environment = "prod",
    [string]$Version = "latest"
)

# 颜色定义
$Red = "Red"
$Green = "Green"
$Yellow = "Yellow"
$Blue = "Blue"

# 函数定义
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor $Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor $Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor $Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor $Red
}

# 验证环境参数
if ($Environment -notin @("prod", "dev")) {
    Write-Error "无效的环境参数: $Environment. 支持的环境: prod, dev"
    exit 1
}

Write-Info "启动 JAiRouter Docker 容器"
Write-Info "环境: $Environment"
Write-Info "版本: $Version"

# 检查 Docker 是否安装
try {
    docker --version | Out-Null
}
catch {
    Write-Error "Docker 未安装或不在 PATH 中"
    exit 1
}

# 停止现有容器（如果存在）
$ContainerName = "jairouter-${Environment}"
$existingContainer = docker ps -a --format "table {{.Names}}" | Select-String "^${ContainerName}$"
if ($existingContainer) {
    Write-Info "停止现有容器: $ContainerName"
    docker stop $ContainerName
    docker rm $ContainerName
}

# 创建必要的目录
New-Item -ItemType Directory -Force -Path "logs", "config", "config-store" | Out-Null

# 设置镜像标签
if ($Environment -eq "dev") {
    if ($Version -eq "latest") {
        $ImageTag = "jairouter/model-router:latest-dev"
    } else {
        $ImageTag = "jairouter/model-router:${Version}-dev"
    }
    $Ports = "-p 8080:8080 -p 5005:5005"
    $JavaOpts = "-Xms256m -Xmx512m -XX:+UseG1GC -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
} else {
    $ImageTag = "jairouter/model-router:${Version}"
    $Ports = "-p 8080:8080"
    $JavaOpts = "-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
}

# 检查镜像是否存在
$imageExists = docker images --format "table {{.Repository}}:{{.Tag}}" | Select-String "^${ImageTag}$"
if (-not $imageExists) {
    Write-Error "Docker 镜像不存在: $ImageTag"
    Write-Info "请先运行构建脚本: .\scripts\docker-build.ps1 $Environment"
    exit 1
}

# 运行容器
Write-Info "启动容器: $ContainerName"
Write-Info "使用镜像: $ImageTag"

$currentDir = (Get-Location).Path
$dockerCommand = @(
    "run", "-d",
    "--name", $ContainerName,
    $Ports.Split(' '),
    "-e", "SPRING_PROFILES_ACTIVE=$Environment",
    "-e", "JAVA_OPTS=$JavaOpts",
    "-v", "${currentDir}/config:/app/config:ro",
    "-v", "${currentDir}/logs:/app/logs",
    "-v", "${currentDir}/config-store:/app/config-store",
    "--restart", "unless-stopped",
    $ImageTag
) | Where-Object { $_ -ne "" }

& docker @dockerCommand

if ($LASTEXITCODE -ne 0) {
    Write-Error "容器启动失败"
    exit 1
}

Write-Success "容器启动成功: $ContainerName"

# 等待应用启动
Write-Info "等待应用启动..."
Start-Sleep -Seconds 10

# 显示容器状态
Write-Info "容器状态:"
docker ps --filter "name=$ContainerName" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 显示日志
Write-Info "应用日志 (最近20行):"
docker logs --tail 20 $ContainerName

# 健康检查
Write-Info "执行健康检查..."
Start-Sleep -Seconds 20

for ($i = 1; $i -le 6; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 10
        if ($response.StatusCode -eq 200) {
            Write-Success "应用启动成功，健康检查通过"
            Write-Info "访问地址:"
            Write-Info "  - 应用主页: http://localhost:8080"
            Write-Info "  - API文档: http://localhost:8080/swagger-ui/index.html"
            Write-Info "  - 健康检查: http://localhost:8080/actuator/health"
            if ($Environment -eq "dev") {
                Write-Info "  - 调试端口: 5005"
            }
            break
        }
    }
    catch {
        if ($i -eq 6) {
            Write-Warning "健康检查失败，应用可能仍在启动中"
            Write-Info "请检查容器日志: docker logs $ContainerName"
        } else {
            Write-Info "健康检查失败，等待重试... ($i/6)"
            Start-Sleep -Seconds 10
        }
    }
}

Write-Info "容器管理命令:"
Write-Info "  - 查看日志: docker logs -f $ContainerName"
Write-Info "  - 停止容器: docker stop $ContainerName"
Write-Info "  - 重启容器: docker restart $ContainerName"
Write-Info "  - 进入容器: docker exec -it $ContainerName sh"

Write-Success "Docker 运行脚本执行完成"