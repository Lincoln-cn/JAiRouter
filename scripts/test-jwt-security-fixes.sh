#!/bin/bash

# JWT安全修复测试脚本
# 测试JWT撤销功能和IP地址获取功能

set -e

# 配置
BASE_URL="http://localhost:8080"
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="admin123"

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

# 检查服务是否运行
check_service() {
    log_info "检查JAiRouter服务状态..."
    
    # 使用登录请求检查服务状态，避免健康检查端点的安全限制
    local test_response=$(curl --noproxy localhost -s -X POST "$BASE_URL/api/auth/jwt/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"test","password":"test"}' 2>/dev/null)
    
    if echo "$test_response" | grep -q '"success":true\|"success":false'; then
        log_success "JAiRouter服务正在运行（登录接口可访问）"
    else
        log_error "JAiRouter服务未运行或登录接口不可访问"
        log_error "响应: $test_response"
        exit 1
    fi
}

# 获取JWT令牌
get_jwt_token() {
    log_info "获取JWT令牌..."
    
    local response=$(curl --noproxy localhost -s -X POST "$BASE_URL/api/auth/jwt/login" \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: 203.0.113.1, 192.168.1.100" \
        -H "X-Real-IP: 203.0.113.1" \
        -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")
    
    if echo "$response" | grep -q '"success":true'; then
        local token=$(echo "$response" | jq -r '.data.token' 2>/dev/null)
        if [ "$token" != "null" ] && [ -n "$token" ]; then
            log_success "JWT令牌获取成功"
            echo "$token"
            return 0
        fi
    fi
    
    log_error "JWT令牌获取失败: $response"
    return 1
}

# 测试IP地址获取
test_ip_address() {
    local token="$1"
    log_info "测试客户端IP地址获取..."
    
    local response=$(curl -s -X GET "$BASE_URL/api/debug/security/client-ip" \
        -H "Authorization: Bearer $token" \
        -H "X-Forwarded-For: 203.0.113.1, 192.168.1.100" \
        -H "X-Real-IP: 203.0.113.1" \
        -H "Proxy-Client-IP: 203.0.113.2")
    
    if echo "$response" | grep -q '"success":true'; then
        local client_ip=$(echo "$response" | jq -r '.data.clientIp' 2>/dev/null)
        log_success "客户端IP获取成功: $client_ip"
        
        # 检查是否获取到了正确的IP（应该是X-Forwarded-For的第一个IP）
        if [ "$client_ip" = "203.0.113.1" ]; then
            log_success "IP地址获取正确，从X-Forwarded-For头获取到真实IP"
        elif [ "$client_ip" = "unknown" ] || [ "$client_ip" = "0.0.0.0" ]; then
            log_warning "IP地址获取异常: $client_ip"
            echo "详细信息:"
            echo "$response" | jq '.data.details' 2>/dev/null || echo "$response"
        else
            log_info "获取到IP地址: $client_ip (可能是代理或本地IP)"
        fi
        
        return 0
    else
        log_error "IP地址获取测试失败: $response"
        return 1
    fi
}

# 测试令牌验证（撤销前）
test_token_validation_before_revoke() {
    local token="$1"
    log_info "测试令牌验证（撤销前）..."
    
    local response=$(curl -s -X POST "$BASE_URL/api/auth/jwt/validate" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d "{\"token\":\"$token\"}")
    
    if echo "$response" | grep -q '"valid":true'; then
        log_success "令牌验证成功（撤销前）"
        return 0
    else
        log_error "令牌验证失败（撤销前）: $response"
        return 1
    fi
}

# 撤销JWT令牌
revoke_jwt_token() {
    local token="$1"
    log_info "撤销JWT令牌..."
    
    local response=$(curl -s -X POST "$BASE_URL/api/auth/jwt/revoke" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -d "{\"token\":\"$token\",\"reason\":\"测试撤销功能\"}")
    
    if echo "$response" | grep -q '"success":true'; then
        log_success "JWT令牌撤销成功"
        return 0
    else
        log_error "JWT令牌撤销失败: $response"
        return 1
    fi
}

# 测试令牌验证（撤销后）
test_token_validation_after_revoke() {
    local token="$1"
    log_info "测试令牌验证（撤销后）..."
    
    # 等待一下确保撤销生效
    sleep 2
    
    local response=$(curl -s -X POST "$BASE_URL/api/auth/jwt/validate" \
        -H "Content-Type: application/json" \
        -d "{\"token\":\"$token\"}")
    
    if echo "$response" | grep -q '"valid":false'; then
        log_success "令牌验证正确返回无效（撤销后）"
        return 0
    elif echo "$response" | grep -q '"valid":true'; then
        log_error "令牌撤销后仍然有效，撤销功能可能存在问题"
        return 1
    else
        log_warning "令牌验证响应异常（撤销后）: $response"
        return 1
    fi
}

# 测试使用撤销的令牌访问受保护资源
test_access_with_revoked_token() {
    local token="$1"
    log_info "测试使用撤销的令牌访问受保护资源..."
    
    local response=$(curl -s -w "%{http_code}" -X GET "$BASE_URL/api/debug/security/client-ip" \
        -H "Authorization: Bearer $token")
    
    local http_code="${response: -3}"
    local body="${response%???}"
    
    if [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
        log_success "撤销的令牌正确被拒绝访问 (HTTP $http_code)"
        return 0
    elif [ "$http_code" = "200" ]; then
        log_error "撤销的令牌仍能访问受保护资源，存在安全漏洞"
        echo "响应内容: $body"
        return 1
    else
        log_warning "意外的HTTP状态码: $http_code"
        echo "响应内容: $body"
        return 1
    fi
}

# 测试黑名单功能
test_blacklist_functionality() {
    local token="$1"
    log_info "测试黑名单功能..."
    
    # 计算令牌哈希
    local token_hash=$(echo -n "$token" | sha256sum | cut -d' ' -f1 | base64 -w 0)
    
    # 测试黑名单添加和检查
    local response=$(curl -s -X POST "$BASE_URL/api/debug/security/blacklist/test" \
        -H "Authorization: Bearer $token" \
        -d "tokenId=$token_hash&ttlSeconds=3600")
    
    if echo "$response" | grep -q '"success":true'; then
        log_success "黑名单功能测试成功"
        
        # 检查统计信息
        local stats_response=$(curl -s -X GET "$BASE_URL/api/debug/security/blacklist/stats" \
            -H "Authorization: Bearer $token")
        
        if echo "$stats_response" | grep -q '"success":true'; then
            log_success "黑名单统计信息获取成功"
            echo "黑名单统计:"
            echo "$stats_response" | jq '.data' 2>/dev/null || echo "$stats_response"
        else
            log_warning "黑名单统计信息获取失败"
        fi
        
        return 0
    else
        log_error "黑名单功能测试失败: $response"
        return 1
    fi
}

# 主测试流程
main() {
    echo "=== JWT安全修复测试 ==="
    echo "测试时间: $(date)"
    echo ""
    
    # 检查服务状态
    check_service
    echo ""
    
    # 获取JWT令牌
    local token
    if token=$(get_jwt_token); then
        echo "获取到的令牌: ${token:0:50}..."
        echo ""
    else
        log_error "无法获取JWT令牌，测试终止"
        exit 1
    fi
    
    # 测试IP地址获取
    test_ip_address "$token"
    echo ""
    
    # 测试令牌验证（撤销前）
    test_token_validation_before_revoke "$token"
    echo ""
    
    # 测试黑名单功能
    test_blacklist_functionality "$token"
    echo ""
    
    # 撤销JWT令牌
    revoke_jwt_token "$token"
    echo ""
    
    # 测试令牌验证（撤销后）
    test_token_validation_after_revoke "$token"
    echo ""
    
    # 测试使用撤销的令牌访问受保护资源
    test_access_with_revoked_token "$token"
    echo ""
    
    log_success "JWT安全修复测试完成"
    
    echo ""
    echo "=== 测试总结 ==="
    echo "1. IP地址获取功能已测试"
    echo "2. JWT令牌撤销功能已测试"
    echo "3. 黑名单功能已测试"
    echo "4. 撤销后的访问控制已测试"
    echo ""
    echo "如果所有测试都通过，说明JWT安全修复生效"
}

# 处理命令行参数
case "${1:-}" in
    --help|-h)
        echo "用法: $0 [选项]"
        echo ""
        echo "选项:"
        echo "  --help, -h     显示帮助信息"
        echo "  --base-url URL 指定服务基础URL (默认: $BASE_URL)"
        echo "  --username USER 指定用户名 (默认: $ADMIN_USERNAME)"
        echo "  --password PASS 指定密码 (默认: $ADMIN_PASSWORD)"
        exit 0
        ;;
    --base-url)
        BASE_URL="$2"
        shift 2
        ;;
    --username)
        ADMIN_USERNAME="$2"
        shift 2
        ;;
    --password)
        ADMIN_PASSWORD="$2"
        shift 2
        ;;
esac

# 检查依赖
if ! command -v curl >/dev/null 2>&1; then
    log_error "curl 未安装，请先安装 curl"
    exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
    log_warning "jq 未安装，JSON解析可能不完整"
fi

# 运行主测试
main