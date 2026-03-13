#!/bin/bash

# Tracing 功能测试脚本

BASE_URL="http://localhost:8081"
API_KEY="dev-admin-12345-abcde-67890-fghij"

echo "========================================"
echo "  Tracing 功能测试"
echo "========================================"
echo ""

echo "=== 1. 发送测试请求 ==="
for i in 1 2 3 4 5 6 7 8 9 10; do
  curl -s -X GET \
    -H "X-API-Key: $API_KEY" \
    "$BASE_URL/v1/models" > /dev/null
  echo "✓ 请求 $i 完成"
done

echo ""
echo "=== 2. 检查追踪统计 ==="
curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/tracing/query/statistics" | python3 -m json.tool 2>/dev/null | head -40

echo ""
echo "=== 3. 检查服务列表 ==="
curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/tracing/query/services" | python3 -m json.tool 2>/dev/null | head -20

echo ""
echo "=== 4. 检查追踪健康状态 ==="
curl -s "http://localhost:8081/actuator/health" | python3 -m json.tool 2>/dev/null | grep -A 20 tracing

echo ""
echo "=== 5. 查看日志中的追踪信息 ==="
tail -50 /tmp/tracing-test-8081.log | grep -i "tracing\|span\|trace" | head -20

echo ""
echo "========================================"
echo "  测试完成"
echo "========================================"
