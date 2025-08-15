# Debugging Guide

This document provides debugging techniques, tool usage, and problem localization methods for JAiRouter.

## Debugging Environment Configuration

### Development Environment Debugging

#### Enable Debug Mode
```yaml
# application-dev.yml
logging:
  level:
    org.unreal.modelrouter: DEBUG
    org.springframework.web: DEBUG
    reactor.netty: DEBUG
    
debug: true

spring:
  profiles:
    active: dev
```

#### IDE Debug Configuration

**IntelliJ IDEA**
```
Run/Debug Configurations:
- Main class: org.unreal.modelrouter.ModelRouterApplication
- VM options: -Dspring.profiles.active=dev -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005
- Program arguments: --debug
- Environment variables: SPRING_PROFILES_ACTIVE=dev
```

**VS Code**
```json
{
  "type": "java",
  "name": "Debug JAiRouter",
  "request": "launch",
  "mainClass": "org.unreal.modelrouter.ModelRouterApplication",
  "projectName": "model-router",
  "args": "--debug",
  "vmArgs": "-Dspring.profiles.active=dev",
  "env": {
    "SPRING_PROFILES_ACTIVE": "dev"
  }
}
```

### Remote Debugging

#### Enable Remote Debugging
```bash
# Add debug parameters when starting the application
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar target/model-router-*.jar
```

#### Docker Remote Debugging
```yaml
# docker-compose-debug.yml
version: '3.8'
services:
  jairouter:
    image: jairouter/model-router:latest
    ports:
      - "8080:8080"
      - "5005:5005"  # Debug port
    environment:
      - JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
      - SPRING_PROFILES_ACTIVE=dev
```

## Log Debugging

### Log Level Configuration

#### Global Log Configuration
```yaml
# application.yml
logging:
  level:
    root: INFO
    org.unreal.modelrouter: DEBUG
    org.springframework: INFO
    reactor: DEBUG
    
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
    
  file:
    name: logs/jairouter-debug.log
    max-size: 100MB
    max-history: 30
```

#### Dynamic Log Level Adjustment
```bash
# Adjust log level at runtime
curl -X POST http://localhost:8080/actuator/loggers/org.unreal.modelrouter \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# View current log level
curl http://localhost:8080/actuator/loggers/org.unreal.modelrouter
```

### Structured Logging

#### Add Trace ID
```java
@Component
public class TraceIdFilter implements WebFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        
        return chain.filter(exchange)
            .contextWrite(Context.of("traceId", traceId))
            .doOnEach(ReactiveUtils.addToMDC("traceId", traceId));
    }
}
```

#### Key Point Logging
```java
@Service
public class LoadBalancerService {
    private static final Logger log = LoggerFactory.getLogger(LoadBalancerService.class);
    
    public ServiceInstance selectInstance(String serviceType, String clientInfo) {
        log.debug("Selecting instance for service: {}, client: {}", serviceType, clientInfo);
        
        List<ServiceInstance> instances = getAvailableInstances(serviceType);
        log.debug("Available instances: {}", instances.size());
        
        ServiceInstance selected = loadBalancer.selectInstance(instances, clientInfo);
        log.info("Selected instance: {} for service: {}", 
                selected != null ? selected.getName() : "null", serviceType);
        
        return selected;
    }
}
```

### Log Analysis Tools

#### Real-time Log Monitoring
```bash
# View logs in real-time
tail -f logs/jairouter-debug.log

# Filter specific content
tail -f logs/jairouter-debug.log | grep -i "error\|exception"

# Count error occurrences
grep -c "ERROR" logs/jairouter-debug.log

# View recent errors
grep "ERROR" logs/jairouter-debug.log | tail -10
```

