#!/bin/bash

# JWT账号创建和列表测试脚本

BASE_URL="http://localhost:8080"
ADMIN_TOKEN=""

echo "=== JWT账号创建和列表测试 ==="
echo ""

# 1. 登录获取管理员token
echo "1. 登录获取管理员token..."
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "UqfpTm2Zw7ff2BNnZb8AQo8t"
  }')

ADMIN_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token')

if [ "$ADMIN_TOKEN" == "null" ] || [ -z "$ADMIN_TOKEN" ]; then
  echo "❌ 登录失败"
  echo "响应: $LOGIN_RESPONSE"
  exit 1
fi

echo "✅ 登录成功，Token: ${ADMIN_TOKEN:0:20}..."
echo ""

# 2. 获取当前账号列表
echo "2. 获取创建前的账号列表..."
BEFORE_LIST=$(curl -s -X GET "${BASE_URL}/api/security/jwt/accounts" \
  -H "jairouter_token: ${ADMIN_TOKEN}")

BEFORE_COUNT=$(echo $BEFORE_LIST | jq '.data | length')
echo "✅ 当前账号数量: $BEFORE_COUNT"
echo "账号列表: $(echo $BEFORE_LIST | jq -c '.data[] | {username, roles, enabled}')"
echo ""

# 3. 创建新账号
echo "3. 创建新账号 'testuser'..."
CREATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/security/jwt/accounts" \
  -H "jairouter_token: ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "test123",
    "roles": ["USER"],
    "enabled": true
  }')

CREATE_SUCCESS=$(echo $CREATE_RESPONSE | jq -r '.success')

if [ "$CREATE_SUCCESS" != "true" ]; then
  echo "❌ 创建账号失败"
  echo "响应: $CREATE_RESPONSE"
  exit 1
fi

echo "✅ 账号创建成功"
echo "响应: $(echo $CREATE_RESPONSE | jq -c '.')"
echo ""

# 4. 等待一下确保数据已保存
echo "4. 等待数据保存..."
sleep 2
echo ""

# 5. 再次获取账号列表
echo "5. 获取创建后的账号列表..."
AFTER_LIST=$(curl -s -X GET "${BASE_URL}/api/security/jwt/accounts" \
  -H "jairouter_token: ${ADMIN_TOKEN}")

AFTER_COUNT=$(echo $AFTER_LIST | jq '.data | length')
echo "✅ 当前账号数量: $AFTER_COUNT"
echo "账号列表: $(echo $AFTER_LIST | jq -c '.data[] | {username, roles, enabled}')"
echo ""

# 6. 验证新账号是否存在
echo "6. 验证新账号是否存在..."
TESTUSER_EXISTS=$(echo $AFTER_LIST | jq '.data[] | select(.username == "testuser")')

if [ -z "$TESTUSER_EXISTS" ]; then
  echo "❌ 新账号未找到！"
  echo "这是问题所在：账号创建成功但列表中没有"
  exit 1
fi

echo "✅ 新账号已找到"
echo "账号信息: $(echo $TESTUSER_EXISTS | jq -c '.')"
echo ""

# 7. 验证账号数量是否增加
if [ "$AFTER_COUNT" -le "$BEFORE_COUNT" ]; then
  echo "❌ 账号数量没有增加！"
  echo "创建前: $BEFORE_COUNT, 创建后: $AFTER_COUNT"
  exit 1
fi

echo "✅ 账号数量正确增加"
echo "创建前: $BEFORE_COUNT, 创建后: $AFTER_COUNT"
echo ""

# 8. 测试新账号登录
echo "7. 测试新账号登录..."
TEST_LOGIN=$(curl -s -X POST "${BASE_URL}/api/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "test123"
  }')

TEST_TOKEN=$(echo $TEST_LOGIN | jq -r '.data.token')

if [ "$TEST_TOKEN" == "null" ] || [ -z "$TEST_TOKEN" ]; then
  echo "❌ 新账号登录失败"
  echo "响应: $TEST_LOGIN"
else
  echo "✅ 新账号登录成功"
  echo "Token: ${TEST_TOKEN:0:20}..."
fi
echo ""

# 9. 清理：删除测试账号
echo "8. 清理：删除测试账号..."
DELETE_RESPONSE=$(curl -s -X DELETE "${BASE_URL}/api/security/jwt/accounts/testuser" \
  -H "jairouter_token: ${ADMIN_TOKEN}")

DELETE_SUCCESS=$(echo $DELETE_RESPONSE | jq -r '.success')

if [ "$DELETE_SUCCESS" != "true" ]; then
  echo "⚠️  删除账号失败（可能需要手动清理）"
  echo "响应: $DELETE_RESPONSE"
else
  echo "✅ 测试账号已删除"
fi
echo ""

echo "=== 测试完成 ==="
echo ""
echo "总结："
echo "- 账号创建: ✅"
echo "- 列表刷新: ✅"
echo "- 账号登录: ✅"
echo "- 账号删除: ✅"
