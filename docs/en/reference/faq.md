# Frequently Asked Questions (FAQ)

This document collects the most common questions and answers encountered by users when using JAiRouter.

## Basic Questions

### Q1: What is JAiRouter?

**A**: JAiRouter is an AI model service routing and load balancing gateway based on Spring Boot. It provides a unified OpenAI-compatible API interface, supporting multiple backend AI services including GPUStack, Ollama, VLLM, and OpenAI. Key features include load balancing, rate limiting, circuit breaking, health checks, and dynamic configuration management.

### Q2: Which AI services does JAiRouter support?

**A**: Currently supported AI services include:
- **GPUStack**: GPU cluster management platform
- **Ollama**: Local large language model runtime platform
- **VLLM**: High-performance LLM inference engine
- **Xinference**: Model inference service framework
- **LocalAI**: Local AI model service
- **OpenAI**: Official OpenAI API

Future support will include Anthropic Claude, Google Gemini, Cohere, and more services.

### Q3: What distinguishes JAiRouter from other API gateways?

**A**: JAiRouter is specifically designed for AI model services with the following features:
- **AI-Specific**: Optimized for the special needs of AI model services
- **OpenAI Compatible**: Provides standard OpenAI API format
- **Intelligent Routing**: Supports multiple load balancing strategies
- **Model-Aware**: Understands different AI models' characteristics and capabilities
- **Cost Optimization**: Supports cost-based routing strategies
- **Easy to Use**: Simple configuration designed specifically for AI developers

### Q4: Is JAiRouter free?

**A**: Yes, JAiRouter is a completely open-source and free project under the MIT License. You can freely use, modify, and distribute it. We are also considering providing enterprise-level commercial support services.

## Installation and Deployment

### Q5: What are the system requirements for JAiRouter?

**A**: Minimum system requirements:
- **Java**: Version 17 or higher
- **Memory**: Minimum 512MB, recommended 2GB+
- **CPU**: Minimum 1 core, recommended 2 cores+
- **Storage**: Minimum 1GB available space
- **Operating System**: Supports Linux, Windows, macOS

### Q6: How to quickly get started with JAiRouter?

**A**: The simplest way is to use Docker:

```bash
# Pull the image
docker pull jairouter/model-router:latest

# Start the service
docker run -d -p 8080:8080 \
  -v ./config:/app/config \
  jairouter/model-router:latest
```

Or build from source:

```bash
# Clone the project
git clone https://github.com/Lincoln-cn/JAiRouter.git
cd jairouter

# Build and run
./mvnw clean package
java -jar target/model-router-*.jar
```

### Q7: How to configure backend AI services?

**A**: Configure in [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml):

```yaml
model:
  services:
    chat:
      instances:
        - name: "ollama-llama2"
          baseUrl: "http://localhost:11434"
          path: "/v1/chat/completions"
          weight: 1
        - name: "openai-gpt4"
          baseUrl: "https://api.openai.com"
          path: "/v1/chat/completions"
          weight: 2
```

### Q8: What deployment methods are supported?

**A**: JAiRouter supports multiple deployment methods:
- **Direct Run**: Running Java JAR package directly
- **Docker Container**: Single container deployment
- **Docker Compose**: Multi-container orchestration deployment
- **Kubernetes**: Cloud-native deployment (planned)
- **Cloud Platforms**: Deployment on AWS, Azure, GCP, and other cloud platforms

## Feature Usage

### Q9: How to configure load balancing strategies?

**A**: Specify the load balancing type in the configuration file:

```yaml
model:
  services:
    chat:
      load-balance:
        type: round-robin  # Options: random, round-robin, least-connections, ip-hash
```

Characteristics of each strategy:
- **random**: Random selection, simple and efficient
- **round-robin**: Round-robin scheduling, evenly distributed
- **least-connections**: Least connections, suitable for long connections
- **ip-hash**: IP hash, session persistence

### Q10: How to set rate limiting?

**A**: Configure rate limiting parameters:

```yaml
model:
  services:
    chat:
      rate-limit:
        type: token-bucket
        capacity: 100        # Bucket capacity
        refill-rate: 10      # Refill rate (per second)
        client-ip-enable: true  # Enable client IP independent rate limiting
```

Supported rate limiting algorithms:
- **token-bucket**: Token bucket, supports burst traffic
- **leaky-bucket**: Leaky bucket, smooth rate limiting
- **sliding-window**: Sliding window, precise control
- **warm-up**: Warm-up rate limiting, gradual increase

### Q11: How does the circuit breaker work?

**A**: Circuit breaker configuration example:

```yaml
model:
  services:
    chat:
      circuit-breaker:
        failure-threshold: 5      # Failure threshold
        recovery-timeout: 30s     # Recovery timeout
        success-threshold: 3      # Success threshold
```

