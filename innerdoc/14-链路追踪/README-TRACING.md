# 完整链路追踪功能

## 🎯 功能概述

本系统实现了完整的端到端分布式链路追踪，可以追踪从HTTP请求进入到后端服务调用返回的整个调用链路。

## 📊 追踪层次

```
┌─────────────────────────────────────────────────────────────┐
│ HTTP请求 (Root Span)                                        │
│ POST /api/v1/chat/completions                              │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Controller.chatCompletions                              │ │
│ │ ┌─────────────────────────────────────────────────────┐ │ │
│ │ │ InstanceSelection                                   │ │ │
│ │ │ - 负载均衡                                           │ │ │
│ │ │ - 健康检查                                           │ │ │
│ │ │ - 熔断器检查                                         │ │ │
│ │ │ - 限流检查                                           │ │ │
│ │ └─────────────────────────────────────────────────────┘ │ │
│ │ ┌─────────────────────────────────────────────────────┐ │ │
│ │ │ Adapter.openai.chat                                 │ │ │
│ │ │ ┌─────────────────────────────────────────────────┐ │ │ │
│ │ │ │ HTTP POST api.openai.com/v1/chat/completions   │ │ │ │
│ │ │ │ - 请求构建                                       │ │ │ │
│ │ │ │ - 认证处理                                       │ │ │ │
│ │ │ │ - 后端调用                                       │ │ │ │
│ │ │ │ - 响应解析                                       │ │ │ │
│ │ │ └─────────────────────────────────────────────────┘ │ │ │
│ │ └─────────────────────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 🔍 追踪的信息

### 每个层次都会记录:

| 层次 | 记录的信息 |
|------|-----------|
| **HTTP请求层** | 请求方法、URL、客户端IP、User-Agent、请求/响应大小、状态码、总耗时 |
| **Controller层** | 方法名、服务类型、模型名称、HTTP信息、请求/响应大小、耗时、状态码 |
| **实例选择层** | 服务类型、模型名称、客户端IP、选中实例ID、实例URL、适配器类型、权重 |
| **适配器层** | 适配器名称、服务类型、实例信息、耗时、成功/失败状态 |
| **后端调用层** | HTTP方法、URL、状态码、响应大小、耗时、后端实例信息 |

## 🚀 快速开始

### 1. 启用追踪

在 `application.yml` 中配置:

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: model-router
    
    sampling:
      ratio: 1.0  # 开发环境100%采样
      
    exporter:
      type: otlp
      otlp:
        endpoint: http://localhost:4317
```

### 2. 启动Jaeger (可选)

```bash
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  jaegertracing/all-in-one:latest
```

### 3. 发送测试请求

```bash
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-key" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

### 4. 查看追踪数据

#### 方式1: 通过API
```bash
# 获取追踪统计
curl http://localhost:8080/api/tracing/actuator/stats | jq

# 搜索追踪
curl "http://localhost:8080/api/tracing/query/search?limit=10" | jq
```

#### 方式2: 通过Jaeger UI
访问 http://localhost:16686

#### 方式3: 通过日志
```bash
tail -f logs/application.log | grep "traceId" | jq
```

## 📈 追踪示例

### 成功请求的追踪

```json
{
  "traceId": "a1b2c3d4e5f6g7h8",
  "spans": [
    {
      "spanId": "root-001",
      "operationName": "POST /api/v1/chat/completions",
      "duration": 1500,
      "attributes": {
        "http.method": "POST",
        "http.status_code": 200
      }
    },
    {
      "spanId": "controller-001",
      "parentSpanId": "root-001",
      "operationName": "Controller.chatCompletions",
      "duration": 1480,
      "attributes": {
        "service.type": "chat",
        "model.name": "gpt-4"
      }
    },
    {
      "spanId": "selection-001",
      "parentSpanId": "controller-001",
      "operationName": "InstanceSelection",
      "duration": 5,
      "attributes": {
        "instance.id": "openai-instance-1",
        "instance.base_url": "https://api.openai.com"
      }
    },
    {
      "spanId": "adapter-001",
      "parentSpanId": "controller-001",
      "operationName": "Adapter.openai.chat",
      "duration": 1450,
      "attributes": {
        "adapter.name": "openai"
      }
    },
    {
      "spanId": "backend-001",
      "parentSpanId": "adapter-001",
      "operationName": "HTTP POST api.openai.com/v1/chat/completions",
      "duration": 1400,
      "attributes": {
        "http.status_code": 200,
        "backend.instance": "api.openai.com:443"
      }
    }
  ]
}
```

## 🎨 Jaeger UI 视图

在Jaeger UI中，你可以看到:

### 1. 追踪列表
![Trace List](https://via.placeholder.com/800x200/4A90E2/FFFFFF?text=Trace+List+View)

### 2. 追踪详情 - 时间线视图
```
POST /api/v1/chat/completions                    [████████████████████] 1500ms
  └─ Controller.chatCompletions                  [███████████████████ ] 1480ms
      ├─ InstanceSelection                       [█                   ]    5ms
      └─ Adapter.openai.chat                     [███████████████████ ] 1450ms
          └─ HTTP POST api.openai.com/...        [██████████████████  ] 1400ms
