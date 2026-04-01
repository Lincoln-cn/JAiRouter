#!/bin/bash

echo "=== 测试限流器和熔断器配置修复 ==="
echo ""

# 获取令牌
TOKEN=$(curl -s -X POST "http://172.16.30.6:8080/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}' | jq -r '.data.token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "✗ 无法获取令牌"
    exit 1
fi

echo "✓ 获取令牌成功"
BASE_URL="http://172.16.30.6:8080"

# 获取实例列表
echo ""
echo "步骤 1: 获取实例列表"
echo "=========================================="
LIST=$(curl -s -X GET "$BASE_URL/api/config/instance/chat" -H "Jairouter_Token: $TOKEN")
echo "$LIST" | jq '.'

INSTANCE_ID=$(echo "$LIST" | jq -r '.data[0].instanceId' 2>/dev/null)
INSTANCE_NAME=$(echo "$LIST" | jq -r '.data[0].name' 2>/dev/null)

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "null" ]; then
    echo "✗ 没有可用的实例进行测试"
    exit 1
fi

echo ""
echo "测试实例：ID=$INSTANCE_ID, Name=$INSTANCE_NAME"

# 测试 1: 使用正确的扁平格式更新限流器和熔断器配置
echo ""
echo "步骤 2: 使用扁平格式更新配置（适配后端 DTO）"
echo "=========================================="

# 后端 DTO 使用的是扁平格式，不是嵌套格式
FLAT_DATA=$(cat <<EOF
{
    "instanceId":"$INSTANCE_ID",
    "name":"$INSTANCE_NAME",
    "baseUrl":"http://172.16.30.6:9090",
    "path":"/v1/chat/completions",
    "weight":8,
    "status":"active",
    "adapter":null,
    "headers":{
        "Authorization":"Bearer flat-format-test",
        "X-Test":"flat"
    },
    "rateLimitEnabled":true,
    "rateLimitAlgorithm":"token-bucket",
    "rateLimitCapacity":300,
    "rateLimitRate":30,
    "rateLimitScope":"instance",
    "rateLimitClientIpEnable":true,
    "circuitBreakerEnabled":true,
    "circuitBreakerFailureThreshold":15,
    "circuitBreakerTimeout":180000,
    "circuitBreakerSuccessThreshold":5
}
EOF
)

echo "发送扁平格式数据:"
echo "$FLAT_DATA" | jq '.'

UPDATE_RESPONSE=$(curl -s -X PUT "$BASE_URL/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-raw "$FLAT_DATA")

echo ""
echo "更新响应:"
echo "$UPDATE_RESPONSE" | jq '.'

# 步骤 3: 验证配置是否保存
echo ""
echo "步骤 3: 验证配置是否保存"
echo "=========================================="
sleep 2

CHECK_RESPONSE=$(curl -s -X GET "$BASE_URL/api/config/instance/chat" -H "Jairouter_Token: $TOKEN")
echo "获取实例列表:"
echo "$CHECK_RESPONSE" | jq '.'

INSTANCE_DATA=$(echo "$CHECK_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\")")
echo ""
echo "实例 $INSTANCE_ID 的详细数据:"
echo "$INSTANCE_DATA" | jq '.'

# 验证权重是否更新
WEIGHT=$(echo "$INSTANCE_DATA" | jq -r '.weight')
echo ""
echo "验证结果:"
if [ "$WEIGHT" = "8" ]; then
    echo "✓ 权重已更新: $WEIGHT"
else
    echo "✗ 权重未更新: $WEIGHT"
fi

# 验证限流器配置
RATELIMIT=$(echo "$INSTANCE_DATA" | jq -r '.rateLimit')
if [ "$RATELIMIT" != "null" ] && [ ! -z "$RATELIMIT" ]; then
    echo "✓ 限流器配置已保存"
    echo "$INSTANCE_DATA" | jq '.rateLimit'
else
    echo "✗ 限流器配置未保存 (null)"
fi

# 验证熔断器配置
CIRCUITBREAKER=$(echo "$INSTANCE_DATA" | jq -r '.circuitBreaker')
if [ "$CIRCUITBREAKER" != "null" ] && [ ! -z "$CIRCUITBREAKER" ]; then
    echo "✓ 熔断器配置已保存"
    echo "$INSTANCE_DATA" | jq '.circuitBreaker'
else
    echo "✗ 熔断器配置未保存 (null)"
fi

echo ""
echo "=== 测试完成 ==="