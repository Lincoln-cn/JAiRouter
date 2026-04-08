# 链路追踪使用示例

## 1. 基础使用示例

### 发起一个追踪请求

```bash
# 发送聊天请求
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "model": "gpt-4",
    "messages": [
      {"role": "user", "content": "Hello, how are you?"}
    ]
  }'
```

### 查看追踪统计

```bash
# 获取追踪系统状态
curl http://localhost:8080/api/tracing/actuator/stats | jq

# 输出示例:
{
  "processing": {
    "processed_count": 1250,
    "dropped_count": 5,
    "queue_size": 12,
    "success_rate": 0.996,
    "is_running": true
  },
  "memory": {
    "heap_usage_ratio": 0.45,
    "cache_size": 1024,
    "cache_hit_ratio": 0.85,
    "pressure_level": "LOW"
  },
  "configInfo": {
    "enabled": true,
    "serviceName": "model-router",
    "exporterType": "otlp",
    "globalSamplingRatio": 1.0
  }
}
```

## 2. 查询特定追踪

### 通过TraceId查询

```bash
# 查询特定的trace
TRACE_ID="a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
curl "http://localhost:8080/api/tracing/query/trace/${TRACE_ID}" | jq

# 输出示例:
{
  "traceId": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "serviceName": "model-router",
  "spans": [
    {
      "spanId": "span-root-001",
      "parentSpanId": null,
      "operationName": "POST /api/v1/chat/completions",
      "startTime": "2024-01-15T10:30:00.000Z",
      "endTime": "2024-01-15T10:30:01.500Z",
      "duration": 1500,
      "hasError": false,
      "statusCode": "200",
      "attributes": {
        "http.method": "POST",
        "http.url": "http://localhost:8080/api/v1/chat/completions",
        "http.status_code": 200,
        "http.client_ip": "192.168.1.100"
      }
    },
    {
      "spanId": "span-controller-001",
      "parentSpanId": "span-root-001",
      "operationName": "Controller.chatCompletions",
      "startTime": "2024-01-15T10:30:00.010Z",
      "endTime": "2024-01-15T10:30:01.490Z",
      "duration": 1480,
      "hasError": false,
      "statusCode": "200",
      "attributes": {
        "controller.method": "chatCompletions",
        "service.type": "chat",
        "model.name": "gpt-4"
      }
    },
    {
      "spanId": "span-selection-001",
      "parentSpanId": "span-controller-001",
      "operationName": "InstanceSelection",
      "startTime": "2024-01-15T10:30:00.020Z",
      "endTime": "2024-01-15T10:30:00.025Z",
      "duration": 5,
      "hasError": false,
      "attributes": {
        "service.type": "chat",
        "model.name": "gpt-4",
        "instance.id": "openai-instance-1",
        "instance.base_url": "https://api.openai.com"
      }
    },
    {
      "spanId": "span-adapter-001",
      "parentSpanId": "span-controller-001",
      "operationName": "Adapter.openai.chat",
      "startTime": "2024-01-15T10:30:00.030Z",
      "endTime": "2024-01-15T10:30:01.480Z",
      "duration": 1450,
      "hasError": false,
      "attributes": {
        "adapter.name": "openai",
        "adapter.service_type": "chat",
        "adapter.instance_id": "openai-instance-1"
      }
    },
    {
      "spanId": "span-backend-001",
      "parentSpanId": "span-adapter-001",
      "operationName": "HTTP POST api.openai.com/v1/chat/completions",
      "startTime": "2024-01-15T10:30:00.050Z",
      "endTime": "2024-01-15T10:30:01.450Z",
      "duration": 1400,
      "hasError": false,
      "statusCode": "200",
      "attributes": {
        "http.method": "POST",
        "http.url": "https://api.openai.com/v1/chat/completions",
        "http.status_code": 200,
        "backend.adapter": "openai",
        "backend.instance": "api.openai.com:443"
      }
    }
  ],
  "totalDuration": 1500,
  "spanCount": 5
}
```

### 按时间范围查询

