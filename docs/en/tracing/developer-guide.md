# Developer Integration Guide

This document provides integration guides and API references for the JAiRouter distributed tracing system for developers.

## Core APIs

### TracingService

The core service of the tracing system, providing Span management and tracing context operations.

#### Main Methods

```java
@Service
public class TracingService {

    /**
     * Create a root tracing context for HTTP requests
     * @param exchange Web exchange object
     * @return Mono containing tracing context
     */
    public Mono<TracingContext> createRootSpan(ServerWebExchange exchange);

    /**
     * Create a tracing context for business operations
     * @param operationName Operation name
     * @param kind Span type
     * @return Tracing context
     */
    public TracingContext createOperationSpan(String operationName, SpanKind kind);

    /**
     * Finish HTTP request tracing
     * @param exchange Web exchange object
     * @param context Tracing context
     * @param duration Request processing duration (milliseconds)
     */
    public void finishHttpSpan(ServerWebExchange exchange, TracingContext context, long duration);

    /**
     * Record error to tracing context
     * @param context Tracing context
     * @param error Error information
     */
    public void recordError(TracingContext context, Throwable error);

    /**
     * Get performance statistics
     * @return Mono containing performance stats
     */
    public Mono<Map<String, Object>> getPerformanceStats();

    /**
     * Trigger performance optimization
     * @return Mono<Void>
     */
    public Mono<Void> triggerPerformanceOptimization();
}
```

### TracingContext

Tracing context interface for managing Span lifecycle and context propagation.

```java
public interface TracingContext {

    // ========================================
    // Span Management
    // ========================================

    /**
     * Create a new Span
     * @param operationName Operation name
     * @param kind Span type
     * @return Created Span
     */
    Span createSpan(String operationName, SpanKind kind);

    /**
     * Create a child Span
     * @param operationName Operation name
     * @param kind Span type
     * @param parentSpan Parent Span
     * @return Created child Span
     */
    Span createChildSpan(String operationName, SpanKind kind, Span parentSpan);

    /**
     * Get current active Span
     * @return Current Span, or null if none
     */
    Span getCurrentSpan();

    /**
     * Set current active Span
     * @param span Span to set
     */
    void setCurrentSpan(Span span);

    /**
     * Finish Span
     * @param span Span to finish
     */
    void finishSpan(Span span);

    /**
     * Finish Span and record error
     * @param span Span to finish
     * @param error Error information
     */
    void finishSpan(Span span, Throwable error);

    // ========================================
    // Context Propagation
    // ========================================

    /**
     * Inject tracing context into headers
     * @param headers Headers to inject into
     */
    void injectContext(Map<String, String> headers);

    /**
     * Extract tracing context from headers
     * @param headers Headers containing tracing information
     * @return Extracted tracing context
     */
    TracingContext extractContext(Map<String, String> headers);

    /**
     * Copy current tracing context
     * @return Copied tracing context
     */
    TracingContext copy();

    // ========================================
    // Attribute Management
    // ========================================

    /**
     * Set string attribute
     * @param key Attribute key
     * @param value Attribute value
     */
    void setTag(String key, String value);

    /**
     * Set numeric attribute
     * @param key Attribute key
     * @param value Attribute value
     */
    void setTag(String key, Number value);

    /**
     * Set boolean attribute
     * @param key Attribute key
     * @param value Attribute value
     */
    void setTag(String key, Boolean value);

    /**
     * Add event
     * @param name Event name
     * @param attributes Event attributes
     */
    void addEvent(String name, Map<String, Object> attributes);

    /**
     * Add simple event
     * @param name Event name
     */
    void addEvent(String name);

    // ========================================
    // Log Correlation
    // ========================================

    /**
     * Get trace ID
     * @return Trace ID, or empty string if none
     */
    String getTraceId();

    /**
     * Get current Span ID
     * @return Span ID, or empty string if none
     */
    String getSpanId();

    /**
     * Get log context information
     * @return Map containing traceId and spanId
     */
    Map<String, String> getLogContext();

    // ========================================
    // State Management
    // ========================================

    /**
     * Check if there is an active trace
     * @return True if active trace exists
     */
    boolean isActive();

    /**
     * Check if sampling is enabled
     * @return True if sampling is enabled
     */
    boolean isSampled();

    /**
     * Clear tracing context
     */
    void clear();
}
```

### TracingContextHolder

Thread-local holder for tracing context, providing context storage and retrieval.