#### Log Analysis Script
```bash
#!/bin/bash
# log-analyzer.sh

LOG_FILE="logs/jairouter-debug.log"
REPORT_FILE="log-analysis-report.txt"

echo "=== JAiRouter Log Analysis Report ===" > $REPORT_FILE
echo "Analysis time: $(date)" >> $REPORT_FILE
echo "" >> $REPORT_FILE

# Error statistics
echo "=== Error Statistics ===" >> $REPORT_FILE
echo "ERROR count: $(grep -c 'ERROR' $LOG_FILE)" >> $REPORT_FILE
echo "WARN count: $(grep -c 'WARN' $LOG_FILE)" >> $REPORT_FILE
echo "" >> $REPORT_FILE

# Most frequent errors
echo "=== Most Frequent Errors ===" >> $REPORT_FILE
grep "ERROR" $LOG_FILE | cut -d'-' -f4- | sort | uniq -c | sort -nr | head -5 >> $REPORT_FILE
echo "" >> $REPORT_FILE

# Performance-related logs
echo "=== Performance Related ===" >> $REPORT_FILE
grep -i "timeout\|slow\|performance" $LOG_FILE | tail -5 >> $REPORT_FILE
echo "" >> $REPORT_FILE

# Connection issues
echo "=== Connection Issues ===" >> $REPORT_FILE
grep -i "connection\|refused\|unreachable" $LOG_FILE | tail -5 >> $REPORT_FILE

echo "Log analysis completed, report saved to: $REPORT_FILE"
```

## Network Debugging

### HTTP Request Debugging

#### Using curl for Debugging
```bash
# Verbose output mode
curl -v -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"test","messages":[{"role":"user","content":"hello"}]}'

# Show response headers
curl -I http://localhost:8080/actuator/health

# Test connection time
curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8080/v1/chat/completions

# curl-format.txt
#      time_namelookup:  %{time_namelookup}s\n
#         time_connect:  %{time_connect}s\n
#      time_appconnect:  %{time_appconnect}s\n
#     time_pretransfer:  %{time_pretransfer}s\n
#        time_redirect:  %{time_redirect}s\n
#   time_starttransfer:  %{time_starttransfer}s\n
#                      ----------\n
#           time_total:  %{time_total}s\n
```

#### Using httpie for Debugging
```bash
# Install httpie
pip install httpie

# Send request
http POST localhost:8080/v1/chat/completions \
  Content-Type:application/json \
  model=test \
  messages:='[{"role":"user","content":"hello"}]'

# Show detailed information
http --print=HhBb POST localhost:8080/v1/chat/completions \
  Content-Type:application/json \
  model=test \
  messages:='[{"role":"user","content":"hello"}]'
```

### Network Connection Debugging

#### Port and Connection Checking
```bash
# Check port listening
netstat -tlnp | grep :8080

# Check connection status
ss -tuln | grep :8080

# Test port connectivity
telnet localhost 8080

# Check firewall
# Ubuntu/Debian
sudo ufw status

# CentOS/RHEL
sudo firewall-cmd --list-all
```

#### Network Packet Analysis
```bash
# Capture packets with tcpdump
sudo tcpdump -i any -w jairouter.pcap port 8080

# Analyze with wireshark
wireshark jairouter.pcap

# Analyze HTTP traffic
sudo tcpdump -i any -A -s 0 port 8080
```

## JVM Debugging

### Memory Debugging

#### Heap Memory Analysis
```bash
# View heap memory usage
jstat -gc <pid>

# Generate heap dump
jmap -dump:format=b,file=heap.hprof <pid>

# Analyze heap dump
jhat heap.hprof
# Or use Eclipse MAT

# View object statistics
jmap -histo <pid> | head -20
```

#### Memory Leak Detection
```bash
# Enable memory leak detection
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/heapdump.hprof \
     -XX:+PrintGCDetails \
     -XX:+PrintGCTimeStamps \
     -Xloggc:gc.log \
     -jar target/model-router-*.jar

# Analyze GC logs
# Use GCViewer or online tools to analyze gc.log
```

### Thread Debugging

#### Thread Dump Analysis
```bash
# Generate thread dump
jstack <pid> > threads.dump

# Or use jcmd
jcmd <pid> Thread.print > threads.dump

# Analyze deadlocks
grep -A 5 -B 5 "deadlock" threads.dump

# Analyze thread states
grep "java.lang.Thread.State" threads.dump | sort | uniq -c
```