```bash
# 查询最近1小时的追踪
START_TIME=$(date -d '1 hour ago' +%s)000
END_TIME=$(date +%s)000

curl "http://localhost:8080/api/tracing/query/search?startTime=${START_TIME}&endTime=${END_TIME}&limit=10" | jq

# 输出示例:
{
  "traces": [
    {
      "traceId": "trace-001",
      "serviceName": "model-router",
      "operationName": "POST /api/v1/chat/completions",
      "startTime": "2024-01-15T10:30:00.000Z",
      "duration": 1500,
      "spanCount": 5,
      "hasError": false
    },
    {
      "traceId": "trace-002",
      "serviceName": "model-router",
      "operationName": "POST /api/v1/embeddings",
      "startTime": "2024-01-15T10:31:00.000Z",
      "duration": 850,
      "spanCount": 5,
      "hasError": false
    }
  ],
  "total": 125,
  "page": 1,
  "pageSize": 10
}
```

### 查询错误追踪

```bash
# 只查询有错误的追踪
curl "http://localhost:8080/api/tracing/query/search?hasError=true&limit=10" | jq

# 输出示例:
{
  "traces": [
    {
      "traceId": "trace-error-001",
      "serviceName": "model-router",
      "operationName": "POST /api/v1/chat/completions",
      "startTime": "2024-01-15T10:35:00.000Z",
      "duration": 5000,
      "spanCount": 5,
      "hasError": true,
      "errorMessage": "Backend service timeout"
    }
  ]
}
```

## 3. 性能分析示例

### 查看慢请求

```bash
# 查询耗时超过2秒的请求
curl "http://localhost:8080/api/tracing/query/search?minDuration=2000&limit=10" | jq

# 分析结果
{
  "traces": [
    {
      "traceId": "slow-trace-001",
      "duration": 3500,
      "spans": [
        {
          "operationName": "Backend Call",
          "duration": 3200,  # 瓶颈在后端调用
          "attributes": {
            "backend.instance": "slow-instance-1"
          }
        }
      ]
    }
  ]
}
```

### 统计各服务耗时

```bash
# 获取追踪统计信息
curl "http://localhost:8080/api/tracing/query/statistics?startTime=${START_TIME}&endTime=${END_TIME}" | jq

# 输出示例:
{
  "totalTraces": 1250,
  "successTraces": 1245,
  "errorTraces": 5,
  "avgDuration": 1250,
  "p50Duration": 1100,
  "p95Duration": 2500,
  "p99Duration": 4000,
  "operationStats": {
    "POST /api/v1/chat/completions": {
      "count": 800,
      "avgDuration": 1500,
      "errorRate": 0.005
    },
    "POST /api/v1/embeddings": {
      "count": 450,
      "avgDuration": 800,
      "errorRate": 0.002
    }
  }
}
```

## 4. 实时监控示例

### 监控追踪流量

```bash
# 持续监控追踪统计
watch -n 5 'curl -s http://localhost:8080/api/tracing/actuator/stats | jq ".processing"'

# 输出:
Every 5.0s: curl -s http://localhost:8080/api/tracing/actuator/stats | jq ".processing"

{
  "processed_count": 1250,
  "dropped_count": 5,
  "queue_size": 12,
  "success_rate": 0.996,
  "is_running": true
}
```

### 监控错误率

```bash
# 监控最近5分钟的错误率
while true; do
  START=$(date -d '5 minutes ago' +%s)000
  END=$(date +%s)000
  
  curl -s "http://localhost:8080/api/tracing/query/statistics?startTime=${START}&endTime=${END}" | \
    jq '{total: .totalTraces, errors: .errorTraces, error_rate: (.errorTraces / .totalTraces * 100)}'
  
  sleep 30
done

# 输出:
{
  "total": 125,
  "errors": 2,
  "error_rate": 1.6
}
```

## 5. 日志查看示例

### 查看结构化追踪日志

```bash
# 查看最近的追踪日志
tail -f logs/application.log | grep "traceId" | jq

# 输出示例:
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "event": "controller_call_start",
  "traceId": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "spanId": "span-controller-001",
  "data": {
    "controller_method": "chatCompletions",
    "service_type": "chat",
    "model_name": "gpt-4",
    "http_method": "POST",
    "http_path": "/api/v1/chat/completions"
  }
}

{
  "timestamp": "2024-01-15T10:30:00.020Z",
  "level": "INFO",
  "event": "instance_selected",
  "traceId": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "spanId": "span-selection-001",
  "data": {
    "service_type": "chat",
    "model_name": "gpt-4",
    "selected_instance": "openai-instance-1",
    "instance_url": "https://api.openai.com",
    "adapter": "openai"
  }
}

{
  "timestamp": "2024-01-15T10:30:01.450Z",
  "level": "INFO",
  "event": "backend_call_completed",
  "traceId": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
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

### 过滤特定模型的追踪

```bash
# 只查看gpt-4模型的追踪
tail -f logs/application.log | grep "traceId" | grep "gpt-4" | jq