```java
public class TracingContextHolder {

    /**
     * Get current thread's tracing context
     * @return Tracing context, or null if none
     */
    public static TracingContext getCurrentContext();

    /**
     * Set current thread's tracing context
     * @param context Tracing context to set
     */
    public static void setCurrentContext(TracingContext context);

    /**
     * Clear current thread's tracing context
     * Important: Must be called after request processing to prevent memory leaks
     */
    public static void clearCurrentContext();

    /**
     * Check if current thread has tracing context
     * @return True if tracing context exists
     */
    public static boolean hasCurrentContext();

    /**
     * Get current trace ID
     * @return Trace ID, or empty string if no tracing context
     */
    public static String getCurrentTraceId();

    /**
     * Get current Span ID
     * @return Span ID, or empty string if no tracing context
     */
    public static String getCurrentSpanId();

    /**
     * Execute operation with tracing context
     * @param context Tracing context
     * @param operation Operation to execute
     * @return Operation result
     */
    public static <T> T executeWithContext(TracingContext context, ContextualOperation<T> operation) throws Exception;

    /**
     * Execute operation with tracing context (no return value)
     * @param context Tracing context
     * @param operation Operation to execute
     */
    public static void executeWithContext(TracingContext context, ContextualVoidOperation operation) throws Exception;
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

## Manual Span Creation

### Creating and Managing Spans Manually

Since annotation-based tracing is not available, use manual Span creation:

```java
@Service
public class UserService {

    @Autowired
    private TracingService tracingService;

    public User authenticate(String username, String password) {
        // Create operation span
        TracingContext context = tracingService.createOperationSpan(
            "user-authentication", SpanKind.INTERNAL);

        try {
            // Add custom attributes
            context.setTag("operation", "login");
            context.setTag("user.name", username);

            // Authentication logic
            User user = userRepository.findByUsername(username);

            // Add success event
            context.addEvent("authentication.success");

            return user;
        } catch (Exception e) {
            // Record error
            context.finishSpan(context.getCurrentSpan(), e);
            throw e;
        } finally {
            // Finish span
            context.finishSpan(context.getCurrentSpan());
        }
    }

    public void updateUserProfile(UserProfile profile) {
        // Get current context or create new one
        TracingContext context = TracingContextHolder.getCurrentContext();
        if (context == null || !context.isActive()) {
            context = tracingService.createOperationSpan(
                "user-profile-update", SpanKind.INTERNAL);
        }

        try {
            context.setTag("user.id", profile.getUserId());
            context.setTag("profile.field", "update");

            // Update user profile
            userProfileRepository.save(profile);

            context.addEvent("profile.updated");
        } finally {
            context.finishSpan(context.getCurrentSpan());
        }
    }
}
```

### Async Operation Tracing

```java
@RestController
public class AsyncController {

    @Autowired
    private TracingService tracingService;

    @PostMapping("/api/process")
    public CompletableFuture<ProcessResult> processData(@RequestBody DataRequest request) {
        // Create span for async operation
        TracingContext context = tracingService.createOperationSpan(
            "data-processing", SpanKind.INTERNAL);

        // Store trace info for propagation
        String traceId = context.getTraceId();
        String spanId = context.getSpanId();

        return CompletableFuture.supplyAsync(() -> {
            // Restore context in async thread
            TracingContextHolder.setCurrentContext(context);

            try {
                ProcessResult result = asyncService.processData(request);
                context.addEvent("processing.completed");
                return result;
            } catch (Exception e) {
                context.finishSpan(context.getCurrentSpan(), e);
                throw new RuntimeException(e);
            } finally {
                context.finishSpan(context.getCurrentSpan());
                TracingContextHolder.clearCurrentContext();
            }
        });
    }
}
```

## Reactive Programming Integration

### Mono/Flux Tracing Support

```java
@Component
public class ReactiveTracingService {

    @Autowired
    private Tracer tracer;

    public <T> Mono<T> traceMono(String operation, Mono<T> mono) {
        return Mono.defer(() -> {
            // Get or create context
            TracingContext parentContext = TracingContextHolder.getCurrentContext();
            TracingContext context;
            Span span;

            if (parentContext != null && parentContext.isActive()) {
                span = parentContext.createChildSpan(operation, SpanKind.INTERNAL, parentContext.getCurrentSpan());
                context = parentContext.copy();
                context.setCurrentSpan(span);
            } else {
                context = new DefaultTracingContext(tracer);
                span = context.createSpan(operation, SpanKind.INTERNAL);
            }

            TracingContextHolder.setCurrentContext(context);

            return mono
                .doOnSuccess(result -> {
                    context.setTag("result", "success");
                    context.finishSpan(span);
                    TracingContextHolder.clearCurrentContext();
                })
                .doOnError(error -> {
                    context.finishSpan(span, error);
                    context.setTag("result", "error");
                    TracingContextHolder.clearCurrentContext();
                });
        });
    }

