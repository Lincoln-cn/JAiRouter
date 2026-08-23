# First Steps: Deep Dive into Configuration

<!-- 版本信息 -->
> **Doc Version**: 1.0.2
> **Last Updated**: 2026-05-21
> **Git Commit**: 61384b4a
> **Author**: Lincoln
<!-- /版本信息 -->



After completing the [Quick Start](quick-start.md), this guide will help you dive deeper into JAiRouter's configuration and usage, building a production-ready AI service gateway step by step.

## 🎯 Learning Path

This guide is organized by real-world usage scenarios. It is recommended to follow it in order:

| Stage | Content | Time | Difficulty |
|-------|---------|------|------------|
| **Basic Configuration** | Understand the configuration structure and service types | 10 minutes | ⭐ |
| **Load Balancing** | Configure multiple instances and load balancing strategies | 15 minutes | ⭐⭐ |
| **Traffic Control** | Set up rate limiting and circuit breaker protection | 20 minutes | ⭐⭐⭐ |
| **Monitoring & Operations** | Configure health checks and monitoring | 15 minutes | ⭐⭐ |
| **Advanced Features** | Dynamic configuration and failure recovery | 20 minutes | ⭐⭐⭐ |

## 📋 Prerequisites

- ✅ Completed the [Quick Start](quick-start.md) guide
- ✅ JAiRouter is running
- ✅ At least one AI service instance has been configured

## 🎯 Learning Objectives

After completing this guide, you will be able to:

- 🎯 Configure multiple types of AI services (Chat, Embedding, TTS, etc.)
- 🎯 Implement intelligent load balancing and traffic distribution
- 🎯 Set up rate limiting policies to protect backend services
- 🎯 Configure circuit breakers to prevent service avalanche
- 🎯 Build a complete monitoring and alerting system
- 🎯 Master dynamic configuration management techniques

## Configuration Basics

### Configuration Methods

JAiRouter supports two configuration methods:

1. **Static configuration**: via `application.yml` or JSON configuration files
2. **Dynamic configuration**: runtime updates via the REST API

### Configuration Priority

| Priority | Configuration Source | Hot Reload | Persistence |
|----------|----------------------|------------|-------------|
| High | Dynamic API configuration | ✅ | ✅ |
| Low | Static configuration files | ❌ | ✅ |

## Configuring AI Services

### Supported Service Types

JAiRouter supports the following AI service types:

| Service Type | Description | Example Models |
|--------------|-------------|----------------|
| `chat` | Chat conversation service | GPT-4, Llama, Qwen |
| `embedding` | Text embedding service | text-embedding-ada-002 |
| `rerank` | Text reranking service | bge-reranker |
| `tts` | Text-to-speech service | tts-1 |
| `stt` | Speech-to-text service | whisper-1 |
| `image-generation` | Image generation service | dall-e-3 |
| `image-editing` | Image editing service | dall-e-2 |

### Basic Service Configuration

Create `config/model-router-config@1.json`:

```json
{
  "services": {
    "chat": {
      "instances": [
        {
          "name": "llama3.2:3b",
          "baseUrl": "http://localhost:11434",
          "path": "/v1/chat/completions",
          "weight": 1,
          "timeout": 30000,
          "maxRetries": 3
        }
      ]
    }
  }
}
```

### Instance Configuration Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `name` | String | ✅ | - | Model name, used for routing |
| `baseUrl` | String | ✅ | - | Base URL of the backend service |
| `path` | String | ✅ | - | API path |
| `weight` | Integer | ❌ | 1 | Load balancing weight |
| `timeout` | Integer | ❌ | 30000 | Request timeout (milliseconds) |
| `maxRetries` | Integer | ❌ | 3 | Maximum number of retries |
| `headers` | Object | ❌ | {} | Custom request headers |

## Configuring Load Balancing

### Load Balancing Strategies

JAiRouter supports four load balancing strategies:

#### 1. Random

```json
{
  "services": {
    "chat": {
      "loadBalance": {
        "type": "random"
      }
    }
  }
}
```

**Features**:
- Randomly selects an available instance
- Simple and efficient, suitable for scenarios where instances have similar performance
- Requests are evenly distributed over the long run

#### 2. Round Robin

```json
{
  "services": {
    "chat": {
      "loadBalance": {
        "type": "round-robin"
      }
    }
  }
}
```

**Features**:
- Distributes requests sequentially in turn
- Guarantees that every instance receives requests
- Suitable for scenarios where instances have similar performance

#### 3. Least Connections

