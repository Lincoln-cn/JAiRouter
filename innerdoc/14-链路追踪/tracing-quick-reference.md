# 链路追踪快速参考

## 🎯 一分钟快速开始

```bash
# 1. 启动Jaeger
docker run -d --name jaeger -p 16686:16686 -p 4317:4317 jaegertracing/all-in-one:latest

# 2. 配置追踪 (application.yml)
jairouter.tracing.enabled=true
jairouter.tracing.exporter.type=otlp
jairouter.tracing.exporter.otlp.endpoint=http://localhost:4317

# 3. 发送请求
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4","messages":[{"role":"user","content":"test"}]}'

# 4. 查看追踪
open http://localhost:16686
```

## 📊 追踪层次速查

| 层次 | Span类型 | 操作名称示例 | 关键属性 |
|------|---------|-------------|---------|
| HTTP请求 | SERVER | `POST /api/v1/chat/completions` | http.method, http.url, http.status_code |
| Controller | INTERNAL | `Controller.chatCompletions` | controller.method, service.type, model.name |
| 实例选择 | INTERNAL | `InstanceSelection` | instance.id, instance.base_url, instance.adapter |
| 适配器 | INTERNAL | `Adapter.openai.chat` | adapter.name, adapter.service_type |
| 后端调用 | CLIENT | `HTTP POST api.openai.com/...` | http.url, http.status_code, backend.instance |

## 🔍 常用查询命令

```bash
# 获取追踪状态
curl http://localhost:8080/api/tracing/actuator/status | jq

# 获取追踪统计
curl http://localhost:8080/api/tracing/actuator/stats | jq

# 搜索最近的追踪
curl "http://localhost:8080/api/tracing/query/search?limit=10" | jq

# 查询特定trace
curl "http://localhost:8080/api/tracing/query/trace/{traceId}" | jq

# 查询慢请求 (>2秒)
curl "http://localhost:8080/api/tracing/query/search?minDuration=2000" | jq

# 查询错误请求
curl "http://localhost:8080/api/tracing/query/search?hasError=true" | jq

# 查询统计信息
START=$(date -d '1 hour ago' +%s)000
END=$(date +%s)000
curl "http://localhost:8080/api/tracing/query/statistics?startTime=${START}&endTime=${END}" | jq
```

## ⚙️ 常用配置

### 开发环境 (100%采样)
```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      ratio: 1.0
    exporter:
      type: logging
      logging:
        enabled: true
```

### 生产环境 (自适应采样)
```yaml
jairouter:
  tracing:
    enabled: true
    sampling:
      ratio: 0.1
      adaptive:
        enabled: true
        target-rate: 0.8
    exporter:
      type: otlp
      otlp:
        endpoint: http://jaeger:4317
```

### 高性能配置
```yaml
jairouter:
  tracing:
    performance:
      async-processing: true
      buffer:
        size: 20000
      batch:
        size: 500
        timeout: 5000
```

## 🎛️ 运行时控制

```bash
# 启用追踪
curl -X POST http://localhost:8080/api/tracing/actuator/enable

# 禁用追踪
curl -X POST http://localhost:8080/api/tracing/actuator/disable

# 刷新采样策略
curl -X POST http://localhost:8080/api/tracing/actuator/sampling/refresh

# 清理缓存
curl -X POST http://localhost:8080/api/tracing/actuator/clear-cache

# 更新配置
curl -X PUT http://localhost:8080/api/tracing/actuator/config \
  -H "Content-Type: application/json" \
  -d '{"sampling":{"ratio":0.5}}'
```

## 📝 日志查询

```bash
# 查看所有追踪日志
tail -f logs/application.log | grep "traceId" | jq

# 查看特定事件
grep "controller_call_start" logs/application.log | jq
grep "instance_selected" logs/application.log | jq
grep "backend_call_completed" logs/application.log | jq

# 查看错误
grep "error" logs/application.log | grep "traceId" | jq

# 统计实例选择
grep "instance_selected" logs/application.log | \
  jq -r '.data.selected_instance' | \
  sort | uniq -c | sort -rn
```