```

### 3. Span详情
点击任意Span可以查看:
- 所有属性 (Attributes)
- 事件 (Events)
- 日志 (Logs)
- 父子关系 (References)

## 🔧 配置选项

### 采样配置

```yaml
jairouter:
  tracing:
    sampling:
      ratio: 0.1  # 10% 采样
      adaptive:
        enabled: true
        target-rate: 0.8
      always-sample:
        - "/api/v1/chat/completions"  # 始终采样
      never-sample:
        - "/health"  # 从不采样
```

### 导出器配置

```yaml
jairouter:
  tracing:
    exporter:
      type: otlp  # 或 logging, jaeger
      otlp:
        endpoint: http://localhost:4317
      logging:
        enabled: true
```

## 📚 详细文档

- [完整追踪指南](docs/tracing-guide.md) - 详细的使用指南和配置说明
- [追踪流程图](docs/tracing-flow-diagram.md) - 可视化的追踪流程和数据流
- [使用示例](docs/tracing-examples.md) - 实际使用示例和故障排查
- [实现总结](docs/tracing-implementation-summary.md) - 技术实现细节

## 🎯 使用场景

### 1. 性能分析
```bash
# 查找慢请求
curl "http://localhost:8080/api/tracing/query/search?minDuration=2000"

# 分析瓶颈
# 在Jaeger UI中查看时间线，找出耗时最长的Span
```

### 2. 错误追踪
```bash
# 查找错误请求
curl "http://localhost:8080/api/tracing/query/search?hasError=true"

# 查看错误详情
curl "http://localhost:8080/api/tracing/query/trace/{traceId}"
```

### 3. 负载分析
```bash
# 查看实例选择情况
grep "instance_selected" logs/application.log | \
  jq -r '.data.selected_instance' | \
  sort | uniq -c | sort -rn
```

### 4. 依赖分析
在Jaeger UI的"Dependencies"视图中查看服务依赖关系图

## ⚡ 性能影响

- **CPU开销**: < 1%
- **内存开销**: < 50MB
- **延迟增加**: < 1ms
- **异步处理**: 不阻塞主流程

## 🛠️ 故障排查

### 问题: 看不到追踪数据

**解决方案**:
1. 检查追踪是否启用: `curl http://localhost:8080/api/tracing/actuator/status`
2. 检查采样率: 确保 `sampling.ratio` > 0
3. 检查导出器连接: 确保Jaeger/OTLP端点可访问

### 问题: 追踪链路不完整

**解决方案**:
1. 查看日志中的警告信息
2. 确保所有组件都正确注入了追踪拦截器
3. 检查异步操作是否正确传播了追踪上下文

### 问题: 性能下降

**解决方案**:
1. 降低采样率: `sampling.ratio: 0.1`
2. 启用自适应采样: `sampling.adaptive.enabled: true`
3. 增加缓冲区: `performance.buffer.size: 20000`

## 🌟 最佳实践

1. **生产环境**: 使用10-20%采样率或自适应采样
2. **开发环境**: 使用100%采样率便于调试
3. **关键接口**: 使用`alwaysSample`确保关键接口始终被追踪
4. **定期清理**: 配置追踪数据保留策略
5. **监控告警**: 基于追踪数据设置性能和错误告警

## ✅ 验证追踪功能

### 快速验证

```bash
# 1. 启用 DEBUG 日志
# 在 application.yml 中添加:
logging:
  level:
    org.unreal.modelrouter.tracing: DEBUG

# 2. 发送测试请求
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4","messages":[{"role":"user","content":"test"}]}'

# 3. 查看日志，应该看到:
# - "BackendCallTracingInterceptor.filter() 被调用"
# - "backend_call_start"
# - "backend_call_completed"

# 4. 查询追踪数据
curl "http://localhost:8080/api/tracing/query/search?limit=1" | jq '.traces[0].spanCount'
# 应该返回 5 或更多（包含 Backend Call Span）
```

### 完整验证

详细的验证步骤和故障排查，请参考：
- [追踪验证指南](docs/tracing-verification.md)

## 📞 支持

如有问题，请查看:
- [完整文档](docs/tracing-guide.md)
- [使用示例](docs/tracing-examples.md)
- [验证指南](docs/tracing-verification.md)
- [GitHub Issues](https://github.com/your-repo/issues)

---

**享受完整的链路追踪体验！** 🚀
