# 管理 API 接口

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



JAiRouter 提供完整的管理 API 接口，用于动态配置管理、服务实例管理、监控配置等功能。所有管理接口都使用 `/api` 前缀。

## 服务类型管理

### 基础路径：`/api/config/type`

#### `GET /api/config/type`
获取当前系统的所有配置信息。

**响应示例：**
```json
{
  "success": true,
  "message": "获取配置成功",
  "data": {
    "chat": {
      "loadBalancer": "random",
      "rateLimit": {
        "algorithm": "token_bucket",
        "capacity": 100
      },
      "instances": [...]
    }
  }
}
```

#### `GET /api/config/type/services`
获取系统中所有可用的服务类型。

**响应示例：**
```json
{
  "success": true,
  "message": "获取服务类型成功",
  "data": ["chat", "embedding", "rerank", "tts", "stt", "imgGen", "imgEdit"]
}
```

#### `GET /api/config/type/services/{serviceType}`
获取指定服务类型的配置信息。

**路径参数：**
- `serviceType` (string): 服务类型，如 "chat"

**响应示例：**
```json
{
  "success": true,
  "message": "获取服务配置成功",
  "data": {
    "loadBalancer": "random",
    "rateLimit": {
      "algorithm": "token_bucket",
      "capacity": 100,
      "refillRate": 10
    },
    "circuitBreaker": {
      "enabled": true,
      "failureThreshold": 5,
      "recoveryTimeout": 30000
    },
    "instances": [...]
  }
}
```

#### `POST /api/config/type/services/{serviceType}`
创建新的服务类型配置。

**请求体示例：**
```json
{
  "loadBalancer": "round_robin",
  "rateLimit": {
    "algorithm": "leaky_bucket",
    "capacity": 50,
    "refillRate": 5
  },
  "circuitBreaker": {
    "enabled": true,
    "failureThreshold": 3,
    "recoveryTimeout": 60000
  }
}
```

#### `PUT /api/config/type/services/{serviceType}`
更新指定服务类型的配置。

#### `DELETE /api/config/type/services/{serviceType}`
删除指定的服务类型及其所有配置。

#### `GET /api/config/type/{serviceType}/models`
获取指定服务类型下的所有可用模型。

**响应示例：**
```json
{
  "success": true,
  "message": "获取模型列表成功",
  "data": ["qwen2:7b", "llama3:8b", "gpt-3.5-turbo"]
}
```

#### `POST /api/config/type/reset`
将系统配置重置为默认值。

## 服务实例管理

### 基础路径：`/api/config/instance`

#### `GET /api/config/instance/type/{serviceType}`
获取指定服务类型下的所有实例。

**响应示例：**
```json
{
  "success": true,
  "message": "获取实例列表成功",
  "data": [
    {
      "name": "qwen2:7b",
      "baseUrl": "http://localhost:8000",
      "apiKey": "sk-xxx",
      "weight": 1,
      "enabled": true,
      "adapter": "ollama"
    }
  ]
}
```

#### `GET /api/config/instance/info/{serviceType}`
获取指定实例的详细信息。

**查询参数：**
- `modelName` (string): 模型名称
- `baseUrl` (string): 基础URL

**响应示例：**
```json
{
  "success": true,
  "message": "获取实例信息成功",
  "data": {
    "name": "qwen2:7b",
    "baseUrl": "http://localhost:8000",
    "apiKey": "sk-xxx",
    "weight": 1,
    "enabled": true,
    "adapter": "ollama",
    "healthStatus": "healthy",
    "lastHealthCheck": "2025-01-15T10:30:00Z"
  }
}
```

#### `POST /api/config/instance/add/{serviceType}`
为指定服务类型添加新实例。

**请求体示例：**
```json
{
  "name": "qwen2:7b",
  "baseUrl": "http://localhost:8000",
  "apiKey": "sk-xxx",
  "weight": 1,
  "enabled": true,
  "adapter": "ollama"
}
```

#### `PUT /api/config/instance/update/{serviceType}`
更新指定服务实例的配置。

**请求体示例：**
```json
{
  "instanceId": "qwen2:7b@http://localhost:8000",
  "instance": {
    "name": "qwen2:7b",
    "baseUrl": "http://localhost:8000",
    "apiKey": "sk-new-key",
    "weight": 2,
    "enabled": true,
    "adapter": "ollama"
  }
}
```

#### `DELETE /api/config/instance/del/{serviceType}`
删除指定的服务实例。

**查询参数：**
- `modelName` (string): 模型名称
- `baseUrl` (string): 基础URL

