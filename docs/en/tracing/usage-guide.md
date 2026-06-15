# Usage Guide

This guide details how to effectively use JAiRouter's distributed tracing feature in various scenarios.

## Basic Usage

### Viewing Tracing Data

#### 1. Log Tracing Information

After enabling tracing, all requests will contain tracing information in the logs:

```bash
# View logs containing tracing information
tail -f logs/application.log | grep traceId

# Find all logs for a specific request by traceId
grep "4bf92f3577b34da6a3ce929d0e0e4736" logs/application.log
```

#### 2. Structured Log Query

```bash
# Use jq to parse JSON-formatted tracing logs
tail -f logs/application.log | jq 'select(.traceId != null)'

# Query tracing data for a specific service
tail -f logs/application.log | jq 'select(.service == "jairouter" and .traceId != null)'
```

#### 3. Actuator Endpoint Query

```bash
# View tracing health status
curl http://localhost:8080/actuator/health/tracing

# View tracing configuration information
curl http://localhost:8080/actuator/info | jq '.tracing'

# View tracing metrics
curl http://localhost:8080/actuator/metrics | grep tracing
```

### Custom Tracing Tags

#### 1. Adding Tags in Business Code

```java
@RestController
public class CustomController {

    @Autowired
    private TracingService tracingService;

    @PostMapping("/api/custom")
    public Mono<ResponseEntity<?>> customEndpoint(@RequestBody CustomRequest request) {
        // Get current tracing context (automatically created by HTTP filter)
        TracingContext context = TracingContextHolder.getCurrentContext();
        
        if (context != null && context.isActive()) {
            Span currentSpan = context.getCurrentSpan();
            
            // Add business attributes to Span
            currentSpan.setAttribute("user.id", request.getUserId());
            currentSpan.setAttribute("business.type", request.getType());
            currentSpan.setAttribute("custom.operation", "data-processing");
            
            // Record business event
            currentSpan.addEvent("business.started", 
                Attributes.of(AttributeKey.longKey("input.size"), request.getData().size()));
        }

        // Business logic processing
        return processRequest(request)
            .doOnSuccess(response -> {
                if (context != null && context.isActive()) {
                    Span span = context.getCurrentSpan();
                    span.setAttribute("result.status", response.getStatus());
                    span.addEvent("business.completed",
                        Attributes.of(AttributeKey.longKey("output.size"), response.getData().size()));
                }
            })
            .map(ResponseEntity::ok);
    }
}
```

#### 2. Manual Span Creation in Business Services

```java
@Component
public class BusinessService {

    @Autowired
    private TracingService tracingService;

    public Mono<ProcessResult> processUserData(UserData data) {
        return Mono.fromCallable(() -> {
            // Create custom operation span
            TracingContext context = tracingService.createOperationSpan(
                "user-data-processing", 
                SpanKind.INTERNAL
            );
            
            try {
                Span span = context.getCurrentSpan();
                span.setAttribute("user.id", data.getUserId());
                span.setAttribute("data.size", data.getSize());
                
                // Business logic
                ProcessResult result = performProcessing(data);
                
                span.addEvent("processing.completed");
                return result;
            } finally {
                context.finishSpan(context.getCurrentSpan());
            }
        });
    }

    public List<Entity> queryDatabase(String condition) {
        TracingContext parentContext = TracingContextHolder.getCurrentContext();
        
        if (parentContext != null && parentContext.isActive()) {
            // Create child span for database operation
            Span dbSpan = parentContext.createChildSpan(
                "database-query", 
                SpanKind.CLIENT,
                parentContext.getCurrentSpan()
            );
            
            dbSpan.setAttribute("db.operation", "query");
            dbSpan.setAttribute("db.condition", condition);
            
            try {
                return entityRepository.findByCondition(condition);
            } finally {
                dbSpan.end();
            }
        }
        
        return entityRepository.findByCondition(condition);
    }
}
```

## Advanced Scenarios

### Distributed Service Chain Tracing

#### 1. Inter-Service Call Tracing

