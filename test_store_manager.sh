#!/bin/bash

# 测试 StoreManager 是否正常工作

BASE_URL="http://172.16.30.6:8080"

# 获取令牌
TOKEN=$(curl -s -X POST "${BASE_URL}/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}' | jq -r '.data.token')

echo "Token: ${TOKEN:0:50}..."

# 1. 获取版本 0 的配置（默认配置）
echo ""
echo "=== 获取版本 0 配置 ==="
curl -s -X GET "${BASE_URL}/api/config/version/0" \
  -H "Jairouter_Token: $TOKEN" | jq '.data | keys'

# 2. 直接测试版本保存 - 使用实例更新接口
echo ""
echo "=== 测试实例更新（应该生成版本） ==="

# 先获取 chat 服务的实例列表
INSTANCES_RESPONSE=$(curl -s -X GET "${BASE_URL}/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "当前 chat 服务实例:"
echo "$INSTANCES_RESPONSE" | jq '.data[].name'

# 更新第一个实例
INSTANCE_ID=$(echo "$INSTANCES_RESPONSE" | jq -r '.data[0].instanceId')
echo ""
echo "更新实例 ID: $INSTANCE_ID"

UPDATE_DATA='{
  "instanceId": "'$INSTANCE_ID'",
  "name": "qwen3:4b",
  "baseUrl": "http://172.16.30.6:9090",
  "path": "/v1/chat/completions",
  "weight": 2,
  "status": "active",
  "rateLimitEnabled": true,
  "rateLimitAlgorithm": "token-bucket",
  "rateLimitCapacity": 100,
  "rateLimitRate": 10,
  "rateLimitScope": "instance",
  "rateLimitClientIpEnable": false,
  "circuitBreakerEnabled": false,
  "circuitBreakerFailureThreshold": 5,
  "circuitBreakerTimeout": 60000,
  "circuitBreakerSuccessThreshold": 2
}'

curl -s -X PUT "${BASE_URL}/api/config/instance/chat/$INSTANCE_ID" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$UPDATE_DATA" | jq '.'

# 等待版本生成
sleep 3

# 3. 检查版本列表
echo ""
echo "=== 检查版本列表 ==="
curl -s -X GET "${BASE_URL}/api/config/version" \
  -H "Jairouter_Token: $TOKEN" | jq '.'

# 4. 检查版本详细信息
echo ""
echo "=== 检查版本详细信息 ==="
curl -s -X GET "${BASE_URL}/api/config/version/info" \
  -H "Jairouter_Token: $TOKEN" | jq '.'
