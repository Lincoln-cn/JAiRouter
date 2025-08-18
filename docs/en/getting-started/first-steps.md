# First Steps

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


After completing the [Quick Start](quick-start.md), this guide will help you understand JAiRouter's core concepts and configure it for your specific needs.

## Core Concepts

### 1. Service Types

JAiRouter supports multiple AI service types:

| Service Type | Description | API Endpoint |
|--------------|-------------|--------------|
| **chat** | Chat completions | `/v1/chat/completions` |
| **embedding** | Text embeddings | `/v1/embeddings` |
| **rerank** | Text reranking | `/v1/rerank` |
| **tts** | Text-to-speech | `/v1/audio/speech` |
| **stt** | Speech-to-text | `/v1/audio/transcriptions` |
| **image** | Image generation | `/v1/images/generations` |

### 2. Service Instances

Each service type can have multiple instances for load balancing:

```yaml
model:
  services:
    chat:
      instances:
        - name: "qwen2.5:7b"
          baseUrl: "http://server1:11434"
          path: "/v1/chat/completions"
          weight: 2
        - name: "qwen2.5:14b"
          baseUrl: "http://server2:11434"
          path: "/v1/chat/completions"
          weight: 1
```

### 3. Load Balancing Strategies

Choose how requests are distributed:

- **random**: Random selection
- **round-robin**: Sequential rotation
- **least-connections**: Route to least busy instance
- **ip-hash**: Consistent routing based on client IP

### 4. Rate Limiting

Control request rates per client or globally:

- **token-bucket**: Allow bursts up to bucket capacity
- **leaky-bucket**: Smooth, constant rate
- **sliding-window**: Rate limit over time windows

## Basic Configuration

### Minimal Configuration

Start with a simple configuration:

```yaml
server:
  port: 8080

model:
  services:
    chat:
      instances:
        - name: "default-model"
          baseUrl: "http://localhost:11434"
          path: "/v1/chat/completions"
```

### Adding Load Balancing

Configure multiple instances with load balancing:

```yaml
model:
  services:
    chat:
      load-balance:
        type: round-robin
      instances:
        - name: "fast-model"
          baseUrl: "http://fast-server:11434"
          weight: 3
        - name: "accurate-model"
          baseUrl: "http://accurate-server:11434"
          weight: 1
```

### Adding Rate Limiting

Protect your services with rate limiting:

```yaml
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100
        refill-rate: 10
        client-ip-enable: true
      instances:
        - name: "protected-model"
          baseUrl: "http://localhost:11434"
```

### Adding Circuit Breaking

Prevent cascading failures:

```yaml
model:
  services:
    chat:
      circuit-breaker:
        enabled: true
        failure-threshold: 5
        recovery-timeout: 30000
        success-threshold: 3
      fallback:
        type: default
        message: "Service temporarily unavailable"
      instances:
        - name: "reliable-model"
          baseUrl: "http://localhost:11434"
```

## Configuration Management

### Static Configuration

Define services in `application.yml`:

```yaml
model:
  services:
    chat:
      # Configuration here
    embedding:
      # Configuration here
```

### Dynamic Configuration

Add, update, or remove instances at runtime:

```bash
# Add a new instance
curl -X POST http://localhost:8080/api/config/instance/add/chat \
  -H "Content-Type: application/json" \
  -d '{
    "name": "new-model",
    "baseUrl": "http://new-server:11434",
    "path": "/v1/chat/completions",
    "weight": 1
  }'

# Update an instance
curl -X PUT http://localhost:8080/api/config/instance/update/chat \
  -H "Content-Type: application/json" \
  -d '{
    "instanceId": "new-model@http://new-server:11434",
    "instance": {
      "name": "new-model",
      "baseUrl": "http://updated-server:11434",
      "path": "/v1/chat/completions",
      "weight": 2
    }
  }'

# Remove an instance
curl -X DELETE "http://localhost:8080/api/config/instance/del/chat?modelName=new-model&baseUrl=http://updated-server:11434"
```

