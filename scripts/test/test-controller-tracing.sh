#!/bin/bash

# ============================================================================
# 统一 Controller 请求链路追踪测试
# ============================================================================

BASE_URL="http://localhost:8081"
API_KEY="dev-admin-12345-abcde-67890-fghij"
LOG_FILE="/tmp/tracing-controller-test.log"

echo "========================================"
echo "  统一 Controller 请求链路追踪测试"
echo "========================================"
echo ""

# 生成唯一的请求 ID
REQUEST_ID="test-$(date +%s)"
echo "请求 ID: $REQUEST_ID"
echo ""

# ============================================================================
# 测试 1: 发送聊天请求并追踪链路
# ============================================================================
echo "=== 测试 1: 发送聊天请求 ==="

# 创建测试消息
CHAT_DATA='{
  "model": "qwen3:4b",
  "messages": [
    {"role": "user", "content": "你好，请介绍一下你自己"}
  ],
  "max_tokens": 100,
  "temperature": 0.7
}'

echo "发送请求到：$BASE_URL/v1/chat/completions"
echo "请求数据：$CHAT_DATA"
echo ""

# 发送请求并捕获响应
RESPONSE=$(curl -s -w "\n%{http_code}\n%{time_total}" \
  -X POST \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -H "X-Request-ID: $REQUEST_ID" \
  -d "$CHAT_DATA" \
  "$BASE_URL/v1/chat/completions" 2>/dev/null)

HTTP_CODE=$(echo "$RESPONSE" | tail -2 | head -1)
DURATION=$(echo "$RESPONSE" | tail -1)

echo "HTTP 状态码：$HTTP_CODE"
echo "请求耗时：${DURATION}s"
echo ""

# ============================================================================
# 测试 2: 发送嵌入请求并追踪链路
# ============================================================================
echo "=== 测试 2: 发送嵌入请求 ==="

EMBEDDING_DATA='{
  "model": "nomic-embed-text-v1.5",
  "input": "这是一段测试文本，用于验证追踪功能"
}'

echo "发送请求到：$BASE_URL/v1/embeddings"
echo ""

RESPONSE2=$(curl -s -w "\n%{http_code}\n%{time_total}" \
  -X POST \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -H "X-Request-ID: $REQUEST_ID-embedding" \
  -d "$EMBEDDING_DATA" \
  "$BASE_URL/v1/embeddings" 2>/dev/null)

HTTP_CODE2=$(echo "$RESPONSE2" | tail -2 | head -1)
DURATION2=$(echo "$RESPONSE2" | tail -1)

echo "HTTP 状态码：$HTTP_CODE2"
echo "请求耗时：${DURATION2}s"
echo ""

# ============================================================================
# 测试 3: 检查追踪日志
# ============================================================================
echo "=== 测试 3: 检查追踪日志 ==="
echo ""

# 等待日志写入
sleep 2

echo "查找包含 TraceID 的日志..."
echo ""

# 提取 TraceID（从最新的日志中）
TRACE_LINE=$(tail -100 "$LOG_FILE" | grep -m 1 "traceId" || echo "")

if [ -n "$TRACE_LINE" ]; then
  echo "✓ 找到追踪日志"
  echo ""
  
  # 提取 TraceID
  TRACE_ID=$(echo "$TRACE_LINE" | grep -o '"traceId":"[a-f0-9]*"' | head -1 | cut -d'"' -f4)
  SPAN_ID=$(echo "$TRACE_LINE" | grep -o '"spanId":"[a-f0-9]*"' | head -1 | cut -d'"' -f4)
  
  echo "追踪 ID: $TRACE_ID"
  echo "Span ID: $SPAN_ID"
  echo ""
  
  # 显示所有包含此 TraceID 的日志
  echo "=== 完整链路日志 ==="
  tail -200 "$LOG_FILE" | grep "$TRACE_ID" | head -20
  
  echo ""
  echo "链路日志数量："
  tail -200 "$LOG_FILE" | grep -c "$TRACE_ID"
  echo ""
else
  echo "⚠️ 未找到追踪日志"
fi

# ============================================================================
# 测试 4: 检查结构化日志格式
# ============================================================================
echo "=== 测试 4: 检查结构化日志格式 ==="
echo ""

