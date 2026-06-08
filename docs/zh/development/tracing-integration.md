# 开发者集成指南

本文档为开发者提供 JAiRouter 分布式追踪系统的集成指南和 API 参考。

## 核心 API

### TracingService

追踪服务的核心接口，提供 Span 管理和追踪上下文操作。

#### 主要方法

```java
@Component
public class TracingService {

    /**
     * 为 HTTP 请求创建根追踪上下文
     * @param exchange Web 交换对象
     * @return 包含追踪上下文的 Mono
     */
    public Mono<TracingContext> createRootSpan(ServerWebExchange exchange);

    /**
     * 创建业务操作的追踪上下文
     * @param operationName 操作名称
     * @param kind Span 类型
     * @return 追踪上下文
     */
    public TracingContext createOperationSpan(String operationName, SpanKind kind);

    /**
     * 完成 HTTP 请求的追踪
     * @param exchange Web 交换对象
     * @param context 追踪上下文
     * @param duration 请求处理时长（毫秒）
     */
    public void finishHttpSpan(ServerWebExchange exchange, TracingContext context, long duration);

    /**
     * 记录错误到追踪上下文
     * @param context 追踪上下文
     * @param error 错误信息
     */
    public void recordError(TracingContext context, Throwable error);

    /**
     * 获取性能统计信息
     * @return 性能统计 Mono
     */
    public Mono<Map<String, Object>> getPerformanceStats();

    /**
     * 触发性能优化
     * @return 完成信号 Mono
     */
    public Mono<Void> triggerPerformanceOptimization();
}
```

### TracingContext

追踪上下文接口，提供分布式追踪的核心功能。

```java
public interface TracingContext {

    // ========================================
    // Span管理
    // ========================================

    /**
     * 创建新的 Span
     * @param operationName 操作名称
     * @param kind Span 类型
     * @return 创建的 Span
     */
    Span createSpan(String operationName, SpanKind kind);

    /**
     * 创建子 Span
     * @param operationName 操作名称
     * @param kind Span 类型
     * @param parentSpan 父 Span
     * @return 创建的子 Span
     */
    Span createChildSpan(String operationName, SpanKind kind, Span parentSpan);

    /**
     * 获取当前活跃的 Span
     * @return 当前 Span，如果没有则返回 null
     */
    Span getCurrentSpan();

    /**
     * 设置当前活跃的 Span
     * @param span 要设置的 Span
     */
    void setCurrentSpan(Span span);

    /**
     * 完成 Span
     * @param span 要完成的 Span
     */
    void finishSpan(Span span);

    /**
     * 完成 Span 并记录错误
     * @param span 要完成的 Span
     * @param error 错误信息
     */
    void finishSpan(Span span, Throwable error);

    // ========================================
    // 属性管理
    // ========================================

    /**
     * 设置字符串属性
     * @param key 属性键
     * @param value 属性值
     */
    void setTag(String key, String value);

    /**
     * 设置数值属性
     * @param key 属性键
     * @param value 属性值
     */
    void setTag(String key, Number value);

    /**
     * 设置布尔属性
     * @param key 属性键
     * @param value 属性值
     */
    void setTag(String key, Boolean value);

    /**
     * 添加事件
     * @param name 事件名称
     */
    void addEvent(String name);

    /**
     * 添加事件（带属性）
     * @param name 事件名称
     * @param attributes 事件属性
     */
    void addEvent(String name, Map<String, Object> attributes);

    // ========================================
    // 上下文传播
    // ========================================

    /**
     * 将追踪上下文注入到头部信息中
     * @param headers 要注入的头部信息
     */
    void injectContext(Map<String, String> headers);

    /**
     * 从头部信息中提取追踪上下文
     * @param headers 包含追踪信息的头部
     * @return 提取的追踪上下文
     */
    TracingContext extractContext(Map<String, String> headers);

    /**
     * 复制当前追踪上下文
     * @return 复制的追踪上下文
     */
    TracingContext copy();

    // ========================================
    // 日志关联
    // ========================================

    /**
     * 获取追踪 ID
     * @return 追踪 ID，如果没有则返回空字符串
     */
    String getTraceId();

    /**
     * 获取当前 Span ID
     * @return Span ID，如果没有则返回空字符串
     */
    String getSpanId();

    /**
     * 获取日志上下文信息
     * @return 包含 traceId 和 spanId 的 Map
     */
    Map<String, String> getLogContext();

    // ========================================
    // 状态管理
    // ========================================

    /**
     * 检查是否有活跃的追踪
     * @return 如果有活跃追踪返回 true
     */
    boolean isActive();

    /**
     * 检查是否启用了采样
     * @return 如果启用采样返回 true
     */
    boolean isSampled();

    /**
     * 清理追踪上下文
     */
    void clear();
}
```

