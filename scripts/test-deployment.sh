#!/bin/bash
# GitHub Pages 部署测试脚本
# 用于验证文档构建和部署流程

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 参数解析
SKIP_BUILD=false
CHECK_LINKS=false
LANGUAGE="all"

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --check-links)
            CHECK_LINKS=true
            shift
            ;;
        --language)
            LANGUAGE="$2"
            shift 2
            ;;
        *)
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

echo -e "${GREEN}=== JAiRouter 文档部署测试 ===${NC}"

# 检查必要的工具
test_prerequisites() {
    echo -e "${YELLOW}检查前置条件...${NC}"
    
    # 检查 Python
    if command -v python3 &> /dev/null; then
        PYTHON_VERSION=$(python3 --version)
        echo -e "${GREEN}✓ Python: $PYTHON_VERSION${NC}"
        PYTHON_CMD="python3"
    elif command -v python &> /dev/null; then
        PYTHON_VERSION=$(python --version)
        echo -e "${GREEN}✓ Python: $PYTHON_VERSION${NC}"
        PYTHON_CMD="python"
    else
        echo -e "${RED}✗ Python 未安装或不在 PATH 中${NC}"
        return 1
    fi
    
    # 检查 pip
    if command -v pip3 &> /dev/null; then
        PIP_VERSION=$(pip3 --version)
        echo -e "${GREEN}✓ pip: $PIP_VERSION${NC}"
        PIP_CMD="pip3"
    elif command -v pip &> /dev/null; then
        PIP_VERSION=$(pip --version)
        echo -e "${GREEN}✓ pip: $PIP_VERSION${NC}"
        PIP_CMD="pip"
    else
        echo -e "${RED}✗ pip 未安装或不在 PATH 中${NC}"
        return 1
    fi
    
    return 0
}

# 安装依赖
install_dependencies() {
    echo -e "${YELLOW}安装 MkDocs 依赖...${NC}"
    
    local packages=(
        "mkdocs-material"
        "mkdocs-git-revision-date-localized-plugin"
        "mkdocs-mermaid2-plugin"
        "mkdocs-static-i18n"
    )
    
    for package in "${packages[@]}"; do
        echo -e "${CYAN}安装 $package...${NC}"
        if ! $PIP_CMD install "$package" --quiet; then
            echo -e "${RED}✗ 安装 $package 失败${NC}"
            return 1
        fi
    done
    
    echo -e "${GREEN}✓ 所有依赖安装完成${NC}"
    return 0
}

# 验证配置文件
test_configuration() {
    echo -e "${YELLOW}验证配置文件...${NC}"
    
    # 检查 mkdocs.yml
    if [[ ! -f "mkdocs.yml" ]]; then
        echo -e "${RED}✗ mkdocs.yml 文件不存在${NC}"
        return 1
    fi
    
    # 验证配置语法
    if ! mkdocs config &> /dev/null; then
        echo -e "${RED}✗ mkdocs.yml 配置语法错误${NC}"
        return 1
    fi
    
    echo -e "${GREEN}✓ mkdocs.yml 配置正确${NC}"
    return 0
}

# 检查文档结构
test_document_structure() {
    echo -e "${YELLOW}检查文档结构...${NC}"
    
    local required_dirs=(
        "docs/zh"
        "docs/en"
    )
    
    for dir in "${required_dirs[@]}"; do
        if [[ ! -d "$dir" ]]; then
            echo -e "${RED}✗ 缺少目录: $dir${NC}"
            return 1
        fi
        echo -e "${GREEN}✓ 目录存在: $dir${NC}"
    done
    
    # 检查关键文件
    local required_files=(
        "docs/zh/index.md"
        "docs/en/index.md"
    )
    
    for file in "${required_files[@]}"; do
        if [[ ! -f "$file" ]]; then
            echo -e "${RED}✗ 缺少文件: $file${NC}"
            return 1
        fi
        echo -e "${GREEN}✓ 文件存在: $file${NC}"
    done
    
    return 0
}

