# Application Configuration

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



This document details the basic application configuration of JAiRouter, including server configuration, WebClient configuration, monitoring configuration, and more.

## Configuration File Locations

- **Main Configuration File**: `src/main/resources/application.yml`
- **Environment Configuration**: `application-{profile}.yml`
- **External Configuration**: `config/application.yml` (optional)

## Server Configuration

### Basic Server Configuration

```yaml
server:
  port: 8080                    # Service port
  servlet:
    context-path: /             # Application context path
  compression:
    enabled: true               # Enable response compression
    mime-types: application/json,text/plain,text/html
  http2:
    enabled: true               # Enable HTTP/2
```

### Advanced Server Configuration

```yaml
server:
  tomcat:
    threads:
      max: 200                  # Maximum thread count
      min-spare: 10             # Minimum spare threads
    connection-timeout: 20000   # Connection timeout (milliseconds)
    max-connections: 8192       # Maximum connections
    accept-count: 100           # Waiting queue length
  netty:
    connection-timeout: 45s     # Netty connection timeout
    h2c-max-content-length: 0   # H2C maximum content length
```

## WebClient Configuration

JAiRouter uses WebClient for backend service calls, supporting detailed connection configurations:

```yaml
webclient:
  connection-timeout: 10s       # Connection timeout
  read-timeout: 30s             # Read timeout
  write-timeout: 30s            # Write timeout
  max-in-memory-size: 10MB      # Maximum in-memory buffer size
  
  # Connection pool configuration
  connection-pool:
    max-connections: 500        # Maximum connections
    max-idle-time: 20s          # Maximum idle time
    max-life-time: 60s          # Connection maximum lifetime
    pending-acquire-timeout: 45s # Connection acquisition timeout
    evict-in-background: 120s   # Background eviction interval
  
  # SSL configuration
  ssl:
    enabled: false              # Enable SSL
    trust-all: false            # Trust all certificates
    key-store: classpath:keystore.p12
    key-store-password: password
    trust-store: classpath:truststore.p12
    trust-store-password: password
```

### WebClient Performance Tuning

```yaml
webclient:
  # Optimized configuration for high-concurrency scenarios
  connection-pool:
    max-connections: 1000       # Increase maximum connections
    max-idle-time: 30s          # Appropriately increase idle time
    pending-acquire-timeout: 60s # Increase connection acquisition timeout
  
  # Optimization for large file transfers
  max-in-memory-size: 50MB      # Increase memory buffer
  read-timeout: 120s            # Increase read timeout
  write-timeout: 120s           # Increase write timeout
```

## Monitoring Configuration

### Basic Monitoring Configuration

```yaml
monitoring:
  metrics:
    enabled: true               # Enable metric collection
    prefix: "jairouter"         # Metric prefix
    collection-interval: 10s    # Collection interval
    
    # Enabled metric categories
    enabled-categories:
      - system                  # System metrics
      - business                # Business metrics
      - infrastructure          # Infrastructure metrics
    
    # Custom tags
    custom-tags:
      environment: "production"
      version: "1.0.0"
      datacenter: "us-west-1"
```

### Advanced Monitoring Configuration

```yaml
monitoring:
  metrics:
    # Metric sampling configuration
    sampling:
      request-metrics: 1.0      # Request metric sampling rate (0.0-1.0)
      backend-metrics: 1.0      # Backend call metric sampling rate
      infrastructure-metrics: 0.1 # Infrastructure metric sampling rate
    
    # Performance optimization configuration
    performance:
      async-processing: true    # Asynchronous metric processing
      batch-size: 100           # Batch processing size
      buffer-size: 1000         # Buffer size
      flush-interval: 5s        # Flush interval
    
    # Metric filtering configuration
    filters:
      exclude-paths:            # Excluded paths
        - "/actuator/health"
        - "/favicon.ico"
      include-status-codes:     # Included status codes
        - 2xx
        - 4xx
        - 5xx
```

