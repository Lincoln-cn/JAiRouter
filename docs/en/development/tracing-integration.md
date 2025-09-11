# Developer Integration Guide

This document provides integration guides and API references for the JAiRouter distributed tracing system for developers.

## Core APIs

### TracingService

The core interface of the tracing service, providing Span management and tracing context operations.

#### Main Methods

```java
@Component
public class TracingService {
    
    /**
     * Create a root Span
     * @param operation Operation name
     * @return Span object
     */
    public Span createRootSpan(String operation);
    
    /**
     * Create a child Span
     * @param operation Operation name
     * @return Span object
     */
    public Span createChildSpan(String operation);
    
    /**
     * Execute operation in current context
     * @param operation Operation name
     * @param function Execution function
     * @return Execution result
     */
    public <T> T withSpan(String operation, Function<Span, T> function);
    
    /**
     * Add tag to current Span
     * @param key Tag key
     * @param value Tag value
     */
    public void addTag(String key, String value);
    
    /**
     * Record event to current Span
     * @param event Event name
     */
    public void addEvent(String event);
    
    /**
     * Record event to current Span (with attributes)
     * @param event Event name
     * @param attributes Event attributes
     */
    public void addEvent(String event, Map<String, Object> attributes);
    
    /**
     * Record exception to current Span
     * @param throwable Exception object
     */
    public void recordException(Throwable throwable);
}
```

### TracingContext

Tracing context manager for getting and manipulating tracing information of the current thread.

```java
public class TracingContext {
    
    /**
     * Get current context
     * @return TracingContext object
     */
    public static Optional<TracingContext> current();
    
    /**
     * Get current Trace ID
     * @return Trace ID
     */
    public static String getCurrentTraceId();
    
    /**
     * Get current Span ID
     * @return Span ID
     */
    public static String getCurrentSpanId();
    
    /**
     * Add attribute to current context
     * @param key Attribute key
     * @param value Attribute value
     */
    public void addAttribute(String key, Object value);
    
    /**
     * Get attribute value
     * @param key Attribute key
     * @return Attribute value
     */
    public Object getAttribute(String key);
}
```

## Custom Tracing Components

### Implementing Custom Sampling Strategy

```java
@Component
public class CustomSamplingStrategy implements SamplingStrategy {
    
    @Override
    public boolean shouldSample(SamplingContext context) {
        // Custom sampling logic
        String userId = context.getAttribute("userId");
        String operation = context.getOperation();
        
        // 100% sampling for VIP users
        if (isVipUser(userId)) {
            return true;
        }
        
        // 50% sampling for critical operations
        if (isCriticalOperation(operation)) {
            return Math.random() < 0.5;
        }
        
        // Default 10% sampling
        return Math.random() < 0.1;
    }
    
    @Override
    public String getName() {
        return "custom";
    }
}
```

### Creating Custom Exporter

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
            // Custom export logic
            List<CustomSpan> customSpans = spans.stream()
                .map(this::convertToCustomSpan)
                .collect(Collectors.toList());
            
            // Send to custom backend
            HttpResponse response = httpClient.post("/traces")
                .body(customSpans)
                .execute();
            
            return response.isSuccess() ? 
                CompletableResultCode.ofSuccess() : 
                CompletableResultCode.ofFailure();
        } catch (Exception e) {
            log.error("Failed to export tracing data", e);
            return CompletableResultCode.ofFailure();
        }
    }
    
    @Override
    public CompletableResultCode flush() {
        // Flush logic
        return CompletableResultCode.ofSuccess();
    }
    
    @Override
    public CompletableResultCode shutdown() {
        // Shutdown logic
        return CompletableResultCode.ofSuccess();
    }
    
    private CustomSpan convertToCustomSpan(SpanData spanData) {
        // Conversion logic
        return new CustomSpan();
    }
}
```

## Annotation Support

### @TraceSpan Annotation

Method-level annotation for automatically creating Spans.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceSpan {
    
    /**
     * Span name
     */
    String name() default "";
    
    /**
     * Tag key-value pairs
     */
    String[] tags() default {};
    
    /**
     * Whether to record exceptions
     */
    boolean recordException() default true;
}
```