# 或使用grep过滤
grep "model_name.*gpt-4" logs/application.log | jq
```

## 6. Jaeger UI 使用示例

### 启动Jaeger

```bash
# 使用Docker启动Jaeger
docker run -d --name jaeger \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  jaegertracing/all-in-one:latest

# 访问Jaeger UI
open http://localhost:16686
```

### 在Jaeger中查询

1. **选择服务**: 在Service下拉框中选择 `model-router`
2. **选择操作**: 选择具体的操作，如 `POST /api/v1/chat/completions`
3. **设置时间范围**: 选择查询的时间范围
4. **点击查找**: 查看追踪列表

### Jaeger查询示例

```
Service: model-router
Operation: POST /api/v1/chat/completions
Tags: 
  - model.name=gpt-4
  - http.status_code=200
Min Duration: 1000ms
Max Duration: 5000ms
Limit: 20
```

### 查看追踪详情

在Jaeger UI中点击某个追踪，可以看到：

1. **时间线视图**: 显示所有Span的执行顺序和耗时
2. **Span详情**: 点击任意Span查看详细属性
3. **依赖关系**: 查看服务间的调用关系
4. **错误高亮**: 错误的Span会用红色标记

## 7. 配置管理示例

### 动态调整采样率

```bash
# 降低采样率到10%
curl -X PUT http://localhost:8080/api/tracing/actuator/config \
  -H "Content-Type: application/json" \
  -d '{
    "sampling": {
      "ratio": 0.1
    }
  }'

# 响应:
{
  "message": "追踪配置更新成功",
  "timestamp": "2024-01-15T10:40:00.000Z",
  "updatedFields": ["sampling", "logging", "performance"]
}
```

### 启用自适应采样

```bash
# 启用自适应采样
curl -X PUT http://localhost:8080/api/tracing/actuator/config \
  -H "Content-Type: application/json" \
  -d '{
    "sampling": {
      "adaptive": {
        "enabled": true,
        "targetRate": 0.8,
        "minRatio": 0.1,
        "maxRatio": 1.0
      }
    }
  }'
```

### 临时禁用追踪

```bash
# 禁用追踪（紧急情况下使用）
curl -X POST http://localhost:8080/api/tracing/actuator/disable

# 响应:
{
  "message": "追踪系统已禁用",
  "status": "disabled",
  "timestamp": "2024-01-15T10:45:00.000Z"
}

# 重新启用
curl -X POST http://localhost:8080/api/tracing/actuator/enable
```

## 8. 故障排查示例

### 场景1: 请求超时

```bash
# 1. 查询慢请求
curl "http://localhost:8080/api/tracing/query/search?minDuration=5000&limit=10" | jq

# 2. 分析具体追踪
TRACE_ID="slow-trace-001"
curl "http://localhost:8080/api/tracing/query/trace/${TRACE_ID}" | jq

# 3. 查看各Span耗时
curl "http://localhost:8080/api/tracing/query/trace/${TRACE_ID}" | \
  jq '.spans[] | {name: .operationName, duration: .duration}' | \
  sort -k2 -rn

# 输出:
{
  "name": "HTTP POST api.openai.com/v1/chat/completions",
  "duration": 4800  # 瓶颈在这里
}
{
  "name": "Adapter.openai.chat",
  "duration": 4850
}
{
  "name": "Controller.chatCompletions",
  "duration": 4900
}
```

### 场景2: 高错误率

```bash
# 1. 查询错误追踪
curl "http://localhost:8080/api/tracing/query/search?hasError=true&limit=20" | jq

# 2. 统计错误类型
curl "http://localhost:8080/api/tracing/query/search?hasError=true&limit=100" | \
  jq '.traces[].errorMessage' | sort | uniq -c | sort -rn

