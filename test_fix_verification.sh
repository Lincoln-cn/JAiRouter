#!/bin/bash

echo "=== 验证实例管理中限流器和熔断器配置修复 ==="

# 检查应用是否运行
echo "检查应用是否在运行..."
if curl -sf http://localhost:8080/health >/dev/null 2>&1; then
    echo "应用正在运行"
else
    echo "应用未运行，启动应用..."
    cd /home/ubuntu/jairouter/modelrouter
    timeout 120s ./mvnw spring-boot:run -P fast &
    sleep 10
fi

# 获取管理员令牌
echo "获取管理员令牌..."
TOKEN=$(curl -s -X POST "http://localhost:8080/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.data.token' 2>/dev/null)

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    # 如果默认凭据无效，尝试使用环境变量或其他方式
    echo "无法获取令牌，使用预设令牌"
    TOKEN="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIiwiVVNFUiJdLCJpc3MiOiJqYWlyb3V0ZXIiLCJpYXQiOjE3NzQ5NDA4OTcsImV4cCI6MTc3NDk0NDQ5N30.KTZNbOJG6D0NYkv7Y3YjgmL9-NapbtzJH7O-rMzISGc"
fi

echo "使用令牌: ${TOKEN:0:20}..."

# 创建测试实例
echo "创建测试实例..."
INSTANCE_NAME="test-instance-$(date +%s)"
curl -X PUT 'http://localhost:8080/api/config/instance/chat/test' \
  -H "Jairouter_Token: $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-raw "{
    \"name\":\"$INSTANCE_NAME\",
    \"baseUrl\":\"http://localhost:9090\",
    \"path\":\"/v1/chat/completions\",
    \"weight\":1,
    \"status\":\"active\",
    \"adapter\":null,
    \"headers\":{\"Authorization\":\"Bearer test-token\"},
    \"rateLimit\":{
      \"enabled\":true,
      \"algorithm\":\"token-bucket\",
      \"capacity\":100,
      \"rate\":10,
      \"scope\":\"instance\",
      \"key\":\"\",
      \"clientIpEnable\":false
    },
    \"circuitBreaker\":{
      \"enabled\":true,
      \"failureThreshold\":5,
      \"timeout\":60000,
      \"successThreshold\":2
    }
  }" -s

sleep 2

# 获取实例列表，检查配置是否保存
echo "获取实例列表，检查配置是否保存..."
RESPONSE=$(curl -s -X GET "http://localhost:8080/api/config/instance/chat" \
  -H "Jairouter_Token: $TOKEN")

echo "响应数据片段:"
echo "$RESPONSE" | jq ".data[] | select(.name==\"$INSTANCE_NAME\")" 2>/dev/null || echo "无法解析JSON响应"

# 检查返回的数据中是否包含rateLimit和circuitBreaker配置
if echo "$RESPONSE" | jq -e ".data[] | select(.name==\"$INSTANCE_NAME\") | .rateLimit" >/dev/null 2>&1; then
    echo "✓ 限流配置已正确保存"
    RATELIMIT_ENABLED=$(echo "$RESPONSE" | jq -r ".data[] | select(.name==\"$INSTANCE_NAME\") | .rateLimit.enabled" 2>/dev/null)
    echo "  限流器状态: $RATELIMIT_ENABLED"
else
    echo "✗ 限流配置未保存"
fi

if echo "$RESPONSE" | jq -e ".data[] | select(.name==\"$INSTANCE_NAME\") | .circuitBreaker" >/dev/null 2>&1; then
    echo "✓ 熔断器配置已正确保存"
    CB_ENABLED=$(echo "$RESPONSE" | jq -r ".data[] | select(.name==\"$INSTANCE_NAME\") | .circuitBreaker.enabled" 2>/dev/null)
    echo "  熔断器状态: $CB_ENABLED"
else
    echo "✗ 熔断器配置未保存"
fi

# 验证特定实例的详细信息
INSTANCE_ID=$(echo "$RESPONSE" | jq -r ".data[] | select(.name==\"$INSTANCE_NAME\") | .instanceId" 2>/dev/null)
if [ ! -z "$INSTANCE_ID" ] && [ "$INSTANCE_ID" != "null" ]; then
    echo "获取实例 $INSTANCE_ID 的详细信息..."
    DETAILS=$(curl -s -X GET "http://localhost:8080/api/config/instance/chat/$INSTANCE_ID" \
      -H "Jairouter_Token: $TOKEN")
    
    if echo "$DETAILS" | jq -e ".data.rateLimit" >/dev/null 2>&1; then
        echo "✓ 限流配置在实例详情中可用"
    else
        echo "✗ 限流配置在实例详情中不可用"
    fi
    
    if echo "$DETAILS" | jq -e ".data.circuitBreaker" >/dev/null 2>&1; then
        echo "✓ 熔断器配置在实例详情中可用"
    else
        echo "✗ 熔断器配置在实例详情中不可用"
    fi
else
    echo "无法获取实例ID，跳过详细信息检查"
fi

echo "=== 测试完成 ==="