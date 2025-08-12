#!/bin/bash

# JAiRouter Docker 构建脚本
# 用法: ./scripts/docker-build.sh [环境] [版本]
# 环境: prod (默认), dev
# 版本: 默认从 pom.xml 读取

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 函数定义
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 参数解析
ENVIRONMENT=${1:-prod}
VERSION=${2:-$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)}

# 验证环境参数
if [[ ! "$ENVIRONMENT" =~ ^(prod|dev)$ ]]; then
    log_error "无效的环境参数: $ENVIRONMENT. 支持的环境: prod, dev"
    exit 1
fi

log_info "开始构建 JAiRouter Docker 镜像"
log_info "环境: $ENVIRONMENT"
log_info "版本: $VERSION"

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    log_error "Docker 未安装或不在 PATH 中"
    exit 1
fi

# 检查 Maven 是否安装
if ! command -v mvn &> /dev/null; then
    log_error "Maven 未安装或不在 PATH 中"
    exit 1
fi

# 构建应用程序
log_info "构建应用程序..."
if [[ "$ENVIRONMENT" == "prod" ]]; then
    mvn clean package -Pfast
else
    mvn clean package -DskipTests
fi

if [ $? -ne 0 ]; then
    log_error "Maven 构建失败"
    exit 1
fi

log_success "应用程序构建完成"

# 构建 Docker 镜像
log_info "构建 Docker 镜像..."

if [[ "$ENVIRONMENT" == "dev" ]]; then
    DOCKERFILE="Dockerfile.dev"
    IMAGE_TAG="jairouter/model-router:${VERSION}-dev"
else
    DOCKERFILE="Dockerfile"
    IMAGE_TAG="jairouter/model-router:${VERSION}"
fi

docker build -f $DOCKERFILE -t $IMAGE_TAG .

if [ $? -ne 0 ]; then
    log_error "Docker 镜像构建失败"
    exit 1
fi

# 添加 latest 标签（仅生产环境）
if [[ "$ENVIRONMENT" == "prod" ]]; then
    docker tag $IMAGE_TAG jairouter/model-router:latest
    log_success "Docker 镜像构建完成: $IMAGE_TAG, jairouter/model-router:latest"
else
    log_success "Docker 镜像构建完成: $IMAGE_TAG"
fi

# 显示镜像信息
log_info "镜像信息:"
docker images | grep "jairouter/model-router"

# 可选：运行镜像验证
read -p "是否要运行镜像进行验证? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    log_info "启动容器进行验证..."
    
    if [[ "$ENVIRONMENT" == "dev" ]]; then
        docker run --rm -d --name jairouter-test -p 8080:8080 -p 5005:5005 $IMAGE_TAG
    else
        docker run --rm -d --name jairouter-test -p 8080:8080 $IMAGE_TAG
    fi
    
    log_info "等待应用启动..."
    sleep 30
    
    # 健康检查
    if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        log_success "应用启动成功，健康检查通过"
    else
        log_warning "健康检查失败，请检查应用日志"
    fi
    
    # 停止测试容器
    docker stop jairouter-test
    log_info "测试容器已停止"
fi

log_success "Docker 构建脚本执行完成"