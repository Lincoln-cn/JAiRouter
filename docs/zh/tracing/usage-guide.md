# 使用指南

本指南详细介绍如何在各种场景下有效使用 JAiRouter 的分布式追踪功能。

## 基本使用

### 查看追踪数据

#### 1. 日志追踪信息

启用追踪后，所有请求都会在日志中包含追踪信息：

```bash
# 查看包含追踪信息的日志
tail -f logs/application.log | grep traceId

# 根据 traceId 查找特定请求的所有日志
grep "4bf92f3577b34da6a3ce929d0e0e4736" logs/application.log
```

#### 2. 结构化日志查询

```bash
# 使用 jq 解析 JSON 格式的追踪日志
tail -f logs/application.log | jq 'select(.traceId != null)'

# 查询特定服务的追踪数据
tail -f logs/application.log | jq 'select(.service == "jairouter" and .traceId != null)'
```

#### 3. Actuator 端点查询

```bash
# 查看追踪健康状态
curl http://localhost:8080/actuator/health/tracing

# 查看追踪配置信息
curl http://localhost:8080/actuator/info | jq '.tracing'

# 查看追踪指标
curl http://localhost:8080/actuator/metrics | grep tracing
```

### 自定义追踪标签

#### 1. 在业务代码中添加标签

```java
@RestController
public class CustomController {
    
    @Autowired
    private TracingService tracingService;
    
    @PostMapping("/api/custom")
    public ResponseEntity<?> customEndpoint(@RequestBody CustomRequest request) {
        // 添加业务标签
        tracingService.addTag("user.id", request.getUserId());
        tracingService.addTag("business.type", request.getType());
        tracingService.addTag("custom.operation", "data-processing");
        
        // 记录业务事件
        tracingService.addEvent("business.started", 
            Map.of("input.size", request.getData().size()));
        
        // 业务逻辑处理
        CustomResponse response = processRequest(request);
        
        // 记录处理结果
        tracingService.addTag("result.status", response.getStatus());
        tracingService.addEvent("business.completed",
            Map.of("output.size", response.getData().size()));
        
        return ResponseEntity.ok(response);
    }
}
```

#### 2. 使用注解添加追踪

```java
@Component
public class BusinessService {
    
    @TraceAsync("user-data-processing")
    public Mono<ProcessResult> processUserData(UserData data) {
        return Mono.fromCallable(() -> {
            // 业务逻辑
            return new ProcessResult();
        });
    }
    
    @TraceSpan(name = "database-query", tags = {"operation", "query"})
    public List<Entity> queryDatabase(String condition) {
        // 数据库查询逻辑
        return entityRepository.findByCondition(condition);
    }
}
```

## 高级场景

### 分布式服务链追踪

#### 1. 服务间调用追踪

```java
@Service
public class ExternalServiceClient {
    
    @Autowired
    private WebClient webClient;
    
    @Autowired
    private TracingService tracingService;
    
    public Mono<ExternalResponse> callExternalService(ExternalRequest request) {
        // 创建子 Span 用于外部服务调用
        return tracingService.withChildSpan("external-service-call", span -> {
            // 添加服务信息
            span.addTag("external.service", "ai-model-service");
            span.addTag("external.endpoint", "/v1/chat/completions");
            span.addTag("request.model", request.getModel());
            
            return webClient.post()
                .uri("/v1/chat/completions")
                .body(BodyInserters.fromValue(request))
                .retrieve()
                .bodyToMono(ExternalResponse.class)
                .doOnSuccess(response -> {
                    span.addTag("response.status", "success");
                    span.addTag("response.tokens", response.getUsage().getTotalTokens());
                })
                .doOnError(error -> {
                    span.addTag("error.type", error.getClass().getSimpleName());
                    span.addTag("error.message", error.getMessage());
                    span.recordException(error);
                });
        });
    }
}
```

#### 2. 响应式流中的上下文传播

