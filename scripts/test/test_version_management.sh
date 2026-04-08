#!/bin/bash

# ========================================
# 版本管理功能测试脚本
# 测试充血模型 DTO 转换是否正确
# ========================================

# set -e  # 禁用，手动处理错误

echo "=========================================="
echo "版本管理功能测试"
echo "测试内容：DTO 充血模型转换、版本查询"
echo "=========================================="
echo ""

BASE_URL="http://172.16.30.6:8080"
TOKEN=""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 测试计数
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 打印带颜色的信息
print_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED_TESTS++))
}

print_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAILED_TESTS++))
}

print_step() {
    echo ""
    echo "=========================================="
    echo "步骤 $1: $2"
    echo "=========================================="
    ((TOTAL_TESTS++))
}

# 步骤 1: 获取 JWT 令牌
print_step "1" "获取 JWT 令牌"

TOKEN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}')

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    print_error "获取令牌失败"
    echo "响应: $TOKEN_RESPONSE"
    exit 1
fi

print_success "获取令牌成功"
print_info "Token: ${TOKEN:0:50}..."

# 步骤 2: 获取版本列表
print_step "2" "获取版本列表 (/api/config/version)"

VERSIONS_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$VERSIONS_RESPONSE" | jq '.'

if echo "$VERSIONS_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    VERSION_COUNT=$(echo "$VERSIONS_RESPONSE" | jq '.data | length')
    print_success "获取版本列表成功，共 $VERSION_COUNT 个版本"
else
    print_error "获取版本列表失败"
    echo "$VERSIONS_RESPONSE" | jq '.message'
fi

# 步骤 3: 获取所有版本详细信息（重点测试 DTO 转换）
print_step "3" "获取所有版本详细信息 (/api/config/version/info)"
print_info "此接口测试充血模型 DTO 转换是否正确"

VERSION_INFO_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/info" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$VERSION_INFO_RESPONSE" | jq '.'

if echo "$VERSION_INFO_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    VERSION_COUNT=$(echo "$VERSION_INFO_RESPONSE" | jq '.data | length')
    print_success "获取版本详细信息成功，共 $VERSION_COUNT 个版本"
    
    # 如果有版本数据，验证返回的数据结构
    if [ "$VERSION_COUNT" -gt 0 ]; then
        echo ""
        print_info "验证返回数据结构..."
        
        # 检查第一个版本的 config 字段是否为对象（不是字符串）
        FIRST_CONFIG=$(echo "$VERSION_INFO_RESPONSE" | jq '.data[0].config')
        
        if [ "$FIRST_CONFIG" != "null" ] && [ -n "$FIRST_CONFIG" ]; then
            # 检查 config 是否包含 services 字段（RouterConfiguration 的结构）
            SERVICES=$(echo "$VERSION_INFO_RESPONSE" | jq '.data[0].config.services')
            
            if [ "$SERVICES" != "null" ]; then
                print_success "config 字段结构正确，包含 services"
                
                # 检查 service 配置结构
                SERVICE_KEYS=$(echo "$VERSION_INFO_RESPONSE" | jq '.data[0].config.services | keys[]')
                if [ -n "$SERVICE_KEYS" ]; then
                    print_info "发现服务类型: $SERVICE_KEYS"
                    
                    # 检查第一个服务的实例列表
                    FIRST_SERVICE=$(echo "$VERSION_INFO_RESPONSE" | jq -r '.data[0].config.services | keys[0]')
                    INSTANCES=$(echo "$VERSION_INFO_RESPONSE" | jq ".data[0].config.services.\"$FIRST_SERVICE\".instances")
                    
                    if [ "$INSTANCES" != "null" ]; then
                        INSTANCE_COUNT=$(echo "$INSTANCES" | jq 'length')
                        print_success "服务 $FIRST_SERVICE 包含 $INSTANCE_COUNT 个实例"
                        
                        # 检查实例字段
                        INSTANCE_NAME=$(echo "$INSTANCES" | jq -r '.[0].name // "null"')
                        INSTANCE_BASEURL=$(echo "$INSTANCES" | jq -r '.[0].baseUrl // "null"')
                        
                        if [ "$INSTANCE_NAME" != "null" ]; then
                            print_success "实例字段正确: name=$INSTANCE_NAME, baseUrl=$INSTANCE_BASEURL"
                        else
                            print_error "实例字段缺失"
                        fi
                    else
                        print_error "instances 字段缺失"
                    fi
                else
                    print_info "services 为空（可能是新系统）"
                fi
            else
                print_error "config.services 字段缺失，DTO 转换可能失败"
            fi
            
            # 检查版本元数据字段
            echo ""
            print_info "检查版本元数据..."
            VERSION_NUM=$(echo "$VERSION_INFO_RESPONSE" | jq -r '.data[0].version // "null"')
            IS_CURRENT=$(echo "$VERSION_INFO_RESPONSE" | jq -r '.data[0].current // "null"')
            OPERATION=$(echo "$VERSION_INFO_RESPONSE" | jq -r '.data[0].operation // "null"')
            TIMESTAMP=$(echo "$VERSION_INFO_RESPONSE" | jq -r '.data[0].timestamp // "null"')
            
            print_info "版本: $VERSION_NUM, 当前版本: $IS_CURRENT, 操作: $OPERATION, 时间戳: $TIMESTAMP"
            
            if [ "$VERSION_NUM" != "null" ] && [ "$IS_CURRENT" != "null" ]; then
                print_success "版本元数据字段完整"
            else
                print_error "版本元数据字段缺失"
            fi
        else
            print_error "config 字段为空或 null"
        fi
    else
        print_info "版本列表为空，跳过数据结构验证"
    fi
