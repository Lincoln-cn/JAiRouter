#!/bin/bash

# JAiRouter 安全功能部署测试脚本
# 用于测试容器化部署的安全功能

set -e

# 脚本配置
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEST_ENV_FILE="$PROJECT_ROOT/.env.security.test"

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
JAiRouter 安全功能部署测试脚本

用法: $0 [选项]

选项:
    -h, --help      显示此帮助信息
    -q, --quick     快速测试（跳过构建）
    -c, --cleanup   清理测试环境

示例:
    $0              # 完整测试
    $0 -q           # 快速测试
    $0 -c           # 清理环境

EOF
}

# 创建测试环境变量文件
create_test_env_file() {
    log_info "创建测试环境变量文件..."
    
    cat > "$TEST_ENV_FILE" << 'EOF'
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
EOF
    
    log_success "测试环境变量文件创建完成"
}

# 检查 Docker 环境
check_docker_environment() {
    log_info "检查 Docker 环境..."
    
    # 检查 Docker 是否运行
    if ! docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
        log_error "Docker 服务未运行或不可访问"
        return 1
    fi
    
    local docker_version=$(docker version --format '{{.Server.Version}}')
    log_success "Docker 服务运行正常 (版本: $docker_version)"
    
    # 检查 Docker Compose
    local docker_compose_cmd=""
    if command -v docker-compose >/dev/null 2>&1; then
        docker_compose_cmd="docker-compose"
    elif docker compose version >/dev/null 2>&1; then
        docker_compose_cmd="docker compose"
    else
        log_error "Docker Compose 未安装"
        return 1
    fi
    
    log_success "Docker Compose 可用: $docker_compose_cmd"
    echo "$docker_compose_cmd"
}

# 检查镜像是否存在
function check_docker_image() {
    if docker images sodlinken/jairouter:latest -q | grep -q .; then
        echo "Docker image sodlinken/jairouter:latest exists"
        return 0
    else
        echo "Docker image sodlinken/jairouter:latest not found"
        return 1
    fi
}

# 构建测试镜像
build_test_images() {
    local docker_compose_cmd="$1"
    
    log_info "构建测试镜像..."
    
    cd "$PROJECT_ROOT"
    
    # 检查是否需要构建
    if docker images sodlinken/jairouter:latest -q | grep -q .; then
        log_info "镜像已存在，跳过构建"
        return 0
    fi
    
    if $docker_compose_cmd -f docker-compose.yml --env-file "$TEST_ENV_FILE" build --quiet; then
        log_success "测试镜像构建完成"
        return 0
    else
        log_error "镜像构建失败"
        return 1
    fi
}

# 启动测试服务
start_test_services() {
    local docker_compose_cmd="$1"
    
    log_info "启动测试服务..."
    
    cd "$PROJECT_ROOT"
    
    # 启动基础服务
    if $docker_compose_cmd -f docker-compose.yml --env-file "$TEST_ENV_FILE" up -d jairouter redis; then
        # 等待服务启动
        log_info "等待服务启动..."
        sleep 30
        
        log_success "测试服务启动完成"
        return 0
    else
        log_error "服务启动失败"
        return 1
    fi
}

# 测试服务健康状态
test_service_health() {
    log_info "测试服务健康状态..."
    
    local health_tests=()
    local passed_tests=0
    local total_tests=0
    
    # 测试 JAiRouter 健康检查
    total_tests=$((total_tests + 1))
    if curl -s --max-time 10 "http://localhost:8080/actuator/health" >/dev/null; then
        log_success "  ✓ JAiRouter 健康检查: 通过"
        passed_tests=$((passed_tests + 1))
    else
        log_error "  ✗ JAiRouter 健康检查: 失败"
    fi
    
    # 测试 Redis 连接
    total_tests=$((total_tests + 1))
    if timeout 5 bash -c "</dev/tcp/localhost/6379" 2>/dev/null; then
        log_success "  ✓ Redis 连接: 通过"
        passed_tests=$((passed_tests + 1))
    else
        log_error "  ✗ Redis 连接: 失败"
    fi
    
    # 显示测试结果
    log_info "健康检查结果:"
    
    if [[ $passed_tests -eq $total_tests ]]; then
        log_success "所有健康检查通过 ($passed_tests/$total_tests)"
        return 0
    else
        log_warning "部分健康检查失败 ($passed_tests/$total_tests)"
        return 1
    fi
}

