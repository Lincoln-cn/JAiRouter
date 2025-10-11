#!/bin/bash

# ========================================
# API Key 测试脚本
# ========================================
# 此脚本用于快速测试API Key是否有效

set -e

# 配置参数
BASE_URL=${1:-"http://localhost:8080"}
API_KEY="dev-admin-12345-abcde-67890-fghij"

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

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

echo "========================================"
echo "        API Key 测试脚本"
echo "========================================"
echo

log_info "测试服务器: $BASE_URL"
log_info "使用API Key: $API_KEY"
echo

# 测试健康检查端点
log_info "测试健康检查端点..."
response=$(curl -s -w "\n%{http_code}" \
    -H "X-API-Key: $API_KEY" \
    "$BASE_URL/actuator/health" 2>/dev/null || echo -e "\n000")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    log_success "健康检查成功 (200)"
    echo "响应: $body"
elif [ "$http_code" = "401" ]; then
    log_error "认证失败 (401) - API Key无效或已过期"
elif [ "$http_code" = "403" ]; then
    log_error "权限不足 (403) - API Key没有访问权限"
else
    log_error "请求失败 ($http_code)"
fi

echo

# 测试追踪统计端点
log_info "测试追踪统计端点..."
response=$(curl -s -w "\n%{http_code}" \
    -H "X-API-Key: $API_KEY" \
    "$BASE_URL/api/tracing/query/statistics" 2>/dev/null || echo -e "\n000")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" = "200" ]; then
    log_success "追踪统计访问成功 (200)"
    if command -v jq &> /dev/null; then
        echo "$body" | jq .
    else
        echo "响应: $body"
    fi
elif [ "$http_code" = "401" ]; then
    log_error "认证失败 (401) - API Key无效或已过期"
elif [ "$http_code" = "403" ]; then
    log_error "权限不足 (403) - API Key没有访问权限"
else
    log_error "请求失败 ($http_code)"
fi

echo

if [ "$http_code" = "200" ]; then
    log_success "API Key 测试通过！可以运行追踪数据生成脚本"
    echo
    log_info "运行追踪数据生成脚本:"
    echo "  ./generate-tracing-data.sh $BASE_URL 10"
else
    log_error "API Key 测试失败！请检查配置"
    echo
    log_info "可能的解决方案:"
    echo "  1. 确保服务器正在运行"
    echo "  2. 检查API Key配置文件: config-dev/security.api-keys.json"
    echo "  3. 确保安全功能已启用"
    echo "  4. 检查API Key是否已过期"
fi