else
    print_error "获取版本详细信息失败"
    echo "$VERSION_INFO_RESPONSE" | jq '.message'
fi

# 步骤 4: 创建测试配置版本（如果没有版本）
print_step "4" "创建测试配置版本"

VERSION_COUNT=$(echo "$VERSION_INFO_RESPONSE" | jq '.data | length')

if [ "$VERSION_COUNT" -eq 0 ]; then
    print_info "系统中没有版本，创建测试配置..."
    
    # 先获取当前配置（YAML 配置）
    CURRENT_CONFIG_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/current" \
      -H "Jairouter_Token: $TOKEN")
    
    echo "当前配置响应:"
    echo "$CURRENT_CONFIG_RESPONSE" | jq '.'
    
    # 创建一个新的服务来生成版本
    CREATE_SERVICE_DATA='{
        "adapter": "normal",
        "instances": [
            {
                "name": "test-instance",
                "baseUrl": "http://localhost:8080",
                "path": "/v1/chat/completions",
                "weight": 1,
                "status": "active"
            }
        ],
        "loadBalance": {
            "type": "round_robin",
            "hashAlgorithm": "murmur3"
        },
        "rateLimit": {
            "requestsPerSecond": 100,
            "requestsPerMinute": 1000,
            "enabled": true
        },
        "circuitBreaker": {
            "failureThreshold": 5,
            "timeout": 60000,
            "enabled": true
        },
        "fallback": {
            "enabled": false,
            "maxRetries": 3
        }
    }'
    
    CREATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/config/type/services/test-service" \
      -H "Jairouter_Token: $TOKEN" \
      -H "Content-Type: application/json" \
      --data-raw "$CREATE_SERVICE_DATA")
    
    echo "创建服务响应:"
    echo "$CREATE_RESPONSE" | jq '.'
    
    if echo "$CREATE_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
        print_success "创建测试服务成功"
        
        # 等待版本生成
        sleep 2
        
        # 重新获取版本列表
        VERSION_INFO_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/info" \
          -H "Jairouter_Token: $TOKEN")
        
        NEW_VERSION_COUNT=$(echo "$VERSION_INFO_RESPONSE" | jq '.data | length')
        print_info "现在共有 $NEW_VERSION_COUNT 个版本"
    else
        print_error "创建测试服务失败"
        echo "$CREATE_RESPONSE" | jq '.message'
    fi
else
    print_info "系统中已有 $VERSION_COUNT 个版本，跳过创建"
fi

# 步骤 5: 获取当前版本
print_step "5" "获取当前版本 (/api/config/version/current)"

CURRENT_VERSION_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/current" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$CURRENT_VERSION_RESPONSE" | jq '.'

if echo "$CURRENT_VERSION_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    CURRENT_VERSION=$(echo "$CURRENT_VERSION_RESPONSE" | jq -r '.data')
    print_success "获取当前版本成功: $CURRENT_VERSION"
else
    print_error "获取当前版本失败"
    echo "$CURRENT_VERSION_RESPONSE" | jq '.message'
fi

# 步骤 6: 重新获取版本详细信息（如果创建了版本）
print_step "6" "重新获取版本详细信息"

# 如果之前创建了版本，重新获取
if [ "$VERSION_COUNT" -eq 0 ]; then
    print_info "重新获取版本信息..."
    VERSION_INFO_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/info" \
      -H "Jairouter_Token: $TOKEN")
    
    echo "响应:"
    echo "$VERSION_INFO_RESPONSE" | jq '.'
