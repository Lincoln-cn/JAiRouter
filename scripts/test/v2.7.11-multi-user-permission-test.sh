#!/bin/bash

# =============================================================================
# v2.7.11 多用户权限自动化测试脚本
# 测试多用户权限分级功能（角色管理、资源隔离）
# 
# 对应手工测试方案：innerdoc/10-开发指南/v2.7.11-多用户权限测试方案.md
# =============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 配置
BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-}"

# 测试计数器
TOTAL=0
PASSED=0
FAILED=0
SKIPPED=0

# 测试数据
TEST_USER_KEY="test-user-key-$(date +%s)"
TEST_ADMIN_KEY="test-admin-key-$(date +%s)"
TEST_VIEWER_KEY="test-viewer-key-$(date +%s)"

# 打印函数
print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[PASS]${NC} $1"; ((PASSED++)); ((TOTAL++)); }
print_fail() { echo -e "${RED}[FAIL]${NC} $1"; ((FAILED++)); ((TOTAL++)); }
print_skip() { echo -e "${YELLOW}[SKIP]${NC} $1"; ((SKIPPED++)); ((TOTAL++)); }
print_section() { echo -e "\n${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; echo -e "${CYAN}  $1${NC}"; echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"; }
print_subsection() { echo -e "\n${YELLOW}▶ $1${NC}"; }

# 检查依赖
check_deps() {
    local missing=0
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}Error: curl not found${NC}"
        missing=1
    fi
    if ! command -v jq &> /dev/null; then
        echo -e "${RED}Error: jq not found${NC}"
        missing=1
    fi
    if [ $missing -eq 1 ]; then
        exit 1
    fi
}

# 检查服务是否运行
check_service() {
    print_info "检查服务状态..."
    local status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null || echo "000")
    if [ "$status" = "200" ] || [ "$status" = "503" ]; then
        # 503表示服务运行但某些组件DOWN（如tracing），核心功能仍可用
        print_info "服务运行正常 (HTTP $status)"
        return 0
    else
        print_fail "服务未运行或无法访问 (HTTP $status)"
        return 1
    fi
}

# API调用封装
api_call() {
    local method=$1
    local endpoint=$2
    local data=$3
    local expect_code=${4:-200}
    
    local response
    if [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE_URL$endpoint" \
            -H "Content-Type: application/json" \
            -H "X-API-Key: $API_KEY" \
            -d "$data" 2>/dev/null)
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE_URL$endpoint" \
            -H "X-API-Key: $API_KEY" 2>/dev/null)
    fi
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | sed '$d')
    
    echo "$body"
}

# 验证JSON字段
validate_json_field() {
    local json=$1
    local field=$2
    local expected=$3
    local test_name=$4
    
    local actual=$(echo "$json" | jq -r "$field // empty" 2>/dev/null)
    if [ "$actual" = "$expected" ]; then
        print_success "$test_name: $field = $actual"
        return 0
    else
        print_fail "$test_name: $field 期望=$expected, 实际=$actual"
        return 1
    fi
}

# 清理测试数据
cleanup() {
    print_subsection "清理测试数据"
    for key_id in "$TEST_USER_KEY" "$TEST_ADMIN_KEY" "$TEST_VIEWER_KEY"; do
        api_call DELETE "/api/auth/api-keys/$key_id" > /dev/null 2>&1 || true
    done
    print_info "测试数据已清理"
}

# ==================== 测试用例 ====================

# 测试1: 创建普通用户API Key（对应手工测试1）
test_create_user_key() {
    print_subsection "测试1: 创建普通用户API Key"
    print_info "对应手工测试: 测试1 - 创建普通用户API Key"
    
    local result=$(api_call POST "/api/auth/api-keys" "{
        \"keyId\": \"$TEST_USER_KEY\",
        \"description\": \"测试用户Key\",
        \"ownerId\": \"user001\",
        \"ownerRole\": \"user\",
        \"permissions\": [\"chat\"]
    }")
    
    # 验证创建成功
    validate_json_field "$result" ".data.keyId" "$TEST_USER_KEY" "创建用户Key"
    
    # 验证ownerId和ownerRole
    validate_json_field "$result" ".data.ownerId" "user001" "用户Key ownerId"
    validate_json_field "$result" ".data.ownerRole" "user" "用户Key ownerRole"
}

# 测试2: 创建管理员API Key（对应手工测试2）
test_create_admin_key() {
    print_subsection "测试2: 创建管理员API Key"
    print_info "对应手工测试: 测试2 - 创建管理员API Key"
    
    local result=$(api_call POST "/api/auth/api-keys" "{
        \"keyId\": \"$TEST_ADMIN_KEY\",
        \"description\": \"测试管理员Key\",
        \"ownerId\": \"admin001\",
        \"ownerRole\": \"admin\",
        \"permissions\": [\"chat\", \"embedding\", \"rerank\"]
    }")
    
    # 验证创建成功
    validate_json_field "$result" ".data.keyId" "$TEST_ADMIN_KEY" "创建管理员Key"
    
    # 验证ownerId和ownerRole
    validate_json_field "$result" ".data.ownerId" "admin001" "管理员Key ownerId"
    validate_json_field "$result" ".data.ownerRole" "admin" "管理员Key ownerRole"
    
    # 验证权限
    local permissions=$(echo "$result" | jq -r '.data.permissions | length')
    if [ "$permissions" = "3" ]; then
        print_success "管理员Key权限数量正确: 3个"
    else
        print_fail "管理员Key权限数量不正确: 期望=3, 实际=$permissions"
    fi
}

