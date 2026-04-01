#!/bin/bash

echo "=== 测试接口是否正确返回限流器和熔断器配置 ==="

# 首先启动应用（使用内存数据库模式避免文件锁定问题）
echo "启动应用..."
cd /home/ubuntu/jairouter/modelrouter
export SPRING_PROFILES_ACTIVE=test
timeout 120s ./mvnw spring-boot:run -Dspring-boot.run.profiles=test -Dspring-boot.run.arguments="--server.port=8081 --spring.datasource.url=jdbc:h2:mem:testdb" &
APP_PID=$!

# 等待应用启动
echo "等待应用启动..."
sleep 20

# 检查应用是否启动成功
if ! curl -sf http://localhost:8081/health >/dev/null 2>&1; then
    echo "应用未能启动，尝试使用预设端口"
    # 如果上面的启动失败，我们直接检查代码逻辑
    echo "直接检查代码实现..."
else
    echo "应用已启动，开始测试接口..."

    # 获取管理员令牌
    echo "获取管理员令牌..."
    TOKEN=$(curl -s -X POST "http://localhost:8081/api/auth/login" \
      -H "Content-Type: application/json" \
      -d '{"username":"admin","password":"admin"}' | jq -r '.data.token' 2>/dev/null)

    if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
        # 使用默认令牌
        TOKEN="eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIiwiVVNFUiJdLCJpc3MiOiJqYWlyb3V0ZXIiLCJpYXQiOjE3NzQ5NDA4OTcsImV4cCI6MTc3NDk0NDQ5N30.KTZNbOJG6D0NYkv7Y3YjgmL9-NapbtzJH7O-rMzISGc"
    fi

    echo "使用令牌: ${TOKEN:0:20}..."

    # 创建测试实例
    echo "创建测试实例..."
    INSTANCE_NAME="test-instance-$(date +%s)"
    RESPONSE=$(curl -s -X PUT "http://localhost:8081/api/config/instance/chat/test" \
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
      }")

    echo "创建实例响应: $RESPONSE"

    sleep 2

    # 获取实例列表，检查配置是否保存和返回
    echo "获取实例列表..."
    LIST_RESPONSE=$(curl -s -X GET "http://localhost:8081/api/config/instance/chat" \
      -H "Jairouter_Token: $TOKEN")

    echo "实例列表响应片段:"
    echo "$LIST_RESPONSE" | jq ".data[] | select(.name==\"$INSTANCE_NAME\")" 2>/dev/null || echo "无法解析JSON响应"

    # 检查返回的数据中是否包含rateLimit和circuitBreaker配置
    if echo "$LIST_RESPONSE" | jq -e ".data[] | select(.name==\"$INSTANCE_NAME\") | .rateLimit" >/dev/null 2>&1; then
        echo "✓ 限流配置已正确返回"
        RATELIMIT_ENABLED=$(echo "$LIST_RESPONSE" | jq -r ".data[] | select(.name==\"$INSTANCE_NAME\") | .rateLimit.enabled" 2>/dev/null)
        echo "  限流器状态: $RATELIMIT_ENABLED"
    else
        echo "✗ 限流配置未返回"
    fi

    if echo "$LIST_RESPONSE" | jq -e ".data[] | select(.name==\"$INSTANCE_NAME\") | .circuitBreaker" >/dev/null 2>&1; then
        echo "✓ 熔断器配置已正确返回"
        CB_ENABLED=$(echo "$LIST_RESPONSE" | jq -r ".data[] | select(.name==\"$INSTANCE_NAME\") | .circuitBreaker.enabled" 2>/dev/null)
        echo "  熔断器状态: $CB_ENABLED"
    else
        echo "✗ 熔断器配置未返回"
    fi

    # 验证特定实例的详细信息
    INSTANCE_ID=$(echo "$LIST_RESPONSE" | jq -r ".data[] | select(.name==\"$INSTANCE_NAME\") | .instanceId" 2>/dev/null)
    if [ ! -z "$INSTANCE_ID" ] && [ "$INSTANCE_ID" != "null" ]; then
        echo "获取实例 $INSTANCE_ID 的详细信息..."
        DETAILS=$(curl -s -X GET "http://localhost:8081/api/config/instance/chat/$INSTANCE_ID" \
          -H "Jairouter_Token: $TOKEN")
        
        echo "实例详情响应片段:"
        echo "$DETAILS" | jq '.' 2>/dev/null || echo "无法解析JSON响应"
        
        if echo "$DETAILS" | jq -e ".data.rateLimit" >/dev/null 2>&1; then
            echo "✓ 限流配置在实例详情中正确返回"
        else
            echo "✗ 限流配置在实例详情中未返回"
        fi
        
        if echo "$DETAILS" | jq -e ".data.circuitBreaker" >/dev/null 2>&1; then
            echo "✓ 熔断器配置在实例详情中正确返回"
        else
            echo "✗ 熔断器配置在实例详情中未返回"
        fi
    else
        echo "无法获取实例ID，跳过详细信息检查"
    fi

    # 清理：删除测试实例
    if [ ! -z "$INSTANCE_ID" ] && [ "$INSTANCE_ID" != "null" ]; then
        echo "清理：删除测试实例..."
        DELETE_RESPONSE=$(curl -s -X DELETE "http://localhost:8081/api/config/instance/chat/$INSTANCE_ID" \
          -H "Jairouter_Token: $TOKEN")
        echo "删除实例响应: $DELETE_RESPONSE"
    fi

    # 结束应用
    kill $APP_PID 2>/dev/null
fi

echo ""
echo "=== 检查代码中关键的修复点 ==="

# 检查关键修复点是否存在
echo "检查 DatabaseConfigService 中的 buildInstanceMap 方法是否返回熔断器配置..."
if grep -n "result.put(\"circuitBreaker\", vo.getCircuitBreaker())" /home/ubuntu/jairouter/modelrouter/src/main/java/org/unreal/modelrouter/config/DatabaseConfigService.java >/dev/null 2>&1; then
    echo "✓ 关键修复已实现：buildInstanceMap 返回熔断器配置"
else
    echo "✗ 关键修复缺失：buildInstanceMap 未返回熔断器配置"
fi

echo ""
echo "=== 接口测试完成 ==="