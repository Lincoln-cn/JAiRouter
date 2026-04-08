#!/bin/bash

# =============================================================================
# JAiRouter 标准构建部署脚本
# 用于构建前端并部署到后端，避免静态资源版本混乱问题
# =============================================================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
STATIC_DIR="${PROJECT_ROOT}/target/classes/static/admin"

# 打印带颜色的信息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查命令是否存在
check_command() {
    if ! command -v "$1" &> /dev/null; then
        print_error "$1 命令未找到，请先安装"
        exit 1
    fi
}

# 停止正在运行的服务
stop_service() {
    print_info "正在停止 Spring Boot 服务..."
    local pid=$(pgrep -f 'modelrouter.ModelRouterApplication' || true)
    if [ -n "$pid" ]; then
        kill "$pid" 2>/dev/null || true
        sleep 2
        # 强制终止如果还在运行
        pid=$(pgrep -f 'modelrouter.ModelRouterApplication' || true)
        if [ -n "$pid" ]; then
            kill -9 "$pid" 2>/dev/null || true
        fi
        print_success "服务已停止"
    else
        print_warning "没有运行中的服务"
    fi
}

# 清理旧的静态资源
clean_old_assets() {
    print_info "正在清理旧的静态资源文件..."
    
    # 清理 src/main/resources/static/admin（防止旧文件被 Maven 复制）
    local src_static_dir="${PROJECT_ROOT}/src/main/resources/static/admin"
    if [ -d "${src_static_dir}" ]; then
        print_info "清理 src/main/resources/static/admin 目录..."
        rm -rf "${src_static_dir}"
    fi
    
    if [ -d "${STATIC_DIR}/assets" ]; then
        rm -rf "${STATIC_DIR}/assets/*"
        print_success "旧静态资源已清理"
    else
        print_warning "静态资源目录不存在，将创建"
        mkdir -p "${STATIC_DIR}/assets"
    fi
}

# 构建前端
build_frontend() {
    print_info "正在构建前端..."
    cd "${FRONTEND_DIR}"

    # 清理旧的构建文件
    print_info "清理旧的前端构建文件..."
    rm -rf dist
    rm -rf node_modules/.vite

    # 检查 node_modules 是否存在
    if [ ! -d "node_modules" ]; then
        print_warning "node_modules 不存在，正在安装依赖..."
        npm install
    fi

    # 执行构建
    npm run build

    if [ $? -ne 0 ]; then
        print_error "前端构建失败"
        exit 1
    fi

    print_success "前端构建完成"
}

