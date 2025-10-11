#!/bin/bash

# 测试审计日志功能脚本
# 用于验证审计日志管理页面的数据显示

BASE_URL="http://localhost:8080/api"

echo "=== 测试审计日志功能 ==="
echo "基础URL: $BASE_URL"
echo

# 1. 生成测试审计数据
echo "1. 生成测试审计数据..."
curl -X POST "$BASE_URL/security/audit/extended/test-data/generate" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  2>/dev/null | jq '.' || echo "生成测试数据请求已发送"

echo
echo "等待 2 秒让数据生成完成..."
sleep 2

# 2. 查询所有审计事件
echo
echo "2. 查询所有审计事件..."
curl -X POST "$BASE_URL/security/audit/extended/query" \
  -H "Content-Type: application/json" \
  -d '{
    "page": 0,
    "size": 20
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  2>/dev/null | jq '.' || echo "查询审计事件请求已发送"

# 3. 查询JWT令牌审计事件
echo
echo "3. 查询JWT令牌审计事件..."
curl -X GET "$BASE_URL/security/audit/extended/jwt-tokens?page=0&size=10" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  2>/dev/null | jq '.' || echo "查询JWT令牌事件请求已发送"

# 4. 查询API Key审计事件
echo
echo "4. 查询API Key审计事件..."
curl -X GET "$BASE_URL/security/audit/extended/api-keys?page=0&size=10" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  2>/dev/null | jq '.' || echo "查询API Key事件请求已发送"

# 5. 查询安全事件
echo
echo "5. 查询安全事件..."
curl -X GET "$BASE_URL/security/audit/extended/security-events?page=0&size=10" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  2>/dev/null | jq '.' || echo "查询安全事件请求已发送"

# 6. 生成安全报告
echo
echo "6. 生成安全报告..."
curl -X GET "$BASE_URL/security/audit/extended/reports/security" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  2>/dev/null | jq '.' || echo "生成安全报告请求已发送"

# 7. 获取扩展审计统计信息
echo
echo "7. 获取扩展审计统计信息..."
curl -X GET "$BASE_URL/security/audit/extended/statistics/extended" \
  -H "Content-Type: application/json" \
  -w "\nHTTP Status: %{http_code}\n" \
  2>/dev/null | jq '.' || echo "获取统计信息请求已发送"

echo
echo "=== 测试完成 ==="
echo "如果所有请求都返回 HTTP 200 状态码，说明审计功能正常工作"
echo "现在可以打开前端页面 http://localhost:8080/swagger-ui.html 查看API文档"
echo "或者直接访问审计日志管理页面测试数据显示"