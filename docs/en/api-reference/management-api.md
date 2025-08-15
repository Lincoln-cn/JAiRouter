# Management API

JAiRouter provides a complete set of management APIs for dynamic configuration management, service instance management, monitoring configuration, and more. All management APIs use the `/api` prefix.

## Service Type Management

### Base Path: `/api/config/type`

#### `GET /api/config/type`
Get all configuration information of the current system.

**Response Example:**
```json
{
  "success": true,
  "message": "Configuration retrieved successfully",
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
Get all available service types in the system.

**Response Example:**
```json
{
  "success": true,
  "message": "Service types retrieved successfully",
  "data": ["chat", "embedding", "rerank", "tts", "stt", "imgGen", "imgEdit"]
}
```

#### `GET /api/config/type/services/{serviceType}`
Get configuration information for a specified service type.

**Path Parameters:**
- `serviceType` (string): Service type, e.g., "chat"

**Response Example:**
```json
{
  "success": true,
  "message": "Service configuration retrieved successfully",
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
Create a new service type configuration.

**Request Body Example:**
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
Update configuration for a specified service type.

#### `DELETE /api/config/type/services/{serviceType}`
Delete a specified service type and all its configurations.

#### `GET /api/config/type/{serviceType}/models`
Get all available models under a specified service type.

**Response Example:**
```json
{
  "success": true,
  "message": "Model list retrieved successfully",
  "data": ["qwen2:7b", "llama3:8b", "gpt-3.5-turbo"]
}
```

#### `POST /api/config/type/reset`
Reset system configuration to default values.

## Service Instance Management

### Base Path: `/api/config/instance`

#### `GET /api/config/instance/type/{serviceType}`
Get all instances under a specified service type.

**Response Example:**
```json
{
  "success": true,
  "message": "Instance list retrieved successfully",
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
Get detailed information about a specified instance.

**Query Parameters:**
- [modelName](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\RateLimitContext.java#L7-L7) (string): Model name
- [baseUrl](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\UpdateInstanceDTO.java#L22-L22) (string): Base URL

**Response Example:**
```json
{
  "success": true,
  "message": "Instance information retrieved successfully",
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
Add a new instance for a specified service type.

**Request Body Example:**
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
Update configuration for a specified service instance.

**Request Body Example:**
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
Delete a specified service instance.

**Query Parameters:**
- [modelName](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\RateLimitContext.java#L7-L7) (string): Model name
- [baseUrl](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\dto\UpdateInstanceDTO.java#L22-L22) (string): Base URL

## Monitoring Management

### Base Path: `/api/monitoring`

#### `GET /api/monitoring/config`
Get current monitoring configuration.

**Response Example:**
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
Update monitoring enabled status.

**Request Body Example:**
```json
{
  "enabled": true
}
```

#### `PUT /api/monitoring/config/prefix`
Update metric prefix.

**Request Body Example:**
```json
{
  "prefix": "jairouter_v2"
}
```

#### `PUT /api/monitoring/config/collection-interval`
Update collection interval.

**Request Body Example:**
```json
{
  "interval": "PT60S"
}
```

#### `PUT /api/monitoring/config/categories`
Update enabled monitoring categories.

**Request Body Example:**
```json
{
  "categories": ["request", "system"]
}
```

#### `PUT /api/monitoring/config/custom-tags`
Update custom tags.

**Request Body Example:**
```json
{
  "customTags": {
    "environment": "staging",
    "region": "us-west-2"
  }
}
```

#### `GET /api/monitoring/config/snapshot`
Get monitoring configuration snapshot.

#### `GET /api/monitoring/health`
Get monitoring system health status.

**Response Example:**
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
Get an overview of the monitoring system's overall status.

**Response Example:**
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

### Error Handling Management

#### `GET /api/monitoring/errors/stats`
Get error handling statistics.

#### `POST /api/monitoring/errors/reset`
Reset error status for a specified component.

**Request Body Example:**
```json
{
  "component": "metricsCollector",
  "operation": "collect"
}
```

### Degradation Policy Management

#### `GET /api/monitoring/degradation/status`
Get degradation policy status.

**Response Example:**
```json
{
  "level": "NORMAL",
  "levelDescription": "Normal Mode",
  "samplingRate": 1.0,
  "autoModeEnabled": true,
  "timeSinceLastChange": 3600000,
  "errorComponentCount": 0
}
```

#### `POST /api/monitoring/degradation/level`
Set degradation level.

**Request Body Example:**
```json
{
  "level": "PARTIAL"
}
```

**Available Levels:**
- `NORMAL` - Normal Mode
- `PARTIAL` - Partial Degradation
- `MINIMAL` - Minimal Mode
- `DISABLED` - Disabled Mode

#### `POST /api/monitoring/degradation/auto-mode`
Enable/disable automatic degradation policy mode.

**Request Body Example:**
```json
{
  "enabled": false
}
```

#### `POST /api/monitoring/degradation/force-recovery`
Force recovery to normal mode.

### Cache Management

#### `GET /api/monitoring/cache/stats`
Get cache statistics.

**Response Example:**
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
Clear cache.

### Circuit Breaker Management

#### `GET /api/monitoring/circuit-breaker/stats`
Get circuit breaker statistics.

**Response Example:**
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
Force open the circuit breaker.

#### `POST /api/monitoring/circuit-breaker/force-close`
Force close the circuit breaker.

## Model Information Query

### Base Path: `/api/models`

#### `GET /api/models`
Get information for all available models.

**Response Example:**
```json
{
  "success": true,
  "message": "Model list retrieved successfully",
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

## Configuration Version Management

### Base Path: `/api/config/version`

#### `GET /api/config/version`
Get a list of all configuration versions.

**Response Example:**
```json
{
  "success": true,
  "message": "Configuration version list retrieved successfully",
  "data": [1, 2, 3, 4, 5]
}
```

#### `GET /api/config/version/{version}`
Get details of a specified version configuration.

**Path Parameters:**
- [version](file://springfox\documentation\service\ApiInfo.java#L8-L8) (integer): Version number

#### `GET /api/config/version/current`
Get the current configuration version number.

**Response Example:**
```json
{
  "success": true,
  "message": "Current configuration version retrieved successfully",
  "data": 5
}
```

#### `POST /api/config/version/rollback/{version}`
Rollback to a specified version configuration.

#### `POST /api/config/version/apply/{version}`
Apply a specified version configuration.

#### `DELETE /api/config/version/{version}`
Delete a specified version configuration (cannot delete the current version).

## General Response Format

All management APIs use a unified response format:

### Success Response
```json
{
  "success": true,
  "message": "Operation successful description",
  "data": "Response data"
}
```

### Error Response
```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

## Error Code Explanation

| HTTP Status Code | Description | Example |
|------------------|-------------|---------|
| 200 | Operation successful | Configuration updated successfully |
| 201 | Resource created successfully | Instance added successfully |
| 400 | Request parameter error | Parameter validation failed |
| 404 | Resource not found | Specified service type does not exist |
| 500 | Internal server error | Configuration save failed |
| 501 | Function not implemented | Delete configuration version function not yet implemented |

## Usage Examples

### cURL Examples

```bash
# Get all service types
curl -X GET "http://localhost:8080/api/config/type/services"

# Add a new service instance
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

# Update monitoring configuration
curl -X PUT "http://localhost:8080/api/monitoring/config/enabled" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'

# Get monitoring system overview
curl -X GET "http://localhost:8080/api/monitoring/overview"
```

### Python Examples

```python
import requests

base_url = "http://localhost:8080"

# Get all available models
response = requests.get(f"{base_url}/api/models")
models = response.json()
print("Available models:", models['data']['data'])

# Add new instance
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
print("Add instance result:", response.json())

# Get monitoring status
response = requests.get(f"{base_url}/api/monitoring/health")
health = response.json()
print("Monitoring health status:", health)
```

### JavaScript Examples

```javascript
const baseUrl = 'http://localhost:8080';

// Get service configuration
async function getServiceConfig(serviceType) {
  const response = await fetch(`${baseUrl}/api/config/type/services/${serviceType}`);
  const data = await response.json();
  return data;
}

// Update monitoring configuration
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

// Get system overview
async function getSystemOverview() {
  const response = await fetch(`${baseUrl}/api/monitoring/overview`);
  return await response.json();
}

// Usage examples
getServiceConfig('chat').then(config => {
  console.log('Chat service configuration:', config);
});

updateMonitoringConfig({ enabled: true }).then(result => {
  console.log('Monitoring configuration update result:', result);
});
```