## 🐛 故障排查速查

| 问题 | 检查命令 | 解决方案 |
|------|---------|---------|
| 看不到追踪 | `curl .../actuator/status` | 检查enabled和采样率 |
| 链路不完整 | 查看日志警告 | 检查组件注入 |
| 性能下降 | `curl .../actuator/stats` | 降低采样率 |
| 内存占用高 | 查看memory.heap_usage_ratio | 增加缓冲区或降低采样 |
| 导出失败 | 查看日志中的exporter错误 | 检查端点连接 |

## 📊 Jaeger UI 快捷键

| 操作 | 快捷键 |
|------|--------|
| 搜索 | `/` |
| 展开所有Span | `Shift + →` |
| 折叠所有Span | `Shift + ←` |
| 下一个追踪 | `n` |
| 上一个追踪 | `p` |

## 🎨 Jaeger 查询技巧

### 基础查询
```
Service: model-router
Operation: POST /api/v1/chat/completions
Lookback: Last Hour
```

### 高级查询
```
Tags:
  model.name=gpt-4
  http.status_code=200
Min Duration: 1000ms
Max Duration: 5000ms
Limit: 20
```

### 查询语法
```
# 精确匹配
model.name="gpt-4"

# 范围查询
duration>1000ms

# 组合查询
model.name="gpt-4" AND http.status_code=200

# 错误查询
error=true
```

## 🔢 性能指标

| 指标 | 正常值 | 告警阈值 |
|------|--------|---------|
| CPU开销 | < 1% | > 5% |
| 内存开销 | < 50MB | > 200MB |
| 延迟增加 | < 1ms | > 10ms |
| 队列大小 | < 100 | > 1000 |
| 丢弃率 | < 0.1% | > 1% |
| 成功率 | > 99% | < 95% |

## 📈 监控告警示例

```yaml
# Prometheus告警规则
groups:
  - name: tracing
    rules:
      - alert: HighTracingDropRate
        expr: tracing_dropped_count / tracing_processed_count > 0.01
        for: 5m
        annotations:
          summary: "追踪丢弃率过高"
          
      - alert: HighTracingLatency
        expr: histogram_quantile(0.95, tracing_duration_seconds) > 0.01
        for: 5m
        annotations:
          summary: "追踪延迟过高"
```

## 🎯 常见场景速查

### 场景1: 查找慢请求
```bash
# 1. 查询慢请求
curl "http://localhost:8080/api/tracing/query/search?minDuration=2000&limit=10" | jq

# 2. 分析具体trace
TRACE_ID=$(curl -s "..." | jq -r '.traces[0].traceId')
curl "http://localhost:8080/api/tracing/query/trace/${TRACE_ID}" | jq

# 3. 查看各Span耗时
curl "..." | jq '.spans[] | {name: .operationName, duration: .duration}' | sort -k2 -rn
```

### 场景2: 分析错误
```bash
# 1. 查询错误
curl "http://localhost:8080/api/tracing/query/search?hasError=true&limit=20" | jq

# 2. 统计错误类型
curl "..." | jq '.traces[].errorMessage' | sort | uniq -c | sort -rn

# 3. 查看错误详情
curl "http://localhost:8080/api/tracing/query/trace/{errorTraceId}" | jq
```

### 场景3: 负载分析
```bash
# 统计各实例调用次数
grep "instance_selected" logs/application.log | \
  jq -r '.data.selected_instance' | \
  sort | uniq -c | sort -rn

# 统计各模型调用次数
grep "controller_call_start" logs/application.log | \
  jq -r '.data.model_name' | \
  sort | uniq -c | sort -rn
```

## 🔗 相关链接

- [完整文档](./tracing-guide.md)
- [流程图](./tracing-flow-diagram.md)
- [使用示例](./tracing-examples.md)
- [实现总结](./tracing-implementation-summary.md)

---

**提示**: 将此页面加入书签，随时查阅！ 📌