```java
@Component
public class ReactiveProcessor {
    
    public Mono<ProcessedData> processDataPipeline(InputData input) {
        return Mono.just(input)
            // 第一阶段：验证
            .flatMap(this::validateInput)
            .contextWrite(ctx -> 
                TracingContext.currentContext()
                    .map(tracing -> ctx.put("tracing", tracing))
                    .orElse(ctx))
            
            // 第二阶段：转换
            .flatMap(this::transformData)
            .contextWrite(ctx -> {
                // 在每个阶段更新追踪信息
                TracingContext.current()
                    .ifPresent(tracing -> 
                        tracing.addEvent("pipeline.stage.transform"));
                return ctx;
            })
            
            // 第三阶段：存储
            .flatMap(this::saveData)
            .doOnSuccess(result -> 
                TracingContext.current()
                    .ifPresent(tracing -> 
                        tracing.addTag("pipeline.result", "success")));
    }
}
```

### 慢查询检测

#### 1. 自动慢查询检测

系统会自动检测超过阈值的请求：

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        slow-request-threshold: 3000  # 3秒阈值
        slow-request-sample-rate: 0.8 # 慢请求80%采样
```

#### 2. 手动慢查询分析

```java
@Component
public class SlowQueryAnalyzer {
    
    @Autowired
    private TracingService tracingService;
    
    public void analyzeSlowOperation() {
        long startTime = System.currentTimeMillis();
        
        try {
            // 执行可能较慢的操作
            performComplexOperation();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            if (duration > 5000) { // 5秒阈值
                // 标记为慢查询
                tracingService.addTag("performance.slow", "true");
                tracingService.addTag("performance.duration", String.valueOf(duration));
                tracingService.addEvent("slow.query.detected", 
                    Map.of("threshold", "5000ms", "actual", duration + "ms"));
                
                // 记录详细的性能信息
                recordPerformanceDetails();
            }
        }
    }
}
```

### 错误追踪和分析

#### 1. 异常自动追踪

```java
@Component
public class ErrorHandlingService {
    
    public Mono<Result> processWithErrorHandling(Request request) {
        return Mono.fromCallable(() -> processRequest(request))
            .onErrorResume(BusinessException.class, ex -> {
                // 业务异常处理
                TracingContext.current().ifPresent(tracing -> {
                    tracing.addTag("error.type", "business");
                    tracing.addTag("error.code", ex.getErrorCode());
                    tracing.recordException(ex);
                });
                return Mono.just(createErrorResult(ex));
            })
            .onErrorResume(Exception.class, ex -> {
                // 系统异常处理
                TracingContext.current().ifPresent(tracing -> {
                    tracing.addTag("error.type", "system");
                    tracing.addTag("error.severity", "high");
                    tracing.recordException(ex);
                });
                return Mono.error(new SystemException("系统处理失败", ex));
            });
    }
}
```

#### 2. 自定义错误追踪

```java
@Component
public class CustomErrorTracker {
    
    @Autowired
    private TracingService tracingService;
    
    public void trackCustomError(String operation, Throwable error, Map<String, Object> context) {
        tracingService.withSpan("error-analysis", span -> {
            // 记录错误基本信息
            span.addTag("error.operation", operation);
            span.addTag("error.class", error.getClass().getSimpleName());
            span.addTag("error.message", error.getMessage());
            
            // 记录上下文信息
            context.forEach((key, value) -> 
                span.addTag("context." + key, String.valueOf(value)));
            
            // 记录堆栈跟踪（脱敏处理）
            String sanitizedStackTrace = sanitizeStackTrace(error);
            span.addEvent("error.stacktrace", 
                Map.of("trace", sanitizedStackTrace));
            
            // 分析错误严重程度
            String severity = analyzeSeverity(error);
            span.addTag("error.severity", severity);
            
            return null;
        });
    }
    
