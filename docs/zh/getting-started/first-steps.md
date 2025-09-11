# 第一步：深入配置

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



完成 [快速开始](quick-start.md) 后，本指南将帮助您深入了解 JAiRouter 的配置和使用，逐步构建生产就绪的 AI 服务网关。

## 🎯 学习路径

本指南按照实际使用场景组织，建议按顺序学习：

| 阶段 | 内容 | 时间 | 难度 |
|------|------|------|------|
| **基础配置** | 理解配置结构和服务类型 | 10分钟 | ⭐ |
| **负载均衡** | 配置多实例和负载策略 | 15分钟 | ⭐⭐ |
| **流量控制** | 设置限流和熔断保护 | 20分钟 | ⭐⭐⭐ |
| **监控运维** | 配置健康检查和监控 | 15分钟 | ⭐⭐ |
| **高级特性** | 动态配置和故障恢复 | 20分钟 | ⭐⭐⭐ |

## 📋 前提条件

- ✅ 已完成 [快速开始](quick-start.md) 指南
- ✅ JAiRouter 服务正在运行
- ✅ 至少配置了一个 AI 服务实例

## 🎯 学习目标

完成本指南后，您将能够：

- 🎯 配置多种 AI 服务类型（Chat、Embedding、TTS等）
- 🎯 实现智能负载均衡和流量分发
- 🎯 设置限流策略保护后端服务
- 🎯 配置熔断器防止服务雪崩
- 🎯 建立完整的监控和告警体系
- 🎯 掌握动态配置管理技巧

## 配置基础

### 配置方式

JAiRouter 支持两种配置方式：

1. **静态配置**：通过 `application.yml` 或 JSON 配置文件
2. **动态配置**：通过 REST API 运行时更新

### 配置优先级

| 优先级 | 配置来源 | 热更新 | 持久化 |
|--------|----------|--------|--------|
| 高 | 动态 API 配置 | ✅ | ✅ |
| 低 | 静态配置文件 | ❌ | ✅ |

## 配置 AI 服务

### 支持的服务类型

JAiRouter 支持以下 AI 服务类型：

| 服务类型 | 说明 | 示例模型 |
|----------|------|----------|
| `chat` | 对话聊天服务 | GPT-4, Llama, Qwen |
| `embedding` | 文本嵌入服务 | text-embedding-ada-002 |
| `rerank` | 文本重排序服务 | bge-reranker |
| `tts` | 文本转语音服务 | tts-1 |
| `stt` | 语音转文本服务 | whisper-1 |
| `image-generation` | 图像生成服务 | dall-e-3 |
| `image-editing` | 图像编辑服务 | dall-e-2 |

### 基本服务配置

创建 `config/model-router-config@1.json`：

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

### 实例配置参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `name` | String | ✅ | - | 模型名称，用于路由 |
| `baseUrl` | String | ✅ | - | 后端服务基础 URL |
| `path` | String | ✅ | - | API 路径 |
| `weight` | Integer | ❌ | 1 | 负载均衡权重 |
| `timeout` | Integer | ❌ | 30000 | 请求超时时间（毫秒） |
| `maxRetries` | Integer | ❌ | 3 | 最大重试次数 |
| `headers` | Object | ❌ | {} | 自定义请求头 |

## 配置负载均衡

### 负载均衡策略

JAiRouter 支持四种负载均衡策略：

#### 1. Random（随机）

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

**特点**：
- 随机选择可用实例
- 简单高效，适合实例性能相近的场景
- 长期来看请求分布均匀

#### 2. Round Robin（轮询）

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

**特点**：
- 按顺序轮流分配请求
- 保证每个实例都能获得请求
- 适合实例性能相近的场景

#### 3. Least Connections（最少连接）

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

**特点**：
- 选择当前连接数最少的实例
- 适合请求处理时间差异较大的场景
- 能够自动平衡负载

#### 4. IP Hash（IP 哈希）

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

**特点**：
- 基于客户端 IP 的一致性哈希
- 同一客户端总是路由到同一实例
- 适合需要会话保持的场景

### 权重配置

所有负载均衡策略都支持权重配置：

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

## 配置限流策略

### 限流算法

JAiRouter 支持四种限流算法：

#### 1. Token Bucket（令牌桶）

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

**参数说明**：
- `capacity`: 桶容量（最大令牌数）
- `refillRate`: 令牌补充速率（每秒）
- `clientIpEnable`: 是否启用基于客户端 IP 的独立限流

#### 2. Leaky Bucket（漏桶）

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

**参数说明**：
- `capacity`: 桶容量
- `leakRate`: 漏出速率（每秒）

#### 3. Sliding Window（滑动窗口）

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

**参数说明**：
- `windowSize`: 时间窗口大小（秒）
- `maxRequests`: 窗口内最大请求数

#### 4. Warm Up（预热）

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

**参数说明**：
- `capacity`: 最终容量
- `warmUpPeriod`: 预热时间（秒）
- `coldFactor`: 冷启动因子

## 配置熔断器

熔断器用于防止服务雪崩，当后端服务出现故障时自动熔断：

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

### 熔断器参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `failureThreshold` | Integer | 5 | 失败阈值，超过后熔断 |
| `recoveryTimeout` | Long | 60000 | 恢复检测间隔（毫秒） |
| `successThreshold` | Integer | 3 | 成功阈值，达到后关闭熔断 |
| `timeout` | Long | 30000 | 请求超时时间（毫秒） |

### 熔断器状态

- **CLOSED**：正常状态，请求正常通过
- **OPEN**：熔断状态，直接返回错误
- **HALF_OPEN**：半开状态，允许少量请求测试服务恢复

## 配置降级策略

