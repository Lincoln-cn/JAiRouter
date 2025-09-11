# Configuration Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-28
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->

JAiRouter provides flexible configuration options to meet various deployment scenarios. This guide covers all configuration aspects from basic setup to advanced features.

## Modular Configuration Overview

Starting from v1.0.0, JAiRouter adopts a modular configuration structure, separating different functional configurations into independent configuration files to improve maintainability and readability.

### Configuration File Structure

```
src/main/resources/
├── application.yml                    # Main configuration file, imports other modules
├── config/
│   ├── base/
│   │   ├── server-base.yml           # Server base configuration
│   │   ├── model-services-base.yml    # Model services configuration
│   │   └── monitoring-base.yml       # Monitoring base configuration
│   ├── security/
│   │   └── security-base.yml         # Security feature configuration
│   ├── tracing/
│   │   └── tracing-base.yml          # Tracing feature configuration
│   └── monitoring/
│       ├── slow-query-alerts.yml     # Slow query alert configuration
│       └── error-tracking.yml        # Error tracking configuration
├── application-dev.yml               # Development environment configuration
├── application-staging.yml           # Staging environment configuration
├── application-prod.yml              # Production environment configuration
├── application-legacy.yml            # Backward compatibility configuration
└── application-security-example.yml  # Security configuration example
```

### Configuration Import Mechanism