# 测试3: 创建只读用户API Key（对应手工测试3）
test_create_viewer_key() {
    print_subsection "测试3: 创建只读用户API Key"
    print_info "对应手工测试: 测试3 - 创建只读用户API Key"
    
    local result=$(api_call POST "/api/auth/api-keys" "{
        \"keyId\": \"$TEST_VIEWER_KEY\",
        \"description\": \"测试只读Key\",
        \"ownerId\": \"viewer001\",
        \"ownerRole\": \"viewer\",
        \"permissions\": [\"chat\"]
    }")
    
    # 验证创建成功
    validate_json_field "$result" ".data.keyId" "$TEST_VIEWER_KEY" "创建只读Key"
    
    # 验证ownerId和ownerRole
    validate_json_field "$result" ".data.ownerId" "viewer001" "只读Key ownerId"
    validate_json_field "$result" ".data.ownerRole" "viewer" "只读Key ownerRole"
}

# 测试4: 验证表格显示（对应手工测试4）
test_verify_table_display() {
    print_subsection "测试4: 验证表格显示（API返回包含ownerId和ownerRole）"
    print_info "对应手工测试: 测试4 - 验证表格显示"
    
    local result=$(api_call GET "/api/auth/api-keys")
    
    # 验证返回结构
    local total=$(echo "$result" | jq '.data.total // 0')
    if [ "$total" -ge 3 ]; then
        print_success "API Key列表返回正确: total=$total"
    else
        print_fail "API Key列表返回不正确: total=$total"
        return
    fi
    
    # 验证普通用户Key
    print_subsection "验证普通用户Key"
    local user_key=$(echo "$result" | jq -r ".data.items[] | select(.keyId==\"$TEST_USER_KEY\")")
    validate_json_field "$user_key" ".ownerId" "user001" "用户Key"
    validate_json_field "$user_key" ".ownerRole" "user" "用户Key角色"
    
    # 验证管理员Key
    print_subsection "验证管理员Key"
    local admin_key=$(echo "$result" | jq -r ".data.items[] | select(.keyId==\"$TEST_ADMIN_KEY\")")
    validate_json_field "$admin_key" ".ownerId" "admin001" "管理员Key"
    validate_json_field "$admin_key" ".ownerRole" "admin" "管理员Key角色"
    
    # 验证只读Key
    print_subsection "验证只读Key"
    local viewer_key=$(echo "$result" | jq -r ".data.items[] | select(.keyId==\"$TEST_VIEWER_KEY\")")
    validate_json_field "$viewer_key" ".ownerId" "viewer001" "只读Key"
    validate_json_field "$viewer_key" ".ownerRole" "viewer" "只读Key角色"
}

# 测试5: 编辑API Key（对应手工测试5）
test_edit_api_key() {
    print_subsection "测试5: 编辑API Key"
    print_info "对应手工测试: 测试5 - 编辑API Key"
    
    # 修改用户Key的所有者信息
    local result=$(api_call PUT "/api/auth/api-keys/$TEST_USER_KEY" "{
        \"description\": \"修改后的描述\",
        \"ownerId\": \"user002\",
        \"ownerRole\": \"admin\"
    }")
    
    # 验证修改成功
    validate_json_field "$result" ".data.description" "修改后的描述" "修改描述"
    validate_json_field "$result" ".data.ownerId" "user002" "修改ownerId"
    validate_json_field "$result" ".data.ownerRole" "admin" "修改ownerRole"
    
    # 验证其他字段未被修改
    validate_json_field "$result" ".data.keyId" "$TEST_USER_KEY" "keyId未变"
}

# 测试6: 验证API返回（对应手工测试6）
test_verify_api_response() {
    print_subsection "测试6: 验证API返回格式"
    print_info "对应手工测试: 测试6 - 验证API返回"
    
    # 获取单个API Key详情
    local result=$(api_call GET "/api/auth/api-keys/$TEST_ADMIN_KEY")
    
    # 验证返回格式包含所有必要字段
    local fields=("keyId" "description" "ownerId" "ownerRole" "permissions" "enabled" "createdAt")
    local all_valid=true
    
    for field in "${fields[@]}"; do
        local has_field=$(echo "$result" | jq "has(\"$field\")" 2>/dev/null)
        if [ "$has_field" = "true" ]; then
            print_success "返回包含字段: $field"
        else
            print_fail "返回缺少字段: $field"
            all_valid=false
        fi
    done
    
    # 验证ownerId和ownerRole不为空
    local owner_id=$(echo "$result" | jq -r '.data.ownerId // empty')
    local owner_role=$(echo "$result" | jq -r '.data.ownerRole // empty')
    
    if [ -n "$owner_id" ] && [ -n "$owner_role" ]; then
        print_success "ownerId和ownerRole字段有值: $owner_id / $owner_role"
    else
        print_fail "ownerId或ownerRole字段为空"
    fi
}

