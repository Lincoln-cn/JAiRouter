#!/bin/bash

# 简单版本生成测试

BASE_URL="http://172.16.30.6:8080"

# 获取令牌
TOKEN=$(curl -s -X POST "${BASE_URL}/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}' | jq -r '.data.token')

echo "Token: ${TOKEN:0:50}..."

# 1. 获取初始版本列表
echo ""
echo "=== 初始版本列表 ==="
curl -s -X GET "${BASE_URL}/api/config/version" \
  -H "Jairouter_Token: $TOKEN" | jq '.'

# 2. 获取初始版本详细信息
echo ""
echo "=== 初始版本详细信息 ==="
curl -s -X GET "${BASE_URL}/api/config/version/info" \
  -H "Jairouter_Token: $TOKEN" | jq '.'

# 3. 创建一个服务（使用现有的 chat 服务）
echo ""
echo "=== 更新 chat 服务 ==="
UPDATE_DATA='{
    "adapter": "normal",
    "instances": [
        {
            "name": "test-chat-instance",
            "baseUrl": "http://localhost:9090",
            "path": "/v1/chat/completions",
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

curl -s -X PUT "${BASE_URL}/api/config/type/services/chat" \
  -H "Jairouter_Token: $TOKEN" \
  -H "Content-Type: application/json" \
  --data-raw "$UPDATE_DATA" | jq '.'

# 等待版本生成
sleep 3

# 4. 获取更新后的版本列表
echo ""
echo "=== 更新后的版本列表 ==="
curl -s -X GET "${BASE_URL}/api/config/version" \
  -H "Jairouter_Token: $TOKEN" | jq '.'

# 5. 获取更新后的版本详细信息
echo ""
echo "=== 更新后的版本详细信息 ==="
curl -s -X GET "${BASE_URL}/api/config/version/info" \
  -H "Jairouter_Token: $TOKEN" | jq '.'