### TracingContextHolder

追踪上下文持有者，提供线程本地的追踪上下文存储。

```java
public class TracingContextHolder {

    /**
     * 获取当前线程的追踪上下文
     * @return 追踪上下文，如果没有则返回 null
     */
    public static TracingContext getCurrentContext();

    /**
     * 设置当前线程的追踪上下文
     * @param context 要设置的追踪上下文
     */
    public static void setCurrentContext(TracingContext context);

    /**
     * 清理当前线程的追踪上下文
     *
     * 重要：必须在请求处理完成后调用，防止内存泄漏
     */
    public static void clearCurrentContext();

    /**
     * 检查当前线程是否有追踪上下文
     * @return 如果有追踪上下文返回 true
     */
    public static boolean hasCurrentContext();

    /**
     * 获取当前追踪 ID
     * @return 追踪 ID，如果没有追踪上下文则返回空字符串
     */
    public static String getCurrentTraceId();

    /**
     * 获取当前 Span ID
     * @return Span ID，如果没有追踪上下文则返回空字符串
     */
    public static String getCurrentSpanId();

    /**
     * 执行带有追踪上下文的操作
     * @param context 追踪上下文
     * @param operation 要执行的操作
     * @return 操作结果
     */
    public static <T> T executeWithContext(TracingContext context, ContextualOperation<T> operation) throws Exception;

    /**
     * 执行带有追踪上下文的操作（无返回值）
     * @param context 追踪上下文
     * @param operation 要执行的操作
     */
    public static void executeWithContext(TracingContext context, ContextualVoidOperation operation) throws Exception;
}
```

## 自定义追踪组件

### 实现自定义采样策略

```java
@Component
public class CustomSamplingStrategy implements SamplingStrategy {

    @Override
    public boolean shouldSample(SamplingContext context) {
        // 自定义采样逻辑
        String userId = context.getAttribute("userId");
        String operation = context.getOperation();

        // VIP 用户 100% 采样
        if (isVipUser(userId)) {
            return true;
        }

        // 关键操作 50% 采样
        if (isCriticalOperation(operation)) {
            return Math.random() < 0.5;
        }

        // 默认 10% 采样
        return Math.random() < 0.1;
    }

    @Override
    public String getName() {
        return "custom";
    }
}
```

### 创建自定义导出器

```java
@Component
public class CustomExporter implements SpanExporter {

    private final HttpClient httpClient;

    public CustomExporter(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        try {
            // 自定义导出逻辑
            List<CustomSpan> customSpans = spans.stream()
                .map(this::convertToCustomSpan)
                .collect(Collectors.toList());

            // 发送到自定义后端
            HttpResponse response = httpClient.post("/traces")
                .body(customSpans)
                .execute();

            return response.isSuccess() ?
                CompletableResultCode.ofSuccess() :
                CompletableResultCode.ofFailure();
        } catch (Exception e) {
            log.error("导出追踪数据失败", e);
            return CompletableResultCode.ofFailure();
        }
    }

    @Override
    public CompletableResultCode flush() {
        // 刷新逻辑
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        // 关闭逻辑
        return CompletableResultCode.ofSuccess();
    }

    private CustomSpan convertToCustomSpan(SpanData spanData) {
        // 转换逻辑
        return new CustomSpan();
    }
}
```

## 响应式编程集成

### Mono/Flux 追踪支持

