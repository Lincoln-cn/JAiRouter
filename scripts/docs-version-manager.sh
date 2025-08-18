#!/bin/bash
# 文档版本管理脚本 (Shell 版本)
# 实现文档版本标识和更新提醒，追踪文档变更

set -euo pipefail

# 默认参数
PROJECT_ROOT="."
SCAN=false
REPORT=""
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

# 帮助信息
show_help() {
    cat << EOF
文档版本管理脚本

用法: $0 [选项]

选项:
    --project-root PATH     项目根目录 (默认: .)
    --scan                  扫描并更新版本信息
    --report PATH           生成版本报告到指定文件
    --add-headers           添加版本头信息
    --cleanup DAYS          清理指定天数前的变更记录
    --export PATH           导出版本数据到指定文件
    --check-outdated DAYS   检查过期文档的天数阈值 (默认: 30)
    --help                  显示此帮助信息

示例:
    $0 --scan --report report.md
    $0 --add-headers --export data.json
    $0 --cleanup 90 --check-outdated 30
EOF
}

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

# 检查依赖
check_dependencies() {
    local missing_deps=()
    
    if ! command -v python3 &> /dev/null; then
        missing_deps+=("python3")
    fi
    
    if ! command -v git &> /dev/null; then
        missing_deps+=("git")
    fi
    
    if [ ${#missing_deps[@]} -ne 0 ]; then
        log_error "❌ 缺少必要依赖: ${missing_deps[*]}"
        log_info "请安装缺少的依赖后重试"
        exit 1
    fi
}

# 检查 Python 模块
check_python_modules() {
    if ! python3 -c "import yaml" 2>/dev/null; then
        log_warning "⚠️ 缺少 PyYAML 模块，尝试安装..."
        if command -v pip3 &> /dev/null; then
            pip3 install pyyaml --user
        elif command -v pip &> /dev/null; then
            pip install pyyaml --user
        else
            log_error "❌ 无法安装 PyYAML，请手动安装: pip install pyyaml"
            exit 1
        fi
    fi
}

# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --project-root)
                PROJECT_ROOT="$2"
                shift 2
                ;;
            --scan)
                SCAN=true
                shift
                ;;
            --report)
                REPORT="$2"
                shift 2
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
            --help)
                show_help
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 确保项目目录存在
ensure_project_structure() {
    local kiro_dir="$PROJECT_ROOT/.kiro"
    
    if [ ! -d "$kiro_dir" ]; then
        log_info "📁 创建 .kiro 目录..."
        mkdir -p "$kiro_dir"
    fi
    
    # 确保版本配置文件存在
    local config_file="$kiro_dir/docs-version-config.yml"
    if [ ! -f "$config_file" ]; then
        log_info "📝 创建默认版本配置文件..."
        cat > "$config_file" << 'EOF'
# 文档版本管理配置文件

version_management:
  version_format: "semantic"
  auto_increment:
    major:
      - "breaking_change"
      - "api_change"
      - "major_restructure"
    minor:
      - "new_section"
      - "new_feature_doc"
      - "significant_update"
    patch:
      - "content_update"
      - "typo_fix"
      - "format_change"
      - "link_update"

document_scanning:
  include_patterns:
    - "docs/**/*.md"
    - "README*.md"
    - "*.md"
  exclude_patterns:
    - "node_modules/**"
    - ".git/**"
    - "target/**"
    - "build/**"

version_headers:
  enabled: true
  template: |
    <!-- 版本信息 -->
    > **文档版本**: {version}  
    > **最后更新**: {last_modified}  
    > **Git 提交**: {git_commit}  
    > **作者**: {author}
    <!-- /版本信息 -->
  position: "after_title"

outdated_detection:
  default_threshold_days: 30
EOF
    fi
}

# 调用 Python 版本管理器
call_python_manager() {
    local script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local python_script="$script_dir/docs-version-manager.py"
    
    if [ ! -f "$python_script" ]; then
        log_error "❌ Python 版本管理脚本不存在: $python_script"
        exit 1
    fi
    
    local args=("--project-root" "$PROJECT_ROOT")
    
    if [ "$SCAN" = true ]; then
        args+=("--scan")
    fi
    
    if [ -n "$REPORT" ]; then
        args+=("--report" "$REPORT")
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
    
    python3 "$python_script" "${args[@]}"
}

# 生成简单的统计报告
generate_simple_stats() {
    local version_file="$PROJECT_ROOT/.kiro/docs-versions.json"
    
    if [ ! -f "$version_file" ]; then
        log_warning "⚠️ 版本文件不存在，请先运行 --scan"
        return
    fi
    
    log_info "📊 文档版本统计:"
    
    # 使用 Python 解析 JSON 并生成统计
    python3 -c "
import json
import sys
from datetime import datetime, timedelta

try:
    with open('$version_file', 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    versions = data.get('versions', {})
    changes = data.get('changes', [])
    
    print(f'  - 总文档数: {len(versions)}')
    print(f'  - 变更记录数: {len(changes)}')
    
    # 最近7天的变更
    recent_changes = []
    cutoff_date = datetime.now() - timedelta(days=7)
    
    for change in changes:
        try:
            change_date = datetime.fromisoformat(change['Timestamp'].replace('Z', '+00:00'))
            if change_date > cutoff_date:
                recent_changes.append(change)
        except:
            pass
    
    print(f'  - 近7天变更: {len(recent_changes)}')
    
    # 过期文档检查
    outdated_count = 0
    threshold_date = datetime.now() - timedelta(days=$CHECK_OUTDATED)
    
    for path, version_info in versions.items():
        try:
            last_modified = datetime.fromisoformat(version_info['LastModified'].replace('Z', '+00:00'))
            if last_modified < threshold_date:
                outdated_count += 1
        except:
            outdated_count += 1
    
    print(f'  - 过期文档数: {outdated_count}')
    
    if recent_changes:
        print('\\n📝 最近变更:')
        for change in sorted(recent_changes, key=lambda x: x['Timestamp'], reverse=True)[:5]:
            timestamp = change['Timestamp'][:10]
            print(f'  - {change[\"ChangeType\"]}: {change[\"FilePath\"]} - {timestamp}')

except Exception as e:
    print(f'统计生成失败: {e}', file=sys.stderr)
    sys.exit(1)
"
}

# 主函数
main() {
    log_info "🚀 启动文档版本管理..."
    
    # 解析参数
    parse_args "$@"
    
    # 检查依赖
    check_dependencies
    check_python_modules
    
    # 确保项目结构
    ensure_project_structure
    
    # 调用 Python 版本管理器
    call_python_manager
    
    # 生成简单统计
    if [ "$SCAN" = true ] || [ -n "$REPORT" ]; then
        echo
        generate_simple_stats
    fi
    
    log_success "✅ 文档版本管理完成"
}

# 错误处理
trap 'log_error "❌ 脚本执行失败，退出码: $?"' ERR

# 执行主函数
main "$@"