```java
@Service
public class ExternalServiceClient {

    @Autowired
    private WebClient webClient;

    public Mono<ExternalResponse> callExternalService(ExternalRequest request) {
        TracingContext parentContext = TracingContextHolder.getCurrentContext();
        
        if (parentContext != null && parentContext.isActive()) {
            // Create child Span for external service call
            Span externalSpan = parentContext.createChildSpan(
                "external-service-call",
                SpanKind.CLIENT,
                parentContext.getCurrentSpan()
            );
            
            // Add service information
            externalSpan.setAttribute("external.service", "ai-model-service");
            externalSpan.setAttribute("external.endpoint", "/v1/chat/completions");
            externalSpan.setAttribute("request.model", request.getModel());
            
            // Prepare headers with tracing context
            Map<String, String> headers = new HashMap<>();
            parentContext.injectContext(headers);

            return webClient.post()
                .uri("/v1/chat/completions")
                .headers(httpHeaders -> headers.forEach(httpHeaders::add))
                .body(BodyInserters.fromValue(request))
                .retrieve()
                .bodyToMono(ExternalResponse.class)
                .doOnSuccess(response -> {
                    externalSpan.setAttribute("response.status", "success");
                    externalSpan.setAttribute("response.tokens", response.getUsage().getTotalTokens());
                    externalSpan.end();
                })
                .doOnError(error -> {
                    externalSpan.setAttribute("error.type", error.getClass().getSimpleName());
                    externalSpan.setAttribute("error.message", error.getMessage());
                    externalSpan.recordException(error);
                    externalSpan.end();
                });
        }
        
        // Fallback without tracing
        return webClient.post()
            .uri("/v1/chat/completions")
            .body(BodyInserters.fromValue(request))
            .retrieve()
            .bodyToMono(ExternalResponse.class);
    }
}
```

#### 2. Context Propagation in Reactive Streams

```java
@Component
public class ReactiveProcessor {

    public Mono<ProcessedData> processDataPipeline(InputData input) {
        return Mono.just(input)
            // First stage: validation
            .flatMap(this::validateInput)
            .doOnNext(validated -> {
                TracingContext context = TracingContextHolder.getCurrentContext();
                if (context != null && context.isActive()) {
                    context.getCurrentSpan().addEvent("pipeline.stage.validation");
                }
            })

            // Second stage: transformation
            .flatMap(this::transformData)
            .doOnNext(transformed -> {
                TracingContext context = TracingContextHolder.getCurrentContext();
                if (context != null && context.isActive()) {
                    Span span = context.getCurrentSpan();
                    span.addEvent("pipeline.stage.transform");
                    span.setAttribute("data.transformed", true);
                }
            })

            // Third stage: storage
            .flatMap(this::saveData)
            .doOnSuccess(result -> {
                TracingContext context = TracingContextHolder.getCurrentContext();
                if (context != null && context.isActive()) {
                    context.getCurrentSpan().setAttribute("pipeline.result", "success");
                }
            });
    }
}
```

### Slow Query Detection

#### 1. Automatic Slow Query Detection

The system automatically detects requests exceeding the threshold:

```yaml
jairouter:
  tracing:
    sampling:
      strategy: "adaptive"
      adaptive:
        slow-request-threshold: 3000  # 3-second threshold
        slow-request-sample-rate: 0.8 # 80% sampling for slow requests
```

#### 2. Manual Slow Query Analysis

```java
@Component
public class SlowQueryAnalyzer {

    public void analyzeSlowOperation() {
        TracingContext context = TracingContextHolder.getCurrentContext();
        long startTime = System.currentTimeMillis();

        try {
            // Execute potentially slow operation
            performComplexOperation();
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            if (duration > 5000 && context != null && context.isActive()) { // 5-second threshold
                Span span = context.getCurrentSpan();
                
                // Mark as slow query
                span.setAttribute("performance.slow", true);
                span.setAttribute("performance.duration_ms", duration);
                span.addEvent("slow.query.detected",
                    Attributes.of(
                        AttributeKey.stringKey("threshold"), "5000ms",
                        AttributeKey.stringKey("actual"), duration + "ms"
                    ));

                // Record detailed performance information
                recordPerformanceDetails();
            }
        }
    }
}
```

### Error Tracing and Analysis

#### 1. Automatic Exception Tracing