使用 OpenTelemetry Span 的正确方式：

```java
@Component
public class ReactiveTracingService {

    private final Tracer tracer;

    public <T> Mono<T> traceMono(String operation, Mono<T> mono) {
        return Mono.defer(() -> {
            // 从当前上下文获取或创建 Span
            TracingContext context = TracingContextHolder.getCurrentContext();
            Span span;
            if (context != null && context.isActive()) {
                span = context.createChildSpan(operation, SpanKind.INTERNAL, context.getCurrentSpan());
            } else {
                span = tracer.spanBuilder(operation).startSpan();
            }

            return mono
                .doOnSuccess(result -> {
                    span.setAttribute("result", "success");
                    span.end();
                })
                .doOnError(error -> {
                    span.recordException(error);
                    span.setAttribute("result", "error");
                    span.end();
                });
        });
    }

    public <T> Flux<T> traceFlux(String operation, Flux<T> flux) {
        return Flux.defer(() -> {
            // 从当前上下文获取或创建 Span
            TracingContext context = TracingContextHolder.getCurrentContext();
            Span span;
            if (context != null && context.isActive()) {
                span = context.createChildSpan(operation, SpanKind.INTERNAL, context.getCurrentSpan());
            } else {
                span = tracer.spanBuilder(operation).startSpan();
            }

            AtomicLong count = new AtomicLong(0);

            return flux
                .doOnNext(item -> count.incrementAndGet())
                .doOnComplete(() -> {
                    span.setAttribute("item.count", count.get());
                    span.setAttribute("result", "success");
                    span.end();
                })
                .doOnError(error -> {
                    span.recordException(error);
                    span.setAttribute("result", "error");
                    span.end();
                });
        });
    }
}
```

使用示例：

```java
@Service
public class DataService {

    @Autowired
    private ReactiveTracingService reactiveTracingService;

    public Mono<DataResult> fetchData(String id) {
        return reactiveTracingService.traceMono("fetch-data",
            dataRepository.findById(id)
                .map(this::transformData));
    }

    public Flux<DataItem> streamData() {
        return reactiveTracingService.traceFlux("stream-data",
            dataRepository.findAll()
                .filter(this::isValidItem));
    }
}
```

## 扩展和插件

### 创建自定义追踪拦截器

```java
@Component
public class CustomTracingInterceptor implements TracingInterceptor {

    @Override
    public void preHandle(TracingContext context, Span span) {
        // 请求前处理
        span.setAttribute("custom.interceptor", "pre-handle");

        // 添加自定义属性
        String userAgent = getCurrentUserAgent();
        if (userAgent != null) {
            span.setAttribute("http.user_agent", userAgent);
        }
    }

    @Override
    public void postHandle(TracingContext context, Span span, Object result) {
        // 响应后处理
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> response = (ResponseEntity<?>) result;
            span.setAttribute("http.status_code", response.getStatusCodeValue());
        }
    }

    @Override
    public void afterCompletion(TracingContext context, Span span, Exception ex) {
        // 完成后处理
        if (ex != null) {
            span.recordException(ex);
        }
        span.end();
    }
}
```

### 自定义指标收集器

```java
@Component
public class CustomMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final Counter customCounter;
    private final Timer customTimer;

    public CustomMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.customCounter = Counter.builder("custom.tracing.operations")
            .description("自定义追踪操作计数")
            .register(meterRegistry);
        this.customTimer = Timer.builder("custom.tracing.duration")
            .description("自定义追踪操作耗时")
            .register(meterRegistry);
    }

    public <T> T measureOperation(String operation, Supplier<T> supplier) {
        customCounter.increment(1.0, "operation", operation);

        return Timer.Sample.start(meterRegistry)
            .stop(customTimer.tag("operation", operation));
    }
}
```

## 测试支持

### 单元测试

