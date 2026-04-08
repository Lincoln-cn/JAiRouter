# 完整链路追踪指南

## 概述

本系统实现了完整的端到端分布式链路追踪，可以追踪从请求进入到响应返回的整个调用链路。

## 追踪架构

### 追踪层次结构

```
HTTP请求 (Root Span)
  └─ Controller层 (Controller.{methodName})
      ├─ 实例选择 (InstanceSelection)
      ├─ 适配器调用 (Adapter.{adapterName}.{serviceType})
      └─ 后端调用 (HTTP {method} {host}{path})
```

### 追踪组件

1. **TracingService**: 核心追踪服务，管理追踪上下文和Span生命周期
2. **ControllerTracingInterceptor**: Controller层追踪拦截器
3. **BackendCallTracingInterceptor**: 后端调用追踪拦截器
4. **TracingContextHolder**: 追踪上下文持有者，使用ThreadLocal传播上下文

## 完整调用链路追踪

### 1. HTTP请求层 (Root Span)

当请求进入系统时，`TracingWebFilter` 会创建根Span：

```
Span类型: SERVER
操作名称: POST /api/v1/chat/completions
属性:
  - http.method: POST
  - http.url: http://localhost:8080/api/v1/chat/completions
  - http.client_ip: 192.168.1.100
  - http.user_agent: curl/7.68.0
```

### 2. Controller层追踪

`ControllerTracingInterceptor` 追踪Controller方法调用：

```
Span类型: INTERNAL
操作名称: Controller.chatCompletions
父Span: HTTP请求Span
属性:
  - controller.method: chatCompletions
  - service.type: chat
  - model.name: gpt-4
  - http.method: POST
  - http.path: /api/v1/chat/completions
  - http.request_size: 1024
```

### 3. 实例选择追踪

追踪负载均衡和实例选择过程：

```
Span类型: INTERNAL
操作名称: InstanceSelection
父Span: Controller Span
属性:
  - service.type: chat
  - model.name: gpt-4
  - client.ip: 192.168.1.100
  - instance.id: openai-instance-1
  - instance.name: gpt-4
  - instance.base_url: https://api.openai.com
  - instance.adapter: openai
  - instance.weight: 100
```

### 4. 适配器调用追踪

追踪适配器层的处理：

```
Span类型: INTERNAL
操作名称: Adapter.openai.chat
父Span: Controller Span
属性:
  - adapter.name: openai
  - adapter.service_type: chat
  - adapter.instance_id: openai-instance-1
  - adapter.instance_url: https://api.openai.com
  - duration_ms: 1250
```

### 5. 后端调用追踪

`BackendCallTracingInterceptor` 追踪实际的HTTP调用：

```
Span类型: CLIENT
操作名称: HTTP POST api.openai.com/v1/chat/completions
父Span: Adapter Span
属性:
  - http.method: POST
  - http.url: https://api.openai.com/v1/chat/completions
  - http.status_code: 200
  - backend.adapter: openai
  - backend.instance: api.openai.com:443
  - backend.host: api.openai.com
  - backend.port: 443
  - http.response_content_length: 2048
```

## 追踪数据示例

### 完整追踪链路JSON

```json
{
  "traceId": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "spans": [
    {
      "spanId": "span-root-001",
      "parentSpanId": null,
      "operationName": "POST /api/v1/chat/completions",
      "spanKind": "SERVER",
      "startTime": "2024-01-15T10:30:00.000Z",
      "endTime": "2024-01-15T10:30:01.500Z",
      "duration": 1500,
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
      "spanKind": "INTERNAL",
      "startTime": "2024-01-15T10:30:00.010Z",
      "endTime": "2024-01-15T10:30:01.490Z",
      "duration": 1480,
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
      "spanKind": "INTERNAL",
      "startTime": "2024-01-15T10:30:00.020Z",
      "endTime": "2024-01-15T10:30:00.025Z",
      "duration": 5,
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
      "spanKind": "INTERNAL",
      "startTime": "2024-01-15T10:30:00.030Z",
      "endTime": "2024-01-15T10:30:01.480Z",
      "duration": 1450,
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
      "spanKind": "CLIENT",
      "startTime": "2024-01-15T10:30:00.050Z",
      "endTime": "2024-01-15T10:30:01.450Z",
      "duration": 1400,
      "attributes": {
        "http.method": "POST",
        "http.url": "https://api.openai.com/v1/chat/completions",
        "http.status_code": 200,
        "backend.adapter": "openai",
        "backend.instance": "api.openai.com:443"
      }
    }
  ]
}
```

## 查询追踪数据

### 1. 通过API查询

```bash
# 获取追踪统计信息
curl http://localhost:8080/api/tracing/actuator/stats

# 查询特定时间范围的追踪
curl "http://localhost:8080/api/tracing/query/search?startTime=1705315800000&endTime=1705319400000"

# 查询特定traceId
curl http://localhost:8080/api/tracing/query/trace/{traceId}
```

### 2. 通过日志查看

系统会记录结构化日志，包含完整的追踪信息：