## Spring Actuator Configuration

### Basic Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,jairouter-metrics
      base-path: /actuator      # Endpoint base path
    enabled-by-default: true    # Enable all endpoints by default
  
  endpoint:
    health:
      show-details: always      # Show health check details
      show-components: always   # Show component status
      cache:
        time-to-live: 10s       # Health check cache time
    
    info:
      enabled: true             # Enable info endpoint
    
    metrics:
      enabled: true             # Enable metrics endpoint
    
    prometheus:
      enabled: true             # Enable Prometheus endpoint
      cache:
        time-to-live: 10s       # Prometheus metric cache time
```

### Security Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info    # Expose only basic endpoints
  
  endpoint:
    health:
      show-details: when-authorized # Details visible only to authorized users
  
  security:
    enabled: true               # Enable security control
    roles: ADMIN,ACTUATOR       # Allowed roles
```

## Prometheus Integration Configuration

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true           # Enable Prometheus export
        descriptions: true      # Include metric descriptions
        step: 10s               # Metric step
        pushgateway:
          enabled: false        # Enable Pushgateway
          base-url: http://localhost:9091
          job: jairouter
          push-rate: 30s
    
    # Global tags
    tags:
      application: jairouter
      environment: ${spring.profiles.active:default}
      instance: ${spring.application.name:jairouter}
    
    # Metric distribution configuration
    distribution:
      percentiles-histogram:
        http.server.requests: true
        jairouter.backend.requests: true
      percentiles:
        http.server.requests: 0.5,0.9,0.95,0.99
        jairouter.backend.requests: 0.5,0.9,0.95,0.99
      sla:
        http.server.requests: 10ms,50ms,100ms,200ms,500ms,1s,2s
        jairouter.backend.requests: 100ms,500ms,1s,2s,5s
```

## Logging Configuration

### Basic Logging Configuration

```yaml
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
    org.springframework.web: INFO
    reactor.netty: WARN
    io.netty: WARN
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  
  file:
    name: logs/jairouter.log
    max-size: 100MB
    max-history: 30
    total-size-cap: 1GB
```

### Environment-Specific Logging Configuration

```yaml
# application-dev.yml (Development Environment)
logging:
  level:
    org.unreal.modelrouter: DEBUG
    org.springframework.web: DEBUG
  pattern:
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr([%thread]){magenta} %clr(%-5level){highlight} %clr(%logger{36}){cyan} - %msg%n"

# application-prod.yml (Production Environment)
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
    org.springframework.web: WARN
  file:
    name: /var/log/jairouter/jairouter.log
    max-size: 500MB
    max-history: 60
```

## Global Model Configuration

### Adapter Configuration

```yaml
model:
  # Global default adapter
  adapter: gpustack             # Supported: normal, gpustack, ollama, vllm, xinference, localai
  
  # Adapter-specific configuration
  adapters:
    gpustack:
      api-version: v1
      timeout: 30s
    ollama:
      api-version: v1
      keep-alive: true
    vllm:
      api-version: v1
      streaming: true
```

### Global Load Balancing Configuration

```yaml
model:
  load-balance:
    type: round-robin           # Default load balancing strategy
    hash-algorithm: "md5"       # Hash algorithm for IP Hash strategy
    
    # Health check configuration
    health-check:
      enabled: true
      interval: 30s
      timeout: 5s
      failure-threshold: 3
      success-threshold: 2
```

## Storage Configuration

### File Storage Configuration

```yaml
store:
  type: file                   # Storage type: memory or file
  path: "config/"              # Configuration file storage path
  
  # File storage specific configuration
  file:
    auto-backup: true          # Automatic backup
    backup-interval: 1h        # Backup interval
    max-backups: 24            # Maximum number of backups
    compression: true          # Compress backup files
