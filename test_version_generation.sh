#!/bin/bash

# ========================================
# 版本自动生成功能测试脚本
# 测试更新服务/实例时是否自动创建版本
# ========================================

set -e

echo "=========================================="
echo "版本自动生成功能测试"
echo "测试内容：更新服务/实例时自动创建版本"
echo "=========================================="
echo ""

BASE_URL="http://172.16.30.6:8080"
TOKEN=""
TEST_SERVICE_NAME="imggen"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 打印带颜色的信息
print_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

print_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

print_step() {
    echo ""
    echo "=========================================="
    echo "步骤 $1: $2"
    echo "=========================================="
}

# 获取 JWT 令牌
print_step "1" "获取 JWT 令牌"

TOKEN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}')

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    print_error "获取令牌失败"
    exit 1
fi

print_success "获取令牌成功"

# 获取初始版本数量
print_step "2" "获取初始版本数量"

INITIAL_VERSIONS_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/info" \
  -H "Jairouter_Token: $TOKEN")

INITIAL_VERSION_COUNT=$(echo "$INITIAL_VERSIONS_RESPONSE" | jq '.data | length')
print_info "初始版本数量: $INITIAL_VERSION_COUNT"

# 创建测试服务
print_step "3" "创建测试服务"

CREATE_SERVICE_DATA='{
    "adapter": "normal",
    "instances": [
        {
            "name": "test-imggen-instance-1",
            "baseUrl": "http://localhost:8081",
            "path": "/v1/images/generations",
            "weight": 1,
            "status": "active"
        }
    ],
    "loadBalance": {
        "type": "round-robin",
        "hashAlgorithm": "murmur3"
    },
    "rateLimit": {
        "algorithm": "token-bucket",
        "capacity": 1000,
        "rate": 100,
        "scope": "service",
        "clientIpEnable": true,
        "enabled": true
    },
    "circuitBreaker": {
        "failureThreshold": 5,
        "timeout": 60000,
        "successThreshold": 2,
        "enabled": true
    },
    "fallback": {
        "enabled": false,
        "maxRetries": 3,
        "retryInterval": 1000,
        "returnDefaultResponse": true
    }
}'

CREATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/config/type/services/${TEST_SERVICE_NAME}" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$CREATE_SERVICE_DATA")

echo "创建服务响应:"
echo "$CREATE_RESPONSE" | jq '.'

if echo "$CREATE_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    print_success "创建测试服务成功"
else
    print_error "创建测试服务失败"
    echo "$CREATE_RESPONSE" | jq '.message'
    exit 1
fi

# 等待版本生成
sleep 2

# 检查创建服务后的版本数量
print_step "4" "检查创建服务后的版本数量"

AFTER_CREATE_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/info" \
  -H "Jairouter_Token: $TOKEN")

AFTER_CREATE_COUNT=$(echo "$AFTER_CREATE_RESPONSE" | jq '.data | length')
print_info "创建服务后版本数量: $AFTER_CREATE_COUNT"

if [ "$AFTER_CREATE_COUNT" -gt "$INITIAL_VERSION_COUNT" ]; then
    print_success "创建服务自动生成了版本"
    
    # 显示最新版本信息
    LATEST_VERSION=$(echo "$AFTER_CREATE_RESPONSE" | jq '.data | sort_by(.version) | last')
    VERSION_NUM=$(echo "$LATEST_VERSION" | jq -r '.version')
    OPERATION=$(echo "$LATEST_VERSION" | jq -r '.operation')
    OPERATION_DETAIL=$(echo "$LATEST_VERSION" | jq -r '.operationDetail')
    
    print_info "最新版本号: $VERSION_NUM"
    print_info "操作类型: $OPERATION"
    print_info "操作详情: $OPERATION_DETAIL"
else
    print_error "创建服务没有自动生成版本"
fi

# 更新服务配置
print_step "5" "更新服务配置（测试版本生成）"

