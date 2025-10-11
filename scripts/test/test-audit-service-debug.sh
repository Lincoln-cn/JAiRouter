#!/bin/bash

# 调试审计服务脚本
# 检查审计服务是否正确工作

BASE_URL="http://localhost:8080/api"

echo "=== 调试审计服务 ==="
echo "基础URL: $BASE_URL"
echo

# 使用开发环境的API Key进行认证
API_KEY="dev-admin-12345-abcde-67890-fghij"

# 1. 先生成一些测试数据
echo "1. 生成测试审计数据..."
GENERATE_RESPONSE=$(curl -s -X POST "$BASE_URL/security/audit/extended/test-data/generate" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY")

echo "生成测试数据响应: $GENERATE_RESPONSE"
echo

# 等待数据生成
sleep 3

# 2. 查询审计事件
echo "2. 查询审计事件..."
QUERY_RESPONSE=$(curl -s -X POST "$BASE_URL/security/audit/extended/query" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
    "page": 0,
    "size": 20
  }')

echo "查询响应: $QUERY_RESPONSE"
echo

# 3. 检查事件数量
EVENT_COUNT=$(echo $QUERY_RESPONSE | jq '.data.totalElements // 0')
echo "审计事件数量: $EVENT_COUNT"

if [ "$EVENT_COUNT" -gt 0 ]; then
    echo "✅ 审计服务工作正常，找到 $EVENT_COUNT 个事件"
    
    # 显示前几个事件
    echo
    echo "前3个审计事件:"
    echo $QUERY_RESPONSE | jq '.data.events[:3]'
else
    echo "❌ 审计服务可能有问题，没有找到审计事件"
    
    # 检查响应是否有错误
    ERROR_MSG=$(echo $QUERY_RESPONSE | jq -r '.message // "无错误信息"')
    echo "错误信息: $ERROR_MSG"
fi

echo
echo "3. 检查应用程序日志中的审计信息..."

# 检查最新的日志文件中是否有审计相关的信息
if [ -f "logs/jairouter-all.log" ]; then
    echo "搜索审计相关日志..."
    grep -i "audit\|ExtendedSecurityAuditService" logs/jairouter-all.log | tail -10
    
    echo
    echo "搜索AUDIT_LOG相关日志..."
    grep "AUDIT_LOG" logs/jairouter-all.log | tail -5
else
    echo "未找到应用程序日志文件"
fi

echo
echo "4. 检查审计日志目录..."
if [ -d "logs/audit" ]; then
    echo "审计日志目录存在:"
    ls -la logs/audit/
    
    if [ -f "logs/audit/security-audit.log" ]; then
        echo
        echo "审计日志文件内容（最后10行）:"
        tail -10 logs/audit/security-audit.log
    fi
else
    echo "审计日志目录不存在"
fi

echo
echo "=== 调试完成 ==="