## 监控管理

### 基础路径：`/api/monitoring`

#### `GET /api/monitoring/config`
获取当前监控配置。

**响应示例：**
```json
{
  "enabled": true,
  "prefix": "jairouter",
  "collectionInterval": "PT30S",
  "enabledCategories": ["request", "system", "custom"],
  "customTags": {
    "environment": "production",
    "version": "1.0.0"
  },
  "sampling": {
    "enabled": true,
    "rate": 0.1
  },
  "performance": {
    "batchSize": 100,
    "flushInterval": "PT10S"
  }
}
```

#### `PUT /api/monitoring/config/enabled`
更新监控启用状态。

**请求体示例：**
```json
{
  "enabled": true
}
```

#### `PUT /api/monitoring/config/prefix`
更新指标前缀。

**请求体示例：**
```json
{
  "prefix": "jairouter_v2"
}
```

#### `PUT /api/monitoring/config/collection-interval`
更新收集间隔。

**请求体示例：**
```json
{
  "interval": "PT60S"
}
```

#### `PUT /api/monitoring/config/categories`
更新启用的监控类别。

**请求体示例：**
```json
{
  "categories": ["request", "system"]
}
```

#### `PUT /api/monitoring/config/custom-tags`
更新自定义标签。

**请求体示例：**
```json
{
  "customTags": {
    "environment": "staging",
    "region": "us-west-2"
  }
}
```

#### `GET /api/monitoring/config/snapshot`
获取监控配置快照。

#### `GET /api/monitoring/health`
获取监控系统健康状态。

**响应示例：**
```json
{
  "status": "UP",
  "healthy": true,
  "details": {
    "metricsCollector": "UP",
    "prometheusRegistry": "UP",
    "configUpdater": "UP"
  }
}
```

#### `GET /api/monitoring/overview`
获取监控系统整体状态概览。

**响应示例：**
```json
{
  "enabled": true,
  "prefix": "jairouter",
  "enabledCategories": ["request", "system"],
  "healthy": true,
  "errorStats": {
    "activeErrorComponents": 0,
    "degradedComponents": 0,
    "totalErrors": 0
  },
  "degradationStatus": {
    "level": "NORMAL",
    "samplingRate": 1.0,
    "autoModeEnabled": true
  },
  "cacheStats": {
    "usageRatio": 0.3,
    "activeRetries": 0
  },
  "circuitBreakerStats": {
    "state": "CLOSED",
    "failureRate": 0.0
  }
}
```

### 错误处理管理

#### `GET /api/monitoring/errors/stats`
获取错误处理统计信息。

#### `POST /api/monitoring/errors/reset`
重置指定组件的错误状态。

**请求体示例：**
```json
{
  "component": "metricsCollector",
  "operation": "collect"
}
```

### 降级策略管理

#### `GET /api/monitoring/degradation/status`
获取降级策略状态。

**响应示例：**
```json
{
  "level": "NORMAL",
  "levelDescription": "正常模式",
  "samplingRate": 1.0,
  "autoModeEnabled": true,
  "timeSinceLastChange": 3600000,
  "errorComponentCount": 0
}
```

#### `POST /api/monitoring/degradation/level`
设置降级级别。

**请求体示例：**
```json
{
  "level": "PARTIAL"
}
```

**可用级别：**
- `NORMAL` - 正常模式
- `PARTIAL` - 部分降级
- `MINIMAL` - 最小化模式
- `DISABLED` - 禁用模式

#### `POST /api/monitoring/degradation/auto-mode`
启用/禁用降级策略自动模式。

**请求体示例：**
```json
{
  "enabled": false
}
```

#### `POST /api/monitoring/degradation/force-recovery`
强制恢复到正常模式。

### 缓存管理

#### `GET /api/monitoring/cache/stats`
获取缓存统计信息。

**响应示例：**
```json
{
  "currentSize": 150,
  "maxSize": 1000,
  "usageRatio": 0.15,
  "activeRetries": 2,
  "queuedRetries": 0
}
```

#### `POST /api/monitoring/cache/clear`
清空缓存。

### 熔断器管理

#### `GET /api/monitoring/circuit-breaker/stats`
获取熔断器统计信息。

**响应示例：**
```json
{
  "state": "CLOSED",
  "failureCount": 0,
  "successCount": 100,
  "requestCount": 100,
  "failureRate": 0.0
}
```

#### `POST /api/monitoring/circuit-breaker/force-open`
强制开启熔断器。

#### `POST /api/monitoring/circuit-breaker/force-close`
强制关闭熔断器。