```json
{
  "services": {
    "chat": {
      "loadBalance": {
        "type": "least-connections"
      }
    }
  }
}
```

**Features**:
- Selects the instance with the fewest current connections
- Suitable for scenarios where request processing times vary greatly
- Automatically balances the load

#### 4. IP Hash

```json
{
  "services": {
    "chat": {
      "loadBalance": {
        "type": "ip-hash"
      }
    }
  }
}
```

**Features**:
- Consistent hashing based on the client IP
- The same client is always routed to the same instance
- Suitable for scenarios that require session stickiness

### Weight Configuration

All load balancing strategies support weight configuration:

```json
{
  "services": {
    "chat": {
      "instances": [
        {
          "name": "high-performance-model",
          "baseUrl": "http://gpu-server:8080",
          "weight": 3
        },
        {
          "name": "standard-model",
          "baseUrl": "http://cpu-server:8080",
          "weight": 1
        }
      ],
      "loadBalance": {
        "type": "round-robin"
      }
    }
  }
}
```

## Configuring Rate Limiting

### Rate Limiting Algorithms

JAiRouter supports four rate limiting algorithms:

#### 1. Token Bucket

```json
{
  "services": {
    "chat": {
      "rateLimit": {
        "type": "token-bucket",
        "capacity": 100,
        "refillRate": 10,
        "clientIpEnable": true
      }
    }
  }
}
```

**Parameter descriptions**:
- `capacity`: Bucket capacity (maximum number of tokens)
- `refillRate`: Token refill rate (per second)
- `clientIpEnable`: Whether to enable independent rate limiting based on client IP

#### 2. Leaky Bucket

```json
{
  "services": {
    "chat": {
      "rateLimit": {
        "type": "leaky-bucket",
        "capacity": 50,
        "leakRate": 5
      }
    }
  }
}
```

**Parameter descriptions**:
- `capacity`: Bucket capacity
- `leakRate`: Leak rate (per second)

#### 3. Sliding Window

```json
{
  "services": {
    "chat": {
      "rateLimit": {
        "type": "sliding-window",
        "windowSize": 60,
        "maxRequests": 100
      }
    }
  }
}
```

**Parameter descriptions**:
- `windowSize`: Time window size (seconds)
- `maxRequests`: Maximum number of requests within the window

#### 4. Warm Up

```json
{
  "services": {
    "chat": {
      "rateLimit": {
        "type": "warm-up",
        "capacity": 100,
        "warmUpPeriod": 300,
        "coldFactor": 3
      }
    }
  }
}
```

**Parameter descriptions**:
- `capacity`: Final capacity
- `warmUpPeriod`: Warm-up time (seconds)
- `coldFactor`: Cold start factor

## Configuring the Circuit Breaker

The circuit breaker is used to prevent service avalanche. When a backend service fails, the circuit breaker trips automatically:

```json
{
  "services": {
    "chat": {
      "circuitBreaker": {
        "failureThreshold": 5,
        "recoveryTimeout": 60000,
        "successThreshold": 3,
        "timeout": 30000
      }
    }
  }
}
```

### Circuit Breaker Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `failureThreshold` | Integer | 5 | Failure threshold; the breaker trips when exceeded |
| `recoveryTimeout` | Long | 60000 | Recovery detection interval (milliseconds) |
| `successThreshold` | Integer | 3 | Success threshold; the breaker closes when reached |
| `timeout` | Long | 30000 | Request timeout (milliseconds) |

### Circuit Breaker States

- **CLOSED**: Normal state, requests pass through normally
- **OPEN**: Tripped state, errors are returned directly
- **HALF_OPEN**: Half-open state, allows a small number of requests to test service recovery

## Configuring Fallback Strategies

When a service is unavailable, you can configure a fallback strategy:

```json
{
  "services": {
    "chat": {
      "fallback": {
        "type": "default",
        "response": {
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "The service is temporarily unavailable. Please try again later."
              }
            }
          ]
        }
      }
    }
  }
}
```

### Fallback Strategy Types

- **default**: Returns a preset default response
- **cache**: Returns cached historical responses

## Configuring Health Checks

JAiRouter automatically checks the health status of service instances:

```json
{
  "checker": {
    "enabled": true,
    "interval": 30000,
    "timeout": 5000,
    "healthPath": "/health"
  }
}
```

### Health Check Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | Boolean | true | Whether health checks are enabled |
| `interval` | Long | 30000 | Check interval (milliseconds) |
| `timeout` | Long | 5000 | Check timeout (milliseconds) |
| `healthPath` | String | /health | Health check path |