Usage example:

```java
@Service
public class UserService {
    
    @TraceSpan(name = "user-authentication", tags = {"operation", "login"})
    public User authenticate(String username, String password) {
        // Authentication logic
        return userRepository.findByUsername(username);
    }
    
    @TraceSpan(name = "user-profile-update")
    public void updateUserProfile(UserProfile profile) {
        // Update user profile
        userProfileRepository.save(profile);
    }
}
```

### @TraceAsync Annotation

Tracing annotation for asynchronous methods.

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

## Reactive Programming Integration

### Mono/Flux Tracing Support

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

Usage example:

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

## Extensions and Plugins

### Creating Custom Tracing Interceptor

```java
@Component
public class CustomTracingInterceptor implements TracingInterceptor {
    
    @Override
    public void preHandle(TracingContext context, Span span) {
        // Pre-handle processing
        span.addTag("custom.interceptor", "pre-handle");
        
        // Add custom attributes
        String userAgent = getCurrentUserAgent();
        if (userAgent != null) {
            span.addTag("http.user_agent", userAgent);
        }
    }
    
    @Override
    public void postHandle(TracingContext context, Span span, Object result) {
        // Post-handle processing
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> response = (ResponseEntity<?>) result;
            span.addTag("http.status_code", String.valueOf(response.getStatusCodeValue()));
        }
    }
    
    @Override
    public void afterCompletion(TracingContext context, Span span, Exception ex) {
        // After completion processing
        if (ex != null) {
            span.recordException(ex);
        }
        span.end();
    }
}
```

### Custom Metrics Collector

```java
@Component
public class CustomMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Counter customCounter;
    private final Timer customTimer;
    
    public CustomMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.customCounter = Counter.builder("custom.tracing.operations")
            .description("Custom tracing operation count")
            .register(meterRegistry);
        this.customTimer = Timer.builder("custom.tracing.duration")
            .description("Custom tracing operation duration")
            .register(meterRegistry);
    }
    
    public <T> T measureOperation(String operation, Supplier<T> supplier) {
        customCounter.increment(1.0, "operation", operation);
        
        return Timer.Sample.start(meterRegistry)
            .stop(customTimer.tag("operation", operation));
    }
}
```

## Test Support

### Unit Testing

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
        // Verify tag was added to Span
    }
}
```

### Integration Testing

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
        
        // Verify tracing data was generated
        verify(tracingService).createRootSpan(anyString());
    }
}
```

## Best Practices

### 1. Span Naming Conventions

```java
// ✅ Good naming
tracingService.createSpan("user-authentication");
tracingService.createSpan("database-query");
tracingService.createSpan("external-api-call");

// ❌ Avoid naming
tracingService.createSpan("op1");
tracingService.createSpan("doSomething");
```

### 2. Tag Usage Conventions

```java
// ✅ Meaningful tags
span.addTag("http.method", "POST");
span.addTag("user.id", userId);
span.addTag("business.operation", "payment");

// ❌ Avoid tags
span.addTag("tag1", "value");
span.addTag("data", largeObject.toString()); // Too large value
```

### 3. Exception Handling

```java
// ✅ Proper exception recording
try {
    performOperation();
} catch (BusinessException e) {
    span.recordException(e);
    span.addTag("error.type", "business");
    throw e;
}

// ❌ Avoid recording sensitive information
try {
    performOperation();
} catch (Exception e) {
    span.addTag("error.details", e.getMessage()); // May contain sensitive information
    throw e;
}
```

### 4. Performance Considerations

```java
// ✅ Efficient tracing
@TraceSpan("critical-operation")
public void criticalOperation() {
    // Critical business logic
}

// ❌ Avoid over-tracing in high-frequency paths
@TraceSpan("high-frequency-op") // May impact performance
public void highFrequencyOperation() {
    // High-frequency method calls
}
```

## Next Steps

- [API Reference](../api-reference/index.md) - Detailed API documentation
- [Configuration Reference](../tracing/config-reference.md) - Complete configuration options
- [Performance Tuning](../tracing/performance-tuning.md) - Performance optimization guide