## 模型信息查询

### 基础路径：`/api/models`

#### `GET /api/models`
获取所有可用模型信息。

**响应示例：**
```json
{
  "success": true,
  "message": "获取模型列表成功",
  "data": {
    "object": "list",
    "data": [
      {
        "id": "qwen2:7b",
        "object": "model",
        "created": 1705123200,
        "owned_by": "model-router",
        "service_type": "chat",
        "adapter": "OllamaAdapter"
      },
      {
        "id": "text-embedding-ada-002",
        "object": "model",
        "created": 1705123200,
        "owned_by": "model-router",
        "service_type": "embedding",
        "adapter": "OpenAIAdapter"
      }
    ]
  }
}
```

## 配置版本管理

### 基础路径：`/api/config/version`

#### `GET /api/config/version`
获取所有配置版本列表。

**响应示例：**
```json
{
  "success": true,
  "message": "获取配置版本列表成功",
  "data": [1, 2, 3, 4, 5]
}
```

#### `GET /api/config/version/{version}`
获取指定版本的配置详情。

**路径参数：**
- `version` (integer): 版本号

#### `GET /api/config/version/current`
获取当前配置版本号。

**响应示例：**
```json
{
  "success": true,
  "message": "获取当前配置版本成功",
  "data": 5
}
```

#### `POST /api/config/version/rollback/{version}`
回滚到指定版本的配置。

#### `POST /api/config/version/apply/{version}`
应用指定版本的配置。

#### `DELETE /api/config/version/{version}`
删除指定版本的配置（不能删除当前版本）。

## 通用响应格式

所有管理 API 都使用统一的响应格式：

### 成功响应
```json
{
  "success": true,
  "message": "操作成功描述",
  "data": "响应数据"
}
```

### 错误响应
```json
{
  "success": false,
  "message": "错误描述",
  "data": null
}
```

## 错误码说明

| HTTP 状态码 | 说明 | 示例 |
|-------------|------|------|
| 200 | 操作成功 | 配置更新成功 |
| 201 | 资源创建成功 | 实例添加成功 |
| 400 | 请求参数错误 | 参数验证失败 |
| 404 | 资源不存在 | 指定的服务类型不存在 |
| 500 | 服务器内部错误 | 配置保存失败 |
| 501 | 功能未实现 | 删除配置版本功能暂未实现 |

## 使用示例

### cURL 示例

```bash
# 获取所有服务类型
curl -X GET "http://localhost:8080/api/config/type/services"

# 添加新的服务实例
curl -X POST "http://localhost:8080/api/config/instance/add/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "qwen2:7b",
    "baseUrl": "http://localhost:8000",
    "apiKey": "sk-xxx",
    "weight": 1,
    "enabled": true,
    "adapter": "ollama"
  }'

# 更新监控配置
curl -X PUT "http://localhost:8080/api/monitoring/config/enabled" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'

# 获取监控系统概览
curl -X GET "http://localhost:8080/api/monitoring/overview"
```

### Python 示例

```python
import requests

base_url = "http://localhost:8080"

# 获取所有可用模型
response = requests.get(f"{base_url}/api/models")
models = response.json()
print("可用模型:", models['data']['data'])

# 添加新实例
instance_data = {
    "name": "llama3:8b",
    "baseUrl": "http://localhost:8001",
    "apiKey": "sk-test",
    "weight": 1,
    "enabled": True,
    "adapter": "ollama"
}

response = requests.post(
    f"{base_url}/api/config/instance/add/chat",
    json=instance_data
)
print("添加实例结果:", response.json())

# 获取监控状态
response = requests.get(f"{base_url}/api/monitoring/health")
health = response.json()
print("监控健康状态:", health)
```

### JavaScript 示例

```javascript
const baseUrl = 'http://localhost:8080';

// 获取服务配置
async function getServiceConfig(serviceType) {
  const response = await fetch(`${baseUrl}/api/config/type/services/${serviceType}`);
  const data = await response.json();
  return data;
}

// 更新监控配置
async function updateMonitoringConfig(config) {
  const response = await fetch(`${baseUrl}/api/monitoring/config/enabled`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(config)
  });
  return await response.json();
}

// 获取系统概览
async function getSystemOverview() {
  const response = await fetch(`${baseUrl}/api/monitoring/overview`);
  return await response.json();
}

// 使用示例
getServiceConfig('chat').then(config => {
  console.log('Chat服务配置:', config);
});

updateMonitoringConfig({ enabled: true }).then(result => {
  console.log('监控配置更新结果:', result);
});
```