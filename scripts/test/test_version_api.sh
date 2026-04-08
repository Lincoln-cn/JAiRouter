#!/bin/bash

# 配置版本管理 API 测试脚本
# 测试配置版本管理的所有 RESTful 端点

# set -e  # 禁用自动退出，我们需要自己处理错误

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
BASE_URL="http://localhost:8080"
API_PREFIX="/api/config/version"
COOKIE_JAR="/tmp/cookies.txt"
API_KEY="dev-admin-12345-abcde-67890-fghij"
AUTH_HEADER="X-API-Key: ${API_KEY}"

# 计数器
PASSED=0
FAILED=0

# 打印函数
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
    ((PASSED++))
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
    if [ -n "$2" ]; then
        echo -e "${RED}  错误: $2${NC}"
    fi
    ((FAILED++))
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

# 检查服务是否运行
check_service() {
    print_header "检查服务状态"
    if curl -s --max-time 5 "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
        print_success "服务运行正常"
        return 0
    else
        print_error "服务未运行" "请确保应用已启动在 ${BASE_URL}"
        exit 1
    fi
}

# 执行 GET 请求
http_get() {
    local url="$1"
    local description="$2"
    local expect_status="${3:-200}"

    print_info "测试: $description"
    print_info "URL: GET $url"

    response=$(curl -s -w "\n%{http_code}" \
        -H "Accept: application/json" \
        -H "${AUTH_HEADER}" \
        "$url" 2>/dev/null)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "$expect_status" ]; then
        print_success "状态码: $http_code"
        echo "$body"
        return 0
    else
        print_error "状态码: $http_code (期望: $expect_status)" "$body"
        return 1
    fi
}

# 执行 POST 请求
http_post() {
    local url="$1"
    local description="$2"
    local data="${3:-}"
    local expect_status="${4:-200}"

    print_info "测试: $description"
    print_info "URL: POST $url"

    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X POST \
            -H "Content-Type: application/json" \
            -H "Accept: application/json" \
            -H "${AUTH_HEADER}" \
            -d "$data" \
            "$url" 2>/dev/null)
    else
        response=$(curl -s -w "\n%{http_code}" -X POST \
            -H "Accept: application/json" \
            -H "${AUTH_HEADER}" \
            "$url" 2>/dev/null)
    fi

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "$expect_status" ]; then
        print_success "状态码: $http_code"
        echo "$body"
        return 0
    else
        print_error "状态码: $http_code (期望: $expect_status)" "$body"
        return 1
    fi
}

# 执行 DELETE 请求
http_delete() {
    local url="$1"
    local description="$2"
    local expect_status="${3:-200}"

    print_info "测试: $description"
    print_info "URL: DELETE $url"

    response=$(curl -s -w "\n%{http_code}" -X DELETE \
        -H "Accept: application/json" \
        -H "${AUTH_HEADER}" \
        "$url" 2>/dev/null)

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "$expect_status" ]; then
        print_success "状态码: $http_code"
        echo "$body"
        return 0
    else
        print_error "状态码: $http_code (期望: $expect_status)" "$body"
        return 1
    fi
}

# 测试 1: 获取版本列表
test_get_versions() {
    print_header "测试 1: 获取配置版本列表"

    result=$(http_get "${BASE_URL}${API_PREFIX}" "获取所有版本号")
    if [ $? -eq 0 ]; then
        print_info "响应: $result"

        # 检查是否为数组
        if echo "$result" | grep -q '"data"'; then
            print_success "返回格式正确 (包含 data 字段)"
        fi
    fi
}

# 测试 2: 获取当前版本
test_get_current_version() {
    print_header "测试 2: 获取当前配置版本"

    result=$(http_get "${BASE_URL}${API_PREFIX}/current" "获取当前版本")
    if [ $? -eq 0 ]; then
        print_info "响应: $result"

        # 提取版本号
        current_version=$(echo "$result" | grep -o '"data":[0-9]*' | cut -d':' -f2)
        if [ -n "$current_version" ]; then
            print_success "当前版本: $current_version"
            export CURRENT_VERSION=$current_version
        fi
    fi
}

# 测试 3: 获取所有版本详细信息
test_get_version_info() {
    print_header "测试 3: 获取所有版本详细信息"

    result=$(http_get "${BASE_URL}${API_PREFIX}/info" "获取版本详细信息")
    if [ $? -eq 0 ]; then
        print_info "响应: $result"

        # 统计版本数量
        version_count=$(echo "$result" | grep -o '"version":[0-9]*' | wc -l)
        print_success "找到 $version_count 个版本"
    fi
}

# 测试 4: 获取指定版本详情
test_get_specific_version() {
    print_header "测试 4: 获取指定版本配置详情"

    # 如果没有当前版本，尝试获取版本 1
    local version_to_test="${CURRENT_VERSION:-1}"

    result=$(http_get "${BASE_URL}${API_PREFIX}/${version_to_test}" "获取版本 $version_to_test 详情")
    if [ $? -eq 0 ]; then
        print_info "响应片段: ${result:0:200}..."

        if echo "$result" | grep -q '"services"\|"models"\|"endpoints"'; then
            print_success "配置内容正确"
        fi
    fi
}

