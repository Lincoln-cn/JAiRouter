#!/bin/bash

# JAiRouter Docker 运行脚本
# Usage: ./scripts/build/docker-run.sh [Environment] [Version] [Image type]
# Image type：standard（standard）, optimized（optimized，推荐）

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
IMAGE_TYPE=${3:-optimized}  # 默认使用optimized

# Show usage
show_usage() {
    echo "Usage: $0 [Environment] [Version] [Image type]"
    echo ""
    echo "Environment:"
    echo "  prod  生产Environment（默认）"
    echo "  dev   开发Environment"
    echo ""
    echo "Version:"
    echo "  latest      最新Version（默认）"
    echo "  v1.7.0      指定Version"
    echo ""
    echo "Image type:"
    echo "  optimized   optimized镜像（推荐，281MB）"
    echo "  standard    standard镜像（440MB）"
    echo ""
    echo "示例:"
    echo "  $0 prod latest optimized    # 使用optimized生产镜像"
    echo "  $0 dev latest standard      # 使用standard开发镜像"
    echo "  $0 prod v1.7.0 optimized    # 使用optimized v1.7.0 生产镜像"
    exit 1
}

# 验证Environment参数
if [[ ! "$ENVIRONMENT" =~ ^(prod|dev)$ ]]; then
    log_error "无效的Environment参数：$ENVIRONMENT. 支持的Environment：prod, dev"
    show_usage
fi

# 验证Image type参数
if [[ ! "$IMAGE_TYPE" =~ ^(optimized|standard)$ ]]; then
    log_error "无效的Image type：$IMAGE_TYPE. Supported types：optimized, standard"
    show_usage
fi

log_info "Starting JAiRouter Docker container"
log_info "Environment：$ENVIRONMENT"
log_info "Version：$VERSION"
log_info "Image type：$IMAGE_TYPE"

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    log_error "Docker 未安装或不在 PATH 中"
    exit 1
fi

# Stopping existing container（如果存在）
CONTAINER_NAME="jairouter-${ENVIRONMENT}"
if docker ps -a --format 'table {{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_info "Stopping existing container：$CONTAINER_NAME"
    docker stop $CONTAINER_NAME 2>/dev/null || true
    docker rm $CONTAINER_NAME 2>/dev/null || true
fi

# Creating necessary directories
mkdir -p logs config config-store data

# Setting image tag and configuration
if [[ "$IMAGE_TYPE" == "optimized" ]]; then
    IMAGE_TAG="sodlinken/jairouter:${VERSION}-optimized"
    log_info "使用optimized镜像（281MB，推荐）⭐"
else
    if [[ "$ENVIRONMENT" == "dev" ]]; then
        IMAGE_TAG="sodlinken/jairouter:${VERSION}-dev"
    else
        IMAGE_TAG="sodlinken/jairouter:${VERSION}"
    fi
    log_info "使用standard镜像（440MB）"
fi

# 设置端口和 JVM 参数
if [[ "$ENVIRONMENT" == "dev" ]]; then
    PORTS="-p 8080:8080 -p 5005:5005"
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    SECURITY_CONFIG="-e JAIROUTER_SECURITY_JWT_ENABLED=false"
else
    PORTS="-p 8080:8080"
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
    # 生产Environment需要配置 JWT 密钥
    log_warning "生产Environment请确保设置 JAIROUTER_SECURITY_JWT_SECRET Environment变量"
    SECURITY_CONFIG="-e JAIROUTER_SECURITY_ENABLED=true -e JAIROUTER_SECURITY_JWT_ENABLED=true"
fi

# Checking if image exists
if ! docker images --format 'table {{.Repository}}:{{.Tag}}' | grep -q "^${IMAGE_TAG}$"; then
    log_error "Docker 镜像不存在：$IMAGE_TAG"
    log_info "请先运行构建脚本：./scripts/build/docker-build.sh"
    log_info "或使用optimized构建脚本：./scripts/build/docker-build.sh optimized"
    exit 1
fi

# 运行容器
log_info "Starting container：$CONTAINER_NAME"
log_info "Using image：$IMAGE_TAG"

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

log_success "Container started successfully：$CONTAINER_NAME"

# Waiting for application to start
log_info "Waiting for application to start..."
sleep 10

# Showing container status
log_info "容器状态:"
docker ps --filter "name=$CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 显示日志
log_info "Application logs (最近 20 行):"
docker logs --tail 20 $CONTAINER_NAME

# Health check
log_info "Performing health check..."
sleep 20

for i in {1..6}; do
    if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        log_success "Application started successfully, health check passed"
        log_info "Access URLs:"
        log_info "  - 应用主页：http://localhost:8080"
        log_info "  - Admin console：http://localhost:8080/admin"
        log_info "  - API documentation：http://localhost:8080/swagger-ui/index.html"
        log_info "  - Health check：http://localhost:8080/actuator/health"
        if [[ "$ENVIRONMENT" == "dev" ]]; then
            log_info "  - Debug port：5005"
        fi
        break
    else
        if [ $i -eq 6 ]; then
            log_warning "Health check失败，应用可能仍在启动中"
            log_info "Please check container logs：docker logs $CONTAINER_NAME"
        else
            log_info "Health check失败，等待重试... ($i/6)"
            sleep 10
        fi
    fi
done

log_info "Container management commands:"
log_info "  - View logs：docker logs -f $CONTAINER_NAME"
log_info "  - Stop container：docker stop $CONTAINER_NAME"
log_info "  - Restart container：docker restart $CONTAINER_NAME"
log_info "  - Enter container：docker exec -it $CONTAINER_NAME sh"
log_info "  - Check image size：docker images $IMAGE_TAG"

log_success "Docker Run script completed"