    public <T> Flux<T> traceFlux(String operation, Flux<T> flux) {
        return Flux.defer(() -> {
            // Get or create context
            TracingContext parentContext = TracingContextHolder.getCurrentContext();
            TracingContext context;
            Span span;

            if (parentContext != null && parentContext.isActive()) {
                span = parentContext.createChildSpan(operation, SpanKind.INTERNAL, parentContext.getCurrentSpan());
                context = parentContext.copy();
                context.setCurrentSpan(span);
            } else {
                context = new DefaultTracingContext(tracer);
                span = context.createSpan(operation, SpanKind.INTERNAL);
            }

            TracingContextHolder.setCurrentContext(context);
            AtomicLong count = new AtomicLong(0);

            return flux
                .doOnNext(item -> count.incrementAndGet())
                .doOnComplete(() -> {
                    context.setTag("item.count", count.get());
                    context.setTag("result", "success");
                    context.finishSpan(span);
                    TracingContextHolder.clearCurrentContext();
                })
                .doOnError(error -> {
                    context.finishSpan(span, error);
                    context.setTag("result", "error");
                    TracingContextHolder.clearCurrentContext();
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
        context.setTag("custom.interceptor", "pre-handle");

        // Add custom attributes
        String userAgent = getCurrentUserAgent();
        if (userAgent != null) {
            span.setAttribute("http.user_agent", userAgent);
        }
    }

    @Override
    public void postHandle(TracingContext context, Span span, Object result) {
        // Post-handle processing
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> response = (ResponseEntity<?>) result;
            span.setAttribute("http.status_code", response.getStatusCodeValue());
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

    @Mock
    private Tracer tracer;

    @InjectMocks
    private TracingService tracingService;

    @Test
    void testCreateOperationSpan() {
        // Given
        String operation = "test-operation";

        // When
        TracingContext context = tracingService.createOperationSpan(
            operation, SpanKind.INTERNAL);

        // Then
        assertThat(context).isNotNull();
        assertThat(context.isActive()).isTrue();
    }

    @Test
    void testSetTag() {
        // Given
        TracingContext context = new DefaultTracingContext(tracer);
        context.createSpan("test", SpanKind.INTERNAL);

        // When
        context.setTag("test.key", "test.value");

        // Then
        // Verify tag was set on Span
        assertThat(context.isActive()).isTrue();
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
        assertThat(response.getHeaders().containsKey("X-Trace-Id")).isTrue();
    }
}
```

## Best Practices

### 1. Span Naming Conventions

```java
// Good naming
tracingService.createOperationSpan("user-authentication", SpanKind.INTERNAL);
tracingService.createOperationSpan("database-query", SpanKind.CLIENT);
tracingService.createOperationSpan("external-api-call", SpanKind.CLIENT);

// Avoid naming
tracingService.createOperationSpan("op1", SpanKind.INTERNAL);
tracingService.createOperationSpan("doSomething", SpanKind.INTERNAL);
```

### 2. Tag Usage Conventions

```java
// Meaningful tags
context.setTag("http.method", "POST");
context.setTag("user.id", userId);
context.setTag("business.operation", "payment");

// Or using Span directly
span.setAttribute("http.method", "POST");
span.setAttribute("user.id", userId);

// Avoid tags
context.setTag("tag1", "value");
context.setTag("data", largeObject.toString()); // Too large value
```

### 3. Exception Handling

```java
// Proper exception recording
try {
    performOperation();
} catch (BusinessException e) {
    context.finishSpan(span, e);
    context.setTag("error.type", "business");
    throw e;
}

// Avoid recording sensitive information
try {
    performOperation();
} catch (Exception e) {
    context.setTag("error.message", e.getMessage()); // May contain sensitive information
    throw e;
}
```

### 4. Performance Considerations

```java
// Efficient tracing - only trace critical operations
public void criticalOperation() {
    TracingContext context = tracingService.createOperationSpan(
        "critical-operation", SpanKind.INTERNAL);
    try {
        // Critical business logic
    } finally {
        context.finishSpan(context.getCurrentSpan());
    }
}

// Avoid over-tracing in high-frequency paths
public void highFrequencyOperation() {
    // Skip tracing for high-frequency method calls
    // May impact performance
}
```

### 5. Context Propagation in Async Operations

```java
// Proper async context handling
public CompletableFuture<Result> asyncOperation() {
    TracingContext context = tracingService.createOperationSpan(
        "async-op", SpanKind.INTERNAL);
    String traceId = context.getTraceId();

    return CompletableFuture.supplyAsync(() -> {
        // Restore context in async thread
        TracingContextHolder.setCurrentContext(context);
        try {
            return doWork();
        } finally {
            context.finishSpan(context.getCurrentSpan());
            TracingContextHolder.clearCurrentContext();
        }
    });
}
```

## Next Steps

- [API Reference](../api-reference/index.md) - Detailed API documentation
- [Configuration Reference](../tracing/config-reference.md) - Complete configuration options
- [Performance Tuning](../tracing/performance-tuning.md) - Performance optimization guide
