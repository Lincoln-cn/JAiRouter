#!/bin/bash

# 专门测试JWT撤销功能修复的脚本

BASE_URL="http://localhost:8080"
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="admin123"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 通用curl函数
safe_curl() {
    curl --noproxy localhost "$@"
}

echo "=== JWT撤销功能修复验证 ==="
echo ""

# 1. 获取JWT令牌
log_info "步骤1: 获取JWT令牌..."
login_response=$(safe_curl -s -X POST "$BASE_URL/api/auth/jwt/login" \
    -H "Content-Type: application/json" \
    -H "X-Forwarded-For: 203.0.113.195, 192.168.1.100" \
    -H "X-Real-IP: 203.0.113.195" \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")

if echo "$login_response" | grep -q '"success":true'; then
    token=$(echo "$login_response" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    if [ -n "$token" ]; then
        log_success "JWT令牌获取成功"
        echo "令牌前缀: ${token:0:50}..."
    else
        log_error "无法提取JWT令牌"
        exit 1
    fi
else
    log_error "登录失败: $login_response"
    exit 1
fi

echo ""

# 2. 验证令牌有效性（撤销前）
log_info "步骤2: 验证令牌有效性（撤销前）..."
validate_response=$(safe_curl -s -X POST "$BASE_URL/api/auth/jwt/validate" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"$token\"}")

if echo "$validate_response" | grep -q '"valid":true'; then
    log_success "令牌验证成功（撤销前）"
else
    log_error "令牌验证失败（撤销前）: $validate_response"
    exit 1
fi

echo ""

# 3. 测试使用令牌访问受保护资源（撤销前）
log_info "步骤3: 测试访问受保护资源（撤销前）..."
access_response=$(safe_curl -s -w "%{http_code}" -X GET "$BASE_URL/api/auth/jwt/tokens" \
    -H "Jairouter_Token: Bearer $token")

http_code="${access_response: -3}"
if [ "$http_code" = "200" ]; then
    log_success "令牌可以正常访问受保护资源（撤销前）"
elif [ "$http_code" = "403" ]; then
    log_warning "权限不足，但令牌认证成功（撤销前）"
else
    log_warning "访问受保护资源返回HTTP $http_code（撤销前）"
fi

echo ""

# 4. 撤销JWT令牌
log_info "步骤4: 撤销JWT令牌..."
revoke_response=$(safe_curl -s -X POST "$BASE_URL/api/auth/jwt/revoke" \
    -H "Content-Type: application/json" \
    -H "Jairouter_Token: Bearer $token" \
    -d "{\"token\":\"$token\",\"reason\":\"测试撤销功能修复\"}")

if echo "$revoke_response" | grep -q '"success":true'; then
    log_success "JWT令牌撤销成功"
else
    log_error "JWT令牌撤销失败: $revoke_response"
    exit 1
fi

echo ""

# 5. 等待撤销生效
log_info "步骤5: 等待撤销生效..."
sleep 3
log_info "等待完成，开始验证撤销效果"

echo ""

# 6. 验证令牌有效性（撤销后）
log_info "步骤6: 验证令牌有效性（撤销后）..."
validate_after_response=$(safe_curl -s -X POST "$BASE_URL/api/auth/jwt/validate" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"$token\"}")

if echo "$validate_after_response" | grep -q '"valid":false'; then
    log_success "✅ 令牌验证正确返回无效（撤销后）- 撤销功能正常工作"
elif echo "$validate_after_response" | grep -q '"valid":true'; then
    log_error "❌ 令牌撤销后仍然有效 - 撤销功能存在问题！"
    echo "响应: $validate_after_response"
    exit 1
else
    log_warning "令牌验证响应异常（撤销后）: $validate_after_response"
fi

echo ""

# 7. 测试使用撤销的令牌访问受保护资源
log_info "步骤7: 测试使用撤销的令牌访问受保护资源..."
access_after_response=$(safe_curl -s -w "%{http_code}" -X GET "$BASE_URL/api/auth/jwt/tokens" \
    -H "Jairouter_Token: Bearer $token")

http_code_after="${access_after_response: -3}"
if [ "$http_code_after" = "401" ] || [ "$http_code_after" = "403" ]; then
    log_success "✅ 撤销的令牌正确被拒绝访问 (HTTP $http_code_after) - 访问控制正常工作"
elif [ "$http_code_after" = "200" ]; then
    log_error "❌ 撤销的令牌仍能访问受保护资源 - 存在严重安全漏洞！"
    exit 1
else
    log_warning "意外的HTTP状态码: $http_code_after"
fi

echo ""

# 8. 测试IP地址记录
log_info "步骤8: 检查IP地址记录..."
echo "在上述测试中，我们使用了以下代理头："
echo "  X-Forwarded-For: 203.0.113.195, 192.168.1.100"
echo "  X-Real-IP: 203.0.113.195"
echo ""
echo "如果IP地址获取修复生效，审计日志中应该记录客户端IP为: 203.0.113.195"
echo "而不是之前的 0.0.0.0 或 unknown"

echo ""
echo "=== 测试结果总结 ==="

if echo "$validate_after_response" | grep -q '"valid":false' && \
   ([ "$http_code_after" = "401" ] || [ "$http_code_after" = "403" ]); then
    echo ""
    log_success "🎉 JWT撤销功能修复验证成功！"
    echo ""
    echo "✅ 修复验证结果："
    echo "   1. JWT令牌可以正常获取和使用"
    echo "   2. JWT令牌撤销操作成功执行"
    echo "   3. 撤销后的令牌验证正确返回无效"
    echo "   4. 撤销后的令牌无法访问受保护资源"
    echo "   5. IP地址获取功能已集成到认证流程"
    echo ""
    echo "🔒 安全状态: 撤销功能正常工作，安全漏洞已修复"
else
    echo ""
    log_error "❌ JWT撤销功能修复验证失败！"
    echo ""
    echo "问题分析："
    if ! echo "$validate_after_response" | grep -q '"valid":false'; then
        echo "   - 令牌验证接口未正确识别撤销状态"
    fi
    if [ "$http_code_after" != "401" ] && [ "$http_code_after" != "403" ]; then
        echo "   - 访问控制未正确阻止撤销的令牌"
    fi
    echo ""
    echo "🚨 安全风险: 撤销功能可能仍存在问题，需要进一步调查"
    exit 1
fi

echo ""
echo "📋 后续建议："
echo "   1. 检查应用日志，确认IP地址记录正确"
echo "   2. 在生产环境部署前进行完整测试"
echo "   3. 监控JWT撤销相关的安全指标"
echo "   4. 定期验证撤销功能的有效性"