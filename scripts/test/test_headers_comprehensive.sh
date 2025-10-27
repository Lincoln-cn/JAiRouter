#!/bin/bash

# 综合测试脚本 - 测试headers配置的各种场景

BASE_URL="http://localhost:8080/api"
SERVICE_TYPE="chat"
API_KEY="dev-admin-12345-abcde-67890-fghij"

echo "=== Headers 配置综合测试 ==="

# 测试场景1: 添加带有headers的实例
echo "测试场景1: 添加带有headers的实例"
curl -X POST "${BASE_URL}/config/instance/add/${SERVICE_TYPE}?createNewVersion=true" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{
    "name": "test-with-headers",
    "baseUrl": "https://api.openai.com",
    "path": "/v1/chat/completions",
    "weight": 1,
    "status": "active",
    "headers": {
      "Authorization": "Bearer sk-test-key-123",
      "Content-Type": "application/json",
      "X-Custom-Header": "test-value"
    }
  }' | jq '.'

echo -e "\n"

# 测试场景2: 添加不带headers的实例
echo "测试场景2: 添加不带headers的实例"
curl -X POST "${BASE_URL}/config/instance/add/${SERVICE_TYPE}?createNewVersion=true" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{
    "name": "test-without-headers",
    "baseUrl": "https://api.example.com",
    "path": "/v1/chat/completions",
    "weight": 1,
    "status": "active"
  }' | jq '.'

echo -e "\n"

# 测试场景3: 添加带有空headers的实例
echo "测试场景3: 添加带有空headers的实例"
curl -X POST "${BASE_URL}/config/instance/add/${SERVICE_TYPE}?createNewVersion=true" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{
    "name": "test-empty-headers",
    "baseUrl": "https://api.test.com",
    "path": "/v1/chat/completions",
    "weight": 1,
    "status": "active",
    "headers": {}
  }' | jq '.'

echo -e "\n"

# 验证所有实例
echo "验证所有测试实例:"
curl -X GET "${BASE_URL}/config/instance/type/${SERVICE_TYPE}" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" | jq '.data[] | select(.name | startswith("test-")) | {name: .name, headers: .headers}'

echo -e "\n"

# 测试场景4: 更新实例的headers
echo "测试场景4: 更新实例的headers"
INSTANCE_ID="test-with-headers@https://api.openai.com"
curl -X PUT "${BASE_URL}/config/instance/update/${SERVICE_TYPE}?createNewVersion=true" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" \
  -d '{
    "instanceId": "'${INSTANCE_ID}'",
    "instance": {
      "name": "test-with-headers",
      "baseUrl": "https://api.openai.com",
      "path": "/v1/chat/completions",
      "weight": 1,
      "status": "active",
      "headers": {
        "Authorization": "Bearer sk-updated-key-456",
        "Content-Type": "application/json",
        "X-Updated-Header": "updated-value"
      }
    }
  }' | jq '.'

echo -e "\n"

# 再次验证更新后的实例
echo "验证更新后的实例:"
curl -X GET "${BASE_URL}/config/instance/type/${SERVICE_TYPE}" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: ${API_KEY}" | jq '.data[] | select(.name == "test-with-headers") | {name: .name, headers: .headers}'

echo -e "\n"

# 清理测试数据
echo "清理测试数据..."
for instance_name in "test-with-headers" "test-without-headers" "test-empty-headers"; do
  # 获取实例ID
  instance_data=$(curl -s -X GET "${BASE_URL}/config/instance/type/${SERVICE_TYPE}" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${API_KEY}" | jq -r ".data[] | select(.name == \"${instance_name}\") | .instanceId")
  
  if [ "$instance_data" != "null" ] && [ -n "$instance_data" ]; then
    echo "删除实例: ${instance_name} (ID: ${instance_data})"
    curl -X DELETE "${BASE_URL}/config/instance/del/${SERVICE_TYPE}?instanceId=${instance_data}&createNewVersion=true" \
      -H "Content-Type: application/json" \
      -H "X-API-Key: ${API_KEY}" | jq '.'
  fi
done

echo -e "\n=== 测试完成 ==="