#!/bin/bash

# 测试集成审计功能脚本
# 验证JWT令牌和API Key操作的审计记录

BASE_URL="http://localhost:8080/api"
API_KEY="dev-admin-12345-abcde-67890-fghij"

echo "=== 测试集成审计功能 ==="
echo "基础URL: $BASE_URL"
echo "使用API Key: ${API_KEY:0:20}..."
echo

# 1. 测试JWT登录审计
echo "1. 测试JWT登录审计..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }')

echo "登录响应: $LOGIN_RESPONSE"

# 提取JWT令牌
JWT_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token // empty')
if [ -n "$JWT_TOKEN" ]; then
    echo "JWT令牌获取成功: ${JWT_TOKEN:0:20}..."
else
    echo "JWT令牌获取失败"
fi

echo
sleep 2

# 2. 测试JWT令牌刷新审计
if [ -n "$JWT_TOKEN" ]; then
    echo "2. 测试JWT令牌刷新审计..."
    REFRESH_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/jwt/refresh" \
      -H "Content-Type: application/json" \
      -H "Jairouter_token: $JWT_TOKEN" \
      -d '{
        "token": "'$JWT_TOKEN'"
      }')
    
    echo "刷新响应: $REFRESH_RESPONSE"
    
    # 提取新令牌
    NEW_JWT_TOKEN=$(echo $REFRESH_RESPONSE | jq -r '.data.token // empty')
    if [ -n "$NEW_JWT_TOKEN" ]; then
        echo "新JWT令牌获取成功: ${NEW_JWT_TOKEN:0:20}..."
        JWT_TOKEN=$NEW_JWT_TOKEN
    fi
fi

echo
sleep 2

# 3. 测试API Key使用审计（使用开发环境的测试API Key）
echo "3. 测试API Key使用审计..."
API_KEY="dev-admin-12345-abcde-67890-fghij"

# 使用API Key访问一个端点
API_RESPONSE=$(curl -s -X GET "$BASE_URL/security/audit/extended/statistics/extended" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY")

echo "API Key使用响应: $API_RESPONSE"

echo
sleep 2

# 4. 测试JWT令牌撤销审计
if [ -n "$JWT_TOKEN" ]; then
    echo "4. 测试JWT令牌撤销审计..."
    REVOKE_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/jwt/revoke" \
      -H "Content-Type: application/json" \
      -H "Jairouter_token: $JWT_TOKEN" \
      -d '{
        "token": "'$JWT_TOKEN'",
        "reason": "测试撤销"
      }')
    
    echo "撤销响应: $REVOKE_RESPONSE"
fi

echo
sleep 2

# 5. 测试认证失败审计
echo "5. 测试认证失败审计..."
FAIL_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/jwt/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "invalid_user",
    "password": "wrong_password"
  }')

echo "认证失败响应: $FAIL_RESPONSE"

echo
sleep 3

# 6. 查询审计事件验证
echo "6. 查询审计事件验证..."

# 查询所有审计事件
echo "查询所有审计事件..."
ALL_EVENTS=$(curl -s -X POST "$BASE_URL/security/audit/extended/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
    "page": 0,
    "size": 50
  }')

echo "所有审计事件数量: $(echo $ALL_EVENTS | jq '.data.totalElements // 0')"

# 查询JWT事件
echo "查询JWT审计事件..."
JWT_EVENTS=$(curl -s -X GET "$BASE_URL/security/audit/extended/jwt-tokens?page=0&size=20" \
  -H "X-API-Key: $API_KEY")
echo "JWT审计事件数量: $(echo $JWT_EVENTS | jq '.data.totalElements // 0')"

# 查询API Key事件
echo "查询API Key审计事件..."
API_KEY_EVENTS=$(curl -s -X GET "$BASE_URL/security/audit/extended/api-keys?page=0&size=20" \
  -H "X-API-Key: $API_KEY")
echo "API Key审计事件数量: $(echo $API_KEY_EVENTS | jq '.data.totalElements // 0')"

# 查询安全事件
echo "查询安全审计事件..."
SECURITY_EVENTS=$(curl -s -X GET "$BASE_URL/security/audit/extended/security-events?page=0&size=20" \
  -H "X-API-Key: $API_KEY")
echo "安全审计事件数量: $(echo $SECURITY_EVENTS | jq '.data.totalElements // 0')"

echo
echo "7. 检查审计日志文件..."

# 检查审计日志文件是否存在
AUDIT_LOG_DIR="logs/audit"
if [ -d "$AUDIT_LOG_DIR" ]; then
    echo "审计日志目录存在: $AUDIT_LOG_DIR"
    ls -la "$AUDIT_LOG_DIR"
    
    # 显示最新的审计日志条目
    if [ -f "$AUDIT_LOG_DIR/security-audit.log" ]; then
        echo
        echo "最新的审计日志条目:"
        tail -10 "$AUDIT_LOG_DIR/security-audit.log"
    fi
else
    echo "审计日志目录不存在: $AUDIT_LOG_DIR"
fi

echo
echo "=== 集成审计功能测试完成 ==="
echo
echo "测试总结:"
echo "- JWT登录审计: 已测试"
echo "- JWT令牌刷新审计: 已测试"
echo "- JWT令牌撤销审计: 已测试"
echo "- API Key使用审计: 已测试"
echo "- 认证失败审计: 已测试"
echo "- 审计事件查询: 已测试"
echo "- 审计日志文件: 已检查"
echo
echo "请检查:"
echo "1. 审计事件是否正确记录到数据库/内存"
echo "2. 审计日志文件是否正确生成"
echo "3. 前端审计日志管理页面是否显示数据"