## Configuring Storage

JAiRouter supports two configuration storage methods:

### In-Memory Storage

```json
{
  "store": {
    "type": "memory"
  }
}
```

**Features**:
- Configuration is stored in memory
- Configuration is lost after restart
- Suitable for development and testing environments

### File Storage

```json
{
  "store": {
    "type": "file",
    "path": "config/"
  }
}
```

**Features**:
- Configuration is persisted to files
- Supports automatic merging of configuration files
- Suitable for production environments

## Multi-Service Configuration Example

Here is a complete configuration example that includes multiple service types:

```json
{
  "services": {
    "chat": {
      "instances": [
        {
          "name": "llama3.2:3b",
          "baseUrl": "http://ollama:11434",
          "path": "/v1/chat/completions",
          "weight": 1
        },
        {
          "name": "qwen2:7b",
          "baseUrl": "http://ollama:11434",
          "path": "/v1/chat/completions",
          "weight": 2
        }
      ],
      "loadBalance": {
        "type": "round-robin"
      },
      "rateLimit": {
        "type": "token-bucket",
        "capacity": 100,
        "refillRate": 10,
        "clientIpEnable": true
      },
      "circuitBreaker": {
        "failureThreshold": 5,
        "recoveryTimeout": 60000,
        "successThreshold": 3
      }
    },
    "embedding": {
      "instances": [
        {
          "name": "nomic-embed-text",
          "baseUrl": "http://ollama:11434",
          "path": "/v1/embeddings",
          "weight": 1
        }
      ],
      "loadBalance": {
        "type": "random"
      },
      "rateLimit": {
        "type": "sliding-window",
        "windowSize": 60,
        "maxRequests": 200
      }
    },
    "tts": {
      "instances": [
        {
          "name": "tts-1",
          "baseUrl": "http://openai-api:8080",
          "path": "/v1/audio/speech",
          "weight": 1,
          "headers": {
            "Authorization": "Bearer your-api-key"
          }
        }
      ]
    }
  },
  "store": {
    "type": "file",
    "path": "config/"
  },
  "checker": {
    "enabled": true,
    "interval": 30000
  }
}
```

## Dynamic Configuration Management

### Managing Configuration via the API

```bash
# Add a new instance
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "new-model",
    "baseUrl": "http://new-server:8080",
    "path": "/v1/chat/completions",
    "weight": 1
  }'

# Update an instance
curl -X PUT "http://localhost:8080/api/config/instance/update/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "instanceId": "new-model@http://new-server:8080",
    "instance": {
      "name": "new-model",
      "baseUrl": "http://new-server:8080",
      "path": "/v1/chat/completions",
      "weight": 2
    }
  }'

# Delete an instance
curl -X DELETE "http://localhost:8080/api/config/instance/del/chat?modelName=new-model&baseUrl=http://new-server:8080"

# View all instances
curl "http://localhost:8080/api/config/instance/type/chat"
```

### Configuration File Version Management

JAiRouter supports configuration file version management. Through the version management API, you can view historical versions and roll back configurations:

```bash
# Get information about all versions
curl "http://localhost:8080/api/config/versions"

# Get the configuration of a specific version
curl "http://localhost:8080/api/config/versions/1"

# Roll back to a specific version
curl -X POST "http://localhost:8080/api/config/versions/1/apply"
```

## Monitoring and Logging

### Enabling Monitoring

JAiRouter has built-in Prometheus metrics support:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### Viewing Metrics

```bash
# View all metrics
curl "http://localhost:8080/actuator/metrics"

# View HTTP request metrics
curl "http://localhost:8080/actuator/metrics/http.server.requests"

# View metrics in Prometheus format
curl "http://localhost:8080/actuator/prometheus"
```

### Logging Configuration

JAiRouter supports multi-environment logging configuration:

```yaml
# application-dev.yml (development environment)
logging:
  level:
    org.unreal.modelrouter: DEBUG
    org.springframework: INFO

# application-prod.yml (production environment)
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
  file:
    name: logs/jairouter.log
```

## Next Steps

After completing the first step of configuration, you can:

1. **[Configuration Guide](../configuration/index.md)** - Dive into all configuration options
2. **[API Reference](../api-reference/index.md)** - View the complete API documentation
3. **[Deployment Guide](../deployment/index.md)** - Learn about production deployment
4. **[Monitoring Guide](../monitoring/index.md)** - Set up monitoring and alerting

## FAQ

### Q: How do I choose the right load balancing strategy?