# 测试安全功能
test_security_features() {
    log_info "测试安全功能..."
    
    local security_tests=()
    local passed_tests=0
    local total_tests=0
    
    # 测试无 API Key 访问（应该被拒绝）
    total_tests=$((total_tests + 1))
    local response_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "http://localhost:8080/v1/models" || echo "000")
    if [[ "$response_code" == "401" ]]; then
        log_success "  ✓ 无 API Key 访问控制: 通过"
        passed_tests=$((passed_tests + 1))
    else
        log_error "  ✗ 无 API Key 访问控制: 失败 (HTTP $response_code, 应该返回401)"
    fi
    
    # 测试有效 API Key 访问
    total_tests=$((total_tests + 1))
    response_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
        -H "X-API-Key: test-admin-key-1234567890abcdef" \
        "http://localhost:8080/v1/models" || echo "000")
    if [[ "$response_code" == "200" ]]; then
        log_success "  ✓ 有效 API Key 访问: 通过"
        passed_tests=$((passed_tests + 1))
    else
        log_error "  ✗ 有效 API Key 访问: 失败 (HTTP $response_code)"
    fi
    
    # 测试无效 API Key 访问
    total_tests=$((total_tests + 1))
    response_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
        -H "X-API-Key: invalid-api-key" \
        "http://localhost:8080/v1/models" || echo "000")
    if [[ "$response_code" == "401" ]]; then
        log_success "  ✓ 无效 API Key 访问控制: 通过"
        passed_tests=$((passed_tests + 1))
    else
        log_error "  ✗ 无效 API Key 访问控制: 失败 (HTTP $response_code, 应该返回401)"
    fi
    
    # 显示测试结果
    log_info "安全功能测试结果:"
    
    if [[ $passed_tests -eq $total_tests ]]; then
        log_success "所有安全功能测试通过 ($passed_tests/$total_tests)"
        return 0
    else
        log_warning "部分安全功能测试失败 ($passed_tests/$total_tests)"
        return 1
    fi
}

# 清理测试环境
cleanup_test_environment() {
    local docker_compose_cmd="$1"
    
    log_info "清理测试环境..."
    
    cd "$PROJECT_ROOT"
    
    # 停止并删除容器
    $docker_compose_cmd -f docker-compose.yml --env-file "$TEST_ENV_FILE" down -v --remove-orphans
    
    # 删除测试环境变量文件
    if [[ -f "$TEST_ENV_FILE" ]]; then
        rm -f "$TEST_ENV_FILE"
    fi
    
    log_success "测试环境清理完成"
}

# 主函数
main() {
    local quick_mode=false
    local cleanup_mode=false
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -q|--quick)
                quick_mode=true
                shift
                ;;
            -c|--cleanup)
                cleanup_mode=true
                shift
                ;;
            *)
                log_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 清理模式
    if [[ "$cleanup_mode" == "true" ]]; then
        local docker_compose_cmd
        docker_compose_cmd=$(check_docker_environment)
        if [[ $? -eq 0 ]]; then
            cleanup_test_environment "$docker_compose_cmd"
        fi
        return
    fi
    
    log_info "开始 JAiRouter 安全功能部署测试..."
    
    # 检查 Docker 环境
    local docker_compose_cmd
    docker_compose_cmd=$(check_docker_environment)
    if [[ $? -ne 0 ]]; then
        log_error "Docker 环境检查失败"
        exit 1
    fi
    
    # 创建测试环境
    create_test_env_file
    
    # 构建镜像（除非快速模式）
    if [[ "$quick_mode" != "true" ]]; then
        if ! build_test_images "$docker_compose_cmd"; then
            log_error "镜像构建失败"
            cleanup_test_environment "$docker_compose_cmd"
            exit 1
        fi
    fi
    
    # 启动测试服务
    if ! start_test_services "$docker_compose_cmd"; then
        log_error "服务启动失败"
        cleanup_test_environment "$docker_compose_cmd"
        exit 1
    fi
    
    # 测试服务健康状态
    local health_result=0
    test_service_health || health_result=1
    
    # 测试安全功能
    local security_result=0
    test_security_features || security_result=1
    
    # 显示总结
    echo ""
    log_info "测试总结:"
    if [[ $health_result -eq 0 && $security_result -eq 0 ]]; then
        log_success "✓ 所有测试通过！安全功能部署成功"
    elif [[ $health_result -eq 0 ]]; then
        log_warning "⚠ 服务健康但安全功能测试失败"
    else
        log_error "✗ 测试失败，请检查配置和日志"
    fi
    
    echo ""
    log_info "测试服务访问地址:"
    log_info "  - JAiRouter API: http://localhost:8080"
    log_info "  - 健康检查: http://localhost:8080/actuator/health"
    log_info "  - API 文档: http://localhost:8080/swagger-ui/index.html"
    echo ""
    log_info "使用以下命令清理测试环境:"
    log_info "  $0 -c"
    
    # 如果测试失败，返回非零退出码
    if [[ $health_result -ne 0 || $security_result -ne 0 ]]; then
        exit 1
    fi
}

# 执行主函数
main "$@"