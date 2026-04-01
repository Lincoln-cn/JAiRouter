#!/bin/bash

echo "=== 测试修改实例 5 的限流器和熔断器配置 ==="
echo ""

# 获取令牌
TOKEN=$(curl -s -X POST 'http://172.16.30.6:8080/api/auth/jwt/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}' | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "✗ 无法获取令牌"
    exit 1
fi

echo "✓ 令牌获取成功"
echo ""

INSTANCE_ID="5"

# 步骤 1: 查看当前配置
echo "步骤 1: 查看实例 5 当前配置"
echo "================================"
curl -s -X GET "http://172.16.30.6:8080/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN" | jq '.data | {name, rateLimit, circuitBreaker}'

echo ""

# 步骤 2: 修改配置 - 启用限流器和熔断器
echo "步骤 2: 修改配置 - 启用限流器和熔断器"
echo "================================"
echo "发送更新请求..."

UPDATE_RESPONSE=$(curl -s -X PUT "http://172.16.30.6:8080/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-raw '{
    "instanceId":"5",
    "name":"qwen3:4b",
    "baseUrl":"http://172.16.30.6:9090",
    "path":"/v1/chat/completions",
    "weight":1,
    "status":"active",
    "adapter":null,
    "headers":{"Authorization":"Bearer gpustack_d9e4cb18169ca682_1fa218aa353544a21c26f2cb22f139c3"},
    "rateLimitEnabled":true,
    "rateLimitAlgorithm":"token-bucket",
    "rateLimitCapacity":100,
    "rateLimitRate":10,
    "rateLimitScope":"instance",
    "rateLimitClientIpEnable":false,
    "circuitBreakerEnabled":true,
    "circuitBreakerFailureThreshold":5,
    "circuitBreakerTimeout":60000,
    "circuitBreakerSuccessThreshold":2
  }')

echo "更新响应:"
echo "$UPDATE_RESPONSE" | jq '{success, message}'

echo ""

# 步骤 3: 验证修改结果
echo "步骤 3: 验证修改结果"
echo "================================"
sleep 2

CHECK_RESPONSE=$(curl -s -X GET "http://172.16.30.6:8080/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN")

echo "当前配置:"
echo "$CHECK_RESPONSE" | jq '.data | {name, rateLimit, circuitBreaker}'

echo ""
echo "验证结果:"
echo "================================"

RATELIMIT=$(echo "$CHECK_RESPONSE" | jq -r '.data.rateLimit')
CIRCUITBREAKER=$(echo "$CHECK_RESPONSE" | jq -r '.data.circuitBreaker')

if [ "$RATELIMIT" != "null" ] && [ ! -z "$RATELIMIT" ]; then
    RL_ENABLED=$(echo "$CHECK_RESPONSE" | jq -r '.data.rateLimit.enabled')
    echo "✓ 限流器配置：enabled=$RL_ENABLED"
else
    echo "✗ 限流器配置：null"
fi

if [ "$CIRCUITBREAKER" != "null" ] && [ ! -z "$CIRCUITBREAKER" ]; then
    CB_ENABLED=$(echo "$CHECK_RESPONSE" | jq -r '.data.circuitBreaker.enabled')
    echo "✓ 熔断器配置：enabled=$CB_ENABLED"
else
    echo "✗ 熔断器配置：null"
fi

echo ""
echo "=== 测试完成 ==="