**A:** Choose based on your scenario:
- **Instances with similar performance**: Use Random or Round Robin
- **Large performance differences**: Use Least Connections
- **Session stickiness required**: Use IP Hash

### Q: How do I choose the right rate limiting algorithm?

**A:** Choose based on traffic characteristics:
- **Steady traffic**: Use Token Bucket
- **Bursty traffic**: Use Leaky Bucket
- **Precise control**: Use Sliding Window
- **Cold start scenarios**: Use Warm Up

### Q: When do configuration updates take effect?

**A:** 
- **Dynamic API configuration**: takes effect immediately
- **Configuration file updates**: require a service restart

### Q: How do I back up my configuration?

**A:** Use the configuration management API:

```bash
# Back up the current configuration
curl -X POST "http://localhost:8080/api/config/merge/backup"
```

Configuration files are backed up to the `config/backup_<timestamp>/` directory.

## 🎉 Complete the First Step of Configuration!

Congratulations on completing your in-depth JAiRouter configuration learning! You have now mastered:

### ✅ Skills Acquired

- 🎯 **Multi-service configuration**: Configure multiple AI services such as Chat, Embedding, and TTS
- ⚖️ **Load balancing**: Master the use cases of the four load balancing strategies
- 🛡️ **Traffic control**: Configure rate limiting, circuit breaking, and fallback protection mechanisms
- 📊 **Monitoring & operations**: Set up health checks and monitoring metrics
- 🔧 **Dynamic management**: Use the API for runtime configuration updates

### 🚀 Next Step Suggestions

Choose your next step based on your needs:

| Goal | Recommended Document | Description |
|------|----------------------|-------------|
| **Production deployment** | [Deployment Guide](../deployment/index.md) | Docker, Kubernetes deployment |
| **API integration development** | [API Reference](../api-reference/index.md) | Complete API documentation and examples |
| **Monitoring and alerting** | [Monitoring Guide](../monitoring/index.md) | Prometheus, Grafana integration |
| **Troubleshooting** | [Troubleshooting](../troubleshooting/index.md) | Common issues and solutions |
| **Advanced configuration** | [Configuration Guide](../configuration/index.md) | Detailed configuration parameter descriptions |

### 💡 Continuous Learning

- 📖 Regularly check the [Changelog](../reference/changelog.md) for new features
- 🐛 When you encounter issues, check the [FAQ](../reference/faq.md)
- 💬 Join the [GitHub Discussions](https://github.com/Lincoln-cn/JAiRouter/discussions)

### 🎯 Practice Suggestions

1. **Start small**: Validate your configuration in a development environment first
2. **Expand gradually**: Add service types and instances one at a time
3. **Monitor first**: Establish monitoring and alerting mechanisms early
4. **Document everything**: Record your configuration decisions and change history

Ready to go to production? Let's continue with the **[Deployment Guide](../deployment/index.md)**!

## FAQ {#faq}

This section summarizes common issues users encounter during the first step of configuration.

### Configuration-Related

#### 1. Configuration File Format Errors

**Problem**: YAML or JSON parsing errors are reported at startup

**Solutions**:
- Use a YAML/JSON validation tool to check the syntax
- Ensure correct indentation (YAML)
- Check quote matching (JSON)

#### 2. Configuration Not Taking Effect

**Problem**: Changes to the configuration have no effect

**Solutions**:
- Dynamic configuration takes effect immediately
- Static configuration requires a service restart
- Check whether the configuration file path is correct

### Load Balancing-Related

#### 3. Unbalanced Load

**Problem**: Some instances receive too many requests while others receive too few

**Solutions**:
- Check whether the weight configuration is reasonable
- Confirm whether health checks are working properly
- Consider using the Least Connections strategy

### Rate Limiting and Circuit Breaker-Related

#### 4. Frequent Rate Limiting Triggers

**Problem**: Normal traffic is being rate limited

**Solutions**:
- Adjust the rate limiting thresholds
- Check whether client IP rate limiting is enabled
- Consider using the sliding window algorithm

#### 5. Circuit Breaker Tripping Frequently

**Problem**: The circuit breaker trips even though the service is healthy

**Solutions**:
- Adjust the failureThreshold threshold
- Check the backend service response time
- Increase the timeout setting

### Monitoring-Related

#### 6. Missing Monitoring Metrics

**Problem**: Prometheus cannot scrape metrics

**Solutions**:
- Check the management endpoint configuration
- Confirm network connectivity
- Check whether `/actuator/prometheus` returns data normally