```

### Memory Storage Configuration

```yaml
store:
  type: memory                 # Memory storage
  
  # Memory storage specific configuration
  memory:
    initial-capacity: 1000     # Initial capacity
    max-size: 10000            # Maximum size
    expire-after-write: 24h    # Expiration after write
    expire-after-access: 12h   # Expiration after access
```

## Performance Tuning Configuration

### JVM Configuration

```yaml
# JVM-related configuration in application.yml
spring:
  application:
    name: jairouter
  
  # Thread pool configuration
  task:
    execution:
      pool:
        core-size: 8            # Core thread count
        max-size: 32            # Maximum thread count
        queue-capacity: 1000    # Queue capacity
        keep-alive: 60s         # Thread keep-alive time
    
    scheduling:
      pool:
        size: 4                 # Scheduling thread pool size
```

### Startup Parameter Recommendations

```bash
# Production Environment JVM Parameters
java -Xms1g -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/log/jairouter/ \
     -Dspring.profiles.active=prod \
     -jar model-router.jar

# Development Environment JVM Parameters
java -Xms512m -Xmx1g \
     -XX:+UseG1GC \
     -Dspring.profiles.active=dev \
     -Ddebug=true \
     -jar model-router.jar
```

## Environment Configuration Examples

### Development Environment Configuration

```yaml
# application-dev.yml
server:
  port: 8080

logging:
  level:
    org.unreal.modelrouter: DEBUG

webclient:
  connection-timeout: 5s
  read-timeout: 15s

monitoring:
  metrics:
    enabled: true
    collection-interval: 5s

store:
  type: memory
```

### Test Environment Configuration

```yaml
# application-test.yml
server:
  port: 8080

logging:
  level:
    org.unreal.modelrouter: INFO
  file:
    name: logs/jairouter-test.log

webclient:
  connection-timeout: 10s
  read-timeout: 30s

monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 0.5

store:
  type: file
  path: "config-test/"
```

### Production Environment Configuration

```yaml
# application-prod.yml
server:
  port: 8080
  tomcat:
    threads:
      max: 200
    max-connections: 8192

logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
  file:
    name: /var/log/jairouter/jairouter.log
    max-size: 500MB

webclient:
  connection-timeout: 10s
  read-timeout: 60s
  connection-pool:
    max-connections: 1000

monitoring:
  metrics:
    enabled: true
    performance:
      async-processing: true
      batch-size: 200

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus

store:
  type: file
  path: "/etc/jairouter/config/"
  file:
    auto-backup: true
    backup-interval: 1h
```

## Configuration Validation

### Configuration Check Commands

```bash
# Check configuration file syntax
java -jar model-router.jar --spring.config.location=classpath:/application.yml --spring.profiles.active=prod --dry-run

# Verify port availability
netstat -tulpn | grep 8080

# Check configuration loading
curl http://localhost:8080/actuator/configprops
```

### Common Configuration Errors

1. **Port Conflicts**
   ```yaml
   # Error: Port occupied
   server:
     port: 8080
   
   # Solution: Change port or stop the occupying process
   server:
     port: 8081
   ```

2. **Improper Timeout Configuration**
   ```yaml
   # Error: Timeout too short
   webclient:
     read-timeout: 1s
   
   # Solution: Adjust according to backend response time
   webclient:
     read-timeout: 30s
   ```

3. **Insufficient Memory Configuration**
   ```yaml
   # Error: Memory buffer too small
   webclient:
     max-in-memory-size: 1MB
   
   # Solution: Adjust according to request size
   webclient:
     max-in-memory-size: 10MB
   ```

## Next Steps

After completing the application configuration, you can continue configuring:

- **[Dynamic Configuration](dynamic-config.md)** - Learn runtime configuration management
- **[Load Balancing](load-balancing.md)** - Configure load balancing strategies
- **[Rate Limiting](rate-limiting.md)** - Set traffic control
- **[Circuit Breaker Configuration](circuit-breaker.md)** - Configure fault protection