```java
@ExtendWith(MockitoExtension.class)
class TracingServiceTest {

    @Mock
    private SpanExporter spanExporter;

    @Mock
    private Tracer tracer;

    @InjectMocks
    private TracingService tracingService;

    @Test
    void testCreateRootSpan() {
        // Given
        String operation = "test-operation";
        ServerWebExchange exchange = mock(ServerWebExchange.class);

        // When
        StepVerifier.create(tracingService.createRootSpan(exchange))
            .assertNext(context -> {
                assertThat(context).isNotNull();
                assertThat(context.isActive()).isTrue();
            })
            .verifyComplete();
    }

    @Test
    void testContextAttributeSetting() {
        // Given
        TracingContext context = new DefaultTracingContext(tracer);
        Span span = context.createSpan("test", SpanKind.INTERNAL);

        // When
        context.setTag("test.key", "test.value");

        // Then
        // 验证属性已设置到 Span
        assertThat(context.isActive()).isTrue();
    }
}
```

### 集成测试

```java
@SpringBootTest
@AutoConfigureTestDatabase
class TracingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TracingService tracingService;

    @Test
    void testTracingIntegration() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Debug", "true");

        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/test", HttpMethod.POST, entity, String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 验证追踪上下文已清理
        assertThat(TracingContextHolder.getCurrentContext()).isNull();
    }
}
```

## 最佳实践

### 1. Span 命名规范

```java
// ✅ 好的命名
TracingContext context = TracingContextHolder.getCurrentContext();
if (context != null) {
    context.createSpan("user-authentication", SpanKind.INTERNAL);
    context.createSpan("database-query", SpanKind.CLIENT);
    context.createSpan("external-api-call", SpanKind.CLIENT);
}

// ❌ 避免的命名
context.createSpan("op1", SpanKind.INTERNAL);
context.createSpan("doSomething", SpanKind.INTERNAL);
```

### 2. 属性使用规范

```java
// ✅ 有意义的属性
Span span = context.getCurrentSpan();
span.setAttribute("http.method", "POST");
span.setAttribute("user.id", userId);
span.setAttribute("business.operation", "payment");

// ❌ 避免的属性
span.setAttribute("tag1", "value");
span.setAttribute("data", largeObject.toString()); // 过大的值
```

### 3. 异常处理

```java
// ✅ 正确的异常记录
Span span = context.getCurrentSpan();
try {
    performOperation();
} catch (BusinessException e) {
    span.recordException(e);
    span.setAttribute("error.type", "business");
    throw e;
}

// ❌ 避免记录敏感信息
try {
    performOperation();
} catch (Exception e) {
    span.setAttribute("error.details", e.getMessage()); // 可能包含敏感信息
    throw e;
}
```

### 4. 上下文获取规范

```java
// ✅ 正确的上下文获取
TracingContext context = TracingContextHolder.getCurrentContext();
if (context != null && context.isActive()) {
    Span span = context.getCurrentSpan();
    span.setAttribute("operation", "value");
}

// ❌ 避免直接使用不存在的静态方法
// TracingContext.current() // 此方法不存在，使用 TracingContextHolder.getCurrentContext()
```

### 5. 性能考虑

```java
// ✅ 高效的追踪 - 只在关键路径添加追踪
public void criticalOperation() {
    TracingContext context = TracingContextHolder.getCurrentContext();
    if (context != null && context.isActive()) {
        Span span = context.createChildSpan("critical-operation", SpanKind.INTERNAL, context.getCurrentSpan());
        try {
            // 关键业务逻辑
        } finally {
            span.end();
        }
    }
}

// ❌ 避免在高频路径中过度追踪
public void highFrequencyOperation() {
    // 高频调用的方法，避免每次都创建 Span
    // 可以使用采样或批量处理
}
```

### 6. 清理上下文

```java
// ✅ 确保上下文被清理
try {
    TracingContextHolder.setCurrentContext(context);
    // 业务逻辑
} finally {
    TracingContextHolder.clearCurrentContext();
}

// ❌ 忘记清理会导致内存泄漏
TracingContextHolder.setCurrentContext(context);
// 业务逻辑后忘记清理
```

## 下一步

- [API 参考](../api-reference/index.md) - 详细的 API 文档
- [配置参考](../tracing/config-reference.md) - 完整的配置选项
- [性能调优](../tracing/performance-tuning.md) - 性能优化指南
