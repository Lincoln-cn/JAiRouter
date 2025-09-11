#!/bin/bash

# JAiRouter 安全功能部署脚本
# 用于部署启用安全功能的 JAiRouter 实例

set -e

# 脚本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
ENV_FILE="$PROJECT_ROOT/.env.security"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
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

# 显示帮助信息
show_help() {
    cat << EOF
JAiRouter 安全功能部署脚本

用法: $0 [选项]

选项:
    -h, --help              显示此帮助信息
    -e, --env-file FILE     指定环境变量文件 (默认: .env.security)
    -f, --force             强制重新构建镜像
    -d, --dev               使用开发环境配置
    -s, --stop              停止服务
    -r, --restart           重启服务
    -l, --logs              查看服务日志
    --enable-monitoring     启用监控服务 (Prometheus + Grafana)
    --check-health          检查服务健康状态

示例:
    $0                      # 使用默认配置部署
    $0 -f                   # 强制重新构建并部署
    $0 -d                   # 使用开发环境部署
    $0 --enable-monitoring  # 启用监控功能部署
    $0 -s                   # 停止所有服务
    $0 -l                   # 查看服务日志

EOF
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装或不在 PATH 中"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安装或不在 PATH 中"
        exit 1
    fi
    
    log_success "依赖检查通过"
}

# 创建默认环境变量文件
create_default_env() {
    if [[ ! -f "$ENV_FILE" ]]; then
        log_info "创建默认环境变量文件: $ENV_FILE"
        cat > "$ENV_FILE" << 'EOF'
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
EOF
        log_warning "请编辑 $ENV_FILE 文件，设置正确的安全配置参数"
        log_warning "特别注意设置强密码用于 ADMIN_API_KEY, USER_API_KEY, JWT_SECRET 和 REDIS_PASSWORD"
    fi
}