# 测试 5: 比较两个版本差异
test_compare_versions() {
    print_header "测试 5: 比较版本差异"

    # 获取版本列表
    versions_response=$(curl -s "${BASE_URL}${API_PREFIX}" 2>/dev/null)

    # 提取版本号 (简化提取，假设至少有 2 个版本)
    v1=$(echo "$versions_response" | grep -oP '"data":\s*\[\s*\K[0-9]+' | head -1)
    v2=$(echo "$versions_response" | grep -oP '"data":\s*\[\s*[0-9]+,\s*\K[0-9]+' | head -1)

    if [ -n "$v1" ] && [ -n "$v2" ]; then
        print_info "比较版本 $v1 和 $v2"

        result=$(http_get "${BASE_URL}${API_PREFIX}/compare/${v1}/${v2}" "版本对比 ($v1 vs $v2)")
        if [ $? -eq 0 ]; then
            print_info "响应片段: ${result:0:300}..."

            # 检查 diff 结构
            if echo "$result" | grep -q '"added"\|"removed"\|"modified"'; then
                print_success "返回差异结构正确"

                # 统计变更数
                added=$(echo "$result" | grep -o '"added":\[' | wc -l)
                removed=$(echo "$result" | grep -o '"removed":\[' | wc -l)
                modified=$(echo "$result" | grep -o '"modified":\[' | wc -l)
                print_info "变更统计 - 新增: $added, 删除: $removed, 修改: $modified"
            fi
        fi
    else
        print_info "版本数量不足，跳过对比测试"
    fi
}

# 测试 6: 查看指定版本的变更内容
test_version_changes() {
    print_header "测试 6: 查看版本变更内容"

    local version_to_test="${CURRENT_VERSION:-1}"

    result=$(http_get "${BASE_URL}${API_PREFIX}/compare/${version_to_test}" "版本 $version_to_test 的变更")
    if [ $? -eq 0 ]; then
        print_info "响应片段: ${result:0:300}..."

        if echo "$result" | grep -q '"sourceVersion"\|"targetVersion"'; then
            print_success "变更内容结构正确"
        fi
    fi
}

# 测试 7: 错误处理测试
test_error_handling() {
    print_header "测试 7: 错误处理测试"

    # 测试不存在的版本
    print_info "测试: 获取不存在的版本 (99999)"
    http_get "${BASE_URL}${API_PREFIX}/99999" "获取不存在的版本" "404\|400\|500"

    # 测试无效的版本对比
    print_info "测试: 相同版本对比"
    http_get "${BASE_URL}${API_PREFIX}/compare/1/1" "相同版本对比" "400"

    # 测试负数版本
    print_info "测试: 负数版本"
    http_get "${BASE_URL}${API_PREFIX}/-1" "负数版本" "400\|404\|500"
}

# 测试 8: 应用版本（谨慎测试）
test_apply_version() {
    print_header "测试 8: 应用版本（回滚测试）"

    local version_to_apply="${CURRENT_VERSION:-1}"

    print_info "注意: 此测试将尝试应用版本 $version_to_apply"
    print_info "如需跳过，请按 Ctrl+C，5 秒后继续..."
    sleep 5

    result=$(http_post "${BASE_URL}${API_PREFIX}/apply/${version_to_apply}" "应用版本 $version_to_apply")
    if [ $? -eq 0 ]; then
        print_success "版本应用成功"
        print_info "响应: $result"
    fi
}

# 打印测试报告
print_report() {
    print_header "测试报告"

    echo -e "通过: ${GREEN}${PASSED}${NC}"
    echo -e "失败: ${RED}${FAILED}${NC}"
    echo -e "总计: $((PASSED + FAILED))"

    if [ $FAILED -eq 0 ]; then
        echo -e "\n${GREEN}所有测试通过!${NC}"
        return 0
    else
        echo -e "\n${RED}存在失败的测试${NC}"
        return 1
    fi
}

# 主函数
main() {
    echo -e "${BLUE}"
    echo "========================================"
    echo "  配置版本管理 API 测试脚本"
    echo "========================================"
    echo -e "${NC}"

    # 检查依赖
    if ! command -v curl &> /dev/null; then
        echo "错误: 需要安装 curl"
        exit 1
    fi

    # 检查服务
    check_service

    # 执行测试
    test_get_versions
    test_get_current_version
    test_get_version_info
    test_get_specific_version
    test_compare_versions
    test_version_changes
    test_error_handling

    # 可选：应用版本测试（有风险）
    # test_apply_version

    # 打印报告
    print_report
}

# 处理参数
case "${1:-}" in
    --apply)
        # 包含应用版本测试
        main
        test_apply_version
        ;;
    --quick)
        # 快速测试（不包含错误处理）
        check_service
        test_get_versions
        test_get_current_version
        test_compare_versions
        print_report
        ;;
    --help|-h)
        echo "用法: $0 [选项]"
        echo ""
        echo "选项:"
        echo "  --apply    包含应用版本测试（谨慎使用）"
        echo "  --quick    快速测试（仅基本功能）"
        echo "  --help     显示帮助"
        echo ""
        echo "环境变量:"
        echo "  BASE_URL   API 基础 URL (默认: http://localhost:8080)"
        exit 0
        ;;
    *)
        main
        ;;
esac