## Health Monitoring

### Automatic Health Checks

JAiRouter automatically monitors service health:

```yaml
model:
  services:
    chat:
      health-check:
        enabled: true
        interval: 30000  # 30 seconds
        timeout: 5000    # 5 seconds
        path: "/health"  # Health check endpoint
```

### Manual Health Check

Check service status manually:

```bash
# Check overall health
curl http://localhost:8080/actuator/health

# Check specific service instances
curl http://localhost:8080/api/config/instance/type/chat
```

## Monitoring and Observability

### Metrics

JAiRouter exposes metrics for monitoring:

```bash
# View all metrics
curl http://localhost:8080/actuator/metrics

# View specific metrics
curl http://localhost:8080/actuator/metrics/http.server.requests
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

### Logging

Configure logging levels:

```yaml
logging:
  level:
    org.unreal.modelrouter: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
  file:
    name: logs/jairouter.log
```

## Testing Your Configuration

### 1. Validate Configuration

Test your configuration before deploying:

```bash
# Check configuration syntax
java -jar jairouter.jar --spring.config.location=file:./application.yml --spring.profiles.active=validate
```

### 2. Load Testing

Use tools like Apache Bench or curl to test load balancing:

```bash
# Simple load test
for i in {1..10}; do
  curl -X POST http://localhost:8080/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d '{"model": "test", "messages": [{"role": "user", "content": "test"}]}' &
done
wait
```

### 3. Circuit Breaker Testing

Test circuit breaker behavior:

```bash
# Stop backend service to trigger circuit breaker
# Then make requests to see fallback responses
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model": "test", "messages": [{"role": "user", "content": "test"}]}'
```

## Common Patterns

### 1. Multi-Model Setup

Configure different models for different use cases:

```yaml
model:
  services:
    chat:
      instances:
        - name: "fast-chat"
          baseUrl: "http://fast-server:11434"
          weight: 3
        - name: "smart-chat"
          baseUrl: "http://smart-server:11434"
          weight: 1
    embedding:
      instances:
        - name: "embedding-model"
          baseUrl: "http://embedding-server:11434"
```

### 2. Environment-Specific Configuration

Use Spring profiles for different environments:

```yaml
# application-dev.yml
model:
  services:
    chat:
      instances:
        - name: "dev-model"
          baseUrl: "http://localhost:11434"

---
# application-prod.yml
model:
  services:
    chat:
      load-balance:
        type: least-connections
      rate-limit:
        type: token-bucket
        capacity: 1000
        refill-rate: 100
      instances:
        - name: "prod-model-1"
          baseUrl: "http://prod-server-1:11434"
        - name: "prod-model-2"
          baseUrl: "http://prod-server-2:11434"
```

### 3. Gradual Rollout

Use weights for gradual model rollouts:

```yaml
model:
  services:
    chat:
      instances:
        - name: "stable-model"
          baseUrl: "http://stable-server:11434"
          weight: 9  # 90% of traffic
        - name: "new-model"
          baseUrl: "http://new-server:11434"
          weight: 1  # 10% of traffic
```

## Next Steps

Now that you understand the basics, explore more advanced topics:

1. **[Configuration Guide](../configuration/index.md)** - Detailed configuration options
2. **[API Reference](../api-reference/index.md)** - Complete API documentation
3. **[Deployment Guide](../deployment/index.md)** - Production deployment strategies
4. **[Monitoring Guide](../monitoring/index.md)** - Set up comprehensive monitoring

## Troubleshooting

### Common Issues

**No Available Instances**:
- Check if backend services are running
- Verify network connectivity
- Check health check configuration

**Rate Limit Exceeded**:
- Adjust rate limit settings
- Check if client IP rate limiting is appropriate
- Monitor request patterns

**Circuit Breaker Open**:
- Check backend service health
- Review failure threshold settings
- Monitor error rates

For more detailed troubleshooting, see the [Troubleshooting Guide](../troubleshooting/index.md).