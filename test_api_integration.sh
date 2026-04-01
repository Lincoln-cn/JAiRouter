#!/bin/bash

echo "=========================================="
echo "限流器和熔断器配置接口完整测试"
echo "=========================================="
echo ""

BASE_URL="http://172.16.30.6:8080"

# 步骤 1: 获取 JWT 令牌
echo "步骤 1: 获取 JWT 令牌"
echo "----------------------------------------"
TOKEN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}')

echo "响应:"
echo "$TOKEN_RESPONSE" | jq '.'

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ 获取令牌失败"
    exit 1
fi

echo "✅ 获取令牌成功"
echo "Token: ${TOKEN:0:50}..."
echo ""

# 步骤 2: 获取实例列表
echo "步骤 2: 获取 chat 服务实例列表"
echo "----------------------------------------"
LIST_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$LIST_RESPONSE" | jq '.data[] | {instanceId, name, status, rateLimit, circuitBreaker}'

INSTANCE_ID=$(echo "$LIST_RESPONSE" | jq -r '.data[0].instanceId')
INSTANCE_NAME=$(echo "$LIST_RESPONSE" | jq -r '.data[0].name')

if [ "$INSTANCE_ID" = "null" ] || [ -z "$INSTANCE_ID" ]; then
    echo "❌ 没有可用实例"
    exit 1
fi

echo "✅ 找到测试实例：ID=$INSTANCE_ID, Name=$INSTANCE_NAME"
echo ""

# 步骤 3: 测试限流器配置
echo "步骤 3: 测试限流器配置（扁平格式）"
echo "----------------------------------------"
echo "发送限流器配置数据..."

RATE_LIMIT_DATA=$(cat <<EOF
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
  "rateLimitClientIpEnable": true,
  "circuitBreakerEnabled": false,
  "circuitBreakerFailureThreshold": 5,
  "circuitBreakerTimeout": 60000,
  "circuitBreakerSuccessThreshold": 2
}
EOF
)

echo "请求数据:"
echo "$RATE_LIMIT_DATA" | jq '.'

UPDATE_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$RATE_LIMIT_DATA")

echo ""
echo "更新响应:"
echo "$UPDATE_RESPONSE" | jq '.'

if echo "$UPDATE_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    echo "✅ 限流器配置更新成功"
else
    echo "❌ 限流器配置更新失败"
    echo "$UPDATE_RESPONSE" | jq '.message'
fi
echo ""

# 步骤 4: 验证限流器配置
echo "步骤 4: 验证限流器配置是否保存"
echo "----------------------------------------"
sleep 2

VERIFY_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$VERIFY_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\") | {name, rateLimit, circuitBreaker}"

RATE_LIMIT=$(echo "$VERIFY_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .rateLimit")
CIRCUIT_BREAKER=$(echo "$VERIFY_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .circuitBreaker")

if [ "$RATE_LIMIT" != "null" ] && [ ! -z "$RATE_LIMIT" ]; then
    RL_ENABLED=$(echo "$RATE_LIMIT" | jq -r '.enabled')
    RL_CAPACITY=$(echo "$RATE_LIMIT" | jq -r '.capacity')
    RL_RATE=$(echo "$RATE_LIMIT" | jq -r '.rate')
    echo "✅ 限流器配置已保存：enabled=$RL_ENABLED, capacity=$RL_CAPACITY, rate=$RL_RATE"
else
    echo "❌ 限流器配置未保存 (null)"
fi
echo ""

# 步骤 5: 测试熔断器配置
echo "步骤 5: 测试熔断器配置（扁平格式）"
echo "----------------------------------------"
echo "发送熔断器配置数据..."

CIRCUIT_BREAKER_DATA=$(cat <<EOF
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
  "rateLimitClientIpEnable": true,
  "circuitBreakerEnabled": true,
  "circuitBreakerFailureThreshold": 10,
  "circuitBreakerTimeout": 120000,
  "circuitBreakerSuccessThreshold": 5
}
EOF
)

echo "请求数据:"
echo "$CIRCUIT_BREAKER_DATA" | jq '.'

UPDATE_CB_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$CIRCUIT_BREAKER_DATA")

echo ""
echo "更新响应:"
echo "$UPDATE_CB_RESPONSE" | jq '.'

if echo "$UPDATE_CB_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    echo "✅ 熔断器配置更新成功"
else
    echo "❌ 熔断器配置更新失败"
    echo "$UPDATE_CB_RESPONSE" | jq '.message'
fi
echo ""

# 步骤 6: 验证熔断器配置
echo "步骤 6: 验证熔断器配置是否保存"
echo "----------------------------------------"
sleep 2

VERIFY_CB_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "响应:"
echo "$VERIFY_CB_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\") | {name, rateLimit, circuitBreaker}"

CB_ENABLED=$(echo "$VERIFY_CB_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .circuitBreaker.enabled")
CB_THRESHOLD=$(echo "$VERIFY_CB_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .circuitBreaker.failureThreshold")
CB_TIMEOUT=$(echo "$VERIFY_CB_RESPONSE" | jq -r ".data[] | select(.instanceId==\"$INSTANCE_ID\") | .circuitBreaker.timeout")

if [ "$CB_ENABLED" = "true" ]; then
    echo "✅ 熔断器配置已保存：enabled=$CB_ENABLED, failureThreshold=$CB_THRESHOLD, timeout=$CB_TIMEOUT"
else
    echo "❌ 熔断器配置未保存"
fi
echo ""

# 步骤 7: 测试关闭配置
echo "步骤 7: 测试关闭限流器和熔断器"
echo "----------------------------------------"
echo "发送关闭配置数据..."

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
  "rateLimitClientIpEnable": false,
  "circuitBreakerEnabled": false,
  "circuitBreakerFailureThreshold": 5,
  "circuitBreakerTimeout": 60000,
  "circuitBreakerSuccessThreshold": 2
}
EOF
)

DISABLE_RESPONSE=$(curl -s -X PUT "${BASE_URL}/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$DISABLE_DATA")

echo ""
echo "更新响应:"
echo "$DISABLE_RESPONSE" | jq '.'

if echo "$DISABLE_RESPONSE" | jq -e '.success' >/dev/null 2>&1; then
    echo "✅ 配置关闭成功"
else
    echo "❌ 配置关闭失败"
fi
echo ""

# 最终验证
echo "步骤 8: 最终验证配置状态"
echo "----------------------------------------"
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
echo "测试完成！"
echo "=========================================="