```java
@Component
public class ErrorHandlingService {

    @Autowired
    private TracingService tracingService;

    public Mono<Result> processWithErrorHandling(Request request) {
        return Mono.fromCallable(() -> processRequest(request))
            .onErrorResume(BusinessException.class, ex -> {
                // Business exception handling
                TracingContext context = TracingContextHolder.getCurrentContext();
                if (context != null && context.isActive()) {
                    Span span = context.getCurrentSpan();
                    span.setAttribute("error.type", "business");
                    span.setAttribute("error.code", ex.getErrorCode());
                    span.recordException(ex);
                }
                return Mono.just(createErrorResult(ex));
            })
            .onErrorResume(Exception.class, ex -> {
                // System exception handling
                TracingContext context = TracingContextHolder.getCurrentContext();
                if (context != null && context.isActive()) {
                    Span span = context.getCurrentSpan();
                    span.setAttribute("error.type", "system");
                    span.setAttribute("error.severity", "high");
                    span.recordException(ex);
                }
                return Mono.error(new SystemException("System processing failed", ex));
            });
    }
}
```

#### 2. Custom Error Tracing

```java
@Component
public class CustomErrorTracker {

    public void trackCustomError(String operation, Throwable error, Map<String, Object> context) {
        TracingContext parentContext = TracingContextHolder.getCurrentContext();
        
        if (parentContext != null && parentContext.isActive()) {
            // Create error analysis span
            Span errorSpan = parentContext.createChildSpan(
                "error-analysis",
                SpanKind.INTERNAL,
                parentContext.getCurrentSpan()
            );
            
            // Record basic error information
            errorSpan.setAttribute("error.operation", operation);
            errorSpan.setAttribute("error.class", error.getClass().getSimpleName());
            errorSpan.setAttribute("error.message", error.getMessage());

            // Record context information
            context.forEach((key, value) ->
                errorSpan.setAttribute("context." + key, String.valueOf(value)));

            // Record stack trace (sanitized)
            String sanitizedStackTrace = sanitizeStackTrace(error);
            errorSpan.addEvent("error.stacktrace",
                Attributes.of(AttributeKey.stringKey("trace"), sanitizedStackTrace));

            // Analyze error severity
            String severity = analyzeSeverity(error);
            errorSpan.setAttribute("error.severity", severity);
            
            // End the error span
            errorSpan.end();
        }
    }

    private String sanitizeStackTrace(Throwable error) {
        // Implement stack trace sanitization logic
        return error.getStackTrace()[0].toString();
    }
}
```

## Performance Monitoring

### Real-time Performance Metrics

#### 1. Viewing Key Metrics

```bash
# View Prometheus metrics related to tracing
curl -s http://localhost:8080/actuator/prometheus | grep jairouter_tracing

# View sampling rate metrics
curl -s http://localhost:8080/actuator/metrics/jairouter.tracing.sampling.rate

# View Span creation and export statistics
curl -s http://localhost:8080/actuator/metrics/jairouter.tracing.spans.created
curl -s http://localhost:8080/actuator/metrics/jairouter.tracing.spans.exported
```

#### 2. Performance Monitoring API

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

### Memory Usage Monitoring

#### 1. Monitoring Tracing Memory Usage

```java
@Component
public class TracingMemoryMonitor {
    
    @Scheduled(fixedRate = 30000) // Check every 30 seconds
    public void monitorMemoryUsage() {
        MemoryUsage memoryUsage = tracingMemoryManager.getMemoryUsage();
        
        if (memoryUsage.getUsedRatio() > 0.8) {
            // Memory usage exceeds 80%, trigger cleanup
            tracingMemoryManager.triggerCleanup();
            
            // Record memory pressure event
            TracingContext.current().ifPresent(tracing -> {
                tracing.addEvent("memory.pressure.detected",
                    Map.of("usedRatio", memoryUsage.getUsedRatio(),
                           "totalSpans", memoryUsage.getTotalSpans()));
            });
        }
    }
}
```

#### 2. Memory Usage Optimization

```yaml
jairouter:
  tracing:
    memory:
      max-spans: 50000              # Adjust based on actual memory
      cleanup-interval: 30s         # More frequent cleanup
      span-ttl: 180s               # Shorter TTL
      memory-threshold: 0.7        # Lower memory threshold
```

## Security and Privacy

### Sensitive Data Sanitization

#### 1. Automatic Sensitive Data Detection