    private String sanitizeStackTrace(Throwable error) {
        // 实现堆栈跟踪脱敏逻辑
        return error.getStackTrace()[0].toString();
    }
}
```

## 性能监控

### 实时性能指标

#### 1. 查看关键指标

```bash
# 查看追踪相关的 Prometheus 指标
curl -s http://localhost:8080/actuator/prometheus | grep jairouter_tracing

# 查看采样率指标
curl -s http://localhost:8080/actuator/metrics/jairouter.tracing.sampling.rate

# 查看 Span 创建和导出统计
curl -s http://localhost:8080/actuator/metrics/jairouter.tracing.spans.created
curl -s http://localhost:8080/actuator/metrics/jairouter.tracing.spans.exported
```

#### 2. 性能监控 API

```java
@RestController
@RequestMapping("/api/tracing/performance")
public class TracingPerformanceController {
    
    @Autowired
    private TracingPerformanceMonitor performanceMonitor;
    
    @GetMapping("/stats")
    public ResponseEntity<PerformanceStats> getPerformanceStats() {
        PerformanceStats stats = performanceMonitor.getStats();
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/slow-requests")
    public ResponseEntity<List<SlowRequest>> getSlowRequests(
            @RequestParam(defaultValue = "5000") long thresholdMs) {
        List<SlowRequest> slowRequests = performanceMonitor
            .getSlowRequests(thresholdMs);
        return ResponseEntity.ok(slowRequests);
    }
}
```

### 内存使用监控

#### 1. 监控追踪内存使用

```java
@Component
public class TracingMemoryMonitor {
    
    @Scheduled(fixedRate = 30000) // 每30秒检查一次
    public void monitorMemoryUsage() {
        MemoryUsage memoryUsage = tracingMemoryManager.getMemoryUsage();
        
        if (memoryUsage.getUsedRatio() > 0.8) {
            // 内存使用率超过80%，触发清理
            tracingMemoryManager.triggerCleanup();
            
            // 记录内存压力事件
            TracingContext.current().ifPresent(tracing -> {
                tracing.addEvent("memory.pressure.detected",
                    Map.of("usedRatio", memoryUsage.getUsedRatio(),
                           "totalSpans", memoryUsage.getTotalSpans()));
            });
        }
    }
}
```

#### 2. 内存使用优化

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 50000              # 根据实际内存调整
      cleanup-interval: 30s         # 更频繁的清理
      span-ttl: 180s               # 较短的 TTL
      memory-threshold: 0.7        # 较低的内存阈值
```

## 安全和隐私

### 敏感数据脱敏

#### 1. 自动敏感数据检测

```java
@Component
public class TracingSanitizer {
    
    public void sanitizeSpanAttributes(Span span, Map<String, Object> attributes) {
        attributes.forEach((key, value) -> {
            if (isSensitiveAttribute(key)) {
                // 脱敏处理
                String sanitizedValue = sanitizeValue(String.valueOf(value));
                span.addTag(key + ".sanitized", sanitizedValue);
            } else {
                span.addTag(key, String.valueOf(value));
            }
        });
    }
    
    private boolean isSensitiveAttribute(String key) {
        // 检查是否为敏感属性
        return key.toLowerCase().contains("password") ||
               key.toLowerCase().contains("token") ||
               key.toLowerCase().contains("secret") ||
               key.toLowerCase().contains("api-key");
    }
    
    private String sanitizeValue(String value) {
        if (value.length() <= 4) {
            return "***";
        }
        // 保留前2位和后2位，中间用*代替
        return value.substring(0, 2) + 
               "*".repeat(value.length() - 4) + 
               value.substring(value.length() - 2);
    }
}
```

#### 2. 配置敏感数据过滤规则

```yaml
jairouter:
  tracing:
    security:
      enabled: true
      sensitive-headers:
        - "Authorization"
        - "Cookie"
        - "X-API-Key"
        - "X-Auth-Token"
      sensitive-params:
        - "password"
        - "token"
        - "secret"
        - "api_key"
        - "access_token"
      mask-pattern: "***"
```

### 访问控制

#### 1. 基于角色的追踪数据访问

```java
@Component
public class TracingSecurityManager {
    
    @PreAuthorize("hasRole('ADMIN')")
    public List<TraceData> getAllTraces() {
        return tracingQueryService.findAllTraces();
    }
    
    @PreAuthorize("hasRole('USER')")
    public List<TraceData> getUserTraces(String userId) {
        return tracingQueryService.findTracesByUser(userId);
    }
    
    @PreAuthorize("hasRole('VIEWER')")
    public TraceData getFilteredTrace(String traceId) {
        TraceData trace = tracingQueryService.findById(traceId);
        return filterSensitiveData(trace);
    }
}
```

## 故障排除

### 常见问题诊断

#### 1. 追踪数据缺失

**诊断步骤：**

```bash
# 1. 检查追踪是否启用
curl http://localhost:8080/actuator/health/tracing

# 2. 检查采样配置
curl http://localhost:8080/actuator/configprops | jq '.jairouter.tracing.sampling'

# 3. 检查导出器状态
curl http://localhost:8080/actuator/metrics/jairouter.tracing.export.errors
```

**解决方案：**

```yaml
# 临时提高采样率进行调试
jairouter:
  tracing:
    sampling:
      strategy: "ratio"
      ratio: 1.0  # 100% 采样
```

#### 2. 性能影响过大

**诊断指标：**

```bash
# 查看追踪处理延迟
curl http://localhost:8080/actuator/metrics/jairouter.tracing.processing.duration

# 查看内存使用情况
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

**优化建议：**

```yaml
jairouter:
  tracing:
    # 降低采样率
    sampling:
      ratio: 0.1
    
