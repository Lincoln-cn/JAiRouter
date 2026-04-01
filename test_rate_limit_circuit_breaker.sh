#!/bin/bash

echo "=== 限流器和熔断器配置修复测试脚本 ==="
echo ""
echo "使用方法："
echo "1. 确保服务已启动"
echo "2. 运行此脚本：./test_fix.sh"
echo ""

# 获取令牌
echo "获取认证令牌..."
TOKEN=$(curl -s -X POST 'http://172.16.30.6:8080/api/auth/jwt/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}' | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "✗ 无法获取令牌，请检查服务是否正常运行"
    exit 1
fi

echo "✓ 令牌获取成功"
echo ""

# 获取实例列表
echo "步骤 1: 获取实例列表"
echo "================================"
INSTANCES=$(curl -s -X GET "http://172.16.30.6:8080/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "$INSTANCES" | jq '.data[] | {instanceId, name, status}'

INSTANCE_ID=$(echo "$INSTANCES" | jq -r '.data[0].instanceId' 2>/dev/null)

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "null" ]; then
    echo "✗ 没有可用实例，测试无法继续"
    exit 1
fi

echo ""
echo "找到实例 ID: $INSTANCE_ID"
echo ""

# 更新实例配置
echo "步骤 2: 更新实例配置（包含限流器和熔断器）"
echo "================================"
echo "发送更新请求..."

UPDATE_RESPONSE=$(curl -s -X PUT "http://172.16.30.6:8080/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-raw '{
    "instanceId":"'"$INSTANCE_ID"'",
    "name":"test-instance-updated",
    "baseUrl":"http://172.16.30.6:9090",
    "path":"/v1/chat/completions",
    "weight":10,
    "status":"active",
    "adapter":null,
    "headers":{"Authorization":"Bearer test-token"},
    "rateLimitEnabled":true,
    "rateLimitAlgorithm":"token-bucket",
    "rateLimitCapacity":200,
    "rateLimitRate":20,
    "rateLimitScope":"instance",
    "rateLimitClientIpEnable":true,
    "circuitBreakerEnabled":true,
    "circuitBreakerFailureThreshold":10,
    "circuitBreakerTimeout":120000,
    "circuitBreakerSuccessThreshold":5
  }')

echo "更新响应:"
echo "$UPDATE_RESPONSE" | jq '{success, message}'

echo ""
echo "步骤 3: 验证配置是否保存"
echo "================================"
sleep 2

CHECK_RESPONSE=$(curl -s -X GET "http://172.16.30.6:8080/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

INSTANCE_DATA=$(echo "$CHECK_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\")")

echo "实例配置数据:"
echo "$INSTANCE_DATA" | jq '{
  name: .name,
  weight: .weight,
  rateLimit: .rateLimit,
  circuitBreaker: .circuitBreaker
}'

# 验证结果
echo ""
echo "验证结果:"
echo "================================"

RATELIMIT=$(echo "$INSTANCE_DATA" | jq -r '.rateLimit')
CIRCUITBREAKER=$(echo "$INSTANCE_DATA" | jq -r '.circuitBreaker')

if [ "$RATELIMIT" != "null" ] && [ ! -z "$RATELIMIT" ]; then
    RL_ENABLED=$(echo "$INSTANCE_DATA" | jq -r '.rateLimit.enabled')
    RL_CAPACITY=$(echo "$INSTANCE_DATA" | jq -r '.rateLimit.capacity')
    echo "✓ 限流器配置已保存：enabled=$RL_ENABLED, capacity=$RL_CAPACITY"
else
    echo "✗ 限流器配置未保存 (null)"
fi

if [ "$CIRCUITBREAKER" != "null" ] && [ ! -z "$CIRCUITBREAKER" ]; then
    CB_ENABLED=$(echo "$INSTANCE_DATA" | jq -r '.circuitBreaker.enabled')
    CB_THRESHOLD=$(echo "$INSTANCE_DATA" | jq -r '.circuitBreaker.failureThreshold')
    echo "✓ 熔断器配置已保存：enabled=$CB_ENABLED, failureThreshold=$CB_THRESHOLD"
else
    echo "✗ 熔断器配置未保存 (null)"
fi

echo ""
echo "=== 测试完成 ==="
