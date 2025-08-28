# JAiRouter Docker 构建脚本 (PowerShell)
# 用法: .\scripts\docker-build.ps1 [环境] [版本]

param(
    [string]$Environment = "prod",
    [string]$Version = ""
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

# 获取版本号
if ([string]::IsNullOrEmpty($Version)) {
    try {
        $Version = (mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
        if ([string]::IsNullOrEmpty($Version)) {
            $Version = "1.0-SNAPSHOT"
        }
    }
    catch {
        $Version = "1.0-SNAPSHOT"
    }
}

Write-Info "开始构建 JAiRouter Docker 镜像"
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

# 检查 Maven 是否安装
try {
    mvn --version | Out-Null
}
catch {
    Write-Error "Maven 未安装或不在 PATH 中"
    exit 1
}

# 构建应用程序
Write-Info "构建应用程序..."
if ($Environment -eq "prod") {
    $buildResult = mvn clean package -Pfast
} else {
    $buildResult = mvn clean package -DskipTests
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "Maven 构建失败"
    exit 1
}

Write-Success "应用程序构建完成"

# 构建 Docker 镜像
Write-Info "构建 Docker 镜像..."

if ($Environment -eq "dev") {
    $Dockerfile = "Dockerfile.dev"
    $ImageTag = "sodlinken/jairouter:${Version}-dev"
} else {
    $Dockerfile = "Dockerfile"
    $ImageTag = "sodlinken/jairouter:${Version}"
}

docker build -f $Dockerfile -t $ImageTag .

if ($LASTEXITCODE -ne 0) {
    Write-Error "Docker 镜像构建失败"
    exit 1
}

# 添加 latest 标签（仅生产环境）
if ($Environment -eq "prod") {
    docker tag $ImageTag sodlinken/jairouter:latest
    Write-Success "Docker 镜像构建完成: $ImageTag, sodlinken/jairouter:latest"
} else {
    Write-Success "Docker 镜像构建完成: $ImageTag"
}

# 显示镜像信息
Write-Info "镜像信息:"
docker images | Select-String "sodlinken/jairouter"

# 可选：运行镜像验证
$verify = Read-Host "是否要运行镜像进行验证? (y/N)"
if ($verify -eq "y" -or $verify -eq "Y") {
    Write-Info "启动容器进行验证..."
    
    if ($Environment -eq "dev") {
        docker run --rm -d --name jairouter-test -p 8080:8080 -p 5005:5005 $ImageTag
    } else {
        docker run --rm -d --name jairouter-test -p 8080:8080 $ImageTag
    }
    
    Write-Info "等待应用启动..."
    Start-Sleep -Seconds 30
    
    # 健康检查
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 10
        if ($response.StatusCode -eq 200) {
            Write-Success "应用启动成功，健康检查通过"
        } else {
            Write-Warning "健康检查失败，请检查应用日志"
        }
    }
    catch {
        Write-Warning "健康检查失败，请检查应用日志"
    }
    
    # 停止测试容器
    docker stop jairouter-test
    Write-Info "测试容器已停止"
}

Write-Success "Docker 构建脚本执行完成"