# 查找 JSON 格式的日志
JSON_LOG=$(tail -100 "$LOG_FILE" | grep -m 1 '{"serviceVersion"' || echo "")

if [ -n "$JSON_LOG" ]; then
  echo "✓ 找到结构化日志"
  echo ""
  echo "日志示例（格式化后）:"
  echo "$JSON_LOG" | python3 -m json.tool 2>/dev/null | head -30 || echo "$JSON_LOG" | head -c 500
  echo ""
else
  echo "⚠️ 未找到结构化日志"
fi

# ============================================================================
# 测试 5: 检查 Span 层级关系
# ============================================================================
echo "=== 测试 5: 检查 Span 层级关系 ==="
echo ""

echo "查找 Span 创建和完成的日志..."
echo ""

# Span 创建
tail -200 "$LOG_FILE" | grep -i "创建\|start\|begin" | head -5
echo ""

# Span 完成
tail -200 "$LOG_FILE" | grep -i "完成\|end\|finish" | head -5
echo ""

# ============================================================================
# 测试 6: 检查追踪统计
# ============================================================================
echo "=== 测试 6: 检查追踪统计 ==="
echo ""

# 从日志中统计操作数量
TOTAL_OPS=$(tail -200 "$LOG_FILE" | grep -c "记录操作性能" || echo "0")
echo "记录的操作数量：$TOTAL_OPS"
echo ""

# 显示最近的操作性能记录
echo "最近的操作性能记录:"
tail -200 "$LOG_FILE" | grep "记录操作性能" | tail -5
echo ""

# ============================================================================
# 测试 7: 检查健康状态
# ============================================================================
echo "=== 测试 7: 检查 Tracing 健康状态 ==="
echo ""

HEALTH=$(curl -s "$BASE_URL/actuator/health" | python3 -m json.tool 2>/dev/null | grep -A 30 '"tracing"' || echo "")

if [ -n "$HEALTH" ]; then
  echo "Tracing 组件健康状态:"
  echo "$HEALTH" | head -35
  echo ""
else
  echo "⚠️ 无法获取健康状态"
fi

# ============================================================================
# 总结
# ============================================================================
echo "========================================"
echo "  测试总结"
echo "========================================"
echo ""

# 检查各项指标
CHECKS_PASSED=0
CHECKS_TOTAL=5

# 检查 1: HTTP 响应
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "400" ]; then
  echo "✓ 聊天请求响应正常 (HTTP $HTTP_CODE)"
  ((CHECKS_PASSED++))
else
  echo "✗ 聊天请求异常 (HTTP $HTTP_CODE)"
fi

# 检查 2: 嵌入请求响应
if [ "$HTTP_CODE2" = "200" ] || [ "$HTTP_CODE2" = "400" ]; then
  echo "✓ 嵌入请求响应正常 (HTTP $HTTP_CODE2)"
  ((CHECKS_PASSED++))
else
  echo "✗ 嵌入请求异常 (HTTP $HTTP_CODE2)"
fi

# 检查 3: 追踪日志
if [ -n "$TRACE_ID" ]; then
  echo "✓ 追踪 ID 生成正常 ($TRACE_ID)"
  ((CHECKS_PASSED++))
else
  echo "✗ 未生成追踪 ID"
fi

# 检查 4: 结构化日志
if [ -n "$JSON_LOG" ]; then
  echo "✓ 结构化日志输出正常"
  ((CHECKS_PASSED++))
else
  echo "✗ 未输出结构化日志"
fi

# 检查 5: 操作记录
if [ "$TOTAL_OPS" -gt 0 ]; then
  echo "✓ 操作性能记录正常 ($TOTAL_OPS 个操作)"
  ((CHECKS_PASSED++))
else
  echo "✗ 未记录操作性能"
fi

echo ""
echo "测试结果：$CHECKS_PASSED/$CHECKS_TOTAL 通过"
echo ""

if [ $CHECKS_PASSED -eq $CHECKS_TOTAL ]; then
  echo "✅ 统一 Controller 请求链路追踪功能正常！"
else
  echo "⚠️ 部分功能异常，请检查日志"
fi

echo ""
echo "详细日志：$LOG_FILE"
echo ""
