#!/bin/bash

echo "=========================================="
echo "限流器和熔断器配置接口测试（简化版 API）"
echo "=========================================="
echo ""

BASE_URL="http://172.16.30.6:8080"

# 步骤 1: 获取 JWT 令牌
echo "步骤 1: 获取 JWT 令牌"
echo "----------------------------------------"
TOKEN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}')

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ 获取令牌失败"
    exit 1
fi

echo "✅ 获取令牌成功"
echo ""

# 步骤 2: 获取实例列表
echo "步骤 2: 获取 chat 服务实例列表"
echo "----------------------------------------"
LIST_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

INSTANCE_ID=$(echo "$LIST_RESPONSE" | jq -r '.data[0].instanceId')
INSTANCE_NAME=$(echo "$LIST_RESPONSE" | jq -r '.data[0].name')

echo "找到测试实例：ID=$INSTANCE_ID, Name=$INSTANCE_NAME"
echo ""

# 步骤 3: 使用简化版 API 测试限流器配置
echo "步骤 3: 测试限流器配置（简化版 API /flat）"
echo "----------------------------------------"

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

# 步骤 4: 验证配置
echo ""
echo "步骤 4: 验证配置是否保存"
echo "----------------------------------------"
sleep 2

VERIFY_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "$VERIFY_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\") | {
  name: .name,
  rateLimit: .rateLimit,
  circuitBreaker: .circuitBreaker
}"

RATELIMIT=$(echo "$VERIFY_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .rateLimit")
CIRCUITBREAKER=$(echo "$VERIFY_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .circuitBreaker")

if [ "$RATELIMIT" != "null" ] && [ ! -z "$RATELIMIT" ]; then
    RL_ENABLED=$(echo "$RATELIMIT" | jq -r '.enabled')
    RL_CAPACITY=$(echo "$RATELIMIT" | jq -r '.capacity')
    echo "✅ 限流器配置已保存：enabled=$RL_ENABLED, capacity=$RL_CAPACITY"
else
    echo "❌ 限流器配置未保存"
fi

if [ "$CIRCUITBREAKER" != "null" ] && [ ! -z "$CIRCUITBREAKER" ]; then
    CB_ENABLED=$(echo "$CIRCUITBREAKER" | jq -r '.enabled')
    echo "✅ 熔断器配置已保存：enabled=$CB_ENABLED"
else
    echo "❌ 熔断器配置未保存"
fi

echo ""
echo "=========================================="
echo "测试完成！"
echo "=========================================="