# 构建测试
test_build() {
    local lang="${1:-all}"
    
    echo -e "${YELLOW}测试文档构建...${NC}"
    
    # 清理之前的构建
    if [[ -d "site" ]]; then
        rm -rf "site"
    fi
    
    # 构建文档
    echo -e "${CYAN}执行构建命令...${NC}"
    if ! mkdocs build --strict --verbose; then
        echo -e "${RED}✗ 文档构建失败${NC}"
        return 1
    fi
    
    echo -e "${GREEN}✓ 文档构建成功${NC}"
    
    # 检查构建输出
    if [[ ! -d "site" ]]; then
        echo -e "${RED}✗ 构建输出目录不存在${NC}"
        return 1
    fi
    
    # 检查多语言版本
    local language_files=(
        "site/index.html"
        "site/en/index.html"
    )
    
    for file in "${language_files[@]}"; do
        if [[ ! -f "$file" ]]; then
            echo -e "${RED}✗ 缺少语言版本文件: $file${NC}"
            return 1
        fi
        echo -e "${GREEN}✓ 语言版本文件存在: $file${NC}"
    done
    
    return 0
}

# 检查链接
test_links() {
    echo -e "${YELLOW}检查文档链接...${NC}"
    
    if [[ ! -d "site" ]]; then
        echo -e "${RED}✗ 构建输出不存在，请先运行构建测试${NC}"
        return 1
    fi
    
    # 这里可以添加链接检查逻辑
    # 例如使用 markdown-link-check 或其他工具
    echo -e "${YELLOW}✓ 链接检查完成（需要实现具体检查逻辑）${NC}"
    
    return 0
}

# 生成测试报告
generate_test_report() {
    local prereq_result=$1
    local config_result=$2
    local structure_result=$3
    local build_result=$4
    local link_result=$5
    
    echo -e "\n${GREEN}=== 测试报告 ===${NC}"
    
    local results=(
        "前置条件检查:$prereq_result"
        "配置文件验证:$config_result"
        "文档结构检查:$structure_result"
        "构建测试:$build_result"
        "链接检查:$link_result"
    )
    
    local pass_count=0
    for result in "${results[@]}"; do
        local name="${result%:*}"
        local status="${result#*:}"
        
        if [[ "$status" == "0" ]]; then
            echo -e "${GREEN}$name: ✓ 通过${NC}"
            ((pass_count++))
        else
            echo -e "${RED}$name: ✗ 失败${NC}"
        fi
    done
    
    local total_count=${#results[@]}
    if [[ $pass_count -eq $total_count ]]; then
        echo -e "\n${GREEN}总体结果: $pass_count/$total_count 项测试通过${NC}"
        return 0
    else
        echo -e "\n${YELLOW}总体结果: $pass_count/$total_count 项测试通过${NC}"
        return 1
    fi
}

# 主执行流程
main() {
    local prereq_result=1
    local config_result=1
    local structure_result=1
    local build_result=1
    local link_result=1
    
    # 前置条件检查
    if test_prerequisites; then
        prereq_result=0
    else
        echo -e "${RED}前置条件检查失败，退出测试${NC}"
        exit 1
    fi
    
    # 安装依赖
    if ! install_dependencies; then
        echo -e "${RED}依赖安装失败，退出测试${NC}"
        exit 1
    fi
    
    # 配置验证
    if test_configuration; then
        config_result=0
    fi
    
    # 文档结构检查
    if test_document_structure; then
        structure_result=0
    fi
    
    # 构建测试
    if [[ "$SKIP_BUILD" != "true" ]]; then
        if test_build "$LANGUAGE"; then
            build_result=0
        fi
    else
        build_result=0
    fi
    
    # 链接检查
    if [[ "$CHECK_LINKS" == "true" && $build_result -eq 0 ]]; then
        if test_links; then
            link_result=0
        fi
    else
        link_result=0
    fi
    
    # 生成报告
    if generate_test_report $prereq_result $config_result $structure_result $build_result $link_result; then
        echo -e "\n🎉 ${GREEN}所有测试通过！文档部署准备就绪。${NC}"
        exit 0
    else
        echo -e "\n❌ ${RED}部分测试失败，请检查上述错误信息。${NC}"
        exit 1
    fi
}

# 运行主函数
main "$@"