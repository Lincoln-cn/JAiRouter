#!/bin/bash

# JAiRouter Docker 运行脚本
# 用法：./scripts/build/docker-run.sh [环境] [版本] [镜像类型]
# 镜像类型：standard（标准版）, optimized（优化版，推荐）

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
IMAGE_TYPE=${3:-optimized}  # 默认使用优化版

# 显示使用说明
show_usage() {
    echo "用法：$0 [环境] [版本] [镜像类型]"
    echo ""
    echo "环境:"
    echo "  prod  生产环境（默认）"
    echo "  dev   开发环境"
    echo ""
    echo "版本:"
    echo "  latest      最新版本（默认）"
    echo "  v1.7.0      指定版本"
    echo ""
    echo "镜像类型:"
    echo "  optimized   优化版镜像（推荐，281MB）"
    echo "  standard    标准版镜像（440MB）"
    echo ""
    echo "示例:"
    echo "  $0 prod latest optimized    # 使用优化版生产镜像"
    echo "  $0 dev latest standard      # 使用标准版开发镜像"
    echo "  $0 prod v1.7.0 optimized    # 使用优化版 v1.7.0 生产镜像"
    exit 1
}

# 验证环境参数
if [[ ! "$ENVIRONMENT" =~ ^(prod|dev)$ ]]; then
    log_error "无效的环境参数：$ENVIRONMENT. 支持的环境：prod, dev"
    show_usage
fi

# 验证镜像类型参数
if [[ ! "$IMAGE_TYPE" =~ ^(optimized|standard)$ ]]; then
    log_error "无效的镜像类型：$IMAGE_TYPE. 支持的类型：optimized, standard"
    show_usage
fi

log_info "启动 JAiRouter Docker 容器"
log_info "环境：$ENVIRONMENT"
log_info "版本：$VERSION"
log_info "镜像类型：$IMAGE_TYPE"

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    log_error "Docker 未安装或不在 PATH 中"
    exit 1
fi

# 停止现有容器（如果存在）
CONTAINER_NAME="jairouter-${ENVIRONMENT}"
if docker ps -a --format 'table {{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_info "停止现有容器：$CONTAINER_NAME"
    docker stop $CONTAINER_NAME 2>/dev/null || true
    docker rm $CONTAINER_NAME 2>/dev/null || true
fi

# 创建必要的目录
mkdir -p logs config config-store data

# 设置镜像标签和配置
if [[ "$IMAGE_TYPE" == "optimized" ]]; then
    IMAGE_TAG="sodlinken/jairouter:${VERSION}-optimized"
    log_info "使用优化版镜像（281MB，推荐）⭐"
else
    if [[ "$ENVIRONMENT" == "dev" ]]; then
        IMAGE_TAG="sodlinken/jairouter:${VERSION}-dev"
    else
        IMAGE_TAG="sodlinken/jairouter:${VERSION}"
    fi
    log_info "使用标准版镜像（440MB）"
fi

# 设置端口和 JVM 参数
if [[ "$ENVIRONMENT" == "dev" ]]; then
    PORTS="-p 8080:8080 -p 5005:5005"
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    SECURITY_CONFIG="-e JAIROUTER_SECURITY_JWT_ENABLED=false"
else
    PORTS="-p 8080:8080"
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
    # 生产环境需要配置 JWT 密钥
    log_warning "生产环境请确保设置 JAIROUTER_SECURITY_JWT_SECRET 环境变量"
    SECURITY_CONFIG="-e JAIROUTER_SECURITY_ENABLED=true -e JAIROUTER_SECURITY_JWT_ENABLED=true"
fi

# 检查镜像是否存在
if ! docker images --format 'table {{.Repository}}:{{.Tag}}' | grep -q "^${IMAGE_TAG}$"; then
    log_error "Docker 镜像不存在：$IMAGE_TAG"
    log_info "请先运行构建脚本：./scripts/build/docker-build.sh"
    log_info "或使用优化版构建脚本：./scripts/build/docker-build.sh optimized"
    exit 1
fi

# 运行容器
log_info "启动容器：$CONTAINER_NAME"
log_info "使用镜像：$IMAGE_TAG"

docker run -d \
    --name $CONTAINER_NAME \
    $PORTS \
    -e SPRING_PROFILES_ACTIVE=$ENVIRONMENT \
    -e JAVA_OPTS="$JAVA_OPTS" \
    $SECURITY_CONFIG \
    -v $(pwd)/config:/app/config:ro \
    -v $(pwd)/logs:/app/logs \
    -v $(pwd)/config-store:/app/config-store \
    -v $(pwd)/data:/app/data \
    --restart unless-stopped \
    $IMAGE_TAG

if [ $? -ne 0 ]; then
    log_error "容器启动失败"
    exit 1
fi

log_success "容器启动成功：$CONTAINER_NAME"

# 等待应用启动
log_info "等待应用启动..."
sleep 10

# 显示容器状态
log_info "容器状态:"
docker ps --filter "name=$CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 显示日志
log_info "应用日志 (最近 20 行):"
docker logs --tail 20 $CONTAINER_NAME

# 健康检查
log_info "执行健康检查..."
sleep 20

for i in {1..6}; do
    if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        log_success "应用启动成功，健康检查通过"
        log_info "访问地址:"
        log_info "  - 应用主页：http://localhost:8080"
        log_info "  - 管理后台：http://localhost:8080/admin"
        log_info "  - API 文档：http://localhost:8080/swagger-ui/index.html"
        log_info "  - 健康检查：http://localhost:8080/actuator/health"
        if [[ "$ENVIRONMENT" == "dev" ]]; then
            log_info "  - 调试端口：5005"
        fi
        break
    else
        if [ $i -eq 6 ]; then
            log_warning "健康检查失败，应用可能仍在启动中"
            log_info "请检查容器日志：docker logs $CONTAINER_NAME"
        else
            log_info "健康检查失败，等待重试... ($i/6)"
            sleep 10
        fi
    fi
done

log_info "容器管理命令:"
log_info "  - 查看日志：docker logs -f $CONTAINER_NAME"
log_info "  - 停止容器：docker stop $CONTAINER_NAME"
log_info "  - 重启容器：docker restart $CONTAINER_NAME"
log_info "  - 进入容器：docker exec -it $CONTAINER_NAME sh"
log_info "  - 查看镜像大小：docker images $IMAGE_TAG"

log_success "Docker 运行脚本执行完成"
