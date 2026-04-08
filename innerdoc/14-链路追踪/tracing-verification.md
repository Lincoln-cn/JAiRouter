# 追踪功能验证指南

## 验证 BackendCallTracingInterceptor 是否被调用

### 方法1: 查看日志

启用 DEBUG 日志级别来查看追踪拦截器的调用：

```yaml
# application.yml
logging:
  level:
    org.unreal.modelrouter.tracing: DEBUG
```

然后发送一个请求：

```bash
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-key" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

在日志中查找：

```
DEBUG o.u.m.t.i.BackendCallTracingInterceptor : BackendCallTracingInterceptor.filter() 被调用: url=https://api.openai.com/v1/chat/completions, hasContext=true
```

如果看到这条日志，说明 `BackendCallTracingInterceptor` 被正确调用了。

### 方法2: 运行集成测试

```bash
mvn test -Dtest=TracingIntegrationTest
```

查看测试输出：

```
✓ TracingWebClientFactory 已注入
✓ BackendCallTracingInterceptor 已注入
✓ ControllerTracingInterceptor 已注入
✓ 成功创建带追踪的 WebClient
```

### 方法3: 检查追踪数据

#### 3.1 通过API查询

```bash
# 发送请求后，查询追踪数据
curl "http://localhost:8080/api/tracing/query/search?limit=1" | jq

# 查看返回的 spans 数组，应该包含：
# 1. Root Span (HTTP请求)
# 2. Controller Span
# 3. InstanceSelection Span
# 4. Adapter Span
# 5. Backend Call Span  <-- 这个就是 BackendCallTracingInterceptor 创建的
```

期望的输出：

```json
{
  "traces": [
    {
      "traceId": "xxx",
      "spans": [
        {
          "operationName": "POST /api/v1/chat/completions",
          "spanKind": "SERVER"
        },
        {
          "operationName": "Controller.chatCompletions",
          "spanKind": "INTERNAL"
        },
        {
          "operationName": "InstanceSelection",
          "spanKind": "INTERNAL"
        },
        {
          "operationName": "Adapter.openai.chat",
          "spanKind": "INTERNAL"
        },
        {
          "operationName": "HTTP POST api.openai.com/v1/chat/completions",
          "spanKind": "CLIENT"  // <-- 这个是 BackendCallTracingInterceptor 创建的
        }
      ]
    }
  ]
}
```

#### 3.2 通过Jaeger UI查看

1. 访问 http://localhost:16686
2. 选择服务: `model-router`
3. 点击一个追踪
4. 在时间线视图中，应该看到 5 个 Span
5. 最底层的 Span 应该是 `HTTP POST api.openai.com/...`，类型为 `CLIENT`

### 方法4: 查看结构化日志

```bash
# 查看后端调用的日志
grep "backend_call" logs/application.log | jq

# 应该看到类似的输出：
{
  "timestamp": "2024-01-15T10:30:00.050Z",
  "level": "INFO",
  "event": "backend_call_start",
  "traceId": "xxx",
  "spanId": "span-backend-001",
  "data": {
    "adapter": "openai",
    "instance": "api.openai.com:443",
    "url": "https://api.openai.com/v1/chat/completions",
    "method": "POST"
  }
}

{
  "timestamp": "2024-01-15T10:30:01.450Z",
  "level": "INFO",
  "event": "backend_call_completed",
  "traceId": "xxx",
  "spanId": "span-backend-001",
  "data": {
    "adapter": "openai",
    "instance": "api.openai.com:443",
    "url": "https://api.openai.com/v1/chat/completions",
    "method": "POST",
    "duration": 1400,
    "status_code": 200,
    "success": true
  }
}
```

## 常见问题排查

### 问题1: BackendCallTracingInterceptor 未被调用

**可能原因**:
1. 追踪功能未启用
2. TracingWebClientFactory 未被正确注入
3. BaseAdapter 没有使用 TracingWebClientFactory

**解决方案**:

1. 检查配置：
```yaml
jairouter:
  tracing:
    enabled: true  # 确保为 true
```

2. 检查 TracingWebClientFactory 是否被注入：
```bash
# 运行测试
mvn test -Dtest=TracingIntegrationTest

# 或者查看启动日志
grep "TracingWebClientFactory" logs/application.log
```

3. 检查 BaseAdapter.getWebClient() 方法：
```java
// 应该看到类似的代码
org.unreal.modelrouter.tracing.client.TracingWebClientFactory tracingFactory =
    org.unreal.modelrouter.util.ApplicationContextProvider.getBean(
        org.unreal.modelrouter.tracing.client.TracingWebClientFactory.class);