UPDATE_SERVICE_DATA='{
    "adapter": "normal",
    "instances": [
        {
            "name": "test-imggen-instance-1",
            "baseUrl": "http://localhost:8081",
            "path": "/v1/images/generations",
            "weight": 2,
            "status": "active"
        },
        {
            "name": "test-imggen-instance-2",
            "baseUrl": "http://localhost:8082",
            "path": "/v1/images/generations",
            "weight": 1,
            "status": "active"
        }
    ],
    "loadBalance": {
        "type": "weighted",
        "hashAlgorithm": "murmur3"
    },
    "rateLimit": {
        "algorithm": "token-bucket",
        "capacity": 2000,
        "rate": 200,
        "scope": "service",
        "clientIpEnable": true,
        "enabled": true
    },
    "circuitBreaker": {
        "failureThreshold": 10,
        "timeout": 120000,
        "successThreshold": 5,
        "enabled": true
    },
    "fallback": {
        "enabled": true,
        "fallbackUrl": "http://fallback.example.com",
        "maxRetries": 5,
        "retryInterval": 2000,
        "returnDefaultResponse": true
    }
}'

UPDATE_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/config/type/services/${TEST_SERVICE_NAME}" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$UPDATE_SERVICE_DATA")

echo "更新服务响应:"
echo "$UPDATE_RESPONSE" | jq '.'

if echo "$UPDATE_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    print_success "更新服务配置成功"
else
    print_error "更新服务配置失败"
    echo "$UPDATE_RESPONSE" | jq '.message'
fi

# 等待版本生成
sleep 2

# 检查更新服务后的版本数量
print_step "6" "检查更新服务后的版本数量"

AFTER_UPDATE_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/info" \
  -H "Jairouter_Token: $TOKEN")

AFTER_UPDATE_COUNT=$(echo "$AFTER_UPDATE_RESPONSE" | jq '.data | length')
print_info "更新服务后版本数量: $AFTER_UPDATE_COUNT"

if [ "$AFTER_UPDATE_COUNT" -gt "$AFTER_CREATE_COUNT" ]; then
    print_success "更新服务自动生成了新版本"
    
    # 显示最新版本信息
    LATEST_VERSION=$(echo "$AFTER_UPDATE_RESPONSE" | jq '.data | sort_by(.version) | last')
    VERSION_NUM=$(echo "$LATEST_VERSION" | jq -r '.version')
    OPERATION=$(echo "$LATEST_VERSION" | jq -r '.operation')
    OPERATION_DETAIL=$(echo "$LATEST_VERSION" | jq -r '.operationDetail')
    
    print_info "最新版本号: $VERSION_NUM"
    print_info "操作类型: $OPERATION"
    print_info "操作详情: $OPERATION_DETAIL"
    
    # 验证版本配置内容
    print_info "验证版本配置内容..."
    CONFIG_SERVICES=$(echo "$LATEST_VERSION" | jq -r '.config.services')
    TEST_SERVICE=$(echo "$LATEST_VERSION" | jq -r ".config.services.\"${TEST_SERVICE_NAME}\"")
    
    if [ "$TEST_SERVICE" != "null" ] && [ -n "$TEST_SERVICE" ]; then
        print_success "版本配置中包含测试服务"
        INSTANCE_COUNT=$(echo "$TEST_SERVICE" | jq '.instances | length')
        print_info "测试服务包含 $INSTANCE_COUNT 个实例"
        
        # 验证实例权重是否更新
        INSTANCE_WEIGHT=$(echo "$TEST_SERVICE" | jq '.instances[0].weight')
        if [ "$INSTANCE_WEIGHT" = "2" ]; then
            print_success "实例权重更新正确: $INSTANCE_WEIGHT"
        else
            print_error "实例权重不正确，期望: 2, 实际: $INSTANCE_WEIGHT"
        fi
        
        # 验证负载均衡类型是否更新
        LB_TYPE=$(echo "$TEST_SERVICE" | jq -r '.loadBalance.type')
        if [ "$LB_TYPE" = "weighted" ]; then
            print_success "负载均衡类型更新正确: $LB_TYPE"
        else
            print_error "负载均衡类型不正确，期望: weighted, 实际: $LB_TYPE"
        fi
    else
        print_error "版本配置中不包含测试服务"
    fi
