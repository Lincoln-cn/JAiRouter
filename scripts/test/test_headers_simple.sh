#!/bin/bash

# 简单测试脚本 - 添加带有headers的实例

BASE_URL="http://localhost:8080/api"
SERVICE_TYPE="chat"
API_KEY="dev-admin-12345-abcde-67890-fghij"

echo "=== 测试添加带有headers的实例 ==="

# 添加带有请求头的新实例
echo "添加带有请求头的新实例..."
curl -X POST "${BASE_URL}/config/instance/add/${SERVICE_TYPE}?createNewVersion=true" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{
    "name": "test-headers-instance",
    "baseUrl": "https://api.openai.com",
    "path": "/v1/chat/completions",
    "weight": 1,
    "status": "active",
    "headers": {
      "Authorization": "Bearer sk-test-key-123",
      "Content-Type": "application/json"
    }
  }' | jq '.'

echo -e "\n"

# 获取实例列表验证
echo "验证实例是否包含headers..."
curl -X GET "${BASE_URL}/config/instance/type/${SERVICE_TYPE}" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" | jq '.data[] | select(.name == "test-headers-instance")'

echo -e "\n=== 测试完成 ==="