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
    public ResponseEntity<?> customEndpoint(@RequestBody CustomRequest request) {
        // Add business tags
        tracingService.addTag("user.id", request.getUserId());
        tracingService.addTag("business.type", request.getType());
        tracingService.addTag("custom.operation", "data-processing");
        
        // Record business events
        tracingService.addEvent("business.started", 
            Map.of("input.size", request.getData().size()));
        
        // Business logic processing
        CustomResponse response = processRequest(request);
        
        // Record processing results
        tracingService.addTag("result.status", response.getStatus());
        tracingService.addEvent("business.completed",
            Map.of("output.size", response.getData().size()));
        
        return ResponseEntity.ok(response);
    }
}
```

#### 2. Using Annotations to Add Tracing

```java
@Component
public class BusinessService {
    
    @TraceAsync("user-data-processing")
    public Mono<ProcessResult> processUserData(UserData data) {
        return Mono.fromCallable(() -> {
            // Business logic
            return new ProcessResult();
        });
    }
    
    @TraceSpan(name = "database-query", tags = {"operation", "query"})
    public List<Entity> queryDatabase(String condition) {
        // Database query logic
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
    
    @Autowired
    private TracingService tracingService;
    
    public Mono<ExternalResponse> callExternalService(ExternalRequest request) {
        // Create child Span for external service call
        return tracingService.withChildSpan("external-service-call", span -> {
            // Add service information
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

#### 2. Context Propagation in Reactive Streams

```java
@Component
public class ReactiveProcessor {
    
    public Mono<ProcessedData> processDataPipeline(InputData input) {
        return Mono.just(input)
            // First stage: validation
            .flatMap(this::validateInput)
            .contextWrite(ctx -> 
                TracingContext.currentContext()
                    .map(tracing -> ctx.put("tracing", tracing))
                    .orElse(ctx))
            
            // Second stage: transformation
            .flatMap(this::transformData)
            .contextWrite(ctx -> {
                // Update tracing information at each stage
                TracingContext.current()
                    .ifPresent(tracing -> 
                        tracing.addEvent("pipeline.stage.transform"));
                return ctx;
            })
            
            // Third stage: storage
            .flatMap(this::saveData)
            .doOnSuccess(result -> 
                TracingContext.current()
                    .ifPresent(tracing -> 
                        tracing.addTag("pipeline.result", "success")));
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
    
    @Autowired
    private TracingService tracingService;
    
    public void analyzeSlowOperation() {
        long startTime = System.currentTimeMillis();
        
        try {
            // Execute potentially slow operation
            performComplexOperation();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            if (duration > 5000) { // 5-second threshold
                // Mark as slow query
                tracingService.addTag("performance.slow", "true");
                tracingService.addTag("performance.duration", String.valueOf(duration));
                tracingService.addEvent("slow.query.detected", 
                    Map.of("threshold", "5000ms", "actual", duration + "ms"));
                
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
    
    public Mono<Result> processWithErrorHandling(Request request) {
        return Mono.fromCallable(() -> processRequest(request))
            .onErrorResume(BusinessException.class, ex -> {
                // Business exception handling
                TracingContext.current().ifPresent(tracing -> {
                    tracing.addTag("error.type", "business");
                    tracing.addTag("error.code", ex.getErrorCode());
                    tracing.recordException(ex);
                });
                return Mono.just(createErrorResult(ex));
            })
            .onErrorResume(Exception.class, ex -> {
                // System exception handling
                TracingContext.current().ifPresent(tracing -> {
                    tracing.addTag("error.type", "system");
                    tracing.addTag("error.severity", "high");
                    tracing.recordException(ex);
                });
                return Mono.error(new SystemException("System processing failed", ex));
            });
    }
}
```

#### 2. Custom Error Tracing

```java
@Component
public class CustomErrorTracker {
    
    @Autowired
    private TracingService tracingService;
    
    public void trackCustomError(String operation, Throwable error, Map<String, Object> context) {
        tracingService.withSpan("error-analysis", span -> {
            // Record basic error information
            span.addTag("error.operation", operation);
            span.addTag("error.class", error.getClass().getSimpleName());
            span.addTag("error.message", error.getMessage());
            
            // Record context information
            context.forEach((key, value) -> 
                span.addTag("context." + key, String.valueOf(value)));
            
            // Record stack trace (sanitized)
            String sanitizedStackTrace = sanitizeStackTrace(error);
            span.addEvent("error.stacktrace", 
                Map.of("trace", sanitizedStackTrace));
            
            // Analyze error severity
            String severity = analyzeSeverity(error);
            span.addTag("error.severity", severity);
            
            return null;
        });
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
                span.addTag(key + ".sanitized", sanitizedValue);
            } else {
                span.addTag(key, String.valueOf(value));
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

### 2. Tag Usage Standards

```java
// ✅ Good tag naming
span.addTag("http.method", "POST");
span.addTag("user.id", userId);
span.addTag("business.operation", "payment");

// ❌ Avoid tag naming
span.addTag("tag1", "value");  // Unclear naming
span.addTag("user_data", largeObject.toString());  // Large values
```

### 3. Error Handling

```java
// ✅ Proper error recording
span.addTag("error", "true");
span.addTag("error.type", "validation");
span.recordException(exception);

// ❌ Avoid recording sensitive error information
span.addTag("error.details", exception.getMessage());  // May contain sensitive information
```

### 4. Performance Considerations

- Avoid adding too many tags in high-frequency call paths
- Use async export to avoid affecting request performance
- Regularly clean up expired tracing data
- Monitor the tracing system's own resource usage

## Next Steps

- [Performance Tuning](performance-tuning.md) - In-depth performance optimization guide
- [Troubleshooting](troubleshooting.md) - Detailed troubleshooting manual
- [Development Integration](../development/tracing-integration.md) - Developer integration guide
- [Operations Guide](operations-guide.md) - Production environment operations best practices