#!/bin/bash

# 测试实例管理请求头配置功能的API脚本

BASE_URL="http://localhost:8080/api"
SERVICE_TYPE="chat"

echo "=== 测试实例管理请求头配置功能 ==="

# 1. 获取现有实例列表
echo "1. 获取现有实例列表..."
curl -X GET "${BASE_URL}/config/instance/type/${SERVICE_TYPE}" \
  -H "Content-Type: application/json" | jq '.'

echo -e "\n"

# 2. 添加带有请求头的新实例
echo "2. 添加带有请求头的新实例..."
curl -X POST "${BASE_URL}/config/instance/add/${SERVICE_TYPE}?createNewVersion=true" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-instance-with-headers",
    "baseUrl": "https://api.example.com",
    "path": "/v1/chat/completions",
    "weight": 1,
    "status": "active",
    "headers": {
      "Authorization": "Bearer test-api-key-12345",
      "Content-Type": "application/json",
      "X-Custom-Header": "test-value"
    }
  }' | jq '.'

echo -e "\n"

# 3. 再次获取实例列表，验证新实例是否包含请求头
echo "3. 验证新实例是否包含请求头..."
curl -X GET "${BASE_URL}/config/instance/type/${SERVICE_TYPE}" \
  -H "Content-Type: application/json" | jq '.'

echo -e "\n"

# 4. 更新实例的请求头配置
echo "4. 更新实例的请求头配置..."
INSTANCE_ID="test-instance-with-headers@https://api.example.com"
curl -X PUT "${BASE_URL}/config/instance/update/${SERVICE_TYPE}?createNewVersion=true" \
  -H "Content-Type: application/json" \
  -d '{
    "instanceId": "'${INSTANCE_ID}'",
    "instance": {
      "name": "test-instance-with-headers",
      "baseUrl": "https://api.example.com",
      "path": "/v1/chat/completions",
      "weight": 2,
      "status": "active",
      "headers": {
        "Authorization": "Bearer updated-api-key-67890",
        "Content-Type": "application/json",
        "X-Custom-Header": "updated-value",
        "X-New-Header": "new-header-value"
      }
    }
  }' | jq '.'

echo -e "\n"

# 5. 验证更新后的配置
echo "5. 验证更新后的配置..."
curl -X GET "${BASE_URL}/config/instance/type/${SERVICE_TYPE}" \
  -H "Content-Type: application/json" | jq '.'

echo -e "\n"

# 6. 清理测试数据（删除测试实例）
echo "6. 清理测试数据..."
curl -X DELETE "${BASE_URL}/config/instance/del/${SERVICE_TYPE}?instanceId=${INSTANCE_ID}&createNewVersion=true" \
  -H "Content-Type: application/json" | jq '.'

echo -e "\n=== 测试完成 ==="