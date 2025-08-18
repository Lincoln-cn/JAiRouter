#!/bin/bash
# JAiRouter 文档管理统一脚本
# 整合了文档服务、链接检查、版本管理、结构验证等功能

set -euo pipefail

# 默认参数
COMMAND=""
HOST="localhost"
PORT="8000"
OUTPUT=""
FAIL_ON_ERROR=false
AUTO_FIX=false
APPLY=false
SCAN=false
ADD_HEADERS=false
CLEANUP=0
EXPORT=""
CHECK_OUTDATED=30

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${CYAN}$1${NC}"
}

log_success() {
    echo -e "${GREEN}$1${NC}"
}

log_warning() {
    echo -e "${YELLOW}$1${NC}"
}

log_error() {
    echo -e "${RED}$1${NC}"
}

show_help() {
    log_success "JAiRouter 文档管理工具"
    log_success "========================"
    echo ""
    log_info "用法: $0 <命令> [选项]"
    echo ""
    log_warning "可用命令:"
    echo "  serve          启动本地文档服务器"
    echo "  check-links    检查文档链接有效性"
    echo "  fix-links      修复无效链接"
    echo "  check-sync     检查文档与代码同步性"
    echo "  version        管理文档版本"
    echo "  validate       验证文档结构和配置"
    echo "  help           显示此帮助信息"
    echo ""
    log_warning "serve 命令选项:"
    echo "  --host <地址>  监听地址 (默认: localhost)"
    echo "  --port <端口>  监听端口 (默认: 8000)"
    echo ""
    log_warning "check-links 命令选项:"
    echo "  --output <文件> 输出报告文件"
    echo "  --fail-on-error 发现问题时退出码为1"
    echo ""
    log_warning "fix-links 命令选项:"
    echo "  --auto-fix     自动修复不询问确认"
    echo "  --apply        应用修复建议"
    echo ""
    log_warning "version 命令选项:"
    echo "  --scan         扫描并更新版本信息"
    echo "  --add-headers  添加版本头信息"
    echo "  --cleanup <天数> 清理指定天数前的变更记录"
    echo "  --export <文件> 导出版本数据"
    echo ""
    log_warning "示例:"
    echo "  $0 serve --port 3000"
    echo "  $0 check-links --output report.json"
    echo "  $0 fix-links --apply --auto-fix"
    echo "  $0 version --scan --add-headers"
}

start_docs_server() {
    log_success "🚀 启动本地文档服务器..."
    
    # 切换到项目根目录
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local project_root="$(dirname "$(dirname "$script_dir")")"
    cd "$project_root"
    
    # 检查 Python
    if ! command -v python3 &> /dev/null; then
        log_error "❌ 错误: 未找到 Python 3，请先安装 Python 3.x"
        exit 1
    fi
    
    log_info "检测到 Python: $(python3 --version)"
    
    # 检查 requirements.txt
    if [ ! -f "requirements.txt" ]; then
        log_error "❌ 错误: 未找到 requirements.txt 文件"
        exit 1
    fi
    
    # 安装依赖
    log_warning "📦 安装文档依赖..."
    pip3 install -r requirements.txt
    
    if [ $? -ne 0 ]; then
        log_error "❌ 错误: 依赖安装失败"
        exit 1
    fi
    
    # 启动服务器
    log_success "🌐 启动文档服务器，监听地址: $HOST:$PORT"
    log_info "📖 访问地址: http://$HOST:$PORT"
    log_warning "⏹️  按 Ctrl+C 停止服务器"
    
    mkdocs serve --dev-addr "$HOST:$PORT"
}

invoke_link_check() {
    log_success "🔍 检查文档链接..."
    
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local script_path="$script_dir/check-links.py"
    
    if [ ! -f "$script_path" ]; then
        log_error "❌ 错误: 链接检查脚本不存在"
        exit 1
    fi
    
    local args=()
    if [ -n "$OUTPUT" ]; then
        args+=("--output" "$OUTPUT")
    fi
    if [ "$FAIL_ON_ERROR" = true ]; then
        args+=("--fail-on-error")
    fi
    
    python3 "$script_path" "${args[@]}"
}

invoke_link_fix() {
    log_success "🔧 修复文档链接..."
    
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local script_path="$script_dir/fix-links.py"
    
    if [ ! -f "$script_path" ]; then
        log_error "❌ 错误: 链接修复脚本不存在"
        exit 1
    fi
    
    local args=()
    if [ "$AUTO_FIX" = true ]; then
        args+=("--auto")
    fi
    if [ "$APPLY" = true ]; then
        args+=("--apply")
    fi
    
    python3 "$script_path" "${args[@]}"
}