#### Thread Monitoring
```java
@Component
public class ThreadMonitor {
    private static final Logger log = LoggerFactory.getLogger(ThreadMonitor.class);
    
    @Scheduled(fixedRate = 60000) // Check every minute
    public void monitorThreads() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        if (deadlockedThreads != null) {
            log.error("Detected deadlocked threads: {}", Arrays.toString(deadlockedThreads));
        }
        
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        
        log.debug("Current thread count: {}, Peak thread count: {}", threadCount, peakThreadCount);
        
        if (threadCount > 200) {
            log.warn("Too many threads: {}", threadCount);
        }
    }
}
```

### Performance Debugging

#### CPU Performance Analysis
```bash
# Use Java Flight Recorder
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=duration=60s,filename=profile.jfr \
     -jar target/model-router-*.jar

# Analyze JFR file
jfr print --events CPULoad profile.jfr
jfr print --events JavaMonitorEnter profile.jfr

# Use async-profiler
java -jar async-profiler.jar -d 60 -f profile.html <pid>
```

#### Method-level Performance Analysis
```java
@Component
public class PerformanceMonitor {
    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitor.class);
    
    @Around("@annotation(Monitored)")
    public Object monitor(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            if (duration > 1000) {
                log.warn("Method {} execution time too long: {}ms", methodName, duration);
            } else {
                log.debug("Method {} execution time: {}ms", methodName, duration);
            }
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Method {} execution failed, duration: {}ms", methodName, duration, e);
            throw e;
        }
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Monitored {
}
```

## Reactive Debugging

### Reactor Debugging

#### Enable Debug Mode
```java
@SpringBootApplication
public class ModelRouterApplication {
    
    public static void main(String[] args) {
        // Enable Reactor debugging
        Hooks.onOperatorDebug();
        
        SpringApplication.run(ModelRouterApplication.class, args);
    }
}
```

#### Reactive Stream Debugging
```java
@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    
    public Mono<String> processChat(String request) {
        return Mono.fromCallable(() -> request)
            .doOnSubscribe(s -> log.debug("Starting chat request processing"))
            .doOnNext(req -> log.debug("Processing request: {}", req))
            .flatMap(this::callBackend)
            .doOnSuccess(response -> log.debug("Backend response: {}", response))
            .doOnError(error -> log.error("Processing failed", error))
            .doFinally(signal -> log.debug("Processing completed: {}", signal));
    }
    
    private Mono<String> callBackend(String request) {
        return webClient.post()
            .uri("/v1/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(30))
            .onErrorMap(TimeoutException.class, 
                ex -> new ServiceException("Backend service timeout", ex));
    }
}
```

#### Backpressure Debugging
```java
@Component
public class BackpressureMonitor {
    private static final Logger log = LoggerFactory.getLogger(BackpressureMonitor.class);
    
    public Flux<String> processStream(Flux<String> input) {
        return input
            .onBackpressureBuffer(1000, 
                dropped -> log.warn("Backpressure buffer full, dropped element: {}", dropped))
            .doOnRequest(n -> log.debug("Requested {} elements", n))
            .doOnCancel(() -> log.debug("Stream cancelled"))
            .map(this::processItem);
    }
}
```

## Configuration Debugging

### Configuration Validation

#### Configuration Property Checking
```bash
# View all configuration properties
curl http://localhost:8080/actuator/configprops | jq

# View environment variables
curl http://localhost:8080/actuator/env | jq

# View specific configuration
curl http://localhost:8080/actuator/env/model.services | jq
```

#### Configuration Binding Debugging
```java
@ConfigurationProperties(prefix = "model")
@Validated
@Data
public class ModelConfiguration {
    
    @PostConstruct
    public void validate() {
        log.info("Model configuration loaded: {}", this);
        
        if (services == null || services.isEmpty()) {
            throw new IllegalStateException("At least one service must be configured");
        }
        
        services.forEach((type, config) -> {
            if (config.getInstances() == null || config.getInstances().isEmpty()) {
                throw new IllegalStateException("Service " + type + " must have at least one instance");
            }
        });
    }
}
```