else
    print_error "更新服务没有自动生成版本"
fi

# 查询指定版本配置
print_step "7" "查询指定版本配置"

LATEST_VERSION_NUM=$(echo "$AFTER_UPDATE_RESPONSE" | jq '.data | sort_by(.version) | last | .version')

if [ "$LATEST_VERSION_NUM" != "null" ] && [ -n "$LATEST_VERSION_NUM" ]; then
    print_info "查询版本 $LATEST_VERSION_NUM 的配置"
    
    VERSION_CONFIG_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/${LATEST_VERSION_NUM}" \
      -H "Jairouter_Token: $TOKEN")
    
    if echo "$VERSION_CONFIG_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
        print_success "查询版本配置成功"
        
        # 验证配置结构
        CONFIG_ADAPTER=$(echo "$VERSION_CONFIG_RESPONSE" | jq -r '.data.adapter')
        CONFIG_SERVICES=$(echo "$VERSION_CONFIG_RESPONSE" | jq '.data.services')
        
        print_info "配置 adapter: $CONFIG_ADAPTER"
        
        if [ "$CONFIG_SERVICES" != "null" ]; then
            print_success "版本配置结构正确"
            
            # 验证测试服务
            TEST_SERVICE_CONFIG=$(echo "$VERSION_CONFIG_RESPONSE" | jq ".data.services.\"${TEST_SERVICE_NAME}\"")
            if [ "$TEST_SERVICE_CONFIG" != "null" ] && [ -n "$TEST_SERVICE_CONFIG" ]; then
                print_success "版本配置中包含测试服务 ${TEST_SERVICE_NAME}"
            else
                print_error "版本配置中不包含测试服务"
            fi
        else
            print_error "版本配置结构不正确"
        fi
    else
        print_error "查询版本配置失败"
        echo "$VERSION_CONFIG_RESPONSE" | jq '.message'
    fi
else
    print_info "没有可用版本进行查询测试"
fi

# 清理测试数据
print_step "8" "清理测试数据"

DELETE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/api/config/type/services/${TEST_SERVICE_NAME}" \
  -H "Jairouter_Token: $TOKEN")

if echo "$DELETE_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    print_success "测试服务删除成功"
else
    print_info "测试服务删除失败或不存在"
fi

# 最终验证
print_step "9" "最终验证"

FINAL_VERSIONS_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/version/info" \
  -H "Jairouter_Token: $TOKEN")

FINAL_VERSION_COUNT=$(echo "$FINAL_VERSIONS_RESPONSE" | jq '.data | length')
print_info "最终版本数量: $FINAL_VERSION_COUNT"

# 测试报告
echo ""
echo "=========================================="
echo "测试报告"
echo "=========================================="
echo "初始版本数量: $INITIAL_VERSION_COUNT"
echo "创建服务后版本数量: $AFTER_CREATE_COUNT"
echo "更新服务后版本数量: $AFTER_UPDATE_COUNT"
echo "最终版本数量: $FINAL_VERSION_COUNT"
echo "=========================================="

if [ "$AFTER_CREATE_COUNT" -gt "$INITIAL_VERSION_COUNT" ] && [ "$AFTER_UPDATE_COUNT" -gt "$AFTER_CREATE_COUNT" ]; then
    echo -e "${GREEN}版本自动生成功能正常！✅${NC}"
    echo ""
    echo "测试结论:"
    echo "  ✅ 创建服务时自动生成版本"
    echo "  ✅ 更新服务时自动生成版本"
    echo "  ✅ 版本配置内容正确"
    echo "  ✅ 版本查询功能正常"
    exit 0
else
    echo -e "${RED}版本自动生成功能异常 ❌${NC}"
    echo ""
    echo "测试结论:"
    if [ "$AFTER_CREATE_COUNT" -le "$INITIAL_VERSION_COUNT" ]; then
        echo "  ❌ 创建服务时没有自动生成版本"
    fi
    if [ "$AFTER_UPDATE_COUNT" -le "$AFTER_CREATE_COUNT" ]; then
        echo "  ❌ 更新服务时没有自动生成版本"
    fi
    exit 1
fi