invoke_sync_check() {
    log_success "🔄 检查文档同步性..."
    
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local script_path="$script_dir/check-docs-sync.py"
    local project_root="$(dirname "$(dirname "$script_dir")")"
    
    if [ ! -f "$script_path" ]; then
        log_error "❌ 错误: 同步检查脚本不存在"
        exit 1
    fi
    
    local args=("--project-root" "$project_root")
    if [ -n "$OUTPUT" ]; then
        args+=("--output" "$OUTPUT")
    fi
    if [ "$FAIL_ON_ERROR" = true ]; then
        args+=("--fail-on-error")
    fi
    
    python3 "$script_path" "${args[@]}"
}

invoke_version_management() {
    log_success "📋 管理文档版本..."
    
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local script_path="$script_dir/docs-version-manager.sh"
    local project_root="$(dirname "$(dirname "$script_dir")")"
    
    if [ ! -f "$script_path" ]; then
        log_error "❌ 错误: 版本管理脚本不存在"
        exit 1
    fi
    
    local args=("--project-root" "$project_root")
    if [ "$SCAN" = true ]; then
        args+=("--scan")
    fi
    if [ "$ADD_HEADERS" = true ]; then
        args+=("--add-headers")
    fi
    if [ "$CLEANUP" -gt 0 ]; then
        args+=("--cleanup" "$CLEANUP")
    fi
    if [ -n "$EXPORT" ]; then
        args+=("--export" "$EXPORT")
    fi
    args+=("--check-outdated" "$CHECK_OUTDATED")
    
    "$script_path" "${args[@]}"
}

invoke_validation() {
    log_success "✅ 验证文档结构..."
    
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local project_root="$(dirname "$(dirname "$script_dir")")"
    
    # 切换到项目根目录进行验证
    cd "$project_root"
    
    # 验证 MkDocs 配置
    local config_script="$script_dir/validate-docs-config.py"
    if [ -f "$config_script" ]; then
        log_info "📋 验证 MkDocs 配置..."
        python3 "$config_script"
    fi
    
    # 验证文档结构（简化版本）
    log_info "📁 验证文档结构..."
    
    local required_dirs=(
        "docs/zh"
        "docs/en"
        "docs/zh/getting-started"
        "docs/zh/configuration"
        "docs/zh/api-reference"
    )
    
    local missing_dirs=()
    for dir in "${required_dirs[@]}"; do
        if [ -d "$dir" ]; then
            echo "✓ 目录存在: $dir"
        else
            echo "✗ 缺少目录: $dir"
            missing_dirs+=("$dir")
        fi
    done
    
    if [ ${#missing_dirs[@]} -eq 0 ]; then
        log_success "🎉 文档结构验证通过！"
    else
        log_error "❌ 发现 ${#missing_dirs[@]} 个缺少的目录"
        exit 1
    fi
}

# 解析命令行参数
parse_args() {
    if [ $# -eq 0 ]; then
        show_help
        exit 0
    fi
    
    COMMAND="$1"
    shift
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --host)
                HOST="$2"
                shift 2
                ;;
            --port)
                PORT="$2"
                shift 2
                ;;
            --output)
                OUTPUT="$2"
                shift 2
                ;;
            --fail-on-error)
                FAIL_ON_ERROR=true
                shift
                ;;
            --auto-fix)
                AUTO_FIX=true
                shift
                ;;
            --apply)
                APPLY=true
                shift
                ;;
            --scan)
                SCAN=true
                shift
                ;;
            --add-headers)
                ADD_HEADERS=true
                shift
                ;;
            --cleanup)
                CLEANUP="$2"
                shift 2
                ;;
            --export)
                EXPORT="$2"
                shift 2
                ;;
            --check-outdated)
                CHECK_OUTDATED="$2"
                shift 2
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 主函数
main() {
    parse_args "$@"
    
    case "$COMMAND" in
        serve)
            start_docs_server
            ;;
        check-links)
            invoke_link_check
            ;;
        fix-links)
            invoke_link_fix
            ;;
        check-sync)
            invoke_sync_check
            ;;
        version)
            invoke_version_management
            ;;
        validate)
            invoke_validation
            ;;
        help)
            show_help
            ;;
        *)
            log_error "❌ 未知命令: $COMMAND"
            show_help
            exit 1
            ;;
    esac
}

# 错误处理
trap 'log_error "❌ 脚本执行失败，退出码: $?"' ERR

# 执行主函数
main "$@"