return tracingFactory.createTracingWebClient(baseUrl);
```

### 问题2: 追踪上下文为空

**可能原因**:
1. TracingWebFilter 未启用
2. 追踪上下文未正确传播

**解决方案**:

1. 检查 TracingWebFilter 是否启用：
```bash
grep "TracingWebFilter" logs/application.log
```

2. 检查追踪上下文传播：
```bash
# 在日志中查找 traceId
grep "traceId" logs/application.log | head -20
```

### 问题3: 只看到部分 Span

**可能原因**:
1. 某些拦截器未被正确注入
2. 异步操作未正确传播追踪上下文

**解决方案**:

1. 检查所有追踪组件是否注入：
```bash
mvn test -Dtest=TracingIntegrationTest
```

2. 查看日志中的警告信息：
```bash
grep "WARN.*tracing" logs/application.log
```

## 验证清单

使用以下清单验证追踪功能是否完整：

- [ ] TracingWebClientFactory 已注入
- [ ] BackendCallTracingInterceptor 已注入
- [ ] ControllerTracingInterceptor 已注入
- [ ] 发送请求后可以在日志中看到 "BackendCallTracingInterceptor.filter() 被调用"
- [ ] 追踪数据包含 5 个 Span（Root, Controller, InstanceSelection, Adapter, Backend）
- [ ] Backend Call Span 的类型为 CLIENT
- [ ] Backend Call Span 包含 http.url, http.method, http.status_code 等属性
- [ ] 可以在 Jaeger UI 中看到完整的追踪链路
- [ ] 结构化日志包含 backend_call_start 和 backend_call_completed 事件

## 完整验证脚本

```bash
#!/bin/bash

echo "=== 追踪功能验证 ==="

# 1. 检查配置
echo "1. 检查追踪配置..."
if grep -q "jairouter.tracing.enabled=true" application.yml || \
   grep -q "enabled: true" application.yml; then
  echo "✓ 追踪已启用"
else
  echo "✗ 追踪未启用"
  exit 1
fi

# 2. 运行集成测试
echo "2. 运行集成测试..."
mvn test -Dtest=TracingIntegrationTest -q
if [ $? -eq 0 ]; then
  echo "✓ 集成测试通过"
else
  echo "✗ 集成测试失败"
  exit 1
fi

# 3. 发送测试请求
echo "3. 发送测试请求..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer test-key" \
  -d '{"model":"gpt-4","messages":[{"role":"user","content":"test"}]}')

if [ $? -eq 0 ]; then
  echo "✓ 请求发送成功"
else
  echo "✗ 请求发送失败"
  exit 1
fi

# 4. 等待追踪数据处理
echo "4. 等待追踪数据处理..."
sleep 2

# 5. 查询追踪数据
echo "5. 查询追踪数据..."
TRACES=$(curl -s "http://localhost:8080/api/tracing/query/search?limit=1")
SPAN_COUNT=$(echo $TRACES | jq -r '.traces[0].spanCount')

if [ "$SPAN_COUNT" -ge 5 ]; then
  echo "✓ 追踪数据完整 (包含 $SPAN_COUNT 个 Span)"
else
  echo "✗ 追踪数据不完整 (只有 $SPAN_COUNT 个 Span，期望至少 5 个)"
  echo "追踪数据: $TRACES"
  exit 1
fi

# 6. 检查 Backend Call Span
echo "6. 检查 Backend Call Span..."
BACKEND_SPAN=$(echo $TRACES | jq -r '.traces[0].spans[] | select(.operationName | contains("HTTP"))')

if [ -n "$BACKEND_SPAN" ]; then
  echo "✓ 找到 Backend Call Span"
  echo "  操作名: $(echo $BACKEND_SPAN | jq -r '.operationName')"
  echo "  类型: $(echo $BACKEND_SPAN | jq -r '.spanKind // "未知"')"
else
  echo "✗ 未找到 Backend Call Span"
  exit 1
fi

# 7. 检查日志
echo "7. 检查日志..."
if grep -q "BackendCallTracingInterceptor.filter() 被调用" logs/application.log; then
  echo "✓ BackendCallTracingInterceptor 被调用"
else
  echo "⚠ 日志中未找到 BackendCallTracingInterceptor 调用记录（可能日志级别不是 DEBUG）"
fi

echo ""
echo "=== 验证完成 ==="
echo "所有检查通过！追踪功能正常工作。"
```

保存为 `verify-tracing.sh` 并运行：

```bash
chmod +x verify-tracing.sh
./verify-tracing.sh
```

## 总结

通过以上方法，你可以全面验证 `BackendCallTracingInterceptor` 是否被正确调用。最直接的方法是：

1. **启用 DEBUG 日志** - 查看拦截器调用日志
2. **查询追踪数据** - 确认包含 Backend Call Span
3. **查看 Jaeger UI** - 可视化查看完整链路

如果所有验证都通过，说明完整的链路追踪功能正常工作！