当服务不可用时，可以配置降级策略：

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
                "content": "服务暂时不可用，请稍后重试。"
              }
            }
          ]
        }
      }
    }
  }
}
```

### 降级策略类型

- **default**：返回预设的默认响应
- **cache**：返回缓存的历史响应

## 配置健康检查

JAiRouter 会自动检查服务实例的健康状态：

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

### 健康检查参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | Boolean | true | 是否启用健康检查 |
| `interval` | Long | 30000 | 检查间隔（毫秒） |
| `timeout` | Long | 5000 | 检查超时（毫秒） |
| `healthPath` | String | /health | 健康检查路径 |

## 配置存储

JAiRouter 支持两种配置存储方式：

### 内存存储

```json
{
  "store": {
    "type": "memory"
  }
}
```

**特点**：
- 配置存储在内存中
- 重启后配置丢失
- 适合开发和测试环境

### 文件存储

```json
{
  "store": {
    "type": "file",
    "path": "config/"
  }
}
```

**特点**：
- 配置持久化到文件
- 支持配置文件自动合并
- 适合生产环境

## 多服务配置示例

这里是一个包含多种服务类型的完整配置示例：

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

## 动态配置管理

### 通过 API 管理配置

```bash
# 添加新实例
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "new-model",
    "baseUrl": "http://new-server:8080",
    "path": "/v1/chat/completions",
    "weight": 1
  }'

# 更新实例
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

# 删除实例
curl -X DELETE "http://localhost:8080/api/config/instance/del/chat?modelName=new-model&baseUrl=http://new-server:8080"

# 查看所有实例
curl "http://localhost:8080/api/config/instance/type/chat"
```

### 配置文件版本管理

JAiRouter 支持配置文件版本管理：

```bash
# 扫描配置文件版本
curl "http://localhost:8080/api/config/merge/scan"

# 预览合并结果
curl "http://localhost:8080/api/config/merge/preview"

# 执行配置合并
curl -X POST "http://localhost:8080/api/config/merge/execute"
```

## 监控和日志

### 启用监控

JAiRouter 内置了 Prometheus 指标支持：

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

### 查看指标

```bash
# 查看所有指标
curl "http://localhost:8080/actuator/metrics"

# 查看 HTTP 请求指标
curl "http://localhost:8080/actuator/metrics/http.server.requests"

# 查看 Prometheus 格式指标
curl "http://localhost:8080/actuator/prometheus"
```

### 日志配置

JAiRouter 支持多环境日志配置：

```yaml
# application-dev.yml（开发环境）
logging:
  level:
    org.unreal.modelrouter: DEBUG
    org.springframework: INFO

# application-prod.yml（生产环境）
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
  file:
    name: logs/jairouter.log
```

## 下一步

完成第一步配置后，您可以：

1. **[配置指南](../configuration/index.md)** - 深入了解所有配置选项
2. **[API 参考](../api-reference/index.md)** - 查看完整的 API 文档
3. **[部署指南](../deployment/index.md)** - 了解生产环境部署
4. **[监控指南](../monitoring/index.md)** - 设置监控和告警

## 常见问题

### Q: 如何选择合适的负载均衡策略？

**A:** 根据您的场景选择：
- **性能相近的实例**：使用 Random 或 Round Robin
- **性能差异较大**：使用 Least Connections
- **需要会话保持**：使用 IP Hash

### Q: 如何选择合适的限流算法？

**A:** 根据流量特征选择：
- **平稳流量**：使用 Token Bucket
- **突发流量**：使用 Leaky Bucket
- **精确控制**：使用 Sliding Window
- **冷启动场景**：使用 Warm Up

### Q: 配置更新后何时生效？

**A:** 
- **动态 API 配置**：立即生效
- **配置文件更新**：需要重启服务

### Q: 如何备份配置？

**A:** 使用配置管理 API：

```bash
# 备份当前配置
curl -X POST "http://localhost:8080/api/config/merge/backup"
```

配置文件会备份到 `config/backup_<timestamp>/` 目录。

## 🎉 完成第一步配置！

恭喜您完成了 JAiRouter 的深入配置学习！现在您已经掌握了：

### ✅ 已掌握的技能

- 🎯 **多服务配置**: 配置 Chat、Embedding、TTS 等多种 AI 服务
- ⚖️ **负载均衡**: 掌握四种负载均衡策略的使用场景
- 🛡️ **流量控制**: 配置限流、熔断、降级保护机制
- 📊 **监控运维**: 设置健康检查和监控指标
- 🔧 **动态管理**: 使用 API 进行运行时配置更新

### 🚀 下一步建议

根据您的需求选择下一步：

| 目标 | 推荐文档 | 说明 |
|------|----------|------|
| **生产环境部署** | [部署指南](../deployment/index.md) | Docker、Kubernetes 部署 |
| **API 集成开发** | [API 参考](../api-reference/index.md) | 完整 API 文档和示例 |
| **监控告警** | [监控指南](../monitoring/index.md) | Prometheus、Grafana 集成 |
| **故障排查** | [故障排查](../troubleshooting/index.md) | 常见问题和解决方案 |
| **高级配置** | [配置指南](../configuration/index.md) | 详细配置参数说明 |

### 💡 持续学习

- 📖 定期查看 [更新日志](../reference/changelog.md) 了解新特性
- 🐛 遇到问题时查看 [FAQ](../reference/faq.md)
- 💬 参与 [GitHub 讨论](https://github.com/Lincoln-cn/JAiRouter/discussions)

### 🎯 实践建议

1. **从小规模开始**: 先在开发环境验证配置
2. **逐步扩展**: 逐个添加服务类型和实例
3. **监控优先**: 及早建立监控和告警机制
4. **文档记录**: 记录您的配置决策和变更历史

准备好进入生产环境了吗？让我们继续 **[部署指南](../deployment/index.md)**！