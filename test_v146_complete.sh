#!/bin/bash

echo "=========================================="
echo "V1.4.6 扁平化 API 完整功能测试"
echo "=========================================="
echo ""

BASE_URL="http://172.16.30.6:8080"
PASS_COUNT=0
FAIL_COUNT=0

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 步骤 1: 获取 JWT 令牌
echo "=========================================="
echo "步骤 1: 获取 JWT 令牌"
echo "=========================================="
TOKEN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}')

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo -e "${RED}❌ 获取令牌失败${NC}"
    echo "$TOKEN_RESPONSE" | jq '.'
    exit 1
fi

echo -e "${GREEN}✅ 获取令牌成功${NC}"
echo ""

# 步骤 2: 获取实例列表
echo "=========================================="
echo "步骤 2: 获取 chat 服务实例列表"
echo "=========================================="
LIST_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$LIST_RESPONSE" | jq '.data[] | {instanceId, name, status}'

INSTANCE_ID=$(echo "$LIST_RESPONSE" | jq -r '.data[0].instanceId')
INSTANCE_NAME=$(echo "$LIST_RESPONSE" | jq -r '.data[0].name')

if [ "$INSTANCE_ID" = "null" ] || [ -z "$INSTANCE_ID" ]; then
    echo -e "${RED}❌ 没有可用实例${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 找到测试实例：ID=$INSTANCE_ID, Name=$INSTANCE_NAME${NC}"
echo ""

# 步骤 3: 测试限流器配置（扁平格式）
echo "=========================================="
echo "步骤 3: 测试限流器配置（扁平格式）"
echo "=========================================="

FLAT_DATA=$(cat <<EOF
{
  "instanceId": "$INSTANCE_ID",
  "name": "$INSTANCE_NAME",
  "baseUrl": "http://172.16.30.6:9090",
  "path": "/v1/chat/completions",
  "weight": 1,
  "status": "active",
  "adapter": null,
  "headers": {
    "Authorization": "Bearer test-token"
  },
  "rateLimitEnabled": true,
  "rateLimitAlgorithm": "token-bucket",
  "rateLimitCapacity": 200,
  "rateLimitRate": 20,
  "rateLimitScope": "instance",
  "rateLimitKey": "",
  "rateLimitClientIpEnable": true,
  "circuitBreakerEnabled": false,
  "circuitBreakerFailureThreshold": 5,
  "circuitBreakerTimeout": 60000,
  "circuitBreakerSuccessThreshold": 2
}
EOF
)

echo "发送扁平格式数据..."
UPDATE_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/config/instance/chat/$INSTANCE_ID/flat" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$FLAT_DATA")

echo "更新响应:"
echo "$UPDATE_RESPONSE" | jq '.success, .message'

if echo "$UPDATE_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 限流器配置更新成功${NC}"
    ((PASS_COUNT++))
else
    echo -e "${RED}❌ 限流器配置更新失败${NC}"
    ((FAIL_COUNT++))
fi
echo ""

# 步骤 4: 验证限流器配置
echo "=========================================="
echo "步骤 4: 验证限流器配置是否保存"
echo "=========================================="
sleep 2

VERIFY_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$VERIFY_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\")"

RATELIMIT=$(echo "$VERIFY_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .rateLimit")
RATELIMIT_ENABLED=$(echo "$VERIFY_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .rateLimit.enabled")
RATELIMIT_CAPACITY=$(echo "$VERIFY_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .rateLimit.capacity")

if [ "$RATELIMIT" != "null" ] && [ ! -z "$RATELIMIT" ] && [ "$RATELIMIT_ENABLED" = "true" ]; then
    echo -e "${GREEN}✅ 限流器配置已保存：enabled=$RATELIMIT_ENABLED, capacity=$RATELIMIT_CAPACITY${NC}"
    ((PASS_COUNT++))
else
    echo -e "${RED}❌ 限流器配置未保存 (rateLimit=$RATELIMIT)${NC}"
    ((FAIL_COUNT++))
fi
echo ""

# 步骤 5: 测试熔断器配置（扁平格式）
echo "=========================================="
echo "步骤 5: 测试熔断器配置（扁平格式）"
echo "=========================================="