fi

# 步骤 7: 获取指定版本配置
print_step "7" "获取指定版本配置 (/api/config/version/{version})"

# 获取第一个版本号进行测试
FIRST_VERSION=$(echo "$VERSION_INFO_RESPONSE" | jq -r '.data[0].version // "0"')

# 如果没有版本，测试版本 0（默认配置）
if [ "$FIRST_VERSION" = "0" ] || [ "$FIRST_VERSION" = "null" ]; then
    print_info "没有历史版本，测试版本 0（默认配置）"
    FIRST_VERSION="0"
fi

print_info "测试获取版本 $FIRST_VERSION 的配置"

VERSION_CONFIG_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/${FIRST_VERSION}" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$VERSION_CONFIG_RESPONSE" | jq '.'

if echo "$VERSION_CONFIG_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    print_success "获取版本 $FIRST_VERSION 配置成功"
    
    # 验证配置结构
    CONFIG_SERVICES=$(echo "$VERSION_CONFIG_RESPONSE" | jq '.data.services')
    if [ "$CONFIG_SERVICES" != "null" ]; then
        print_success "配置结构正确，包含 services 字段"
        
        # 检查 test-service 是否存在
        TEST_SERVICE=$(echo "$VERSION_CONFIG_RESPONSE" | jq '.data.services.test-service')
        if [ "$TEST_SERVICE" != "null" ] && [ -n "$TEST_SERVICE" ]; then
            print_success "test-service 存在于配置中"
            
            # 检查实例
            INSTANCES=$(echo "$VERSION_CONFIG_RESPONSE" | jq '.data.services.test-service.instances')
            if [ "$INSTANCES" != "null" ]; then
                INSTANCE_COUNT=$(echo "$INSTANCES" | jq 'length')
                print_success "test-service 包含 $INSTANCE_COUNT 个实例"
            fi
        else
            print_info "test-service 不在配置中（可能使用了默认配置）"
        fi
    else
        print_error "配置结构不正确，缺少 services 字段"
    fi
else
    print_error "获取版本 $FIRST_VERSION 配置失败"
    echo "$VERSION_CONFIG_RESPONSE" | jq '.message'
fi

# 步骤 8: 验证 DTO 转换的完整性
print_step "8" "验证 DTO 转换完整性"
print_info "对比 /api/config/version/info 和 /api/config/version/{version} 返回的数据结构"

if [ "$FIRST_VERSION" != "0" ] && [ "$FIRST_VERSION" != "null" ]; then
    # 从 version/info 获取的 config
    INFO_CONFIG=$(echo "$VERSION_INFO_RESPONSE" | jq ".data[] | select(.version==$FIRST_VERSION) | .config")
    
    # 从 version/{version} 获取的 config
    SINGLE_CONFIG=$(echo "$VERSION_CONFIG_RESPONSE" | jq '.data')
    
    # 简单对比关键字段
    INFO_SERVICES_KEYS=$(echo "$INFO_CONFIG" | jq -r '.services | keys | sort | join(",")')
    SINGLE_SERVICES_KEYS=$(echo "$SINGLE_CONFIG" | jq -r '.services | keys | sort | join(",")')
    
    if [ "$INFO_SERVICES_KEYS" = "$SINGLE_SERVICES_KEYS" ]; then
        print_success "两个接口返回的 services 结构一致"
    else
        print_error "两个接口返回的 services 结构不一致"
        print_info "version/info: $INFO_SERVICES_KEYS"
        print_info "version/{id}: $SINGLE_SERVICES_KEYS"
    fi
fi

# 步骤 9: 清理测试数据
print_step "9" "清理测试数据"

print_info "删除测试服务 test-service..."

DELETE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/api/config/type/services/test-service" \
  -H "Jairouter_Token: $TOKEN")

echo "删除响应:"
echo "$DELETE_RESPONSE" | jq '.'

if echo "$DELETE_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    print_success "测试服务删除成功"
else
    print_info "测试服务删除失败或不存在"
fi

# 测试报告
echo ""
echo "=========================================="
echo "测试报告"
echo "=========================================="
echo "总测试数: $TOTAL_TESTS"
echo -e "通过: ${GREEN}$PASSED_TESTS${NC}"
echo -e "失败: ${RED}$FAILED_TESTS${NC}"
echo "=========================================="

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}所有测试通过！✅${NC}"
    exit 0
else
    echo -e "${RED}存在失败的测试 ❌${NC}"
    exit 1
fi