    # 启用异步处理
    async:
      enabled: true
      core-pool-size: 4
    
    # 优化内存配置
    memory:
      max-spans: 5000
      cleanup-interval: 15s
```

#### 3. 上下文传播问题

**检查响应式流上下文：**

```java
@Component
public class ContextDiagnostic {
    
    public Mono<String> diagnoseContext() {
        return Mono.deferContextual(ctx -> {
            boolean hasTracing = ctx.hasKey("tracing");
            String traceId = TracingContext.getCurrentTraceId();
            
            return Mono.just(String.format(
                "Context有追踪: %s, 当前TraceId: %s", 
                hasTracing, traceId));
        });
    }
}
```

## 最佳实践

### 1. 采样策略选择

- **开发环境**：使用 100% 采样率便于调试
- **测试环境**：使用规则采样，重点关注关键接口
- **生产环境**：使用自适应采样，平衡性能和可观测性

### 2. 标签使用规范

```java
// ✅ 好的标签命名
span.addTag("http.method", "POST");
span.addTag("user.id", userId);
span.addTag("business.operation", "payment");

// ❌ 避免的标签命名
span.addTag("tag1", "value");  // 不明确的命名
span.addTag("user_data", largeObject.toString());  // 过大的值
```

### 3. 错误处理

```java
// ✅ 正确的错误记录
span.addTag("error", "true");
span.addTag("error.type", "validation");
span.recordException(exception);

// ❌ 避免记录敏感错误信息
span.addTag("error.details", exception.getMessage());  // 可能包含敏感信息
```

### 4. 性能考虑

- 避免在高频调用路径中添加过多标签
- 使用异步导出避免影响请求性能
- 定期清理过期的追踪数据
- 监控追踪系统自身的资源使用

## 下一步

- [性能调优](performance-tuning.md) - 深入的性能优化指南
- [故障排除](troubleshooting.md) - 详细的故障排除手册
- [开发集成](../development/tracing-integration.md) - 开发者集成指南
- [运维指南](operations-guide.md) - 生产环境运维最佳实践