# 输出:
  45 "Backend service timeout"
  12 "Connection refused"
   8 "Rate limit exceeded"
   3 "Authentication failed"

# 3. 查看特定错误的详细信息
curl "http://localhost:8080/api/tracing/query/trace/error-trace-001" | \
  jq '.spans[] | select(.hasError == true)'
```

### 场景3: 实例负载不均

```bash
# 1. 统计各实例的调用次数
grep "instance_selected" logs/application.log | \
  jq -r '.data.selected_instance' | \
  sort | uniq -c | sort -rn

# 输出:
 450 openai-instance-1
 320 openai-instance-2
  80 openai-instance-3  # 这个实例调用较少

# 2. 检查实例健康状态
curl http://localhost:8080/api/health/instances | jq

# 3. 查看负载均衡配置
curl http://localhost:8080/api/config/load-balance | jq
```

## 9. 性能优化示例

### 清理追踪缓存

```bash
# 手动触发缓存清理
curl -X POST http://localhost:8080/api/tracing/actuator/clear-cache

# 响应:
{
  "message": "追踪缓存清理已触发",
  "timestamp": "2024-01-15T11:00:00.000Z"
}
```

### 查看内存使用

```bash
# 查看追踪系统内存使用
curl http://localhost:8080/api/tracing/actuator/stats | jq '.memory'

# 输出:
{
  "heap_usage_ratio": 0.45,
  "cache_size": 1024,
  "cache_hit_ratio": 0.85,
  "pressure_level": "LOW"
}
```

### 调整性能参数

```bash
# 增加缓冲区大小
curl -X PUT http://localhost:8080/api/tracing/actuator/config \
  -H "Content-Type: application/json" \
  -d '{
    "performance": {
      "buffer": {
        "size": 20000
      },
      "batch": {
        "size": 500,
        "timeout": 5000
      }
    }
  }'
```

## 10. 集成测试示例

### 完整的追踪测试脚本

```bash
#!/bin/bash

# 测试追踪功能
echo "=== 追踪功能测试 ==="

# 1. 检查追踪状态
echo "1. 检查追踪状态..."
STATUS=$(curl -s http://localhost:8080/api/tracing/actuator/status | jq -r '.enabled')
if [ "$STATUS" = "true" ]; then
  echo "✓ 追踪已启用"
else
  echo "✗ 追踪未启用"
  exit 1
fi

# 2. 发送测试请求
echo "2. 发送测试请求..."
RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer test-key" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "test"}]
  }')

# 3. 等待追踪数据处理
echo "3. 等待追踪数据处理..."
sleep 2

# 4. 查询追踪统计
echo "4. 查询追踪统计..."
STATS=$(curl -s http://localhost:8080/api/tracing/actuator/stats)
PROCESSED=$(echo $STATS | jq -r '.processing.processed_count')
echo "✓ 已处理追踪数: $PROCESSED"

# 5. 查询最近的追踪
echo "5. 查询最近的追踪..."
START_TIME=$(date -d '1 minute ago' +%s)000
END_TIME=$(date +%s)000
TRACES=$(curl -s "http://localhost:8080/api/tracing/query/search?startTime=${START_TIME}&endTime=${END_TIME}&limit=1")
TRACE_COUNT=$(echo $TRACES | jq -r '.traces | length')

if [ "$TRACE_COUNT" -gt 0 ]; then
  echo "✓ 找到追踪记录"
  TRACE_ID=$(echo $TRACES | jq -r '.traces[0].traceId')
  echo "  TraceId: $TRACE_ID"
  
  # 6. 查询追踪详情
  echo "6. 查询追踪详情..."
  TRACE_DETAIL=$(curl -s "http://localhost:8080/api/tracing/query/trace/${TRACE_ID}")
  SPAN_COUNT=$(echo $TRACE_DETAIL | jq -r '.spanCount')
  echo "✓ Span数量: $SPAN_COUNT"
  
  # 验证Span层次
  echo "7. 验证Span层次..."
  echo $TRACE_DETAIL | jq -r '.spans[] | "  - \(.operationName) (\(.duration)ms)"'
else
  echo "✗ 未找到追踪记录"
  exit 1
fi

echo ""
echo "=== 测试完成 ==="
```

这些示例展示了如何使用完整的链路追踪系统来监控、分析和优化你的应用。