### Dynamic Configuration Debugging

#### Configuration Update Tracking
```java
@Component
public class ConfigurationChangeListener {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationChangeListener.class);
    
    @EventListener
    public void handleConfigurationChange(ConfigurationChangeEvent event) {
        log.info("Configuration changed: Type={}, Key={}, Old Value={}, New Value={}", 
                event.getType(), event.getKey(), event.getOldValue(), event.getNewValue());
    }
}
```

## Debugging Tool Integration

### Spring Boot Actuator

#### Enable Debug Endpoints
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
    env:
      show-values: always
```

#### Custom Health Check
```java
@Component
public class BackendHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // Check backend service health status
            boolean isHealthy = checkBackendHealth();
            
            if (isHealthy) {
                return Health.up()
                    .withDetail("backend", "All services are healthy")
                    .build();
            } else {
                return Health.down()
                    .withDetail("backend", "Some services are unhealthy")
                    .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### External Debugging Tools

#### Zipkin Distributed Tracing
```yaml
# Add dependencies
spring:
  zipkin:
    base-url: http://zipkin:9411
  sleuth:
    sampler:
      probability: 1.0  # Full sampling in development environment
```

#### Prometheus Monitoring
```java
@Component
public class CustomMetrics {
    private final Counter requestCounter;
    private final Timer requestTimer;
    
    public CustomMetrics(MeterRegistry meterRegistry) {
        this.requestCounter = Counter.builder("jairouter.requests.custom")
            .description("Custom request counter")
            .register(meterRegistry);
            
        this.requestTimer = Timer.builder("jairouter.request.duration.custom")
            .description("Custom request duration")
            .register(meterRegistry);
    }
    
    public void recordRequest(String serviceType, Duration duration) {
        requestCounter.increment(Tags.of("service", serviceType));
        requestTimer.record(duration, Tags.of("service", serviceType));
    }
}
```

## Debugging Best Practices

### 1. Layered Debugging Strategy

#### Network Layer Debugging
- Use network packet capture tools to analyze requests/responses
- Check DNS resolution and connection establishment
- Verify SSL/TLS handshake process

#### Application Layer Debugging
- Add logging for critical paths
- Use breakpoints to debug business logic
- Monitor method execution time

#### Data Layer Debugging
- Check configuration data loading and parsing
- Verify data transformation and serialization
- Monitor cache hit rates

### 2. Problem Localization Process

#### Quick Localization
1. Check application health status
2. Review recent error logs
3. Verify configuration correctness
4. Test network connectivity

#### In-depth Analysis
1. Enable detailed logging
2. Use performance analysis tools
3. Analyze memory and thread states
4. Perform stress testing for validation

### 3. Debugging Environment Management

#### Development Environment
```yaml
# application-dev.yml
logging:
  level:
    org.unreal.modelrouter: DEBUG
    
management:
  endpoints:
    web:
      exposure:
        include: "*"
        
spring:
  webflux:
    netty:
      access-log: true
```

#### Test Environment
```yaml
# application-test.yml
logging:
  level:
    org.unreal.modelrouter: INFO
    
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics"
```

#### Production Environment
```yaml
# application-prod.yml
logging:
  level:
    org.unreal.modelrouter: WARN
    
management:
  endpoints:
    web:
      exposure:
        include: "health,info"
```

### 4. Debugging Toolbox

#### Essential Tools
- **curl/httpie**: HTTP request testing
- **jstack/jmap**: JVM debugging
- **tcpdump/wireshark**: Network analysis
- **ab/wrk**: Performance testing

#### Recommended Tools
- **VisualVM**: JVM performance analysis
- **Eclipse MAT**: Memory analysis
- **async-profiler**: CPU performance analysis
- **Arthas**: Online diagnostic tool

By mastering these debugging techniques and tools, you can quickly locate and resolve various issues encountered during JAiRouter operation.
