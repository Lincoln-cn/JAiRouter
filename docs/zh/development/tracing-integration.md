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
     * 创建根 Span
     * @param operation 操作名称
     * @return Span 对象
     */
    public Span createRootSpan(String operation);
    
    /**
     * 创建子 Span
     * @param operation 操作名称
     * @return Span 对象
     */
    public Span createChildSpan(String operation);
    
    /**
     * 在当前上下文中执行操作
     * @param operation 操作名称
     * @param function 执行函数
     * @return 执行结果
     */
    public <T> T withSpan(String operation, Function<Span, T> function);
    
    /**
     * 添加标签到当前 Span
     * @param key 标签键
     * @param value 标签值
     */
    public void addTag(String key, String value);
    
    /**
     * 记录事件到当前 Span
     * @param event 事件名称
     */
    public void addEvent(String event);
    
    /**
     * 记录事件到当前 Span（带属性）
     * @param event 事件名称
     * @param attributes 事件属性
     */
    public void addEvent(String event, Map<String, Object> attributes);
    
    /**
     * 记录异常到当前 Span
     * @param throwable 异常对象
     */
    public void recordException(Throwable throwable);
}
```

### TracingContext

追踪上下文管理器，用于获取和操作当前线程的追踪信息。

```java
public class TracingContext {
    
    /**
     * 获取当前上下文
     * @return TracingContext 对象
     */
    public static Optional<TracingContext> current();
    
    /**
     * 获取当前 Trace ID
     * @return Trace ID
     */
    public static String getCurrentTraceId();
    
    /**
     * 获取当前 Span ID
     * @return Span ID
     */
    public static String getCurrentSpanId();
    
    /**
     * 添加属性到当前上下文
     * @param key 属性键
     * @param value 属性值
     */
    public void addAttribute(String key, Object value);
    
    /**
     * 获取属性值
     * @param key 属性键
     * @return 属性值
     */
    public Object getAttribute(String key);
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

## 注解支持

### @TraceSpan 注解

用于自动创建 Span 的方法级注解。

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceSpan {
    
    /**
     * Span 名称
     */
    String name() default "";
    
    /**
     * 标签键值对
     */
    String[] tags() default {};
    
    /**
     * 是否记录异常
     */
    boolean recordException() default true;
}
```

使用示例：

```java
@Service
public class UserService {
    
    @TraceSpan(name = "user-authentication", tags = {"operation", "login"})
    public User authenticate(String username, String password) {
        // 认证逻辑
        return userRepository.findByUsername(username);
    }
    
    @TraceSpan(name = "user-profile-update")
    public void updateUserProfile(UserProfile profile) {
        // 更新用户资料
        userProfileRepository.save(profile);
    }
}
```

### @TraceAsync 注解

用于异步方法的追踪注解。

```java
@RestController
public class AsyncController {
    
    @Autowired
    private AsyncService asyncService;
    
    @TraceAsync("data-processing")
    @PostMapping("/api/process")
    public CompletableFuture<ProcessResult> processData(@RequestBody DataRequest request) {
        return asyncService.processData(request);
    }
}
```

## 响应式编程集成

### Mono/Flux 追踪支持

```java
@Component
public class ReactiveTracingService {
    
    public <T> Mono<T> traceMono(String operation, Mono<T> mono) {
        return Mono.defer(() -> {
            Span span = tracingService.createChildSpan(operation);
            return mono
                .doOnSuccess(result -> {
                    span.addTag("result", "success");
                    span.end();
                })
                .doOnError(error -> {
                    span.recordException(error);
                    span.addTag("result", "error");
                    span.end();
                });
        });
    }
    
    public <T> Flux<T> traceFlux(String operation, Flux<T> flux) {
        return Flux.defer(() -> {
            Span span = tracingService.createChildSpan(operation);
            AtomicLong count = new AtomicLong(0);
            
            return flux
                .doOnNext(item -> count.incrementAndGet())
                .doOnComplete(() -> {
                    span.addTag("item.count", String.valueOf(count.get()));
                    span.addTag("result", "success");
                    span.end();
                })
                .doOnError(error -> {
                    span.recordException(error);
                    span.addTag("result", "error");
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
        span.addTag("custom.interceptor", "pre-handle");
        
        // 添加自定义属性
        String userAgent = getCurrentUserAgent();
        if (userAgent != null) {
            span.addTag("http.user_agent", userAgent);
        }
    }
    
    @Override
    public void postHandle(TracingContext context, Span span, Object result) {
        // 响应后处理
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> response = (ResponseEntity<?>) result;
            span.addTag("http.status_code", String.valueOf(response.getStatusCodeValue()));
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
    
    @InjectMocks
    private TracingService tracingService;
    
    @Test
    void testCreateRootSpan() {
        // Given
        String operation = "test-operation";
        
        // When
        Span span = tracingService.createRootSpan(operation);
        
        // Then
        assertThat(span).isNotNull();
        assertThat(span.getName()).isEqualTo(operation);
    }
    
    @Test
    void testAddTag() {
        // Given
        Span span = tracingService.createRootSpan("test");
        
        // When
        tracingService.addTag("test.key", "test.value");
        
        // Then
        // 验证标签已添加到 Span
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
        
        // 验证追踪数据已生成
        verify(tracingService).createRootSpan(anyString());
    }
}
```

## 最佳实践

### 1. Span 命名规范

```java
// ✅ 好的命名
tracingService.createSpan("user-authentication");
tracingService.createSpan("database-query");
tracingService.createSpan("external-api-call");

// ❌ 避免的命名
tracingService.createSpan("op1");
tracingService.createSpan("doSomething");
```

### 2. 标签使用规范

```java
// ✅ 有意义的标签
span.addTag("http.method", "POST");
span.addTag("user.id", userId);
span.addTag("business.operation", "payment");

// ❌ 避免的标签
span.addTag("tag1", "value");
span.addTag("data", largeObject.toString()); // 过大的值
```

### 3. 异常处理

```java
// ✅ 正确的异常记录
try {
    performOperation();
} catch (BusinessException e) {
    span.recordException(e);
    span.addTag("error.type", "business");
    throw e;
}

// ❌ 避免记录敏感信息
try {
    performOperation();
} catch (Exception e) {
    span.addTag("error.details", e.getMessage()); // 可能包含敏感信息
    throw e;
}
```

### 4. 性能考虑

```java
// ✅ 高效的追踪
@TraceSpan("critical-operation")
public void criticalOperation() {
    // 关键业务逻辑
}

// ❌ 避免在高频路径中过度追踪
@TraceSpan("high-frequency-op") // 可能影响性能
public void highFrequencyOperation() {
    // 高频调用的方法
}
```

## 下一步

- [API 参考](../api-reference/index.md) - 详细的 API 文档
- [配置参考](../tracing/config-reference.md) - 完整的配置选项
- [性能调优](../tracing/performance-tuning.md) - 性能优化指南