The circuit breaker has three states:
- **CLOSED**: Normal state, requests pass through normally
- **OPEN**: Circuit breaker state, returns error directly
- **HALF_OPEN**: Half-open state, attempting recovery

### Q12: How to dynamically update configuration?

**A**: Use REST API for dynamic updates:

```bash
# Add instance
curl -X POST http://localhost:8080/api/config/instance/add/chat \
  -H "Content-Type: application/json" \
  -d '{
    "name": "new-model",
    "baseUrl": "http://new-server:9090",
    "path": "/v1/chat/completions",
    "weight": 1
  }'

# Update instance
curl -X PUT http://localhost:8080/api/config/instance/update/chat \
  -H "Content-Type: application/json" \
  -d '{
    "instanceId": "new-model@http://new-server:9090",
    "instance": {
      "name": "new-model",
      "baseUrl": "http://new-server:9090",
      "path": "/v1/chat/completions",
      "weight": 2
    }
  }'
```

## Monitoring and Operations

### Q13: How to monitor JAiRouter's running status?

**A**: JAiRouter provides multiple monitoring methods:

**Health Check**:
```bash
curl http://localhost:8080/actuator/health
```

**Prometheus Metrics**:
```bash
curl http://localhost:8080/actuator/prometheus
```

**Key Metrics**:
- Total requests and success rate
- Response time distribution
- Rate limiting and circuit breaker statistics
- JVM memory and GC metrics
- System CPU and memory usage

### Q14: How to view logs?

**A**: Log file locations:
- **Container Deployment**: `/app/logs/jairouter-debug.log`
- **Direct Run**: `./logs/jairouter-debug.log`

View real-time logs:
```bash
# Docker container
docker logs -f jairouter

# Direct run
tail -f logs/jairouter-debug.log
```

Adjust log level:
```bash
curl -X POST http://localhost:8080/actuator/loggers/org.unreal.modelrouter \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

### Q15: How to backup and restore configuration?

**A**: Configuration file backup:

```bash
# Backup configuration directory
cp -r config/ config-backup-$(date +%Y%m%d)

# Backup using API
curl -X POST http://localhost:8080/api/config/merge/backup
```

Restore configuration:
```bash
# Restore configuration files
cp -r config-backup-20250115/ config/

# Restart service to apply configuration
docker restart jairouter
```

## Performance and Optimization

### Q16: How is JAiRouter's performance?

**A**: Performance metrics (based on standard test environment):
- **Throughput**: 1000+ RPS
- **Latency**: P95 < 100ms
- **Concurrency**: Supports 1000+ concurrent connections
- **Memory**: Base runtime approximately 200MB

Actual performance depends on:
- Hardware configuration
- Backend service performance
- Network latency
- Configuration parameters

### Q17: How to optimize performance?

**A**: Performance optimization recommendations:

**JVM Tuning**:
```bash
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar target/model-router-*.jar
```

**Connection Pool Optimization**:
```yaml
spring:
  webflux:
    httpclient:
      pool:
        max-connections: 200
        max-idle-time: 30s
```

**Cache Configuration**:
```yaml
model:
  cache:
    enabled: true
    ttl: 300s
    max-size: 1000
```

### Q18: How to handle high-concurrency scenarios?

**A**: High-concurrency optimization strategies:

1. **Horizontal Scaling**: Deploy multiple JAiRouter instances
2. **Load Balancing**: Use Nginx or cloud load balancers
3. **Connection Pool**: Increase connection pool size
4. **Caching**: Enable response caching
5. **Rate Limiting**: Set rate limiting parameters appropriately
6. **Monitoring**: Real-time performance monitoring

## Troubleshooting

### Q19: What to do if service startup fails?

**A**: Common startup issues and solutions:

**Port Occupied**:
```bash
# Check port occupancy
netstat -tlnp | grep :8080
# Modify port or stop occupying process
```

**Configuration File Error**:
```bash
# Validate YAML format
./mvnw spring-boot:run --debug
```

**Java Version Incompatibility**:
```bash
# Check Java version
java -version
# Ensure using Java 17+
```

### Q20: What to do if backend service connection fails?

**A**: Connection issue troubleshooting steps:

1. **Check Service Status**:
```bash
curl -v http://backend-server:9090/health
```

2. **Check Network Connectivity**:
```bash
telnet backend-server 9090
```

3. **Check Configuration**:
```bash
curl http://localhost:8080/api/config/instance/type/chat
```

4. **View Logs**:
```bash
grep -i "connection\|timeout" logs/jairouter-debug.log
```

### Q21: How to troubleshoot performance issues?

**A**: Performance issue diagnosis:

**Check System Resources**:
```bash
# CPU usage
curl http://localhost:8080/actuator/metrics/system.cpu.usage

# Memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# GC status
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
```

**Analyze Response Time**:
```bash
# Response time distribution
curl http://localhost:8080/actuator/metrics/jairouter.request.duration

# Request statistics
curl http://localhost:8080/actuator/metrics/jairouter.requests.total
```

## Development and Integration

### Q22: How to integrate into existing projects?

**A**: Integration methods:

**As Proxy Service**:
```python
# Python example
import openai

# Point OpenAI client to JAiRouter
openai.api_base = "http://jairouter:8080/v1"
openai.api_key = "your-api-key"

response = openai.ChatCompletion.create(
    model="gpt-3.5-turbo",
    messages=[{"role": "user", "content": "Hello!"}]
)
```

**As Load Balancer**:
```javascript
// Node.js example
const axios = require('axios');

const response = await axios.post('http://jairouter:8080/v1/chat/completions', {
  model: 'gpt-3.5-turbo',
  messages: [{ role: 'user', content: 'Hello!' }]
}, {
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer your-api-key'
  }
});
```

### Q23: How to develop custom adapters?

**A**: Custom adapter development:

```java
@Component
public class CustomAdapter extends BaseAdapter {
    
    @Override
    public Mono<String> processRequest(String serviceType, String requestBody, ServiceInstance instance) {
        return webClient.post()
            .uri(instance.getBaseUrl() + instance.getPath())
            .bodyValue(transformRequest(requestBody))
            .retrieve()
            .bodyToMono(String.class)
            .map(this::transformResponse);
    }
    
    private String transformRequest(String requestBody) {
        // Request format transformation logic
        return requestBody;
    }
    
    private String transformResponse(String responseBody) {
        // Response format transformation logic
        return responseBody;
    }
}
```

### Q24: How to contribute code?

**A**: Contribution process:

1. **Fork Project**: Fork the project on GitHub
2. **Create Branch**: `git checkout -b feature/your-feature`
3. **Develop Feature**: Write code and tests
4. **Commit Code**: `git commit -m "feat: add new feature"`
5. **Push Branch**: `git push origin feature/your-feature`
6. **Create PR**: Create Pull Request on GitHub

For detailed information, please refer to the [Contribution Guide](../development/contributing.md).

## Commercial Use

### Q25: Can it be used commercially?

**A**: Yes, JAiRouter uses the MIT License, allowing commercial use. You can:
- Use in commercial projects
- Modify and customize features
- Redistribute (license must be retained)
- Provide commercial services based on JAiRouter

### Q26: Is technical support provided?

**A**: Currently provided support methods:
- **Community Support**: GitHub Issues and Discussions
- **Documentation Support**: Complete user and API documentation
- **Sample Code**: Rich usage examples

We are considering providing enterprise-level commercial technical support services.

### Q27: How to obtain enterprise-level features?

**A**: Enterprise-level feature planning:
- **Multi-tenant Support**: Tenant isolation and management
- **Advanced Security**: Enterprise-level authentication and authorization
- **Professional Monitoring**: Advanced monitoring and analysis features
- **Technical Support**: Professional technical support services

For enterprise-level requirements, please contact us to discuss custom development.

## Other Questions

### Q28: What is JAiRouter's future roadmap?

**A**: Please check our [Roadmap](roadmap.md), which mainly includes:
- Security and authentication system improvement
- Intelligent feature enhancement
- Cloud-native support
- Ecosystem construction

### Q29: How to participate in the community?

**A**: Participation methods:
- **GitHub**: Submit Issues, PRs, or participate in discussions
- **Documentation**: Help improve documentation and translation
- **Testing**: Participate in feature testing and feedback
- **Promotion**: Share usage experiences and cases

### Q30: How to seek help when encountering problems?

**A**: Help channels:
1. **Check Documentation**: First check relevant documentation and FAQ
2. **Search Issues**: Search for similar issues in GitHub Issues
3. **Submit Issue**: Create a new Issue with detailed problem description
4. **Community Discussion**: Seek help in GitHub Discussions
5. **Email Contact**: jairouter@example.com

---

## Issue Feedback

If your question is not answered in this FAQ, please feel free to provide feedback through the following methods:

- **GitHub Issues**: [Submit Issue](https://github.com/Lincoln-cn/JAiRouter/issues)
- **GitHub Discussions**: [Participate in Discussion](https://github.com/Lincoln-cn/JAiRouter/discussions)
- **Email Contact**: jairouter@example.com

We will continuously update the FAQ content to provide better support for users.

**Last Updated**: January 15, 2025