```json
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
```

## 追踪配置

### 启用追踪

在 `application.yml` 中配置：

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: model-router
    service-version: 1.0.0
    
    # 采样配置
    sampling:
      ratio: 1.0  # 100% 采样
      adaptive:
        enabled: true
        target-rate: 0.8
        
    # 导出器配置
    exporter:
      type: otlp  # 或 logging, jaeger
      otlp:
        endpoint: http://localhost:4317
        
    # 性能配置
    performance:
      async-processing: true
      thread-pool:
        core-size: 4
      buffer:
        size: 10000
```

### 运行时控制

```bash
# 启用追踪
curl -X POST http://localhost:8080/api/tracing/actuator/enable

# 禁用追踪
curl -X POST http://localhost:8080/api/tracing/actuator/disable

# 刷新采样策略
curl -X POST http://localhost:8080/api/tracing/actuator/sampling/refresh
```

## 追踪可视化

### 使用Jaeger

1. 启动Jaeger：
```bash
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  jaegertracing/all-in-one:latest
```

2. 配置导出器：
```yaml
jairouter:
  tracing:
    exporter:
      type: otlp
      otlp:
        endpoint: http://localhost:4317
```

3. 访问Jaeger UI：
```
http://localhost:16686
```

### 追踪视图示例

在Jaeger中，你可以看到：

1. **服务依赖图**: 显示服务之间的调用关系
2. **追踪时间线**: 显示每个Span的执行时间和顺序
3. **Span详情**: 显示每个Span的属性、事件和日志
4. **错误追踪**: 高亮显示失败的Span

## 性能影响

追踪系统设计为低开销：

- **异步处理**: 追踪数据异步提交，不阻塞主流程
- **采样策略**: 支持自适应采样，减少高负载时的开销
- **内存管理**: 自动清理过期的追踪数据
- **批量导出**: 批量提交追踪数据，减少网络开销

典型性能影响：
- CPU开销: < 1%
- 内存开销: < 50MB
- 延迟增加: < 1ms

## 故障排查

### 追踪数据丢失

1. 检查追踪是否启用：
```bash
curl http://localhost:8080/api/tracing/actuator/status
```

2. 检查采样率：
```yaml
jairouter:
  tracing:
    sampling:
      ratio: 1.0  # 确保采样率足够高
```

3. 检查导出器连接：
```bash
# 查看日志中的导出错误
tail -f logs/application.log | grep "exporter"
```

### 追踪链路不完整

1. 确保所有组件都注入了追踪拦截器
2. 检查异步操作是否正确传播了追踪上下文
3. 查看日志中的警告信息

### 性能问题

1. 降低采样率：
```yaml
jairouter:
  tracing:
    sampling:
      ratio: 0.1  # 10% 采样
```

2. 启用自适应采样：
```yaml
jairouter:
  tracing:
    sampling:
      adaptive:
        enabled: true
        target-rate: 0.5
```

3. 增加缓冲区大小：
```yaml
jairouter:
  tracing:
    performance:
      buffer:
        size: 20000
```

## 最佳实践

1. **生产环境采样**: 使用自适应采样或固定低采样率（10-20%）
2. **开发环境全采样**: 设置采样率为100%以便调试
3. **关键路径追踪**: 使用 `alwaysSample` 配置确保关键接口始终被追踪
4. **敏感信息脱敏**: 追踪系统自动脱敏Authorization等敏感头信息
5. **定期清理**: 配置追踪数据保留策略，避免存储爆满

## 扩展追踪

### 添加自定义Span

```java
@Autowired
private TracingService tracingService;

public void customOperation() {
    TracingContext context = tracingService.createOperationSpan(
        "CustomOperation", 
        SpanKind.INTERNAL
    );
    
    try {
        // 执行业务逻辑
        
        // 添加自定义属性
        context.getCurrentSpan().setAttribute("custom.key", "value");
        
        // 添加事件
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("step", "processing");
        context.addEvent("custom_event", eventData);
        
    } finally {
        context.finishSpan(context.getCurrentSpan());
    }
}
```

### 添加业务标签

```java
TracingContext context = TracingContextHolder.getCurrentContext();
if (context != null && context.isActive()) {
    context.getCurrentSpan().setAttribute("business.user_id", userId);
    context.getCurrentSpan().setAttribute("business.order_id", orderId);
}
```

## 总结

完整的链路追踪系统提供了：

✅ **端到端可见性**: 从HTTP请求到后端调用的完整链路
✅ **多层次追踪**: Controller → 实例选择 → 适配器 → 后端调用
✅ **详细属性**: 每个Span包含丰富的上下文信息
✅ **性能监控**: 记录每个环节的耗时
✅ **错误追踪**: 自动捕获和记录错误信息
✅ **灵活配置**: 支持运行时调整采样率和导出器
✅ **低开销**: 异步处理，对业务影响最小

通过这套追踪系统，你可以：
- 快速定位性能瓶颈
- 分析服务依赖关系
- 追踪错误传播路径
- 优化系统架构
- 监控SLA指标