# 复制静态资源到目标目录
copy_assets() {
    print_info "正在复制静态资源到目标目录..."

    # 清理目标目录
    if [ -d "${STATIC_DIR}" ]; then
        print_info "清理目标静态资源目录..."
        rm -rf "${STATIC_DIR}"
    fi

    # 确保目标目录存在
    mkdir -p "${STATIC_DIR}"

    # 复制前端构建文件
    cp -r "${FRONTEND_DIR}/dist"/* "${STATIC_DIR}/"

    # 验证文件数量
    local file_count=$(find "${STATIC_DIR}/assets" -name "InstanceManagement-*.js" 2>/dev/null | wc -l)
    if [ "$file_count" -gt 1 ]; then
        print_warning "发现多个 InstanceManagement JS 文件，正在清理..."
        # 保留最新的一个，删除其他的
        cd "${STATIC_DIR}/assets"
        ls -t InstanceManagement-*.js | tail -n +2 | xargs -r rm -f
    fi

    print_success "静态资源复制完成"
    print_info "当前 InstanceManagement 文件:"
    ls -la "${STATIC_DIR}/assets/InstanceManagement-*.js" 2>/dev/null || print_warning "未找到 InstanceManagement 文件"
}

# 编译后端（可选，如果只需要更新前端可以跳过）
build_backend() {
    print_info "正在编译后端..."
    cd "${PROJECT_ROOT}"
    ./mvnw clean compile -DskipTests -P fast
    if [ $? -ne 0 ]; then
        print_error "后端编译失败"
        exit 1
    fi
    print_success "后端编译完成"
    
    # Maven copy-resources 不会清理目标目录，需要手动清理重复的 JS 文件
    print_info "清理重复的 JS 文件..."
    local assets_dir="${PROJECT_ROOT}/target/classes/static/admin/assets"
    if [ -d "${assets_dir}" ]; then
        # 查找重复的文件并删除旧的
        for pattern in "InstanceManagement-*.js" "index-*.js"; do
            local file_count=$(find "${assets_dir}" -name "${pattern}" 2>/dev/null | wc -l)
            if [ "$file_count" -gt 1 ]; then
                print_warning "发现 $file_count 个 ${pattern} 文件，保留最新的..."
                cd "${assets_dir}"
                ls -t ${pattern} | tail -n +2 | xargs -r rm -f
            fi
        done
    fi
}

# 启动服务
start_service() {
    print_info "正在启动 Spring Boot 服务..."
    cd "${PROJECT_ROOT}"
    nohup ./mvnw spring-boot:run -P fast > "${PROJECT_ROOT}/logs/spring-boot.log" 2>&1 &
    print_success "服务启动中，日志输出到 logs/spring-boot.log"

    # 等待服务启动
    print_info "等待服务启动 (约15秒)..."
    sleep 15

    # 检查服务是否成功启动
    local health_status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
    if [ "$health_status" = "200" ]; then
        print_success "服务启动成功！"
        print_info "访问地址: http://localhost:8080/admin/config/instances"
    else
        print_warning "服务可能还在启动中，请稍后检查日志"
    fi
}

# 验证部署
verify_deployment() {
    print_info "正在验证部署..."

    # 检查 index.html
    local index_file="${STATIC_DIR}/index.html"
    if [ -f "$index_file" ]; then
        local main_js=$(grep -o 'src="/admin/assets/index-[^"]*\.js"' "$index_file" | head -1)
        print_info "主 JS 文件: $main_js"
    fi

    # 检查 InstanceManagement 文件
    local instance_files=$(find "${STATIC_DIR}/assets" -name "InstanceManagement-*.js" 2>/dev/null)
    local file_count=$(echo "$instance_files" | grep -c "\.js$" || echo "0")

    if [ "$file_count" -eq 1 ]; then
        print_success "✓ InstanceManagement 文件数量正确 (1个)"
    elif [ "$file_count" -eq 0 ]; then
        print_error "✗ 未找到 InstanceManagement 文件"
    else
        print_warning "✗ 发现 $file_count 个 InstanceManagement 文件，可能存在版本混乱"
        print_info "文件列表:"
        echo "$instance_files"
    fi

    # 检查按钮配置
    local button_count=$(grep -o "限流器配置\|熔断器配置" "${STATIC_DIR}/assets/InstanceManagement-"*.js 2>/dev/null | wc -l)
    if [ "$button_count" -ge 4 ]; then
        print_success "✓ 限流器和熔断器配置按钮已正确配置"
    else
        print_warning "✗ 按钮配置可能不完整 (找到 $button_count 个引用)"
    fi
}

# 显示帮助信息
show_help() {
    cat << EOF
JAiRouter 标准构建部署脚本

用法: $0 [选项]

选项:
    -h, --help          显示帮助信息
    -f, --frontend-only 仅构建和部署前端（不编译后端，不重启服务）
    -s, --skip-build    跳过构建，仅清理和复制资源
    -v, --verify        仅验证当前部署状态

示例:
    $0                  完整构建部署流程（停止->清理->构建->启动）
    $0 -f               仅更新前端资源（适用于开发调试）
    $0 -s               跳过构建，仅清理和复制已有资源
    $0 -v               仅验证部署状态

EOF
}

# 主函数
main() {
    local frontend_only=false
    local skip_build=false
    local verify_only=false

    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -f|--frontend-only)
                frontend_only=true
                shift
                ;;
            -s|--skip-build)
                skip_build=true
                shift
                ;;
            -v|--verify)
                verify_only=true
                shift
                ;;
            *)
                print_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done

    # 仅验证模式
    if [ "$verify_only" = true ]; then
        verify_deployment
        exit 0
    fi

    print_info "========================================"
    print_info "JAiRouter 构建部署脚本"
    print_info "========================================"
    print_info "项目根目录: ${PROJECT_ROOT}"
    print_info "前端目录: ${FRONTEND_DIR}"
    print_info "静态资源目录: ${STATIC_DIR}"
    print_info "========================================"

    # 检查必要命令
    check_command npm
    check_command java

    # 执行流程
    if [ "$frontend_only" = false ]; then
        stop_service
    fi

    clean_old_assets

    if [ "$skip_build" = false ]; then
        build_frontend
    fi

    copy_assets

    if [ "$frontend_only" = false ]; then
        build_backend
        start_service
    fi

    verify_deployment

    print_info "========================================"
    print_success "构建部署完成！"
    print_info "========================================"
}

# 执行主函数
main "$@"