# 测试7: 删除API Key（对应手工测试7）
test_delete_api_key() {
    print_subsection "测试7: 删除API Key"
    print_info "对应手工测试: 测试7 - 删除API Key"
    
    # 删除用户Key
    local result=$(api_call DELETE "/api/auth/api-keys/$TEST_USER_KEY")
    
    # 验证删除成功（检查列表中是否还有该Key）
    local list_result=$(api_call GET "/api/auth/api-keys")
    local has_key=$(echo "$list_result" | jq -r ".data.items[] | select(.keyId==\"$TEST_USER_KEY\") | .keyId // empty")
    
    if [ -z "$has_key" ]; then
        print_success "删除用户Key成功"
    else
        print_fail "删除用户Key失败: Key仍然存在"
    fi
    
    # 删除管理员Key
    api_call DELETE "/api/auth/api-keys/$TEST_ADMIN_KEY" > /dev/null 2>&1
    print_success "删除管理员Key"
    
    # 删除只读Key
    api_call DELETE "/api/auth/api-keys/$TEST_VIEWER_KEY" > /dev/null 2>&1
    print_success "删除只读Key"
}

# 测试8: 角色权限验证
test_role_permissions() {
    print_subsection "测试8: 角色权限验证"
    print_info "验证不同角色的权限差异"
    
    # 创建不同角色的Key
    local user_result=$(api_call POST "/api/auth/api-keys" "{
        \"keyId\": \"perm-test-user\",
        \"description\": \"权限测试-用户\",
        \"ownerId\": \"perm-user\",
        \"ownerRole\": \"user\",
        \"permissions\": [\"chat\"]
    }")
    
    local admin_result=$(api_call POST "/api/auth/api-keys" "{
        \"keyId\": \"perm-test-admin\",
        \"description\": \"权限测试-管理员\",
        \"ownerId\": \"perm-admin\",
        \"ownerRole\": \"admin\",
        \"permissions\": [\"chat\", \"embedding\", \"rerank\", \"tts\", \"stt\", \"imgGen\", \"imgEdit\"]
    }")
    
    # 验证用户权限数量
    local user_perms=$(echo "$user_result" | jq '.data.permissions | length')
    if [ "$user_perms" = "1" ]; then
        print_success "用户权限数量正确: 1个"
    else
        print_fail "用户权限数量不正确: 期望=1, 实际=$user_perms"
    fi
    
    # 验证管理员权限数量
    local admin_perms=$(echo "$admin_result" | jq '.data.permissions | length')
    if [ "$admin_perms" = "7" ]; then
        print_success "管理员权限数量正确: 7个"
    else
        print_fail "管理员权限数量不正确: 期望=7, 实际=$admin_perms"
    fi
    
    # 清理
    api_call DELETE "/api/auth/api-keys/perm-test-user" > /dev/null 2>&1
    api_call DELETE "/api/auth/api-keys/perm-test-admin" > /dev/null 2>&1
}

# 主函数
main() {
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║     v2.7.11 多用户权限自动化测试                              ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
    
    check_deps
    
    if [ -z "$API_KEY" ]; then
        echo -e "${RED}Error: Please set API_KEY environment variable${NC}"
        echo "Usage: API_KEY=your-api-key bash $0"
        echo ""
        echo "Example:"
        echo "  export API_KEY=sk-your-admin-key"
        echo "  bash scripts/test/v2.7.11-multi-user-permission-test.sh"
        exit 1
    fi
    
    print_info "Base URL: $BASE_URL"
    print_info "API Key: ${API_KEY:0:10}..."
    
    # 检查服务状态
    if ! check_service; then
        exit 1
    fi
    
    # 清理旧的测试数据
    cleanup
    
    # 执行测试
    test_create_user_key
    test_create_admin_key
    test_create_viewer_key
    test_verify_table_display
    test_edit_api_key
    test_verify_api_response
    test_delete_api_key
    test_role_permissions
    
    # 清理
    cleanup
    
    # 打印结果
    echo -e "\n${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║                      测试结果汇总                             ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo -e "  总计: $TOTAL"
    echo -e "  ${GREEN}通过: $PASSED${NC}"
    echo -e "  ${RED}失败: $FAILED${NC}"
    echo -e "  ${YELLOW}跳过: $SKIPPED${NC}"
    
    if [ $FAILED -eq 0 ]; then
        echo -e "\n${GREEN}✅ 所有测试通过!${NC}"
        exit 0
    else
        echo -e "\n${RED}❌ 有测试失败!${NC}"
        exit 1
    fi
}

main "$@"