The main configuration file [application.yml](file://D:/IdeaProjects/model-router/src/main/resources/application.yml) imports various module configurations through the `spring.config.import` mechanism:

```yaml
# application.yml
spring:
  config:
    import:
      - classpath:config/base/server-base.yml
      - classpath:config/base/model-services-base.yml
      - classpath:config/base/monitoring-base.yml
      - classpath:config/tracing/tracing-base.yml
      - classpath:config/security/security-base.yml
      - classpath:config/monitoring/slow-query-alerts.yml
      - classpath:config/monitoring/error-tracking.yml
```

This approach makes the configuration clearer and easier to maintain and extend.

## Configuration Overview

JAiRouter supports two main configuration approaches:

1. **Static Configuration**: Defined in YAML files, loaded at startup
2. **Dynamic Configuration**: Updated at runtime via REST APIs

## Configuration Hierarchy

Configuration is loaded in the following order (later sources override earlier ones):

1. Default configuration (embedded in JAR)
2. `application.yml` (classpath)
3. `./application.yml` (current directory)
4. `./config/application.yml` (config directory)
5. Environment variables
6. Command line arguments
7. Dynamic configuration (runtime updates)

## Basic Structure

```yaml
server:
  port: 8080

model:
  services:
    <service-type>:
      load-balance:
        type: <strategy>
      rate-limit:
        type: <algorithm>
        # ... rate limit settings
      circuit-breaker:
        enabled: true
        # ... circuit breaker settings
      fallback:
        type: <fallback-type>
        # ... fallback settings
      instances:
        - name: <model-name>
          baseUrl: <service-url>
          path: <api-path>
          weight: <load-balance-weight>
          # ... instance-specific settings

store:
  type: <storage-backend>
  # ... storage settings
```

## Service Types

JAiRouter supports the following service types:

| Service Type | Description | Default Path |
|--------------|-------------|--------------|
| `chat` | Chat completions | `/v1/chat/completions` |
| `embedding` | Text embeddings | `/v1/embeddings` |
| `rerank` | Text reranking | `/v1/rerank` |
| `tts` | Text-to-speech | `/v1/audio/speech` |
| `stt` | Speech-to-text | `/v1/audio/transcriptions` |
| `imgGen` | Image generation | `/v1/images/generations` |
| `imgEdit` | Image editing | `/v1/images/edits` |

## Configuration Sections

### 1. Load Balancing

Configure how requests are distributed across service instances:

```yaml
model:
  services:
    chat:
      load-balance:
        type: round-robin  # random, round-robin, least-connections, ip-hash
```

**Available Strategies:**

- **random**: Random selection of instances
- **round-robin**: Sequential rotation through instances
- **least-connections**: Route to instance with fewest active connections
- **ip-hash**: Consistent routing based on client IP hash

### 2. Rate Limiting

Control request rates to prevent service overload:

```yaml
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100          # Maximum tokens in bucket
        refill-rate: 10        # Tokens added per second
        client-ip-enable: true # Enable per-client-IP rate limiting
```

**Available Algorithms:**

- **token-bucket**: Allow bursts up to bucket capacity
- **leaky-bucket**: Smooth, constant rate limiting
- **sliding-window**: Rate limit over time windows
- **warm-up**: Gradually increase rate limit

### 3. Circuit Breaking

Prevent cascading failures with circuit breaker pattern:

```yaml
model:
  services:
    chat:
      circuit-breaker:
        enabled: true
        failure-threshold: 5      # Failures before opening circuit
        recovery-timeout: 30000   # Time before attempting recovery (ms)
        success-threshold: 3      # Successes needed to close circuit
```

### 4. Fallback Strategies

Define fallback behavior when services are unavailable:

```yaml
model:
  services:
    chat:
      fallback:
        type: default
        message: "Service temporarily unavailable"
        # OR
        type: cache
        ttl: 300000  # Cache TTL in milliseconds
```

**Available Types:**

- **default**: Return predefined message
- **cache**: Return cached responses
- **none**: No fallback (return error)

### 5. Service Instances

Define the actual service endpoints:

```yaml
model:
  services:
    chat:
      instances:
        - name: "qwen2.5:7b"
          baseUrl: "http://server1:11434"
          path: "/v1/chat/completions"
          weight: 2
          timeout: 30000
          headers:
            Authorization: "Bearer token"
            Custom-Header: "value"
        - name: "qwen2.5:14b"
          baseUrl: "http://server2:11434"
          path: "/v1/chat/completions"
          weight: 1
```

**Instance Properties:**

- `name`: Model name identifier
- `baseUrl`: Service base URL
- `path`: API endpoint path
- `weight`: Load balancing weight (higher = more traffic)
- `timeout`: Request timeout in milliseconds
- `headers`: Custom headers to include in requests

### 6. Storage Configuration

Configure how dynamic configuration is persisted:

```yaml
store:
  type: file              # memory or file
  path: ./config          # File storage directory
  auto-backup: true       # Enable automatic backups
  backup-interval: 3600   # Backup interval in seconds
```

**Storage Types:**

- **memory**: In-memory storage (lost on restart)
- **file**: File-based storage (persisted across restarts)

## Environment-Specific Configuration

JAiRouter supports multiple environment configuration files:

- **Development Environment**: [application-dev.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-dev.yml)
- **Staging Environment**: [application-staging.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-staging.yml)
- **Production Environment**: [application-prod.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-prod.yml)
- **Compatibility Mode**: [application-legacy.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-legacy.yml)
- **Security Example**: [application-security-example.yml](file://D:/IdeaProjects/model-router/src/main/resources/application-security-example.yml)

Environment configuration files only contain the differences from the base configuration, following Spring Boot's configuration override mechanism.

## Environment Variables

Override configuration using environment variables:

```bash
# Server configuration
export SERVER_PORT=8080

# Model service configuration
export MODEL_LOAD_BALANCE_TYPE=round-robin
export MODEL_RATE_LIMIT_TYPE=token-bucket
```

## Command Line Arguments

Override configuration via command line:

```bash
java -jar jairouter.jar \
  --server.port=8080 \
  --model.load-balance.type=round-robin \
  --spring.profiles.active=prod
```

## Configuration Validation

JAiRouter validates configuration at startup. Common validation errors:

### Missing Required Fields

```yaml
# ❌ Invalid - missing baseUrl
model:
  services:
    chat:
      instances:
        - name: "model"
          path: "/v1/chat/completions"

# ✅ Valid
model:
  services:
    chat:
      instances:
        - name: "model"
          baseUrl: "http://localhost:11434"
          path: "/v1/chat/completions"
```

### Invalid Configuration Values

```yaml
# ❌ Invalid - unsupported load balance type
model:
  services:
    chat:
      load-balance:
        type: invalid-type

# ✅ Valid
model:
  services:
    chat:
      load-balance:
        type: round-robin
```

## Configuration Best Practices

### 1. Use Meaningful Names

```yaml
# ✅ Good - descriptive names
model:
  services:
    chat:
      instances:
        - name: "qwen2.5-7b-fast"
          baseUrl: "http://fast-gpu-server:11434"
        - name: "qwen2.5-14b-accurate"
          baseUrl: "http://high-memory-server:11434"
```

### 2. Set Appropriate Timeouts

```yaml
# ✅ Good - reasonable timeouts
model:
  services:
    chat:
      instances:
        - name: "model"
          baseUrl: "http://server:11434"
          timeout: 30000  # 30 seconds for chat
    embedding:
      instances:
        - name: "embedding"
          baseUrl: "http://server:11434"
          timeout: 10000  # 10 seconds for embeddings
```

### 3. Configure Health Checks

```yaml
# ✅ Good - enable health monitoring
model:
  load-balance:
    health-check:
      enabled: true
      interval: 30000
      timeout: 5000
```

### 4. Use Weights for Gradual Rollouts

```yaml
# ✅ Good - gradual rollout with weights
model:
  services:
    chat:
      instances:
        - name: "stable-model-v1"
          baseUrl: "http://stable-server:11434"
          weight: 9  # 90% traffic
        - name: "new-model-v2"
          baseUrl: "http://new-server:11434"
          weight: 1  # 10% traffic
```

## Next Steps

- [Application Configuration](application-config.md) - Detailed application settings
- [Dynamic Configuration](dynamic-config.md) - Runtime configuration management
- [Load Balancing](load-balancing.md) - Load balancing strategies
- [Rate Limiting](rate-limiting.md) - Rate limiting algorithms
- [Circuit Breaker](circuit-breaker.md) - Circuit breaker configuration