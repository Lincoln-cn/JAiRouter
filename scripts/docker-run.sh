#!/bin/bash

# JAiRouter Docker 运行脚本
# 用法: ./scripts/docker-run.sh [环境] [版本]

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
VERSION=${2:-latest}

# 验证环境参数
if [[ ! "$ENVIRONMENT" =~ ^(prod|dev)$ ]]; then
    log_error "无效的环境参数: $ENVIRONMENT. 支持的环境: prod, dev"
    exit 1
fi

log_info "启动 JAiRouter Docker 容器"
log_info "环境: $ENVIRONMENT"
log_info "版本: $VERSION"

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    log_error "Docker 未安装或不在 PATH 中"
    exit 1
fi

# 停止现有容器（如果存在）
CONTAINER_NAME="jairouter-${ENVIRONMENT}"
if docker ps -a --format 'table {{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_info "停止现有容器: $CONTAINER_NAME"
    docker stop $CONTAINER_NAME
    docker rm $CONTAINER_NAME
fi

# 创建必要的目录
mkdir -p logs config config-store

# 设置镜像标签
if [[ "$ENVIRONMENT" == "dev" ]]; then
    if [[ "$VERSION" == "latest" ]]; then
        IMAGE_TAG="jairouter/model-router:${VERSION}-dev"
    else
        IMAGE_TAG="jairouter/model-router:${VERSION}-dev"
    fi
    PORTS="-p 8080:8080 -p 5005:5005"
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
else
    IMAGE_TAG="jairouter/model-router:${VERSION}"
    PORTS="-p 8080:8080"
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
fi

# 检查镜像是否存在
if ! docker images --format 'table {{.Repository}}:{{.Tag}}' | grep -q "^${IMAGE_TAG}$"; then
    log_error "Docker 镜像不存在: $IMAGE_TAG"
    log_info "请先运行构建脚本: ./scripts/docker-build.sh $ENVIRONMENT"
    exit 1
fi

# 运行容器
log_info "启动容器: $CONTAINER_NAME"
log_info "使用镜像: $IMAGE_TAG"

docker run -d \
    --name $CONTAINER_NAME \
    $PORTS \
    -e SPRING_PROFILES_ACTIVE=$ENVIRONMENT \
    -e JAVA_OPTS="$JAVA_OPTS" \
    -v $(pwd)/config:/app/config:ro \
    -v $(pwd)/logs:/app/logs \
    -v $(pwd)/config-store:/app/config-store \
    --restart unless-stopped \
    $IMAGE_TAG

if [ $? -ne 0 ]; then
    log_error "容器启动失败"
    exit 1
fi

log_success "容器启动成功: $CONTAINER_NAME"

# 等待应用启动
log_info "等待应用启动..."
sleep 10

# 显示容器状态
log_info "容器状态:"
docker ps --filter "name=$CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 显示日志
log_info "应用日志 (最近20行):"
docker logs --tail 20 $CONTAINER_NAME

# 健康检查
log_info "执行健康检查..."
sleep 20

for i in {1..6}; do
    if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        log_success "应用启动成功，健康检查通过"
        log_info "访问地址:"
        log_info "  - 应用主页: http://localhost:8080"
        log_info "  - API文档: http://localhost:8080/swagger-ui/index.html"
        log_info "  - 健康检查: http://localhost:8080/actuator/health"
        if [[ "$ENVIRONMENT" == "dev" ]]; then
            log_info "  - 调试端口: 5005"
        fi
        break
    else
        if [ $i -eq 6 ]; then
            log_warning "健康检查失败，应用可能仍在启动中"
            log_info "请检查容器日志: docker logs $CONTAINER_NAME"
        else
            log_info "健康检查失败，等待重试... ($i/6)"
            sleep 10
        fi
    fi
done

log_info "容器管理命令:"
log_info "  - 查看日志: docker logs -f $CONTAINER_NAME"
log_info "  - 停止容器: docker stop $CONTAINER_NAME"
log_info "  - 重启容器: docker restart $CONTAINER_NAME"
log_info "  - 进入容器: docker exec -it $CONTAINER_NAME sh"

log_success "Docker 运行脚本执行完成"