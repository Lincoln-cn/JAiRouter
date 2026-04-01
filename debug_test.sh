#!/bin/bash

echo "=== 完整测试：限流器和熔断器配置 ==="
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

# 步骤 1: 获取当前实例列表
echo ""
echo "步骤 1: 获取当前实例列表"
echo "=========================================="
LIST=$(curl -s -X GET "$BASE_URL/api/config/instance/chat" -H "Jairouter_Token: $TOKEN")
echo "$LIST" | jq '.'

# 获取第一个实例的 ID
INSTANCE_ID=$(echo "$LIST" | jq -r '.data[0].instanceId' 2>/dev/null)
INSTANCE_NAME=$(echo "$LIST" | jq -r '.data[0].name' 2>/dev/null)

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "null" ]; then
    echo "✗ 没有可用的实例进行测试"
    exit 1
fi

echo ""
echo "找到测试实例：ID=$INSTANCE_ID, Name=$INSTANCE_NAME"

# 步骤 2: 更新实例配置（包含限流器和熔断器）
echo ""
echo "步骤 2: 更新实例配置"
echo "=========================================="
echo "发送更新请求..."

UPDATE_DATA=$(cat <<EOF
{
    "instanceId":"$INSTANCE_ID",
    "name":"$INSTANCE_NAME",
    "baseUrl":"http://172.16.30.6:9090",
    "path":"/v1/chat/completions",
    "weight":5,
    "status":"active",
    "adapter":null,
    "headers":{
        "Authorization":"Bearer test-token-$(date +%s)",
        "X-Custom-Header":"custom-value"
    },
    "rateLimit":{
        "enabled":true,
        "algorithm":"token-bucket",
        "capacity":200,
        "rate":20,
        "scope":"instance",
        "key":"test-key",
        "clientIpEnable":true
    },
    "circuitBreaker":{
        "enabled":true,
        "failureThreshold":10,
        "timeout":120000,
        "successThreshold":3
    }
}
EOF
)

echo "更新数据:"
echo "$UPDATE_DATA" | jq '.'

UPDATE_RESPONSE=$(curl -s -X PUT "$BASE_URL/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-raw "$UPDATE_DATA")

echo ""
echo "更新响应:"
echo "$UPDATE_RESPONSE" | jq '.'

# 步骤 3: 再次获取实例列表，检查配置是否保存
echo ""
echo "步骤 3: 验证配置是否保存"
echo "=========================================="
sleep 2

CHECK_RESPONSE=$(curl -s -X GET "$BASE_URL/api/config/instance/chat" -H "Jairouter_Token: $TOKEN")
echo "获取实例列表:"
echo "$CHECK_RESPONSE" | jq '.'

# 检查特定实例的配置
INSTANCE_DATA=$(echo "$CHECK_RESPONSE" | jq ".data[] | select(.instanceId==\"$INSTANCE_ID\")")
echo ""
echo "实例 $INSTANCE_ID 的详细数据:"
echo "$INSTANCE_DATA" | jq '.'

# 验证限流器配置
echo ""
echo "验证限流器配置:"
RATELIMIT=$(echo "$INSTANCE_DATA" | jq -r '.rateLimit')
if [ "$RATELIMIT" != "null" ] && [ ! -z "$RATELIMIT" ]; then
    echo "✓ 限流器配置已保存"
    echo "$INSTANCE_DATA" | jq '.rateLimit'
else
    echo "✗ 限流器配置未保存 (null)"
fi

# 验证熔断器配置
echo ""
echo "验证熔断器配置:"
CIRCUITBREAKER=$(echo "$INSTANCE_DATA" | jq -r '.circuitBreaker')
if [ "$CIRCUITBREAKER" != "null" ] && [ ! -z "$CIRCUITBREAKER" ]; then
    echo "✓ 熔断器配置已保存"
    echo "$INSTANCE_DATA" | jq '.circuitBreaker'
else
    echo "✗ 熔断器配置未保存 (null)"
fi

# 验证其他字段
echo ""
echo "验证其他字段:"
echo "  权重：$(echo "$INSTANCE_DATA" | jq -r '.weight')"
echo "  请求头：$(echo "$INSTANCE_DATA" | jq -r '.headers')"

echo ""
echo "=== 测试完成 ==="