```java
@Component
public class TracingSanitizer {
    
    public void sanitizeSpanAttributes(Span span, Map<String, Object> attributes) {
        attributes.forEach((key, value) -> {
            if (isSensitiveAttribute(key)) {
                // Sanitization processing
                String sanitizedValue = sanitizeValue(String.valueOf(value));
                span.setAttribute(key + ".sanitized", sanitizedValue);
            } else {
                span.setAttribute(key, String.valueOf(value));
            }
        });
    }
    
    private boolean isSensitiveAttribute(String key) {
        // Check if it's a sensitive attribute
        return key.toLowerCase().contains("password") ||
               key.toLowerCase().contains("token") ||
               key.toLowerCase().contains("secret") ||
               key.toLowerCase().contains("api-key");
    }
    
    private String sanitizeValue(String value) {
        if (value.length() <= 4) {
            return "***";
        }
        // Keep first 2 and last 2 characters, replace middle with *
        return value.substring(0, 2) + 
               "*".repeat(value.length() - 4) + 
               value.substring(value.length() - 2);
    }
}
```

#### 2. Configure Sensitive Data Filtering Rules

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

### Access Control

#### 1. Role-Based Tracing Data Access

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

## Troubleshooting

### Common Issue Diagnosis

#### 1. Missing Tracing Data

**Diagnosis Steps:**

```bash
# 1. Check if tracing is enabled
curl http://localhost:8080/actuator/health/tracing

# 2. Check sampling configuration
curl http://localhost:8080/actuator/configprops | jq '.jairouter.tracing.sampling'

# 3. Check exporter status
curl http://localhost:8080/actuator/metrics/jairouter.tracing.export.errors
```

**Solutions:**

```yaml
# Temporarily increase sampling rate for debugging
jairouter:
  tracing:
    sampling:
      strategy: "ratio"
      ratio: 1.0  # 100% sampling
```

#### 2. Performance Impact Too Large

**Diagnostic Metrics:**

```bash
# View tracing processing latency
curl http://localhost:8080/actuator/metrics/jairouter.tracing.processing.duration

# View memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

**Optimization Suggestions:**

```yaml
jairouter:
  tracing:
    # Reduce sampling rate
    sampling:
      ratio: 0.1
    
    # Enable async processing
    async:
      enabled: true
      core-pool-size: 4
    
    # Optimize memory configuration
    memory:
      max-spans: 5000
      cleanup-interval: 15s
```

#### 3. Context Propagation Issues

**Check Reactive Stream Context:**

```java
@Component
public class ContextDiagnostic {
    
    public Mono<String> diagnoseContext() {
        return Mono.deferContextual(ctx -> {
            boolean hasTracing = ctx.hasKey("tracing");
            String traceId = TracingContext.getCurrentTraceId();
            
            return Mono.just(String.format(
                "Context has tracing: %s, Current TraceId: %s", 
                hasTracing, traceId));
        });
    }
}
```

## Best Practices

### 1. Sampling Strategy Selection

- **Development Environment**: Use 100% sampling rate for debugging
- **Test Environment**: Use rule sampling, focusing on critical interfaces
- **Production Environment**: Use adaptive sampling, balancing performance and observability

### 2. Attribute Usage Standards

```java
// ✅ Good attribute naming
span.setAttribute("http.method", "POST");
span.setAttribute("user.id", userId);
span.setAttribute("business.operation", "payment");

// ❌ Avoid attribute naming
span.setAttribute("tag1", "value");  // Unclear naming
span.setAttribute("user_data", largeObject.toString());  // Large values
```

### 3. Error Handling

```java
// ✅ Proper error recording
span.setAttribute("error", "true");
span.setAttribute("error.type", "validation");
span.recordException(exception);

// ❌ Avoid recording sensitive error information
span.setAttribute("error.details", exception.getMessage());  // May contain sensitive information
```

### 4. Performance Considerations

- Avoid adding too many tags in high-frequency call paths
- Use async export to avoid affecting request performance
- Regularly clean up expired tracing data
- Monitor the tracing system's own resource usage

## Next Steps

- [Performance Tuning](performance-tuning.md) - In-depth performance optimization guide
- [Troubleshooting](troubleshooting.md) - Detailed troubleshooting manual
- [Development Integration](developer-guide.md) - Developer integration guide
- [Operations Guide](operations-guide.md) - Production environment operations best practices