CB_DATA=$(cat <<EOF
{
  "instanceId": "$INSTANCE_ID",
  "name": "$INSTANCE_NAME",
  "baseUrl": "http://172.16.30.6:9090",
  "path": "/v1/chat/completions",
  "weight": 1,
  "status": "active",
  "adapter": null,
  "headers": {
    "Authorization": "Bearer test-token"
  },
  "rateLimitEnabled": true,
  "rateLimitAlgorithm": "token-bucket",
  "rateLimitCapacity": 200,
  "rateLimitRate": 20,
  "rateLimitScope": "instance",
  "rateLimitKey": "",
  "rateLimitClientIpEnable": true,
  "circuitBreakerEnabled": true,
  "circuitBreakerFailureThreshold": 10,
  "circuitBreakerTimeout": 120000,
  "circuitBreakerSuccessThreshold": 5
}
EOF
)

echo "发送扁平格式数据..."
CB_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/config/instance/chat/$INSTANCE_ID/flat" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$CB_DATA")

echo "更新响应:"
echo "$CB_RESPONSE" | jq '.success, .message'

if echo "$CB_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 熔断器配置更新成功${NC}"
    ((PASS_COUNT++))
else
    echo -e "${RED}❌ 熔断器配置更新失败${NC}"
    ((FAIL_COUNT++))
fi
echo ""

# 步骤 6: 验证熔断器配置
echo "=========================================="
echo "步骤 6: 验证熔断器配置是否保存"
echo "=========================================="
sleep 2

VERIFY_CB_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$VERIFY_CB_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\")"

CIRCUITBREAKER=$(echo "$VERIFY_CB_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .circuitBreaker")
CB_ENABLED=$(echo "$VERIFY_CB_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .circuitBreaker.enabled")
CB_THRESHOLD=$(echo "$VERIFY_CB_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .circuitBreaker.failureThreshold")

if [ "$CIRCUITBREAKER" != "null" ] && [ ! -z "$CIRCUITBREAKER" ] && [ "$CB_ENABLED" = "true" ]; then
    echo -e "${GREEN}✅ 熔断器配置已保存：enabled=$CB_ENABLED, failureThreshold=$CB_THRESHOLD${NC}"
    ((PASS_COUNT++))
else
    echo -e "${RED}❌ 熔断器配置未保存 (circuitBreaker=$CIRCUITBREAKER)${NC}"
    ((FAIL_COUNT++))
fi
echo ""

# 步骤 7: 测试关闭配置
echo "=========================================="
echo "步骤 7: 测试关闭限流器和熔断器"
echo "=========================================="

DISABLE_DATA=$(cat <<EOF
{
  "instanceId": "$INSTANCE_ID",
  "name": "$INSTANCE_NAME",
  "baseUrl": "http://172.16.30.6:9090",
  "path": "/v1/chat/completions",
  "weight": 1,
  "status": "active",
  "adapter": null,
  "headers": {
    "Authorization": "Bearer test-token"
  },
  "rateLimitEnabled": false,
  "rateLimitAlgorithm": "token-bucket",
  "rateLimitCapacity": 100,
  "rateLimitRate": 10,
  "rateLimitScope": "instance",
  "rateLimitKey": "",
  "rateLimitClientIpEnable": false,
  "circuitBreakerEnabled": false,
  "circuitBreakerFailureThreshold": 5,
  "circuitBreakerTimeout": 60000,
  "circuitBreakerSuccessThreshold": 2
}
EOF
)

DISABLE_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/config/instance/chat/$INSTANCE_ID/flat" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$DISABLE_DATA")

echo "更新响应:"
echo "$DISABLE_RESPONSE" | jq '.success, .message'

if echo "$DISABLE_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    echo -e "${GREEN}✅ 配置关闭成功${NC}"
    ((PASS_COUNT++))
else
    echo -e "${RED}❌ 配置关闭失败${NC}"
    ((FAIL_COUNT++))
fi
echo ""

# 最终验证
echo "=========================================="
echo "步骤 8: 最终验证配置状态"
echo "=========================================="
sleep 2

FINAL_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "最终配置状态:"
echo "$FINAL_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\") | {
  name: .name,
  rateLimitEnabled: .rateLimit.enabled,
  circuitBreakerEnabled: .circuitBreaker.enabled
}"

echo ""
echo "=========================================="
echo "测试结果汇总"
echo "=========================================="
echo -e "通过：${GREEN}$PASS_COUNT${NC}"
echo -e "失败：${RED}$FAIL_COUNT${NC}"

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}=========================================="
    echo "✅ 所有测试通过！V1.4.6 功能正常！"
    echo "==========================================${NC}"
    exit 0
else
    echo -e "${RED}=========================================="
    echo "❌ 部分测试失败，请检查日志"
    echo "==========================================${NC}"
    exit 1
fi
