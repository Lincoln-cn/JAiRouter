#!/bin/bash

echo "=== 测试限流器和熔断器配置修复（最终版） ==="
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

# 使用扁平格式更新限流器和熔断器配置
echo ""
echo "步骤 2: 使用扁平格式更新配置"
echo "=========================================="

FLAT_DATA=$(cat <<EOF
{
    "instanceId":"$INSTANCE_ID",
    "name":"$INSTANCE_NAME-updated",
    "baseUrl":"http://172.16.30.6:9091",
    "path":"/v1/chat/completions",
    "weight":9,
    "status":"active",
    "adapter":null,
    "headers":{
        "Authorization":"Bearer final-test-token",
        "X-Test":"final"
    },
    "rateLimitEnabled":true,
    "rateLimitAlgorithm":"token-bucket",
    "rateLimitCapacity":500,
    "rateLimitRate":50,
    "rateLimitScope":"instance",
    "rateLimitClientIpEnable":true,
    "circuitBreakerEnabled":true,
    "circuitBreakerFailureThreshold":20,
    "circuitBreakerTimeout":240000,
    "circuitBreakerSuccessThreshold":8
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

# 验证配置是否保存
echo ""
echo "步骤 3: 验证配置是否保存"
echo "=========================================="
sleep 2

CHECK_RESPONSE=$(curl -s -X GET "$BASE_URL/api/config/instance/chat" -H "Jairouter_Token: $TOKEN")

INSTANCE_DATA=$(echo "$CHECK_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\")")
echo ""
echo "实例 $INSTANCE_ID 的详细数据:"
echo "$INSTANCE_DATA" | jq '.'

# 验证各项配置
echo ""
echo "验证结果:"
echo "=========================================="

WEIGHT=$(echo "$INSTANCE_DATA" | jq -r '.weight')
if [ "$WEIGHT" = "9" ]; then
    echo "✓ 权重已更新: $WEIGHT"
else
    echo "✗ 权重未更新: $WEIGHT"
fi

NAME=$(echo "$INSTANCE_DATA" | jq -r '.name')
if [[ "$NAME" == *"updated"* ]]; then
    echo "✓ 名称已更新: $NAME"
else
    echo "✗ 名称未更新: $NAME"
fi

BASEURL=$(echo "$INSTANCE_DATA" | jq -r '.baseUrl')
if [[ "$BASEURL" == *"9091"* ]]; then
    echo "✓ BaseURL 已更新: $BASEURL"
else
    echo "✗ BaseURL 未更新: $BASEURL"
fi

# 验证限流器配置
RATELIMIT=$(echo "$INSTANCE_DATA" | jq -r '.rateLimit')
if [ "$RATELIMIT" != "null" ] && [ ! -z "$RATELIMIT" ]; then
    RL_ENABLED=$(echo "$INSTANCE_DATA" | jq -r '.rateLimit.enabled')
    RL_CAPACITY=$(echo "$INSTANCE_DATA" | jq -r '.rateLimit.capacity')
    if [ "$RL_ENABLED" = "true" ]; then
        echo "✓ 限流器配置已保存: enabled=$RL_ENABLED, capacity=$RL_CAPACITY"
    else
        echo "✗ 限流器配置 enabled 不正确: $RL_ENABLED"
    fi
else
    echo "✗ 限流器配置未保存 (null)"
fi

# 验证熔断器配置
CIRCUITBREAKER=$(echo "$INSTANCE_DATA" | jq -r '.circuitBreaker')
if [ "$CIRCUITBREAKER" != "null" ] && [ ! -z "$CIRCUITBREAKER" ]; then
    CB_ENABLED=$(echo "$INSTANCE_DATA" | jq -r '.circuitBreaker.enabled')
    CB_THRESHOLD=$(echo "$INSTANCE_DATA" | jq -r '.circuitBreaker.failureThreshold')
    if [ "$CB_ENABLED" = "true" ]; then
        echo "✓ 熔断器配置已保存: enabled=$CB_ENABLED, failureThreshold=$CB_THRESHOLD"
    else
        echo "✗ 熔断器配置 enabled 不正确: $CB_ENABLED"
    fi
else
    echo "✗ 熔断器配置未保存 (null)"
fi

echo ""
echo "=== 测试完成 ==="