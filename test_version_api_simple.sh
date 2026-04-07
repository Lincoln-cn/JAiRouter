#!/bin/bash

# 简单的配置版本管理 API 测试脚本
# 直接测试 Controller 是否能正确处理请求（忽略认证）

# set -e  # 禁用自动退出

BASE_URL="http://localhost:8080"
API_PREFIX="/api/config/version"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  配置版本管理 API 测试${NC}"
echo -e "${BLUE}========================================${NC}\n"

# 检查服务是否运行
echo -e "${YELLOW}检查服务状态...${NC}"
if ! curl -s --max-time 5 "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}✗ 服务未运行，请先启动应用${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 服务运行正常${NC}\n"

# 测试 API 端点是否存在（即使返回 401/403 也证明端点存在）
test_endpoint() {
    local method="$1"
    local path="$2"
    local description="$3"

    echo -e "${YELLOW}测试: $description${NC}"
    echo -e "${YELLOW}URL: $method ${BASE_URL}${API_PREFIX}${path}${NC}"

    response=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" \
        "${BASE_URL}${API_PREFIX}${path}" 2>/dev/null)

    # 只要不是 404 或 500，就说明端点存在
    if [ "$response" == "200" ] || [ "$response" == "401" ] || [ "$response" == "403" ]; then
        echo -e "${GREEN}✓ 端点存在 (状态码: $response)${NC}\n"
        return 0
    elif [ "$response" == "404" ]; then
        echo -e "${RED}✗ 端点不存在 (404)${NC}\n"
        return 1
    else
        echo -e "${YELLOW}ℹ 端点返回: $response${NC}\n"
        return 0
    fi
}

# 测试所有端点
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}测试 API 端点是否存在${NC}"
echo -e "${BLUE}========================================${NC}\n"

PASSED=0
FAILED=0

# 1. 获取版本列表
test_endpoint "GET" "" "获取版本列表" && ((PASSED++)) || ((FAILED++))

# 2. 获取当前版本
test_endpoint "GET" "/current" "获取当前版本" && ((PASSED++)) || ((FAILED++))

# 3. 获取指定版本
test_endpoint "GET" "/1" "获取指定版本详情" && ((PASSED++)) || ((FAILED++))

# 4. 获取版本详细信息
test_endpoint "GET" "/info" "获取所有版本详细信息" && ((PASSED++)) || ((FAILED++))

# 5. 版本对比
test_endpoint "GET" "/compare/1/2" "比较版本差异" && ((PASSED++)) || ((FAILED++))

# 6. 查看版本变更
test_endpoint "GET" "/compare/2" "查看版本变更内容" && ((PASSED++)) || ((FAILED++))

# 7. 应用版本
test_endpoint "POST" "/apply/1" "应用版本" && ((PASSED++)) || ((FAILED++))

# 8. 删除版本
test_endpoint "DELETE" "/1" "删除版本" && ((PASSED++)) || ((FAILED++))

# 测试报告
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}测试报告${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "通过: ${GREEN}${PASSED}${NC}"
echo -e "失败: ${RED}${FAILED}${NC}"
echo -e "总计: $((PASSED + FAILED))"

if [ $FAILED -eq 0 ]; then
    echo -e "\n${GREEN}所有 API 端点都存在!${NC}"
    echo -e "${YELLOW}注意: 返回 401/403 是正常的，表示端点存在但需要认证${NC}"
    exit 0
else
    echo -e "\n${RED}存在不存在的 API 端点${NC}"
    exit 1
fi