# 验证环境变量
validate_env() {
    log_info "验证环境变量配置..."
    
    source "$ENV_FILE"
    
    # 检查必要的环境变量
    local required_vars=(
        "JAIROUTER_SECURITY_ENABLED"
        "ADMIN_API_KEY"
        "USER_API_KEY"
    )
    
    local missing_vars=()
    for var in "${required_vars[@]}"; do
        if [[ -z "${!var}" ]]; then
            missing_vars+=("$var")
        fi
    done
    
    if [[ ${#missing_vars[@]} -gt 0 ]]; then
        log_error "以下必要的环境变量未设置:"
        for var in "${missing_vars[@]}"; do
            echo "  - $var"
        done
        exit 1
    fi
    
    # 检查密钥强度
    if [[ "$ADMIN_API_KEY" == "your-admin-api-key-here" ]] || [[ ${#ADMIN_API_KEY} -lt 16 ]]; then
        log_error "ADMIN_API_KEY 必须设置为至少16位的强密钥"
        exit 1
    fi
    
    if [[ "$USER_API_KEY" == "your-user-api-key-here" ]] || [[ ${#USER_API_KEY} -lt 16 ]]; then
        log_error "USER_API_KEY 必须设置为至少16位的强密钥"
        exit 1
    fi
    
    if [[ "$JAIROUTER_SECURITY_JWT_ENABLED" == "true" ]]; then
        if [[ "$JWT_SECRET" == "your-jwt-secret-key-here" ]] || [[ ${#JWT_SECRET} -lt 32 ]]; then
            log_error "启用 JWT 时，JWT_SECRET 必须设置为至少32位的强密钥"
            exit 1
        fi
    fi
    
    log_success "环境变量验证通过"
}

# 构建镜像
build_images() {
    local force_build=$1
    
    log_info "构建 JAiRouter 镜像..."
    
    cd "$PROJECT_ROOT"
    
    if [[ "$force_build" == "true" ]]; then
        docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" build --no-cache
    else
        docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" build
    fi
    
    log_success "镜像构建完成"
}

# 部署服务
deploy_services() {
    local enable_monitoring=$1
    
    log_info "部署 JAiRouter 安全功能服务..."
    
    cd "$PROJECT_ROOT"
    
    # 基础服务
    local services="jairouter redis"
    
    # 如果启用监控，添加监控服务
    if [[ "$enable_monitoring" == "true" ]]; then
        services="$services prometheus grafana"
        export COMPOSE_PROFILES="monitoring"
    fi
    
    docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d $services
    
    log_success "服务部署完成"
}

# 停止服务
stop_services() {
    log_info "停止 JAiRouter 服务..."
    
    cd "$PROJECT_ROOT"
    docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down
    
    log_success "服务已停止"
}

# 重启服务
restart_services() {
    log_info "重启 JAiRouter 服务..."
    
    cd "$PROJECT_ROOT"
    docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" restart
    
    log_success "服务已重启"
}

# 查看日志
show_logs() {
    log_info "显示服务日志..."
    
    cd "$PROJECT_ROOT"
    docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" logs -f
}

# 检查健康状态
check_health() {
    log_info "检查服务健康状态..."
    
    cd "$PROJECT_ROOT"
    
    # 检查容器状态
    local containers=$(docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps -q)
    
    if [[ -z "$containers" ]]; then
        log_error "没有运行的容器"
        return 1
    fi
    
    # 检查每个容器的健康状态
    for container in $containers; do
        local name=$(docker inspect --format='{{.Name}}' "$container" | sed 's/^.//')
        local health=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "no-healthcheck")
        local status=$(docker inspect --format='{{.State.Status}}' "$container")
        
        if [[ "$status" == "running" ]]; then
            if [[ "$health" == "healthy" ]] || [[ "$health" == "no-healthcheck" ]]; then
                log_success "$name: 运行正常"
            else
                log_warning "$name: 运行中但健康检查失败 ($health)"
            fi
        else
            log_error "$name: 未运行 ($status)"
        fi
    done
    
    # 检查服务端点
    log_info "检查服务端点..."
    
    local jairouter_url="http://localhost:8080/actuator/health"
    if curl -s "$jairouter_url" > /dev/null; then
        log_success "JAiRouter 健康检查端点可访问"
    else
        log_error "JAiRouter 健康检查端点不可访问"
    fi
    
    local redis_host="localhost"
    local redis_port="6379"
    if timeout 5 bash -c "</dev/tcp/$redis_host/$redis_port"; then
        log_success "Redis 服务可访问"
    else
        log_error "Redis 服务不可访问"
    fi
}

# 主函数
main() {
    local force_build=false
    local dev_mode=false
    local stop_mode=false
    local restart_mode=false
    local logs_mode=false
    local enable_monitoring=false
    local check_health_mode=false
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -e|--env-file)
                ENV_FILE="$2"
                shift 2
                ;;
            -f|--force)
                force_build=true
                shift
                ;;
            -d|--dev)
                dev_mode=true
                COMPOSE_FILE="$PROJECT_ROOT/docker-compose.dev.yml"
                ENV_FILE="$PROJECT_ROOT/.env.security.dev"
                shift
                ;;
            -s|--stop)
                stop_mode=true
                shift
                ;;
            -r|--restart)
                restart_mode=true
                shift
                ;;
            -l|--logs)
                logs_mode=true
                shift
                ;;
            --enable-monitoring)
                enable_monitoring=true
                shift
                ;;
            --check-health)
                check_health_mode=true
                shift
                ;;
            *)
                log_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 检查依赖
    check_dependencies
    
    # 执行相应操作
    if [[ "$stop_mode" == "true" ]]; then
        stop_services
    elif [[ "$restart_mode" == "true" ]]; then
        restart_services
    elif [[ "$logs_mode" == "true" ]]; then
        show_logs
    elif [[ "$check_health_mode" == "true" ]]; then
        check_health
    else
        # 部署模式
        create_default_env
        validate_env
        build_images "$force_build"
        deploy_services "$enable_monitoring"
        
        log_success "JAiRouter 安全功能部署完成!"
        log_info "服务访问地址:"
        log_info "  - JAiRouter API: http://localhost:8080"
        log_info "  - 健康检查: http://localhost:8080/actuator/health"
        log_info "  - API 文档: http://localhost:8080/swagger-ui/index.html"
        
        if [[ "$enable_monitoring" == "true" ]]; then
            log_info "  - Prometheus: http://localhost:9090"
            log_info "  - Grafana: http://localhost:3000 (admin/admin)"
        fi
        
        log_info ""
        log_info "使用以下命令管理服务:"
        log_info "  - 查看日志: $0 -l"
        log_info "  - 检查健康: $0 --check-health"
        log_info "  - 重启服务: $0 -r"
        log_info "  - 停止服务: $0 -s"
    fi
}

# 执行主函数
main "$@"